package ru.reader.rsvp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

/** Actual percent is provided by the downloader; only the highlight moves.
 * Animators stop on pause, completion, detachment and system reduced-animation mode.
 */
final class AnimatedDownloadProgressBar extends ProgressBar {
    private final Paint sheen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator motion;
    private boolean working;
    private float phase;
    AnimatedDownloadProgressBar(Context context){
        super(context,null,android.R.attr.progressBarStyleHorizontal);
        sheen.setColor(0x88ffffff);
    }
    void setWorking(boolean active){
        if(working==active)return;
        working=active;
        if(active)startMotion();else stopMotion();
        invalidate();
    }
    private void startMotion(){
        if(!working||!isAttachedToWindow()||motion!=null)return;
        if(Build.VERSION.SDK_INT>=26&&!ValueAnimator.areAnimatorsEnabled())return;
        motion=ValueAnimator.ofFloat(0f,1f);
        motion.setInterpolator(new LinearInterpolator());motion.setDuration(1100L);
        motion.setRepeatCount(ValueAnimator.INFINITE);
        motion.addUpdateListener(animation->{phase=(Float)animation.getAnimatedValue();invalidate();});
        motion.start();
    }
    private void stopMotion(){
        if(motion!=null){motion.cancel();motion=null;}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();startMotion();}
    @Override protected void onDetachedFromWindow(){stopMotion();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(!working||isIndeterminate()||getProgress()<=0||getMax()<=0)return;
        float left=getPaddingLeft(),right=getWidth()-getPaddingRight();
        float top=getPaddingTop(),bottom=getHeight()-getPaddingBottom();
        if(right<=left||bottom<=top)return;
        float filled=left+(right-left)*Math.min(1f,getProgress()/(float)getMax());
        float width=Math.min((right-left)*.18f,46f*getResources().getDisplayMetrics().density);
        if(filled-left<2f||width<2f)return;
        float x=left+(filled-left+width*2f)*phase-width;
        int save=canvas.save();canvas.clipRect(left,top,filled,bottom);
        canvas.drawRoundRect(x,top,x+Math.max(2f,width*.22f),bottom,
            (bottom-top)/2f,(bottom-top)/2f,sheen);
        canvas.restoreToCount(save);
    }
}
