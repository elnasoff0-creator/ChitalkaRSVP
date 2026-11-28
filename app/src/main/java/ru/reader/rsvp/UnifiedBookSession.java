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
    final SentenceIndex sentenceIndex;
    private volatile ReaderTextReanchor openingMigration;
    int translateOpeningWord(int old){ReaderTextReanchor m=openingMigration;return m==null?old:m.word(old); }
    void finishOpeningMigration(){openingMigration=null;}
    private static volatile UnifiedBookSession active;
    private volatile java.util.ArrayList<SentenceChunks.Chunk> edgeGroups;
    private String edgeGroupsRevision="";
    synchronized java.util.ArrayList<SentenceChunks.Chunk> edgeGroups(){
        return edgeGroups(null,EdgeSpeechLayout.current());
    }
    synchronized java.util.ArrayList<SentenceChunks.Chunk> edgeGroups(EdgeSpeechTextTransform.Options options){
        return edgeGroups(options,EdgeSpeechLayout.current());
    }
    synchronized java.util.ArrayList<SentenceChunks.Chunk> edgeGroups(
            EdgeSpeechTextTransform.Options options,EdgeSpeechLayout layout){
        final String revision=(options==null?"raw":options.fingerprint())+
            "|"+layout.first+"|"+layout.normal+"|"+layout.legacyParagraphs;
        if(edgeGroups==null||!revision.equals(edgeGroupsRevision)){
            edgeGroups=EdgeSpeechSegments.splitIndexed(book.text,book.chapters,
                text->options==null?EdgeSpeechSize.fits(text):options.fitsSpeech(text),layout,
                speechSegments,ttsWordPositions);
            edgeGroupsRevision=revision;
        }
        return edgeGroups;
    }
    java.util.ArrayList<SentenceChunks.Chunk> startupEdgeGroups(EdgeSpeechTextTransform.Options options,
            EdgeSpeechLayout layout,int word){
        int at=sentenceIndex.atWord(word);
        // Avoid duplicating a potentially huge prefix on a late-book seek.
        if(at<0||at>512)return new java.util.ArrayList<>();
        return EdgeSpeechSegments.startupPrefix(book.text,book.chapters,
            text->options==null?EdgeSpeechSize.fits(text):options.fitsSpeech(text),layout,
            speechSegments,ttsWordPositions,at);
    }
    private UnifiedBookSession(String u,String rev,BookLoader.Book b,ReadingWordMap w){
        uri=u;revision=rev;book=b;words=w;blocks=new BookChunkIndex(b.text,w,560);
        ttsWordPositions=new java.util.ArrayList<>(w.starts.length);
        for(int value:w.starts)ttsWordPositions.add(value);
        speechSegments=EdgeSentenceBoundary.splitIndexed(b.text,ttsWordPositions);
        sentenceIndex=SentenceIndex.fromSourceChunks(speechSegments,ttsWordPositions);
    }
    static UnifiedBookSession current(String uri){
        UnifiedBookSession s=active;
        return s!=null&&s.uri.equals(uri)?s:null;
    }
    static synchronized void invalidate(String uri){
        if(active!=null&&active.uri.equals(uri))active=null;
    }
    static UnifiedBookSession open(Context context,Uri source)throws Exception {
        UnifiedBookSession ready=current(source.toString());
        if(ready!=null)return ready;
        return openUncached(context,source);
    }
    private static synchronized UnifiedBookSession openUncached(Context context,Uri source)throws Exception {
        String uri=source.toString();
        // The previously opened immutable book is already ready for all reading modes.
        // Never traverse HTML chapter trees or hash the source while returning to the reader.
        UnifiedBookSession s=current(uri);
        if(s!=null)return s;
        // New downloads/imports explicitly invalidate this session after publishing.
        String fingerprint=ReaderBookCache.revision(context,source);
        // RBI5 carries parsed text, word offsets, a structural TOC and revision.
        // EPUB/FB2 need not be reopened after restart when the source is unchanged.
        ReaderBookCache.CachedBook cached=ReaderBookCache.load(context,source);
        BookLoader.Book book;
        ReadingWordMap map;
        ReaderTextReanchor openingMigration=null;
        if(cached!=null){
            book=new BookLoader.Book(cached.title,cached.author,cached.text,cached.chapters);
            map=cached.map;
        }else{
            ReaderBookCache.CachedBook previous=ReaderBookCache.loadLegacy(context,source);
            book=BookLoader.load(context,source);
            if(book.text==null||book.text.trim().isEmpty())
                throw new IllegalArgumentException("Нет текста книги");
            map=ReadingWordMap.parse(book.text);
            if(map.size()==0)throw new IllegalArgumentException("В книге нет слов");
            if(ReaderTextMigration.migrate(context,uri,previous,book,map))
                openingMigration=new ReaderTextReanchor(previous.map,map,previous.chapters,book.chapters);
            ReaderBookCache.store(context,source,book.title,book.author,book.text,map,book.chapters);
        }
        UnifiedBookSession prepared=new UnifiedBookSession(uri,fingerprint,book,map);
        prepared.openingMigration=openingMigration;
        // Serialize cache conversion and publication to prevent duplicate migrations.
        synchronized(UnifiedBookSession.class){active=prepared;}
        return prepared;
    }
}
