package ru.reader.rsvp;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;
import androidx.annotation.Nullable;

/** Layered Android AnimatedVectorDrawable pet: independent wings, head, eyes,
 * eyebrows, beak and fixed-size glasses/shades/headphones. No full-sprite swaps.
 * Each mode owns its animators. Idle is paused while reactions/intro/sleep run.
 */
public final class ChteicoPetView extends ImageView {
    private static final int IDLE=0, JOY=1, SURPRISE=2, EVOLVE=3, SLEEP=4, WAKE=5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private int currentMode=-1;
    private boolean attached=false;
    private boolean second=false;
    private boolean transientMode=false;
    private int stageSnapshot=-1, branchSnapshot=-1, eggUnitsSnapshot=-1;
    private final Runnable eggMotion=new Runnable(){@Override public void run(){
        if(!attached || ChteicoStore.stage(getContext())!=ChteicoStore.EGG)return;
        second=!second;
        setPivotX(getWidth()/2f);setPivotY(getHeight()*.83f);
        animate().scaleX(second?1.04f:1f).scaleY(second?.965f:1f).rotation(second?-2.4f:2.4f)
            .setDuration(900).start();
        handler.postDelayed(this,1100);
    }};
    private final Runnable idleReturn=new Runnable(){@Override public void run(){transientMode=false;startIdle();}};
    private final Runnable finishSleep=new Runnable(){@Override public void run(){
        if(!attached){transientMode=false;return;}
        setMode(WAKE);
        handler.postDelayed(idleReturn,820);
    }};

    public ChteicoPetView(Context c){super(c);setup();}
    public ChteicoPetView(Context c,@Nullable AttributeSet attrs){super(c,attrs);setup();}
    private void setup(){setScaleType(ScaleType.FIT_CENTER);setAdjustViewBounds(true);setContentDescription("Чтейцо — книжный питомец");}
    private String stageKey(){int s=ChteicoStore.stage(getContext());return s==ChteicoStore.CHICK?"chick":s==ChteicoStore.READER?"reader":"adult";}
    private String branchKey(){int b=ChteicoStore.branch(getContext());return b==ChteicoStore.AUDIO?"audio":b==ChteicoStore.SPEED?"speed":"classic";}
    private String modeKey(int mode){return mode==JOY?"joy":mode==SURPRISE?"surprise":mode==EVOLVE?"evolve":mode==SLEEP?"sleep":mode==WAKE?"wake":"idle";}
    private void stopAnimation(){
        Drawable old=getDrawable();
        if(old instanceof Animatable)((Animatable)old).stop();
    }
    private void setMode(int mode){
        int stage=ChteicoStore.stage(getContext());
        if(stage==ChteicoStore.EGG)return;
        stopAnimation();
        currentMode=mode;
        stageSnapshot=stage;branchSnapshot=ChteicoStore.branch(getContext());
        int id=getResources().getIdentifier("avd_chteico_"+stageKey()+"_"+branchKey()+"_"+modeKey(mode),"drawable",getContext().getPackageName());
        if(id==0)throw new IllegalStateException("Missing Chteico AnimatedVectorDrawable "+stageKey()+" "+branchKey()+" "+modeKey(mode));
        Drawable drawable=getContext().getDrawable(id);
        if(drawable!=null){
            setImageDrawable(drawable.mutate());
            if(attached && drawable instanceof Animatable)((Animatable)drawable).start();
        }
    }
    public void refresh(){
        handler.removeCallbacks(eggMotion);
        handler.removeCallbacks(idleReturn);
        handler.removeCallbacks(finishSleep);
        stopAnimation();
        currentMode=-1;transientMode=false;
        int stage=ChteicoStore.stage(getContext());
        stageSnapshot=stage;branchSnapshot=ChteicoStore.branch(getContext());
        second=false;
        if(stage==ChteicoStore.EGG){
            int eggUnits=ChteicoStore.eggUnits(getContext());eggUnitsSnapshot=eggUnits;
            setImageResource(ChteicoStore.eggCracked(getContext())?R.drawable.ic_chteico_egg_cracked:R.drawable.ic_chteico_egg);
            setScaleX(1);setScaleY(1);setRotation(0);
            if(attached)handler.postDelayed(eggMotion,800);
        }else{
            setScaleX(1);setScaleY(1);setRotation(0);
            setMode(IDLE);
        }
    }
    private void startIdle(){
        if(!attached)return;
        transientMode=false;
        if(ChteicoStore.stage(getContext())==ChteicoStore.EGG){refresh();return;}
        setMode(IDLE);
    }
    public void playTapReaction(boolean surprise){
        if(ChteicoStore.stage(getContext())==ChteicoStore.EGG)return;
        handler.removeCallbacks(idleReturn);
        handler.removeCallbacks(finishSleep);
        transientMode=true;
        setMode(surprise?SURPRISE:JOY);
        handler.postDelayed(idleReturn,surprise?820:750);
    }
    public void playEvolution(){
        if(ChteicoStore.stage(getContext())==ChteicoStore.EGG)return;
        handler.removeCallbacks(idleReturn);handler.removeCallbacks(finishSleep);
        transientMode=true;
        setMode(EVOLVE);
        handler.postDelayed(idleReturn,1410);
    }
    public void playSleepWake(){
        if(ChteicoStore.stage(getContext())==ChteicoStore.EGG)return;
        handler.removeCallbacks(idleReturn);handler.removeCallbacks(finishSleep);
        transientMode=true;
        setMode(SLEEP);
        handler.postDelayed(finishSleep,1580);
    }
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();attached=true;
        if(currentMode==-1){refresh();return;}
        Drawable active=getDrawable();
        if(active instanceof Animatable)((Animatable)active).start();
        if(ChteicoStore.stage(getContext())==ChteicoStore.EGG)handler.postDelayed(eggMotion,800);
    }
    @Override protected void onDetachedFromWindow(){
        attached=false;
        handler.removeCallbacksAndMessages(null);
        stopAnimation();
        animate().cancel();
        super.onDetachedFromWindow();
    }
}
