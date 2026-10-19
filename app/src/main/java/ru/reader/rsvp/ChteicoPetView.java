package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;
import android.util.AttributeSet;
import android.widget.ImageView;

/** Vector pet: animated wings + spring reaction, persistent baseline and fixed-size audio headphones. */
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
        setPivotX(getWidth()/2f);setPivotY(getHeight());
        if(stage==ChteicoStore.EGG){
            setImageResource(ChteicoStore.eggCracked(getContext())?R.drawable.ic_chteico_egg_cracked:R.drawable.ic_chteico_egg);
            setScaleX(second?1.06f:1f);setScaleY(second?.93f:1f);
        }else{
            setScaleX(second?1.018f:1f);setScaleY(second?.976f:1f);
            if(branch==ChteicoStore.AUDIO)setImageResource(stage==ChteicoStore.CHICK?
                (second?R.drawable.ic_chteico_chick_audio_down:R.drawable.ic_chteico_chick_audio_up):
                (second?R.drawable.ic_chteico_adult_audio_down:R.drawable.ic_chteico_adult_audio_up));
            else if(branch==ChteicoStore.SPEED)setImageResource(stage==ChteicoStore.CHICK?
                (second?R.drawable.ic_chteico_chick_speed_down:R.drawable.ic_chteico_chick_speed_up):
                (second?R.drawable.ic_chteico_adult_speed_down:R.drawable.ic_chteico_adult_speed_up));
            else setImageResource(stage==ChteicoStore.CHICK?
                (second?R.drawable.ic_chteico_chick_classic_down:R.drawable.ic_chteico_chick_classic_up):
                (second?R.drawable.ic_chteico_adult_classic_down:R.drawable.ic_chteico_adult_classic_up));
        }
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        int stage=ChteicoStore.stage(getContext());
        if(stage!=ChteicoStore.EGG && second && ChteicoStore.branch(getContext())==ChteicoStore.AUDIO){
            Drawable current=getDrawable();
            int fixedRes=stage==ChteicoStore.CHICK?R.drawable.ic_chteico_chick_audio_up:R.drawable.ic_chteico_adult_audio_up;
            Drawable fixed=getContext().getDrawable(fixedRes);
            if(current!=null && fixed!=null){
                int w=getWidth(), h=getHeight();
                RectF headRect=new RectF(w*.17f,h*.07f,w*.83f,h*.63f);
                Path bodyOnly=new Path();
                bodyOnly.setFillType(Path.FillType.EVEN_ODD);
                bodyOnly.addRect(0,0,w,h,Path.Direction.CW);
                bodyOnly.addOval(headRect,Path.Direction.CCW);
                int saveBody=canvas.save();
                canvas.clipPath(bodyOnly);
                current.setBounds(0,0,w,h);
                current.draw(canvas);
                canvas.restoreToCount(saveBody);

                int saveHead=canvas.save();
                Path headOnly=new Path();
                headOnly.addOval(headRect,Path.Direction.CW);
                canvas.clipPath(headOnly);
                fixed.setBounds(0,0,w,h);
                fixed.draw(canvas);
                canvas.restoreToCount(saveHead);
                return;
            }
        }
        super.onDraw(canvas);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refresh();handler.postDelayed(animate,850);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(animate);super.onDetachedFromWindow();}
}
