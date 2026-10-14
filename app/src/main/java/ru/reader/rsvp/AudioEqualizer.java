package ru.reader.rsvp;

import android.content.Context;
import android.media.audiofx.Equalizer;

/** Optional audio-session equalizer; unsupported devices simply play unprocessed audio. */
public final class AudioEqualizer {
    private static Equalizer effect;
    private static int session = -1;
    private AudioEqualizer() {}

    public static synchronized void attach(Context context, int audioSessionId) {
        if (audioSessionId <= 0) return;
        if (session != audioSessionId || effect == null) {
            release();
            try {
                effect = new Equalizer(0, audioSessionId);
                session = audioSessionId;
            } catch (Exception | LinkageError ignored) {
                effect = null;
                session = -1;
            }
        }
        apply(context);
    }

    public static synchronized void apply(Context context) {
        if (effect == null) return;
        String mode = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
                .getString("audio_equalizer", "off");
        try {
            if ("off".equals(mode)) { effect.setEnabled(false); return; }
            short[] range = effect.getBandLevelRange();
            short min = range[0], max = range[1];
            for (short band = 0; band < effect.getNumberOfBands(); band++) {
                int hz = effect.getCenterFreq(band) / 1000;
                float fraction = 0f;
                if ("bass".equals(mode)) fraction = hz < 300 ? 0.72f : hz < 1000 ? 0.2f : 0f;
                else if ("treble".equals(mode)) fraction = hz > 3000 ? 0.65f : hz > 1000 ? 0.2f : 0f;
                else if ("voice".equals(mode)) fraction = hz >= 450 && hz <= 4000 ? 0.55f : 0f;
                short gain = (short) Math.max(min, Math.min(max, (int) (max * fraction)));
                effect.setBandLevel(band, gain);
            }
            effect.setEnabled(true);
        } catch (Exception | LinkageError ignored) { release(); }
    }

    public static synchronized void release() {
        if (effect != null) {
            try { effect.setEnabled(false); effect.release(); } catch (Exception ignored) {}
        }
        effect = null;
        session = -1;
    }
}
