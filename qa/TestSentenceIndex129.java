package ru.reader.rsvp;

public final class TestSentenceIndex129 {
    private static void eq(int actual,int expected,String what) {
        if(actual!=expected)throw new AssertionError(what+": expected "+expected+" got "+actual);
    }
    public static void main(String[] args){
        SentenceIndex index=SentenceIndex.fromText("One two three. Four five six! Seven eight nine?");
        eq(index.size(),3,"number of sentences");
        eq(index.beginning(1),0,"resume current first sentence");
        eq(index.beginning(4),3,"resume current second sentence");
        eq(index.previous(7),3,"previous from third sentence");
        eq(index.next(4),6,"next from second sentence");
        eq(index.next(8),6,"last sentence cannot overrun");
        eq(index.previous(1),0,"first sentence cannot underrun");
        eq(index.beginning(3),3,"resume exactly at sentence start");
        SentenceIndex empty=SentenceIndex.fromText("");
        eq(empty.size(),0,"empty text");
        eq(empty.beginning(12),12,"empty current fallback");
        eq(empty.previous(12),0,"empty previous fallback");
        SentenceIndex longText=SentenceIndex.fromText("word ".repeat(190)+"end.");
        if(longText.size()<2)throw new AssertionError("oversize sentence should split into logical blocks");
        System.out.println("PASS: current/previous/next boundary, safe limits, empty and oversized text");
    }
}
