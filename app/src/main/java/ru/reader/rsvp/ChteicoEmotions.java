package ru.reader.rsvp;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.Random;

/** Shared emotions and stroke gesture for Chteico and the home companion. */
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
    interface Drag { void start(View view); }
    static void attach(View view,Runnable tap,Runnable longTap,Runnable pet,Drag drag){
        final float slop=ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
        view.setClickable(true);
        view.setOnTouchListener(new View.OnTouchListener(){
            float x,y;boolean moved,dragged;
            private void vibrate(View v){v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);}
            @Override public boolean onTouch(View v,MotionEvent e){
                switch(e.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        x=e.getX();y=e.getY();moved=false;dragged=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        float dist=(float)Math.hypot(e.getX()-x,e.getY()-y);
                        if(!moved&&dist>slop){
                            moved=true;
                            vibrate(v);
                            if(pet!=null)pet.run(); // Every direction triggers an emotion.
                            if(drag!=null){dragged=true;drag.start(v);} // No hold or timeout.
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(!moved){v.performClick();vibrate(v);if(tap!=null)tap.run();}
                        return true;
                    case MotionEvent.ACTION_CANCEL:return true;
                    default:return true;
                }
            }
        });
    }
}
