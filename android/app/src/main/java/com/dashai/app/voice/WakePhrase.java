package com.dashai.app.voice;

import com.dashai.app.util.TextUtils;

public final class WakePhrase {
    public static final String DISPLAY = "Dis Diasco";
    public static final String SPOKEN = "dis diasco";

    private WakePhrase() {
    }

    public static boolean matches(String text) {
        String clean = TextUtils.normalizeForIntent(text);
        String compact = clean.replace(" ", "");
        return compact.contains("diasco")
                || compact.contains("diasko")
                || compact.contains("diasquo")
                || compact.contains("diascot")
                || compact.contains("diascoq")
                || compact.contains("diazko")
                || compact.contains("djasco")
                || compact.contains("yasco")
                || compact.contains("diaco")
                || clean.contains("dis di asco")
                || clean.contains("dit di asco")
                || clean.contains("dis diasco")
                || clean.contains("dit diasco")
                || clean.contains("dix diasco")
                || ((clean.contains("dis") || clean.contains("dit")) && compact.contains("dia"));
    }
}
