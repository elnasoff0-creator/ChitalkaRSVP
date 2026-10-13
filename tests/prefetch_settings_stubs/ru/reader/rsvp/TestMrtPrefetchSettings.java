package ru.reader.rsvp;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

public class TestMrtPrefetchSettings {
    static final class Preferences implements SharedPreferences, SharedPreferences.Editor {
        Map<String,Integer> data=new HashMap<>();
        @Override public int getInt(String key,int defaultValue) { return data.getOrDefault(key,defaultValue); }
        @Override public SharedPreferences.Editor edit() { return this; }
        @Override public SharedPreferences.Editor putInt(String key,int value) { data.put(key,value); return this; }
        @Override public void apply() {}
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void main(String[] unused) {
        Preferences prefs=new Preferences();
        check(MrtPrefetchSettings.get(prefs)==3,"default 3");
        prefs.data.put(MrtPrefetchSettings.KEY,1); // Migration from old builds, which allowed 1.
        check(MrtPrefetchSettings.get(prefs)==3,"legacy 1 -> 3");
        prefs.data.put(MrtPrefetchSettings.KEY,100);
        check(MrtPrefetchSettings.get(prefs)==10,"clamp >10");
        prefs.data.put(MrtPrefetchSettings.KEY,3);
        final int[] changed={-1};
        MrtPrefetchSettings.show(new Activity(),prefs,value->changed[0]=value);
        check(AlertDialog.Builder.lastOptions.length==8,"eight options from 3 to 10");
        check(AlertDialog.Builder.lastSelected==0,"selection for 3");
        for(int i=0;i<8;i++) {
            String label=AlertDialog.Builder.lastOptions[i];
            check(label.startsWith((3+i)+" "),"option "+i);
        }
        AlertDialog.Builder.lastListener.onClick(new AlertDialog(),7);
        check(changed[0]==10,"callback for 10");
        check(MrtPrefetchSettings.get(prefs)==10,"10 persisted");
        check(MrtPrefetchSettings.label(10).contains("текущий + 9"),"10 = current + 9");
        System.out.println("PASS: MRT prefetch setting 3..10, saved selection, legacy clamp");
    }
}
