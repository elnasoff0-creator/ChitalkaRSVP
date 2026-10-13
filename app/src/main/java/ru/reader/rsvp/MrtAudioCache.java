package ru.reader.rsvp;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.os.Environment;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
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
    public interface Callback { void onReady(File file, long bytes, int chunks, String error); }
    public interface ClearCallback { void onCleared(); }
    private final File directory;
    private final File bookDirectory;
    private final File legacyDirectory;
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

    /** A persistent, book-specific app library. The original content Uri may not allow sibling writes. */
    public MrtAudioCache(Context context, String bookUri, String bookTitle, int limitMb) {
        bookDirectory = bookFolder(context, bookUri, bookTitle);
        legacyDirectory = new File(context.getCacheDir(), "mrt_encoded_audio");
        directory = new File(bookDirectory, "Аудио");
        directory.mkdirs();
        // No automatic eviction: preserve audio while the book remains in the library.
        maxBytes = Math.max(20, Math.min(500, limitMb)) * 1024L * 1024L;
    }

    private static final String TAG = "MRT-AudioCache";
    private static File bookRoot(Context context) {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (root == null) root = context.getFilesDir();
        return new File(root, "ChitalkaRSVP/Books");
    }

    private static String bookId(String uri) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(uri.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i=0;i<8;i++) hex.append(String.format(java.util.Locale.ROOT, "%02x",digest[i]&255));
            return hex.toString();
        } catch (Exception ex) { return Integer.toHexString(uri.hashCode()); }
    }
    public static File bookFolder(Context context, String uri, String title) {
        String clean = title == null ? "Книга" : title.trim().replaceAll("[\\/:*?\"<>|\\p{Cntrl}]", "_");
        if (clean.isEmpty()) clean = "Книга";
        if (clean.length() > 65) clean = clean.substring(0, 65);
        return new File(bookRoot(context), clean + "--" + bookId(uri));
    }
    public static void removeBook(Context context, String uri) {
        final File root = bookRoot(context);
        final String suffix = "--" + bookId(uri);
        // Never block the book-library UI while deleting a large offline cache.
        new Thread(() -> {
            File[] dirs = root.listFiles();
            if (dirs != null) for (File d:dirs) if(d.isDirectory() && d.getName().endsWith(suffix)) deleteTree(d);
        }, "mrt-remove-book-audio").start();
    }
    private static void deleteTree(File directory) {
        File[] files = directory.listFiles();
        if (files != null) for (File f:files) {
            if (f.isDirectory()) deleteTree(f); else f.delete();
        }
        directory.delete();
    }

    /** Copies the original electronic book alongside Audio/, if an imported file isn't already here. */
    public void keepBookCopy(Context ctx, String uri) {
        final String bookUri = uri;
        new Thread(() -> {
            if (closed || bookUri == null || bookUri.isEmpty()) return;
            String filename = "Книга.epub";
            try (android.database.Cursor cursor = ctx.getContentResolver().query(Uri.parse(bookUri),
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null)
                    filename = cursor.getString(0);
            } catch (Exception ignored) {}
            filename = filename.replaceAll("[\\/:*?\"<>|\\p{Cntrl}]", "_");
            File copy = new File(bookDirectory, filename);
            if (copy.exists() && copy.length()>0) return;
            File temp = new File(bookDirectory,".book-copy.part");
            try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(bookUri));
                 FileOutputStream out = new FileOutputStream(temp)) {
                if (in==null) return;
                byte[] block = new byte[8192]; int n; long bytes=0;
                while ((n=in.read(block))!=-1) {
                    bytes+=n; if (bytes>150L*1024*1024) throw new Exception("Book too large");
                    out.write(block,0,n);
                }
                if (!temp.renameTo(copy)) temp.delete();
            } catch (Exception ignored) { temp.delete(); }
        }, "mrt-keep-book-copy").start();
    }

    public File directory() { return directory; }
    public String readIndex() {
        File index = new File(directory,"queue.json");
        if (!index.isFile()) return "[]";
        try (FileInputStream in = new FileInputStream(index)) {
            byte[] bytes = new byte[(int)Math.min(24*1024*1024, index.length())];
            int total=0, n;
            while (total<bytes.length && (n=in.read(bytes,total,bytes.length-total))>0) total+=n;
            return new String(bytes,0,total,StandardCharsets.UTF_8);
        } catch (Exception e) { return "[]"; }
    }
    public void saveIndex(String json) {
        writer.execute(() -> {
            if (closed) return;
            File tmp = new File(directory,"queue.json.tmp");
            File target = new File(directory,"queue.json");
            try (FileOutputStream stream = new FileOutputStream(tmp)) {
                stream.write(json.getBytes(StandardCharsets.UTF_8));
                stream.flush();
                if (target.exists() && !target.delete()) return;
                tmp.renameTo(target);
            } catch (Exception ignored) { tmp.delete(); }
        });
    }
    public void clear(ClearCallback callback) {
        writer.execute(() -> {
            discard();
            File[] files = directory.listFiles();
            if (files != null) for (File file:files) if (file.isFile()) file.delete();
            // Do not resurrect deleted clips from the old shared cache on next Play.
            try { new File(bookDirectory,".legacy_migration_disabled").createNewFile(); }
            catch (Exception ignored) {}
            ui.post(callback::onCleared);
        });
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
        for(String ext:MrtAudioFormat.EXTENSIONS){
            File ready=new File(directory,key+ext);
            if(ready.isFile()&&ready.length()>1024){ready.setLastModified(System.currentTimeMillis());return ready;}
        }
        File legacyFile = new File(directory,key+".mp3");
        // Migrate previously downloaded encoded audio once, keeping the original bytes.
        if (!new File(bookDirectory,".legacy_migration_disabled").exists()) {
            File old = new File(legacyDirectory,key + ".mp3");
            if (old.isFile() && old.length() > 1024 && old.length() <= 24L*1024*1024) {
                try (FileInputStream in = new FileInputStream(old);
                     FileOutputStream out = new FileOutputStream(legacyFile)) {
                    byte[] block = new byte[16384]; int n;
                    while ((n=in.read(block))!=-1)out.write(block,0,n);
                } catch (Exception ex) { legacyFile.delete(); }
                if(legacyFile.isFile()&&legacyFile.length()>1024)return legacyFile;
            }
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
            long completedBytes = written;
            int completedChunks = chunks;
            if (!closed && session == currentSession && output != null) {
                try {
                    output.flush();
                    output.close();
                    output = null;
                    if (written < 2048 || chunks == 0) throw new Exception("MRT не передал достаточно аудиоданных");
                    // decodeAudioData may receive WAV/OGG/AAC/WebM, not only MP3.
                    MrtAudioFormat format = MrtAudioFormat.identify(temporary);
                    if (format == null) throw new Exception("Неизвестный аудиоформат; первые байты: "
                            + MrtAudioFormat.signature(temporary));
                    // The Android platform metadata retriever may be stricter than
                    // Media3 for uncommon encodings. Non-fatal: the player validates by decoding.
                    MediaMetadataRetriever probe = new MediaMetadataRetriever();
                    try {
                        probe.setDataSource(temporary.getAbsolutePath());
                        String duration = probe.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (duration == null || Long.parseLong(duration) <= 0)
                            Log.w(TAG,"No platform duration for "+format.label+"; will ask ExoPlayer to decode");
                    } catch (Exception ex) {
                        Log.w(TAG,"Android metadata probe unavailable for "+format.label,ex);
                    } finally { try { probe.release(); } catch (Exception ignored) {} }
                    File target = new File(directory, currentKey + format.extension);
                    if (target.exists() && !target.delete()) throw new Exception("Ошибка замены кэша");
                    if (!temporary.renameTo(target)) throw new Exception("Не удалось сохранить "+format.label+" в кэш");
                    Log.i(TAG,"Saved "+format.label+" "+written+" bytes to "+target.getName());
                    result = target;
                    // Persistent book cache: intentionally do not evict earlier played clips.
                } catch (Exception e) { error = e.getMessage(); }
            } else error = "Сеанс синтеза прерван";
            if (temporary != null && temporary.exists()) temporary.delete();
            currentSession = -1; currentKey = ""; temporary = null; written = 0; chunks = 0;
            File ready = result;
            String failure = error;
            ui.post(() -> callback.onReady(ready, completedBytes, completedChunks, failure));
        });
    }

    public void abort() { writer.execute(this::discard); }
    private void discard() {
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        output = null;
        if (temporary != null && temporary.exists()) temporary.delete();
        temporary = null; currentSession = -1; currentKey = ""; chunks = 0; written = 0;
    }
    private void cleanup() {
        File[] files = directory.listFiles((dir, name) -> MrtAudioFormat.forFilename(name)!=null);
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
    public void close() {
        // Flush queued index writes before closing; otherwise recent history is lost
        // when the user returns to the library immediately after playback.
        writer.execute(() -> { discard(); closed = true; });
        writer.shutdown();
    }
}
