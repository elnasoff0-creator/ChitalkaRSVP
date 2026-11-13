package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;

/** One current book across normal, Cursor, RSVP and TTS. No Activity is retained.
 * Parse and build indexes once per book during an app process; services obtain
 * this immutable snapshot instead of reopening EPUB/FB2 on a mode switch.
 */
final class UnifiedBookSession {
    final String uri;
    final String revision;
    final BookLoader.Book book;
    final ReadingWordMap words;
    final BookChunkIndex blocks;
    final java.util.ArrayList<Integer> ttsWordPositions;
    final java.util.ArrayList<SentenceChunks.Chunk> speechSegments;
    private static volatile UnifiedBookSession active;
    private volatile java.util.ArrayList<SentenceChunks.Chunk> edgeGroups;
    synchronized java.util.ArrayList<SentenceChunks.Chunk> edgeGroups(){
        if(edgeGroups==null)edgeGroups=EdgeSpeechSegments.split(book.text,book.chapters);
        return edgeGroups;
    }
    private UnifiedBookSession(String u,String rev,BookLoader.Book b,ReadingWordMap w){
        uri=u;revision=rev;book=b;words=w;blocks=new BookChunkIndex(b.text,w,560);
        ttsWordPositions=new java.util.ArrayList<>(w.starts.length);
        for(int value:w.starts)ttsWordPositions.add(value);
        speechSegments=SentenceChunks.split(b.text);
    }
    static UnifiedBookSession current(String uri){
        UnifiedBookSession s=active;
        return s!=null&&s.uri.equals(uri)?s:null;
    }
    static synchronized void invalidate(String uri){
        if(active!=null&&active.uri.equals(uri))active=null;
    }
    static UnifiedBookSession open(Context context,Uri source)throws Exception {
        String uri=source.toString();
        // Source checks and rebuilding are off the UI thread in ReaderActivity.load().
        if(HtmlFolderLibrary.manages(context,uri))HtmlFolderLibrary.syncIfChanged(context,uri);
        else if(!RanobeLibStorage.slugForBookUri(context,uri).isEmpty())RanobeLibStorage.syncManualEdits(context,uri);
        String fingerprint=ReaderBookCache.revision(context,source);
        UnifiedBookSession s=current(uri);
        if(s!=null&&!fingerprint.isEmpty()&&fingerprint.equals(s.revision))return s;
        // RBI4 carries parsed text, word offsets, a structural TOC and revision.
        // EPUB/FB2 need not be reopened after restart when the source is unchanged.
        ReaderBookCache.CachedBook cached=ReaderBookCache.load(context,source);
        BookLoader.Book book;
        ReadingWordMap map;
        if(cached!=null){
            book=new BookLoader.Book(cached.title,cached.author,cached.text,cached.chapters);
            map=cached.map;
        }else{
            book=BookLoader.load(context,source);
            if(book.text==null||book.text.trim().isEmpty())
                throw new IllegalArgumentException("Нет текста книги");
            map=ReadingWordMap.parse(book.text);
            if(map.size()==0)throw new IllegalArgumentException("В книге нет слов");
            ReaderBookCache.store(context,source,book.title,book.author,book.text,map,book.chapters);
        }
        UnifiedBookSession prepared=new UnifiedBookSession(uri,fingerprint,book,map);
        // Hold the session lock only for publishing the immutable snapshot.
        // Never wait on SAF folder I/O while holding it: manual sync also invalidates it.
        synchronized(UnifiedBookSession.class){active=prepared;}
        return prepared;
    }
}
