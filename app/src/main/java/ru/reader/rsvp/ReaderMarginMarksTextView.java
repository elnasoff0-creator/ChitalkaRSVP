package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

/** Reader's gutter is inside this TextView, so marks are never clipped by the text Layout. */
final class ReaderMarginMarksTextView extends TextView {
    private int[] notes=new int[0], bookmarks=new int[0];
    private int accent;
    private final int markSize, gutterInset;
    private final Drawable noteIcon, bookmarkIcon;
    private final Paint badgePaint=new Paint(Paint.ANTI_ALIAS_FLAG);
    ReaderMarginMarksTextView(Context context){
        super(context);
        float density=getResources().getDisplayMetrics().density;
        markSize=Math.round(18*density);gutterInset=Math.max(1,Math.round(2*density));
        noteIcon=getDrawable(R.drawable.ic_reader_selection_note);
        bookmarkIcon=getDrawable(R.drawable.ic_reader_bookmark_side);
    }
    private Drawable getDrawable(int id){
        Drawable drawable=getContext().getDrawable(id);
        if(drawable==null)return null;
        return drawable.getConstantState()==null?drawable.mutate():
            drawable.getConstantState().newDrawable(getResources()).mutate();
    }
    void setIndicators(int[] noteOffsets,int[] bookmarkOffsets,int color){
        notes=noteOffsets==null?new int[0]:noteOffsets.clone();
        bookmarks=bookmarkOffsets==null?new int[0]:bookmarkOffsets.clone();
        accent=color;
        if(noteIcon!=null)noteIcon.setTint(color);
        if(bookmarkIcon!=null)bookmarkIcon.setTint(color);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if((notes.length==0&&bookmarks.length==0)||getLayout()==null)return;
        Layout layout=getLayout();
        Map<Integer,Integer> notesPerLine=new HashMap<>();
        Map<Integer,Integer> marksPerLine=new HashMap<>();
        int length=getText()==null?0:getText().length();if(length<=0)return;
        for(int offset:notes){
            int line=layout.getLineForOffset(Math.max(0,Math.min(length-1,offset-1)));
            notesPerLine.put(line,notesPerLine.containsKey(line)?notesPerLine.get(line)+1:1);
        }
        for(int offset:bookmarks){
            int line=layout.getLineForOffset(Math.max(0,Math.min(length-1,offset)));
            marksPerLine.put(line,marksPerLine.containsKey(line)?marksPerLine.get(line)+1:1);
        }
        int save=canvas.save();
        // This clip lies inside the actual View, not the shifted/clipped TextLayout.
        canvas.clipRect(0,0,getTotalPaddingLeft(),getHeight());
        for(int line=0;line<layout.getLineCount();line++){
            int n=notesPerLine.containsKey(line)?notesPerLine.get(line):0;
            int b=marksPerLine.containsKey(line)?marksPerLine.get(line):0;
            if(n==0&&b==0)continue;
            int height=layout.getLineBottom(line)-layout.getLineTop(line);
            int top=getTotalPaddingTop()+layout.getLineTop(line)+Math.max(0,(height-markSize)/2);
            int x=Math.max(gutterInset,(getTotalPaddingLeft()-markSize)/2);
            if(n>0&&b>0){
                // Two readable symbols inside the SAME 24dp gutter: a compact paired mark.
                paintIcon(canvas,noteIcon,x,top,Math.round(markSize*.78f));
                paintIcon(canvas,bookmarkIcon,x+Math.round(markSize*.58f),top+Math.round(markSize*.48f),Math.round(markSize*.55f));
            }else if(n>0)paintIcon(canvas,noteIcon,x,top,markSize);
            else paintIcon(canvas,bookmarkIcon,x,top,markSize);
            if(n>1){
                float density=getResources().getDisplayMetrics().density;
                int radius=Math.round(5*density);
                float cx=Math.min(getTotalPaddingLeft()-radius-1,x+markSize-radius/2f),cy=top+radius;
                badgePaint.setColor(accent);canvas.drawCircle(cx,cy,radius,badgePaint);
                int luma=(Color.red(accent)*299+Color.green(accent)*587+Color.blue(accent)*114)/1000;
                badgePaint.setColor(luma>155?Color.BLACK:Color.WHITE);
                badgePaint.setTextAlign(Paint.Align.CENTER);badgePaint.setTextSize(8*density);
                canvas.drawText(n>9?"9+":String.valueOf(n),cx,cy+3*density,badgePaint);
            }
        }
        canvas.restoreToCount(save);
    }
    private void paintIcon(Canvas canvas,Drawable icon,int x,int y,int size){
        if(icon==null||size<=0)return;
        icon.setBounds(x,y,x+size,y+size);
        icon.draw(canvas);
    }
}
