package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.File;
import java.util.Locale;

/** User-visible names are keyed by stable URIs/job IDs, never by file names.
 * Renaming must not move HTML, CBZ, MP3 or invalidate reading progress. */
final class BookTitleStore {
    private static final String PREF = "book_display_titles_v156";
    static final String TEXT = "text", AUDIO = "audio", COMIC = "comic", DOWNLOAD = "download";
    private BookTitleStore() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
    private static String key(String kind, String id) { return kind + "|" + id; }

    static String title(Context c, String kind, String id, String original) {
        String fallback = original == null ? "" : original;
        if (id == null || id.isEmpty()) return fallback;
        String custom = prefs(c).getString(key(kind, id), "");
        return custom == null || custom.isEmpty() ? fallback : custom;
    }

    /** Keep an explicitly renamed text book named after its file URI changes. */
    static synchronized void moveTextId(Context c,String oldUri,String newUri){
        SharedPreferences p=prefs(c);
        SharedPreferences.Editor edit=p.edit();
        for(String kind:new String[]{TEXT,DOWNLOAD}){
            String old=key(kind,kind.equals(DOWNLOAD)?"edge:"+oldUri:oldUri);
            String newer=key(kind,kind.equals(DOWNLOAD)?"edge:"+newUri:newUri);
            if(p.contains(old))edit.putString(newer,p.getString(old,""));
        }
        edit.commit();
    }

    static synchronized void moveComicId(Context c,String oldUri,String newUri){
        SharedPreferences p=prefs(c);String name=p.getString(key(COMIC,oldUri),"");
        if(name!=null&&!name.isEmpty())p.edit().putString(key(COMIC,newUri),name).commit();
    }

    /** Single-line, human-readable label; never used as a filesystem path. */
    static String clean(String raw) {
        if (raw == null) return "";
        String name = raw.replaceAll("[\\p{Cntrl}\\p{Cf}]", " ")
            .replaceAll("\\s+", " ").trim();
        if (name.length() > 120) name = name.substring(0, 120).trim();
        return name;
    }

    static synchronized boolean renameText(Context c, String uri, String name) {
        name = clean(name);
        if (uri == null || uri.isEmpty() || name.isEmpty()) return false;
        SharedPreferences.Editor edit = prefs(c).edit().putString(key(TEXT, uri), name)
            .putString(key(DOWNLOAD, "edge:" + uri), name);
        String slug = ranobeSlug(uri);
        if(slug.isEmpty())slug=RanobeLibStorage.slugForBookUri(c,uri);
        if (!slug.isEmpty()) edit.putString(key(DOWNLOAD, "ranobe:" + slug), name);
        return edit.commit();
    }

    static synchronized boolean renameAudio(Context c, String id, String name) {
        name = clean(name);
        if (id == null || id.isEmpty() || name.isEmpty()) return false;
        SharedPreferences.Editor edit = prefs(c).edit().putString(key(AUDIO, id), name);
        if (id.startsWith("url:")) edit.putString(key(DOWNLOAD, "audio:" + id.substring(4)), name);
        if (id.startsWith("direct:")) edit.putString(key(DOWNLOAD, "link:" + id.substring(7)), name);
        return edit.commit();
    }

    static synchronized boolean renameComic(Context c, String uri, String name) {
        name = clean(name);
        if (uri == null || uri.isEmpty() || name.isEmpty()) return false;
        SharedPreferences.Editor edit = prefs(c).edit().putString(key(COMIC, uri), name);
        if (uri.startsWith("mangalib:"))
            edit.putString(key(DOWNLOAD, "manga:" + uri.substring("mangalib:".length()).toLowerCase(Locale.ROOT)), name);
        return edit.commit();
    }

    /** Synchronise titles of linked library records in one preferences transaction. */
    static synchronized boolean renameDownload(Context c, String id, String type,
                                                String bookUri, String source, String name) {
        name = clean(name);
        if (id == null || id.isEmpty() || name.isEmpty()) return false;
        SharedPreferences.Editor edit = prefs(c).edit().putString(key(DOWNLOAD, id), name);
        if (DownloadsRepository.RANOBE.equals(type) || DownloadsRepository.EDGE.equals(type)) {
            if (bookUri != null && !bookUri.isEmpty()) edit.putString(key(TEXT, bookUri), name);
        } else if (DownloadsRepository.MANGA.equals(type)) {
            if (bookUri != null && !bookUri.isEmpty()) edit.putString(key(COMIC, bookUri), name);
        } else if (DownloadsRepository.AUDIO.equals(type)) {
            if (source != null && !source.isEmpty()) edit.putString(key(AUDIO, "url:" + source), name);
        } else if (DownloadsRepository.LINK.equals(type)) {
            if (id.startsWith("link:")) edit.putString(key(AUDIO, "direct:" + id.substring(5)), name);
        }
        return edit.commit();
    }

    static String downloadTitle(Context c, DownloadsRepository.Item item) {
        String fallback = item.title;
        if (DownloadsRepository.RANOBE.equals(item.type) || DownloadsRepository.EDGE.equals(item.type))
            fallback = title(c, TEXT, item.bookUri, fallback);
        else if (DownloadsRepository.MANGA.equals(item.type))
            fallback = title(c, COMIC, item.bookUri, fallback);
        else if (DownloadsRepository.AUDIO.equals(item.type))
            fallback = title(c, AUDIO, "url:" + item.source, fallback);
        else if (DownloadsRepository.LINK.equals(item.type) && item.id.startsWith("link:"))
            fallback = title(c, AUDIO, "direct:" + item.id.substring(5), fallback);
        return title(c, DOWNLOAD, item.id, fallback);
    }

    private static String ranobeSlug(String uri) {
        try {
            Uri parsed = Uri.parse(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) return "";
            File file = new File(parsed.getPath());
            File series = file.getParentFile();
            if ("book.html".equals(file.getName()) && series != null
                    && series.getParentFile() != null
                    && "RanobeLIB".equals(series.getParentFile().getName()))
                return series.getName();
        } catch (Exception ignored) { }
        return "";
    }
}
