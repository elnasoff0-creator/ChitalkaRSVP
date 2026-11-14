package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

/** Drag handles tied to immutable word indexes. Coordinates are re-read after every scroll frame. */
final class ReaderSelectionController extends View {
    interface Host {
        float[][] handlePositions();
        /** Returns the new START/END role; dragging across the opposite end swaps roles. */
        boolean dragHandle(boolean start,float x,float y);
        /** Scrolls only while the finger is held close to the visible text edge. */
        boolean autoScrollForHandle(boolean start,float x,float y);
        void startedHandleDrag();
        void finishedHandleDrag(boolean canceled);
        int handleColor();
    }
    private final Host host;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int active=-1,pointerId=-1;
    private float lastX,lastY;
    private boolean pendingFrame;
    private final float touchRadius;
    ReaderSelectionController(Context context,Host target){super(context);host=target;
        touchRadius=32f*context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
        float[][] positions=host.handlePositions();if(positions==null)return;
        float d=getResources().getDisplayMetrics().density;
        paint.setColor(host.handleColor());paint.setStrokeWidth(2.5f*d);paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<2;i++){float[] p=positions[i];if(p==null)continue;
            canvas.drawLine(p[0],p[1]-7*d,p[0],p[1]+3*d,paint);
            paint.setStyle(Paint.Style.FILL);canvas.drawCircle(p[0],p[1]+9*d,6*d,paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }
    private final Runnable scrollFrame=new Runnable(){@Override public void run(){
        pendingFrame=false;if(active<0||pointerId<0||!isAttachedToWindow())return;
        boolean start=active==0;
        if(host.autoScrollForHandle(start,lastX,lastY)){
            active=host.dragHandle(start,lastX,lastY)?0:1;
            invalidate();scheduleFrame();
        }
    }};
    private void scheduleFrame(){if(!pendingFrame&&active>=0){pendingFrame=true;postOnAnimation(scrollFrame);}}
    private void stopFrame(){pendingFrame=false;removeCallbacks(scrollFrame);}
    private void finish(boolean canceled){
        stopFrame();active=-1;pointerId=-1;invalidate();
        ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);
        host.finishedHandleDrag(canceled);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            active=-1;pointerId=-1;float[][] positions=host.handlePositions();if(positions==null)return false;
            float best=touchRadius*touchRadius;
            for(int i=0;i<2;i++)if(positions[i]!=null){
                float dx=event.getX()-positions[i][0],dy=event.getY()-(positions[i][1]+9*getResources().getDisplayMetrics().density);
                float squared=dx*dx+dy*dy;
                if(squared<=best){best=squared;active=i;}
            }
            if(active<0)return false;
            pointerId=event.getPointerId(0);lastX=event.getX();lastY=event.getY();
            ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);
            host.startedHandleDrag();return true;
        }
        if(active<0)return false;
        if(action==MotionEvent.ACTION_MOVE){
            int position=event.findPointerIndex(pointerId);
            if(position<0){finish(true);return true;}
            lastX=event.getX(position);lastY=event.getY(position);
            active=host.dragHandle(active==0,lastX,lastY)?0:1;
            scheduleFrame();invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_UP&&event.getPointerId(event.getActionIndex())==pointerId){finish(true);return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){finish(action==MotionEvent.ACTION_CANCEL);return true;}
        return true;
    }
    @Override protected void onDetachedFromWindow(){stopFrame();active=-1;pointerId=-1;super.onDetachedFromWindow();}
}
