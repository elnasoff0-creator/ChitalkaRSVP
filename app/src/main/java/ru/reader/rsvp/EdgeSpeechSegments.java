package ru.reader.rsvp;

import java.util.ArrayList;

/** Immutable source-backed sentence grouping, shared by live and bulk Edge TTS.
 * Soft targets combine complete sentences; only hard transport overflow permits
 * splitting at original word boundaries. Reader sentence boundaries stay unchanged.
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
        return splitIndexed(raw,chapters,checker,layout,EdgeSentenceBoundary.split(raw),null);
    }
    static ArrayList<SentenceChunks.Chunk> splitIndexed(String raw,BookChapterIndex chapters,
            TextFits checker,EdgeSpeechLayout layout,ArrayList<SentenceChunks.Chunk> sentences,
            ArrayList<Integer> words){
        ArrayList<SentenceChunks.Chunk> groups=new ArrayList<>();
        ArrayList<SentenceChunks.Chunk> pieces=new ArrayList<>();
        for(SentenceChunks.Chunk sentence:sentences){
            if(checker==null||checker.fits(sentence.text)){pieces.add(sentence);continue;}
            if(words==null)words=SentenceChunks.wordPositions(raw);
            pieces.addAll(fitPieces(raw,sentence,words,checker));
        }
        SentenceChunks.Chunk first=null,last=null;
        final int laterTarget=Math.min(MAX,layout.normal);
        final int firstTarget=Math.min(MAX,layout.first);
        for(SentenceChunks.Chunk next:pieces){
            if(first==null){first=next;last=next;continue;}
            final int target=groups.isEmpty()?firstTarget:laterTarget;
            final String between=raw.substring(last.toChar,next.fromChar);
            final boolean newParagraph=layout.legacyParagraphs&&
                (between.contains("\n\n")||between.contains("\r\n\r\n"));
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
    /** Return only stable prefix groups. The final group of a truncated prefix
     * may change when the next sentence arrives. Keeping it out preserves
     * the same group keys as full-book synthesis. */
    static ArrayList<SentenceChunks.Chunk> startupPrefix(String raw,BookChapterIndex chapters,
            TextFits checker,EdgeSpeechLayout layout,ArrayList<SentenceChunks.Chunk> sentences,
            ArrayList<Integer> words,int selectedSentence){
        int count=Math.min(sentences.size(),Math.max(0,selectedSentence)+64);
        if(count==sentences.size())return new ArrayList<>();
        ArrayList<SentenceChunks.Chunk> prefix=splitIndexed(raw,chapters,checker,layout,
            new ArrayList<>(sentences.subList(0,count)),words);
        if(!prefix.isEmpty())prefix.remove(prefix.size()-1);
        return prefix;
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
    private static SentenceChunks.Chunk slice(String raw,SentenceChunks.Chunk source,
            ArrayList<Integer> words,int first,int end){
        int a=first==source.fromWord?source.fromChar:words.get(first);
        int b=end==source.toWord?source.toChar:words.get(end);
        while(b>a&&Character.isWhitespace(raw.charAt(b-1)))b--;
        return new SentenceChunks.Chunk(a,b,first,end,raw.substring(a,b));
    }
    /** Hard limits are measured after XML escaping/dictionary transformation.
     * A pathological single word cannot be split without invalidating word indices;
     * retain it for the explicit engine fallback rather than silently truncating it. */
    private static ArrayList<SentenceChunks.Chunk> fitPieces(String raw,SentenceChunks.Chunk source,
            ArrayList<Integer> words,TextFits checker){
        ArrayList<SentenceChunks.Chunk> out=new ArrayList<>();
        if(checker==null||checker.fits(source.text)){out.add(source);return out;}
        int first=source.fromWord;
        while(first<source.toWord){
            int low=first+1,high=Math.min(source.toWord,first+2048),best=first;
            // The final candidate is always checked, including non-monotonic regex rules.
            while(low<=high){int mid=(low+high)>>>1;
                if(checker.fits(slice(raw,source,words,first,mid).text)){best=mid;low=mid+1;}
                else high=mid-1;
            }
            if(best==first)best=first+1;
            // Prefer a nearby natural pause when possible, without leaving tiny fragments.
            for(int end=best;end>first+Math.max(1,(best-first)*3/4);end--){
                SentenceChunks.Chunk candidate=slice(raw,source,words,first,end);
                char last=candidate.text.charAt(candidate.text.length()-1);
                if(",;:—–".indexOf(last)>=0&&checker.fits(candidate.text)){best=end;break;}
            }
            out.add(slice(raw,source,words,first,best));first=best;
        }
        return out;
    }
    /** Normal seeks begin at the grammatical sentence; a hard-limit split may
     * end inside it, so clamp to the selected transport group's end. */
    static ArrayList<SentenceChunks.Chunk> priorityPieces(String raw,SentenceIndex index,
            int selectedWord,int groupEndWord,TextFits checker){
        ArrayList<SentenceChunks.Chunk> result=new ArrayList<>();
        if(raw==null||index==null||selectedWord<0||selectedWord>=groupEndWord)return result;
        final int target=EdgeAdaptiveBlocks.priorityTarget();
        SentenceChunks.Chunk first=null,last=null;
        int word=selectedWord;
        ArrayList<Integer> words=null;
        while(word<groupEndWord){
            SentenceChunks.Chunk next=index.atWordRange(word);
            if(next==null||next.toWord<=word)break;
            // The first selection retains the natural start unless caller already
            // selected a hard-limit continuation (normal player uses group start).
            if(next.toWord>groupEndWord){
                if(words==null)words=SentenceChunks.wordPositions(raw);
                next=slice(raw,next,words,next.fromWord,groupEndWord);
            }
            ArrayList<SentenceChunks.Chunk> pieces;
            if(checker!=null&&!checker.fits(next.text)){
                if(words==null)words=SentenceChunks.wordPositions(raw);
                pieces=fitPieces(raw,next,words,checker);
            }else{pieces=new ArrayList<>();pieces.add(next);}
            for(SentenceChunks.Chunk piece:pieces){
                if(first==null){first=piece;last=piece;continue;}
                String proposed=raw.substring(first.fromChar,piece.toChar);
                if(proposed.length()>target||(checker!=null&&!checker.fits(proposed))){
                    result.add(join(raw,first,last));first=piece;
                }
                last=piece;
            }
            word=next.toWord;
        }
        if(first!=null)result.add(join(raw,first,last));
        return result;
    }
}
