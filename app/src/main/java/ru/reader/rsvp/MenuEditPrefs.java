package ru.reader.rsvp;

import android.content.Context;

/** Shared entry mode for both home and drawer editors; button is the default. */
final class MenuEditPrefs {
    static final String LONG_PRESS="menu_editor_long_press";
    static boolean longPress(Context context){
        return context.getSharedPreferences("reader",0).getBoolean(LONG_PRESS,false);
    }
    private MenuEditPrefs(){}
}
