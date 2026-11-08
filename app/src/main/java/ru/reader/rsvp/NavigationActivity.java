package ru.reader.rsvp;

import android.app.Activity;
import android.view.MotionEvent;

/** Shared navigation host with an edge swipe that follows the finger. */
public class NavigationActivity extends Activity {
    private float drawerStartX,drawerStartY,drawerDistance;
    private boolean edgeCandidate,edgeDragging;
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                drawerStartX=event.getX();drawerStartY=event.getY();drawerDistance=0;
                edgeDragging=false;
                edgeCandidate=!UniversalDrawer.isOpen(this)&&drawerStartX<=dp(20);
                break;
            case MotionEvent.ACTION_MOVE:
                if(edgeDragging){
                    drawerDistance=Math.max(0,event.getX()-drawerStartX);
                    UniversalDrawer.dragOpen(this,drawerDistance);
                    return true;
                }
                if(edgeCandidate){
                    float dx=event.getX()-drawerStartX,dy=event.getY()-drawerStartY;
                    if(Math.abs(dy)>dp(18)&&Math.abs(dy)>Math.abs(dx)){edgeCandidate=false;break;}
                    if(dx>dp(24)&&dx>Math.abs(dy)*1.45f){
                        edgeDragging=true;
                        MotionEvent cancel=MotionEvent.obtain(event);
                        cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel);cancel.recycle();
                        UniversalDrawer.showFromGesture(this);
                        drawerDistance=dx;
                        UniversalDrawer.dragOpen(this,dx);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if(edgeDragging){
                    UniversalDrawer.finishOpeningGesture(this,
                        event.getActionMasked()==MotionEvent.ACTION_CANCEL?0:Math.max(0,event.getX()-drawerStartX));
                    edgeDragging=false;edgeCandidate=false;
                    return true;
                }
                edgeCandidate=false;break;
        }
        return super.dispatchTouchEvent(event);
    }
    private int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    @Override protected void onDestroy(){UniversalDrawer.discard(this);super.onDestroy();}
    @Override public void onBackPressed(){
        if(UniversalDrawer.closeIfOpen(this))return;
        super.onBackPressed();
    }
}
