package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;

/** One current book across normal, Cursor, RSVP and TTS. No Activity is retained.
 * Parse and build indexes once per book during an app process; services obtain
 * this immutable snapshot instead of reopening EPUB/FB2 on a mode switch.
 */
final class UnifiedBookSession {
    final String uri;
    final BookLoader.Book book;
    final ReadingWordMap words;
    final BookChunkIndex blocks;
    final java.util.ArrayList<Integer> ttsWordPositions;
    final java.util.ArrayList<SentenceChunks.Chunk> speechSegments;
    private static volatile UnifiedBookSession active;
    private UnifiedBookSession(String u,BookLoader.Book b,ReadingWordMap w){
        uri=u;book=b;words=w;blocks=new BookChunkIndex(b.text,w,560);
        ttsWordPositions=new java.util.ArrayList<>(w.starts.length);
        for(int value:w.starts)ttsWordPositions.add(value);
        speechSegments=SentenceChunks.split(b.text);
    }
    static UnifiedBookSession current(String uri){
        UnifiedBookSession s=active;
        return s!=null&&s.uri.equals(uri)?s:null;
    }
    static synchronized UnifiedBookSession open(Context context,Uri source)throws Exception {
        String uri=source.toString();UnifiedBookSession s=current(uri);
        if(s!=null)return s;
        // RBI3 carries the parsed text, all word offsets and the structural
        // TOC. An EPUB/FB2 need not be reopened even after process restart.
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
        active=new UnifiedBookSession(uri,book,map);
        return active;
    }
}
