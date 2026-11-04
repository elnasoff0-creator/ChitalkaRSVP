package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable real sentence boundaries in source words and chars. 800-character
 * speech clips are transport units and must never become sentence boundaries. */
final class SentenceIndex {
    private final ArrayList<SentenceChunks.Chunk> sentences;
    private final ArrayList<Integer> wordOffsets;
    private SentenceIndex(ArrayList<SentenceChunks.Chunk> items,ArrayList<Integer> words){
        sentences=items;wordOffsets=words;
    }
    /** Compatibility with already-segmented clients. Prefer fromText(source). */
    static SentenceIndex fromChunks(List<SentenceChunks.Chunk> source){
        ArrayList<SentenceChunks.Chunk> values=new ArrayList<>();
        if(source!=null)values.addAll(source);
        return new SentenceIndex(values,new ArrayList<>());
    }
    static SentenceIndex fromText(String text){
        if(text==null)text="";
        ArrayList<Integer> offsets=SentenceChunks.wordPositions(text);
        ArrayList<SentenceChunks.Chunk> result=new ArrayList<>();
        Matcher breaks=Pattern.compile("(?<=[.!?…])\\s+|\\n{2,}").matcher(text);
        int start=0;
        while(breaks.find()){
            int end=breaks.start();
            if(isAbbreviation(text,start,end))continue;
            addSentence(result,offsets,text,start,end);
            start=breaks.end();
        }
        addSentence(result,offsets,text,start,text.length());
        return new SentenceIndex(result,offsets);
    }
    private static boolean isAbbreviation(String raw,int start,int end){
        if(end<=start)return false;
        String tail=raw.substring(Math.max(start,end-15),end).toLowerCase(java.util.Locale.ROOT).trim();
        // A common abbreviation must not create an artificial sentence.
        return tail.equals("т.")||tail.endsWith(" т.")||tail.endsWith("т. д.")||tail.endsWith("т. п.")
            ||tail.endsWith("т. е.")||tail.endsWith("рис.")||tail.endsWith("стр.")
            ||tail.endsWith("ул.")||tail.endsWith("им.");
    }
    private static void addSentence(ArrayList<SentenceChunks.Chunk> result,
            ArrayList<Integer> offsets,String text,int start,int end){
        while(start<end&&Character.isWhitespace(text.charAt(start)))start++;
        while(end>start&&Character.isWhitespace(text.charAt(end-1)))end--;
        int first=SentenceChunks.lowerBound(offsets,start);
        int last=SentenceChunks.lowerBound(offsets,end);
        if(first<last)result.add(new SentenceChunks.Chunk(start,end,first,last,text.substring(start,end)));
    }
    int size(){return sentences.size();}
    int atWord(int word){
        if(sentences.isEmpty())return -1;
        int low=0,high=sentences.size()-1;
        while(low<=high){int middle=(low+high)>>>1;
            SentenceChunks.Chunk s=sentences.get(middle);
            if(word<s.fromWord)high=middle-1;
            else if(word>=s.toWord)low=middle+1;
            else return middle;
        }
        return Math.max(0,Math.min(sentences.size()-1,low));
    }
    int wordAtChar(int charOffset){
        if(wordOffsets.isEmpty())return 0;
        int i=SentenceChunks.lowerBound(wordOffsets,Math.max(0,charOffset));
        if(i>=wordOffsets.size())return wordOffsets.size()-1;
        if(i>0&&wordOffsets.get(i)>charOffset)return i-1;
        return i;
    }
    SentenceChunks.Chunk atWordRange(int word){
        int id=atWord(word);return id<0?null:sentences.get(id);
    }
    int startAt(int id){return sentences.isEmpty()?0:sentences.get(Math.max(0,Math.min(id,sentences.size()-1))).fromWord;}
    int beginning(int word){return sentences.isEmpty()?Math.max(0,word):startAt(atWord(word));}
    int previous(int word){return sentences.isEmpty()?0:startAt(atWord(word)-1);}
    int next(int word){return sentences.isEmpty()?0:startAt(atWord(word)+1);}
}
