package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Lightweight transparent drag handles. The host owns TextView layout and word mapping. */
final class ReaderSelectionController extends View {
    interface Host {
        /** Returns two handle centres in this view's coordinate system, null when off-screen. */
        float[][] handlePositions();
        void dragHandle(boolean start,float x,float y);
        void finishedHandleDrag();
        int handleColor();
    }
    private final Host host;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int active=-1;
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
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            active=-1;float[][] pos=host.handlePositions();if(pos==null)return false;
            for(int i=0;i<2;i++)if(pos[i]!=null){float dx=e.getX()-pos[i][0],dy=e.getY()-(pos[i][1]+9*getResources().getDisplayMetrics().density);
                if(dx*dx+dy*dy<=touchRadius*touchRadius){active=i;return true;}}
            return false;
        }
        if(active<0)return false;
        if(action==MotionEvent.ACTION_MOVE){host.dragHandle(active==0,e.getX(),e.getY());invalidate();return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            boolean finished=action==MotionEvent.ACTION_UP;active=-1;invalidate();
            if(finished)host.finishedHandleDrag();return true;
        }
        return true;
    }
}
