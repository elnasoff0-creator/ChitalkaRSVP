package ru.reader.rsvp;

import android.content.SharedPreferences;

/** Migrates shared queue settings to Edge-only keys without resetting existing choices. */
public final class EdgeSettings {
    private EdgeSettings() {}
    public static void migrate(SharedPreferences prefs) {
        SharedPreferences.Editor edit=prefs.edit();
        if(!prefs.contains(EdgeSegmentSettings.KEY) && prefs.contains("mrt_segment_characters"))
            edit.putInt(EdgeSegmentSettings.KEY,prefs.getInt("mrt_segment_characters",EdgeSegmentSettings.DEFAULT));
        if(!prefs.contains(EdgePrefetchSettings.KEY) && prefs.contains("mrt_prefetch_ahead"))
            edit.putInt(EdgePrefetchSettings.KEY,prefs.getInt("mrt_prefetch_ahead",EdgePrefetchSettings.DEFAULT));
        for(String key:prefs.getAll().keySet()) {
            if(key.startsWith("mrt_") || key.startsWith("speechkit_") || key.startsWith("native_selected_") || key.equals("edge_auto_continue") || key.equals("edge_prefetch"))
                edit.remove(key);
        }
        edit.apply();
    }
}
