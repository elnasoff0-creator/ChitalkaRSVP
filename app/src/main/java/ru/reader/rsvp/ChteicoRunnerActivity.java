package ru.reader.rsvp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

/** Retro outlined runner: two running frames and one static airborne frame per evolution. */
public final class ChteicoRunnerActivity extends Activity {
    private Runner runner;
    @Override public void onCreate(Bundle state){super.onCreate(state);runner=new Runner(this);setContentView(runner);}
    @Override protected void onPause(){runner.pauseClock();super.onPause();}
    @Override protected void onResume(){super.onResume();if(runner!=null)runner.resumeClock();}
    private final class Runner extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),fontPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random=new Random();private final Bitmap[] frames=new Bitmap[3],books=new Bitmap[5];
        private final ArrayList<Obstacle> obstacles=new ArrayList<>();private long last=0;private float velocity=0,jump=0,distance=0,spawn=0;
        private boolean running=true,airborne=false,awarded=false,paused=false;private float ground,playerSize,scale;
        private int background,ink,stage,frame=0;
        private class Obstacle{float x,w,h;int image;Obstacle(float x,float w,float h,int i){this.x=x;this.w=w;this.h=h;image=i;}}
        Runner(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            int bg=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
            background=bg;ink=ReadingThemeCycle.isDark(bg)?0xfff3e7d5:0xff30221e;
            stage=Math.max(1,ChteicoStore.stage(c));int[] one,two,jmp;
            if(stage<=1){one=new int[]{R.drawable.chteico_game_chick_run1,R.drawable.chteico_game_chick_run2,R.drawable.chteico_game_chick_jump};}
            else if(stage==2){one=new int[]{R.drawable.chteico_game_reader_run1,R.drawable.chteico_game_reader_run2,R.drawable.chteico_game_reader_jump};}
            else{one=new int[]{R.drawable.chteico_game_adult_run1,R.drawable.chteico_game_adult_run2,R.drawable.chteico_game_adult_jump};}
            for(int i=0;i<3;i++)frames[i]=BitmapFactory.decodeResource(getResources(),one[i]);
            int[] ids={R.drawable.chteico_book_1,R.drawable.chteico_book_2,R.drawable.chteico_book_3,R.drawable.chteico_book_4,R.drawable.chteico_book_5};
            for(int i=0;i<5;i++)books[i]=BitmapFactory.decodeResource(getResources(),ids[i]);
            paint.setFilterBitmap(false);fontPaint.setTypeface(android.graphics.Typeface.MONOSPACE);fontPaint.setColor(ink);fontPaint.setTextSize(17*getResources().getDisplayMetrics().scaledDensity);
        }
        void pauseClock(){paused=true;last=0;}
        void resumeClock(){paused=false;last=0;invalidate();}
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
        private void restart(){running=true;awarded=false;airborne=false;distance=0;spawn=0;velocity=0;jump=0;obstacles.clear();last=0;invalidate();}
        private void jump(){if(!airborne){velocity=dp(435);airborne=true;}}
        @Override public boolean onTouchEvent(MotionEvent event){if(event.getAction()!=MotionEvent.ACTION_DOWN)return true;
            if(!running)restart();else if(!paused)jump();return true;
        }
        private void reward(){if(awarded)return;awarded=true;int xp=Math.max(0,(int)(distance/120));
            ChteicoStore.addGameExperience(getContext(),xp,distance);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(background);
            if(paused){fontPaint.setTextSize(dp(18));c.drawText("Пауза",dp(16),dp(50),fontPaint);return;}
            long now=SystemClock.elapsedRealtime();float dt=last==0?0:Math.min(.045f,(now-last)/1000f);last=now;
            ground=getHeight()*.69f;playerSize=dp(stage==1?52:stage==2?65:80);scale=Math.max(1f,getWidth()/dp(400));
            float px=dp(38),py=ground-playerSize-jump;
            if(running){float speed=dp(190+Math.min(240,distance/10));distance+=speed*dt/dp(1);
                frame=((int)(distance/21))%2;
                if(airborne){jump+=velocity*dt;velocity-=dp(1050)*dt;if(jump<=0){jump=0;velocity=0;airborne=false;}}
                spawn-=speed*dt;
                if(spawn<=0){int t=random.nextInt(books.length);float h=dp(22+random.nextInt(34));float w=dp(24+random.nextInt(24));
                    obstacles.add(new Obstacle(getWidth()+w,w,h,t));spawn=dp(145+random.nextInt(145));}
                for(int i=obstacles.size()-1;i>=0;i--){Obstacle b=obstacles.get(i);b.x-=speed*dt;
                    if(b.x+b.w<0){obstacles.remove(i);continue;}
                    if(px+playerSize*.73f>b.x+dp(6)&&px+playerSize*.20f<b.x+b.w-dp(6)&&
                        ground-jump-playerSize*.14f>ground-b.h+dp(4)&&ground-jump-playerSize*.87f<ground){running=false;reward();}
                }
            }
            paint.setColor(ink);paint.setStrokeWidth(dp(2));c.drawLine(0,ground,getWidth(),ground,paint);
            for(Obstacle b:obstacles){Bitmap bitmap=books[b.image];if(bitmap!=null){paint.setColor(Color.WHITE);c.drawBitmap(bitmap,null,new RectF(b.x,ground-b.h,b.x+b.w,ground),paint);}}
            Bitmap sprite=frames[airborne?2:frame];if(sprite!=null){paint.setColor(Color.WHITE);c.drawBitmap(sprite,null,new RectF(px,ground-playerSize-jump,px+playerSize,ground-jump),paint);}
            fontPaint.setColor(ink);fontPaint.setTextSize(dp(16));c.drawText("ПУТЬ "+(int)distance,dp(14),dp(40),fontPaint);
            c.drawText("РЕКОРД "+ChteicoStore.prefs(getContext()).getInt("runner_best",0),dp(14),dp(65),fontPaint);
            if(!running){fontPaint.setTextSize(dp(20));c.drawText("СТОЛКНОВЕНИЕ!",dp(25),ground+dp(75),fontPaint);
                fontPaint.setTextSize(dp(16));c.drawText("Тап — играть снова · +"+Math.max(0,(int)(distance/120))+" XP",dp(20),ground+dp(106),fontPaint);
            }else postInvalidateDelayed(16);
        }
    }
}
