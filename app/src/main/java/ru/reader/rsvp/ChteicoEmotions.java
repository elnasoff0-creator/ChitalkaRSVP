package ru.reader.rsvp;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.Random;

/** Tap opens; system-duration long press starts drag; horizontal-only swipe reacts; vertical gestures scroll. */
final class ChteicoEmotions {
    private static final String[] FACES={"(~‿^)","(♡‿♡)","(＾▽＾)","(◕‿◕)"};
    private static final Random RANDOM=new Random();
    private static boolean sleeping=true;
    private static long lastInteraction=SystemClock.elapsedRealtime();
    private ChteicoEmotions(){}
    static boolean isSleeping(){return sleeping||SystemClock.elapsedRealtime()-lastInteraction>20L*60*1000;}
    static void wake(){sleeping=false;lastInteraction=SystemClock.elapsedRealtime();}
    static String stroke(){wake();return FACES[RANDOM.nextInt(FACES.length)];}
    static String sleep(int phase){return "( ◡‿◡ ) Zzz"+new String[]{".","..","..."}[Math.abs(phase)%3];}
    static boolean isFace(String text){return text!=null&&text.startsWith("(")&&text.endsWith(")")&&text.length()<=16;}
    interface Drag { void start(View view); }
    static void attach(View view,Runnable tap,Runnable longTap,Runnable pet,Drag drag){
        final int slop=ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        final int longMs=ViewConfiguration.getLongPressTimeout();
        view.setClickable(true);
        view.setOnTouchListener(new View.OnTouchListener(){
            float x,y;boolean moved,dragging,down,horizontal,vertical;
            final Runnable triggerLong=()->{
                if(!down||moved||dragging)return;
                dragging=true;
                if(drag!=null){
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    drag.start(view);
                }else if(longTap!=null)longTap.run();
            };
            @Override public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        x=e.getX();y=e.getY();moved=false;dragging=false;down=true;horizontal=false;vertical=false;
                        // Allow the containing ScrollView to intercept vertical drags.
                        v.removeCallbacks(triggerLong);
                        if(drag!=null||longTap!=null)v.postDelayed(triggerLong,longMs);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if(!down)return true;
                        float dx=e.getX()-x,dy=e.getY()-y;
                        if(!moved&&Math.hypot(dx,dy)>slop){
                            moved=true;v.removeCallbacks(triggerLong);
                            horizontal=Math.abs(dx)>Math.abs(dy)*1.25f;
                            vertical=!horizontal;
                            if(horizontal&&v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        return !vertical;
                    case MotionEvent.ACTION_UP:
                        v.removeCallbacks(triggerLong);down=false;
                        if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);
                        if(dragging)return true;
                        if(horizontal){if(pet!=null)pet.run();}
                        else {v.performClick();if(tap!=null)tap.run();}
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.removeCallbacks(triggerLong);down=false;
                        if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);
                        return true;
                    default:return true;
                }
            }
        });
    }
}
