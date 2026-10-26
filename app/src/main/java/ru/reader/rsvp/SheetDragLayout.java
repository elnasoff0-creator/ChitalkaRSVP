package ru.reader.rsvp;

import android.app.Dialog;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/** A single direction-aware dismissal gesture for top and bottom sheet content.
 * Clicks and text controls stay responsive; a scrolling child gets first refusal. */
public final class SheetDragLayout extends LinearLayout {
    private Dialog dialog;
    private boolean fromTop;
    private float startX,startY;
    private boolean intercepting;
    private final float density;

    public SheetDragLayout(Context context){
        super(context);
        density=context.getResources().getDisplayMetrics().density;
        setOrientation(VERTICAL);
    }
    public void attach(Dialog dialog,boolean fromTop){this.dialog=dialog;this.fromTop=fromTop;}

    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                startX=event.getRawX();startY=event.getRawY();intercepting=false;
                return false;
            case MotionEvent.ACTION_MOVE:
                float dy=event.getRawY()-startY;
                float dx=event.getRawX()-startX;
                if(dialog!=null&&((fromTop&&dy<0)||(!fromTop&&dy>0))
                        &&Math.abs(dy)>density*24&&Math.abs(dy)>Math.abs(dx)*1.35f
                        &&!scrollableUnderFinger(this,fromTop?1:-1,event.getRawX(),event.getRawY())){
                    intercepting=true;
                    return true;
                }
                return false;
            default:return false;
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!intercepting)return super.onTouchEvent(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                float dy=event.getRawY()-startY;
                if((fromTop?-dy:dy)>density*52){
                    if(dialog!=null&&dialog.isShowing())dialog.dismiss();
                    intercepting=false;
                }
                return true;
            case MotionEvent.ACTION_CANCEL:intercepting=false;return true;
            default:return true;
        }
    }
    /** Only the scrollable child actually touched may block a swipe-to-dismiss.
     * A chapter list must not disable swipes started on the header/drag handle. */
    private static boolean scrollableUnderFinger(View v,int direction,float rawX,float rawY){
        if(v.getVisibility()!=View.VISIBLE)return false;
        int[] location=new int[2];
        v.getLocationOnScreen(location);
        if(rawX<location[0]||rawX>=location[0]+v.getWidth()
                ||rawY<location[1]||rawY>=location[1]+v.getHeight())return false;
        if(v instanceof ViewGroup){
            ViewGroup group=(ViewGroup)v;
            for(int i=group.getChildCount()-1;i>=0;i--)
                if(scrollableUnderFinger(group.getChildAt(i),direction,rawX,rawY))return true;
        }
        return v.canScrollVertically(direction);
    }
}
