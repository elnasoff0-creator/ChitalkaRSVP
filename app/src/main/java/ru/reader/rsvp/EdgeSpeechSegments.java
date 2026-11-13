package ru.reader.rsvp;

import java.util.ArrayList;

/** Groups whole natural sentences into Edge WebSocket requests.
 * 800 characters is a SOFT target, never a reason to split a sentence.
 * Original word/character offsets and chapter boundaries are preserved.
 */
final class EdgeSpeechSegments {
    static final int MAX=800,FIRST_TARGET=800;
    private EdgeSpeechSegments(){}
    static ArrayList<SentenceChunks.Chunk> split(String raw){return split(raw,BookChapterIndex.empty());}
    static ArrayList<SentenceChunks.Chunk> split(String raw,BookChapterIndex chapters){
        if(raw==null)raw="";
        ArrayList<SentenceChunks.Chunk> sentences=EdgeSentenceBoundary.split(raw);
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
        // Avoid standalone requests for very short paragraphs. Merge only if
        // contiguous in book word order, within MAX and inside the same chapter.
        // Every merged Chunk retains original fromChar/fromWord and toChar/toWord.
        ArrayList<SentenceChunks.Chunk> compact=new ArrayList<>();
        for(int i=0;i<grouped.size();i++){
            SentenceChunks.Chunk part=grouped.get(i);
            if(part.text.length()<180){
                if(!compact.isEmpty()){
                    SentenceChunks.Chunk prev=compact.get(compact.size()-1);
                    if(canMerge(raw,prev,part,chapters)){
                        compact.set(compact.size()-1,merge(raw,prev,part));
                        continue;
                    }
                }
                if(i+1<grouped.size()&&canMerge(raw,part,grouped.get(i+1),chapters)){
                    grouped.set(i+1,merge(raw,part,grouped.get(i+1)));
                    continue;
                }
            }
            compact.add(part);
        }
        return compact;
    }
    private static boolean canMerge(String raw,SentenceChunks.Chunk a,SentenceChunks.Chunk b,BookChapterIndex chapters){
        if(a.toWord!=b.fromWord||a.fromChar>=b.fromChar)return false;
        if(b.toChar-a.fromChar>MAX)return false;
        return chapters==null||chapters.size()==0||
            chapters.atChar(a.fromChar)==chapters.atChar(b.fromChar);
    }
    private static SentenceChunks.Chunk merge(String raw,SentenceChunks.Chunk a,SentenceChunks.Chunk b){
        return new SentenceChunks.Chunk(a.fromChar,b.toChar,a.fromWord,b.toWord,
                raw.substring(a.fromChar,b.toChar));
    }

    /** Manual selection starts at the chosen word, and ALWAYS ends at the
     * natural end of its ordinary Edge group (which ends on a full sentence).
     * The former 500-character slicing created pauses in the middle of speech.
     */
    static ArrayList<SentenceChunks.Chunk> priorityPieces(String raw,
            ArrayList<Integer> positions,int fromWord,int toWord,int groupEndChar){
        ArrayList<SentenceChunks.Chunk> result=new ArrayList<>();
        if(raw==null||positions==null||positions.isEmpty()||fromWord<0||
                toWord>positions.size()||fromWord>=toWord)return result;
        int begin=positions.get(fromWord);
        int end=Math.min(raw.length(),groupEndChar);
        while(end>begin&&Character.isWhitespace(raw.charAt(end-1)))end--;
        if(end>begin)result.add(new SentenceChunks.Chunk(begin,end,fromWord,toWord,
                raw.substring(begin,end)));
        return result;
    }
}
