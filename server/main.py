import base64
import binascii
import html
import json
import os
import re
import secrets
import urllib.error
import urllib.parse
import urllib.request
from functools import lru_cache

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from openai import OpenAI

load_dotenv()

app = FastAPI(title="DIASCO Backend", version="2.1.0")

DEFAULT_CORS_ORIGINS = {
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:8888",
    "http://127.0.0.1:8888",
}
ALLOWED_IMAGE_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGE_BYTES = 7_000_000
MAX_PROVIDER_RESPONSE_BYTES = 12_000_000
DEFAULT_CLOUDFLARE_TEXT_MODEL = "@cf/meta/llama-3.1-8b-instruct-fp8-fast"
DEFAULT_CLOUDFLARE_IMAGE_MODEL = "@cf/black-forest-labs/flux-1-schnell"

SYSTEM_PROMPT = """
Tu es DIASCO, un assistant personnel francophone, naturel, attentif et créatif.
Règles de réponse :
- Réponds en français clair et naturel, sauf si l'utilisateur demande une autre langue.
- Réponds directement à la question, sans inventer de faits.
- Tiens une vraie conversation : comprends les sous-entendus à partir du contexte, adapte le ton et évite les formulations mécaniques.
- Aide à réfléchir, écrire, apprendre, programmer, concevoir et créer, tout en restant honnête sur tes limites.
- Quand tu n'es pas sûr, dis-le simplement et propose une vérification.
- Les réponses vocales doivent rester courtes par défaut : 2 à 6 phrases.
- N'utilise pas de Markdown pour les réponses courantes : pas de **gras**, pas de titres avec #.
- Si l'utilisateur demande du code, une formule, une équation ou un contenu technique, donne une réponse exploitable à l'écran.
- Pour le code, conserve l'indentation et les retours à la ligne, mais n'encadre pas le code avec des balises Markdown ``` sauf si l'utilisateur le demande.
- Pour les formules, écris la formule clairement, explique brièvement les variables, puis donne un exemple si utile.
- N'inclus pas de lien brut sauf si l'utilisateur demande explicitement des sources.
- Si l'utilisateur fait une relance courte comme « oui », « dis-moi », « depuis quand » ou « et lui ? », utilise le contexte récent fourni pour comprendre la question.
- Quand l'assistant précédent a proposé « je peux te dire son parcours / depuis quand / la prochaine élection » et que l'utilisateur répond « oui » ou « dis-le-moi », donne directement l'information proposée la plus utile au lieu de redemander le sujet.
- Pour les relances contextuelles, commence par répondre à la relance, puis ajoute au maximum une phrase de précision.
""".strip()


def allowed_origins_from_env() -> list[str]:
    raw = os.getenv("ALLOWED_ORIGINS", "").strip()
    origins = set(DEFAULT_CORS_ORIGINS)
    if raw:
        for item in raw.split(","):
            origin = item.strip().rstrip("/")
            if origin:
                origins.add(origin)
    return sorted(origins)


cors_origins = allowed_origins_from_env()
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"] if "*" in cors_origins else cors_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)


class AskRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=4000)
    locale: str = Field(default="fr-FR", max_length=32)
    client: str | None = Field(default=None, max_length=64)
    history: str | None = Field(default=None, max_length=12000)


class AskResponse(BaseModel):
    answer: str


class ImageRequest(BaseModel):
    prompt: str = Field(..., min_length=1, max_length=2000)
    locale: str = Field(default="fr-FR", max_length=32)
    client: str | None = Field(default=None, max_length=64)


class ImageResponse(BaseModel):
    answer: str
    image_base64: str
    mime_type: str
    revised_prompt: str | None = None


class SiteRequest(BaseModel):
    prompt: str = Field(..., min_length=1, max_length=4000)
    locale: str = Field(default="fr-FR", max_length=32)
    client: str | None = Field(default=None, max_length=64)
    history: str | None = Field(default=None, max_length=12000)


class SiteResponse(BaseModel):
    answer: str
    title: str
    html: str


class VisionRequest(BaseModel):
    image_base64: str = Field(..., min_length=1, max_length=10_000_000)
    mime_type: str = Field(default="image/jpeg", max_length=64)
    prompt: str | None = Field(default=None, max_length=1000)
    locale: str = Field(default="fr-FR", max_length=32)
    client: str | None = Field(default=None, max_length=64)
    history: str | None = Field(default=None, max_length=12000)


