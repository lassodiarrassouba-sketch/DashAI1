package com.dashai.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ConversationMemory {
    private static final String PREFS = "dashai_prefs";
    private static final String KEY_ENTRIES = "diasco_conversation_entries";
    private static final int MAX_ENTRIES = 40;
    private static final Object LOCK = new Object();

    private ConversationMemory() {
    }

    public static final class Entry {
        public final String author;
        public final String text;

        public Entry(String author, String text) {
            this.author = author;
            this.text = text;
        }
    }

    public static List<Entry> load(Context context) {
        synchronized (LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = preferences.getString(KEY_ENTRIES, "[]");
            ArrayList<Entry> entries = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(raw == null ? "[]" : raw);
                for (int index = 0; index < array.length(); index++) {
                    JSONObject item = array.optJSONObject(index);
                    if (item == null) continue;
                    String author = item.optString("author", "").trim();
                    String text = item.optString("text", "").trim();
                    if (!author.isEmpty() && !text.isEmpty()) {
                        entries.add(new Entry(author, text));
                    }
                }
            } catch (JSONException ignored) {
                preferences.edit().remove(KEY_ENTRIES).apply();
            }
            return entries;
        }
    }

    public static void appendTurn(Context context, String userText, String assistantText) {
        synchronized (LOCK) {
            List<Entry> entries = load(context);
            addIfUseful(entries, "Vous", userText);
            addIfUseful(entries, "DIASCO", assistantText);
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(0);
            }
            save(context, entries);
        }
    }

    public static String buildHistory(Context context) {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : load(context)) {
            builder.append(entry.author).append(" : ").append(entry.text).append('\n');
        }
        String history = builder.toString().trim();
        if (history.length() <= 12_000) return history;
        return history.substring(history.length() - 12_000);
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_ENTRIES)
                    .apply();
        }
    }

    private static void addIfUseful(List<Entry> entries, String author, String text) {
        String clean = text == null ? "" : text.trim();
        if (!clean.isEmpty()) entries.add(new Entry(author, clean));
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject item = new JSONObject();
            try {
                item.put("author", entry.author);
                item.put("text", entry.text);
                array.put(item);
            } catch (JSONException ignored) {
                // Les deux valeurs sont de simples chaînes locales.
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ENTRIES, array.toString())
                .apply();
    }
}
