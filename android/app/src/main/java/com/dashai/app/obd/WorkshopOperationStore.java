package com.dashai.app.obd;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local audit trail for operations prepared from DIASCO Atelier. */
public final class WorkshopOperationStore {
    private static final String PREFS = "diasco_workshop_history";
    private static final String KEY_ITEMS = "items";
    private static final int MAX_ITEMS = 30;

    private WorkshopOperationStore() {
    }

    public static final class Entry {
        public final long timestamp;
        public final String vehicle;
        public final String operation;
        public final String status;
        public final String note;

        private Entry(long timestamp, String vehicle, String operation, String status, String note) {
            this.timestamp = timestamp;
            this.vehicle = vehicle;
            this.operation = operation;
            this.status = status;
            this.note = note;
        }
    }

    public static synchronized void add(
            Context context,
            String vehicle,
            String operation,
            String status,
            String note
    ) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray previous = parse(preferences.getString(KEY_ITEMS, "[]"));
        JSONArray updated = new JSONArray();
        JSONObject current = new JSONObject();
        try {
            current.put("timestamp", System.currentTimeMillis());
            current.put("vehicle", limit(vehicle, 180));
            current.put("operation", limit(operation, 120));
            current.put("status", limit(status, 80));
            current.put("note", limit(note, 2_000));
            updated.put(current);
        } catch (JSONException ignored) {
            return;
        }

        for (int index = 0; index < previous.length() && updated.length() < MAX_ITEMS; index++) {
            JSONObject item = previous.optJSONObject(index);
            if (item != null) updated.put(item);
        }
        preferences.edit().putString(KEY_ITEMS, updated.toString()).apply();
    }

    public static synchronized List<Entry> load(Context context) {
        JSONArray array = parse(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ITEMS, "[]"));
        List<Entry> entries = new ArrayList<>();
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            entries.add(new Entry(
                    item.optLong("timestamp", 0L),
                    item.optString("vehicle", "Véhicule non renseigné"),
                    item.optString("operation", "Opération inconnue"),
                    item.optString("status", "Statut inconnu"),
                    item.optString("note", "")
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
