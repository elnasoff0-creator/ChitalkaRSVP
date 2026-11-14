package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.style.LineBackgroundSpan;

/** Stable left-gutter indicators. Neither marker is drawn over the book's glyphs. */
final class ReaderNoteIndicatorSpan implements LineBackgroundSpan {
    private final Drawable noteIcon,bookmarkIcon;
    private final int color;
    private final float density;
    private final int[] noteEnds,bookmarkStarts;
    ReaderNoteIndicatorSpan(Context context,int tint,int[] ends,int[] starts){
        color=tint;noteEnds=ends.clone();bookmarkStarts=starts.clone();
        density=context.getResources().getDisplayMetrics().density;
        Drawable note=context.getDrawable(R.drawable.ic_reader_selection_note);
        noteIcon=note==null?null:note.mutate();
        Drawable bookmark=context.getDrawable(R.drawable.ic_reader_bookmark_side);
        bookmarkIcon=bookmark==null?null:bookmark.mutate();
        if(noteIcon!=null)noteIcon.setColorFilter(color,PorterDuff.Mode.SRC_IN);
        if(bookmarkIcon!=null)bookmarkIcon.setColorFilter(color,PorterDuff.Mode.SRC_IN);
    }
    @Override public void drawBackground(Canvas canvas,Paint paint,int left,int right,int top,
        int baseline,int bottom,CharSequence text,int start,int end,int lineNumber){
        int notes=0,marks=0;
        for(int offset:noteEnds)if(offset>start&&offset<=end)notes++;
        for(int offset:bookmarkStarts)if(offset>=start&&offset<end)marks++;
        if(notes==0&&marks==0)return;
        int size=Math.round(18*density),x=left-Math.round(22*density),height=bottom-top;
        int noteY=top+Math.max(0,(height-size)/2),bookmarkY=noteY;
        if(notes>0&&marks>0){
            // Two separate symbols are stacked inside the same permanent margin.
            int delta=Math.round(10*density);
            bookmarkY=top+Math.max(0,(height-size)/2)-delta;
            noteY=top+Math.max(0,(height-size)/2)+delta;
        }
        if(marks>0&&bookmarkIcon!=null){bookmarkIcon.setBounds(x,bookmarkY,x+size,bookmarkY+size);bookmarkIcon.draw(canvas);}
        if(notes>0&&noteIcon!=null){noteIcon.setBounds(x,noteY,x+size,noteY+size);noteIcon.draw(canvas);}
        if(notes>1){
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);
            float cx=x+size-2*density,cy=noteY+3*density;
            canvas.drawCircle(cx,cy,5.5f*density,p);
            int luminance=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
            p.setColor(luminance>155?Color.BLACK:Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(8*density);
            canvas.drawText(notes>9?"9+":String.valueOf(notes),cx,cy+2.7f*density,p);
        }
    }
}
