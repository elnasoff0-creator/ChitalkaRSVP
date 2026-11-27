package ru.reader.rsvp;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;

final class WebTransferIndicator extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int color;private final float density;private float phase;
    private ValueAnimator animator;
    WebTransferIndicator(Context c){color=UiColorScheme.blend(UiColorScheme.accent(c),UiColorScheme.onAccent(c),.5f);density=c.getResources().getDisplayMetrics().density;}
    void start(){if(animator!=null)return;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(1800);animator.setRepeatCount(ValueAnimator.INFINITE);animator.addUpdateListener(a->{phase=(Float)a.getAnimatedValue();invalidateSelf();});animator.start();}
    void stop(){if(animator!=null){animator.cancel();animator=null;}phase=0;invalidateSelf();}
    @Override public void draw(Canvas c){Rect b=getBounds();float inset=(2+phase*3)*density;paint.setColor(color);paint.setAlpha((int)(220*(1-phase)));paint.setStrokeWidth(2*density);paint.setStyle(Paint.Style.STROKE);c.drawRoundRect(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset,12*density,12*density,paint);}
    @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
