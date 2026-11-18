package ru.reader.rsvp;

import android.content.Context;
import android.graphics.drawable.Drawable;

/** Package-provided TTS artwork; no third-party icon assets are bundled. */
final class TtsEngineIcons {
    private TtsEngineIcons(){}
    static Drawable load(Context c,String packageId){
        if(packageId==null||packageId.isEmpty()){
            packageId=android.provider.Settings.Secure.getString(c.getContentResolver(),"tts_default_synth");
            if(packageId==null||packageId.isEmpty())return fallback(c);
        }
        try{return c.getPackageManager().getApplicationIcon(packageId);}
        catch(Exception ignored){return fallback(c);}
    }
    static Drawable fallback(Context c){return androidx.core.content.ContextCompat.getDrawable(c,R.drawable.ic_tts_engine);}
    static String label(Context c,String packageId){
        if(packageId==null||packageId.isEmpty())return "Системный TTS Android";
        try{return c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(packageId,0)).toString();}
        catch(Exception ignored){return packageId;}
    }
}
