package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.content.SharedPreferences;
import android.graphics.drawable.Animatable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.Locale;
import java.util.Random;

/** Fishing uses Android XML graphics only; Java controls states, positioning and animation. */
public final class ChteicoFishingView extends FrameLayout {
    private static final int READY=0,CASTING=1,FLOATING=2,REELING=3,BITE=4,CATCHING=5,RESULT=6,FLYING=7;
    private static final int[] FISH_ART={R.drawable.ic_fishing_fish_common,R.drawable.ic_fishing_fish_uncommon,
            R.drawable.ic_fishing_fish_rare,R.drawable.ic_fishing_fish_legendary,R.drawable.ic_fishing_fish_mythical};
    private static final String[] RARITY_NAMES={"Обычная","Необычная","Редкая","Легендарная","Мифическая"};
    private static final int[] RARITY_COLORS={Color.WHITE,0xff65c96b,0xff55c8ef,0xfff49a45,0xffb478e7};
    private static final float[] SPEED={1f,1.15f,1.35f,1.60f,1.90f};
    private static final float[] FILL_TIME={1f,1.15f,1.35f,1.65f,2f};
    // Smaller visual target for more valuable fish, without altering the catch-zone collision rule.
    private static final float[] FISH_SCALE={1f,.90f,.80f,.70f,.60f};
    // Grade order: Super, Excellent, Good, Fair, Failed. One quality modifier, not a double bonus.
    private static final double[] QUALITY_RARITY_MULTIPLIER={1.80,1.40,1.00,.70,.40};
    private static final int BOOT_BROWN=0xff8b5a2b;
    private static final int[] BITE_TIMES={6000,5000,4000,3000,2000};
    private static final String[] ROD_NAMES={"Тренировочная удочка","Удочка новичка","Хорошая удочка","Удочка мастера","Удочка прочитателя"};
    private static final int[] ROD_COST={0,180,800,3000,8000};
    private static final float[] CATCH_ZONE_WIDTH={1f,.90f,.80f,.70f,.60f};
    private static final int[] BITE_DRAWABLES={R.drawable.ic_fishing_bite_6,R.drawable.ic_fishing_bite_5,
            R.drawable.ic_fishing_bite_4,R.drawable.ic_fishing_bite_3,R.drawable.ic_fishing_bite_2};
    private static final long QUICK_TAP_MS=220L;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private final Runnable exit;
    private final TextView hud,goldCount,catchHeader,status,feedback,biteMessage,resultName,resultRarity,resultMass,resultRewards,resultContinue;
    private final ImageView bobber,fish,zone,castMark,castCursor,ring,wave1,wave2,resultFish,catchShimmer,catchShimmerTrail,resultShimmer,resultShimmerTrail;
    private final FrameLayout meter,catchPanel,track;
    // The result panel is a LinearLayout in XML; only common View methods are needed.
    private final View resultPanel;
    private final FrameLayout catchProgress;
    private final View catchProgressFill,gameToolbar;
    private final int accent,ink,themeBg,surface;
    private final ImageView shopIcon,pond,baitIcon,goldIcon;
    private int phase=READY,rarity=0,castGrade=2;
    private boolean held=false,paused=false,stopped=false,castWaitingSecondTap=false,ignoreUp=false,biteNeedsRelease=false,hookedBoot=false,fishHeadingUp=true,shimmering=false;
    private float cast=0f,castDirection=1f,distance=0f,depth=0f,zoneY=.62f,fishY=.45f;
    private float fishVelocity=.27f,fishSteer=0f,catchAmount=.28f,bobberX=.5f,flightProgress=0f,flightDuration=.5f;
    private long lastMs=0,castPressTime=0,biteStartedMs=0,feedbackUntil=0;
    private String outcome="",feedbackText="";
    private int feedbackColor=0xff16a34a,displayedBait=Integer.MIN_VALUE,displayedGold=Integer.MIN_VALUE;
    private final Runnable clock=new Runnable(){@Override public void run(){
        if(stopped||paused||!isAttachedToWindow())return;
        long now=SystemClock.uptimeMillis();
        float dt=lastMs==0?0f:Math.min(.055f,Math.max(0f,(now-lastMs)/1000f));lastMs=now;
        update(dt,now);render();handler.postDelayed(this,16);
    }};
    public ChteicoFishingView(Context context,Runnable onExit,int petCardColor){
        super(context);exit=onExit;
        LayoutInflater.from(context).inflate(R.layout.view_chteico_fishing,this,true);
        hud=findViewById(R.id.fishing_hud);goldCount=findViewById(R.id.fishing_gold_count);
        catchHeader=findViewById(R.id.fishing_catch_header);status=findViewById(R.id.fishing_status);
        bobber=findViewById(R.id.fishing_bobber);fish=findViewById(R.id.fishing_fish);zone=findViewById(R.id.fishing_zone);
        meter=findViewById(R.id.fishing_cast_meter);castMark=findViewById(R.id.fishing_cast_optimum);
        castCursor=findViewById(R.id.fishing_cast_indicator);catchPanel=findViewById(R.id.fishing_catch_panel);
        track=findViewById(R.id.fishing_track);catchProgress=findViewById(R.id.fishing_catch_progress);
        catchProgressFill=findViewById(R.id.fishing_catch_progress_fill);gameToolbar=findViewById(R.id.fishing_toolbar);
        feedback=findViewById(R.id.fishing_cast_feedback);biteMessage=findViewById(R.id.fishing_bite_message);
        ring=findViewById(R.id.fishing_bite_ring);wave1=findViewById(R.id.fishing_wave_1);wave2=findViewById(R.id.fishing_wave_2);
        resultPanel=findViewById(R.id.fishing_result_panel);resultFish=findViewById(R.id.fishing_result_fish);
        resultName=findViewById(R.id.fishing_result_title);resultRarity=findViewById(R.id.fishing_result_rarity);
        resultMass=findViewById(R.id.fishing_result_mass);resultRewards=findViewById(R.id.fishing_result_rewards);
        resultContinue=findViewById(R.id.fishing_result_continue);
        catchShimmer=findViewById(R.id.fishing_catch_shimmer);
        catchShimmerTrail=findViewById(R.id.fishing_catch_shimmer_trail);
        resultShimmer=findViewById(R.id.fishing_result_shimmer);
        resultShimmerTrail=findViewById(R.id.fishing_result_shimmer_trail);
        shopIcon=findViewById(R.id.fishing_shop);pond=findViewById(R.id.fishing_pond);
        baitIcon=findViewById(R.id.fishing_bait_icon);goldIcon=findViewById(R.id.fishing_gold_icon);
        resultPanel.setClickable(true); // Consume every reward tap except the dedicated button.
        resultPanel.bringToFront();
        resultContinue.setOnClickListener(v->closeResult());
        SharedPreferences theme=context.getSharedPreferences("reader",Context.MODE_PRIVATE);
        // Exactly the same solid surface as ChteicoActivity.card(): no lake/card seam.
        themeBg=petCardColor;
        ink=theme.getInt("fg",isDark(themeBg)?Color.WHITE:0xff30221e);
        accent=ReaderButtonColors.accent(context,0xff16a34a);
        surface=mix(themeBg,ink,isDark(themeBg)?.09f:.045f);
        setBackgroundColor(themeBg);
        pond.setBackgroundColor(themeBg);
        pond.setColorFilter(ink);
        // The RIGHT-HAND fishing panel is fully opaque, but never covers the lake.
        catchPanel.setBackground(panelBackground(themeBg,accent,dp(14)));
        gameToolbar.setBackground(panelBackground(surface,ink,dp(13)));
        catchHeader.setBackground(panelBackground(surface,0,dp(12)));
        catchHeader.setTextColor(ink);
        catchProgress.setBackground(panelBackground(surface,ink,dp(7)));
        catchProgressFill.setBackground(panelBackground(accent,0,dp(5)));
        resultPanel.setBackground(panelBackground(themeBg,accent,0));
        status.setBackground(panelBackground(surface,accent,dp(15)));
        resultContinue.setBackground(panelBackground(surface,accent,dp(12)));
        resultContinue.setTextColor(accent);
        shopIcon.setBackground(panelBackground(surface,ink,dp(12)));
        shopIcon.setColorFilter(ink);baitIcon.setColorFilter(ink);goldIcon.setColorFilter(ink);
        findViewById(R.id.fishing_close).setBackground(panelBackground(surface,ink,dp(12)));
        ((TextView)findViewById(R.id.fishing_close)).setTextColor(ink);
        meter.setBackground(panelBackground(surface,ink,dp(8)));
        track.setBackground(panelBackground(mix(themeBg,ink,isDark(themeBg)?.30f:.06f),ink,dp(12)));
        hud.setTextColor(ink);goldCount.setTextColor(ink);status.setTextColor(ink);feedback.setTextColor(ink);biteMessage.setTextColor(ink);
        resultName.setTextColor(ink);resultMass.setTextColor(ink);resultRewards.setTextColor(ink);
        castMark.setColorFilter(accent);zone.setColorFilter(accent);ring.setColorFilter(accent);
        catchShimmer.setColorFilter(accent);catchShimmerTrail.setColorFilter(accent);
        resultShimmer.setColorFilter(accent);resultShimmerTrail.setColorFilter(accent);
        // The vertical fill uses the live accent as an opaque bottom-up drawable.
        setClickable(true);setFocusable(true);
        setContentDescription("Рыбалка. Касайтесь игрового поля для заброса, поклёвки и вываживания");
        render();
    }
    private static boolean isDark(int color){return (.299*Color.red(color)+.587*Color.green(color)+.114*Color.blue(color))<135;}
    private static int mix(int base,int target,float amount){
        return Color.rgb(Math.round(Color.red(base)*(1-amount)+Color.red(target)*amount),
            Math.round(Color.green(base)*(1-amount)+Color.green(target)*amount),
            Math.round(Color.blue(base)*(1-amount)+Color.blue(target)*amount));
    }
    private GradientDrawable panelBackground(int fill,int outline,float radius){
        GradientDrawable g=new GradientDrawable();g.setColor(fill);
        if(outline!=0)g.setStroke(Math.max(1,Math.round(dp(1.4f))),outline);
        g.setCornerRadius(radius);return g;
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();resumeClock();}
    @Override protected void onDetachedFromWindow(){stopClock();super.onDetachedFromWindow();}
    public void pauseClock(){paused=true;held=false;castWaitingSecondTap=false;
        if(phase==CASTING||phase==FLYING)phase=READY;
        else if(phase==REELING||phase==BITE){phase=FLOATING;stopRing();}
        handler.removeCallbacks(clock);lastMs=0;render();
    }
    public void resumeClock(){if(stopped)return;paused=false;lastMs=0;handler.removeCallbacks(clock);handler.post(clock);}
    public void stopClock(){stopped=true;paused=true;held=false;stopRing();handler.removeCallbacks(clock);
        wave1.animate().cancel();wave2.animate().cancel();}
    private void stopRing(){if(ring.getDrawable() instanceof Animatable)((Animatable)ring.getDrawable()).stop();}
    private void update(float dt,long now){
        if(phase==CASTING){
            cast+=castDirection*dt*.83f;
            if(cast>=1f){cast=2f-cast;castDirection=-1f;}
            if(cast<=0f){cast=-cast;castDirection=1f;}
        }
        if(phase==FLYING){
            flightProgress=Math.min(1f,flightProgress+dt/flightDuration);
            if(flightProgress>=1f){phase=FLOATING;vibrate(40);post(this::playWaves);}
        }
        if(phase==REELING&&held){
            depth=Math.min(1f,depth+dt*.20f);
            // Rate per second, independent from display refresh rate; never rolls while idle.
            double p=(.40+.52*distance)*dt;
            if(random.nextDouble()<1-Math.exp(-p)){startBite(now);return;}
            if(depth>=.985f){finish("Поплавок вернулся. Поклёвки не было.");return;}
        }
        if(phase==BITE&&now-biteStartedMs>=(hookedBoot?7000:BITE_TIMES[rarity])){
            stopRing();finish("Поклёвка упущена!");return;
        }
        if(phase==CATCHING){
            fishSteer-=dt;
            if(fishSteer<=0){fishSteer=.27f+random.nextFloat()*.43f;
                fishVelocity=(random.nextFloat()-.5f)*1.18f*(hookedBoot?.65f:SPEED[rarity])*(shimmering?1.2f:1f);}
            float endgameSlow=catchFillFactor(catchAmount);
            // A better rod tires fish as the progress approaches completion.
            // Training/novice rods and the old cover receive no movement bonus.
            float movementFactor=!hookedBoot&&ChteicoStore.fishingRodEquipped(getContext())>=2
                ?endgameSlow:1f;
            fishY+=fishVelocity*dt*movementFactor;
            if(fishY<.07f){fishY=.07f;fishVelocity=Math.abs(fishVelocity);}
            if(fishY>.91f){fishY=.91f;fishVelocity=-Math.abs(fishVelocity);}
            // Head points in the actual travel direction (positive Y = down).
            updateFishHeading(fishVelocity<0f);
            zoneY+=dt*(held?-.53f:.34f);
            zoneY=Math.max(0f,Math.min(.75f,zoneY));
            // Hit-testing must match the visibly smaller zone, not merely resize its drawable.
            float trackHeight=Math.max(dp(140),track.getHeight());
            float fishCenter=(trackHeight-fish.getHeight())*fishY+fish.getHeight()/2f;
            float zoneCenter=(trackHeight-zone.getHeight())*zoneY/.75f+zone.getHeight()/2f;
            float zoneFactor=(hookedBoot?1.2f:CATCH_ZONE_WIDTH[rarity])*(shimmering?.85f:1f);
            boolean inside=Math.abs(fishCenter-zoneCenter)<=zone.getHeight()*zoneFactor/2f;
            float slow=endgameSlow;
            float fillTime=hookedBoot?.70f:FILL_TIME[rarity];
            float shimmerFactor=shimmering?.75f:1f;
            catchAmount=Math.max(0,Math.min(1,catchAmount+dt*(inside?.26f*slow*shimmerFactor/fillTime:-.12f)));
            if(catchAmount>=1f){if(hookedBoot)showBootResult();else successfulCatch();return;}
            if(catchAmount<=0f){finish(hookedBoot?"Находка сорвалась!":"Рыба сорвалась!");}
        }
    }
    private static float catchFillFactor(float progress){
        return progress>=.90f?.70f:progress>=.70f?.85f:1f;
    }
    private void startBite(long now){
        // An old worn book cover is possible only after a failed cast and gives no rewards.
        hookedBoot=castGrade==4&&random.nextFloat()<.25f;
        rarity=hookedBoot?0:chooseRarity();
        shimmering=!hookedBoot&&(ChteicoStore.developerUnlocked(getContext())||random.nextFloat()<.01f);
        phase=BITE;held=false;biteNeedsRelease=true;biteStartedMs=now;
        biteMessage.setText(hookedBoot?"Что-то зацепилось!":"Рыба клюнула!");
        ring.setImageResource(hookedBoot?R.drawable.ic_fishing_bite_7:BITE_DRAWABLES[rarity]);ring.setColorFilter(accent);
        ring.setVisibility(VISIBLE);
        if(ring.getDrawable() instanceof Animatable)((Animatable)ring.getDrawable()).start();
        vibrate(75);playWaves();
    }
    private int chooseRarity(){
        // Baseline relative weights: common 55, uncommon 28, rare 12, legendary 4, mythical 1.
        // Apply cast quality to the three rarest groups, then normalize by total weight.
        double quality=QUALITY_RARITY_MULTIPLIER[Math.max(0,Math.min(4,castGrade))];
        double[] weights={55d,28d,12d*quality,4d*quality,1d*quality};
        int maxRarity=ChteicoStore.fishingRodEquipped(getContext());
        for(int i=maxRarity+1;i<weights.length;i++)weights[i]=0d;
        double total=0d;for(double w:weights)total+=w;
        double roll=random.nextDouble()*total;
        for(int i=0;i<weights.length;i++){roll-=weights[i];if(roll<0)return i;}
        return 4;
    }
    private float fishAngle(boolean headUp){
        // The black fish silhouette is always the same vertical vector during catching.
        return hookedBoot?0f:(headUp?0f:180f);
    }
    private void updateFishHeading(boolean headUp){
        if(fishHeadingUp==headUp)return;
        fishHeadingUp=headUp;
        fish.animate().cancel();
        fish.animate().rotation(fishAngle(headUp)).setDuration(145L).start();
    }
    private void beginCatch(){
        stopRing();ring.setVisibility(GONE);
        phase=CATCHING;held=true;
        zoneY=.58f;fishY=.52f;fishSteer=.25f;catchAmount=.28f;
        fishVelocity=random.nextBoolean()?-.27f:.27f;
        // Keep the real item hidden until the reward: old covers use the same fish silhouette.
        fish.setImageResource(R.drawable.ic_fishing_catch_silhouette);
        float targetScale=hookedBoot?1.1f:FISH_SCALE[rarity];
        fish.setScaleX(targetScale);fish.setScaleY(targetScale);
        float zoneScale=(hookedBoot?1.2f:CATCH_ZONE_WIDTH[rarity])*(shimmering?.85f:1f);
        zone.setScaleX(zoneScale);zone.setScaleY(zoneScale);
        catchShimmer.setVisibility(shimmering?VISIBLE:GONE);
        catchShimmerTrail.setVisibility(shimmering?VISIBLE:GONE);
        fish.animate().cancel();
        fishHeadingUp=fishVelocity<0f;
        fish.setRotation(fishAngle(fishHeadingUp));
    }
    private void showBootResult(){
        ChteicoStore.recordFishingOldCover(getContext());
        shimmering=false;
        resultName.setText("Старая обложка");resultName.setTextColor(BOOT_BROWN);
        resultRarity.setText("Бесполезная находка");resultRarity.setTextColor(BOOT_BROWN);
        resultMass.setText("Вместо рыбы — старая обложка");resultMass.setTextColor(BOOT_BROWN);
        resultRewards.setText("0 XP   ·   0 золота");resultRewards.setTextColor(BOOT_BROWN);
        resultShimmer.setVisibility(GONE);resultShimmerTrail.setVisibility(GONE);
        resultFish.setImageResource(R.drawable.ic_fishing_old_cover);
        resultFish.setRotation(0f);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)resultFish.getLayoutParams();
        lp.width=(int)dp(142f);lp.height=(int)dp(84f);resultFish.setLayoutParams(lp);
        finish("Старая обложка найдена!");
    }
    private void successfulCatch(){
        float[] min={.2f,.5f,1f,2f,3f},max={2f,3.5f,5f,8f,12f};
        float mass=min[rarity]+random.nextFloat()*(max[rarity]-min[rarity]);
        int[] mul2={2,3,5,8,14};
        int xp=Math.round(mass*20*mul2[rarity]/2f);
        int gold=Math.max(1,Math.round(mass*5*mul2[rarity]/2f*(shimmering?3f:1f)));
        ChteicoStore.recordFishingCatch(getContext(),Math.round(mass*1000),xp,gold,rarity,shimmering);
        resultName.setText(shimmering?"Мерцающая рыба поймана!":"Рыба поймана!");resultName.setTextColor(ink);
        resultMass.setTextColor(ink);resultRewards.setTextColor(ink);
        resultRarity.setText(shimmering?"Мерцающая · "+RARITY_NAMES[rarity]:RARITY_NAMES[rarity]);
        resultRarity.setTextColor(rarity==0?ink:RARITY_COLORS[rarity]);
        resultMass.setText(String.format(Locale.forLanguageTag("ru"),"Масса: %.2f кг",mass));
        resultRewards.setText("+"+xp+" XP   ·   +"+gold+" золота");
        resultFish.setImageResource(FISH_ART[rarity]);
        resultShimmer.setVisibility(shimmering?VISIBLE:GONE);
        resultShimmerTrail.setVisibility(shimmering?VISIBLE:GONE);
        resultFish.setRotation(rarity==0?-90f:0f);
        // sqrt scaling keeps larger specimens visible but limits to the card width.
        float scale=Math.max(.85f,Math.min(1.75f,.85f+.22f*(float)Math.sqrt(mass)));
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)resultFish.getLayoutParams();
        lp.width=(int)dp(92f*scale);lp.height=(int)dp(72f*scale);resultFish.setLayoutParams(lp);
        finish("Улов сохранён!");
    }
    private void finish(String message){
        phase=RESULT;held=false;castWaitingSecondTap=false;outcome=message;
        boolean success="Улов сохранён!".equals(message)||"Старая обложка найдена!".equals(message);
        if(!success){
            resultName.setText("Попытка завершена");resultName.setTextColor(ink);
            resultRarity.setText("");resultMass.setText(message);resultMass.setTextColor(ink);
            resultRewards.setText("0 XP   ·   0 золота");resultRewards.setTextColor(ink);
            resultFish.setVisibility(GONE);resultShimmer.setVisibility(GONE);resultShimmerTrail.setVisibility(GONE);
        }else resultFish.setVisibility(VISIBLE);
    }
    private void closeResult(){
        if(phase!=RESULT)return;
        phase=READY;outcome="";held=false;ignoreUp=false;
        resultShimmer.setVisibility(GONE);resultShimmerTrail.setVisibility(GONE);render();
    }
    private void startCast(float x){
        if(ChteicoStore.fishingBait(getContext())<1){
            new SwipeDialogs.Builder(getContext()).setTitle("Нет наживок")
              .setMessage("Читайте главы и страницы манги, чтобы получать наживки, или покупайте их за золото.")
              .setPositiveButton("Купить",(d,w)->showShop()).setNegativeButton("Закрыть",null).show();return;
        }
        bobberX=Math.max(.06f,Math.min(.94f,x/Math.max(1f,getWidth())));
        cast=0f;castDirection=1f;phase=CASTING;held=true;castWaitingSecondTap=false;hookedBoot=false;
        castPressTime=SystemClock.uptimeMillis();feedbackUntil=0;outcome="";shimmering=false;
    }
    private void finishCast(){
        if(!ChteicoStore.useFishingBait(getContext())){phase=READY;held=false;return;}
        float error=Math.abs(cast-.80f);
        distance=Math.max(.08f,Math.min(1f,1f-error/.80f));
        depth=.09f+(1-distance)*.73f;phase=FLYING;held=false;castWaitingSecondTap=false;
        flightProgress=0f;flightDuration=.35f+.30f*distance;
        if(error<=.06f){castGrade=0;feedbackText="Супер!";feedbackColor=0xff16a34a;}
        else if(error<=.12f){castGrade=1;feedbackText="Отлично!";feedbackColor=0xff3f9b50;}
        else if(error<=.20f){castGrade=2;feedbackText="Хорошо!";feedbackColor=0xffaa8600;}
        else if(error<=.35f){castGrade=3;feedbackText="Неплохо";feedbackColor=0xffd47c1c;}
        else {castGrade=4;feedbackText="Неудачно";feedbackColor=0xffc53030;}
        feedbackUntil=SystemClock.uptimeMillis()+1400;
        vibrate(20); // Cast confirmation; stronger vibration and ripples follow touchdown.
    }
    private void vibrate(int duration){
        try{Vibrator v=(Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if(v==null||!v.hasVibrator())return;
            if(Build.VERSION.SDK_INT>=26)v.vibrate(VibrationEffect.createOneShot(duration,VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(duration);
        }catch(Exception ignored){}
    }
    private void playWaves(){
        final ImageView[] waves={wave1,wave2};
        for(int i=0;i<waves.length;i++){
            ImageView w=waves[i];w.animate().cancel();
            w.setX(bobber.getX()+bobber.getWidth()/2f-w.getWidth()/2f);
            w.setY(bobber.getY()+bobber.getHeight()/2f-w.getHeight()/2f);
            w.setScaleX(.65f);w.setScaleY(.65f);w.setAlpha(.9f);w.setVisibility(VISIBLE);
            w.animate().scaleX(2.1f).scaleY(2.1f).alpha(0f).setStartDelay(i*180L)
                .setDuration(660).withEndAction(()->w.setVisibility(GONE)).start();
        }
    }
    private void showShop(){
        String[] items={"Магазин удочек", "1 наживка — 30 золота","10 наживок — 270 золота","50 наживок — 1200 золота"};
        final int[] amounts={1,10,50};
        new SwipeDialogs.Builder(getContext()).setTitle("Купить наживки · Золото: "+ChteicoStore.fishingGold(getContext()))
          .setItems(items,(d,which)->{
            if(which==0){showRods();return;}
            if(!ChteicoStore.buyFishingBait(getContext(),amounts[which-1]))
                android.widget.Toast.makeText(getContext(),"Недостаточно золота",android.widget.Toast.LENGTH_SHORT).show();
            render();
          }).setNegativeButton("Отмена",null).show();
    }
    private void showRods(){
        int gold=ChteicoStore.fishingGold(getContext());
        int equipped=ChteicoStore.fishingRodEquipped(getContext());
        int[] art={R.drawable.ic_fishing_rod_training,R.drawable.ic_fishing_rod_novice,
            R.drawable.ic_fishing_rod_good,R.drawable.ic_fishing_rod_master,R.drawable.ic_fishing_rod_spinning};
        android.widget.LinearLayout list=new android.widget.LinearLayout(getContext());
        list.setOrientation(android.widget.LinearLayout.VERTICAL);list.setPadding((int)dp(12),(int)dp(8),(int)dp(12),(int)dp(8));
        final android.app.AlertDialog[] dialog=new android.app.AlertDialog[1];
        for(int i=0;i<ROD_NAMES.length;i++){
            final int rod=i;
            boolean owned=ChteicoStore.hasFishingRod(getContext(),rod);
            android.widget.LinearLayout line=new android.widget.LinearLayout(getContext());
            line.setGravity(android.view.Gravity.CENTER_VERTICAL);
            line.setPadding((int)dp(10),(int)dp(10),(int)dp(10),(int)dp(10));
            line.setBackground(panelBackground(surface,i==equipped?accent:ink,dp(12)));
            android.widget.ImageView icon=new android.widget.ImageView(getContext());icon.setImageResource(art[i]);icon.setColorFilter(ink);
            line.addView(icon,new android.widget.LinearLayout.LayoutParams((int)dp(40),(int)dp(40)));
            android.widget.TextView label=new android.widget.TextView(getContext());
            label.setText(ROD_NAMES[i]+"\n"+RARITY_NAMES[i]+" · "+
                (i==equipped?"Используется":owned?"Куплена":ROD_COST[i]+" золота"));
            label.setTextColor(ink);label.setTextSize(14);label.setPadding((int)dp(12),0,0,0);
            line.addView(label,new android.widget.LinearLayout.LayoutParams(0,-2,1));
            line.setOnClickListener(v->{
                if(dialog[0]!=null)dialog[0].dismiss();
                if(ChteicoStore.hasFishingRod(getContext(),rod)){
                    ChteicoStore.equipFishingRod(getContext(),rod);
                    android.widget.Toast.makeText(getContext(),"Выбрана: "+ROD_NAMES[rod],android.widget.Toast.LENGTH_SHORT).show();
                    showRods();
                }else{
                    new SwipeDialogs.Builder(getContext()).setTitle("Купить "+ROD_NAMES[rod]+"?")
                        .setMessage("Стоимость: "+ROD_COST[rod]+" золота. Удочка останется навсегда.")
                        .setPositiveButton("Купить",(d,w)->{
                            if(!ChteicoStore.purchaseFishingRod(getContext(),rod))
                                android.widget.Toast.makeText(getContext(),"Недостаточно золота",android.widget.Toast.LENGTH_SHORT).show();
                            else android.widget.Toast.makeText(getContext(),"Удочка куплена и выбрана",android.widget.Toast.LENGTH_SHORT).show();
                            render();showRods();
                        }).setNegativeButton("Отмена",(d,w)->showRods()).show();
                }
            });
            android.widget.LinearLayout.LayoutParams rowParams=new android.widget.LinearLayout.LayoutParams(-1,-2);
            rowParams.bottomMargin=(int)dp(7);list.addView(line,rowParams);
        }
        android.widget.ScrollView scroll=new android.widget.ScrollView(getContext());scroll.addView(list);
        dialog[0]=new SwipeDialogs.Builder(getContext()).setTitle("Удочки · Золото: "+gold)
            .setView(scroll).setNegativeButton("Закрыть",null).show();
    }
    private void updateStatus(String message){if(!message.contentEquals(status.getText()))status.setText(message);}
    private void renderBobber(){
        float minX=dp(8),maxX=Math.max(minX,getWidth()-bobber.getWidth()-dp(8));
        float targetX=Math.max(minX,Math.min(maxX,getWidth()*bobberX-bobber.getWidth()/2f));
        float max=Math.max(0,getHeight()-dp(168)),targetY=dp(67)+max*depth;
        if(phase==FLYING){
            float t=flightProgress;
            float startX=getWidth()/2f-bobber.getWidth()/2f,startY=getHeight()-dp(100);
            bobber.setX(startX+(targetX-startX)*t);
            bobber.setY(startY+(targetY-startY)*t-(float)Math.sin(Math.PI*t)*dp(38));
            bobber.setRotation((1f-t)*18f);
        }else{bobber.setX(targetX);bobber.setY(targetY);bobber.setRotation(0f);}
    }
    private void renderCatchSparkles(long now){
        // Ghosting trail is opposite fish movement in SCREEN coordinates (not fish rotation).
        float trailDirection=fishHeadingUp?1f:-1f;
        renderCatchSparkle(catchShimmer,now,0L,trailDirection,0f);
        renderCatchSparkle(catchShimmerTrail,now,550L,trailDirection,dp(8));
    }
    private void renderCatchSparkle(ImageView star,long now,long offset,float direction,float xOffset){
        float t=((now+offset)%1100L)/1100f;
        // Sine envelope has zero alpha on BOTH edges: seamless cyclic reappearance.
        float visibility=(float)Math.sin(Math.PI*t);
        star.setAlpha(Math.max(0f,visibility)*.88f);
        star.setScaleX(.68f+.24f*t);star.setScaleY(.68f+.24f*t);
        star.setRotation(0f); // Particles do not rotate together with the fish.
        star.setX(fish.getX()+(fish.getWidth()-star.getWidth())/2f+xOffset);
        star.setY(fish.getY()+(fish.getHeight()-star.getHeight())/2f+direction*dp(30)*t);
    }
    private void renderRewardSparkles(long now){
        renderRewardSparkle(resultShimmer,now,0L,0f);
        renderRewardSparkle(resultShimmerTrail,now,550L,dp(10));
    }
    private void renderRewardSparkle(ImageView star,long now,long offset,float xOffset){
        float t=((now+offset)%1100L)/1100f;
        star.setAlpha(Math.max(0f,(float)Math.sin(Math.PI*t))*.9f);
        star.setScaleX(.72f+.3f*t);star.setScaleY(.72f+.3f*t);
        star.setRotation(0f);
        star.setTranslationX(xOffset);
        star.setTranslationY(-dp(32)*t); // Reward shimmer always drifts upward.
    }
    /** A vertical 0..100 percent progress column growing from the bottom.
     *  The moving fish and the accent catch zone remain independent. */
    private void renderVerticalCatchProgress(){
        final int available=Math.max(0,catchProgress.getHeight()-Math.round(dp(4)));
        final int fillHeight=Math.round(available*Math.max(0f,Math.min(1f,catchAmount)));
        final FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)catchProgressFill.getLayoutParams();
        if(lp.height!=fillHeight){
            lp.height=fillHeight;
            catchProgressFill.setLayoutParams(lp);
        }
    }
    private void render(){
        int bait=ChteicoStore.fishingBait(getContext()),gold=ChteicoStore.fishingGold(getContext());
        if(bait!=displayedBait||gold!=displayedGold){displayedBait=bait;displayedGold=gold;
            hud.setText(String.valueOf(bait));goldCount.setText(String.valueOf(gold));}
        meter.setVisibility(phase==CASTING?VISIBLE:GONE);
        bobber.setVisibility(phase==FLYING||phase==FLOATING||phase==REELING||phase==BITE?VISIBLE:GONE);
        catchPanel.setVisibility(phase==CATCHING?VISIBLE:GONE);
        catchProgress.setVisibility(phase==CATCHING?VISIBLE:GONE);
        ring.setVisibility(phase==BITE?VISIBLE:GONE);
        biteMessage.setVisibility(phase==BITE?VISIBLE:GONE);
        boolean rewardVisible=phase==RESULT;
        resultPanel.setVisibility(rewardVisible?VISIBLE:GONE);
        // Tutorials are shown ONLY with the training rod. Other rods get uncluttered play.
        boolean trainingRod=ChteicoStore.fishingRodEquipped(getContext())==0;
        boolean showTutorial=trainingRod&&!rewardVisible&&phase!=CATCHING&&phase!=FLYING;
        status.setVisibility(showTutorial?VISIBLE:GONE);
        // Separate shimmer overlays are rendered AFTER fish positioning, above artwork.
        boolean catchGlitter=phase==CATCHING&&shimmering;
        catchShimmer.setVisibility(catchGlitter?VISIBLE:GONE);
        catchShimmerTrail.setVisibility(catchGlitter?VISIBLE:GONE);
        boolean rewardGlitter=rewardVisible&&shimmering;
        resultShimmer.setVisibility(rewardGlitter?VISIBLE:GONE);
        resultShimmerTrail.setVisibility(rewardGlitter?VISIBLE:GONE);
        if(phase==CASTING){
            int height=Math.max(1,meter.getHeight());
            castMark.setY(Math.max(0f,(1f-.80f)*height-castMark.getHeight()/2f));
            castCursor.setY(Math.max(0f,(1f-cast)*(height-castCursor.getHeight())));
            updateStatus(castWaitingSecondTap?"Второй тап — заброс":"Отпустите на 80% или сделайте два коротких тапа");
        }else if(phase==FLYING)updateStatus("Поплавок летит к воде...");
        else if(phase==FLOATING)updateStatus("Зажмите, чтобы подтягивать поплавок");
        else if(phase==REELING)updateStatus("Подтягивание · шанс поклёвки только при удерживании");
        else if(phase==BITE)updateStatus(hookedBoot?"Что-то клюнуло! Отпустите и быстро тапните!":"Рыба клюнула! Отпустите и быстро тапните!");
        else if(phase==CATCHING){
            float height=Math.max(dp(140),track.getHeight());
            zone.setY((height-zone.getHeight())*zoneY/.75f);
            fish.setY((height-fish.getHeight())*fishY);
            renderVerticalCatchProgress();
            updateStatus("Удерживайте рыбку внутри акцентной зоны");
        }else if(phase==RESULT)updateStatus(outcome+" · Нажмите «Продолжить»");
        else updateStatus("Нажмите для заброса. Можно удерживать или тапнуть дважды");
        if(phase==FLYING||phase==FLOATING||phase==REELING||phase==BITE)renderBobber();
        // Opaque narrow panel stays at the right; lake remains visible to its left.
        if(phase==CATCHING)catchPanel.setElevation(dp(1));
        if(catchGlitter)renderCatchSparkles(SystemClock.uptimeMillis());
        if(rewardGlitter)renderRewardSparkles(SystemClock.uptimeMillis());
        if(phase==BITE){
            float x=bobber.getX()+bobber.getWidth()/2f-ring.getWidth()/2f;
            ring.setX(Math.max(dp(6),Math.min(x,getWidth()-ring.getWidth()-dp(6))));
            ring.setY(Math.max(dp(70),bobber.getY()-dp(16)));
        }
        boolean showFeedback=feedbackUntil>SystemClock.uptimeMillis()&&phase!=CASTING;
        feedback.setVisibility(showFeedback?VISIBLE:GONE);
        if(showFeedback){feedback.setText(feedbackText);feedback.setTextColor(feedbackColor);}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){
        // Let only the result button handle its own clicks. Overlay consumes all other touches.
        return phase!=RESULT;
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            float x=e.getX(),y=e.getY();ignoreUp=false;
            if(phase==RESULT)return true; // Reward closes ONLY via the button.
            // A separate toolbar sits above the lake. Tapping its counters must NEVER cast.
            if(y<=dp(57)){
                ignoreUp=true;
                if(x>getWidth()-dp(49)){stopClock();if(exit!=null)exit.run();}
                else if(x>getWidth()-dp(91)){showShop();}
                return true;
            }
            if(phase==READY)startCast(x);
            else if(phase==CASTING&&castWaitingSecondTap){finishCast();ignoreUp=true;}
            else if(phase==FLOATING){phase=REELING;held=true;}
            else if(phase==BITE){
                if(!biteNeedsRelease&&SystemClock.uptimeMillis()-biteStartedMs<(hookedBoot?7000:BITE_TIMES[rarity]))beginCatch();
            }else if(phase==CATCHING)held=true;
            render();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(ignoreUp){ignoreUp=false;return true;}
            if(phase==CASTING){
                if(action==MotionEvent.ACTION_CANCEL){phase=READY;held=false;castWaitingSecondTap=false;}
                else if(SystemClock.uptimeMillis()-castPressTime<QUICK_TAP_MS){
                    held=false;castWaitingSecondTap=true;
                }else finishCast();
            }else if(phase==REELING){phase=FLOATING;held=false;}
            else if(phase==BITE){held=false;biteNeedsRelease=false;}
            else if(phase==CATCHING)held=false;
            else held=false;
            render();return true;
        }
        return true;
    }
}
