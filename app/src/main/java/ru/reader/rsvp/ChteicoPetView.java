package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Two-frame vector animation: fixed ground baseline, wings beat; egg gently squash-stretches. */
public final class ChteicoPetView extends ImageView {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean second=false;
    private final Runnable animate=new Runnable(){@Override public void run(){
        if(!isAttachedToWindow())return;
        second=!second;render();handler.postDelayed(this,650);
    }};
    public ChteicoPetView(Context c){super(c);setup();}
    public ChteicoPetView(Context c,@Nullable AttributeSet attrs){super(c,attrs);setup();}
    private void setup(){setScaleType(ScaleType.FIT_CENTER);setAdjustViewBounds(true);}
    public void refresh(){second=false;render();}
    private void render(){
        int stage=ChteicoStore.stage(getContext()), branch=ChteicoStore.branch(getContext());
        if(stage==ChteicoStore.EGG){setImageResource(R.drawable.ic_chteico_egg);setPivotX(getWidth()/2f);setPivotY(getHeight()*.87f);
            setScaleX(second?1.06f:1f);setScaleY(second?.93f:1f);
        }else{
            setScaleX(1f);setScaleY(1f);
            if(branch==ChteicoStore.AUDIO)setImageResource(stage==ChteicoStore.CHICK?(second?R.drawable.ic_chteico_chick_audio_down:R.drawable.ic_chteico_chick_audio_up):(second?R.drawable.ic_chteico_adult_audio_down:R.drawable.ic_chteico_adult_audio_up));
            else if(branch==ChteicoStore.SPEED)setImageResource(stage==ChteicoStore.CHICK?(second?R.drawable.ic_chteico_chick_speed_down:R.drawable.ic_chteico_chick_speed_up):(second?R.drawable.ic_chteico_adult_speed_down:R.drawable.ic_chteico_adult_speed_up));
            else setImageResource(stage==ChteicoStore.CHICK?(second?R.drawable.ic_chteico_chick_classic_down:R.drawable.ic_chteico_chick_classic_up):(second?R.drawable.ic_chteico_adult_classic_down:R.drawable.ic_chteico_adult_classic_up));
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refresh();
        if(ChteicoStore.prefs(getContext()).getBoolean("animations_enabled",true))handler.postDelayed(animate,850);
    }
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(animate);super.onDetachedFromWindow();}
}
