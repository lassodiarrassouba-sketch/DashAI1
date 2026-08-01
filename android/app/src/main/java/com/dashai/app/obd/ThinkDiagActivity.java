package com.dashai.app.obd;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dashai.app.R;
import com.dashai.app.ai.RemoteAiClient;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Read-only bridge between the official ThinkDiag+ app and DIASCO.
 *
 * ThinkDiag+ remains responsible for Bluetooth communication and the vehicle
 * scan. A report shared to this activity is analysed automatically. No command
 * for clearing DTCs, coding an ECU or operating an actuator exists here.
 */
public final class ThinkDiagActivity extends AppCompatActivity {
    private static final int REQUEST_OPEN_REPORT = 701;
    private static final String THINKDIAG_PACKAGE = "com.us.thinkdiag.plus";
    private static final String PROFILE_PREFS = "diasco_obd_profile";
    private static final String KEY_NOTICE_SEEN = "notice_seen";
    private static final String KEY_MAKE = "make";
    private static final String KEY_MODEL = "model";
    private static final String KEY_YEAR = "year";
    private static final String KEY_FUEL = "fuel";
    private static final int MAX_PDF_PAGES = 6;
    private static final int MAX_RENDER_WIDTH = 1_400;
    private static final int MAX_RENDER_HEIGHT = 2_100;
    private static final int MAX_BACKEND_QUESTION_CHARS = 3_900;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private SharedPreferences profilePreferences;
    private EditText makeInput;
    private EditText modelInput;
    private EditText yearInput;
    private EditText fuelInput;
    private TextView statusText;
    private TextView codesText;
    private TextView resultText;
    private MaterialButton openThinkDiagButton;
    private MaterialButton importButton;
    private MaterialButton pasteButton;
    private MaterialButton historyButton;
    private TextToSpeech textToSpeech;
    private boolean busy;

    private static final class AnalysisOutcome {
        final String answer;
        final String severity;
        final List<String> codes;
        final String source;

        AnalysisOutcome(String answer, String severity, List<String> codes, String source) {
            this.answer = answer;
            this.severity = severity;
            this.codes = codes;
            this.source = source;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        profilePreferences = getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE);
        buildUi();
        initTextToSpeech();
        showFirstUseNotice();
        handleIncomingIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private void buildUi() {
        int padding = dp(16);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(243, 246, 245));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = new TextView(this);
        title.setText("DIASCO Auto");
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(23, 33, 38));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Extension ThinkDiag · diagnostic assisté en lecture seule");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(0, 122, 97));
        subtitle.setPadding(0, dp(2), 0, dp(12));
        root.addView(subtitle);

        TextView explanation = new TextView(this);
        explanation.setText("ThinkDiag+ réalise le scan du véhicule. Ensuite, utilisez Partager et choisissez DIASCO Auto : l’analyse démarre automatiquement, sans modifier les fonctions actuelles de DIASCO.");
        explanation.setTextSize(15);
        explanation.setTextColor(Color.rgb(55, 68, 64));
        explanation.setLineSpacing(0, 1.1f);
        root.addView(cardWithContent(explanation), cardParams(dp(0), dp(10)));

        root.addView(sectionTitle("Profil du véhicule"), sectionParams());
        LinearLayout profileContent = new LinearLayout(this);
        profileContent.setOrientation(LinearLayout.VERTICAL);
        profileContent.setPadding(dp(12), dp(8), dp(12), dp(12));

