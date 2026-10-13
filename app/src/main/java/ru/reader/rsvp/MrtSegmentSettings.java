package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

/** Persisted input length for the hosted MRT text-to-speech page. */
public final class MrtSegmentSettings {
    public static final String KEY = "mrt_segment_characters";
    public static final int DEFAULT = 1200;
    public static final int MIN = 100;
    public static final int MAX = 19000; // Below the hosted MRT 20,000-character limit.
    private static final int[] PRESETS = {100, 200, 300, 500, 800, 1000, 2000, 4000, 8000, 12000, 19000};

    private MrtSegmentSettings() {}

    public static int get(SharedPreferences preferences) {
        return clamp(preferences.getInt(KEY, DEFAULT));
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    public static void show(Activity activity, SharedPreferences preferences, java.util.function.IntConsumer onChange) {
        final String[] options = new String[PRESETS.length + 1];
        int selected = -1;
        for (int i = 0; i < PRESETS.length; i++) {
            options[i] = PRESETS[i] + " символов";
            if (PRESETS[i] == get(preferences)) selected = i;
        }
        options[PRESETS.length] = "Указать вручную…";
        new AlertDialog.Builder(activity)
            .setTitle("Длина фрагмента MRT")
            .setSingleChoiceItems(options, selected, (dialog, choice) -> {
                dialog.dismiss();
                if (choice == PRESETS.length) {
                    showCustom(activity, preferences, onChange);
                    return;
                }
                int length = PRESETS[choice];
                preferences.edit().putInt(KEY, length).apply();
                onChange.accept(length);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private static void showCustom(Activity activity, SharedPreferences preferences, java.util.function.IntConsumer onChange) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setText(String.valueOf(get(preferences)));
        int pad = (int) (18 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(activity)
            .setTitle("Количество символов (100–19000)")
            .setView(input)
            .setPositiveButton("Применить", (dialog, which) -> {
                try {
                    int length = Integer.parseInt(input.getText().toString().trim());
                    if (length < MIN || length > MAX) {
                        Toast.makeText(activity, "Допустимый диапазон: 100–19000 символов", Toast.LENGTH_LONG).show();
                        showCustom(activity, preferences, onChange);
                        return;
                    }
                    preferences.edit().putInt(KEY, length).apply();
                    onChange.accept(length);
                } catch (NumberFormatException error) {
                    Toast.makeText(activity, "Введите целое число от 100 до 19000", Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Отмена", null)
            .show();
    }
}