@lru_cache(maxsize=1)
def get_openai_client() -> OpenAI:
    api_key = os.getenv("OPENAI_API_KEY", "").strip()
    if not api_key:
        raise RuntimeError("OPENAI_API_KEY absent")
    return OpenAI(api_key=api_key)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


def wants_structured_answer(question: str) -> bool:
    normalized = question.lower()
    normalized = normalized.replace("é", "e").replace("è", "e").replace("ê", "e").replace("à", "a")
    return any(
        keyword in normalized
        for keyword in (
            "code",
            "programme",
            "script",
            "algorithme",
            "html",
            "css",
            "javascript",
            "java",
            "kotlin",
            "python",
            "php",
            "sql",
            "formule",
            "equation",
            "math",
            "excel",
            "physique",
            "chimie",
        )
    )


def clean_answer(text: str, preserve_format: bool = False) -> str:
    """Nettoie les marqueurs Markdown qui s'affichent mal dans l'application vocale."""
    cleaned = text.strip()
    cleaned = re.sub(r"\*\*(.*?)\*\*", r"\1", cleaned)
    cleaned = re.sub(r"__(.*?)__", r"\1", cleaned)
    cleaned = cleaned.replace("**", "").replace("__", "")
    cleaned = re.sub(r"(?m)^\s*```[a-zA-Z0-9_-]*\s*$", "", cleaned)
    if not preserve_format:
        cleaned = cleaned.replace("`", "")
    cleaned = re.sub(r"(?m)^\s*#{1,6}\s*", "", cleaned)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def provider_http_exception(exc: Exception, feature: str) -> HTTPException:
    """Transforme les erreurs fournisseur en messages utiles sans exposer les détails internes."""
    raw = str(exc).lower()
    if "billing_hard_limit_reached" in raw or "billing hard limit" in raw:
        return HTTPException(
            status_code=402,
            detail=(
                "La génération est momentanément indisponible car le plafond de facturation "
                "du compte IA est atteint. Réactivez le crédit ou augmentez la limite du compte API."
            ),
        )
    if "insufficient_quota" in raw or "quota" in raw and "exceed" in raw:
        return HTTPException(
            status_code=402,
            detail="Le crédit du compte IA est insuffisant pour cette demande.",
        )
    if "rate_limit" in raw or "rate limit" in raw:
        return HTTPException(
            status_code=429,
            detail="Le service IA reçoit trop de demandes. Réessayez dans un instant.",
        )
    return HTTPException(
        status_code=502,
        detail=f"Le service IA {feature} est temporairement indisponible.",
    )


def provider_chain_http_exception(errors: list[Exception], feature: str) -> HTTPException:
    """Retourne une erreur utile sans exposer les réponses ni les secrets des fournisseurs."""
    combined = " ".join(str(error).lower() for error in errors)
    if "cloudflare_authentication" in combined or "cloudflare_invalid_account" in combined:
        return HTTPException(
            status_code=503,
            detail="Le moteur IA gratuit doit être reconfiguré sur le serveur.",
        )
    if "cloudflare_quota" in combined or "cloudflare_rate_limit" in combined:
        return HTTPException(
            status_code=429,
            detail="Le quota gratuit de DIASCO est momentanément atteint. Réessayez plus tard.",
        )
    if errors:
        return provider_http_exception(errors[-1], feature)
    return HTTPException(status_code=503, detail=f"Aucun moteur IA {feature} n’est configuré.")


def cloudflare_is_configured() -> bool:
    return bool(
        os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip()
        and os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
    )


