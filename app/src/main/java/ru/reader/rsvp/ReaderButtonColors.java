package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Color;
import android.content.SharedPreferences;

/** Two independently configurable action-button palettes. A zero value keeps each screen's native default. */
public final class ReaderButtonColors {
    private ReaderButtonColors() {}
    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("reader", Context.MODE_PRIVATE);
    }
    public static int neutral(Context context, int defaultColor) {
        int saved=preferences(context).getInt("button_color",0);
        return saved==0?defaultColor:saved;
    }
    public static int accent(Context context, int defaultColor) {
        int saved=preferences(context).getInt("button_accent_color",0);
        return saved==0?defaultColor:saved;
    }
    public static int contrastingText(int color) {
        double brightness=Color.red(color)*0.299+Color.green(color)*0.587+Color.blue(color)*0.114;
        return brightness<140?Color.WHITE:0xff30221e;
    }
}
