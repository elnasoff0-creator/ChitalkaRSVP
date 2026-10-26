package ru.reader.rsvp;

import java.util.ArrayList;

/** Groups adjacent sentences into meaningful requests of no more than 600 chars.
 * All word and character offsets remain tied to the original book text. The
 * first group is intentionally shorter for rapid start. Paragraphs stay intact.
 * This dramatically reduces socket handshakes for dialogue-heavy books.
 */
final class EdgeSpeechSegments {
    static final int MAX=600,FIRST_TARGET=320;
    private EdgeSpeechSegments(){}
    static ArrayList<SentenceChunks.Chunk> split(String raw){return split(raw,BookChapterIndex.empty());}
    static ArrayList<SentenceChunks.Chunk> split(String raw,BookChapterIndex chapters){
        if(raw==null)raw="";
        ArrayList<SentenceChunks.Chunk> sentences=SentenceChunks.split(raw);
        ArrayList<SentenceChunks.Chunk> grouped=new ArrayList<>();
        SentenceChunks.Chunk first=null,last=null;
        for(SentenceChunks.Chunk c:sentences){
            if(first==null){first=c;last=c;continue;}
            int length=c.toChar-first.fromChar;
            int target=grouped.isEmpty()?FIRST_TARGET:MAX;
            String between=raw.substring(last.toChar,c.fromChar);
            boolean paragraph=between.contains("\n\n")||between.contains("\r\n\r\n");
            // Keep chapter-specific offline downloads reusable by live playback.
            boolean chapterChange=chapters!=null&&chapters.size()>0
                &&chapters.atChar(first.fromChar)!=chapters.atChar(c.fromChar);
            if(length>target||paragraph||chapterChange){
                grouped.add(new SentenceChunks.Chunk(first.fromChar,last.toChar,
                    first.fromWord,last.toWord,raw.substring(first.fromChar,last.toChar)));
                first=c;
            }
            last=c;
        }
        if(first!=null)grouped.add(new SentenceChunks.Chunk(first.fromChar,last.toChar,
            first.fromWord,last.toWord,raw.substring(first.fromChar,last.toChar)));
        return grouped;
    }
}
