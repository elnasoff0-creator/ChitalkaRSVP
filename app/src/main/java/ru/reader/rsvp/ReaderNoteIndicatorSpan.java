package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.style.LineBackgroundSpan;

/** One note icon at the end of a text line, with count when multiple notes share the line. */
final class ReaderNoteIndicatorSpan implements LineBackgroundSpan {
    private final Drawable noteIcon;
    private final int color;
    private final float density;
    private final int[] endOffsets;
    ReaderNoteIndicatorSpan(Context context,int tint,int[] ends){
        this.color=tint;this.endOffsets=ends.clone();
        density=context.getResources().getDisplayMetrics().density;
        Drawable raw=context.getDrawable(R.drawable.ic_reader_selection_note);
        noteIcon=raw==null?null:raw.mutate();
        if(noteIcon!=null)noteIcon.setColorFilter(color,PorterDuff.Mode.SRC_IN);
    }
    @Override public void drawBackground(Canvas canvas,Paint paint,int left,int right,int top,
        int baseline,int bottom,CharSequence text,int start,int end,int lineNumber){
        int count=0;
        for(int offset:endOffsets)if(offset>start&&offset<=end)count++;
        if(count==0)return;
        int size=Math.round(18*density);
        int x=right-Math.round(20*density),y=top+Math.max(0,(bottom-top-size)/2);
        if(noteIcon!=null){noteIcon.setBounds(x,y,x+size,y+size);noteIcon.draw(canvas);}
        if(count>1){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);
            float cx=x+size+1*density,cy=y+2*density;
            canvas.drawCircle(cx,cy,7*density,p);
            int luminance=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
            p.setColor(luminance>155?Color.BLACK:Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);p.setTextSize(9*density);
            canvas.drawText(count>9?"9+":String.valueOf(count),cx,cy+3*density,p);
        }
    }
}
