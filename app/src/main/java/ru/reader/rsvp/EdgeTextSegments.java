package ru.reader.rsvp;

import java.util.ArrayList;

/** Compatibility adapter: both TTS engines use SentenceChunks with a hard 600-char cap. */
final class EdgeTextSegments {
    static final class Part {
        final int start,end;
        final String text;
        Part(int start,int end,String text){this.start=start;this.end=end;this.text=text;}
    }
    private EdgeTextSegments(){}
    static ArrayList<Integer> positions(String raw){return SentenceChunks.wordPositions(raw);}
    static Part at(String raw,ArrayList<Integer> positions,int start,int characters){
        SentenceChunks.Chunk c=SentenceChunks.forWord(SentenceChunks.split(raw),start);
        return c==null?null:new Part(c.fromWord,c.toWord,c.text);
    }
    static ArrayList<Part> all(String raw,int characters){
        ArrayList<Part> result=new ArrayList<>();
        for(SentenceChunks.Chunk c:SentenceChunks.split(raw))result.add(new Part(c.fromWord,c.toWord,c.text));
        return result;
    }
}