        makeInput = profileInput("Marque", profilePreferences.getString(KEY_MAKE, ""));
        modelInput = profileInput("Modèle", profilePreferences.getString(KEY_MODEL, ""));
        yearInput = profileInput("Année", profilePreferences.getString(KEY_YEAR, ""));
        fuelInput = profileInput("Énergie", profilePreferences.getString(KEY_FUEL, ""));

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        rowOne.addView(makeInput, weightedInputParams(true));
        rowOne.addView(modelInput, weightedInputParams(false));
        profileContent.addView(rowOne, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        rowTwo.addView(yearInput, weightedInputParams(true));
        rowTwo.addView(fuelInput, weightedInputParams(false));
        profileContent.addView(rowTwo, new LinearLayout.LayoutParams(-1, -2));
        root.addView(cardWithContent(profileContent), cardParams(dp(0), dp(8)));

        root.addView(sectionTitle("Connexion et rapport"), sectionParams());
        openThinkDiagButton = actionButton("Ouvrir ThinkDiag+", Color.rgb(0, 143, 114), Color.WHITE);
        openThinkDiagButton.setOnClickListener(view -> openThinkDiag());
        root.addView(openThinkDiagButton, fullButtonParams());

        LinearLayout importRow = new LinearLayout(this);
        importRow.setOrientation(LinearLayout.HORIZONTAL);
        importButton = actionButton("Importer", Color.WHITE, Color.rgb(23, 33, 38));
        importButton.setStrokeColor(ColorStateList.valueOf(Color.rgb(210, 220, 216)));
        importButton.setStrokeWidth(dp(1));
        importButton.setOnClickListener(view -> openReportPicker());
        pasteButton = actionButton("Coller", Color.WHITE, Color.rgb(23, 33, 38));
        pasteButton.setStrokeColor(ColorStateList.valueOf(Color.rgb(210, 220, 216)));
        pasteButton.setStrokeWidth(dp(1));
        pasteButton.setOnClickListener(view -> pasteReportFromClipboard());
        importRow.addView(importButton, rowButtonParams(true));
        importRow.addView(pasteButton, rowButtonParams(false));
        root.addView(importRow, new LinearLayout.LayoutParams(-1, -2));

        historyButton = actionButton("Historique des diagnostics", Color.rgb(234, 239, 237), Color.rgb(23, 33, 38));
        historyButton.setOnClickListener(view -> showHistory());
        root.addView(historyButton, fullButtonParams());

        statusText = new TextView(this);
        statusText.setText("Prêt. Lancez ThinkDiag+ ou importez un rapport.");
        statusText.setTextSize(13);
        statusText.setTextColor(Color.rgb(92, 107, 102));
        statusText.setPadding(dp(2), dp(8), dp(2), dp(8));
        root.addView(statusText);

        root.addView(sectionTitle("Résultat"), sectionParams());
        codesText = new TextView(this);
        codesText.setText("Aucun rapport analysé.");
        codesText.setTextSize(14);
        codesText.setTypeface(Typeface.DEFAULT_BOLD);
        codesText.setTextColor(Color.rgb(0, 122, 97));
        codesText.setPadding(dp(12), dp(12), dp(12), dp(4));

        resultText = new TextView(this);
        resultText.setText("Le résumé du diagnostic apparaîtra ici.");
        resultText.setTextSize(16);
        resultText.setTextColor(Color.rgb(23, 33, 38));
        resultText.setTextIsSelectable(true);
        resultText.setLineSpacing(0, 1.12f);
        resultText.setPadding(dp(12), dp(6), dp(12), dp(14));

        LinearLayout resultContent = new LinearLayout(this);
        resultContent.setOrientation(LinearLayout.VERTICAL);
        resultContent.addView(codesText, new LinearLayout.LayoutParams(-1, -2));
        resultContent.addView(resultText, new LinearLayout.LayoutParams(-1, -2));
        root.addView(cardWithContent(resultContent), cardParams(dp(0), dp(10)));

        TextView safety = new TextView(this);
        safety.setText("Sécurité : DIASCO Auto analyse des rapports. Il n’efface aucun code, ne programme aucun calculateur et ne commande aucun organe du véhicule.");
        safety.setTextSize(13);
        safety.setTextColor(Color.rgb(126, 78, 28));
        safety.setPadding(dp(12), dp(10), dp(12), dp(10));
        MaterialCardView safetyCard = cardWithContent(safety);
        safetyCard.setCardBackgroundColor(Color.rgb(255, 248, 232));
        safetyCard.setStrokeColor(Color.rgb(235, 204, 146));
        root.addView(safetyCard, cardParams(dp(0), dp(0)));

        setContentView(scroll);
    }

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.FRANCE);
            }
        });
    }

    private void showFirstUseNotice() {
        if (profilePreferences.getBoolean(KEY_NOTICE_SEEN, false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Fonctionnement avec ThinkDiag")
                .setMessage("La communication Bluetooth protégée reste gérée par l’application officielle ThinkDiag+. Après le scan, partagez le rapport vers DIASCO Auto. DIASCO l’analyse automatiquement et vous l’explique à la voix. Cette extension est strictement en lecture seule.")
                .setPositiveButton("J’ai compris", (dialog, which) -> profilePreferences.edit()
                        .putBoolean(KEY_NOTICE_SEEN, true)
                        .apply())
                .show();
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = streamUri(intent);
            CharSequence sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (stream != null) {
                importReportUri(stream, "Rapport partagé par ThinkDiag+");
            } else if (sharedText != null && !sharedText.toString().trim().isEmpty()) {
                analyseSharedText(sharedText.toString(), "Texte partagé par ThinkDiag+");
            }
            setIntent(new Intent(this, ThinkDiagActivity.class));
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            importReportUri(intent.getData(), "Rapport ouvert avec DIASCO Auto");
            setIntent(new Intent(this, ThinkDiagActivity.class));
        }
    }

    @SuppressWarnings("deprecation")
    private Uri streamUri(Intent intent) {
        Uri stream;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stream = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        } else {
            stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (stream != null) return stream;
        ClipData clipData = intent.getClipData();
        if (clipData != null && clipData.getItemCount() > 0) {
            return clipData.getItemAt(0).getUri();
        }
        return null;
    }

    private void openThinkDiag() {
        saveProfile();
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(THINKDIAG_PACKAGE);
        if (launchIntent != null) {
            status("ThinkDiag+ ouvert. Faites le scan puis utilisez Partager > DIASCO Auto.");
            startActivity(launchIntent);
            return;
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + THINKDIAG_PACKAGE)));
        } catch (ActivityNotFoundException exception) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + THINKDIAG_PACKAGE)));
            } catch (ActivityNotFoundException secondException) {
                Toast.makeText(this, "ThinkDiag+ n’est pas installé.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openReportPicker() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/pdf", "text/plain", "text/html", "application/xhtml+xml"
                });
        try {
            startActivityForResult(intent, REQUEST_OPEN_REPORT);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, "Aucun gestionnaire de fichiers n’est disponible.", Toast.LENGTH_LONG).show();
        }
    }

    private void pasteReportFromClipboard() {
        if (busy) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "Le presse-papiers est vide.", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Aucun texte de rapport dans le presse-papiers.", Toast.LENGTH_SHORT).show();
            return;
        }
        analyseSharedText(text.toString(), "Rapport collé");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_REPORT || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The temporary grant is enough for immediate analysis.
        }
        importReportUri(uri, "Rapport importé");
    }

    private void importReportUri(Uri uri, String source) {
        if (busy || uri == null) return;
        String mimeType = getContentResolver().getType(uri);
        String value = uri.toString().toLowerCase(Locale.ROOT);
        boolean pdf = "application/pdf".equalsIgnoreCase(mimeType) || value.endsWith(".pdf");
        if (pdf) {
            analysePdf(uri, source);
            return;
        }

        saveProfile();
        setBusy(true);
        status("Lecture du rapport…");
        executor.execute(() -> {
            try {
                String text = ThinkDiagReportLoader.readTextUri(getContentResolver(), uri);
                String prepared = resolveSharedReport(text);
                AnalysisOutcome outcome = analyseReportSynchronously(prepared, source);
                runOnUiThread(() -> applyOutcome(outcome));
            } catch (Exception exception) {
                runOnUiThread(() -> showFailure(exception.getMessage()));
            }
        });
    }

    private void analyseSharedText(String sharedText, String source) {
        if (busy) return;
        saveProfile();
        setBusy(true);
        status("Récupération du rapport ThinkDiag…");
        executor.execute(() -> {
            try {
                String prepared = resolveSharedReport(sharedText);
                AnalysisOutcome outcome = analyseReportSynchronously(prepared, source);
                runOnUiThread(() -> applyOutcome(outcome));
            } catch (Exception exception) {
                runOnUiThread(() -> showFailure(exception.getMessage()));
            }
        });
    }

    private String resolveSharedReport(String sharedText) throws IOException {
        String normalized = ObdReportTools.normalize(sharedText);
        String reportUrl = ObdReportTools.extractFirstAllowedThinkCarUrl(normalized);
        if (reportUrl == null) {
            if (normalized.isEmpty()) throw new IOException("Le rapport partagé est vide.");
            return normalized;
        }

        statusFromWorker("Téléchargement sécurisé du rapport ThinkCar…");
        String downloaded = ThinkDiagReportLoader.fetchThinkCarReport(reportUrl);
        if (downloaded.isEmpty()) throw new IOException("Le rapport ThinkCar téléchargé est vide.");
        if (normalized.length() > reportUrl.length() + 30) {
            return downloaded + "\n\nInformations accompagnant le partage :\n" + normalized;
        }
        return downloaded;
    }

    private void analysePdf(Uri uri, String source) {
        if (busy) return;
        saveProfile();
        if (!isOnlineModeEnabled()) {
            showFailure("Le mode en ligne est nécessaire pour lire un rapport PDF.");
            return;
        }

        setBusy(true);
        status("Ouverture du rapport PDF…");
        executor.execute(() -> {
            try (ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r")) {
                if (descriptor == null) throw new IOException("Impossible d’ouvrir le PDF.");
                try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                    int pageCount = renderer.getPageCount();
                    if (pageCount == 0) throw new IOException("Le PDF ne contient aucune page.");
                    int pagesToRead = Math.min(pageCount, MAX_PDF_PAGES);
                    StringBuilder extracted = new StringBuilder();
                    RemoteAiClient client = new RemoteAiClient();
                    String endpoint = currentEndpoint();

                    for (int index = 0; index < pagesToRead; index++) {
                        int displayedPage = index + 1;
                        statusFromWorker("Lecture du PDF : page " + displayedPage + " sur " + pagesToRead + "…");
                        try (PdfRenderer.Page page = renderer.openPage(index)) {
                            Bitmap bitmap = renderPage(page);
                            String base64 = bitmapToBase64(bitmap);
                            bitmap.recycle();
                            String pagePrompt = "Lis cette page " + displayedPage + " sur " + pageCount
                                    + " d’un rapport automobile ThinkDiag. Transcris fidèlement le VIN, le kilométrage, "
                                    + "les calculateurs, les statuts et tous les codes défaut visibles. Ne propose pas encore de réparation.";
                            String pageText = client.describeImage(
                                    endpoint,
                                    base64,
                                    "image/jpeg",
                                    pagePrompt,
                                    Locale.getDefault().toLanguageTag(),
                                    "",
                                    isDebugBuild()
                            );
                            extracted.append("PAGE ").append(displayedPage).append(" :\n")
                                    .append(pageText).append("\n\n");
                        }
                    }
                    if (pageCount > pagesToRead) {
                        extracted.append("Le PDF contient ").append(pageCount)
                                .append(" pages. Les ").append(pagesToRead)
                                .append(" premières pages ont été lues automatiquement.\n");
                    }
                    AnalysisOutcome outcome = analyseReportSynchronously(extracted.toString(), source + " (PDF)");
                    runOnUiThread(() -> applyOutcome(outcome));
                }
            } catch (Exception exception) {
                runOnUiThread(() -> showFailure(exception.getMessage()));
            }
        });
    }

    private Bitmap renderPage(PdfRenderer.Page page) {
        float scale = Math.min(2.2f, MAX_RENDER_WIDTH / (float) Math.max(1, page.getWidth()));
        int width = Math.max(1, Math.round(page.getWidth() * scale));
        int height = Math.max(1, Math.round(page.getHeight() * scale));
        if (height > MAX_RENDER_HEIGHT) {
            scale *= MAX_RENDER_HEIGHT / (float) height;
            width = Math.max(1, Math.round(page.getWidth() * scale));
            height = Math.max(1, Math.round(page.getHeight() * scale));
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    private String bitmapToBase64(Bitmap bitmap) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) {
            throw new IOException("Impossible de préparer une page du PDF.");
        }
        return android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP);
    }

    private AnalysisOutcome analyseReportSynchronously(String reportText, String source) throws IOException {
        String normalized = ObdReportTools.normalize(reportText);
        if (normalized.length() < 12) throw new IOException("Le rapport ne contient pas assez d’informations.");

        List<String> codes = ObdReportTools.extractCodes(normalized);
        String severity = ObdReportTools.detectSeverity(normalized, codes);
        String answer;

        if (!isOnlineModeEnabled()) {
            answer = "Analyse locale uniquement. Niveau : " + severity + ". "
                    + ObdReportTools.codesLabel(codes)
                    + " Activez le mode en ligne pour obtenir l’explication détaillée des causes et des contrôles.";
        } else {
            statusFromWorker("DIASCO analyse les défauts…");
            String prompt = ObdReportTools.buildAnalysisPrompt(
                    makeInput.getText().toString(),
                    modelInput.getText().toString(),
                    yearInput.getText().toString(),
                    fuelInput.getText().toString(),
                    normalized,
                    codes
            );
            if (prompt.length() > MAX_BACKEND_QUESTION_CHARS) {
                prompt = prompt.substring(0, MAX_BACKEND_QUESTION_CHARS);
            }
            answer = new RemoteAiClient().ask(
                    currentEndpoint(),
                    prompt,
                    Locale.getDefault().toLanguageTag(),
                    "",
                    isDebugBuild()
            );
        }
        return new AnalysisOutcome(ObdReportTools.normalize(answer), severity, codes, source);
    }

    private void applyOutcome(AnalysisOutcome outcome) {
        setBusy(false);
        String codeLabel = ObdReportTools.codesLabel(outcome.codes);
        codesText.setText("Niveau : " + outcome.severity + " · " + codeLabel);
        resultText.setText(outcome.answer);
        status("Analyse terminée · " + outcome.source);

        String vehicle = vehicleLabel();
        ObdHistoryStore.add(this, vehicle, outcome.severity, codeLabel, outcome.answer);
        if (textToSpeech != null) {
            textToSpeech.speak(
                    ObdReportTools.voiceSummary(outcome.answer, outcome.severity, outcome.codes),
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "diasco-obd-result"
            );
        }
    }

    private void showFailure(String message) {
        setBusy(false);
        String clean = message == null || message.trim().isEmpty()
                ? "Le rapport n’a pas pu être analysé."
                : message.trim();
        status("Erreur : " + clean);
        Toast.makeText(this, clean, Toast.LENGTH_LONG).show();
    }

    private void showHistory() {
        List<ObdHistoryStore.Entry> entries = ObdHistoryStore.load(this);
        if (entries.isEmpty()) {
            Toast.makeText(this, "Aucun diagnostic enregistré.", Toast.LENGTH_SHORT).show();
            return;
        }

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(8), dp(12), dp(8));
        DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.FRANCE);

        for (ObdHistoryStore.Entry entry : entries) {
            TextView item = new TextView(this);
            String date = entry.timestamp > 0 ? formatter.format(new Date(entry.timestamp)) : "Date inconnue";
            String answer = entry.answer.length() > 750 ? entry.answer.substring(0, 750) + "…" : entry.answer;
            item.setText(date + "\n" + entry.vehicle + "\nNiveau : " + entry.severity
                    + "\n" + entry.codes + "\n\n" + answer);
            item.setTextSize(14);
            item.setTextColor(Color.rgb(23, 33, 38));
            item.setTextIsSelectable(true);
            item.setPadding(dp(12), dp(10), dp(12), dp(10));
            item.setBackground(roundedBackground(Color.WHITE, Color.rgb(220, 228, 225), 8));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = dp(10);
            content.addView(item, params);
        }
        scrollView.addView(content);

        new AlertDialog.Builder(this)
                .setTitle("Historique DIASCO Auto")
                .setView(scrollView)
                .setPositiveButton("Fermer", null)
                .setNegativeButton("Effacer l’historique", (dialog, which) -> {
                    ObdHistoryStore.clear(this);
                    Toast.makeText(this, "Historique effacé.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveProfile() {
        profilePreferences.edit()
                .putString(KEY_MAKE, makeInput.getText().toString().trim())
                .putString(KEY_MODEL, modelInput.getText().toString().trim())
                .putString(KEY_YEAR, yearInput.getText().toString().trim())
                .putString(KEY_FUEL, fuelInput.getText().toString().trim())
                .apply();
    }

    private String vehicleLabel() {
        return ObdReportTools.vehicleLabel(
                makeInput.getText().toString(),
                modelInput.getText().toString(),
                yearInput.getText().toString(),
                fuelInput.getText().toString()
        );
    }

    private String currentEndpoint() {
        String endpoint = getString(R.string.default_backend_endpoint).trim();
        if (endpoint == null) return "";
        String clean = endpoint.trim();
        if (clean.endsWith("/api/askq")) clean = clean.substring(0, clean.length() - 1);
        if (clean.endsWith("/api/ask/")) clean = clean.substring(0, clean.length() - 1);
        return clean;
    }

    private boolean isOnlineModeEnabled() {
        return true;
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private void setBusy(boolean value) {
        busy = value;
        openThinkDiagButton.setEnabled(!value);
        importButton.setEnabled(!value);
        pasteButton.setEnabled(!value);
        historyButton.setEnabled(!value);
        makeInput.setEnabled(!value);
        modelInput.setEnabled(!value);
        yearInput.setEnabled(!value);
        fuelInput.setEnabled(!value);
    }

    private void status(String message) {
        statusText.setText(message == null || message.trim().isEmpty() ? "Prêt" : message.trim());
    }

    private void statusFromWorker(String message) {
        runOnUiThread(() -> status(message));
    }

    private EditText profileInput(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextSize(15);
        input.setTextColor(Color.rgb(23, 33, 38));
        input.setHintTextColor(Color.rgb(128, 143, 138));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setBackground(roundedBackground(Color.rgb(248, 250, 249), Color.rgb(220, 228, 225), 8));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        return input;
    }

    private LinearLayout.LayoutParams weightedInputParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        if (first) params.rightMargin = dp(6);
        params.topMargin = dp(6);
        return params;
    }

    private TextView sectionTitle(String value) {
        TextView title = new TextView(this);
        title.setText(value);
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(23, 33, 38));
        return title;
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        params.bottomMargin = dp(6);
        return params;
    }

    private MaterialCardView cardWithContent(View content) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(8));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(Color.rgb(220, 228, 225));
        card.setCardBackgroundColor(Color.WHITE);
        card.addView(content);
        return card;
    }

    private LinearLayout.LayoutParams cardParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = top;
        params.bottomMargin = bottom;
        return params;
    }

    private MaterialButton actionButton(String label, int background, int foreground) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setCornerRadius(dp(8));
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams fullButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.bottomMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams rowButtonParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        if (first) params.rightMargin = dp(6);
        params.bottomMargin = dp(8);
        return params;
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
