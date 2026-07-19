package com.dashai.app.ai;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class RemoteAiClient {
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 120_000;

    public static final class ImageResult {
        public final String message;
        public final String imageBase64;
        public final String mimeType;

        private ImageResult(String message, String imageBase64, String mimeType) {
            this.message = message;
            this.imageBase64 = imageBase64;
            this.mimeType = mimeType;
        }

        public boolean hasImage() {
            return imageBase64 != null && !imageBase64.trim().isEmpty();
        }
    }

    public static final class SiteResult {
        public final String message;
        public final String title;
        public final String html;

        private SiteResult(String message, String title, String html) {
            this.message = message;
            this.title = title;
            this.html = html;
        }

        public boolean hasSite() {
            return html != null && !html.trim().isEmpty();
        }
    }

    private static final class PostResult {
        final int code;
        final String raw;
        final String error;

        private PostResult(int code, String raw, String error) {
            this.code = code;
            this.raw = raw;
            this.error = error;
        }
    }

    public String ask(String endpoint, String question, String localeTag) {
        return ask(endpoint, question, localeTag, "", false);
    }

    public String ask(String endpoint, String question, String localeTag, String history) {
        return ask(endpoint, question, localeTag, history, false);
    }

    public String ask(String endpoint, String question, String localeTag, String history, boolean allowHttp) {
        try {
            JSONObject body = new JSONObject();
            body.put("question", question);
            body.put("locale", localeTag == null || localeTag.isEmpty() ? "fr-FR" : localeTag);
            body.put("client", "diasco-android");
            if (history != null && !history.trim().isEmpty()) {
                body.put("history", history.trim());
            }

            return postForAnswer(endpoint, body, allowHttp);
        } catch (JSONException ex) {
            return "Demande backend illisible : " + ex.getMessage();
        }
    }

    public String describeImage(
            String askEndpoint,
            String imageBase64,
            String mimeType,
            String prompt,
            String localeTag,
            String history,
            boolean allowHttp
    ) {
        String visionEndpoint = buildVisionEndpoint(askEndpoint);
        if (imageBase64 == null || imageBase64.trim().isEmpty()) {
            return "Image vide : je n’ai rien reçu à analyser.";
        }

        try {
            JSONObject body = new JSONObject();
            body.put("image_base64", imageBase64);
            body.put("mime_type", mimeType == null || mimeType.isEmpty() ? "image/jpeg" : mimeType);
            body.put("prompt", prompt == null || prompt.trim().isEmpty()
                    ? "Décris clairement ce que tu vois avec la caméra."
                    : prompt.trim());
            body.put("locale", localeTag == null || localeTag.isEmpty() ? "fr-FR" : localeTag);
            body.put("client", "diasco-android");
            if (history != null && !history.trim().isEmpty()) {
                body.put("history", history.trim());
            }

            return postForAnswer(visionEndpoint, body, allowHttp);
        } catch (JSONException ex) {
            return "Demande image illisible : " + ex.getMessage();
        }
    }

    public ImageResult generateImage(String askEndpoint, String prompt, String localeTag, boolean allowHttp) {
        String imageEndpoint = buildSiblingEndpoint(askEndpoint, "/api/image");
        String cleanPrompt = prompt == null ? "" : prompt.trim();
        if (cleanPrompt.isEmpty()) {
            return new ImageResult("Décris l’image à générer, par exemple : affiche une image d’un robot bleu.", null, null);
        }

        try {
            JSONObject body = new JSONObject();
            body.put("prompt", cleanPrompt);
            body.put("locale", localeTag == null || localeTag.isEmpty() ? "fr-FR" : localeTag);
            body.put("client", "diasco-android");

            PostResult result = postJson(imageEndpoint, body, allowHttp);
            if (result.error != null) {
                return new ImageResult(result.error, null, null);
            }
            if (result.code < 200 || result.code >= 300) {
                return new ImageResult(formatBackendError(result.code, result.raw), null, null);
            }

            JSONObject root = new JSONObject(result.raw);
            String answer = root.optString("answer", "Voici l’image générée.").trim();
            String imageBase64 = root.optString("image_base64", "").trim();
            String mimeType = root.optString("mime_type", "image/png").trim();
            if (imageBase64.isEmpty()) {
                return new ImageResult("Le backend a répondu, mais sans image exploitable.", null, null);
            }
            return new ImageResult(answer.isEmpty() ? "Voici l’image générée." : answer, imageBase64, mimeType);
        } catch (JSONException ex) {
            return new ImageResult("Réponse image illisible : " + ex.getMessage(), null, null);
        }
    }

    public SiteResult generateWebsite(
            String askEndpoint,
            String prompt,
            String localeTag,
            String history,
            boolean allowHttp
    ) {
        String siteEndpoint = buildSiblingEndpoint(askEndpoint, "/api/site");
        String cleanPrompt = prompt == null ? "" : prompt.trim();
        if (cleanPrompt.isEmpty()) {
            return new SiteResult("Décris le site à créer.", null, null);
        }

        try {
            JSONObject body = new JSONObject();
            body.put("prompt", cleanPrompt);
            body.put("locale", localeTag == null || localeTag.isEmpty() ? "fr-FR" : localeTag);
            body.put("client", "diasco-android");
            if (history != null && !history.trim().isEmpty()) body.put("history", history.trim());

            PostResult result = postJson(siteEndpoint, body, allowHttp);
            if (result.error != null) {
                return new SiteResult(result.error, null, null);
            }
            if (result.code < 200 || result.code >= 300) {
                return new SiteResult(formatBackendError(result.code, result.raw), null, null);
            }

            JSONObject root = new JSONObject(result.raw);
            String answer = root.optString("answer", "Le site est prêt.").trim();
            String title = root.optString("title", "Site créé par DIASCO").trim();
            String html = root.optString("html", "").trim();
            if (html.isEmpty()) {
                return new SiteResult("Le backend a répondu, mais sans site HTML exploitable.", null, null);
            }
            return new SiteResult(answer.isEmpty() ? "Le site est prêt." : answer, title, html);
        } catch (JSONException exception) {
            return new SiteResult("Réponse de création de site illisible.", null, null);
        }
    }

    private String postForAnswer(String endpoint, JSONObject body, boolean allowHttp) {
        PostResult result = postJson(endpoint, body, allowHttp);
        if (result.error != null) return result.error;
        if (result.code < 200 || result.code >= 300) {
            return formatBackendError(result.code, result.raw);
        }

        try {
            String answer = parseAnswer(result.raw);
            if (answer == null || answer.trim().isEmpty()) {
                return "Le backend a répondu, mais sans champ answer exploitable.";
            }
            return answer.trim();
        } catch (JSONException ex) {
            return "Réponse backend illisible : " + ex.getMessage();
        }
    }

    private PostResult postJson(String endpoint, JSONObject body, boolean allowHttp) {
        String checkedEndpoint = endpoint == null ? "" : endpoint.trim();
        String endpointError = validateEndpoint(checkedEndpoint, allowHttp);
        if (endpointError != null) return new PostResult(0, "", endpointError);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(checkedEndpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String raw = readAll(stream);
            return new PostResult(code, raw, null);
        } catch (IOException ex) {
            return new PostResult(0, "", "Impossible de joindre le backend IA : " + ex.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String validateEndpoint(String endpoint, boolean allowHttp) {
        if (endpoint.isEmpty()) {
            return "Backend IA non configuré. Renseigne l’URL du serveur, par exemple https://ton-domaine.com/api/ask.";
        }

        try {
            URI uri = URI.create(endpoint);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (scheme.equals("https")) return null;
            if (scheme.equals("http") && allowHttp) return null;
            if (scheme.equals("http")) {
                return "HTTP local est autorisé seulement en debug. En release, configure une URL HTTPS.";
            }
            return "URL du backend invalide. Utilise une URL http en debug ou https en production.";
        } catch (IllegalArgumentException ex) {
            return "URL du backend invalide : " + ex.getMessage();
        }
    }

    private String buildVisionEndpoint(String askEndpoint) {
        return buildSiblingEndpoint(askEndpoint, "/api/vision");
    }

    private String buildSiblingEndpoint(String askEndpoint, String siblingPath) {
        String endpoint = askEndpoint == null ? "" : askEndpoint.trim();
        if (endpoint.endsWith(siblingPath)) return endpoint;
        if (endpoint.endsWith("/api/ask")) {
            return endpoint.substring(0, endpoint.length() - "/api/ask".length()) + siblingPath;
        }
        if (endpoint.endsWith("/")) return endpoint + siblingPath.substring(1);
        return endpoint + siblingPath;
    }

    private String readAll(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String parseAnswer(String raw) throws JSONException {
        JSONObject root = new JSONObject(raw);
        if (root.has("answer")) return root.optString("answer", "");
        if (root.has("text")) return root.optString("text", "");
        JSONObject data = root.optJSONObject("data");
        if (data != null && data.has("answer")) return data.optString("answer", "");
        return "";
    }

    private String parseError(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "réponse vide";
        try {
            JSONObject root = new JSONObject(raw);
            if (root.has("detail")) return root.optString("detail");
            if (root.has("error")) return root.optString("error");
            if (root.has("message")) return root.optString("message");
        } catch (JSONException ignored) {
            // On renvoie le texte brut tronqué ci-dessous.
        }
        return raw.length() > 300 ? raw.substring(0, 300) + "…" : raw;
    }

    private String formatBackendError(int code, String raw) {
        if (code == 402) {
            return "La création d’image est temporairement indisponible. "
                    + "Le propriétaire de DIASCO doit réactiver le crédit du service IA.";
        }
        if (code == 429) {
            return "Le service IA reçoit trop de demandes. Réessayez dans quelques instants.";
        }
        String detail = parseError(raw);
        if (detail == null || detail.trim().isEmpty()) {
            return "Le service DIASCO est momentanément indisponible.";
        }
        return detail;
    }
}
