package com.dashai.app.voice;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WakePhraseTest {
    @Test
    public void acceptsCommonFrenchTranscriptions() {
        assertTrue(WakePhrase.matches("Dis Diasco"));
        assertTrue(WakePhrase.matches("dit diasco"));
        assertTrue(WakePhrase.matches("dis dia sko"));
        assertTrue(WakePhrase.matches("Dix Diasco"));
    }

    @Test
    public void rejectsUnrelatedSpeech() {
        assertFalse(WakePhrase.matches("dis moi la météo"));
        assertFalse(WakePhrase.matches("ouvre la caméra"));
        assertFalse(WakePhrase.matches("diaspora"));
    }
}
