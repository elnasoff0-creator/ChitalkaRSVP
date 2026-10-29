package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

/** Counts only audible playback time. The two domains have distinct persistent keys.
 * Neither wall clock time nor background buffering advances this countdown. */
final class PlaybackSleepCountdown {
    static final String TTS_KEY="tts_sleep_remaining_ms";
    static final String AUDIOBOOK_KEY="audiobook_sleep_remaining_ms";
    static final String TTS_CHAPTER_KEY="tts_sleep_chapter_end";
    private final SharedPreferences prefs;
    private final String key;
    private long remaining, lastClock;
    private boolean wasPlaying;

    PlaybackSleepCountdown(Context context,String domain){
        prefs=context.getSharedPreferences("reader",0);key=domain;reload();
    }
    void reload(){remaining=Math.max(0L,prefs.getLong(key,0L));lastClock=0L;wasPlaying=false;}
    void setMinutes(int minutes){
        remaining=minutes>0?Math.min((long)minutes,10080L)*60000L:0L;
        lastClock=0L;wasPlaying=false;persist();
    }
    long remainingMillis(){
        if(!wasPlaying||lastClock==0||remaining<=0)return remaining;
        return Math.max(0L,remaining-(SystemClock.elapsedRealtime()-lastClock));
    }
    /** True only when an armed countdown actually reaches zero while playing. */
    boolean tick(boolean audible){
        long now=SystemClock.elapsedRealtime();
        boolean armed=remaining>0L;
        if(wasPlaying&&lastClock>0L){
            long elapsed=Math.max(0L,now-lastClock);
            remaining=Math.max(0L,remaining-elapsed);
            if(elapsed>0)persist();
        }
        lastClock=now;wasPlaying=audible&&remaining>0L;
        return armed&&remaining==0L;
    }
    void freeze(){tick(false);lastClock=0L;wasPlaying=false;}
    private void persist(){prefs.edit().putLong(key,remaining).apply();}
}
