package com.wordlock.app;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class Speaker implements TextToSpeech.OnInitListener {
    private static Speaker inst;
    private final TextToSpeech tts; private boolean ok;

    static synchronized Speaker get(Context c) { if (inst == null) inst = new Speaker(c.getApplicationContext()); return inst; }

    private Speaker(Context c) { tts = new TextToSpeech(c, this); }

    @Override public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) { int r = tts.setLanguage(Locale.US); ok = r >= 0; tts.setSpeechRate(0.9f); }
    }

    void speak(String w) { if (ok) tts.speak(w, TextToSpeech.QUEUE_FLUSH, null, "w"); }
}
