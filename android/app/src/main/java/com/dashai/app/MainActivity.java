package com.dashai.app;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dashai.app.ai.AiRepository;
import com.dashai.app.ai.RemoteAiClient;
import com.dashai.app.util.ConversationMemory;
import com.dashai.app.util.TextUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO = 42;
    private static final int REQUEST_IMAGE_CAPTURE = 43;
    private static final int REQUEST_SAVE_IMAGE = 44;
    private static final String PREFS = "dashai_prefs";
    private static final String KEY_ENDPOINT = "backend_endpoint";
    private static final String KEY_ONLINE = "online_enabled";
    private static final String KEY_PRIVACY_NOTICE_ACCEPTED = "privacy_notice_accepted";
    private static final int MAX_HISTORY_LINES = 18;
    private static final long AUDIO_ERROR_CHAT_COOLDOWN_MS = 15_000L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AiRepository aiRepository = new AiRepository();
    private final ConcurrentHashMap<String, Runnable> speechCallbacks = new ConcurrentHashMap<>();

    private SharedPreferences preferences;
    private EditText endpointInput;
    private MaterialSwitch onlineSwitch;
    private LinearLayout chatContainer;
    private TextView statusText;
    private View statusDot;
    private EditText questionInput;
    private MaterialButton askButton;
    private MaterialButton micButton;
    private MaterialButton cameraButton;
    private MaterialButton testButton;
    private MaterialButton clearButton;
    private ScrollView scrollView;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean busy;
    private boolean voiceListening;
    private boolean speechPartialHandled;
    private boolean manualQuestionAutoRetryUsed;
    private String pendingQuestionText;
    private int pendingQuestionVersion;
    private int speechCounter;
    private long lastAudioChatErrorAt;
    private String pendingVisionPrompt;
    private byte[] pendingImageBytes;
    private String pendingImageMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        ensureProductionDefaults();
        buildUi();
        initTextToSpeech();
        renderStoredConversation();
        showPrivacyNoticeIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        statusReady();
    }

    private void buildUi() {
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        int pagePadding = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(243, 246, 245));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(pagePadding, dp(12), pagePadding, dp(8));

        MaterialCardView logoCard = new MaterialCardView(this);
        logoCard.setRadius(dp(8));
        logoCard.setCardElevation(0);
        logoCard.setStrokeWidth(dp(1));
        logoCard.setStrokeColor(Color.rgb(220, 228, 225));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logoCard.addView(logo, new FrameLayout.LayoutParams(-1, -1));
        header.addView(logoCard, new LinearLayout.LayoutParams(dp(52), dp(52)));

        LinearLayout identity = new LinearLayout(this);
        identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText("DIASCO");
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(23, 33, 38));
        identity.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Assistant vocal, visuel et créatif");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.rgb(92, 107, 102));
        identity.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        header.addView(identity, new LinearLayout.LayoutParams(0, -2, 1f));

        clearButton = createIconButton(R.drawable.ic_delete, "Effacer la conversation", false);
        clearButton.setOnClickListener(view -> clearConversation());
        header.addView(clearButton, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        HorizontalScrollView actionScroller = new HorizontalScrollView(this);
        actionScroller.setHorizontalScrollBarEnabled(false);
        actionScroller.setClipToPadding(false);
        actionScroller.setPadding(pagePadding, dp(4), pagePadding, dp(10));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createQuickAction("Image", R.drawable.ic_sparkles,
                () -> setQuestionTemplate("Crée une image de ")));
        actions.addView(createQuickAction("Caméra", R.drawable.ic_camera, this::startCameraDescription));
        actions.addView(createQuickAction("Code", R.drawable.ic_code,
                () -> setQuestionTemplate("Écris le code pour ")));
        actions.addView(createQuickAction("Site", R.drawable.ic_globe,
                () -> setQuestionTemplate("Crée un site internet pour ")));
        actionScroller.addView(actions, new HorizontalScrollView.LayoutParams(-2, -2));
        root.addView(actionScroller, new LinearLayout.LayoutParams(-1, -2));

        endpointInput = new EditText(this);
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setHint("URL backend : https://ton-domaine.com/api/ask");
        endpointInput.setText(developerBackendEndpoint());
        endpointInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        endpointInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveSettings();
                endpointInput.clearFocus();
                status("URL backend sauvegardée.");
                return true;
            }
            return false;
        });
        endpointInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveSettings();
        });

        onlineSwitch = new MaterialSwitch(this);
        onlineSwitch.setText("Mode en ligne");
        onlineSwitch.setChecked(isOnlineModeEnabled());
        onlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isOnlineModeForced() && !isChecked) {
                onlineSwitch.setChecked(true);
                status("Mode en ligne activé automatiquement.");
                return;
            }
            saveSettings();
            status(isChecked ? "Mode en ligne activé." : "Mode hors ligne activé.");
        });

        testButton = new MaterialButton(this);
        testButton.setText("Tester");
        testButton.setCornerRadius(dp(8));
        testButton.setOnClickListener(view -> testBackend());
        if (showDeveloperControls()) {
            MaterialCardView debugCard = new MaterialCardView(this);
            debugCard.setRadius(dp(8));
            debugCard.setCardElevation(0);
            debugCard.setStrokeColor(Color.rgb(220, 228, 225));
            debugCard.setStrokeWidth(dp(1));
            debugCard.setCardBackgroundColor(Color.WHITE);
            LinearLayout debugContent = new LinearLayout(this);
            debugContent.setOrientation(LinearLayout.VERTICAL);
            debugContent.setPadding(dp(12), dp(8), dp(12), dp(8));
            debugContent.addView(endpointInput, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout debugRow = new LinearLayout(this);
            debugRow.setGravity(Gravity.CENTER_VERTICAL);
            debugRow.addView(onlineSwitch, new LinearLayout.LayoutParams(0, -2, 1f));
            debugRow.addView(testButton, new LinearLayout.LayoutParams(-2, dp(44)));
            debugContent.addView(debugRow, new LinearLayout.LayoutParams(-1, -2));
            debugCard.addView(debugContent);
            LinearLayout.LayoutParams debugParams = new LinearLayout.LayoutParams(-1, -2);
            debugParams.setMargins(pagePadding, 0, pagePadding, dp(10));
            root.addView(debugCard, debugParams);
        }

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(pagePadding, dp(2), pagePadding, dp(8));
        statusDot = new View(this);
        statusDot.setBackground(circleBackground(Color.rgb(0, 143, 114)));
        statusRow.addView(statusDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        statusText = new TextView(this);
        statusText.setText("Prêt");
        statusText.setTextSize(12);
        statusText.setTextColor(Color.rgb(92, 107, 102));
        statusText.setPadding(dp(8), 0, 0, 0);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(statusRow, new LinearLayout.LayoutParams(-1, -2));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(pagePadding, dp(4), pagePadding, dp(18));
        chatContainer.setBackgroundColor(Color.TRANSPARENT);
        scrollView.addView(chatContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setPadding(pagePadding, dp(9), pagePadding, dp(12));
        composer.setBackgroundColor(Color.WHITE);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);

        questionInput = new EditText(this);
        questionInput.setSingleLine(false);
        questionInput.setMinLines(1);
        questionInput.setMaxLines(4);
        questionInput.setTextSize(16);
        questionInput.setTextColor(Color.rgb(23, 33, 38));
        questionInput.setHintTextColor(Color.rgb(128, 143, 138));
        questionInput.setHint("Écrivez à DIASCO…");
        questionInput.setPadding(dp(14), dp(11), dp(14), dp(11));
        questionInput.setBackground(roundedBackground(Color.rgb(248, 250, 249), Color.rgb(220, 228, 225), 8));
        questionInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        questionInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                askCurrentQuestion();
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, -2, 1f);
        inputParams.rightMargin = dp(6);
        inputRow.addView(questionInput, inputParams);

        cameraButton = createIconButton(R.drawable.ic_camera, "Décrire avec la caméra", false);
        cameraButton.setOnClickListener(view -> startCameraDescription());
        inputRow.addView(cameraButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        micButton = createIconButton(R.drawable.ic_mic, "Poser une question à la voix", false);
        micButton.setOnClickListener(view -> startVoiceInput());
        inputRow.addView(micButton, new LinearLayout.LayoutParams(dp(48), dp(48)));

        askButton = createIconButton(R.drawable.ic_send, "Envoyer", true);
        askButton.setOnClickListener(view -> askCurrentQuestion());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        sendParams.leftMargin = dp(2);
        inputRow.addView(askButton, sendParams);
        composer.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        updateControlsState();
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.FRANCE);
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                runSpeechCallback(utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                runSpeechCallback(utteranceId);
            }
        });
    }

    private void showPrivacyNoticeIfNeeded() {
        if (preferences.getBoolean(KEY_PRIVACY_NOTICE_ACCEPTED, false)) return;
        new AlertDialog.Builder(this)
                .setTitle("Confidentialité")
                .setMessage("DIASCO peut envoyer vos questions, vos demandes créatives et les photos que vous choisissez d’analyser vers son backend IA sécurisé. La mémoire de conversation reste stockée sur cet appareil.")
                .setPositiveButton("J’ai compris", (dialog, which) -> preferences.edit()
                        .putBoolean(KEY_PRIVACY_NOTICE_ACCEPTED, true)
                        .apply())
                .show();
    }

    private void renderStoredConversation() {
        List<ConversationMemory.Entry> entries = ConversationMemory.load(this);
        if (entries.isEmpty()) {
            appendAssistant("Bonjour, je suis DIASCO. Je vous écoute.");
            return;
        }
        for (ConversationMemory.Entry entry : entries) {
            append(entry.author, entry.text, looksLikeTechnicalContent(entry.text));
        }
    }

    private void ensureProductionDefaults() {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_ONLINE, true)
                .remove("wake_enabled")
                .remove("owner_phrase")
                .remove("voice_profile");
        editor.apply();
    }

    private void askCurrentQuestion() {
        String question = questionInput.getText().toString().trim();
        if (question.isEmpty()) return;
        questionInput.setText("");
        ask(question);
    }

    private void ask(String question) {
        saveSettings();
        appendUser(question);

        String commandAnswer = handleModeCommand(question);
        if (commandAnswer != null) {
            appendAssistant(commandAnswer);
            speak(commandAnswer);
            return;
        }

        if (isWebsiteRequest(question)) {
            generateRequestedWebsite(question);
            return;
        }

        if (isImageRequest(question)) {
            generateRequestedImage(question);
            return;
        }

        boolean onlineEnabled = isOnlineModeEnabled();
        String endpoint = currentEndpoint();
        String historyForAi = buildHistoryForAi();
        boolean technicalRequest = isTechnicalRequest(question);
        String questionForAi = buildQuestionForAi(question, historyForAi);
        boolean forceRemote = looksLikeFollowUp(question) && !historyForAi.isEmpty();

        setBusy(true);
        status("IA : réflexion en cours…");
        executor.execute(() -> {
            String answer = aiRepository.answer(
                    MainActivity.this,
                    question,
                    questionForAi,
                    onlineEnabled,
                    endpoint,
                    historyForAi,
                    forceRemote,
                    isDebugBuild()
            );
            runOnUiThread(() -> {
                setBusy(false);
                statusReady();
                String cleanAnswer = cleanAssistantText(answer);
                appendAssistant(cleanAnswer, technicalRequest || looksLikeTechnicalContent(cleanAnswer));
                rememberTurn(question, cleanAnswer);
                speak(speechSummaryForAnswer(question, cleanAnswer));
            });
        });
    }

    private String handleModeCommand(String question) {
        String clean = TextUtils.normalizeForIntent(question);
        if (clean.contains("mode en ligne") || clean.contains("active le mode en ligne") || clean.contains("activer le mode en ligne")) {
            onlineSwitch.setChecked(true);
            saveSettings();
            return "Mode en ligne activé automatiquement.";
        }
        if (clean.contains("mode hors ligne") || clean.contains("desactive le mode en ligne") || clean.contains("désactive le mode en ligne")) {
            if (isOnlineModeForced()) {
                onlineSwitch.setChecked(true);
                saveSettings();
                return "Le mode en ligne reste activé automatiquement pour DIASCO.";
            }
            onlineSwitch.setChecked(false);
            saveSettings();
            return "Mode hors ligne activé. Je répondrai seulement avec les capacités locales.";
        }
        return null;
    }

    private boolean isImageRequest(String question) {
        String clean = TextUtils.normalizeForIntent(question);
        if (clean.isEmpty()) return false;
        boolean asksForVisual = clean.contains("image")
                || clean.contains("photo")
                || clean.contains("dessin")
                || clean.contains("illustration")
                || clean.contains("logo")
                || clean.contains("avatar")
                || clean.contains("poster")
                || clean.contains("banniere");
        boolean startsLikeCommand = clean.startsWith("affiche")
                || clean.startsWith("montre")
                || clean.startsWith("genere")
                || clean.startsWith("cree")
                || clean.startsWith("dessine")
                || clean.startsWith("fais")
                || clean.startsWith("fait")
                || clean.startsWith("produis")
                || clean.startsWith("image de")
                || clean.startsWith("une image de");
        return asksForVisual && startsLikeCommand;
    }

    private boolean isWebsiteRequest(String question) {
        String clean = TextUtils.normalizeForIntent(question);
        if (clean.isEmpty()) return false;
        boolean website = clean.contains("site internet")
                || clean.contains("site web")
                || clean.contains("page web")
                || clean.contains("landing page")
                || clean.contains("portfolio web")
                || clean.contains("boutique en ligne");
        boolean creation = clean.startsWith("cree")
                || clean.startsWith("genere")
                || clean.startsWith("fais")
                || clean.startsWith("construis")
                || clean.startsWith("developpe")
                || clean.startsWith("code");
        return website && creation;
    }

    private boolean isTechnicalRequest(String question) {
        String clean = TextUtils.normalizeForIntent(question);
        if (clean.isEmpty()) return false;
        return clean.contains("code")
                || clean.contains("programme")
                || clean.contains("script")
                || clean.contains("algorithme")
                || clean.contains("html")
                || clean.contains("css")
                || clean.contains("javascript")
                || clean.contains("java")
                || clean.contains("kotlin")
                || clean.contains("python")
                || clean.contains("php")
                || clean.contains("sql")
                || clean.contains("formule")
                || clean.contains("equation")
                || clean.contains("math")
                || clean.contains("excel")
                || clean.contains("physique")
                || clean.contains("chimie")
                || clean.startsWith("calcule")
                || clean.startsWith("resous")
                || clean.startsWith("ecris une fonction")
                || clean.startsWith("ecris un programme")
                || clean.startsWith("corrige ce code");
    }

    private void generateRequestedImage(String prompt) {
        if (!isOnlineModeEnabled()) {
            appendAssistant("Le mode en ligne doit être actif pour générer et afficher une image.");
            statusReady();
            return;
        }

        saveSettings();
        String endpoint = currentEndpoint();
        setBusy(true);
        status("Image : génération en cours…");

        executor.execute(() -> {
            RemoteAiClient client = new RemoteAiClient();
            RemoteAiClient.ImageResult result = client.generateImage(
                    endpoint,
                    prompt,
                    Locale.getDefault().toLanguageTag(),
                    isDebugBuild()
            );
            runOnUiThread(() -> {
                setBusy(false);
                statusReady();
                String cleanMessage = cleanAssistantText(result.message);
                appendAssistant(cleanMessage);
                if (result.hasImage()) {
                    appendImage(result.imageBase64, result.mimeType);
                }
                rememberTurn(prompt, cleanMessage);
                speak(cleanMessage);
            });
        });
    }

    private void generateRequestedWebsite(String prompt) {
        if (!isOnlineModeEnabled()) {
            appendAssistant("Le mode en ligne doit être actif pour créer un site.");
            statusReady();
            return;
        }

        saveSettings();
        String endpoint = currentEndpoint();
        String history = buildHistoryForAi();
        setBusy(true);
        status("Site : création en cours…");

        executor.execute(() -> {
            RemoteAiClient.SiteResult result = new RemoteAiClient().generateWebsite(
                    endpoint,
                    prompt,
                    Locale.getDefault().toLanguageTag(),
                    history,
                    isDebugBuild()
            );
            runOnUiThread(() -> {
                setBusy(false);
                String message = cleanAssistantText(result.message);
                appendAssistant(message);
                rememberTurn(prompt, message);
                if (result.hasSite()) {
                    openWebsitePreview(result.title, result.html);
                    status("Site prêt à prévisualiser.");
                    speak("Le site est prêt. Je l’ai ouvert dans l’aperçu.");
                } else {
                    statusReady();
                    speak(message);
                }
            });
        });
    }

    private void openWebsitePreview(String title, String html) {
        File file = new File(getCacheDir(), "diasco-generated-site.html");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException exception) {
            appendAssistant("Le site a été créé, mais son aperçu n’a pas pu être préparé.");
            return;
        }
        Intent intent = new Intent(this, SitePreviewActivity.class)
                .putExtra(SitePreviewActivity.EXTRA_FILE_PATH, file.getAbsolutePath())
                .putExtra(SitePreviewActivity.EXTRA_SITE_TITLE, title);
        startActivity(intent);
    }


    private boolean looksLikeFollowUp(String question) {
        String clean = TextUtils.normalizeForIntent(question);
        if (clean.isEmpty()) return false;
        if (clean.length() <= 4 && (clean.equals("oui") || clean.equals("ok") || clean.equals("daccord") || clean.equals("d accord"))) {
            return true;
        }
        return clean.equals("oui dis moi")
                || clean.equals("oui dis le moi")
                || clean.equals("oui dis-le-moi")
                || clean.equals("dis moi")
                || clean.equals("dis le moi")
                || clean.equals("dis-le-moi")
                || clean.equals("continue")
                || clean.equals("vas y")
                || clean.equals("explique")
                || clean.equals("explique moi")
                || clean.equals("depuis quand")
                || clean.equals("et depuis quand")
                || clean.equals("son parcours")
                || clean.equals("donne son parcours")
                || clean.equals("la prochaine election")
                || clean.equals("date de la prochaine election")
                || clean.startsWith("depuis quand ")
                || clean.startsWith("et lui")
                || clean.startsWith("et elle")
                || clean.startsWith("pourquoi ") && clean.length() < 80;
    }

    private String buildQuestionForAi(String question, String historyForAi) {
        String questionForAi = question;
        if (looksLikeFollowUp(question) && historyForAi != null && !historyForAi.trim().isEmpty()) {
            questionForAi = "L'utilisateur fait une relance courte qui dépend du contexte. "
                    + "Utilise le contexte récent ci-dessous pour répondre directement, sans demander de préciser si le contexte suffit.\n\n"
                    + "Contexte récent :\n" + historyForAi.trim() + "\n\n"
                    + "Relance actuelle de l'utilisateur : " + question;
        }

        if (!isTechnicalRequest(question)) {
            return questionForAi;
        }

        return questionForAi + "\n\n"
                + "Instruction d'affichage DIASCO : la demande concerne du code, une formule ou un contenu technique. "
                + "Réponds de façon précise et exploitable à l'écran. "
                + "Pour le code, conserve l'indentation et les retours à la ligne, mais n'encadre pas le code avec des balises Markdown ```."
                + "Pour les formules, écris la formule clairement sur une ligne séparée, puis explique les variables brièvement.";
    }

    private void testBackend() {
        saveSettings();
        String endpoint = currentEndpoint();

        setBusy(true);
        status("Test du backend…");
        executor.execute(() -> {
            RemoteAiClient client = new RemoteAiClient();
            String answer = client.ask(
                    endpoint,
                    "Réponds uniquement par OK si tu reçois cette demande.",
                    "fr-FR",
                    "",
                    isDebugBuild()
            );
            runOnUiThread(() -> {
                setBusy(false);
                statusReady();
                appendAssistant("Test backend : " + answer);
            });
        });
    }

    private void startCameraDescription() {
        if (busy || voiceListening) return;
        if (!isOnlineModeEnabled()) {
            appendAssistant("Le mode en ligne doit être actif pour décrire ce que voit la caméra.");
            statusReady();
            return;
        }

        saveSettings();
        pendingVisionPrompt = questionInput.getText().toString().trim();
        if (pendingVisionPrompt.isEmpty()) {
            pendingVisionPrompt = "Décris clairement ce que tu vois avec la caméra.";
        }
        questionInput.setText("");

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            status("Caméra : capture en cours…");
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE);
        } catch (ActivityNotFoundException ex) {
            statusReady();
            appendAssistant("Aucune application caméra n’est disponible sur cet appareil.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_IMAGE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingImageBytes != null) {
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new IOException("Flux de sortie indisponible");
                    output.write(pendingImageBytes);
                    Toast.makeText(this, "Image enregistrée.", Toast.LENGTH_LONG).show();
                } catch (IOException exception) {
                    Toast.makeText(this, "Impossible d’enregistrer l’image.", Toast.LENGTH_LONG).show();
                }
            }
            pendingImageBytes = null;
            pendingImageMimeType = null;
            return;
        }
        if (requestCode != REQUEST_IMAGE_CAPTURE) return;

        if (resultCode != RESULT_OK) {
            statusReady();
            return;
        }

        Bitmap bitmap = null;
        if (data != null && data.getExtras() != null) {
            Object rawBitmap = data.getExtras().get("data");
            if (rawBitmap instanceof Bitmap) {
                bitmap = (Bitmap) rawBitmap;
            }
        }

        if (bitmap == null) {
            statusReady();
            appendAssistant("Je n’ai pas reçu d’image exploitable depuis la caméra.");
            return;
        }

        describeImage(bitmap);
    }

    private void describeImage(Bitmap bitmap) {
        saveSettings();
        String endpoint = currentEndpoint();
        String prompt = pendingVisionPrompt == null || pendingVisionPrompt.trim().isEmpty()
                ? "Décris clairement ce que tu vois avec la caméra."
                : pendingVisionPrompt.trim();
        pendingVisionPrompt = null;
        String historyForAi = buildHistoryForAi();

        appendUser(prompt + " (photo)");
        setBusy(true);
        status("Image : analyse en cours…");

        executor.execute(() -> {
            String imageBase64 = encodeBitmapAsJpegBase64(bitmap);
            RemoteAiClient client = new RemoteAiClient();
            String answer = client.describeImage(
                    endpoint,
                    imageBase64,
                    "image/jpeg",
                    prompt,
                    Locale.getDefault().toLanguageTag(),
                    historyForAi,
                    isDebugBuild()
            );
            runOnUiThread(() -> {
                setBusy(false);
                statusReady();
                String cleanAnswer = cleanAssistantText(answer);
                appendAssistant(cleanAnswer);
                rememberTurn("Photo : " + prompt, cleanAnswer);
                speak(cleanAnswer);
            });
        });
    }

    private String encodeBitmapAsJpegBase64(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output);
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private void startVoiceInput() {
        if (busy || voiceListening) return;
        manualQuestionAutoRetryUsed = false;
        startSpeechRecognition();
    }

    private void startSpeechRecognition() {
        if (busy || voiceListening) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            appendAssistant("La reconnaissance vocale Android n’est pas disponible sur cet appareil.");
            statusReady();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        speechPartialHandled = false;
        pendingQuestionText = null;
        pendingQuestionVersion++;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { status("Micro : écoute longue, pose ta question…"); }
            @Override public void onBeginningOfSpeech() { status("Micro : parole détectée…"); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { status("Micro : traitement audio…"); }
            @Override public void onPartialResults(Bundle partialResults) {
                handleSpeechPartial(partialResults);
            }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onError(int error) {
                if (speechPartialHandled) return;
                handleSpeechError(error);
            }

            @Override
            public void onResults(Bundle results) {
                if (speechPartialHandled) return;
                setVoiceState(false, "Micro : prêt.");
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    handleEmptySpeechResult();
                    return;
                }
                handleSpeechMatches(matches);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 12_000);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_500);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Posez votre question à DIASCO");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        setVoiceState(true, "Micro : écoute longue, pose ta question…");
        try {
            speechRecognizer.startListening(intent);
        } catch (RuntimeException exception) {
            stopSpeechRecognizer();
            status("Micro indisponible. Réessaie.");
        }
    }

    private void handleSpeechError(int error) {
        setVoiceState(false, "Micro : prêt.");
        if (pendingQuestionText != null && !pendingQuestionText.trim().isEmpty()) {
            submitQuestionFromPartial(pendingQuestionVersion);
            return;
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            handleEmptySpeechResult();
            return;
        }
        status("Erreur audio. Réessaie ou écris la question.");
        appendAudioErrorOnce("Je n’ai pas compris l’audio. Réessaie ou écris la question.");
    }

    private void handleSpeechPartial(Bundle partialResults) {
        if (speechPartialHandled || partialResults == null) return;
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;
        String heard = firstNonEmpty(matches);
        if (heard != null && heard.trim().length() >= 2) {
            pendingQuestionText = heard.trim();
            int version = ++pendingQuestionVersion;
            status("Question entendue : « " + limitForStatus(pendingQuestionText) + " ».");
            scrollView.postDelayed(() -> submitQuestionFromPartial(version), 2_200);
        }
    }

    private void handleSpeechMatches(ArrayList<String> matches) {
        String first = firstNonEmpty(matches);
        if (first == null) {
            handleEmptySpeechResult();
            return;
        }
        pendingQuestionVersion++;
        pendingQuestionText = null;
        ask(first);
    }

    private void submitQuestionFromPartial(int version) {
        if (speechPartialHandled) return;
        if (version != pendingQuestionVersion) return;
        String question = pendingQuestionText == null ? "" : pendingQuestionText.trim();
        if (question.length() < 2) return;

        speechPartialHandled = true;
        stopSpeechRecognizer();
        status("Question reçue : « " + limitForStatus(question) + " ».");
        ask(question);
    }

    private String firstNonEmpty(ArrayList<String> matches) {
        for (String match : matches) {
            if (match != null && !match.trim().isEmpty()) return match.trim();
        }
        return null;
    }

    private String limitForStatus(String text) {
        String clean = text == null ? "" : text.trim();
        return clean.length() > 34 ? clean.substring(0, 34) + "…" : clean;
    }

    private void handleEmptySpeechResult() {
        if (!manualQuestionAutoRetryUsed) {
            manualQuestionAutoRetryUsed = true;
            status("Micro : je n’ai rien entendu, je relance l’écoute…");
            scrollView.postDelayed(() -> {
                if (!busy && !voiceListening) startSpeechRecognition();
            }, 700);
        } else {
            status("Micro : aucune question détectée.");
        }
    }

    private void stopSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        voiceListening = false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput();
        } else if (requestCode == REQUEST_RECORD_AUDIO) {
            statusReady();
        }
    }

    private void appendUser(String text) {
        append("Vous", text);
    }

    private void appendAssistant(String text) {
        String clean = cleanAssistantText(text);
        append("DIASCO", clean, looksLikeTechnicalContent(clean));
    }

    private void appendAssistant(String text, boolean technical) {
        append("DIASCO", cleanAssistantText(text), technical);
    }

    private void appendAudioErrorOnce(String text) {
        long now = System.currentTimeMillis();
        if (now - lastAudioChatErrorAt < AUDIO_ERROR_CHAT_COOLDOWN_MS) return;
        lastAudioChatErrorAt = now;
        appendAssistant(text);
    }

    private void clearConversation() {
        ConversationMemory.clear(this);
        chatContainer.removeAllViews();
        appendAssistant("Nouvelle conversation. Je vous écoute.");
        statusReady();
    }

    private String cleanAssistantText(String text) {
        if (text == null) return "";
        String cleaned = text
                .replace("**", "")
                .replace("__", "")
                .replaceAll("(?m)^\\s*```[a-zA-Z0-9_-]*\\s*$", "")
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .trim();
        if (!looksLikeTechnicalContent(cleaned)) {
            cleaned = cleaned.replace("`", "");
        }
        return cleaned.trim();
    }

    private void rememberTurn(String userQuestion, String assistantAnswer) {
        ConversationMemory.appendTurn(this, userQuestion, assistantAnswer);
    }

    private String buildHistoryForAi() {
        return ConversationMemory.buildHistory(this);
    }

    private void append(String author, String text) {
        append(author, text, false);
    }

    private void append(String author, String text, boolean technical) {
        if (text == null || text.trim().isEmpty()) return;
        boolean user = "Vous".equalsIgnoreCase(author);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(user ? Gravity.END : Gravity.START);

        MaterialCardView bubble = new MaterialCardView(this);
        bubble.setRadius(dp(8));
        bubble.setCardElevation(0);
        bubble.setStrokeWidth(technical ? 0 : dp(1));
        bubble.setStrokeColor(Color.rgb(220, 228, 225));
        bubble.setCardBackgroundColor(technical
                ? Color.rgb(23, 33, 38)
                : user ? Color.rgb(225, 245, 238) : Color.WHITE);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(9), dp(12), dp(10));

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        TextView authorView = new TextView(this);
        authorView.setText(user ? "Vous" : "DIASCO");
        authorView.setTextSize(11);
        authorView.setTypeface(Typeface.DEFAULT_BOLD);
        authorView.setTextColor(technical ? Color.rgb(168, 231, 211) : Color.rgb(0, 122, 97));
        meta.addView(authorView, new LinearLayout.LayoutParams(0, -2, 1f));
        if (technical) {
            MaterialButton copyButton = createIconButton(R.drawable.ic_copy, "Copier le contenu", false);
            copyButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
            copyButton.setOnClickListener(view -> copyToClipboard(text));
            meta.addView(copyButton, new LinearLayout.LayoutParams(dp(36), dp(36)));
        }
        content.addView(meta, new LinearLayout.LayoutParams(-1, -2));

        TextView messageView = new TextView(this);
        messageView.setText(text.trim());
        messageView.setTextSize(technical ? 14 : 16);
        messageView.setTextColor(technical ? Color.WHITE : Color.rgb(23, 33, 38));
        messageView.setTextIsSelectable(true);
        messageView.setLineSpacing(0, 1.08f);
        messageView.setMaxWidth(Math.max(dp(220), getResources().getDisplayMetrics().widthPixels - dp(74)));
        if (technical) {
            messageView.setTypeface(Typeface.MONOSPACE);
        }
        content.addView(messageView, new LinearLayout.LayoutParams(-1, -2));
        bubble.addView(content);
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (chatContainer.getChildCount() > 0) {
            params.topMargin = dp(9);
        }
        chatContainer.addView(row, params);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("Contenu DIASCO", text));
        Toast.makeText(this, "Copié.", Toast.LENGTH_SHORT).show();
    }

    private boolean looksLikeTechnicalContent(String text) {
        if (text == null) return false;
        String clean = text.trim();
        if (clean.isEmpty()) return false;
        String lower = clean.toLowerCase(Locale.ROOT);
        return clean.contains("{")
                || clean.contains("}")
                || clean.contains("=>")
                || clean.contains("</")
                || lower.contains("function ")
                || lower.contains("class ")
                || lower.contains("def ")
                || lower.contains("import ")
                || lower.contains("select ")
                || lower.contains("const ")
                || lower.contains("let ")
                || lower.contains("var ")
                || lower.contains("public static")
                || lower.contains("formule")
                || lower.contains("equation")
                || lower.contains("équation")
                || clean.matches("(?s).*\\n\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*=\\s*.+")
                || clean.matches("(?s).*\\n\\s*[-+*/=()^√Σ].*");
    }

    private String speechSummaryForAnswer(String question, String answer) {
        if (isTechnicalRequest(question) || looksLikeTechnicalContent(answer)) {
            if (answer != null && answer.length() < 160 && !answer.contains("\n")) {
                return answer;
            }
            if (TextUtils.normalizeForIntent(question).contains("formule")) {
                return "J’ai affiché la formule à l’écran avec l’explication.";
            }
            if (TextUtils.normalizeForIntent(question).contains("code")
                    || TextUtils.normalizeForIntent(question).contains("programme")
                    || TextUtils.normalizeForIntent(question).contains("script")) {
                return "J’ai affiché le code à l’écran.";
            }
            return "J’ai affiché la réponse technique à l’écran.";
        }
        return answer;
    }

    private void appendImage(String imageBase64, String mimeType) {
        try {
            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                appendAssistant("L’image reçue n’a pas pu être affichée.");
                return;
            }

            MaterialCardView imageCard = new MaterialCardView(this);
            imageCard.setRadius(dp(8));
            imageCard.setCardElevation(0);
            imageCard.setStrokeWidth(dp(1));
            imageCard.setStrokeColor(Color.rgb(220, 228, 225));
            imageCard.setCardBackgroundColor(Color.WHITE);
            LinearLayout imageContent = new LinearLayout(this);
            imageContent.setOrientation(LinearLayout.VERTICAL);

            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);
            imageView.setMaxHeight(dp(420));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(Color.rgb(239, 243, 241));
            imageContent.addView(imageView, new LinearLayout.LayoutParams(-1, -2));

            MaterialButton saveButton = new MaterialButton(this);
            saveButton.setText("Enregistrer l’image");
            saveButton.setIconResource(R.drawable.ic_download);
            saveButton.setIconTint(ColorStateList.valueOf(Color.WHITE));
            saveButton.setTextColor(Color.WHITE);
            saveButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(0, 143, 114)));
            saveButton.setCornerRadius(dp(8));
            saveButton.setOnClickListener(view -> requestSaveImage(bytes, mimeType));
            LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(48));
            saveParams.setMargins(dp(10), dp(8), dp(10), dp(10));
            imageContent.addView(saveButton, saveParams);
            imageCard.addView(imageContent);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(10);
            chatContainer.addView(imageCard, params);
        } catch (IllegalArgumentException ex) {
            appendAssistant("L’image reçue est illisible.");
        }
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void requestSaveImage(byte[] bytes, String mimeType) {
        pendingImageBytes = bytes;
        pendingImageMimeType = mimeType == null || mimeType.trim().isEmpty() ? "image/png" : mimeType;
        String extension = pendingImageMimeType.contains("jpeg") || pendingImageMimeType.contains("jpg") ? "jpg" : "png";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(pendingImageMimeType)
                .putExtra(Intent.EXTRA_TITLE, "diasco-image-" + System.currentTimeMillis() + "." + extension);
        try {
            startActivityForResult(intent, REQUEST_SAVE_IMAGE);
        } catch (ActivityNotFoundException exception) {
            pendingImageBytes = null;
            pendingImageMimeType = null;
            Toast.makeText(this, "Aucun gestionnaire de fichiers disponible.", Toast.LENGTH_LONG).show();
        }
    }

    private void speak(String text) {
        speakThen(text, null);
    }

    private void speakThen(String text, Runnable afterSpeech) {
        if (tts == null) {
            if (afterSpeech != null) scrollView.postDelayed(afterSpeech, estimateSpeechDelayMs(text));
            return;
        }

        String utteranceId = "diasco-tts-" + (++speechCounter);
        if (afterSpeech != null) {
            speechCallbacks.put(utteranceId, afterSpeech);
        }

        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        if (result == TextToSpeech.ERROR && afterSpeech != null) {
            speechCallbacks.remove(utteranceId);
            scrollView.postDelayed(afterSpeech, estimateSpeechDelayMs(text));
        }
    }

    private void runSpeechCallback(String utteranceId) {
        Runnable callback = speechCallbacks.remove(utteranceId);
        if (callback != null) {
            runOnUiThread(callback);
        }
    }

    private long estimateSpeechDelayMs(String text) {
        int length = text == null ? 0 : text.length();
        return Math.max(900L, Math.min(6500L, 450L + length * 55L));
    }

    private String currentEndpoint() {
        if (!showDeveloperControls()) {
            return fixCommonEndpointTypo(defaultBackendEndpoint());
        }
        String endpoint = endpointInput.getText().toString().trim();
        String fixed = fixCommonEndpointTypo(endpoint);
        if (!fixed.equals(endpoint)) {
            endpointInput.setText(fixed);
            endpointInput.setSelection(fixed.length());
        }
        return fixed;
    }

    private String fixCommonEndpointTypo(String endpoint) {
        if (endpoint == null) return "";
        String clean = endpoint.trim();
        if (clean.endsWith("/api/askq")) {
            return clean.substring(0, clean.length() - 1);
        }
        if (clean.endsWith("/api/ask/")) {
            return clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    private String defaultBackendEndpoint() {
        return getString(R.string.default_backend_endpoint).trim();
    }

    private String developerBackendEndpoint() {
        return preferences.getString(KEY_ENDPOINT, defaultBackendEndpoint());
    }

    private boolean showDeveloperControls() {
        return false;
    }

    private boolean isOnlineModeForced() {
        return !showDeveloperControls();
    }

    private boolean isOnlineModeEnabled() {
        return isOnlineModeForced() || onlineSwitch == null || onlineSwitch.isChecked();
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(KEY_ONLINE, isOnlineModeEnabled());
        if (showDeveloperControls()) {
            editor.putString(KEY_ENDPOINT, currentEndpoint());
        } else {
            editor.remove(KEY_ENDPOINT);
        }
        editor.apply();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        updateControlsState();
    }

    private void setVoiceState(boolean listening, String message) {
        voiceListening = listening;
        status(message);
        updateControlsState();
    }

    private void updateControlsState() {
        boolean canStartAction = !busy && !voiceListening;
        askButton.setEnabled(canStartAction);
        micButton.setEnabled(canStartAction);
        cameraButton.setEnabled(canStartAction);
        testButton.setEnabled(canStartAction);
        clearButton.setEnabled(!busy);
        endpointInput.setEnabled(!busy);
        questionInput.setEnabled(!busy);
        onlineSwitch.setEnabled(!busy && !isOnlineModeForced());
        micButton.setText(null);
        micButton.setIconResource(R.drawable.ic_mic);
        micButton.setContentDescription(voiceListening ? "Écoute en cours" : "Poser une question à la voix");
    }

    private void statusReady() {
        status("Prêt");
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private MaterialButton createIconButton(int iconRes, String description, boolean primary) {
        MaterialButton button = new MaterialButton(this);
        button.setText(null);
        button.setIconResource(iconRes);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(0);
        button.setIconTint(ColorStateList.valueOf(primary ? Color.WHITE : Color.rgb(23, 33, 38)));
        button.setBackgroundTintList(ColorStateList.valueOf(primary
                ? Color.rgb(0, 143, 114)
                : Color.TRANSPARENT));
        button.setCornerRadius(dp(8));
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(description);
        return button;
    }

    private MaterialButton createQuickAction(String label, int iconRes, Runnable action) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(23, 33, 38));
        button.setIconResource(iconRes);
        button.setIconTint(ColorStateList.valueOf(Color.rgb(0, 143, 114)));
        button.setIconSize(dp(20));
        button.setIconPadding(dp(7));
        button.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
        button.setStrokeColor(ColorStateList.valueOf(Color.rgb(220, 228, 225)));
        button.setStrokeWidth(dp(1));
        button.setCornerRadius(dp(8));
        button.setMinHeight(dp(44));
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44));
        params.rightMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private void setQuestionTemplate(String template) {
        questionInput.setText(template);
        questionInput.setSelection(questionInput.length());
        questionInput.requestFocus();
        android.view.inputmethod.InputMethodManager keyboard =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.showSoftInput(questionInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable circleBackground(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private void status(String message) {
        String safeMessage = message == null || message.trim().isEmpty() ? "Prêt" : message.trim();
        statusText.setText(safeMessage);
        if (statusDot != null) {
            String normalized = TextUtils.normalizeForIntent(safeMessage);
            int dotColor = normalized.contains("erreur") || normalized.contains("indisponible")
                    ? Color.rgb(196, 66, 66)
                    : normalized.contains("cours") || normalized.contains("reflechit")
                    || normalized.contains("traitement") || normalized.contains("generation")
                    ? Color.rgb(233, 154, 46)
                    : Color.rgb(0, 143, 114);
            statusDot.setBackground(circleBackground(dotColor));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
