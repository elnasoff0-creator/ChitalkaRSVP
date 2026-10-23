package ru.reader.rsvp;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.view.Gravity;
import android.widget.*;

/** Shared WPM preference. Speech rate is an estimate because voice cadence varies. */
public final class TtsWpm {
    public static final String KEY="tts_wpm";
    private TtsWpm(){}
    public static int get(Context c){return Math.max(100,Math.min(1000,c.getSharedPreferences("reader",0).getInt(KEY,240)));}
    public static void set(Context c,int value){c.getSharedPreferences("reader",0).edit().putInt(KEY,Math.max(100,Math.min(1000,value))).apply();}
    public static float rate(Context c){return Math.max(0.5f,Math.min(5f,get(c)/200f));}
    public static void dialog(Activity activity,Runnable changed){
        int dp=(int)(activity.getResources().getDisplayMetrics().density*1.0f+.5f);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(1);content.setPadding(16*dp,12*dp,16*dp,6*dp);
        TextView value=new TextView(activity);value.setGravity(Gravity.CENTER);value.setTextSize(27);value.setText(get(activity)+" слов/мин");content.addView(value);
        LinearLayout stepper=new LinearLayout(activity);stepper.setGravity(Gravity.CENTER);content.addView(stepper);
        Button minus=new Button(activity);minus.setText("−10");stepper.addView(minus,new LinearLayout.LayoutParams(0,56*dp,1));
        Button plus=new Button(activity);plus.setText("+10");stepper.addView(plus,new LinearLayout.LayoutParams(0,56*dp,1));
        Runnable refresh=()->{value.setText(get(activity)+" слов/мин");if(changed!=null)changed.run();};
        minus.setOnClickListener(v->{set(activity,get(activity)-10);refresh.run();});plus.setOnClickListener(v->{set(activity,get(activity)+10);refresh.run();});
        TextView label=new TextView(activity);label.setText("Быстрый выбор");label.setPadding(4*dp,10*dp,0,4*dp);content.addView(label);
        LinearLayout presets=new LinearLayout(activity);content.addView(presets);
        for(int n:new int[]{100,200,300,400,500}){
            Button b=new Button(activity);b.setText(String.valueOf(n));b.setTextSize(12);b.setAllCaps(false);b.setPadding(0,0,0,0);
            presets.addView(b,new LinearLayout.LayoutParams(0,49*dp,1));b.setOnClickListener(v->{set(activity,n);refresh.run();});
        }
        new AlertDialog.Builder(activity).setTitle("Скорость озвучивания").setView(content).setPositiveButton("Готово",(d,w)->{if(changed!=null)changed.run();}).show();
    }
}
