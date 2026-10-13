package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;

/** Number of MRT clips in the playback window, including the active clip. */
public final class MrtPrefetchSettings {
    public static final String KEY = "mrt_prefetch_ahead";
    public static final int MIN = 3;
    public static final int MAX = 10;
    public static final int DEFAULT = 3;

    private MrtPrefetchSettings() {}

    public static int clamp(int count) {
        return Math.max(MIN, Math.min(MAX, count));
    }

    public static int get(SharedPreferences preferences) {
        return clamp(preferences.getInt(KEY, DEFAULT));
    }

    public static String label(int count) {
        int total = clamp(count);
        return "В очереди: " + total + " фрагм. (текущий + " + (total - 1) + ")";
    }

    public static void show(Activity activity, SharedPreferences preferences,
                            java.util.function.IntConsumer onChange) {
        String[] options = new String[MAX - MIN + 1];
        for (int count = MIN; count <= MAX; count++) {
            options[count - MIN] = count + " фрагментов: текущий + " + (count - 1);
        }
        new AlertDialog.Builder(activity)
            .setTitle("Количество фрагментов предзагрузки MRT (3–10)")
            .setSingleChoiceItems(options, get(preferences) - MIN, (dialog, index) -> {
                int count = MIN + index;
                preferences.edit().putInt(KEY, count).apply();
                dialog.dismiss();
                if (onChange != null) onChange.accept(count);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
