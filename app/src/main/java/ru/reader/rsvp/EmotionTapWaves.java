package ru.reader.rsvp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** A light, short-lived burst of comic tap rays when a pet emotion appears.
 * No AnimatedVectorDrawable or permanent sprite animation is involved. */
public final class EmotionTapWaves extends View {
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator burst;
    private float progress=-1;
    public EmotionTapWaves(Context context){ super(context);setClickable(false); }
    public void burst(){
        if(burst!=null)burst.cancel();
        burst=ValueAnimator.ofFloat(0f,1f);
        burst.setDuration(300);
        burst.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});
        burst.start();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(progress<0||progress>=1||getWidth()==0||getHeight()==0)return;
        final float scale=getResources().getDisplayMetrics().density;
        float cx=getWidth()/2f,cy=getHeight()*.33f;
        float outer=Math.min(getWidth()*.49f,getHeight()*.31f);
        float start=outer*(.43f+.35f*progress),end=outer*(.63f+.35f*progress);
        int fg=getContext().getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        pen.setStyle(Paint.Style.STROKE);pen.setStrokeWidth(Math.max(scale*2f,getWidth()*.012f));
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setColor(fg==Color.TRANSPARENT?0xff30221e:fg);
        pen.setAlpha((int)(220*(1-progress)));
        for(int angle=20;angle<=340;angle+=40){
            double r=Math.toRadians(angle);
            float dx=(float)Math.cos(r),dy=(float)Math.sin(r);
            canvas.drawLine(cx+dx*start,cy+dy*start,cx+dx*end,cy+dy*end,pen);
        }
    }
    @Override protected void onDetachedFromWindow(){if(burst!=null)burst.cancel();super.onDetachedFromWindow();}
}
