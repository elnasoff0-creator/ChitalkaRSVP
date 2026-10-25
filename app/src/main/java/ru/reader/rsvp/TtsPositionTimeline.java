package ru.reader.rsvp;

/** Book position expressed as an estimated read-aloud time, not listened history.
 * Every TTS engine shares the same global word positions and WPM setting. */
final class TtsPositionTimeline {
    private TtsPositionTimeline() {}
    static long positionMinutes(int totalWords,int currentWord,int wpm){
        int safeWpm=Math.max(1,wpm);
        int word=Math.max(0,Math.min(Math.max(0,totalWords),currentWord));
        return word/(long)safeWpm;
    }
    static long totalMinutes(int totalWords,int wpm){
        return Math.round(Math.max(0,totalWords)/(double)Math.max(1,wpm));
    }
    static String clock(long minutes){return (minutes/60)+" ч "+(minutes%60)+" мин";}
    static String label(int totalWords,int currentWord,int wpm){
        return clock(positionMinutes(totalWords,currentWord,wpm))+
            " / ~"+clock(totalMinutes(totalWords,wpm));
    }
}
