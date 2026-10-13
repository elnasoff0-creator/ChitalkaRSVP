package ru.reader.rsvp;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.File;

/**
 * Native audio transport independent of MRT's WebView synthesis and its cache writer.
 * One MP3 is audible; the next can be prepared but NEVER started early.
 * All methods and callbacks run on the Activity main thread.
 */
public final class MrtPlaybackEngine {
    public interface Listener {
        void onStarted(Object clip);
        void onCompleted(Object clip);
        void onError(Object clip, String message);
    }

    private static final String TAG = "MRT-Playback";
    private final Listener listener;
    private MediaPlayer current;
    private MediaPlayer standby;
    private Object currentClip;
    private Object standbyClip;
    private boolean standbyReady;
    private boolean preparing;
    private boolean paused;

    public MrtPlaybackEngine(Listener listener) { this.listener = listener; }
    public boolean hasCurrent() { return current != null; }
    public boolean isPreparing() { return preparing; }
    public boolean isPaused() { return paused; }

    private void initialize(MediaPlayer player, File file, Object clip) throws Exception {
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        player.setDataSource(file.getAbsolutePath());
        player.setOnPreparedListener(mp -> {
            if (mp == current) {
                preparing = false;
                startCurrent();
            } else if (mp == standby) standbyReady = true;
        });
        player.setOnCompletionListener(mp -> {
            if (mp != current) return;
            Object finished = currentClip;
            current = null;
            currentClip = null;
            preparing = false;
            release(mp);
            listener.onCompleted(finished);
        });
        player.setOnErrorListener((mp, what, extra) -> {
            if (mp == standby) {
                standby = null;
                standbyClip = null;
                standbyReady = false;
                release(mp);
                Log.w(TAG, "Standby prepare failed: " + what + "/" + extra);
            } else if (mp == current) {
                Object failed = currentClip;
                current = null;
                currentClip = null;
                preparing = false;
                release(mp);
                listener.onError(failed, "MediaPlayer " + what + "/" + extra);
            }
            return true;
        });
    }

    /** Start only on explicit demand from the queue manager. */
    public void play(File file, Object clip) {
        if (current != null || file == null || !file.isFile()) return;
        if (standby != null && standbyClip == clip) {
            current = standby;
            currentClip = clip;
            preparing = !standbyReady;
            standby = null;
            standbyClip = null;
            standbyReady = false;
            if (!preparing) startCurrent();
            return;
        }
        clearStandby();
        MediaPlayer player = new MediaPlayer();
        current = player;
        currentClip = clip;
        preparing = true;
        try {
            initialize(player, file, clip);
            player.prepareAsync();
        } catch (Exception ex) {
            current = null;
            currentClip = null;
            preparing = false;
            release(player);
            listener.onError(clip, "Cannot open MP3: " + ex.getMessage());
        }
    }

    private void startCurrent() {
        if (current == null) return;
        try {
            if (!paused) current.start();
            listener.onStarted(currentClip);
        } catch (Exception ex) {
            Object failed = currentClip;
            MediaPlayer broken = current;
            current = null;
            currentClip = null;
            preparing = false;
            release(broken);
            listener.onError(failed, "Cannot start MP3: " + ex.getMessage());
        }
    }

    /** Prepare silently; the completion callback of the current file decides when to play it. */
    public void warm(File file, Object clip) {
        if (current == null || file == null || !file.isFile() || clip == currentClip) return;
        if (standby != null && standbyClip == clip) return;
        clearStandby();
        MediaPlayer player = new MediaPlayer();
        standby = player;
        standbyClip = clip;
        standbyReady = false;
        try {
            initialize(player, file, clip);
            player.prepareAsync();
        } catch (Exception ex) {
            Log.w(TAG, "Could not warm next MP3", ex);
            clearStandby();
        }
    }

    public void pause() {
        paused = true;
        if (current != null && !preparing) try { current.pause(); }
        catch (IllegalStateException ex) { Log.w(TAG, "Pause ignored", ex); }
    }

    public void resume() {
        paused = false;
        if (current != null && !preparing) try { current.start(); }
        catch (IllegalStateException ex) { Log.w(TAG, "Resume ignored", ex); }
    }

    public void stop() {
        paused = false;
        preparing = false;
        MediaPlayer active = current;
        current = null;
        currentClip = null;
        release(active);
        clearStandby();
    }

    private void clearStandby() {
        MediaPlayer player = standby;
        standby = null;
        standbyClip = null;
        standbyReady = false;
        release(player);
    }

    private static void release(MediaPlayer player) {
        if (player != null) try { player.release(); } catch (Exception ignored) {}
    }
}
