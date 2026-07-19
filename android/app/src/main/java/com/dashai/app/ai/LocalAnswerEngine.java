package com.dashai.app.ai;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import com.dashai.app.util.TextUtils;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocalAnswerEngine {
    private static final Pattern SIMPLE_CALC = Pattern.compile(
            "(-?\\d+(?:[.,]\\d+)?)\\s*([+\\-x×*/])\\s*(-?\\d+(?:[.,]\\d+)?)"
    );

    public String answer(Context context, String question) {
        String clean = TextUtils.normalizeForIntent(question);
        if (clean.isEmpty()) {
            return "Je n’ai pas entendu de question.";
        }

        if (containsAny(clean, "bonjour", "salut", "coucou", "bonsoir")) {
            return "Bonjour. Je suis prêt à répondre.";
        }

        if (containsAny(clean, "qui es tu", "presente toi", "tu es quoi", "c'est quoi diasco", "c est quoi diasco")) {
            return "Je suis DIASCO, votre assistant vocal, visuel et créatif. Je peux converser, analyser une photo, écrire du code, créer des images et préparer des sites internet.";
        }

        if (containsAny(clean, "aide", "que peux tu faire", "commandes")) {
            return "Je peux donner l’heure, la date, le niveau de batterie, quelques infos sur le téléphone et faire des calculs simples. Pour les questions générales, active le mode en ligne et configure l’URL du backend IA.";
        }

        if (containsAny(clean, "heure", "quelle heure")) {
            return "Il est " + new SimpleDateFormat("HH:mm", Locale.FRANCE).format(new Date()) + ".";
        }

        if (containsAny(clean, "date", "quel jour", "aujourd'hui", "aujourdhui")) {
            return "Nous sommes le " + new SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRANCE).format(new Date()) + ".";
        }

        if (containsAny(clean, "batterie", "niveau de charge", "charge du telephone")) {
            return batteryAnswer(context);
        }

        if (containsAny(clean, "modele du telephone", "modele telephone", "mon telephone", "appareil")) {
            return "Téléphone détecté : " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + ".";
        }

        if (containsAny(clean, "merci")) {
            return "Avec plaisir.";
        }

        String calculation = simpleCalculation(clean);
        if (calculation != null) {
            return calculation;
        }

        return null;
    }

    public String offlineFallback() {
        return "Je n’ai pas de réponse locale fiable pour cette question. Active le mode en ligne et configure le backend IA pour les questions générales.";
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private String batteryAnswer(Context context) {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            return "Je n’arrive pas à lire le niveau de batterie.";
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level < 0 || scale <= 0) {
            return "Je n’arrive pas à lire le niveau de batterie.";
        }
        int percent = Math.round(level * 100f / scale);
        return "La batterie est à " + percent + " %.";
    }

    private String simpleCalculation(String clean) {
        Matcher matcher = SIMPLE_CALC.matcher(clean.replace(',', '.'));
        if (!matcher.find()) return null;

        double a = Double.parseDouble(matcher.group(1));
        String operator = matcher.group(2);
        double b = Double.parseDouble(matcher.group(3));
        double result;

        switch (operator) {
            case "+":
                result = a + b;
                break;
            case "-":
                result = a - b;
                break;
            case "x":
            case "×":
            case "*":
                result = a * b;
                break;
            case "/":
                if (b == 0d) return "Division par zéro impossible.";
                result = a / b;
                break;
            default:
                return null;
        }

        DecimalFormat format = new DecimalFormat("0.########");
        return "Le résultat est " + format.format(result) + ".";
    }
}
