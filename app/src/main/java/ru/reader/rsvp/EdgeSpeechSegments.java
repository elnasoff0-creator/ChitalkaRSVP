package ru.reader.rsvp;

import java.util.ArrayList;

/** Immutable source-backed sentence grouping, shared by live and bulk Edge TTS.
 * Soft targets apply only to combinations of COMPLETE sentences. A single long
 * sentence remains indivisible; the engine handles hard SSML overflow explicitly.
 */
final class EdgeSpeechSegments {
    static final int MAX=800,FIRST_TARGET=500;
    interface TextFits {boolean fits(String sourceText);}
    private EdgeSpeechSegments(){}
    static ArrayList<SentenceChunks.Chunk> split(String raw){return split(raw,BookChapterIndex.empty());}
    static ArrayList<SentenceChunks.Chunk> split(String raw,BookChapterIndex chapters){
        return split(raw,chapters,EdgeSpeechSize::fits);
    }
    static ArrayList<SentenceChunks.Chunk> split(String raw,BookChapterIndex chapters,TextFits checker){
        return split(raw,chapters,checker,EdgeSpeechLayout.current());
    }
    static ArrayList<SentenceChunks.Chunk> split(String raw,BookChapterIndex chapters,
            TextFits checker,EdgeSpeechLayout layout){
        if(raw==null)raw="";
        ArrayList<SentenceChunks.Chunk> sentences=EdgeSentenceBoundary.split(raw);
        ArrayList<SentenceChunks.Chunk> groups=new ArrayList<>();
        SentenceChunks.Chunk first=null,last=null;
        final int laterTarget=Math.min(MAX,layout.normal);
        final int firstTarget=Math.min(MAX,layout.first);
        for(SentenceChunks.Chunk next:sentences){
            if(first==null){first=next;last=next;continue;}
            final int target=groups.isEmpty()?firstTarget:laterTarget;
            final String between=raw.substring(last.toChar,next.fromChar);
            final boolean newParagraph=between.contains("\n\n")||between.contains("\r\n\r\n");
            final boolean newChapter=chapters!=null&&chapters.size()>0&&
                chapters.atChar(first.fromChar)!=chapters.atChar(next.fromChar);
            final String combined=raw.substring(first.fromChar,next.toChar);
            if(newChapter||newParagraph||combined.length()>target||
                    (checker!=null&&!checker.fits(combined))){
                groups.add(join(raw,first,last));
                first=next;
            }
            last=next;
        }
        if(first!=null)groups.add(join(raw,first,last));
        // Merge tiny adjacent groups only if the *whole* merged source remains
        // within its target and post-dictionary byte budget; never split sentences.
        ArrayList<SentenceChunks.Chunk> compact=new ArrayList<>();
        for(int i=0;i<groups.size();i++){
            SentenceChunks.Chunk part=groups.get(i);
            if(part.text.length()<180 && !compact.isEmpty()){
                SentenceChunks.Chunk prev=compact.get(compact.size()-1);
                int target=compact.size()==1?firstTarget:laterTarget;
                if(canMerge(raw,prev,part,chapters,checker,target)){
                    compact.set(compact.size()-1,join(raw,prev,part));continue;
                }
            }
            compact.add(part);
        }
        return compact;
    }
    private static boolean canMerge(String raw,SentenceChunks.Chunk a,SentenceChunks.Chunk b,
            BookChapterIndex chapters,TextFits checker,int target){
        if(a.toWord!=b.fromWord||a.fromChar>=b.fromChar||b.toChar-a.fromChar>target)return false;
        if(chapters!=null&&chapters.size()>0&&chapters.atChar(a.fromChar)!=chapters.atChar(b.fromChar))return false;
        return checker==null||checker.fits(raw.substring(a.fromChar,b.toChar));
    }
    private static SentenceChunks.Chunk join(String raw,SentenceChunks.Chunk first,SentenceChunks.Chunk last){
        return new SentenceChunks.Chunk(first.fromChar,last.toChar,first.fromWord,last.toWord,
            raw.substring(first.fromChar,last.toChar));
    }
    /** Manual seek: synthesize from the BEGINNING of the sentence containing
     * the tapped word. The first and subsequent pieces contain whole sentences
     * and always round DOWN when the next sentence would exceed the soft target.
     */
    static ArrayList<SentenceChunks.Chunk> priorityPieces(String raw,SentenceIndex index,
            int selectedWord,int groupEndWord,TextFits checker){
        ArrayList<SentenceChunks.Chunk> result=new ArrayList<>();
        if(raw==null||index==null||selectedWord<0||selectedWord>=groupEndWord)return result;
        final int target=EdgeAdaptiveBlocks.priorityTarget();
        SentenceChunks.Chunk first=null,last=null;
        int word=selectedWord;
        while(word<groupEndWord){
            SentenceChunks.Chunk next=index.atWordRange(word);
            if(next==null||next.toWord<=word||next.toWord>groupEndWord)
                throw new IllegalStateException("Нарушена граница предложения Edge TTS");
            if(first==null){first=next;last=next;word=next.toWord;continue;}
            String proposed=raw.substring(first.fromChar,next.toChar);
            if(proposed.length()>target||(checker!=null&&!checker.fits(proposed))){
                result.add(join(raw,first,last));first=next;
            }
            last=next;word=next.toWord;
        }
        if(first!=null)result.add(join(raw,first,last));
        return result;
    }
}
