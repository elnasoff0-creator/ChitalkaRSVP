package ru.reader.rsvp;

/** Sentence-level bookmark ranges without modifying a user's manually expanded selection. */
final class ReaderBookmarkSentences {
    static final class Range {
        final int from,to; // inclusive words
        Range(int from,int to){this.from=from;this.to=to;}
    }
    private ReaderBookmarkSentences(){}
    static Range choose(int from,int to,int size,SentenceIndex sentences){
        if(size<=0)return new Range(0,0);
        int first=Math.max(0,Math.min(size-1,Math.min(from,to)));
        int last=Math.max(first,Math.min(size-1,Math.max(from,to)));
        if(first==last&&sentences!=null){
            SentenceChunks.Chunk sentence=sentences.atWordRange(first);
            if(sentence!=null&&sentence.fromWord<=first&&sentence.toWord>first){
                first=Math.max(0,sentence.fromWord);
                last=Math.min(size-1,sentence.toWord-1);
            }
        }
        return new Range(first,last);
    }
    static String excerpt(String text,ReadingWordMap map,Range range){
        if(text==null||map==null||map.size()==0)return "";
        int start=map.starts[range.from],end=map.ends[range.to];
        if(start<0||end>text.length()||start>=end)return "";
        return text.substring(start,end).replaceAll("\\s+"," ").trim();
    }
}
