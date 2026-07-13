import base64
import binascii
import os
import re
from functools import lru_cache

from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from openai import OpenAI

load_dotenv()

app = FastAPI(title="DashAI Backend", version="1.1.0")

DEFAULT_CORS_ORIGINS = {
    "http://localhost:5173",
    "http://127.0.0.1:5173",
    "http://localhost:8888",
    "http://127.0.0.1:8888",
}
ALLOWED_IMAGE_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_IMAGE_BYTES = 7_000_000

SYSTEM_PROMPT = """
Tu es DashAI, un assistant vocal Android francophone.
Règles de réponse :
- Réponds en français clair et naturel, sauf si l'utilisateur demande une autre langue.
- Réponds directement à la question, sans inventer de faits.
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

    try:
        response = get_openai_client().responses.create(
            model=model,
            input=prompt,
            max_output_tokens=max_output_tokens,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Erreur fournisseur IA : {exc}") from exc

    answer = clean_answer(
        getattr(response, "output_text", "") or "",
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

    try:
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
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Erreur fournisseur IA vision : {exc}") from exc

    answer = clean_answer(getattr(response, "output_text", "") or "")
    if not answer:
        raise HTTPException(status_code=502, detail="Le fournisseur IA n'a pas renvoyé de description exploitable.")
    return AskResponse(answer=answer)


@app.post("/api/image", response_model=ImageResponse)
def image(payload: ImageRequest) -> ImageResponse:
    model = os.getenv("OPENAI_IMAGE_MODEL", "gpt-image-1").strip()
    size = os.getenv("OPENAI_IMAGE_SIZE", "1024x1024").strip()
    quality = os.getenv("OPENAI_IMAGE_QUALITY", "low").strip()
    output_format = os.getenv("OPENAI_IMAGE_FORMAT", "png").strip().lower()
    prompt = (
        "Génère une image claire, utile et adaptée à une application mobile francophone.\n"
        f"Demande de l'utilisateur : {payload.prompt.strip()}"
    )

    try:
        response = get_openai_client().images.generate(
            model=model,
            prompt=prompt,
            size=size,
            quality=quality,
            output_format=output_format,
            n=1,
        )
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Erreur fournisseur IA image : {exc}") from exc

    data = response.data or []
    first = data[0] if data else None
    image_base64 = getattr(first, "b64_json", None) if first is not None else None
    revised_prompt = getattr(first, "revised_prompt", None) if first is not None else None
    if not image_base64:
        raise HTTPException(status_code=502, detail="Le fournisseur IA n'a pas renvoyé d'image exploitable.")

    mime_type = f"image/{'jpeg' if output_format == 'jpg' else output_format}"
    return ImageResponse(
        answer="Voici l’image générée.",
        image_base64=image_base64,
        mime_type=mime_type,
        revised_prompt=revised_prompt,
    )
