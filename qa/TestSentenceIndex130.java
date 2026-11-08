package ru.reader.rsvp;

public final class TestSentenceIndex130 {
    private static void eq(int actual,int expected,String label){if(actual!=expected)
        throw new AssertionError(label+" expected "+expected+", got "+actual);}
    public static void main(String[] args){
        SentenceIndex idx=SentenceIndex.fromText("Первое предложение. Второе предложение! Третье предложение.");
        eq(idx.size(),3,"three real sentences");
        eq(idx.beginning(3),2,"second sentence start");
        eq(idx.previous(5),2,"previous of third");
        eq(idx.next(3),4,"next of second");
        TtsSentenceHistory history=new TtsSentenceHistory();history.bind(idx);
        history.observe(1);history.observe(3);history.observe(5);
        eq(history.currentSentence(),2,"highlighted sentence");
        eq(history.previousSentence(),1,"observed previous sentence");
        history.capturePause(5);
        eq(history.previousOnResume(5),2,"resume from prior sentence");
        history.manuallySelect(0);history.capturePause(0);
        eq(history.previousOnResume(0),0,"first sentence cannot underrun");
        // Synthesis may fragment an oversized sentence into 800-char pieces,
        // but those must not be extra navigable sentences.
        String giant="длинное ".repeat(170)+"предложение. Следующее.";
        SentenceIndex longIdx=SentenceIndex.fromText(giant);
        eq(longIdx.size(),2,"long sentence remains one");
        java.util.ArrayList<SentenceChunks.Chunk> clips=SentenceChunks.split(giant);
        if(clips.size()<=2)throw new AssertionError("test requires multiple audio clips");
        int lastWordInFirst=longIdx.atWordRange(0).toWord-1;
        eq(longIdx.beginning(lastWordInFirst),0,"inside giant sentence returns its beginning");
        eq(longIdx.previous(lastWordInFirst),0,"giant sentence prev means true prev");
        eq(longIdx.next(lastWordInFirst),longIdx.atWordRange(giant.split("\\s+").length-1).fromWord,"next from giant");
        // Abbreviation is not a standalone sentence.
        SentenceIndex abbreviations=SentenceIndex.fromText("Обсудим т. д. потом. После будет итог.");
        eq(abbreviations.size(),2,"common abbreviation");
        SentenceIndex empty=SentenceIndex.fromText("");
        eq(empty.size(),0,"empty");
        eq(empty.beginning(8),8,"empty fallback");
        System.out.println("PASS: true sentence boundaries, history capture, long sentence, abbreviation, first/last limits");
    }
}
