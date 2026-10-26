package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.FrameLayout;

/** The same one-piece bubble and speech tail as the full Chteico screen. */
public final class ChteicoSpeechBubble extends FrameLayout {
    private final Paint fill=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final int background,foreground;
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    public ChteicoSpeechBubble(Context context,int bg,int fg){
        super(context);background=bg;foreground=fg;
        setWillNotDraw(false);setPadding(dp(12),dp(7),dp(12),dp(19));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        float tailH=dp(12),r=dp(16);
        float x1=dp(1),x2=w-dp(1),y1=dp(1),y2=h-tailH-dp(1);
        float tail=w*.70f;
        path.reset();
        path.moveTo(x1+r,y1);path.lineTo(x2-r,y1);
        path.quadTo(x2,y1,x2,y1+r);path.lineTo(x2,y2-r);
        path.quadTo(x2,y2,x2-r,y2);path.lineTo(tail+dp(11),y2);
        path.lineTo(tail-dp(3),h-dp(1));path.lineTo(tail-dp(12),y2);
        path.lineTo(x1+r,y2);path.quadTo(x1,y2,x1,y2-r);
        path.lineTo(x1,y1+r);path.quadTo(x1,y1,x1+r,y1);
        path.close();
        fill.setColor(background==0xff1e2025?0xff514a49:0xfffffdf8);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(foreground);stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(1));
        canvas.drawPath(path,fill);canvas.drawPath(path,stroke);
    }
}