def cloudflare_run(model: str, payload: dict, timeout: int = 90) -> object:
    account_id = os.getenv("CLOUDFLARE_ACCOUNT_ID", "").strip()
    api_token = os.getenv("CLOUDFLARE_API_TOKEN", "").strip()
    if not account_id or not api_token:
        raise RuntimeError("cloudflare_not_configured")
    if not re.fullmatch(r"[A-Za-z0-9_-]{6,128}", account_id):
        raise RuntimeError("cloudflare_invalid_account")
    if not model.startswith("@cf/"):
        raise RuntimeError("cloudflare_invalid_model")

    encoded_account = urllib.parse.quote(account_id, safe="")
    encoded_model = urllib.parse.quote(model, safe="@/-_.")
    url = f"https://api.cloudflare.com/client/v4/accounts/{encoded_account}/ai/run/{encoded_model}"
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": f"Bearer {api_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw_response = response.read(MAX_PROVIDER_RESPONSE_BYTES + 1)
    except urllib.error.HTTPError as exc:
        error_code = None
        try:
            error_payload = json.loads(exc.read(50_000).decode("utf-8"))
            errors = error_payload.get("errors", []) if isinstance(error_payload, dict) else []
            error_code = errors[0].get("code") if errors and isinstance(errors[0], dict) else None
        except (UnicodeDecodeError, json.JSONDecodeError, AttributeError):
            pass
        if error_code == 5016:
            raise RuntimeError("cloudflare_model_terms") from exc
        if exc.code in (401, 403):
            raise RuntimeError("cloudflare_authentication") from exc
        if exc.code == 429:
            raise RuntimeError("cloudflare_rate_limit") from exc
        if exc.code == 402:
            raise RuntimeError("cloudflare_quota") from exc
        raise RuntimeError(f"cloudflare_http_{exc.code}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError("cloudflare_unreachable") from exc

    if len(raw_response) > MAX_PROVIDER_RESPONSE_BYTES:
        raise RuntimeError("cloudflare_response_too_large")
    try:
        response_payload = json.loads(raw_response.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RuntimeError("cloudflare_invalid_response") from exc
    if not isinstance(response_payload, dict) or response_payload.get("success") is False:
        raise RuntimeError("cloudflare_failed_response")
    return response_payload.get("result", response_payload)


def generate_cloudflare_text(prompt: str, max_output_tokens: int) -> str:
    model = os.getenv("CLOUDFLARE_TEXT_MODEL", DEFAULT_CLOUDFLARE_TEXT_MODEL).strip()
    result = cloudflare_run(
        model,
        {
            "messages": [{"role": "user", "content": prompt}],
            "max_tokens": max(64, min(max_output_tokens, 8192)),
            "temperature": 0.4,
        },
    )
    if isinstance(result, dict):
        answer = result.get("response", "")
    else:
        answer = result if isinstance(result, str) else ""
    if not isinstance(answer, str) or not answer.strip():
        raise RuntimeError("cloudflare_missing_text")
    return answer.strip()


def generate_openai_text(prompt: str, model: str, max_output_tokens: int) -> str:
    response = get_openai_client().responses.create(
        model=model,
        input=prompt,
        max_output_tokens=max_output_tokens,
    )
    answer = getattr(response, "output_text", "") or ""
    if not answer.strip():
        raise RuntimeError("openai_missing_text")
    return answer.strip()


def generate_text_with_fallback(prompt: str, model: str, max_output_tokens: int, feature: str) -> str:
    provider = os.getenv("AI_PROVIDER", "auto").strip().lower()
    if provider not in {"auto", "cloudflare", "openai"}:
        provider = "auto"
    errors: list[Exception] = []

    if provider in {"auto", "cloudflare"}:
        if cloudflare_is_configured():
            try:
                return generate_cloudflare_text(prompt, max_output_tokens)
            except Exception as exc:
                errors.append(exc)
        elif provider == "cloudflare":
            raise HTTPException(status_code=503, detail="Le moteur IA gratuit n’est pas encore configuré.")

    if provider in {"auto", "openai"}:
        try:
            return generate_openai_text(prompt, model, max_output_tokens)
        except Exception as exc:
            errors.append(exc)

    raise provider_chain_http_exception(errors, feature)


def detected_image_mime_type(decoded: bytes) -> str:
    if decoded.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if decoded.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if decoded.startswith(b"RIFF") and decoded[8:12] == b"WEBP":
        return "image/webp"
    raise RuntimeError("cloudflare_invalid_image")


def generate_cloudflare_image(prompt: str) -> tuple[str, str, str | None]:
    model = os.getenv("CLOUDFLARE_IMAGE_MODEL", DEFAULT_CLOUDFLARE_IMAGE_MODEL).strip()
    try:
        steps = max(1, min(int(os.getenv("CLOUDFLARE_IMAGE_STEPS", "4")), 8))
    except ValueError:
        steps = 4
    result = cloudflare_run(
        model,
        {
            "prompt": prompt[:2048],
            "steps": steps,
            "seed": secrets.randbelow(2_147_483_647),
        },
    )
    image_base64 = result.get("image") if isinstance(result, dict) else None
    if not isinstance(image_base64, str) or not image_base64:
        raise RuntimeError("cloudflare_missing_image")
    try:
        decoded = base64.b64decode(image_base64, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise RuntimeError("cloudflare_invalid_base64") from exc
    if not decoded or len(decoded) > MAX_IMAGE_BYTES:
        raise RuntimeError("cloudflare_invalid_image_size")
    return image_base64, detected_image_mime_type(decoded), None


def generate_openai_image(prompt: str) -> tuple[str, str, str | None]:
    model = os.getenv("OPENAI_IMAGE_MODEL", "gpt-image-2").strip()
    size = os.getenv("OPENAI_IMAGE_SIZE", "1024x1024").strip()
    quality = os.getenv("OPENAI_IMAGE_QUALITY", "low").strip()
    output_format = os.getenv("OPENAI_IMAGE_FORMAT", "png").strip().lower()
    response = get_openai_client().images.generate(
        model=model,
        prompt=prompt,
        size=size,
        quality=quality,
        output_format=output_format,
        n=1,
    )
    data = response.data or []
    first = data[0] if data else None
    image_base64 = getattr(first, "b64_json", None) if first is not None else None
    revised_prompt = getattr(first, "revised_prompt", None) if first is not None else None
    if not image_base64:
        raise RuntimeError("openai_missing_image")
    mime_type = f"image/{'jpeg' if output_format == 'jpg' else output_format}"
    return image_base64, mime_type, revised_prompt


def generate_image_with_fallback(prompt: str) -> tuple[str, str, str | None]:
    provider = os.getenv("IMAGE_PROVIDER", "auto").strip().lower()
    if provider not in {"auto", "cloudflare", "openai"}:
        provider = "auto"
    errors: list[Exception] = []

    if provider in {"auto", "cloudflare"}:
        if cloudflare_is_configured():
            try:
                return generate_cloudflare_image(prompt)
            except Exception as exc:
                errors.append(exc)
        elif provider == "cloudflare":
            raise HTTPException(status_code=503, detail="Le moteur d’images gratuit n’est pas encore configuré.")

    if provider in {"auto", "openai"}:
        try:
            return generate_openai_image(prompt)
        except Exception as exc:
            errors.append(exc)

    raise provider_chain_http_exception(errors, "d’image")


def generate_cloudflare_vision(
    prompt: str,
    image_base64: str,
    mime_type: str,
    max_output_tokens: int,
) -> str:
    model = os.getenv("CLOUDFLARE_VISION_MODEL", "@cf/meta/llama-3.2-11b-vision-instruct").strip()
    result = cloudflare_run(
        model,
        {
            "messages": [{"role": "user", "content": prompt}],
            "image": f"data:{mime_type};base64,{image_base64}",
            "max_tokens": max(64, min(max_output_tokens, 2048)),
            "temperature": 0.3,
        },
    )
    if isinstance(result, dict):
        answer = result.get("response", "")
    else:
        answer = result if isinstance(result, str) else ""
    if not isinstance(answer, str) or not answer.strip():
        raise RuntimeError("cloudflare_missing_vision_text")
    return answer.strip()


def generate_openai_vision(
    prompt: str,
    image_base64: str,
    mime_type: str,
    model: str,
    max_output_tokens: int,
) -> str:
    response = get_openai_client().responses.create(
        model=model,
        input=[
            {
                "role": "user",
                "content": [
                    {"type": "input_text", "text": prompt},
                    {"type": "input_image", "image_url": f"data:{mime_type};base64,{image_base64}"},
                ],
            }
        ],
        max_output_tokens=max_output_tokens,
    )
    answer = getattr(response, "output_text", "") or ""
    if not answer.strip():
        raise RuntimeError("openai_missing_vision_text")
    return answer.strip()


def generate_vision_with_fallback(
    prompt: str,
    image_base64: str,
    mime_type: str,
    model: str,
    max_output_tokens: int,
) -> str:
    provider = os.getenv("VISION_PROVIDER", "auto").strip().lower()
    if provider not in {"auto", "cloudflare", "openai"}:
        provider = "auto"
    errors: list[Exception] = []

    if provider in {"auto", "cloudflare"}:
        if cloudflare_is_configured():
            try:
                return generate_cloudflare_vision(prompt, image_base64, mime_type, max_output_tokens)
            except Exception as exc:
                errors.append(exc)
        elif provider == "cloudflare":
            raise HTTPException(status_code=503, detail="Le moteur visuel gratuit n’est pas encore configuré.")

    if provider in {"auto", "openai"}:
        try:
            return generate_openai_vision(prompt, image_base64, mime_type, model, max_output_tokens)
        except Exception as exc:
            errors.append(exc)

    combined = " ".join(str(error).lower() for error in errors)
    if "cloudflare_model_terms" in combined:
        raise HTTPException(
            status_code=503,
            detail="Le modèle visuel gratuit attend l’acceptation de sa licence dans Cloudflare.",
        )
    raise provider_chain_http_exception(errors, "de vision")


def extract_standalone_html(raw: str) -> str:
    cleaned = raw.strip()
    cleaned = re.sub(r"(?i)^\s*```(?:html)?\s*", "", cleaned)
    cleaned = re.sub(r"\s*```\s*$", "", cleaned)
    lower = cleaned.lower()
    doctype_index = lower.find("<!doctype html")
    html_index = lower.find("<html")
    starts = [index for index in (doctype_index, html_index) if index >= 0]
    if not starts:
        raise HTTPException(status_code=502, detail="Le site généré ne contient pas de document HTML complet.")
    start = min(starts)
    end = lower.rfind("</html>")
    if end < start:
        raise HTTPException(status_code=502, detail="Le site généré est incomplet.")
    result = cleaned[start : end + len("</html>")].strip()
    if len(result) > 500_000:
        raise HTTPException(status_code=502, detail="Le site généré est trop volumineux.")
    return result


def title_from_html(document: str) -> str:
    match = re.search(r"(?is)<title[^>]*>(.*?)</title>", document)
    if not match:
        return "Site créé par DIASCO"
    title = re.sub(r"<[^>]+>", "", html.unescape(match.group(1))).strip()
    return title[:100] or "Site créé par DIASCO"


def normalize_image_payload(payload: VisionRequest) -> tuple[str, str]:
    raw_image = payload.image_base64.strip()
    mime_type = payload.mime_type.strip().lower() or "image/jpeg"

    if raw_image.startswith("data:"):
        try:
            header, raw_image = raw_image.split(",", 1)
        except ValueError as exc:
            raise HTTPException(status_code=400, detail="Image data URL invalide.") from exc
        header_mime = header[5:].split(";", 1)[0].strip().lower()
        if header_mime:
            mime_type = header_mime

    if mime_type not in ALLOWED_IMAGE_MIME_TYPES:
        raise HTTPException(status_code=400, detail="Type d’image non supporté. Utilisez JPEG, PNG ou WebP.")

    try:
        decoded = base64.b64decode(raw_image, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=400, detail="Image Base64 invalide.") from exc

    if not decoded:
        raise HTTPException(status_code=400, detail="Image vide.")
    if len(decoded) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image trop volumineuse.")

    return raw_image, mime_type


@app.post("/api/ask", response_model=AskResponse)
def ask(payload: AskRequest) -> AskResponse:
    model = os.getenv("OPENAI_MODEL", "gpt-5.4-mini").strip()
    max_output_tokens = int(os.getenv("MAX_OUTPUT_TOKENS", "700"))

    history = (payload.history or "").strip()
    prompt = (
        f"{SYSTEM_PROMPT}\n\n"
        f"Langue préférée du téléphone : {payload.locale}\n"
        f"Contexte récent de la conversation, du plus ancien au plus récent :\n"
        f"{history if history else 'Aucun contexte récent.'}\n\n"
        f"Question actuelle de l'utilisateur : {payload.question}\n\n"
        "Instruction importante : si la question actuelle contient déjà un bloc de contexte ou une relance courte, "
        "ne réponds pas que tu ne sais pas le sujet. Déduis le sujet du contexte récent quand il est suffisant."
    )

    raw_answer = generate_text_with_fallback(prompt, model, max_output_tokens, "conversationnel")
    answer = clean_answer(
        raw_answer,
        preserve_format=wants_structured_answer(payload.question),
    )
    if not answer:
        raise HTTPException(status_code=502, detail="Le fournisseur IA n'a pas renvoyé de texte exploitable.")
    return AskResponse(answer=answer)


@app.post("/api/vision", response_model=AskResponse)
def vision(payload: VisionRequest) -> AskResponse:
    image_base64, mime_type = normalize_image_payload(payload)
    model = os.getenv("OPENAI_VISION_MODEL", os.getenv("OPENAI_MODEL", "gpt-5.4-mini")).strip()
    max_output_tokens = int(os.getenv("MAX_OUTPUT_TOKENS", "700"))
    history = (payload.history or "").strip()
    user_prompt = (payload.prompt or "").strip() or "Décris clairement ce que tu vois avec la caméra."

    prompt = (
        f"{SYSTEM_PROMPT}\n\n"
        "Tu reçois une image capturée depuis la caméra du téléphone.\n"
        "Décris les objets, personnes, textes visibles, couleurs et éléments importants de façon utile.\n"
        "Si l'image est floue ou ambiguë, dis ce que tu peux identifier avec prudence.\n\n"
        f"Langue préférée du téléphone : {payload.locale}\n"
        f"Contexte récent de la conversation :\n{history if history else 'Aucun contexte récent.'}\n\n"
        f"Demande de l'utilisateur : {user_prompt}"
    )

    raw_answer = generate_vision_with_fallback(
        prompt,
        image_base64,
        mime_type,
        model,
        max_output_tokens,
    )
    answer = clean_answer(raw_answer)
    if not answer:
        raise HTTPException(status_code=502, detail="Le fournisseur IA n'a pas renvoyé de description exploitable.")
    return AskResponse(answer=answer)


@app.post("/api/image", response_model=ImageResponse)
def image(payload: ImageRequest) -> ImageResponse:
    prompt = (
        "Génère une image claire, utile et adaptée à une application mobile francophone.\n"
        f"Demande de l'utilisateur : {payload.prompt.strip()}"
    )
    image_base64, mime_type, revised_prompt = generate_image_with_fallback(prompt)
    return ImageResponse(
        answer="Voici l’image générée.",
        image_base64=image_base64,
        mime_type=mime_type,
        revised_prompt=revised_prompt,
    )


@app.post("/api/site", response_model=SiteResponse)
def create_site(payload: SiteRequest) -> SiteResponse:
    model = os.getenv("OPENAI_SITE_MODEL", os.getenv("OPENAI_MODEL", "gpt-5.4-mini")).strip()
    max_output_tokens = int(os.getenv("SITE_MAX_OUTPUT_TOKENS", "9000"))
    history = (payload.history or "").strip()
    prompt = f"""
Tu es le studio web de DIASCO. Crée un site internet complet à partir de la demande ci-dessous.

Exigences obligatoires :
- Retourne uniquement un document HTML5 complet, de <!doctype html> à </html>.
- Place tout le CSS dans une balise <style> et tout le JavaScript utile dans une balise <script>.
- Le site doit être responsive, accessible, lisible sur mobile et prêt à être prévisualisé.
- N'utilise aucune bibliothèque, police, image ou ressource externe.
- N'ajoute aucun appel réseau, traqueur, collecte de données ni formulaire qui transmet des informations.
- Utilise des formes CSS, des couleurs et une mise en page soignée lorsque la demande ne fournit pas d'images.
- Implémente réellement les interactions simples demandées, sans texte expliquant comment utiliser le site.
- N'entoure jamais le document de balises Markdown ```.

Langue du téléphone : {payload.locale}
Contexte récent :
{history if history else 'Aucun contexte récent.'}

Demande de l'utilisateur :
{payload.prompt.strip()}
""".strip()

    raw_document = generate_text_with_fallback(prompt, model, max_output_tokens, "de création de site")
    document = extract_standalone_html(raw_document)
    return SiteResponse(
        answer="Le site est prêt. Vous pouvez le prévisualiser et télécharger le fichier HTML.",
        title=title_from_html(document),
        html=document,
    )
