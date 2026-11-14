package ru.reader.rsvp;
import java.util.ArrayList;
public final class TestEdgeSegments162 {
    public static void main(String[] args){
        StringBuilder b=new StringBuilder();
        for(int i=0;i<220;i++)b.append("Слово ").append(i).append(" в предложении. ");
        String text=b.toString();
        ArrayList<Integer> offsets=SentenceChunks.wordPositions(text);
        ArrayList<SentenceChunks.Chunk> normal=EdgeSpeechSegments.split(text);
        if(EdgeSpeechSegments.FIRST_TARGET_PRIORITY!=500||EdgeSpeechSegments.FIRST_TARGET!=800)
            throw new AssertionError("v158 priorities not restored");
        if(normal.size()<3)throw new AssertionError("800-char segments missing");
        int previous=0;
        for(SentenceChunks.Chunk clip:normal){
            if(clip.fromWord<previous)throw new AssertionError("overlapping normal segments");
            if(clip.text.length()>800)throw new AssertionError("oversized 800-char clip");
            previous=clip.toWord;
        }
        SentenceChunks.Chunk first=normal.get(0);
        ArrayList<SentenceChunks.Chunk> pieces=EdgeSpeechSegments.priorityPieces(text,offsets,
            first.fromWord,first.toWord,first.toChar);
        if(pieces.size()<1)throw new AssertionError("no priority pieces");
        int at=first.fromWord;
        for(SentenceChunks.Chunk part:pieces){
            if(part.fromWord!=at)throw new AssertionError("priority clips skip a word");
            if(part.text.length()>500)throw new AssertionError("priority block > 500 characters");
            at=part.toWord;
        }
        if(at!=first.toWord)throw new AssertionError("priority clip coverage incomplete");
        System.out.println("PASS: v158 Edge 800/500 blocks and word coverage");
    }
}
