package com.dashai.app.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.ArrayList;
import java.util.Locale;

public final class VoiceAuthenticator {
    public static final double DEFAULT_THRESHOLD = 0.54;

    private static final int SAMPLE_RATE = 16_000;
    private static final int RECORD_MS = 2_600;
    private static final int MIN_VOICE_SAMPLES = SAMPLE_RATE / 2;
    private static final double MIN_RMS = 280.0;
    private static final double[] PROBE_FREQUENCIES = {120, 220, 360, 520, 760, 1100, 1600, 2400, 3400};

    private VoiceAuthenticator() {
    }

    public static VoiceSample captureSample(Context context) throws VoiceAuthException {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new VoiceAuthException("Permission micro manquante.");
        }

        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBuffer <= 0) {
            throw new VoiceAuthException("Micro indisponible pour l’empreinte vocale.");
        }

        int targetSamples = SAMPLE_RATE * RECORD_MS / 1000;
        int bufferSize = Math.max(minBuffer, SAMPLE_RATE);
        short[] captured = new short[targetSamples];
        short[] buffer = new short[bufferSize];

        AudioRecord recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
        );

        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            throw new VoiceAuthException("Impossible d’initialiser le micro.");
        }

        int offset = 0;
        try {
            recorder.startRecording();
            long deadline = System.currentTimeMillis() + RECORD_MS + 1200L;
            while (offset < targetSamples && System.currentTimeMillis() < deadline) {
                int read = recorder.read(buffer, 0, Math.min(buffer.length, targetSamples - offset));
                if (read > 0) {
                    System.arraycopy(buffer, 0, captured, offset, read);
                    offset += read;
                }
            }
        } finally {
            try {
                recorder.stop();
            } catch (IllegalStateException ignored) {
                // Le recorder peut déjà être arrêté selon l'état du périphérique.
            }
            recorder.release();
        }

        if (offset < MIN_VOICE_SAMPLES) {
            throw new VoiceAuthException("Échantillon vocal trop court.");
        }

        short[] actual = new short[offset];
        System.arraycopy(captured, 0, actual, 0, offset);
        short[] voice = trimSilence(actual);
        double rms = rms(voice);
        if (rms < MIN_RMS) {
            throw new VoiceAuthException("Voix trop faible. Rapproche-toi du micro et réessaie.");
        }
        double[] features = extractFeatures(voice, rms);
        return new VoiceSample(features, rms);
    }

    public static String serialize(double[] features) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < features.length; i++) {
            if (i > 0) builder.append(',');
            builder.append(String.format(Locale.US, "%.8f", features[i]));
        }
        return builder.toString();
    }

    public static double[] deserialize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String[] parts = raw.trim().split(",");
        if (parts.length < 8) return null;
        double[] values = new double[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                values[i] = Double.parseDouble(parts[i]);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return values;
    }

    public static double similarity(double[] expected, double[] actual) {
        if (expected == null || actual == null || expected.length != actual.length) return 0.0;
        double distance = 0.0;
        for (int i = 0; i < expected.length; i++) {
            double weight = i == 0 ? 0.05 : 1.0;
            double diff = expected[i] - actual[i];
            distance += weight * diff * diff;
        }
        distance = Math.sqrt(distance / expected.length);
        return Math.max(0.0, Math.min(1.0, Math.exp(-3.0 * distance)));
    }

    private static short[] trimSilence(short[] samples) {
        double rms = rms(samples);
        double threshold = Math.max(180.0, rms * 0.18);
        int start = 0;
        int end = samples.length - 1;
        while (start < samples.length && Math.abs(samples[start]) < threshold) start++;
        while (end > start && Math.abs(samples[end]) < threshold) end--;
        int length = Math.max(0, end - start + 1);
        if (length < MIN_VOICE_SAMPLES) return samples;
        short[] trimmed = new short[length];
        System.arraycopy(samples, start, trimmed, 0, length);
        return trimmed;
    }

    private static double[] extractFeatures(short[] samples, double rms) throws VoiceAuthException {
        if (samples.length < MIN_VOICE_SAMPLES) {
            throw new VoiceAuthException("Échantillon vocal trop court.");
        }

        ArrayList<Double> features = new ArrayList<>();
        features.add(Math.log1p(rms) / 10.0);
        features.add(zeroCrossingRate(samples));
        features.add(estimatePitch(samples) / 400.0);
        features.add(energyRatio(samples, 0, 4));
        features.add(energyRatio(samples, 1, 4));
        features.add(energyRatio(samples, 2, 4));
        features.add(energyRatio(samples, 3, 4));

        double totalProbeEnergy = 0.0;
        double[] probeEnergies = new double[PROBE_FREQUENCIES.length];
        for (int i = 0; i < PROBE_FREQUENCIES.length; i++) {
            probeEnergies[i] = goertzelEnergy(samples, PROBE_FREQUENCIES[i]);
            totalProbeEnergy += probeEnergies[i];
        }
        if (totalProbeEnergy <= 0.0) totalProbeEnergy = 1.0;
        for (double probeEnergy : probeEnergies) {
            features.add(probeEnergy / totalProbeEnergy);
        }

        double[] result = new double[features.size()];
        for (int i = 0; i < features.size(); i++) {
            result[i] = features.get(i);
        }
        return result;
    }

    private static double rms(short[] samples) {
        double sum = 0.0;
        for (short sample : samples) {
            sum += sample * (double) sample;
        }
        return Math.sqrt(sum / Math.max(1, samples.length));
    }

    private static double zeroCrossingRate(short[] samples) {
        int crossings = 0;
        for (int i = 1; i < samples.length; i++) {
            if ((samples[i - 1] < 0 && samples[i] >= 0) || (samples[i - 1] >= 0 && samples[i] < 0)) {
                crossings++;
            }
        }
        return crossings / (double) samples.length;
    }

    private static double estimatePitch(short[] samples) {
        int minLag = SAMPLE_RATE / 360;
        int maxLag = SAMPLE_RATE / 75;
        double bestScore = 0.0;
        int bestLag = SAMPLE_RATE / 160;

        for (int lag = minLag; lag <= maxLag; lag++) {
            double sum = 0.0;
            double energyA = 0.0;
            double energyB = 0.0;
            for (int i = 0; i + lag < samples.length; i += 2) {
                double a = samples[i];
                double b = samples[i + lag];
                sum += a * b;
                energyA += a * a;
                energyB += b * b;
            }
            double score = sum / Math.sqrt(Math.max(1.0, energyA * energyB));
            if (score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }
        return SAMPLE_RATE / (double) bestLag;
    }

    private static double energyRatio(short[] samples, int bucket, int bucketCount) {
        int start = samples.length * bucket / bucketCount;
        int end = samples.length * (bucket + 1) / bucketCount;
        double bucketEnergy = 0.0;
        double totalEnergy = 0.0;
        for (int i = 0; i < samples.length; i++) {
            double value = samples[i] * (double) samples[i];
            totalEnergy += value;
            if (i >= start && i < end) bucketEnergy += value;
        }
        return bucketEnergy / Math.max(1.0, totalEnergy);
    }

    private static double goertzelEnergy(short[] samples, double frequency) {
        double normalized = frequency / SAMPLE_RATE;
        double coeff = 2.0 * Math.cos(2.0 * Math.PI * normalized);
        double s0;
        double s1 = 0.0;
        double s2 = 0.0;
        int step = Math.max(1, samples.length / 6000);
        for (int i = 0; i < samples.length; i += step) {
            s0 = samples[i] + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return s1 * s1 + s2 * s2 - coeff * s1 * s2;
    }

    public static final class VoiceSample {
        public final double[] features;
        public final double rms;

        private VoiceSample(double[] features, double rms) {
            this.features = features;
            this.rms = rms;
        }
    }

    public static final class VoiceAuthException extends Exception {
        public VoiceAuthException(String message) {
            super(message);
        }
    }
}
