package ru.reader.rsvp;

/** Pixel bounds for the speech bubble. Never modifies the fixed-size pet frame. */
final class PetSpeechBubbleSizing {
    private PetSpeechBubbleSizing() {}
    static int maxWidth(int frameWidth,int preferredMax,int sideMargin){
        return Math.max(1,Math.min(preferredMax,Math.max(1,frameWidth-sideMargin)));
    }
    static int maxHeight(int frameHeight,int preferredMax,int petReserve){
        return Math.max(1,Math.min(preferredMax,Math.max(1,frameHeight-petReserve)));
    }
    static int quoteLines(int bubbleHeight,int decorationHeight,int lineHeight){
        return Math.max(1,Math.min(6,(Math.max(0,bubbleHeight-decorationHeight))
            /Math.max(1,lineHeight)));
    }
}
