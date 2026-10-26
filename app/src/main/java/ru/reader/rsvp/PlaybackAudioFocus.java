package ru.reader.rsvp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

/** Exclusive audio focus shared by the audiobook/TTS service and the Edge TTS player.
 * Focus is never used to restart playback automatically after another app or call. */
final class PlaybackAudioFocus {
    private final AudioManager manager;
    private final Runnable interrupted;
    private final AudioManager.OnAudioFocusChangeListener listener;
    private AudioFocusRequest request;
    private boolean held;
    private boolean registered;
    private final boolean keepPlayingOnDuck;

    PlaybackAudioFocus(Context context, Runnable interrupted) {
        this(context,interrupted,false);
    }

    PlaybackAudioFocus(Context context, Runnable interrupted, boolean keepPlayingOnDuck) {
        this.keepPlayingOnDuck=keepPlayingOnDuck;
        this.manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.interrupted = interrupted;
        this.listener = change -> {
            if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && keepPlayingOnDuck) {
                // Notification / navigation sound: retain focus and keep the audiobook playing.
                // Some Android builds do not auto-duck speech, which is intentional here.
                return;
            }
            if (change == AudioManager.AUDIOFOCUS_LOSS ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                held = false;
                interrupted.run(); // Incoming call or another audio/video app: pause.
            } else if(change == AudioManager.AUDIOFOCUS_GAIN && registered){
                held = true; // Never auto-resume interrupted playback.
            }
        };
    }

    boolean acquire() {
        if (manager == null) return false;
        if (held) return true;
        int result;
        if (Build.VERSION.SDK_INT >= 26) {
            if (request == null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
                request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(!keepPlayingOnDuck)
                        .setOnAudioFocusChangeListener(listener).build();
            }
            result = manager.requestAudioFocus(request);
        } else {
            result = manager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
        held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(held)registered=true;
        return held;
    }

    void release() {
        if (manager == null) return;
        if (!registered) return;
        held = false;
        registered = false;
        if (Build.VERSION.SDK_INT >= 26 && request != null) manager.abandonAudioFocusRequest(request);
        else manager.abandonAudioFocus(listener);
    }
}
