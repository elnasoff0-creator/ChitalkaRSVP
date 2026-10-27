package ru.reader.rsvp;

import java.util.ArrayList;

/** Compatibility adapter: both TTS engines use SentenceChunks with a 800-char cap. */
final class EdgeTextSegments {
    static final class Part {
        final int start,end;
        final String text;
        Part(int start,int end,String text){this.start=start;this.end=end;this.text=text;}
    }
    private EdgeTextSegments(){}
    static ArrayList<Integer> positions(String raw){return SentenceChunks.wordPositions(raw);}
    static Part at(String raw,ArrayList<Integer> positions,int start,int characters){
        SentenceChunks.Chunk c=SentenceChunks.forWord(EdgeSpeechSegments.split(raw),start);
        return c==null?null:new Part(c.fromWord,c.toWord,c.text);
    }
    /** Preserve live-cache compatibility: bulk and interactive playback MUST use
     * identical word boundaries and hash keys; 'characters' is legacy metadata.
     */
    static ArrayList<Part> all(String raw,int characters){
        return all(raw,BookChapterIndex.empty(),characters);
    }
    static ArrayList<Part> all(String raw,BookChapterIndex chapters,int characters){
        ArrayList<Part> result=new ArrayList<>();
        for(SentenceChunks.Chunk c:EdgeSpeechSegments.split(raw,chapters))
            result.add(new Part(c.fromWord,c.toWord,c.text));
        return result;
    }
}
