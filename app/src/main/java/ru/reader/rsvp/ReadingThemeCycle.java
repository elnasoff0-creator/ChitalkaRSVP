package ru.reader.rsvp;

import android.content.SharedPreferences;

/** The five reader themes; color snapshot is persisted for the custom preset. */
final class ReadingThemeCycle {
    static final int DAY=0, NIGHT=1, SOFT=2, SEPIA=3, CUSTOM=4;
    private static final int[] BACKGROUND={0xffffffff,0xff1e2025,0xffdfe9d9,0xfff3e7d5};
    private static final int[] FOREGROUND={0xff202020,0xffe8e5db,0xff223a29,0xff30221e};
    private static final int[] ICONS={R.drawable.ic_reading_theme_day,R.drawable.ic_reading_theme_night,
        R.drawable.ic_reading_theme_soft,R.drawable.ic_reading_theme_sepia,R.drawable.ic_reading_theme_custom};
    private static final String[] NAMES={"Дневная","Ночная","Мягкая","Сепия","Пользовательская"};
    private ReadingThemeCycle(){}

    static int current(SharedPreferences p){
        int bg=p.getInt("bg",BACKGROUND[SEPIA]), fg=p.getInt("fg",FOREGROUND[SEPIA]);
        for(int i=0;i<4;i++)if(bg==BACKGROUND[i]&&fg==FOREGROUND[i])return i;
        return CUSTOM;
    }
    static final int[] DARK_ICONS={R.drawable.ic_reading_theme_day_dark,R.drawable.ic_reading_theme_night_dark,
        R.drawable.ic_reading_theme_soft_dark,R.drawable.ic_reading_theme_sepia_dark,R.drawable.ic_reading_theme_custom_dark};
    static int icon(SharedPreferences p){
        int index=current(p);
        return isDark(p.getInt("bg",BACKGROUND[SEPIA]))?DARK_ICONS[index]:ICONS[index];
    }
    static String name(SharedPreferences p){return NAMES[current(p)];}
    static void saveCustom(SharedPreferences p,int bg,int fg){
        p.edit().putInt("theme_custom_bg",bg).putInt("theme_custom_fg",fg).apply();
    }
    static void apply(SharedPreferences p,int index){
        int next=Math.max(DAY,Math.min(CUSTOM,index));
        if(next==CUSTOM){
            int bg=p.getInt("theme_custom_bg",0xffd9ecf8);
            int fg=p.getInt("theme_custom_fg",0xff203447);
            p.edit().putInt("bg",bg).putInt("fg",fg).apply();
        }else p.edit().putInt("bg",BACKGROUND[next]).putInt("fg",FOREGROUND[next]).apply();
    }
    static void next(SharedPreferences p){
        int current=current(p);
        if(current==CUSTOM) saveCustom(p,p.getInt("bg",0xffd9ecf8),p.getInt("fg",0xff203447));
        apply(p,(current+1)%5);
    }
    static boolean isDark(int bg){
        return .299*((bg>>16)&255)+.587*((bg>>8)&255)+.114*(bg&255)<125;
    }
}
