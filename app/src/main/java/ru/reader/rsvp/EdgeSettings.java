package ru.reader.rsvp;

import android.content.SharedPreferences;
import java.util.Map;

/** Migrates shared queue settings to Edge-only keys without resetting existing choices. */
public final class EdgeSettings {
    public static final String AUTO_CONTINUE="edge_auto_continue";
    public static final String PREFETCH="edge_prefetch";
    private EdgeSettings() {}
    public static void migrate(SharedPreferences prefs) {
        SharedPreferences.Editor edit=prefs.edit();
        if(!prefs.contains(AUTO_CONTINUE) && prefs.contains("mrt_auto_continue"))
            edit.putBoolean(AUTO_CONTINUE,prefs.getBoolean("mrt_auto_continue",true));
        if(!prefs.contains(PREFETCH) && prefs.contains("mrt_prefetch"))
            edit.putBoolean(PREFETCH,prefs.getBoolean("mrt_prefetch",true));
        if(!prefs.contains(EdgeSegmentSettings.KEY) && prefs.contains("mrt_segment_characters"))
            edit.putInt(EdgeSegmentSettings.KEY,prefs.getInt("mrt_segment_characters",EdgeSegmentSettings.DEFAULT));
        if(!prefs.contains(EdgePrefetchSettings.KEY) && prefs.contains("mrt_prefetch_ahead"))
            edit.putInt(EdgePrefetchSettings.KEY,prefs.getInt("mrt_prefetch_ahead",EdgePrefetchSettings.DEFAULT));
        for(String key:prefs.getAll().keySet()) {
            if(key.startsWith("mrt_") || key.startsWith("speechkit_") || key.startsWith("native_selected_"))
                edit.remove(key);
        }
        edit.apply();
    }
}
