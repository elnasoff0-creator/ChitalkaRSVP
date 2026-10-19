package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Book companion, intentionally rendered with native Android views and local SVG-like VectorDrawables. */
public final class ChteicoActivity extends Activity {
    private LinearLayout root,content;
    private int bg,fg,accent;
    private SharedPreferences p;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);show();}
    @Override protected void onResume(){super.onResume();if(root!=null)show();}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int contrast(int c){return (.299*Color.red(c)+.587*Color.green(c)+.114*Color.blue(c))<135?Color.WHITE:0xff30221e;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(fg);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(16),dp(15),dp(16),dp(15));l.setBackground(shape(bg==0xff1e2025?0xff373538:0xfffff9f2,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(11));content.addView(l,lp);return l;}
    private void gap(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(-1,dp(n)));}
    private TextView button(String s,Runnable click){TextView t=text(s,14,true);t.setGravity(Gravity.CENTER);t.setTextColor(contrast(accent));t.setBackground(shape(accent,14));t.setPadding(dp(10),dp(13),dp(10),dp(13));t.setOnClickListener(v->click.run());return t;}
    private String mins(String key){return String.valueOf(p.getLong(key,0)/60);}
    private void stat(LinearLayout container,String label,String value){LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(9),dp(13),dp(9),dp(13));cell.setBackground(shape(bg==0xff1e2025?0xff444349:0xfff0e6dc,14));
        TextView big=text(value,24,true);big.setGravity(Gravity.CENTER);cell.addView(big);
        TextView sub=text(label,12,false);sub.setGravity(Gravity.CENTER);cell.addView(sub);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));container.addView(cell,lp);}
    private void setting(LinearLayout l,String label,String key,boolean fallback){Switch sw=new Switch(this);sw.setText(label);sw.setTextColor(fg);sw.setTextSize(14);sw.setChecked(p.getBoolean(key,fallback));
        sw.setPadding(0,dp(9),0,dp(9));l.addView(sw);sw.setOnCheckedChangeListener((v,on)->{p.edit().putBoolean(key,on).apply();ChteicoNotifications.schedule(this);});}
    private void show(){
        SharedPreferences settings=getSharedPreferences("reader",MODE_PRIVATE);
        bg=settings.getInt("bg",0xfff3e7d5);fg=contrast(bg);accent=ReaderButtonColors.accent(this,0xff915439);
        p=ChteicoStore.prefs(this);
        root=column();root.setBackgroundColor(bg);root.setPadding(dp(13),dp(22),dp(13),dp(9));setContentView(root);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",38,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());title.addView(back,new LinearLayout.LayoutParams(dp(45),dp(52)));
        TextView name=text("Чтейцо",25,true);title.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        ImageView settingsIcon=new ImageView(this);settingsIcon.setImageResource(R.drawable.ic_settings);settingsIcon.setColorFilter(fg);settingsIcon.setPadding(dp(10),dp(10),dp(10),dp(10));title.addView(settingsIcon,new LinearLayout.LayoutParams(dp(46),dp(46)));
        settingsIcon.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class).putExtra("chteico_tab",true)));
        root.addView(title);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);content=column();content.setPadding(dp(2),dp(5),dp(2),dp(3));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!ChteicoStore.enabled(this)){
            LinearLayout disabled=card();disabled.addView(text("Ваш книжный питомец пока спит",19,true));gap(disabled,10);
            disabled.addView(text("Включите «Чтейцо» в настройках, чтобы он рос вместе с чтением и аудиокнигами.",15,false));gap(disabled,12);
            disabled.addView(button("Открыть настройки",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("chteico_tab",true))));return;
        }
        int stage=ChteicoStore.stage(this), branch=ChteicoStore.branch(this);
        LinearLayout hero=card();hero.setGravity(Gravity.CENTER_HORIZONTAL);
        ChteicoPetView pet=new ChteicoPetView(this);
        hero.addView(pet,new LinearLayout.LayoutParams(dp(232),dp(229)));
        TextView stageName=text("Стадия: "+ChteicoStore.stageName(stage),18,true);stageName.setGravity(Gravity.CENTER);hero.addView(stageName);
        TextView branchName=text("Направление: "+ChteicoStore.branchName(branch),13,false);branchName.setGravity(Gravity.CENTER);hero.addView(branchName);gap(hero,9);
        TextView phrase=text(stage==ChteicoStore.EGG?"Откройте книгу или аудиокнигу и вернитесь в библиотеку — начнётся вылупление!":
            stage==ChteicoStore.CHICK?"Я расту вместе с вами! Закончим книгу или аудиокнигу?":"Ура! Я Чтун! Продолжаем книжные приключения!",14,false);
        phrase.setGravity(Gravity.CENTER);if(p.getBoolean("reactions_enabled",true))hero.addView(phrase);gap(hero,10);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setProgress(ChteicoStore.progress(this));
        if(android.os.Build.VERSION.SDK_INT>=21)progress.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));hero.addView(progress,new LinearLayout.LayoutParams(-1,dp(12)));gap(hero,5);
        TextView progressLabel=text(stage==ChteicoStore.ADULT?"Взрослая стадия достигнута":stage==ChteicoStore.EGG?"До вылупления: "+ChteicoStore.progress(this)+"%":"До Чтуна: "+ChteicoStore.progress(this)+"% · завершите книгу или аудиокнигу",12,false);
        progressLabel.setGravity(Gravity.CENTER);hero.addView(progressLabel);
        LinearLayout stats=card();stats.addView(text("Статистика чтения и аудио",18,true));gap(stats,8);
        LinearLayout stats1=new LinearLayout(this);stats.addView(stats1);
        stat(stats1,"Прочитано книг",String.valueOf(p.getInt("finished_books_count",0)));
        stat(stats1,"Аудиокниг",String.valueOf(p.getInt("finished_audio_count",0)));
        stat(stats1,"Минут чтения",String.valueOf((p.getLong("seconds_classic",0)+p.getLong("seconds_speed",0))/60));
        LinearLayout stats2=new LinearLayout(this);stats.addView(stats2);
        stat(stats2,"Минут аудио",mins("seconds_audio"));
        stat(stats2,"Дней подряд",String.valueOf(p.getInt("streak",0)));
        stat(stats2,"Сессий",String.valueOf(p.getInt("sessions",0)));
        gap(stats,6);
        TextView wordsRead=text("Пройдено слов: "+p.getLong("words_advanced",0)+" · Открыто книг: "+p.getInt("opened_books_count",0),13,false);
        stats.addView(wordsRead);gap(stats,8);stats.addView(button("Подробная статистика",this::statistics));
        LinearLayout care=card();care.addView(text("Забота и напоминания",18,true));gap(care,8);
        care.addView(text("Питомец радуется чтению и прослушиванию. Он не погибнет и не потеряет стадию, если вы сделаете перерыв.",13,false));
        setting(care,"Ежедневное напоминание","daily",false);setting(care,"Напоминать после 3 дней без чтения","inactive",false);
        setting(care,"Сообщать о достижениях","achievements_notice",false);
        LinearLayout rewards=card();rewards.addView(text("Награды",18,true));gap(rewards,8);
        rewards.addView(text("Первая книга   "+(p.getInt("finished_books_count",0)>0?"✓":"○"),15,false));gap(rewards,5);
        rewards.addView(text("Первая аудиокнига   "+(p.getInt("finished_audio_count",0)>0?"✓":"○"),15,false));gap(rewards,5);
        rewards.addView(text("Серия 7 дней   "+(p.getInt("best_streak",0)>=7?"✓":"○"),15,false));gap(rewards,5);
        rewards.addView(text("Взрослый Чтун   "+(stage==ChteicoStore.ADULT?"✓":"○"),15,false));
        LinearLayout info=card();info.addView(text("Что влияет на рост?",18,true));gap(info,7);
        info.addView(text("• Реальное время обычного чтения и режима «Курсор»\n• Скорочтение и просмотр слов\n• Прослушивание аудиокниг\n• Завершение книги или аудиокниги\n• Серия дней активности",14,false));
        gap(info,10);info.addView(button("Настройки Чтейца",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("chteico_tab",true))));
    }
    private void statistics(){new AlertDialog.Builder(this).setTitle("Статистика Чтейца")
        .setMessage("Книг открыто: "+p.getInt("opened_books_count",0)+"\nПрочитано: "+p.getInt("finished_books_count",0)
        +"\nАудиокниг открыто: "+p.getInt("opened_audio_count",0)+"\nПрослушано полностью: "+p.getInt("finished_audio_count",0)
        +"\nОбычное чтение / Курсор: "+mins("seconds_classic")+" мин\nСкорочтение: "+mins("seconds_speed")
        +" мин\nАудио: "+mins("seconds_audio")+" мин\nСерия дней: "+p.getInt("streak",0)
        +"\nЛучшая серия: "+p.getInt("best_streak",0)
        +"\nСессий: "+p.getInt("sessions",0)+"\nПройдено слов: "+p.getLong("words_advanced",0)+"\nВетка: "+ChteicoStore.branchName(ChteicoStore.branch(this)))
        .setPositiveButton("Понятно",null).show();}
}
