package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.audiofx.Equalizer;

/** Optional per-audio-session equalizer. Saved profiles work across ordinary audio and Edge MP3. */
public final class AudioEqualizer {
    private static Equalizer effect;
    private static int session=-1;
    private AudioEqualizer() {}

    public static final String[] KEYS={"off","flat","rock","pop","classical","jazz","electronic","hiphop","bass","voice","audiobook","podcast","soft","treble","custom"};
    public static final String[] LABELS={"Выключен","Обычный (Flat)","Рок","Поп","Классика","Джаз","Электронная музыка","Хип-хоп","Бас+","Вокал","Аудиокниги","Подкасты","Мягкое звучание","Высокие частоты","Пользовательский"};
    // Gain in dB at approximate 60/230/910/3600/14000 Hz. Mapped to device bands.
    private static final float[][] CURVES={
        {0,0,0,0,0},{0,0,0,0,0},{5,3,-2,2,4},{-1,2,3,2,-1},{-2,0,1,2,4},
        {2,2,-1,2,3},{5,3,0,3,5},{5,3,1,2,3},{7,5,1,-1,-2},{-3,-1,3,4,0},
        {-4,-1,3,4,-2},{-5,-2,4,4,-1},{-2,0,1,0,-2},{-3,-2,0,4,6},{0,0,0,0,0}
    };
    public static synchronized void attach(Context context,int audioSessionId){
        if(audioSessionId<=0)return;
        if(effect==null||session!=audioSessionId){
            release();
            try{effect=new Equalizer(0,audioSessionId);session=audioSessionId;}
            catch(Exception|LinkageError ignored){effect=null;session=-1;}
        }
        apply(context);
    }
    public static float[] gains(Context ctx){
        SharedPreferences p=ctx.getSharedPreferences("reader",0);
        String mode=p.getString("audio_equalizer","off");
        for(int i=0;i<KEYS.length-1;i++)if(KEYS[i].equals(mode))return CURVES[i].clone();
        String saved=p.getString("eq_custom_bands","0,0,0,0,0");
        String[] vals=saved.split(",");float[] out=new float[5];
        for(int i=0;i<Math.min(vals.length,5);i++)try{out[i]=Float.parseFloat(vals[i]);}catch(Exception ignored){}
        return out;
    }
    public static synchronized void apply(Context ctx){
        if(effect==null)return;
        try{
            String mode=ctx.getSharedPreferences("reader",0).getString("audio_equalizer","off");
            if("off".equals(mode)){effect.setEnabled(false);return;}
            float[] values=gains(ctx);
            short[] range=effect.getBandLevelRange();
            for(short i=0;i<effect.getNumberOfBands();i++){
                float hz=effect.getCenterFreq(i)/1000f;
                float gain=interpolate(values,hz);
                short millibels=(short)Math.max(range[0],Math.min(range[1],Math.round(gain*100f)));
                effect.setBandLevel(i,millibels);
            }
            effect.setEnabled(true);
        }catch(Exception|LinkageError ignored){release();}
    }
    private static float interpolate(float[] values,float hz){
        float[] marks={60,230,910,3600,14000};
        if(hz<=marks[0])return values[0];
        for(int i=1;i<marks.length;i++){
            if(hz<=marks[i]){
                float alpha=(float)(Math.log(hz/marks[i-1])/Math.log(marks[i]/marks[i-1]));
                return values[i-1]*(1-alpha)+values[i]*alpha;
            }
        }
        return values[4];
    }
    public static synchronized void release(){
        if(effect!=null)try{effect.setEnabled(false);effect.release();}catch(Exception ignored){}
        effect=null;session=-1;
    }
}
