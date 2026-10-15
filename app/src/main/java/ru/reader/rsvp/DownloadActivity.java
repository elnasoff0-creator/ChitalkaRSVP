package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/** Persistent download queue UI; tasks themselves run independently in DownloadWorker. */
public class DownloadActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout jobs;
    private boolean visible;
    private int background = 0xfff3e7d5, foreground = 0xff30221e;
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private TextView label(String s,int size,boolean bold) { TextView t=new TextView(this);t.setText(s);t.setTextColor(foreground);t.setTextSize(size);if(bold)t.setTypeface(null,Typeface.BOLD);return t; }
    private GradientDrawable panel(){GradientDrawable d=new GradientDrawable();d.setColor(background==0xff1e2025?0xff313339:0xfffff8ef);d.setCornerRadius(dp(18));return d;}
    private Button button(String text,Runnable click){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setTextSize(14);
        b.setTextColor(foreground);b.setBackground(panel());
        b.setMinHeight(dp(48));b.setGravity(Gravity.CENTER);b.setOnClickListener(v->click.run());return b;
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);SharedPreferences pref=getSharedPreferences("reader",0);background=pref.getInt("bg",background);foreground=pref.getInt("fg",foreground);
        ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(14),dp(30),dp(14),dp(20));page.setBackgroundColor(background);scroll.addView(page);setContentView(scroll);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);page.addView(bar);
        android.widget.ImageView back=new android.widget.ImageView(this);back.setImageResource(R.drawable.ic_back);
        back.setColorFilter(foreground);back.setPadding(dp(12),dp(12),dp(12),dp(12));
        back.setBackground(panel());bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->BackNavigation.back(this));
        TextView title=label("Загрузки",24,true);bar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button add=button("+",()->startActivity(new Intent(this,UrlImportActivity.class)));
        add.setContentDescription("Импорт по ссылке");bar.addView(add,new LinearLayout.LayoutParams(dp(48),dp(48)));
        TextView subtitle=label("Очередь сохранена. При переходе в другие разделы загрузка продолжится.",13,false);subtitle.setPadding(0,dp(12),0,dp(14));page.addView(subtitle);
        jobs=new LinearLayout(this);jobs.setOrientation(1);page.addView(jobs);
    }
    @Override protected void onResume(){super.onResume();visible=true;handler.post(poller);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(poller);super.onPause();}
    private final Runnable poller=new Runnable(){@Override public void run(){if(!visible)return;render();handler.postDelayed(this,1200);}};
    private void send(String source,String action){Intent i=new Intent(this,DownloadWorker.class).setAction(action).putExtra("source",source);try{if(Build.VERSION.SDK_INT>=26 && DownloadWorker.START.equals(action))startForegroundService(i);else startService(i);}catch(Exception e){android.widget.Toast.makeText(this,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}
    private void render(){if(jobs==null)return;jobs.removeAllViews();ArrayList<BookImportManager.Job> all=BookImportManager.load(this);
        if(all.isEmpty()){TextView empty=label("Пока нет загрузок. Нажмите +, чтобы импортировать аудиокнигу по ссылке.",16,false);jobs.addView(empty);return;}
        for(BookImportManager.Job j:all){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(13),dp(14),dp(13),dp(12));card.setBackground(panel());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(12);jobs.addView(card,cp);
            TextView name=label(j.title.isEmpty()?j.source:j.title,18,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);
            int done=0,total=0;for(int s:j.states){if(s==100)done++;if(s>=0)total++;}
            TextView info=label("Глав: "+done+" / "+total+" · "+j.status+(j.bytes>0?" · "+String.format(Locale.ROOT,"%.1f МБ",j.bytes/1048576.0):""),13,false);card.addView(info);
            ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(Math.max(1,total));progress.setProgress(done);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(6));pp.topMargin=dp(10);pp.bottomMargin=dp(8);card.addView(progress,pp);
            if(!j.error.isEmpty()){TextView err=label(j.error,12,false);card.addView(err);}
            LinearLayout controls=new LinearLayout(this);card.addView(controls);
            Button pause=button("Пауза",()->send(j.source,DownloadWorker.PAUSE));controls.addView(pause,new LinearLayout.LayoutParams(0,-2,1));
            Button resume=button("Продолжить",()->send(j.source,DownloadWorker.START));controls.addView(resume,new LinearLayout.LayoutParams(0,-2,1));
            Button cancel=button("Отмена",()->send(j.source,DownloadWorker.CANCEL));controls.addView(cancel,new LinearLayout.LayoutParams(0,-2,1));
            Button details=button("Подробнее / главы",()->startActivity(new Intent(this,UrlImportActivity.class).putExtra("source",j.source)));card.addView(details);
            for(int i=0;i<j.chapters.size();i++){int state=i<j.states.size()?j.states.get(i):-1;String stateText=state==100?"Готово":state<0?"Не выбрана":state+"%";TextView c=label(String.format(Locale.ROOT,"%03d · %s — %s",i+1,j.chapters.get(i).name,stateText),12,false);c.setPadding(0,dp(3),0,dp(3));card.addView(c);}
        }
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
