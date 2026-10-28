package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists only the latest HTTP Date time offset, not keys, URL or text content. */
final class EdgeClockPreferences implements EdgeServerClock.OffsetStore {
    private static final String PREFS="edge_server_clock_v1";
    private final SharedPreferences prefs;
    EdgeClockPreferences(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    @Override public long savedSkew(){return prefs.getLong("skew_ms",0L);}
    @Override public long savedAt(){return prefs.getLong("updated_epoch_ms",0L);}
    @Override public void save(long skew,long sampleWall){
        prefs.edit().putLong("skew_ms",skew).putLong("updated_epoch_ms",sampleWall).apply();
    }
}
