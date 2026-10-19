package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
        super.onDraw(canvas);
        int stage=ChteicoStore.stage(getContext());
        if(stage==ChteicoStore.EGG||!second)return;
        if(ChteicoStore.branch(getContext())==ChteicoStore.AUDIO){
            // The audio 'down' sprite has a different headphone fit. Overlay the same
            // central headphone/head drawing from the UP pose while the wings still flap.
            int res=stage==ChteicoStore.CHICK?R.drawable.ic_chteico_chick_audio_up:R.drawable.ic_chteico_adult_audio_up;
            Drawable fixed=getContext().getDrawable(res);
            if(fixed!=null){
                int save=canvas.save();
                Path clip=new Path();clip.addOval(new RectF(getWidth()*.21f,getHeight()*.09f,getWidth()*.79f,getHeight()*.60f),Path.Direction.CW);
                canvas.clipPath(clip);
                fixed.setBounds(0,0,getWidth(),getHeight());fixed.draw(canvas);
                canvas.restoreToCount(save);
            }
        }
        // Expressive raised brow accents, drawn above the original vector eyebrows.
        Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);brush.setColor(0xff30221e);
        brush.setStrokeWidth(getWidth()*.012f);brush.setStrokeCap(Paint.Cap.ROUND);brush.setStyle(Paint.Style.STROKE);
        for(float x:new float[]{.39f,.61f}){
            Path eyebrow=new Path();eyebrow.moveTo(getWidth()*(x-.023f),getHeight()*.305f);
            eyebrow.quadTo(getWidth()*x,getHeight()*.28f,getWidth()*(x+.024f),getHeight()*.301f);
            canvas.drawPath(eyebrow,brush);
        }
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refresh();handler.postDelayed(animate,850);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(animate);super.onDetachedFromWindow();}
}
