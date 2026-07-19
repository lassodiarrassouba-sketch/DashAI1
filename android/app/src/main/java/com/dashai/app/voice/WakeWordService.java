package com.dashai.app.voice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.dashai.app.MainActivity;
import com.dashai.app.R;
import com.dashai.app.ai.RemoteAiClient;
import com.dashai.app.util.ConversationMemory;
import com.dashai.app.util.TextUtils;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WakeWordService extends Service {
    public static final String ACTION_START = "com.dashai.app.action.START_WAKE_SERVICE";
    public static final String ACTION_STOP = "com.dashai.app.action.STOP_WAKE_SERVICE";
    public static final String ACTION_LISTEN_NOW = "com.dashai.app.action.LISTEN_NOW";
    public static final String ACTION_EVENT = "com.dashai.app.action.WAKE_EVENT";
    public static final String EXTRA_EVENT = "event";
    public static final String EXTRA_TEXT = "text";
    public static final String EVENT_STATUS = "status";
    public static final String EVENT_WAKE = "wake";
    public static final String EVENT_QUESTION = "question";
    public static final String EVENT_ANSWER = "answer";

    private static final String CHANNEL_ID = "diasco_wake_channel";
    private static final int NOTIFICATION_ID = 2201;
    private static final int MODE_WAKE = 1;
    private static final int MODE_QUESTION = 2;
    private static final long RESTART_DELAY_MS = 1_600L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ConcurrentHashMap<String, Runnable> speechCallbacks = new ConcurrentHashMap<>();

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private PowerManager.WakeLock wakeLock;
    private boolean ttsReady;
    private boolean recognitionActive;
    private boolean recognitionHandled;
    private boolean processing;
    private boolean stopping;
    private int recognitionMode = MODE_WAKE;
    private int questionVersion;
    private int utteranceCounter;
    private String partialQuestion;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        initTextToSpeech();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DIASCO:WakeConversation");
            wakeLock.setReferenceCounted(false);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopping = true;
            stopSelf();
            return START_NOT_STICKY;
        }

        stopping = false;
        boolean listenNow = intent != null && ACTION_LISTEN_NOW.equals(intent.getAction());
        try {
            startForeground(NOTIFICATION_ID, notification("En attente de « Dis Diasco »"));
        } catch (SecurityException exception) {
            sendEvent(EVENT_STATUS, "Réveil vocal indisponible : permission micro manquante.");
            stopSelf();
            return START_NOT_STICKY;
        }
        if (listenNow) {
            if (!processing) {
                onWakePhraseDetected();
            } else {
                sendEvent(EVENT_STATUS, "DIASCO traite déjà une demande.");
            }
            return START_STICKY;
        }
        sendEvent(EVENT_STATUS, "Réveil vocal actif, même écran verrouillé.");
        startListeningSoon(MODE_WAKE, 350L);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopping = true;
        mainHandler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        executor.shutdownNow();
        releaseWakeLock();
        super.onDestroy();
    }

    private void initTextToSpeech() {
        tts = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) tts.setLanguage(Locale.FRANCE);
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

    private void startListeningSoon(int mode, long delayMs) {
        mainHandler.postDelayed(() -> {
            if (!stopping && !processing && !recognitionActive) {
                startRecognition(mode);
            }
        }, delayMs);
    }

    private void startRecognition(int mode) {
        if (stopping || processing || recognitionActive) return;
        acquireWakeLock();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateState("Permission micro requise");
            stopSelf();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateState("Reconnaissance vocale Android indisponible");
            startListeningSoon(MODE_WAKE, 5_000L);
            return;
        }

        destroyRecognizer();
        recognitionMode = mode;
        recognitionHandled = false;
        partialQuestion = null;
        if (mode == MODE_QUESTION) questionVersion++;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
        } catch (RuntimeException exception) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        }

        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                updateState(mode == MODE_WAKE ? "En attente de « Dis Diasco »" : "Je vous écoute…");
            }

            @Override
            public void onBeginningOfSpeech() {
                sendEvent(EVENT_STATUS, mode == MODE_WAKE ? "Voix détectée…" : "Question en cours…");
            }

            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }

            @Override
            public void onEndOfSpeech() {
                sendEvent(EVENT_STATUS, "Traitement de la voix…");
            }

            @Override
            public void onError(int error) {
                recognitionActive = false;
                if (recognitionHandled || stopping) return;
                if (mode == MODE_QUESTION && partialQuestion != null && partialQuestion.length() >= 2) {
                    submitQuestion(partialQuestion);
                    return;
                }
                if (mode == MODE_QUESTION) {
                    updateState("Aucune question entendue. Retour au réveil vocal.");
                }
                startListeningSoon(MODE_WAKE, RESTART_DELAY_MS);
            }

            @Override
            public void onResults(Bundle results) {
                recognitionActive = false;
                if (recognitionHandled || stopping) return;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                handleMatches(mode, matches);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (recognitionHandled || stopping || partialResults == null) return;
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty()) return;
                if (mode == MODE_WAKE && findWakeMatch(matches) != null) {
                    recognitionHandled = true;
                    onWakePhraseDetected();
                    return;
                }
                if (mode == MODE_QUESTION) {
                    String heard = firstNonEmpty(matches);
                    if (heard != null && heard.length() >= 2) {
                        partialQuestion = heard;
                        int version = ++questionVersion;
                        sendEvent(EVENT_STATUS, "Question entendue : « " + shorten(heard) + " »");
                        mainHandler.postDelayed(() -> {
                            if (!recognitionHandled && recognitionMode == MODE_QUESTION
                                    && version == questionVersion && partialQuestion != null) {
                                submitQuestion(partialQuestion);
                            }
                        }, 2_300L);
                    }
                }
            }

            @Override public void onEvent(int eventType, Bundle params) { }
        });

        Intent recognitionIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognitionIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR");
        recognitionIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognitionIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        recognitionIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4);
        if (mode == MODE_QUESTION) {
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Posez votre question à DIASCO");
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 12_000);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3_500);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_200);
        } else {
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Dites : Dis Diasco");
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5_000);
            recognitionIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_400);
        }

        recognitionActive = true;
        try {
            recognizer.startListening(recognitionIntent);
        } catch (RuntimeException exception) {
            recognitionActive = false;
            destroyRecognizer();
            startListeningSoon(MODE_WAKE, 3_000L);
        }
    }

    private void handleMatches(int mode, ArrayList<String> matches) {
        if (mode == MODE_WAKE) {
            if (findWakeMatch(matches) != null) {
                recognitionHandled = true;
                onWakePhraseDetected();
            } else {
                startListeningSoon(MODE_WAKE, RESTART_DELAY_MS);
            }
            return;
        }

        String question = firstNonEmpty(matches);
        if (question == null || question.length() < 2) {
            startListeningSoon(MODE_WAKE, RESTART_DELAY_MS);
            return;
        }
        submitQuestion(question);
    }

    private void onWakePhraseDetected() {
        destroyRecognizer();
        acquireWakeLock();
        processing = true;
        String acknowledgement = "Oui, je vous écoute.";
        updateState(acknowledgement);
        sendEvent(EVENT_WAKE, acknowledgement);
        speakThen(acknowledgement, () -> {
            processing = false;
            startListeningSoon(MODE_QUESTION, 250L);
        });
    }

    private void submitQuestion(String rawQuestion) {
        String question = rawQuestion == null ? "" : rawQuestion.trim();
        if (question.length() < 2 || processing) return;
        recognitionHandled = true;
        destroyRecognizer();
        processing = true;
        acquireWakeLock();
        updateState("DIASCO réfléchit…");
        sendEvent(EVENT_QUESTION, question);

        executor.execute(() -> {
            RemoteAiClient client = new RemoteAiClient();
            String answer = client.ask(
                    getString(R.string.default_backend_endpoint).trim(),
                    question,
                    Locale.getDefault().toLanguageTag(),
                    ConversationMemory.buildHistory(this),
                    isDebugBuild()
            );
            String cleanAnswer = answer == null ? "" : answer.trim();
            if (cleanAnswer.isEmpty()) cleanAnswer = "Je n’ai pas reçu de réponse exploitable.";
            ConversationMemory.appendTurn(this, question, cleanAnswer);
            sendEvent(EVENT_ANSWER, cleanAnswer);
            String speechText = speechSummary(question, cleanAnswer);
            mainHandler.post(() -> {
                updateState("Réponse prête");
                speakThen(speechText, () -> {
                    processing = false;
                    releaseWakeLock();
                    startListeningSoon(MODE_WAKE, 500L);
                });
            });
        });
    }

    private String speechSummary(String question, String answer) {
        String normalized = TextUtils.normalizeForIntent(question);
        if (normalized.contains("site web") || normalized.contains("site internet") || normalized.contains("page web")) {
            return "J’ai préparé le site. Ouvrez DIASCO pour le prévisualiser.";
        }
        if (normalized.contains("code") || normalized.contains("programme") || normalized.contains("script")) {
            return "J’ai préparé le code. Ouvrez DIASCO pour le consulter et le copier.";
        }
        if (normalized.contains("image") || normalized.contains("dessin") || normalized.contains("logo")) {
            return "Ouvrez DIASCO pour lancer et afficher la création de l’image.";
        }
        String compact = answer.replace('\n', ' ').trim();
        return compact.length() > 700 ? compact.substring(0, 700) + "…" : compact;
    }

    private String findWakeMatch(ArrayList<String> matches) {
        if (matches == null) return null;
        for (String match : matches) {
            if (WakePhrase.matches(match)) return match;
        }
        return null;
    }

    private String firstNonEmpty(ArrayList<String> matches) {
        if (matches == null) return null;
        for (String match : matches) {
            if (match != null && !match.trim().isEmpty()) return match.trim();
        }
        return null;
    }

    private String shorten(String text) {
        String clean = text == null ? "" : text.trim();
        return clean.length() > 42 ? clean.substring(0, 42) + "…" : clean;
    }

    private void speakThen(String text, Runnable afterSpeech) {
        if (!ttsReady || tts == null) {
            mainHandler.postDelayed(afterSpeech, Math.max(900L, Math.min(5_000L, text.length() * 45L)));
            return;
        }
        String utteranceId = "diasco-service-" + (++utteranceCounter);
        speechCallbacks.put(utteranceId, afterSpeech);
        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
        if (result == TextToSpeech.ERROR) {
            speechCallbacks.remove(utteranceId);
            mainHandler.post(afterSpeech);
        }
    }

    private void runSpeechCallback(String utteranceId) {
        Runnable callback = speechCallbacks.remove(utteranceId);
        if (callback != null) mainHandler.post(callback);
    }

    private void destroyRecognizer() {
        recognitionActive = false;
        if (recognizer != null) {
            try {
                recognizer.cancel();
                recognizer.destroy();
            } catch (RuntimeException ignored) {
                // Certains moteurs vocaux sont déjà détruits après leur callback final.
            }
            recognizer = null;
        }
    }

    private void acquireWakeLock() {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(60_000L);
        }
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    private void updateState(String state) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(state));
        sendEvent(EVENT_STATUS, state);
    }

    private void sendEvent(String event, String text) {
        Intent intent = new Intent(ACTION_EVENT)
                .setPackage(getPackageName())
                .putExtra(EXTRA_EVENT, event)
                .putExtra(EXTRA_TEXT, text == null ? "" : text);
        sendBroadcast(intent);
    }

    private Notification notification(String state) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent openApp = PendingIntent.getActivity(this, 10, openIntent, pendingFlags);

        Intent stopIntent = new Intent(this, WakeWordService.class).setAction(ACTION_STOP);
        PendingIntent stopService = PendingIntent.getService(this, 11, stopIntent, pendingFlags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(getString(R.string.wake_notification_title))
                .setContentText(state)
                .setContentIntent(openApp)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_delete, "Arrêter", stopService)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_notification_channel),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Maintient l’écoute de la phrase « Dis Diasco » lorsque l’écran est verrouillé.");
        channel.setSound(null, null);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
