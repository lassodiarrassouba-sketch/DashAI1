package com.dashai.app.obd;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads reports shared by the official ThinkDiag+ application. */
public final class ThinkDiagReportLoader {
    private static final int MAX_REPORT_BYTES = 1_500_000;
    private static final int MAX_REDIRECTS = 3;
    private static final int CONNECT_TIMEOUT_MS = 12_000;
    private static final int READ_TIMEOUT_MS = 25_000;
    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset=([A-Za-z0-9._-]+)");

    private ThinkDiagReportLoader() {
    }

    public static String readTextUri(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null) throw new IOException("Rapport introuvable.");
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("Impossible d’ouvrir le rapport.");
            byte[] bytes = readLimited(input, MAX_REPORT_BYTES);
            return ObdReportTools.normalize(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    public static String fetchThinkCarReport(String reportUrl) throws IOException {
        String current = reportUrl == null ? "" : reportUrl.trim();
        if (!ObdReportTools.isAllowedThinkCarUrl(current)) {
            throw new IOException("Seuls les liens HTTPS officiels ThinkCar peuvent être importés automatiquement.");
        }

        for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
            URL url = new URL(current);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/html,text/plain,application/xhtml+xml");
            connection.setRequestProperty("Accept-Encoding", "identity");
            connection.setRequestProperty("User-Agent", "DIASCO-Auto/2.2 Android");

            try {
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new IOException("Redirection ThinkCar invalide.");
                    }
                    current = new URL(url, location).toString();
                    if (!ObdReportTools.isAllowedThinkCarUrl(current)) {
                        throw new IOException("Le rapport redirige vers un domaine non autorisé.");
                    }
                    continue;
                }
                if (code < 200 || code >= 300) {
                    throw new IOException("Le serveur ThinkCar a répondu avec le code " + code + ".");
                }

                String contentType = connection.getContentType();
                if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/pdf")) {
                    throw new IOException("Ce lien ouvre un PDF. Utilisez Partager ou Importer le rapport PDF.");
                }

                try (InputStream input = connection.getInputStream()) {
                    byte[] bytes = readLimited(input, MAX_REPORT_BYTES);
                    Charset charset = charsetFromContentType(contentType);
                    String raw = new String(bytes, charset);
                    return htmlToText(raw);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Le rapport ThinkCar contient trop de redirections.");
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8_192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) throw new IOException("Le rapport est trop volumineux.");
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static Charset charsetFromContentType(String contentType) {
        if (contentType != null) {
            Matcher matcher = CHARSET_PATTERN.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (IllegalArgumentException ignored) {
                    // UTF-8 below.
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    private static String htmlToText(String raw) {
        String value = raw == null ? "" : raw;
        value = value
                .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(?:p|div|li|tr|h[1-6])>", "\n");

        Spanned spanned;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            spanned = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY);
        } else {
            //noinspection deprecation
            spanned = Html.fromHtml(value);
        }
        return ObdReportTools.normalize(spanned.toString());
    }
}
