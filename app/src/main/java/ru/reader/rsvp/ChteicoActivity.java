package ru.reader.rsvp;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import java.util.Random;

/** Reading companion screen with spoiler-free evolution, stats and earned rewards. */
public final class ChteicoActivity extends Activity {
    private LinearLayout root,content;
    private ProgressBar progressBar;
    private TextView progressLabel;
    private ChteicoPetView pet;
    private int bg,fg,accent;
    private SharedPreferences p;
    private boolean firstResume=true;
    private TextView speechBubble;
    private SpeechBubbleLayout speechContainer;
    private Runnable pendingSpeechHide;
    private final Random phraseRandom=new Random();
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
        new SwipeDialogs.Builder(this).setTitle("Как назвать питомца?")
            .setMessage("Выбрать имя можно только один раз. Оно останется с питомцем, когда он вырастет.")
            .setView(wrap).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                if(ChteicoStore.setPetName(this,input.getText().toString()))show();
                else android.widget.Toast.makeText(this,"Введите имя до 18 символов",android.widget.Toast.LENGTH_SHORT).show();
            }).show();
    }
    /** Short comic tap lines around the pet, matching the illustrated UI style. */
    private final class TapWaves extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase=-1f;
        private ValueAnimator animator;
        private final float[] angles={-145f,-112f,-68f,-34f,34f,68f,112f,145f};
        TapWaves(){super(ChteicoActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}        
        void burst(){if(animator!=null)animator.cancel();animator=ValueAnimator.ofFloat(0,1);animator.setDuration(320);
            animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            if(phase<0||phase>=1)return;
            // Anchor rays to the actual pet, although the drawing overlay occupies the
            // wider full-width container. This keeps the rays centred on every stage.
            float cx=pet.getLeft()+pet.getWidth()/2f;
            float cy=pet.getTop()+pet.getHeight()*0.47f;
            // Vectors use a 160-unit viewport and a 3.3-3.6-unit silhouette stroke.
            // Match the visible outline at its rendered size, not a thin 2dp UI line.
            float stroke=(pet.getWidth()/160f)*3.5f;
            // Reach nearly to the full width of the card, beyond the wing tips.
            // Leave room for rounded stroke caps to avoid edge clipping.
            float sideRadius=Math.min(cx, getWidth()-cx)-stroke/2f-dp(5);
            // A shorter parent must not clip the upper or lower tap rays.
            float topRadius=Math.max(dp(1),cy-stroke/2f-dp(6));
            float bottomRadius=Math.max(dp(1),getHeight()-cy-stroke/2f-dp(6));
            float maxRadius=Math.max(dp(1),Math.min(sideRadius,Math.min(topRadius,bottomRadius)));
            float inner=maxRadius*(0.80f+0.035f*phase);
            float outer=maxRadius*(0.96f+0.025f*phase);
            paint.setColor(0xff30221e); // Same dark-brown outline as the pet vectors.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setAlpha(Math.round(210*(1f-phase)));
            for(float angle:angles){
                double rad=Math.toRadians(angle);
                float x1=(float)(cx+Math.cos(rad)*inner);
                float y1=(float)(cy+Math.sin(rad)*inner);
                float x2=(float)(cx+Math.cos(rad)*outer);
                float y2=(float)(cy+Math.sin(rad)*outer);
                c.drawLine(x1,y1,x2,y2,paint);
            }
        }
        @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    }
    /** One-piece speech bubble with a tail and continuous outline. */
    private final class SpeechBubbleLayout extends FrameLayout {
        private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path=new Path();
        SpeechBubbleLayout(){super(ChteicoActivity.this);setWillNotDraw(false);setPadding(dp(12),dp(7),dp(12),dp(19));}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(), h=getHeight();
            float tailH=dp(12), r=dp(16);
            path.reset();
            float x1=dp(1), x2=w-dp(1), y1=dp(1), y2=h-tailH-dp(1);
            float tail=w*.70f;
            // Single closed path: bottom border stops at the tail, not a separate oval/triangle.
            path.moveTo(x1+r,y1);
            path.lineTo(x2-r,y1);
            path.quadTo(x2,y1,x2,y1+r);
            path.lineTo(x2,y2-r);
            path.quadTo(x2,y2,x2-r,y2);
            path.lineTo(tail+dp(11),y2);
            path.lineTo(tail-dp(3),h-dp(1));
            path.lineTo(tail-dp(12),y2);
            path.lineTo(x1+r,y2);
            path.quadTo(x1,y2,x1,y2-r);
            path.lineTo(x1,y1+r);
            path.quadTo(x1,y1,x1+r,y1);
            path.close();
            fill.setColor(bg==0xff1e2025?0xff514a49:0xfffffdf8);
            fill.setStyle(Paint.Style.FILL);
            stroke.setColor(fg);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(dp(1));
            canvas.drawPath(path,fill);
            canvas.drawPath(path,stroke);
        }
    }
    private String nextPetReply(){
        SharedPreferences petPrefs=ChteicoStore.prefs(this);
        long lastActive=petPrefs.getLong("last_active",0);
        int inactiveDays=Math.max(1,Math.min(36500,petPrefs.getInt("inactive_days",3)));
        boolean inactive=lastActive>0 && System.currentTimeMillis()-lastActive>=inactiveDays*86400000L;
        boolean achieved=petPrefs.getInt("finished_books_count",0)>0
            || petPrefs.getInt("finished_audio_count",0)>0
            || petPrefs.getInt("best_streak",0)>=7;
        ChteicoPhrases.Reply reply=ChteicoPhrases.next(
            ChteicoStore.stage(this),ChteicoStore.branch(this),inactive,achieved,
            petPrefs.getString("bubble_seen",""),petPrefs.getInt("bubble_last_id",-1),phraseRandom);
        if(reply==null)return null;
        petPrefs.edit().putString("bubble_seen",reply.seen).putInt("bubble_last_id",reply.id).apply();
        return reply.text;
    }
    private void showSpeech(String message,long durationMs){
        if(message==null||speechBubble==null||speechContainer==null)return;
        if(pendingSpeechHide!=null)ui.removeCallbacks(pendingSpeechHide);
        speechBubble.setText(message);
        speechContainer.setVisibility(View.VISIBLE);
        speechContainer.setAlpha(0f);
        speechContainer.animate().cancel();speechContainer.animate().alpha(1f).setDuration(180).start();
        final SpeechBubbleLayout current=speechContainer;
        pendingSpeechHide=()->{
            current.animate().alpha(0f).setDuration(180).withEndAction(()->current.setVisibility(View.INVISIBLE)).start();
            pendingSpeechHide=null;
        };
        ui.postDelayed(pendingSpeechHide,durationMs);
    }
    @Override protected void onDestroy(){if(pendingSpeechHide!=null)ui.removeCallbacks(pendingSpeechHide);super.onDestroy();}
    private String progressText(int stage){
        if(stage==ChteicoStore.ADULT)return "С вами настоящий книжный друг";
        return "До следующей эволюции: "+ChteicoStore.progress(this)+"%";
    }
    private void addStatsCard(){
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
    }
    private void rewardRow(LinearLayout host,int iconRes,String title){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(10),dp(10),dp(10));
        row.setBackground(shape(bg==0xff1e2025?0xff444349:0xfff0e6dc,14));
        ImageView icon=new ImageView(this);icon.setImageResource(iconRes);icon.setColorFilter(accent);row.addView(icon,new LinearLayout.LayoutParams(dp(22),dp(22)));
        TextView label=text(title,14,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.leftMargin=dp(10);row.addView(label,lp);
        host.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private void addRewardsCard(){
        boolean previewAll=ChteicoStore.developerUnlocked(this) && p.getBoolean("dev_show_all_rewards",false);
        LinearLayout rewards=card();rewards.addView(text(previewAll?"Награды (предпросмотр)":"Награды",18,true));gap(rewards,8);
        if(previewAll){
            TextView note=text("Тестовый показ всех наград — без изменения статистики.",12,false);
            rewards.addView(note);gap(rewards,8);
        }
        boolean any=false;
        if(previewAll || p.getInt("finished_books_count",0)>0){rewardRow(rewards,R.drawable.ic_reward_book,"Первая книга");gap(rewards,6);any=true;}
        if(previewAll || p.getInt("finished_audio_count",0)>0){rewardRow(rewards,R.drawable.ic_reward_audio,"Первая аудиокнига");gap(rewards,6);any=true;}
        if(previewAll || p.getInt("best_streak",0)>=7){rewardRow(rewards,R.drawable.ic_reward_streak,"Серия 7 дней");gap(rewards,6);any=true;}
        if(previewAll || p.getInt("finished_books_count",0)>=10){rewardRow(rewards,R.drawable.ic_reward_books10,"10 прочитанных книг");gap(rewards,6);any=true;}
        if(previewAll || p.getInt("finished_audio_count",0)>=10){rewardRow(rewards,R.drawable.ic_reward_audio10,"10 аудиокниг");gap(rewards,6);any=true;}
        if(previewAll || p.getInt("best_streak",0)>=30){rewardRow(rewards,R.drawable.ic_reward_streak30,"30 дней подряд");any=true;}
        if(!any){
            TextView none=text("Новые награды будут появляться по мере чтения и прослушивания аудиокниг.",14,false);
            none.setTextColor(bg==0xff1e2025?0xffd5cec8:0xff7d6d60);rewards.addView(none);
        }
    }
    private void showDisabledSummary(){
        LinearLayout hero=card();hero.setGravity(Gravity.CENTER_HORIZONTAL);
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_stats_nav);icon.setColorFilter(accent);
        hero.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));
        gap(hero,10);
        TextView title=text("Статистика и награды",21,true);title.setGravity(Gravity.CENTER);hero.addView(title);
        gap(hero,6);
        TextView sub=text("Чтейцо сейчас отключено. Здесь сохраняются ваши достижения, а включить спутника можно в настройках.",14,false);
        sub.setGravity(Gravity.CENTER);hero.addView(sub);
        gap(hero,12);
        hero.addView(button("Открыть настройки",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("chteico_tab",true))));
        addStatsCard();
        addRewardsCard();
    }
    private void show(){
        if(pendingSpeechHide!=null){ui.removeCallbacks(pendingSpeechHide);pendingSpeechHide=null;}
        if(speechContainer!=null)speechContainer.animate().cancel();
        speechContainer=null;speechBubble=null;
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
        if(!ChteicoStore.enabled(this)){showDisabledSummary();return;}
        int stage=ChteicoStore.stage(this);
        LinearLayout hero=card();hero.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout nameRow=new LinearLayout(this);nameRow.setGravity(Gravity.CENTER);
        TextView name=text(ChteicoStore.petName(this),23,true);nameRow.addView(name);
        if(ChteicoStore.canName(this)){
            TextView edit=text("  ✎",21,false);edit.setContentDescription("Назвать питомца");edit.setOnClickListener(v->askName());nameRow.addView(edit);
            name.setOnClickListener(v->askName());
        }
        hero.addView(nameRow);gap(hero,8);
        FrameLayout petFrame=new FrameLayout(this);
        // Keep the original 248x247dp pet; reclaim unused speech space above it.
        hero.addView(petFrame,new LinearLayout.LayoutParams(-1,dp(260)));
        FrameLayout.LayoutParams spriteLayout=new FrameLayout.LayoutParams(dp(248),dp(247),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        pet=new ChteicoPetView(this);petFrame.addView(pet,spriteLayout);
        // Draw rays over the whole card width; restrict taps to the pet itself.
        TapWaves waves=new TapWaves();petFrame.addView(waves,new FrameLayout.LayoutParams(-1,-1));
        View petTapTarget=new View(this);
        petFrame.addView(petTapTarget,new FrameLayout.LayoutParams(dp(248),dp(247),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL));
        petTapTarget.setOnClickListener(v->{
            waves.burst();
            if(ChteicoStore.stage(this)==ChteicoStore.EGG){
                boolean hatched=ChteicoStore.petTapped(this);
                if(progressBar!=null)progressBar.setProgress(ChteicoStore.progress(this));
                if(progressLabel!=null)progressLabel.setText(progressText(ChteicoStore.stage(this)));
                pet.refresh();
                if(hatched)ui.postDelayed(this::show,520);
            }else{
                showSpeech(nextPetReply(),4200);
            }
        });
        if(stage!=ChteicoStore.EGG){
            speechContainer=new SpeechBubbleLayout();
            speechBubble=text("",13,false);speechBubble.setGravity(Gravity.CENTER);speechBubble.setMaxLines(4);
            speechContainer.addView(speechBubble,new FrameLayout.LayoutParams(-1,-2));
            FrameLayout.LayoutParams bubbleLayout=new FrameLayout.LayoutParams(dp(235),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            bubbleLayout.topMargin=0;
            petFrame.addView(speechContainer,bubbleLayout);
            speechContainer.setVisibility(View.INVISIBLE);
            String intro=ChteicoStore.consumeIntroduction(this);
            if(intro!=null){
                showSpeech(intro,7000);
            }
        }
        gap(hero,12);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progressBar.setMax(100);progressBar.setProgress(ChteicoStore.progress(this));
        if(android.os.Build.VERSION.SDK_INT>=21)progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        hero.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(9)));gap(hero,7);
        progressLabel=text(progressText(stage),13,false);progressLabel.setGravity(Gravity.CENTER);hero.addView(progressLabel);
        addStatsCard();
        addRewardsCard();
    }
    private void statistics(){new SwipeDialogs.Builder(this).setTitle("Книжная статистика")
        .setMessage("Книг открыто: "+p.getInt("opened_books_count",0)+"\nПрочитано: "+p.getInt("finished_books_count",0)
        +"\nАудиокниг открыто: "+p.getInt("opened_audio_count",0)+"\nПрослушано полностью: "+p.getInt("finished_audio_count",0)
        +"\nОбычное чтение: "+mins("seconds_classic")+" мин\nСкорочтение и Курсор: "+mins("seconds_speed")
        +" мин\nАудио: "+mins("seconds_audio")+" мин\nСерия дней: "+p.getInt("streak",0)
        +"\nЛучшая серия: "+p.getInt("best_streak",0)
        +"\nСессий: "+p.getInt("sessions",0)+"\nПройдено слов: "+p.getLong("words_advanced",0))
        .setPositiveButton("Понятно",null).show();}
}
