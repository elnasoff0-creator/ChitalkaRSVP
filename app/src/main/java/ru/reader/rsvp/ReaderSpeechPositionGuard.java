package ru.reader.rsvp;

/** Keeps visible speech position driven by confirmed Media3 audio, not by stale
 * word callbacks or missing WordBoundary metadata. Never rewinds a playing track
 * merely because its word-timing sidecar is loading.
 */
final class ReaderSpeechPositionGuard {
    private int requestedStart=-1, requestedEnd=-1;
    private String trackKey="";
    private int lastWord=-1;
    private long lastPositionMs=-1;

    void reset(){requestedStart=requestedEnd=-1;trackKey="";lastWord=-1;lastPositionMs=-1;}
    void seek(int start,int end){
        reset();requestedStart=Math.max(0,start);requestedEnd=Math.max(requestedStart+1,end);
    }
    boolean pending(){return requestedStart>=0;}

    /** -1 means do not change the visible cursor. A seek can only be confirmed
     * by a word in the selected sentence or a later actual playback track (e.g.
     * an ignored sentence). Time elapsed is deliberately NOT a confirmation.
     */
    int accept(String key,int trackStart,long positionMs,int actual,boolean playing){
        if(!playing||key==null||key.isEmpty()||actual<0)return -1;
        if(requestedStart>=0){
            boolean inside=actual>=requestedStart&&actual<requestedEnd;
            boolean skipped=trackStart>=requestedEnd&&actual>=trackStart;
            if(!inside&&!skipped)return -1;
            requestedStart=requestedEnd=-1;
            trackKey="";lastWord=-1;lastPositionMs=-1;
        }
        if(key.equals(trackKey)){
            // Real backward seek is visible through a rewind of Media3's source clock.
            // Small clock rounding must not resurrect a previous sentence.
            boolean rewound=lastPositionMs>=0&&positionMs+600L<lastPositionMs;
            if(!rewound&&lastWord>=0&&actual<lastWord){
                lastPositionMs=Math.max(lastPositionMs,positionMs);
                return lastWord;
            }
        }else{
            // A spontaneous next-track transition must not run backwards.
            // Explicit Next/Previous/seek resets this guard through seek().
            if(lastWord>=0 && actual<lastWord)return -1;
            trackKey=key;
            lastWord=-1;
            lastPositionMs=-1;
        }
        lastWord=actual;
        lastPositionMs=Math.max(0,positionMs);
        return actual;
    }

    /** A newly prepared track can temporarily lack metadata. Do not reset its
     * highlight to the first word after it has already spoken a later word. */
    int withoutTimings(String key,int start){
        return key!=null&&key.equals(trackKey)&&lastWord>=0?lastWord:start;
    }
}
