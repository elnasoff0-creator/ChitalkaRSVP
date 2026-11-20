package ru.reader.rsvp;

/** Computes a low-overhead UI refresh interval near the next real WordBoundary.
 * Audio time remains Media3's only clock; the renderer never seeks or changes speech.
 */
final class WordBoundaryRefresh {
    private WordBoundaryRefresh() {}
    static long nextDelayMs(long audioPositionMs,long nextBoundaryMs,float audioSpeed){
        if(nextBoundaryMs<0)return 90L;
        float speed=Math.max(0.35f,Math.min(5f,audioSpeed));
        long until=Math.max(0L,nextBoundaryMs-audioPositionMs);
        long wait=(long)Math.ceil(until/speed)-5L;
        return Math.max(20L,Math.min(95L,wait));
    }
}
