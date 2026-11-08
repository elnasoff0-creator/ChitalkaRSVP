package ru.reader.rsvp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.Locale;
import java.util.Random;

/** Fishing uses Android XML graphics only; Java controls states, positioning and animation. */
public final class ChteicoFishingView extends FrameLayout {
    private static final int READY=0,CASTING=1,FLOATING=2,REELING=3,BITE=4,CATCHING=5,RESULT=6;
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
    private static final int[] BITE_DRAWABLES={R.drawable.ic_fishing_bite_6,R.drawable.ic_fishing_bite_5,
            R.drawable.ic_fishing_bite_4,R.drawable.ic_fishing_bite_3,R.drawable.ic_fishing_bite_2};
    private static final long QUICK_TAP_MS=220L;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private final Runnable exit;
    private final TextView hud,status,feedback,biteMessage,resultName,resultRarity,resultMass,resultRewards;
    private final ImageView bobber,fish,zone,castMark,castCursor,ring,wave1,wave2,resultFish;
    private final FrameLayout meter,catchPanel,track;
    // The result panel is a LinearLayout in XML; only common View methods are needed.
    private final View resultPanel;
    private final ProgressBar catchProgress;
    private final int accent,ink;
    private int phase=READY,rarity=0,castGrade=2;
    private boolean held=false,paused=false,stopped=false,castWaitingSecondTap=false,ignoreUp=false,biteNeedsRelease=false,hookedBoot=false,fishHeadingUp=true;
    private float cast=0f,castDirection=1f,distance=0f,depth=0f,zoneY=.62f,fishY=.45f;
    private float fishVelocity=.27f,fishSteer=0f,catchAmount=.28f,bobberX=.5f;
    private long lastMs=0,castPressTime=0,biteStartedMs=0,feedbackUntil=0;
    private String outcome="",feedbackText="";
    private int feedbackColor=0xff16a34a,displayedBait=Integer.MIN_VALUE,displayedGold=Integer.MIN_VALUE;
    private final Runnable clock=new Runnable(){@Override public void run(){
        if(stopped||paused||!isAttachedToWindow())return;
        long now=SystemClock.uptimeMillis();
        float dt=lastMs==0?0f:Math.min(.055f,Math.max(0f,(now-lastMs)/1000f));lastMs=now;
        update(dt,now);render();handler.postDelayed(this,16);
    }};
    public ChteicoFishingView(Context context,Runnable onExit){
        super(context);exit=onExit;
        LayoutInflater.from(context).inflate(R.layout.view_chteico_fishing,this,true);
        hud=findViewById(R.id.fishing_hud);status=findViewById(R.id.fishing_status);
        bobber=findViewById(R.id.fishing_bobber);fish=findViewById(R.id.fishing_fish);zone=findViewById(R.id.fishing_zone);
        meter=findViewById(R.id.fishing_cast_meter);castMark=findViewById(R.id.fishing_cast_optimum);
        castCursor=findViewById(R.id.fishing_cast_indicator);catchPanel=findViewById(R.id.fishing_catch_panel);
        track=findViewById(R.id.fishing_track);catchProgress=findViewById(R.id.fishing_catch_progress);
        feedback=findViewById(R.id.fishing_cast_feedback);biteMessage=findViewById(R.id.fishing_bite_message);
        ring=findViewById(R.id.fishing_bite_ring);wave1=findViewById(R.id.fishing_wave_1);wave2=findViewById(R.id.fishing_wave_2);
        resultPanel=findViewById(R.id.fishing_result_panel);resultFish=findViewById(R.id.fishing_result_fish);
        resultName=findViewById(R.id.fishing_result_title);resultRarity=findViewById(R.id.fishing_result_rarity);
        resultMass=findViewById(R.id.fishing_result_mass);resultRewards=findViewById(R.id.fishing_result_rewards);
        accent=ReaderButtonColors.accent(context,0xff16a34a);ink=0xff30221e;
        hud.setTextColor(ink);status.setTextColor(ink);feedback.setTextColor(ink);biteMessage.setTextColor(ink);
        castMark.setColorFilter(accent);zone.setColorFilter(accent);ring.setColorFilter(accent);
        catchProgress.setProgressTintList(ColorStateList.valueOf(accent));
        setClickable(true);setFocusable(true);
        setContentDescription("Рыбалка. Касайтесь игрового поля для заброса, поклёвки и вываживания");
        render();
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();resumeClock();}
    @Override protected void onDetachedFromWindow(){stopClock();super.onDetachedFromWindow();}
    public void pauseClock(){paused=true;held=false;castWaitingSecondTap=false;
        if(phase==CASTING)phase=READY;
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
        if(phase==REELING&&held){
            depth=Math.min(1f,depth+dt*.20f);
            // Rate per second, independent from display refresh rate; never rolls while idle.
            double p=(.40+.52*distance)*dt;
            if(random.nextDouble()<1-Math.exp(-p)){startBite(now);return;}
            if(depth>=.985f){finish("Поплавок вернулся. Поклёвки не было.");return;}
        }
        if(phase==BITE&&now-biteStartedMs>=BITE_TIMES[rarity]){
            stopRing();finish("Время вышло — рыба сорвалась!");return;
        }
        if(phase==CATCHING){
            fishSteer-=dt;
            if(fishSteer<=0){fishSteer=.27f+random.nextFloat()*.43f;
                fishVelocity=(random.nextFloat()-.5f)*1.18f*SPEED[rarity];}
            fishY+=fishVelocity*dt;
            if(fishY<.07f){fishY=.07f;fishVelocity=Math.abs(fishVelocity);}
            if(fishY>.91f){fishY=.91f;fishVelocity=-Math.abs(fishVelocity);}
            // Head points in the actual travel direction (positive Y = down).
            updateFishHeading(fishVelocity<0f);
            zoneY+=dt*(held?-.53f:.34f);
            zoneY=Math.max(0f,Math.min(.75f,zoneY));
            boolean inside=fishY>=zoneY-.04f&&fishY<=zoneY+.30f;
            catchAmount=Math.max(0,Math.min(1,catchAmount+dt*(inside?.26f/FILL_TIME[rarity]:-.12f)));
            if(catchAmount>=1f){successfulCatch();return;}
            if(catchAmount<=0f){finish("Рыба сорвалась!");}
        }
    }
    private void startBite(long now){
        // Boots are possible only after a failed cast and cannot award XP or gold.
        hookedBoot=castGrade==4&&random.nextFloat()<.25f;
        rarity=hookedBoot?0:chooseRarity();
        phase=BITE;held=false;biteNeedsRelease=true;biteStartedMs=now;
        biteMessage.setText(hookedBoot?"Что-то зацепилось!":"Рыба клюнула!");
        ring.setImageResource(BITE_DRAWABLES[rarity]);ring.setColorFilter(accent);
        ring.setVisibility(VISIBLE);
        if(ring.getDrawable() instanceof Animatable)((Animatable)ring.getDrawable()).start();
        vibrate(75);playWaves();
    }
    private int chooseRarity(){
        // Baseline relative weights: common 55, uncommon 28, rare 12, legendary 4, mythical 1.
        // Apply cast quality to the three rarest groups, then normalize by total weight.
        double quality=QUALITY_RARITY_MULTIPLIER[Math.max(0,Math.min(4,castGrade))];
        double[] weights={55d,28d,12d*quality,4d*quality,1d*quality};
        double total=0d;for(double w:weights)total+=w;
        double roll=random.nextDouble()*total;
        for(int i=0;i<weights.length;i++){roll-=weights[i];if(roll<0)return i;}
        return 4;
    }
    private float fishAngle(boolean headUp){
        // Common art is vertical, the other four XML vectors have their head on the left.
        return rarity==0?(headUp?0f:180f):(headUp?90f:-90f);
    }
    private void updateFishHeading(boolean headUp){
        if(fishHeadingUp==headUp)return;
        fishHeadingUp=headUp;
        fish.animate().cancel();
        fish.animate().rotation(fishAngle(headUp)).setDuration(145L).start();
    }
    private void beginCatch(){
        stopRing();ring.setVisibility(GONE);
        if(hookedBoot){showBootResult();return;}
        phase=CATCHING;held=true;
        zoneY=.58f;fishY=.52f;fishSteer=.25f;catchAmount=.28f;
        fishVelocity=random.nextBoolean()?-.27f:.27f;
        fish.setImageResource(FISH_ART[rarity]);
        fish.setScaleX(FISH_SCALE[rarity]);fish.setScaleY(FISH_SCALE[rarity]);
        fish.animate().cancel();
        fishHeadingUp=fishVelocity<0f;
        fish.setRotation(fishAngle(fishHeadingUp));
    }
    private void showBootResult(){
        ChteicoStore.recordFishingBoot(getContext());
        resultName.setText("Старый башмак");resultName.setTextColor(BOOT_BROWN);
        resultRarity.setText("Бесполезная находка");resultRarity.setTextColor(BOOT_BROWN);
        resultMass.setText("Вместо рыбы — старый башмак");resultMass.setTextColor(BOOT_BROWN);
        resultRewards.setText("0 XP   ·   0 золота");resultRewards.setTextColor(BOOT_BROWN);
        resultFish.setImageResource(R.drawable.ic_fishing_old_boot);
        resultFish.setRotation(0f);
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)resultFish.getLayoutParams();
        lp.width=(int)dp(142f);lp.height=(int)dp(84f);resultFish.setLayoutParams(lp);
        finish("Башмак пойман!");
    }
    private void successfulCatch(){
        float[] min={.2f,.5f,1f,2f,3f},max={2f,3.5f,5f,8f,12f};
        float mass=min[rarity]+random.nextFloat()*(max[rarity]-min[rarity]);
        int[] mul2={2,3,5,8,14};
        int xp=Math.round(mass*20*mul2[rarity]/2f);
        int gold=Math.max(1,Math.round(mass*5*mul2[rarity]/2f));
        ChteicoStore.recordFishingCatch(getContext(),Math.round(mass*1000),xp,gold,rarity);
        resultName.setText("Рыба поймана!");resultName.setTextColor(ink);
        resultMass.setTextColor(ink);resultRewards.setTextColor(ink);
        resultRarity.setText(RARITY_NAMES[rarity]);
        resultRarity.setTextColor(rarity==0?ink:RARITY_COLORS[rarity]);
        resultMass.setText(String.format(Locale.forLanguageTag("ru"),"Масса: %.2f кг",mass));
        resultRewards.setText("+"+xp+" XP   ·   +"+gold+" золота");
        resultFish.setImageResource(FISH_ART[rarity]);
        resultFish.setRotation(rarity==0?-90f:0f);
        // sqrt scaling keeps larger specimens visible but limits to the card width.
        float scale=Math.max(.85f,Math.min(1.75f,.85f+.22f*(float)Math.sqrt(mass)));
        FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)resultFish.getLayoutParams();
        lp.width=(int)dp(92f*scale);lp.height=(int)dp(72f*scale);resultFish.setLayoutParams(lp);
        finish("Улов сохранён!");
    }
    private void finish(String message){phase=RESULT;held=false;castWaitingSecondTap=false;outcome=message;}
    private void startCast(float x){
        if(ChteicoStore.fishingBait(getContext())<1){
            new SwipeDialogs.Builder(getContext()).setTitle("Нет наживок")
              .setMessage("Читайте главы и страницы манги, чтобы получать наживки, или покупайте их за золото.")
              .setPositiveButton("Купить",(d,w)->showShop()).setNegativeButton("Закрыть",null).show();return;
        }
        bobberX=Math.max(.06f,Math.min(.94f,x/Math.max(1f,getWidth())));
        cast=0f;castDirection=1f;phase=CASTING;held=true;castWaitingSecondTap=false;hookedBoot=false;
        castPressTime=SystemClock.uptimeMillis();feedbackUntil=0;outcome="";
    }
    private void finishCast(){
        if(!ChteicoStore.useFishingBait(getContext())){phase=READY;held=false;return;}
        float error=Math.abs(cast-.80f);
        distance=Math.max(.08f,Math.min(1f,1f-error/.80f));
        depth=.09f+(1-distance)*.73f;phase=FLOATING;held=false;castWaitingSecondTap=false;
        if(error<=.06f){castGrade=0;feedbackText="Супер!";feedbackColor=0xff16a34a;}
        else if(error<=.12f){castGrade=1;feedbackText="Отлично!";feedbackColor=0xff3f9b50;}
        else if(error<=.20f){castGrade=2;feedbackText="Хорошо!";feedbackColor=0xffaa8600;}
        else if(error<=.35f){castGrade=3;feedbackText="Неплохо";feedbackColor=0xffd47c1c;}
        else {castGrade=4;feedbackText="Неудачно";feedbackColor=0xffc53030;}
        feedbackUntil=SystemClock.uptimeMillis()+1400;
        vibrate(40);post(()->{renderBobber();playWaves();});
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
        String[] items={"1 наживка — 30 золота","10 наживок — 270 золота","50 наживок — 1200 золота"};
        final int[] amounts={1,10,50};
        new SwipeDialogs.Builder(getContext()).setTitle("Купить наживки · Золото: "+ChteicoStore.fishingGold(getContext()))
          .setItems(items,(d,which)->{
            if(!ChteicoStore.buyFishingBait(getContext(),amounts[which]))
                android.widget.Toast.makeText(getContext(),"Недостаточно золота",android.widget.Toast.LENGTH_SHORT).show();
            render();
          }).setNegativeButton("Отмена",null).show();
    }
    private void updateStatus(String message){if(!message.contentEquals(status.getText()))status.setText(message);}
    private void renderBobber(){
        float minX=dp(8),maxX=Math.max(minX,getWidth()-bobber.getWidth()-dp(8));
        bobber.setX(Math.max(minX,Math.min(maxX,getWidth()*bobberX-bobber.getWidth()/2f)));
        float max=Math.max(0,getHeight()-dp(150));bobber.setY(dp(55)+max*depth);
    }
    private void render(){
        int bait=ChteicoStore.fishingBait(getContext()),gold=ChteicoStore.fishingGold(getContext());
        if(bait!=displayedBait||gold!=displayedGold){displayedBait=bait;displayedGold=gold;
            hud.setText("Рыбалка · "+bait+" наж. · "+gold+" зол.");}
        meter.setVisibility(phase==CASTING?VISIBLE:GONE);
        bobber.setVisibility(phase==FLOATING||phase==REELING||phase==BITE?VISIBLE:GONE);
        catchPanel.setVisibility(phase==CATCHING?VISIBLE:GONE);
        catchProgress.setVisibility(phase==CATCHING?VISIBLE:GONE);
        ring.setVisibility(phase==BITE?VISIBLE:GONE);
        biteMessage.setVisibility(phase==BITE?VISIBLE:GONE);
        resultPanel.setVisibility(phase==RESULT&&( "Улов сохранён!".equals(outcome)||"Башмак пойман!".equals(outcome))?VISIBLE:GONE);
        if(phase==CASTING){
            int height=Math.max(1,meter.getHeight());
            castMark.setY(Math.max(0f,(1f-.80f)*height-castMark.getHeight()/2f));
            castCursor.setY(Math.max(0f,(1f-cast)*(height-castCursor.getHeight())));
            updateStatus(castWaitingSecondTap?"Второй тап — заброс":"Отпустите на 80% или сделайте два коротких тапа");
        }else if(phase==FLOATING)updateStatus("Зажмите, чтобы подтягивать поплавок");
        else if(phase==REELING)updateStatus("Подтягивание · шанс поклёвки только при удерживании");
        else if(phase==BITE)updateStatus("Рыба клюнула! Отпустите и быстро тапните!");
        else if(phase==CATCHING){
            float height=Math.max(dp(140),track.getHeight());
            zone.setY((height-zone.getHeight())*zoneY/.75f);
            fish.setY((height-fish.getHeight())*fishY);
            catchProgress.setProgress(Math.round(catchAmount*100));
            updateStatus("Удерживайте рыбку внутри акцентной зоны");
        }else if(phase==RESULT)updateStatus(outcome+" · Тап — закрыть результат");
        else updateStatus("Нажмите для заброса. Можно удерживать или тапнуть дважды");
        if(phase==FLOATING||phase==REELING||phase==BITE)renderBobber();
        if(phase==BITE){
            float x=bobber.getX()+bobber.getWidth()/2f-ring.getWidth()/2f;
            ring.setX(Math.max(dp(6),Math.min(x,getWidth()-ring.getWidth()-dp(6))));
            ring.setY(Math.max(dp(70),bobber.getY()-dp(16)));
        }
        boolean showFeedback=feedbackUntil>SystemClock.uptimeMillis()&&phase!=CASTING;
        feedback.setVisibility(showFeedback?VISIBLE:GONE);
        if(showFeedback){feedback.setText(feedbackText);feedback.setTextColor(feedbackColor);}
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            float x=e.getX(),y=e.getY();ignoreUp=false;
            if(phase==RESULT){
                // A reward overlay consumes the WHOLE gesture even if the user taps on a toolbar icon.
                // ACTION_UP is swallowed too, so it cannot begin the next cast.
                phase=READY;outcome="";held=false;ignoreUp=true;render();return true;
            }
            if(y<dp(45)&&x>getWidth()-dp(44)){ignoreUp=true;stopClock();if(exit!=null)exit.run();return true;}
            if(y<dp(45)&&x>getWidth()-dp(116)){ignoreUp=true;showShop();return true;}
            if(phase==READY)startCast(x);
            else if(phase==CASTING&&castWaitingSecondTap){finishCast();ignoreUp=true;}
            else if(phase==FLOATING){phase=REELING;held=true;}
            else if(phase==BITE){
                if(!biteNeedsRelease&&SystemClock.uptimeMillis()-biteStartedMs<BITE_TIMES[rarity])beginCatch();
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
