package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared playback preferences used by both settings UI and audio service. */
public final class AudioPlaybackPrefs {
    private AudioPlaybackPrefs(){}
    public static final String REWIND_ON_RESUME="audio_pause_rewind_sec";
    public static final String SEEK_SECONDS="audio_seek_seconds";
    public static final String SEEK_MINUTES="audio_seek_minutes";
    public static int clamp(int value,int lo,int hi){return Math.min(hi,Math.max(lo,value));}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences("reader",0);}
    public static int pauseRewindSeconds(Context c){return clamp(prefs(c).getInt(REWIND_ON_RESUME,0),0,60);}
    public static int seekSeconds(Context c){return clamp(prefs(c).getInt(SEEK_SECONDS,10),1,3600);}
    public static int seekMinutes(Context c){return clamp(prefs(c).getInt(SEEK_MINUTES,1),1,120);}
}
