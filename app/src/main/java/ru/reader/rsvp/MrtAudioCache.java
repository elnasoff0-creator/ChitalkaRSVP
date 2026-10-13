package ru.reader.rsvp;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serial writer for original encoded audio frames intercepted before WebAudio decoding.
 * The WebView sends short Base64 blocks; a whole chapter is never held in the JS bridge.
 * A completed file is exposed only after the final block has been written and probed.
 */
public final class MrtAudioCache {
    public interface Callback { void onReady(File file, String error); }
    private final File directory;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private FileOutputStream output;
    private File temporary;
    private String currentKey = "";
    private int currentSession = -1;
    private long written;
    private int chunks;
    private volatile boolean closed;
    private final long maxBytes;

    public MrtAudioCache(Context context, int limitMb) {
        directory = new File(context.getCacheDir(), "mrt_encoded_audio");
        directory.mkdirs();
        maxBytes = Math.max(20, Math.min(500, limitMb)) * 1024L * 1024L;
    }

    public static String key(String text, String voice, String speed, String emotion) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    ("mrt-prefetch-v1\n" + voice + "\n" + speed + "\n" + emotion + "\n" + text)
                            .getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } catch (Exception e) { return Integer.toHexString(text.hashCode()); }
    }

    public File cached(String key) {
        File file = new File(directory, key + ".mp3");
        if (file.isFile() && file.length() > 1024) {
            file.setLastModified(System.currentTimeMillis());
            return file;
        }
        return null;
    }

    public void begin(int session, String key) {
        writer.execute(() -> {
            discard();
            if (closed) return;
            currentSession = session;
            currentKey = key;
            temporary = new File(directory, key + ".part");
            try { output = new FileOutputStream(temporary, false); }
            catch (Exception ignored) { discard(); }
        });
    }

    public void append(int session, String base64) {
        writer.execute(() -> {
            if (closed || session != currentSession || output == null) return;
            try {
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                if (bytes.length == 0 || bytes.length > 65536 || written + bytes.length > 24L * 1024 * 1024) {
                    discard(); return;
                }
                output.write(bytes);
                written += bytes.length;
                chunks++;
            } catch (Exception ignored) { discard(); }
        });
    }

    public void finish(int session, Callback callback) {
        writer.execute(() -> {
            File result = null;
            String error = null;
            if (!closed && session == currentSession && output != null) {
                try {
                    output.flush();
                    output.close();
                    output = null;
                    if (written < 2048 || chunks == 0) throw new Exception("MRT не передал достаточно аудиоданных");
                    if (!looksLikeMp3(temporary)) throw new Exception("Перехваченные данные не похожи на MP3");
                    // MediaMetadataRetriever is the same framework that the audiobook player uses.
                    MediaMetadataRetriever probe = new MediaMetadataRetriever();
                    try {
                        probe.setDataSource(temporary.getAbsolutePath());
                        String duration = probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (duration == null || Long.parseLong(duration) <= 0)
                            throw new Exception("Android не смог определить длительность MP3");
                    } finally { try { probe.release(); } catch (Exception ignored) {} }
                    File target = new File(directory, currentKey + ".mp3");
                    if (target.exists() && !target.delete()) throw new Exception("Ошибка замены кэша");
                    if (!temporary.renameTo(target)) throw new Exception("Не удалось сохранить MP3 в кэш");
                    result = target;
                    cleanup();
                } catch (Exception e) { error = e.getMessage(); }
            } else error = "Сеанс синтеза прерван";
            if (temporary != null && temporary.exists()) temporary.delete();
            currentSession = -1; currentKey = ""; temporary = null; written = 0; chunks = 0;
            File ready = result;
            String failure = error;
            ui.post(() -> callback.onReady(ready, failure));
        });
    }

    /** Reject obvious HTML/JSON/WAV/OGG instead of passing them off as MP3. */
    private boolean looksLikeMp3(File file) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            byte[] head = new byte[4096];
            int n = in.read(head);
            if (n < 4) return false;
            if (head[0]=='I' && head[1]=='D' && head[2]=='3') return true;
            for (int i=0;i<n-3;i++) {
                int b=head[i]&255,c=head[i+1]&255;
                if (b==0xff && (c&0xe0)==0xe0 && ((c>>1)&3)!=0 && ((c>>3)&3)!=3) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public void abort() { writer.execute(this::discard); }
    private void discard() {
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        output = null;
        if (temporary != null && temporary.exists()) temporary.delete();
        temporary = null; currentSession = -1; currentKey = ""; chunks = 0; written = 0;
    }
    private void cleanup() {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (files == null) return;
        long total = 0;
        for (File file : files) total += file.length();
        if (total <= maxBytes) return;
        java.util.Arrays.sort(files, (a,b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File file : files) {
            if (total <= maxBytes) break;
            long size = file.length();
            if (file.delete()) total -= size;
        }
    }
    public void close() { closed = true; writer.execute(this::discard); writer.shutdown(); }
}
