package ru.reader.rsvp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.LineBackgroundSpan;

/** A small themed note glyph drawn in the line margin without modifying book text. */
final class ReaderNoteIndicatorSpan implements LineBackgroundSpan {
    private final int color,endOffset;
    private final float density;
    ReaderNoteIndicatorSpan(int color,int endOffset,float density){
        this.color=color;this.endOffset=endOffset;this.density=density;
    }
    @Override public void drawBackground(Canvas canvas,Paint paint,int left,int right,int top,
        int baseline,int bottom,CharSequence text,int start,int end,int lineNumber){
        if(end<endOffset||start>=endOffset)return;
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f*density);
        float x=right-12*density,y=top+2*density;
        canvas.drawRoundRect(new RectF(x,y,x+10*density,y+8*density),2.1f*density,2.1f*density,p);
        canvas.drawLine(x+4*density,y+8*density,x+3*density,y+10*density,p);
    }
}
