package ru.reader.rsvp;

import java.util.ArrayList;

/** Groups adjacent sentences into meaningful requests of no more than 900 chars.
 * All word and character offsets remain tied to the original book text.
 * Normal first and subsequent groups use the same target. Paragraphs stay intact.
 * This dramatically reduces socket handshakes for dialogue-heavy books.
 */
final class EdgeSpeechSegments {
    static final int MAX=900,FIRST_TARGET=900,FIRST_TARGET_PRIORITY=600;
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
    /** An uncached manual jump starts at the selected word. Cover the rest of
     * its normal group with contiguous priority pieces (first <=600 chars),
     * then return to normal 900-character groups. Keep absolute book offsets.
     */
    static ArrayList<SentenceChunks.Chunk> priorityPieces(String raw,
            ArrayList<Integer> positions,int fromWord,int toWord,int groupEndChar){
        ArrayList<SentenceChunks.Chunk> result=new ArrayList<>();
        if(raw==null||positions==null||positions.isEmpty()||fromWord<0||
                toWord>positions.size()||fromWord>=toWord)return result;
        int word=fromWord;
        while(word<toWord){
            int beginChar=positions.get(word);
            int limit=beginChar+FIRST_TARGET_PRIORITY;
            int next=word;
            while(next<toWord){
                int wordStart=positions.get(next);
                int wordEnd=wordStart;
                while(wordEnd<raw.length()&&!Character.isWhitespace(raw.charAt(wordEnd)))wordEnd++;
                if(wordEnd>limit&&next>word)break;
                // A single pathological unbroken word may exceed 600 chars;
                // preserve that word rather than corrupting word offsets.
                next++;
                if(wordEnd>limit)break;
            }
            int endChar=next==toWord?groupEndChar:positions.get(next);
            endChar=Math.min(raw.length(),endChar);
            while(endChar>beginChar&&Character.isWhitespace(raw.charAt(endChar-1)))endChar--;
            if(endChar<=beginChar)throw new IllegalStateException("Empty priority piece");
            result.add(new SentenceChunks.Chunk(beginChar,endChar,word,next,
                raw.substring(beginChar,endChar)));
            word=next;
        }
        return result;
    }

}
