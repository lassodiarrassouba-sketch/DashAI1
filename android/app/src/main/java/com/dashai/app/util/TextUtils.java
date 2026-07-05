package com.dashai.app.util;

import java.text.Normalizer;
import java.util.Locale;

public final class TextUtils {
    private TextUtils() {
    }

    public static String normalizeForIntent(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized
                .replaceAll("[’'`´-]+", " ")
                .replaceAll("[^\\p{L}\\p{Nd}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
