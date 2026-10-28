package ru.reader.rsvp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.ImageView;
import androidx.annotation.Nullable;

/** Lightweight two-frame pet. Only the wings change between the
 * up/down vectors; a small spring is applied to the whole mascot.
 * No animated drawable resources or secondary emotion animations.
 */
public final class ChteicoPetView extends ImageView {
    private static final long FRAME_MS=820L;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean attached;
    private boolean frameDown;
    private boolean sleeping;
    private boolean forceNightWhenSleeping;
    private int shownStage=-1, shownBranch=-1, shownEggUnits=-1;

    // Stage order: Chick, Reader, Adult. Branch order: classic, speed, audio.
    private static final int[][][] FRAMES={
        {
            {R.drawable.ic_chteico_chick_classic_up,R.drawable.ic_chteico_chick_classic_down},
            {R.drawable.ic_chteico_chick_speed_up,R.drawable.ic_chteico_chick_speed_down},
            {R.drawable.ic_chteico_chick_audio_up,R.drawable.ic_chteico_chick_audio_down}
        },
        {
            {R.drawable.ic_chteico_reader_classic_up,R.drawable.ic_chteico_reader_classic_down},
            {R.drawable.ic_chteico_reader_speed_up,R.drawable.ic_chteico_reader_speed_down},
            {R.drawable.ic_chteico_reader_audio_up,R.drawable.ic_chteico_reader_audio_down}
        },
        {
            {R.drawable.ic_chteico_adult_classic_up,R.drawable.ic_chteico_adult_classic_down},
            {R.drawable.ic_chteico_adult_speed_up,R.drawable.ic_chteico_adult_speed_down},
            {R.drawable.ic_chteico_adult_audio_up,R.drawable.ic_chteico_adult_audio_down}
        }
    };

    private final Runnable cycle=new Runnable(){@Override public void run(){
        if(!attached||sleeping)return;
        int stage=ChteicoStore.stage(getContext());
        int branch=ChteicoStore.branch(getContext());
        int units=ChteicoStore.eggUnits(getContext());
        if(stage!=shownStage || branch!=shownBranch || (stage==ChteicoStore.EGG&&units!=shownEggUnits)){
            frameDown=false;
            showFrame();
        }else{
            frameDown=!frameDown;
            showFrame();
        }
        spring();
        handler.postDelayed(this,FRAME_MS);
    }};

    public ChteicoPetView(Context c){super(c);setup();}
    public ChteicoPetView(Context c,@Nullable AttributeSet a){super(c,a);setup();}
    private void setup(){setScaleType(ScaleType.FIT_CENTER);setAdjustViewBounds(true);setContentDescription("Чтейцо — книжный питомец");}

    private void applyThemeInk(){
        int bg=getContext().getSharedPreferences("reader",Context.MODE_PRIVATE).getInt("bg",0xfff3e7d5);
        if((forceNightWhenSleeping && sleeping)||ReadingThemeCycle.isDark(bg))setColorFilter(0xffffffff);
        else clearColorFilter();
    }
    private void showFrame(){
        applyThemeInk();
        int stage=ChteicoStore.stage(getContext());
        int branch=ChteicoStore.branch(getContext());
        shownStage=stage;shownBranch=branch;
        if(stage==ChteicoStore.EGG){
            shownEggUnits=ChteicoStore.eggUnits(getContext());
            setImageResource(ChteicoStore.eggCracked(getContext())
                    ?R.drawable.ic_chteico_egg_cracked:R.drawable.ic_chteico_egg);
            return;
        }
        int s=Math.max(0,Math.min(FRAMES.length-1,stage-ChteicoStore.CHICK));
        int b=Math.max(0,Math.min(2,branch-ChteicoStore.CLASSIC));
        setImageResource(FRAMES[s][b][frameDown?1:0]);
    }

    private void spring(){
        boolean egg=shownStage==ChteicoStore.EGG;
        setPivotX(getWidth()*.5f);
        setPivotY(getHeight()*.84f);
        animate().cancel();
        animate()
            .scaleX(egg?(frameDown?1.035f:1f):(frameDown?1.012f:1f))
            .scaleY(egg?(frameDown?.96f:1f):(frameDown?.986f:1.012f))
            .setDuration(FRAME_MS-80L).start();
    }

    /** Only the home mini-pet opts into isolated night contrast while sleeping. */
    public void setForceNightWhenSleeping(boolean value){
        if(forceNightWhenSleeping==value)return;
        forceNightWhenSleeping=value;
        applyThemeInk();
    }
    public void setSleeping(boolean value){
        if(sleeping==value)return;
        sleeping=value;handler.removeCallbacks(cycle);
        animate().cancel();setScaleX(1f);setScaleY(1f);
        frameDown=value;showFrame();
        if(attached&&!sleeping)handler.postDelayed(cycle,FRAME_MS);
    }
    public void refresh(){
        handler.removeCallbacks(cycle);
        frameDown=sleeping;
        shownStage=-1;shownBranch=-1;shownEggUnits=-1;
        animate().cancel();setScaleX(1f);setScaleY(1f);setRotation(0f);setTranslationY(0f);
        showFrame();
        if(attached&&!sleeping)handler.postDelayed(cycle,FRAME_MS);
    }

    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();attached=true;refresh();
    }
    @Override protected void onDetachedFromWindow(){
        attached=false;handler.removeCallbacksAndMessages(null);animate().cancel();
        super.onDetachedFromWindow();
    }
}
