package com.dashai.app.obd;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Stores only the user's local diagnostic history on the Android device. */
public final class ObdHistoryStore {
    private static final String PREFS = "diasco_obd_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 20;

    private ObdHistoryStore() {
    }

    public static final class Entry {
        public final long timestamp;
        public final String vehicle;
        public final String severity;
        public final String codes;
        public final String answer;

        private Entry(long timestamp, String vehicle, String severity, String codes, String answer) {
            this.timestamp = timestamp;
            this.vehicle = vehicle;
            this.severity = severity;
            this.codes = codes;
            this.answer = answer;
        }
    }

    public static synchronized void add(
            Context context,
            String vehicle,
            String severity,
            String codes,
            String answer
    ) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray existing = parse(preferences.getString(KEY_ITEMS, "[]"));
        JSONArray updated = new JSONArray();

        JSONObject current = new JSONObject();
        try {
            current.put("timestamp", System.currentTimeMillis());
            current.put("vehicle", limit(vehicle, 180));
            current.put("severity", limit(severity, 32));
            current.put("codes", limit(codes, 500));
            current.put("answer", limit(answer, 6_000));
            updated.put(current);
        } catch (JSONException ignored) {
            return;
        }

        for (int index = 0; index < existing.length() && updated.length() < MAX_ITEMS; index++) {
            JSONObject item = existing.optJSONObject(index);
            if (item != null) updated.put(item);
        }
        preferences.edit().putString(KEY_ITEMS, updated.toString()).apply();
    }

    public static synchronized List<Entry> load(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray array = parse(preferences.getString(KEY_ITEMS, "[]"));
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            entries.add(new Entry(
                    item.optLong("timestamp", 0L),
                    item.optString("vehicle", "Véhicule non renseigné"),
                    item.optString("severity", "INFORMATION"),
                    item.optString("codes", ""),
                    item.optString("answer", "")
            ));
        }
        return entries;
    }

    public static synchronized void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ITEMS)
                .apply();
    }

    private static JSONArray parse(String raw) {
        try {
            return new JSONArray(raw == null || raw.trim().isEmpty() ? "[]" : raw);
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    private static String limit(String value, int maxLength) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength);
    }
}
