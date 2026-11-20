package ru.reader.rsvp;

public final class TestWordBoundaryRefresh186 {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        check(WordBoundaryRefresh.nextDelayMs(100,110,1f)==20L,"close boundary must schedule promptly");
        check(WordBoundaryRefresh.nextDelayMs(100,160,1f)==55L,"mid-boundary interval");
        check(WordBoundaryRefresh.nextDelayMs(100,160,2f)==25L,"speed scaling");
        check(WordBoundaryRefresh.nextDelayMs(100,1000,1f)==95L,"bounded idle intervals");
        check(WordBoundaryRefresh.nextDelayMs(100,-1,1f)==90L,"no metadata fallback");
        check(WordBoundaryRefresh.nextDelayMs(100,160,0f)>=20L,"invalid speed is safe");
        System.out.println("PASS: WordBoundary refresh delay is bounded, prompt and audio-speed-aware");
    }
}
