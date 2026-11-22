package ru.reader.rsvp;

public final class TestPetSpeechBubble189 {
    static void eq(int got,int expected,String why){
        if(got!=expected)throw new AssertionError(why+": "+got+" expected "+expected);
    }
    public static void main(String[] args){
        eq(PetSpeechBubbleSizing.maxWidth(360,270,20),270,"wide card");
        eq(PetSpeechBubbleSizing.maxWidth(200,270,20),180,"narrow card");
        eq(PetSpeechBubbleSizing.maxHeight(260,145,112),145,"fixed 260dp frame");
        eq(PetSpeechBubbleSizing.maxHeight(200,145,112),88,"compact pet frame");
        eq(PetSpeechBubbleSizing.quoteLines(145,20,19),6,"bounded quote lines");
        eq(PetSpeechBubbleSizing.quoteLines(80,20,19),3,"shorter bubble");
        eq(PetSpeechBubbleSizing.quoteLines(10,20,19),1,"tiny bubble");
        System.out.println("PASS: bubble scales inside fixed pet frame and truncation bounds");
    }
}
