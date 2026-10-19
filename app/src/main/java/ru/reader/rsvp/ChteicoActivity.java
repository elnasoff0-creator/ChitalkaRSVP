package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Minimal companion screen; details of evolution and its branch stay private. */
public final class ChteicoActivity extends Activity {
    private LinearLayout root,content;
    private ProgressBar progressBar;
    private TextView progressLabel;
    private ChteicoPetView pet;
    private int bg,fg,accent;
    private SharedPreferences p;
    private boolean firstResume=true;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);show();}
    @Override protected void onResume(){super.onResume();if(firstResume)firstResume=false;else show();}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private int contrast(int c){return (.299*Color.red(c)+.587*Color.green(c)+.114*Color.blue(c))<135?Color.WHITE:0xff30221e;}
    private TextView text(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(fg);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private LinearLayout card(){LinearLayout l=column();l.setPadding(dp(16),dp(15),dp(16),dp(15));l.setBackground(shape(bg==0xff1e2025?0xff373538:0xfffff9f2,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(11));content.addView(l,lp);return l;}
    private void gap(LinearLayout l,int n){View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(-1,dp(n)));}
    private TextView button(String s,Runnable click){TextView t=text(s,14,true);t.setGravity(Gravity.CENTER);t.setTextColor(contrast(accent));t.setBackground(shape(accent,14));t.setPadding(dp(10),dp(13),dp(10),dp(13));t.setOnClickListener(v->click.run());return t;}
    private void stat(LinearLayout container,String label,String value){LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(9),dp(13),dp(9),dp(13));cell.setBackground(shape(bg==0xff1e2025?0xff444349:0xfff0e6dc,14));
        TextView big=text(value,24,true);big.setGravity(Gravity.CENTER);cell.addView(big);
        TextView sub=text(label,12,false);sub.setGravity(Gravity.CENTER);cell.addView(sub);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));container.addView(cell,lp);}
    private String mins(String key){return String.valueOf(p.getLong(key,0)/60);}
    private void askName(){
        if(!ChteicoStore.canName(this))return;
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("Имя питомца");input.setText(ChteicoStore.petName(this));
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});input.setSelectAllOnFocus(true);
        LinearLayout wrap=column();wrap.setPadding(dp(18),dp(4),dp(18),0);wrap.addView(input);
        new AlertDialog.Builder(this).setTitle("Как назвать питомца?")
            .setMessage("Выбрать имя можно только один раз. Оно останется с питомцем, когда он вырастет.")
            .setView(wrap).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                if(ChteicoStore.setPetName(this,input.getText().toString()))show();
                else android.widget.Toast.makeText(this,"Введите имя до 18 символов",android.widget.Toast.LENGTH_SHORT).show();
            }).show();
    }
    /** Transparent, temporary waves at every tap: no speech or comic bubbles for petting. */
    private final class TapWaves extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase=-1f;
        private ValueAnimator animator;
        TapWaves(){super(ChteicoActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void burst(){if(animator!=null)animator.cancel();animator=ValueAnimator.ofFloat(0,1);animator.setDuration(470);
            animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);if(phase<0||phase>=1)return;
            float x=getWidth()/2f,y=getHeight()/2f;float min=Math.min(getWidth(),getHeight());
            paint.setColor(accent);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));
            for(int i=0;i<2;i++){
                float t=(phase+i*.23f)%1f;
                paint.setAlpha(Math.round(130*(1-t)));
                c.drawCircle(x,y,min*(.25f+.23f*t),paint);
            }
        }
        @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    }
    private void show(){
        SharedPreferences settings=getSharedPreferences("reader",MODE_PRIVATE);
        bg=settings.getInt("bg",0xfff3e7d5);fg=contrast(bg);accent=ReaderButtonColors.accent(this,0xff915439);
        p=ChteicoStore.prefs(this);
        root=column();root.setBackgroundColor(bg);root.setPadding(dp(13),dp(22),dp(13),dp(9));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",38,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(45),dp(52)));
        View spacer=new View(this);top.addView(spacer,new LinearLayout.LayoutParams(0,dp(20),1));
        ImageView settingsIcon=new ImageView(this);settingsIcon.setImageResource(R.drawable.ic_settings);settingsIcon.setColorFilter(fg);
        settingsIcon.setPadding(dp(10),dp(10),dp(10),dp(10));top.addView(settingsIcon,new LinearLayout.LayoutParams(dp(46),dp(46)));
        settingsIcon.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class).putExtra("chteico_tab",true)));
        root.addView(top);
        ScrollView scroll=new ScrollView(this);content=column();content.setPadding(dp(2),dp(5),dp(2),dp(3));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!ChteicoStore.enabled(this)){
            LinearLayout disabled=card();disabled.addView(text("Ваш книжный спутник пока спит",19,true));gap(disabled,10);
            disabled.addView(button("Открыть настройки",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("chteico_tab",true))));return;
        }
        int stage=ChteicoStore.stage(this);
        LinearLayout hero=card();hero.setGravity(Gravity.CENTER_HORIZONTAL);
        // An introduction appears only after evolution; custom names do not hide the species reveal.
        String intro=ChteicoStore.consumeIntroduction(this);
        if(intro!=null){
            TextView bubble=text(intro,16,true);bubble.setGravity(Gravity.CENTER);bubble.setBackground(shape(bg==0xff1e2025?0xff514a49:0xffeee0d0,18));
            bubble.setPadding(dp(14),dp(12),dp(14),dp(12));
            hero.addView(bubble,new LinearLayout.LayoutParams(-1,-2));gap(hero,8);
            ui.postDelayed(()->{if(bubble.getParent()!=null){bubble.setVisibility(View.GONE);}},7500);
        }
        FrameLayout petFrame=new FrameLayout(this);
        hero.addView(petFrame,new LinearLayout.LayoutParams(dp(248),dp(247)));
        pet=new ChteicoPetView(this);petFrame.addView(pet,new FrameLayout.LayoutParams(-1,-1));
        TapWaves waves=new TapWaves();petFrame.addView(waves,new FrameLayout.LayoutParams(-1,-1));
        waves.setOnClickListener(v->{
            waves.burst();pet.animate().cancel();pet.animate().translationY(-dp(5)).setDuration(110).withEndAction(()->pet.animate().translationY(0).setDuration(180).start()).start();
            if(ChteicoStore.stage(this)==ChteicoStore.EGG){
                boolean hatched=ChteicoStore.petTapped(this);
                if(progressBar!=null)progressBar.setProgress(ChteicoStore.progress(this));
                if(progressLabel!=null)progressLabel.setText("До следующей эволюции: "+ChteicoStore.progress(this)+"%");
                pet.refresh();
                if(hatched)ui.postDelayed(this::show,520);
            }
        });
        LinearLayout nameRow=new LinearLayout(this);nameRow.setGravity(Gravity.CENTER);
        TextView name=text(ChteicoStore.petName(this),23,true);nameRow.addView(name);
        if(ChteicoStore.canName(this)){
            TextView edit=text("  ✎",21,false);edit.setContentDescription("Назвать питомца");edit.setOnClickListener(v->askName());nameRow.addView(edit);
            name.setOnClickListener(v->askName());
        }
        hero.addView(nameRow);gap(hero,12);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progressBar.setMax(100);progressBar.setProgress(ChteicoStore.progress(this));
        if(android.os.Build.VERSION.SDK_INT>=21)progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        hero.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(9)));gap(hero,7);
        progressLabel=text(stage==ChteicoStore.ADULT?"С вами настоящий книжный друг":"До следующей эволюции: "+ChteicoStore.progress(this)+"%",13,false);
        progressLabel.setGravity(Gravity.CENTER);hero.addView(progressLabel);
        LinearLayout stats=card();stats.addView(text("Статистика чтения и аудио",18,true));gap(stats,8);
        LinearLayout a=new LinearLayout(this);stats.addView(a);
        stat(a,"Прочитано книг",String.valueOf(p.getInt("finished_books_count",0)));
        stat(a,"Аудиокниг",String.valueOf(p.getInt("finished_audio_count",0)));
        stat(a,"Минут чтения",String.valueOf((p.getLong("seconds_classic",0)+p.getLong("seconds_speed",0))/60));
        LinearLayout b=new LinearLayout(this);stats.addView(b);
        stat(b,"Минут аудио",mins("seconds_audio"));
        stat(b,"Дней подряд",String.valueOf(p.getInt("streak",0)));
        stat(b,"Сессий",String.valueOf(p.getInt("sessions",0)));
        gap(stats,8);stats.addView(button("Подробная статистика",this::statistics));
        LinearLayout rewards=card();rewards.addView(text("Награды",18,true));gap(rewards,8);
        rewards.addView(text("Первая книга   "+(p.getInt("finished_books_count",0)>0?"✓":"○"),15,false));gap(rewards,6);
        rewards.addView(text("Первая аудиокнига   "+(p.getInt("finished_audio_count",0)>0?"✓":"○"),15,false));gap(rewards,6);
        rewards.addView(text("Серия 7 дней   "+(p.getInt("best_streak",0)>=7?"✓":"○"),15,false));
    }
    private void statistics(){new AlertDialog.Builder(this).setTitle("Книжная статистика")
        .setMessage("Книг открыто: "+p.getInt("opened_books_count",0)+"\nПрочитано: "+p.getInt("finished_books_count",0)
        +"\nАудиокниг открыто: "+p.getInt("opened_audio_count",0)+"\nПрослушано полностью: "+p.getInt("finished_audio_count",0)
        +"\nОбычное чтение: "+mins("seconds_classic")+" мин\nСкорочтение и Курсор: "+mins("seconds_speed")
        +" мин\nАудио: "+mins("seconds_audio")+" мин\nСерия дней: "+p.getInt("streak",0)
        +"\nЛучшая серия: "+p.getInt("best_streak",0)
        +"\nСессий: "+p.getInt("sessions",0)+"\nПройдено слов: "+p.getLong("words_advanced",0))
        .setPositiveButton("Понятно",null).show();}
}
