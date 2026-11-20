package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

/** Reader-owned, accent-tinted teardrop selection handles. Screen positions are book-wide UTF-16 carets. */
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
    private final Path teardrop=new Path();
    private int active=-1,pointerId=-1;
    private float lastX,lastY;
    private boolean pendingFrame;
    private final float touchRadius;
    ReaderSelectionController(Context context,Host target){super(context);host=target;
        touchRadius=32f*context.getResources().getDisplayMetrics().density;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float[][] positions=host.handlePositions();if(positions==null)return;
        float d=getResources().getDisplayMetrics().density;
        paint.setColor(host.handleColor());paint.setStyle(Paint.Style.FILL);
        for(int i=0;i<2;i++){
            float[] p=positions[i];if(p==null)continue;
            float x=p[0],y=p[1],side=i==0?-1f:1f;
            // Same teardrop silhouette as a modern Android selection grip:
            // the sharp upper corner stays attached to the exact caret.
            teardrop.reset();teardrop.moveTo(x,y);
            teardrop.lineTo(x+side*11*d,y);
            teardrop.cubicTo(x+side*22*d,y,x+side*23*d,y+12*d,x+side*14*d,y+18*d);
            teardrop.cubicTo(x+side*6*d,y+22*d,x+side*1*d,y+16*d,x,y+8*d);
            teardrop.close();canvas.drawPath(teardrop,paint);
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
                float d=getResources().getDisplayMetrics().density;
                float gripX=positions[i][0]+(i==0?-11f:11f)*d;
                float dx=event.getX()-gripX,dy=event.getY()-(positions[i][1]+10*d);
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
