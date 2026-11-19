package ru.reader.rsvp;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Pure-Java regression checks for sentence integrity and actual UTF-8 limits. */
public final class TestEdgeSentenceBlocks182 {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static String sentence(int chars,String word){
        StringBuilder s=new StringBuilder();while(s.length()<chars-1)s.append(word).append(' ');
        return s.substring(0,chars-1)+".";
    }
    private static void verifyBoundary(String text,List<SentenceChunks.Chunk> parts){
        for(SentenceChunks.Chunk c:parts){
            require(c.text.equals(text.substring(c.fromChar,c.toChar)),"source text changed");
            require(!c.text.trim().isEmpty(),"empty part");
            require(c.fromWord<c.toWord,"invalid word index");
        }
    }
    public static void main(String[] args){
        String a=sentence(318,"Первое");
        String b=sentence(278,"Второе");
        String c=sentence(248,"Третье");
        String raw=a+" "+b+" "+c;
        ArrayList<SentenceChunks.Chunk> natural=SentenceChunks.split(raw);
        require(natural.size()==3,"all three grammatical sentences must stay whole");
        verifyBoundary(raw,natural);
        ArrayList<SentenceChunks.Chunk> grouped=EdgeSpeechSegments.split(raw);
        require(grouped.size()>=2,"first fast group must be smaller than full 800");
        verifyBoundary(raw,grouped);
        require(grouped.get(0).text.equals(a),"round down instead of cutting the next sentence");
        require(grouped.get(1).text.equals(b+" "+c),"keep next two complete sentences together");
        SentenceIndex index=SentenceIndex.fromText(raw);
        int tapped=natural.get(1).fromWord+3;
        ArrayList<SentenceChunks.Chunk> priority=EdgeSpeechSegments.priorityPieces(raw,index,tapped,
            natural.get(2).toWord,EdgeSpeechSize::fits);
        require(!priority.isEmpty(),"manual selection requires at least one segment");
        require(priority.get(0).fromWord==natural.get(1).fromWord,"manual seek cut the selected sentence");
        verifyBoundary(raw,priority);
        String longSentence=sentence(1807,"ОченьДлинноеПредложение");
        require(SentenceChunks.split(longSentence).size()==1,"long sentence split at character cap");
        require(EdgeSpeechSegments.split(longSentence).size()==1,"long sentence split at transport cap");
        require(EdgeSpeechSegments.priorityPieces(longSentence,SentenceIndex.fromText(longSentence),3,
            SentenceChunks.wordPositions(longSentence).size(),EdgeSpeechSize::fits).size()==1,
            "priority pieces split a long sentence");
        String abbr="Он жил в г. Москве. Потом уехал.";
        require(SentenceChunks.split(abbr).size()==2,"abbreviation treated as end of sentence");
        String blocks="Название главы\n\nТут начинается история без завершения главы";
        require(SentenceChunks.split(blocks).size()==2,"paragraph boundary failed");
        String utf="&".repeat(1000)+".";
        require(!EdgeSpeechSize.fits(utf),"XML escaping undercounted bytes");
        String oversized="тест ".repeat(550)+"конец.";
        require(EdgeSpeechSegments.split(oversized).size()==1,"oversized UTF-8 sentence cut in pieces");
        String small="Привет, это одна фраза. Другая короткая фраза.";
        // A synthetic dictionary expands the first sentence: check candidate budget *after* transform.
        ArrayList<SentenceChunks.Chunk> checked=EdgeSpeechSegments.split(small,BookChapterIndex.empty(),
            rawText->!rawText.contains("Другая")||!rawText.contains("Привет"));
        require(checked.size()==2,"post-dictionary byte check did not round down");
        try{
            java.nio.file.Path folder=java.nio.file.Files.createTempDirectory("edge-182-layout-");
            EdgeSpeechLayout remembered=EdgeSpeechLayout.forFolder(folder.toFile());
            EdgeAdaptiveBlocks.observe(EdgeSpeechQueue.PLAY,true,3800,6800);
            EdgeSpeechLayout second=EdgeSpeechLayout.forFolder(folder.toFile());
            require(second.first==remembered.first&&second.normal==remembered.normal,
                "network latency silently changed persisted MP3 grouping");
            java.nio.file.Path legacy=java.nio.file.Files.createTempDirectory("edge-182-legacy-");
            java.nio.file.Files.write(legacy.resolve("old-fragment.mp3"),new byte[]{1,2,3});
            EdgeSpeechLayout old=EdgeSpeechLayout.forFolder(legacy.toFile());
            require(old.first==800&&old.normal==800,"older downloaded MP3 groups not preserved");
        }catch(java.io.IOException error){throw new AssertionError(error);}
        require(EdgeAdaptiveBlocks.normalTarget()>=600&&EdgeAdaptiveBlocks.normalTarget()<=800,
            "normal adaptive target out of range");
        require(EdgeAdaptiveBlocks.priorityTarget()<=500,"priority target out of range");
        System.out.println("PASS: whole sentences, UTF-8, manual selection, abbreviations, adaptation");
    }
}
