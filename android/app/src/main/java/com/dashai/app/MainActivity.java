package com.dashai.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
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
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.dashai.app.ai.AiRepository;
import com.dashai.app.ai.RemoteAiClient;
import com.dashai.app.util.TextUtils;
import com.dashai.app.voice.VoiceAuthenticator;

import java.util.ArrayList;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 42;
    private static final int REQUEST_IMAGE_CAPTURE = 43;
    private static final String PREFS = "dashai_prefs";
    private static final String KEY_ENDPOINT = "backend_endpoint";
    private static final String KEY_ONLINE = "online_enabled";
    private static final String KEY_WAKE = "wake_enabled";
    private static final String KEY_OWNER_PHRASE = "owner_phrase";
    private static final String KEY_VOICE_PROFILE = "voice_profile";
    private static final String KEY_PRIVACY_NOTICE_ACCEPTED = "privacy_notice_accepted";
    private static final String FIXED_WAKE_PHRASE = "dis Diasco";
    private static final String FIXED_WAKE_PHRASE_SPOKEN = "dis diasco";
    private static final int MAX_HISTORY_LINES = 18;
    private static final long AUDIO_ERROR_CHAT_COOLDOWN_MS = 15_000L;
    private static final long WAKE_RESTART_DELAY_MS = 2_500L;
    private static final long OWNER_TRUST_WINDOW_MS = 3 * 60 * 1000L;
    private static final int VOICE_MODE_IDLE = 0;
    private static final int VOICE_MODE_MANUAL_QUESTION = 1;
    private static final int VOICE_MODE_WAKE_LISTENING = 2;
    private static final int VOICE_MODE_WAKE_QUESTION = 3;
    private static final int VOICE_MODE_OWNER_CHECK = 4;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AiRepository aiRepository = new AiRepository();
    private final ArrayList<String> recentConversation = new ArrayList<>();
    private final ConcurrentHashMap<String, Runnable> speechCallbacks = new ConcurrentHashMap<>();

    private SharedPreferences preferences;
    private EditText endpointInput;
    private EditText ownerPhraseInput;
    private Switch onlineSwitch;
    private Switch wakeSwitch;
    private LinearLayout chatContainer;
    private TextView statusText;
    private EditText questionInput;
    private Button askButton;
    private Button micButton;
    private Button cameraButton;
    private Button testButton;
    private Button clearButton;
    private Button enrollVoiceButton;
    private ScrollView scrollView;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private boolean busy;
    private boolean voiceListening;
    private boolean wakeModeEnabled;
    private boolean pendingEnableWakeMode;
    private boolean pendingEnrollVoice;
    private boolean resumeWakeAfterEnroll;
    private int voiceMode = VOICE_MODE_IDLE;
    private boolean speechPartialHandled;
    private boolean manualQuestionAutoRetryUsed;
    private String pendingQuestionText;
    private int pendingQuestionVersion;
    private int speechCounter;
    private long lastAudioChatErrorAt;
    private long ownerVoiceTrustedUntilMs;
    private String pendingVisionPrompt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        ensureProductionDefaults();
        buildUi();
        initTextToSpeech();
        appendAssistant("Bonjour. Je suis prêt. Pose une question ou dites « dis Diasco » si le réveil vocal est activé.");
        showPrivacyNoticeIfNeeded();
        if (wakeSwitch.isChecked()) {
            wakeSwitch.post(() -> enableWakeMode());
        }
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
        if (wakeModeEnabled) {
            stopSpeechRecognizer();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (wakeModeEnabled && !busy) {
            startWakeListeningSoon(500);
        }
    }

    private void buildUi() {
        int padding = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(0xFFF8FAFC);

        TextView title = new TextView(this);
        title.setText("DashAI");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(0xFF111827);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = new TextView(this);
        subtitle.setText("Assistant vocal + backend IA sécurisé — V3 contexte");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF475569);
        subtitle.setPadding(0, 0, 0, dp(12));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

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
        if (showDeveloperControls()) {
            root.addView(endpointInput, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout switches = new LinearLayout(this);
        switches.setOrientation(LinearLayout.HORIZONTAL);
        switches.setGravity(Gravity.CENTER_VERTICAL);
        switches.setPadding(0, dp(8), 0, dp(8));

        onlineSwitch = new Switch(this);
        onlineSwitch.setText("Mode en ligne");
        onlineSwitch.setChecked(isOnlineModeEnabled());
        onlineSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isOnlineModeForced() && !isChecked) {
                    onlineSwitch.setChecked(true);
                    status("Mode en ligne activé automatiquement.");
                    return;
                }
                saveSettings();
                status(isChecked ? "Mode en ligne activé." : "Mode hors ligne activé.");
            }
        });
        if (showDeveloperControls()) {
            switches.addView(onlineSwitch, new LinearLayout.LayoutParams(0, -2, 1));
        }

        testButton = new Button(this);
        testButton.setText("Tester");
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testBackend();
            }
        });
        if (showDeveloperControls()) {
            switches.addView(testButton, new LinearLayout.LayoutParams(-2, -2));
        }

        clearButton = new Button(this);
        clearButton.setText("Effacer");
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearConversation();
            }
        });
        switches.addView(clearButton, new LinearLayout.LayoutParams(-2, -2));
        root.addView(switches, new LinearLayout.LayoutParams(-1, -2));

        wakeSwitch = new Switch(this);
        wakeSwitch.setText("Réveil vocal : " + FIXED_WAKE_PHRASE);
        wakeSwitch.setChecked(preferences.getBoolean(KEY_WAKE, false));
        wakeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    enableWakeMode();
                } else {
                    disableWakeMode();
                }
            }
        });
        root.addView(wakeSwitch, new LinearLayout.LayoutParams(-1, -2));

        ownerPhraseInput = new EditText(this);
        ownerPhraseInput.setSingleLine(true);
        ownerPhraseInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ownerPhraseInput.setHint("Phrase utilisateur");
        ownerPhraseInput.setText(FIXED_WAKE_PHRASE_SPOKEN);
        ownerPhraseInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ownerPhraseInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveSettings();
                ownerPhraseInput.clearFocus();
                status("Phrase utilisateur sauvegardée.");
                return true;
            }
            return false;
        });
        ownerPhraseInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) saveSettings();
        });
        if (showDeveloperControls()) {
            root.addView(ownerPhraseInput, new LinearLayout.LayoutParams(-1, -2));
        }

        enrollVoiceButton = new Button(this);
        enrollVoiceButton.setText(hasVoiceProfile() ? "Refaire empreinte voix" : "Enregistrer ma voix");
        enrollVoiceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enrollOwnerVoice();
            }
        });
        root.addView(enrollVoiceButton, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Micro : prêt.");
        statusText.setTextColor(0xFF334155);
        statusText.setPadding(0, 0, 0, dp(8));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        scrollView = new ScrollView(this);
        chatContainer = new LinearLayout(this);
        chatContainer.setOrientation(LinearLayout.VERTICAL);
        chatContainer.setPadding(dp(12), dp(12), dp(12), dp(12));
        chatContainer.setBackgroundColor(0xFFFFFFFF);
        scrollView.addView(chatContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(0, dp(10), 0, 0);

        questionInput = new EditText(this);
        questionInput.setSingleLine(false);
        questionInput.setMinLines(1);
        questionInput.setMaxLines(3);
        questionInput.setHint("Pose ta question…");
        questionInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        questionInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                askCurrentQuestion();
                return true;
            }
            return false;
        });
        inputRow.addView(questionInput, new LinearLayout.LayoutParams(0, -2, 1));

        cameraButton = new Button(this);
        cameraButton.setText("📷");
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCameraDescription();
            }
        });
        inputRow.addView(cameraButton, new LinearLayout.LayoutParams(-2, -2));

        micButton = new Button(this);
        micButton.setText("🎤");
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVoiceInput();
            }
        });
        inputRow.addView(micButton, new LinearLayout.LayoutParams(-2, -2));

        askButton = new Button(this);
        askButton.setText("Envoyer");
        askButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askCurrentQuestion();
            }
        });
        inputRow.addView(askButton, new LinearLayout.LayoutParams(-2, -2));
        root.addView(inputRow, new LinearLayout.LayoutParams(-1, -2));

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
                .setMessage("DashAI peut envoyer vos questions et les photos que vous choisissez d’analyser vers son backend IA sécurisé afin de générer une réponse. L’empreinte vocale reste stockée sur cet appareil.")
                .setPositiveButton("J’ai compris", (dialog, which) -> preferences.edit()
                        .putBoolean(KEY_PRIVACY_NOTICE_ACCEPTED, true)
                        .apply())
                .show();
    }

    private void ensureProductionDefaults() {
        preferences.edit()
                .putBoolean(KEY_ONLINE, true)
                .putString(KEY_OWNER_PHRASE, FIXED_WAKE_PHRASE_SPOKEN)
                .apply();
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
            speakAndResumeWake(commandAnswer);
            return;
        }

        if (isImageRequest(question)) {
            generateRequestedImage(question);
            return;
        }

        boolean onlineEnabled = isOnlineModeEnabled();
        String endpoint = currentEndpoint();
        String historyForAi = buildHistoryForAi();
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
                appendAssistant(cleanAnswer);
                rememberTurn(question, cleanAnswer);
                speakAndResumeWake(cleanAnswer);
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
                return "Le mode en ligne reste activé automatiquement pour DashAI.";
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
                || clean.contains("illustration");
        boolean startsLikeCommand = clean.startsWith("affiche")
                || clean.startsWith("montre")
                || clean.startsWith("genere")
                || clean.startsWith("cree")
                || clean.startsWith("dessine")
                || clean.startsWith("fais")
                || clean.startsWith("produis")
                || clean.startsWith("image de")
                || clean.startsWith("une image de");
        return asksForVisual && startsLikeCommand;
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
                speakAndResumeWake(cleanMessage);
            });
        });
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
        if (!looksLikeFollowUp(question) || historyForAi == null || historyForAi.trim().isEmpty()) {
            return question;
        }
        return "L'utilisateur fait une relance courte qui dépend du contexte. "
                + "Utilise le contexte récent ci-dessous pour répondre directement, sans demander de préciser si le contexte suffit.\n\n"
                + "Contexte récent :\n" + historyForAi.trim() + "\n\n"
                + "Relance actuelle de l'utilisateur : " + question;
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
                speakAndResumeWake(cleanAnswer);
            });
        });
    }

    private String encodeBitmapAsJpegBase64(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 86, output);
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    private void startVoiceInput() {
        if (busy || voiceListening || wakeModeEnabled) return;
        manualQuestionAutoRetryUsed = false;
        startSpeechRecognition(VOICE_MODE_MANUAL_QUESTION);
    }

    private void enableWakeMode() {
        if (busy) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            appendAssistant("La reconnaissance vocale Android n’est pas disponible sur cet appareil.");
            setWakeSwitchChecked(false);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingEnableWakeMode = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        if (!hasVoiceProfile()) {
            appendAssistant("Enregistre d’abord la voix de l’utilisateur, puis active le réveil vocal.");
            setWakeSwitchChecked(false);
            saveSettings();
            return;
        }

        wakeModeEnabled = true;
        pendingEnableWakeMode = false;
        saveSettings();
        startWakeListeningSoon(100);
    }

    private void disableWakeMode() {
        wakeModeEnabled = false;
        pendingEnableWakeMode = false;
        clearOwnerVoiceTrust();
        saveSettings();
        stopSpeechRecognizer();
        statusReady();
        updateControlsState();
    }

    private void startSpeechRecognition(int mode) {
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
        voiceMode = mode;
        speechPartialHandled = false;
        if (isQuestionMode(mode)) {
            pendingQuestionText = null;
            pendingQuestionVersion++;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { statusForListeningMode(mode); }
            @Override public void onBeginningOfSpeech() { status("Micro : parole détectée…"); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { status("Micro : traitement audio…"); }
            @Override public void onPartialResults(Bundle partialResults) {
                handleSpeechPartial(mode, partialResults);
            }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onError(int error) {
                if (speechPartialHandled) return;
                handleSpeechError(mode, error);
            }

            @Override
            public void onResults(Bundle results) {
                if (speechPartialHandled) return;
                setVoiceState(false, "Micro : prêt.");
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) {
                    handleEmptySpeechResult(mode);
                    return;
                }
                handleSpeechMatches(mode, matches);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        if (isQuestionMode(mode)) {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 12_000);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4_500);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3_000);
        } else {
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 4_000);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_800);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200);
        }
        String prompt = "Pose ta question à DashAI";
        if (mode == VOICE_MODE_WAKE_LISTENING) {
            prompt = "Dites : dis Diasco";
        } else if (mode == VOICE_MODE_OWNER_CHECK) {
            prompt = "Dites votre phrase utilisateur";
        }
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        setVoiceState(true, messageForListeningMode(mode));
        speechRecognizer.startListening(intent);
    }

    private void handleSpeechError(int mode, int error) {
        setVoiceState(false, idleMessageForMode(mode));
        if (isQuestionMode(mode) && pendingQuestionText != null && !pendingQuestionText.trim().isEmpty()) {
            submitQuestionFromPartial(pendingQuestionVersion, mode);
            return;
        }
        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            handleEmptySpeechResult(mode);
            return;
        }

        if (mode == VOICE_MODE_WAKE_LISTENING || mode == VOICE_MODE_WAKE_QUESTION || mode == VOICE_MODE_OWNER_CHECK) {
            status(idleMessageForMode(mode));
            startWakeListeningSoon(WAKE_RESTART_DELAY_MS);
            return;
        }

        status("Erreur audio. Réessaie ou écris la question.");
        appendAudioErrorOnce("Je n’ai pas compris l’audio. Réessaie ou écris la question.");
    }

    private void handleSpeechPartial(int mode, Bundle partialResults) {
        if (speechPartialHandled || partialResults == null) return;
        ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) return;

        if (mode == VOICE_MODE_WAKE_LISTENING) {
            String wakeMatch = findWakeMatch(matches);
            if (wakeMatch != null) {
                speechPartialHandled = true;
                scrollView.post(() -> {
                    stopSpeechRecognizer();
                    status("Réveil vocal : mot détecté.");
                    handleSpeechText(mode, wakeMatch);
                });
                return;
            }

            String heard = firstNonEmpty(matches);
            if (heard != null) {
                status("Réveil vocal : entendu « " + limitForStatus(heard) + " ».");
            }
        }

        if (isQuestionMode(mode)) {
            String heard = firstNonEmpty(matches);
            if (heard != null && heard.trim().length() >= 2) {
                pendingQuestionText = heard.trim();
                int version = ++pendingQuestionVersion;
                status("Question entendue : « " + limitForStatus(pendingQuestionText) + " ».");
                scrollView.postDelayed(() -> submitQuestionFromPartial(version, mode), 2_200);
            }
        }
    }

    private void handleSpeechMatches(int mode, ArrayList<String> matches) {
        if (mode == VOICE_MODE_WAKE_LISTENING) {
            String wakeMatch = findWakeMatch(matches);
            if (wakeMatch != null) {
                handleSpeechText(mode, wakeMatch);
                return;
            }

            String heard = firstNonEmpty(matches);
            if (heard != null) {
                status("Réveil vocal : entendu « " + limitForStatus(heard) + " ».");
            }
            startWakeListeningSoon(WAKE_RESTART_DELAY_MS);
            return;
        }

        String first = firstNonEmpty(matches);
        if (first == null) {
            handleEmptySpeechResult(mode);
            return;
        }
        if (isQuestionMode(mode)) {
            pendingQuestionVersion++;
            pendingQuestionText = null;
        }
        handleSpeechText(mode, first);
    }

    private void submitQuestionFromPartial(int version, int expectedMode) {
        if (speechPartialHandled) return;
        if (voiceMode != expectedMode) return;
        if (!isQuestionMode(expectedMode)) return;
        if (version != pendingQuestionVersion) return;
        String question = pendingQuestionText == null ? "" : pendingQuestionText.trim();
        if (question.length() < 2) return;

        speechPartialHandled = true;
        stopSpeechRecognizer();
        status("Question reçue : « " + limitForStatus(question) + " ».");
        ask(question);
    }

    private boolean isQuestionMode(int mode) {
        return mode == VOICE_MODE_MANUAL_QUESTION || mode == VOICE_MODE_WAKE_QUESTION;
    }

    private String findWakeMatch(ArrayList<String> matches) {
        for (String match : matches) {
            if (containsWakePhrase(match)) return match;
        }
        return null;
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

    private void handleEmptySpeechResult(int mode) {
        if (mode == VOICE_MODE_MANUAL_QUESTION) {
            if (!manualQuestionAutoRetryUsed) {
                manualQuestionAutoRetryUsed = true;
                status("Micro : je n’ai rien entendu, je relance l’écoute…");
                scrollView.postDelayed(() -> {
                    if (!busy && !voiceListening && !wakeModeEnabled) {
                        startSpeechRecognition(VOICE_MODE_MANUAL_QUESTION);
                    }
                }, 700);
            } else {
                status("Micro : aucune question détectée.");
            }
            return;
        }
        if (mode == VOICE_MODE_WAKE_LISTENING) {
            status(idleMessageForMode(mode));
            startWakeListeningSoon(WAKE_RESTART_DELAY_MS);
            return;
        }
        if (mode == VOICE_MODE_WAKE_QUESTION) {
            status("Réveil vocal : aucune question détectée.");
            startWakeListeningSoon(WAKE_RESTART_DELAY_MS);
            return;
        }
        if (mode == VOICE_MODE_OWNER_CHECK) {
            status("Réveil vocal : utilisateur non reconnu.");
            startWakeListeningSoon(WAKE_RESTART_DELAY_MS);
            return;
        }
        status("Micro : aucun texte détecté.");
    }

    private void handleSpeechText(int mode, String text) {
        String spoken = text == null ? "" : text.trim();
        if (spoken.isEmpty()) {
            handleEmptySpeechResult(mode);
            return;
        }

        if (mode == VOICE_MODE_WAKE_LISTENING) {
            if (containsWakePhrase(spoken)) {
                if (!hasVoiceProfile()) {
                    appendAssistant("Enregistre d’abord la voix de l’utilisateur avant d’activer le réveil privé.");
                    status("Réveil vocal : empreinte voix absente.");
                    startWakeListeningSoon(1600);
                    return;
                }
                if (isOwnerVoiceTrusted()) {
                    acknowledgeOwnerAndListen();
                    return;
                }
                String answer = "Je vérifie votre voix. Répétez maintenant : " + FIXED_WAKE_PHRASE + ".";
                appendAssistant(answer);
                status("Réveil vocal : identification utilisateur…");
                speakThen(answer, () -> startOwnerVoiceCheckSoon(350));
            } else {
                status("Réveil vocal : dites « dis Diasco ».");
                startWakeListeningSoon(400);
            }
            return;
        }

        if (mode == VOICE_MODE_OWNER_CHECK) {
            verifyOwnerVoice();
            return;
        }

        if (mode == VOICE_MODE_WAKE_QUESTION) {
            status("Question reçue : « " + limitForStatus(spoken) + " ».");
            ask(spoken);
            return;
        }

        ask(spoken);
    }

    private boolean containsWakePhrase(String text) {
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
                || compact.contains("dixdiasco")
                || clean.contains("dis di asco")
                || clean.contains("dit di asco")
                || clean.contains("dis diasco")
                || clean.contains("dit diasco")
                || clean.contains("dix diasco")
                || (clean.contains("dis") && compact.contains("dia"))
                || (clean.contains("dit") && compact.contains("dia"));
    }

    private String cleanOwnerPhrase() {
        return TextUtils.normalizeForIntent(FIXED_WAKE_PHRASE_SPOKEN);
    }

    private boolean hasVoiceProfile() {
        return VoiceAuthenticator.deserialize(preferences.getString(KEY_VOICE_PROFILE, "")) != null;
    }

    private boolean isOwnerVoiceTrusted() {
        return System.currentTimeMillis() < ownerVoiceTrustedUntilMs;
    }

    private void trustOwnerVoiceTemporarily() {
        ownerVoiceTrustedUntilMs = System.currentTimeMillis() + OWNER_TRUST_WINDOW_MS;
    }

    private void clearOwnerVoiceTrust() {
        ownerVoiceTrustedUntilMs = 0L;
    }

    private void enrollOwnerVoice() {
        if (busy || voiceListening) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingEnrollVoice = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        resumeWakeAfterEnroll = wakeModeEnabled || wakeSwitch.isChecked();
        wakeModeEnabled = false;
        clearOwnerVoiceTrust();
        stopSpeechRecognizer();
        saveSettings();
        String phrase = FIXED_WAKE_PHRASE;
        appendAssistant("Enregistrement voix : dites clairement « " + phrase + " » quand je vous le demande.");
        status("Empreinte voix : préparez-vous…");
        setBusy(true);
        speakThen("Préparez-vous. Répétez maintenant : " + phrase + ".", this::captureOwnerVoiceProfile);
    }

    private void captureOwnerVoiceProfile() {
        status("Empreinte voix : parlez maintenant…");
        executor.execute(() -> {
            try {
                VoiceAuthenticator.VoiceSample sample = VoiceAuthenticator.captureSample(MainActivity.this);
                String serialized = VoiceAuthenticator.serialize(sample.features);
                runOnUiThread(() -> {
                    preferences.edit().putString(KEY_VOICE_PROFILE, serialized).apply();
                    setBusy(false);
                    enrollVoiceButton.setText("Refaire empreinte voix");
                    appendAssistant("Empreinte vocale enregistrée.");
                    resumeWakeAfterVoiceEnroll();
                });
            } catch (VoiceAuthenticator.VoiceAuthException ex) {
                runOnUiThread(() -> {
                    setBusy(false);
                    appendAssistant("Empreinte vocale non enregistrée : " + ex.getMessage());
                    resumeWakeAfterVoiceEnroll();
                });
            }
        });
    }

    private void resumeWakeAfterVoiceEnroll() {
        if (resumeWakeAfterEnroll && wakeSwitch.isChecked() && hasVoiceProfile()) {
            wakeModeEnabled = true;
            resumeWakeAfterEnroll = false;
            status(idleMessageForMode(VOICE_MODE_WAKE_LISTENING));
            startWakeListeningSoon(1200);
            updateControlsState();
            return;
        }

        resumeWakeAfterEnroll = false;
        statusReady();
        updateControlsState();
    }

    private void verifyOwnerVoice() {
        if (busy) return;
        double[] expected = VoiceAuthenticator.deserialize(preferences.getString(KEY_VOICE_PROFILE, ""));
        if (expected == null) {
            appendAssistant("Aucune empreinte vocale utilisateur n’est enregistrée.");
            startWakeListeningSoon(1200);
            return;
        }

        stopSpeechRecognizer();
        status("Réveil vocal : vérification de la voix…");
        setBusy(true);
        executor.execute(() -> {
            try {
                VoiceAuthenticator.VoiceSample sample = VoiceAuthenticator.captureSample(MainActivity.this);
                double score = VoiceAuthenticator.similarity(expected, sample.features);
                runOnUiThread(() -> {
                    setBusy(false);
                    String scoreText = String.format(Locale.FRANCE, "%.2f", score);
                    if (score >= VoiceAuthenticator.DEFAULT_THRESHOLD) {
                        status("Réveil vocal : voix reconnue (" + scoreText + ").");
                        trustOwnerVoiceTemporarily();
                        acknowledgeOwnerAndListen();
                    } else {
                        status("Réveil vocal : voix refusée.");
                        appendAssistant("Voix non reconnue (score " + scoreText + "). Je reste en attente de l’utilisateur.");
                        startWakeListeningSoon(1200);
                    }
                });
            } catch (VoiceAuthenticator.VoiceAuthException ex) {
                runOnUiThread(() -> {
                    setBusy(false);
                    status("Réveil vocal : voix non vérifiée.");
                    appendAssistant("Voix non vérifiée : " + ex.getMessage());
                    startWakeListeningSoon(1200);
                });
            }
        });
    }

    private void acknowledgeOwnerAndListen() {
        String answer = "Oui, je vous écoute.";
        appendAssistant(answer);
        status("Réveil vocal : question attendue…");
        speakThen(answer, () -> startWakeQuestionSoon(250));
    }

    private void startWakeListeningSoon(long delayMs) {
        if (!wakeModeEnabled || busy) return;
        scrollView.postDelayed(() -> {
            if (wakeModeEnabled && !busy && !voiceListening) {
                startSpeechRecognition(VOICE_MODE_WAKE_LISTENING);
            }
        }, delayMs);
    }

    private void startWakeQuestionSoon(long delayMs) {
        if (!wakeModeEnabled || busy) return;
        scrollView.postDelayed(() -> {
            if (wakeModeEnabled && !busy && !voiceListening) {
                startSpeechRecognition(VOICE_MODE_WAKE_QUESTION);
            }
        }, delayMs);
    }

    private void startOwnerVoiceCheckSoon(long delayMs) {
        if (!wakeModeEnabled || busy) return;
        scrollView.postDelayed(() -> {
            if (wakeModeEnabled && !busy && !voiceListening) {
                verifyOwnerVoice();
            }
        }, delayMs);
    }

    private void speakAndResumeWake(String text) {
        if (wakeModeEnabled) {
            speakThen(text, () -> startWakeListeningSoon(300));
        } else {
            speak(text);
        }
    }

    private void statusForListeningMode(int mode) {
        status(messageForListeningMode(mode));
    }

    private String messageForListeningMode(int mode) {
        if (mode == VOICE_MODE_WAKE_LISTENING) return "Réveil vocal : surveillance active. Dites « " + FIXED_WAKE_PHRASE + " ».";
        if (mode == VOICE_MODE_OWNER_CHECK) return "Réveil vocal : répétez « " + FIXED_WAKE_PHRASE + " ».";
        if (mode == VOICE_MODE_WAKE_QUESTION) return "Réveil vocal : je vous écoute…";
        return "Micro : écoute longue, pose ta question…";
    }

    private String idleMessageForMode(int mode) {
        if (mode == VOICE_MODE_WAKE_LISTENING) return "Réveil vocal : surveillance active. Dites « " + FIXED_WAKE_PHRASE + " ».";
        if (mode == VOICE_MODE_WAKE_QUESTION) return "Réveil vocal : en attente.";
        if (mode == VOICE_MODE_OWNER_CHECK) return "Réveil vocal : identification en attente.";
        return "Micro : prêt.";
    }

    private void stopSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        voiceMode = VOICE_MODE_IDLE;
        voiceListening = false;
    }

    private void setWakeSwitchChecked(boolean checked) {
        wakeSwitch.setOnCheckedChangeListener(null);
        wakeSwitch.setChecked(checked);
        wakeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    enableWakeMode();
                } else {
                    disableWakeMode();
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingEnrollVoice) {
                pendingEnrollVoice = false;
                enrollOwnerVoice();
                return;
            }
            if (pendingEnableWakeMode) {
                enableWakeMode();
                return;
            }
            startVoiceInput();
        } else if (requestCode == REQUEST_RECORD_AUDIO && pendingEnableWakeMode) {
            pendingEnableWakeMode = false;
            setWakeSwitchChecked(false);
            statusReady();
        } else if (requestCode == REQUEST_RECORD_AUDIO && pendingEnrollVoice) {
            pendingEnrollVoice = false;
            statusReady();
        }
    }

    private void appendUser(String text) {
        append("Vous", text);
    }

    private void appendAssistant(String text) {
        append("DashAI", cleanAssistantText(text));
    }

    private void appendAudioErrorOnce(String text) {
        long now = System.currentTimeMillis();
        if (now - lastAudioChatErrorAt < AUDIO_ERROR_CHAT_COOLDOWN_MS) return;
        lastAudioChatErrorAt = now;
        appendAssistant(text);
    }

    private void clearConversation() {
        recentConversation.clear();
        chatContainer.removeAllViews();
        statusReady();
    }

    private String cleanAssistantText(String text) {
        if (text == null) return "";
        return text
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .trim();
    }

    private void rememberTurn(String userQuestion, String assistantAnswer) {
        recentConversation.add("Vous : " + userQuestion);
        recentConversation.add("DashAI : " + assistantAnswer);
        while (recentConversation.size() > MAX_HISTORY_LINES) {
            recentConversation.remove(0);
        }
    }

    private String buildHistoryForAi() {
        if (recentConversation.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String line : recentConversation) {
            builder.append(line).append('\n');
        }
        return builder.toString().trim();
    }

    private void append(String author, String text) {
        TextView messageView = new TextView(this);
        messageView.setText(author + " : " + text);
        messageView.setTextSize(16);
        messageView.setTextColor(0xFF111827);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (chatContainer.getChildCount() > 0) {
            params.topMargin = dp(10);
        }
        chatContainer.addView(messageView, params);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void appendImage(String imageBase64, String mimeType) {
        try {
            byte[] bytes = Base64.decode(imageBase64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                appendAssistant("L’image reçue n’a pas pu être affichée.");
                return;
            }

            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            imageView.setAdjustViewBounds(true);
            imageView.setMaxHeight(dp(420));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setBackgroundColor(0xFFE5E7EB);
            imageView.setPadding(dp(4), dp(4), dp(4), dp(4));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(10);
            chatContainer.addView(imageView, params);
        } catch (IllegalArgumentException ex) {
            appendAssistant("L’image reçue est illisible.");
        }
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void speak(String text) {
        speakThen(text, null);
    }

    private void speakThen(String text, Runnable afterSpeech) {
        if (tts == null) {
            if (afterSpeech != null) scrollView.postDelayed(afterSpeech, estimateSpeechDelayMs(text));
            return;
        }

        String utteranceId = "dashai-tts-" + (++speechCounter);
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
        return isDebugBuild();
    }

    private boolean isOnlineModeForced() {
        return !showDeveloperControls();
    }

    private boolean isOnlineModeEnabled() {
        return isOnlineModeForced() || onlineSwitch == null || onlineSwitch.isChecked();
    }

    private void saveSettings() {
        SharedPreferences.Editor editor = preferences.edit()
                .putString(KEY_OWNER_PHRASE, FIXED_WAKE_PHRASE_SPOKEN)
                .putBoolean(KEY_ONLINE, isOnlineModeEnabled())
                .putBoolean(KEY_WAKE, wakeSwitch != null && wakeSwitch.isChecked());
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
        micButton.setEnabled(canStartAction && !wakeModeEnabled);
        cameraButton.setEnabled(canStartAction);
        testButton.setEnabled(canStartAction);
        clearButton.setEnabled(!busy);
        enrollVoiceButton.setEnabled(canStartAction);
        endpointInput.setEnabled(!busy);
        ownerPhraseInput.setEnabled(!busy && !voiceListening);
        questionInput.setEnabled(!busy);
        onlineSwitch.setEnabled(!busy && !isOnlineModeForced());
        wakeSwitch.setEnabled(!busy);
        if (wakeModeEnabled || wakeSwitch.isChecked()) {
            micButton.setText("Réveil");
        } else {
            micButton.setText(voiceListening ? "Écoute" : "🎤");
        }
    }

    private void statusReady() {
        if (wakeModeEnabled) {
            status(idleMessageForMode(VOICE_MODE_WAKE_LISTENING));
        } else {
            status("Micro : prêt.");
        }
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private void status(String message) {
        statusText.setText(message);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
