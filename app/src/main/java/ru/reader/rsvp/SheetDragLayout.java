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
    private float movement;
    private long started;
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
                startX=event.getRawX();startY=event.getRawY();intercepting=false;movement=0f;
                started=android.os.SystemClock.uptimeMillis();
                return false;
            case MotionEvent.ACTION_MOVE:
                float dy=event.getRawY()-startY;
                float dx=event.getRawX()-startX;
                if(dialog!=null&&((fromTop&&dy<0)||(!fromTop&&dy>0))
                        &&Math.abs(dy)>density*24&&Math.abs(dy)>Math.abs(dx)*1.35f
                        &&!scrollableUnderFinger(this,fromTop?1:-1,startX,startY)){
                    intercepting=true;
                    return true;
                }
                return false;
            default:return false;
        }
    }
    private void moveWindow(float distance){
        if(dialog==null||dialog.getWindow()==null)return;
        View decor=dialog.getWindow().getDecorView();
        decor.animate().cancel();decor.setTranslationY(distance);
    }
    private void settle(boolean dismiss){
        if(dialog==null||dialog.getWindow()==null)return;
        View decor=dialog.getWindow().getDecorView();
        if(dismiss){
            float target=(fromTop?-1f:1f)*Math.max(decor.getHeight(),density*200);
            decor.animate().translationY(target).setDuration(155).withEndAction(()->{
                if(dialog.isShowing())dialog.dismiss();
            }).start();
        }else decor.animate().translationY(0f).setDuration(170).start();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!intercepting)return super.onTouchEvent(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:
                float dy=event.getRawY()-startY;
                movement=fromTop?Math.min(0,dy):Math.max(0,dy);
                moveWindow(movement);
                return true;
            case MotionEvent.ACTION_UP:
                long elapsed=Math.max(1,android.os.SystemClock.uptimeMillis()-started);
                float velocity=movement*1000f/elapsed;
                boolean dismiss=(fromTop?-movement:movement)>Math.max(density*68,getHeight()*.28f)
                    ||(fromTop?-velocity:velocity)>density*700;
                settle(dismiss);intercepting=false;return true;
            case MotionEvent.ACTION_CANCEL:
                settle(false);intercepting=false;return true;
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
