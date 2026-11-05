package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;

/** Which complete real sentence to play after the user resumes paused TTS. */
final class TtsResumeChoice {
    static final String KEY="tts_resume_sentence_mode";
    static final String PREVIOUS="previous";
    static final String CURRENT="current";
    private TtsResumeChoice(){}

    static String get(Context context){
        SharedPreferences prefs=context.getSharedPreferences("reader",Context.MODE_PRIVATE);
        String value=prefs.getString(KEY,"");
        if(PREVIOUS.equals(value)||CURRENT.equals(value))return value;
        // Preserve the previous setting: 'off' now means start current sentence.
        return prefs.getBoolean("tts_resume_previous_sentence",true)?PREVIOUS:CURRENT;
    }
    static boolean previous(Context context){return PREVIOUS.equals(get(context));}
    static void set(Context context,String value){
        if(!PREVIOUS.equals(value)&&!CURRENT.equals(value))throw new IllegalArgumentException("Unknown TTS resume mode");
        context.getSharedPreferences("reader",Context.MODE_PRIVATE).edit()
            .putString(KEY,value)
            .putBoolean("tts_resume_previous_sentence",PREVIOUS.equals(value))
            .apply();
    }
    static String summary(Context context){
        return previous(context)?"С начала предыдущего предложения":"С начала текущего предложения";
    }
}
