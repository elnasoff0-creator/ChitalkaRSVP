package ru.reader.rsvp;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

/** Retro outlined runner: two running frames and one static airborne frame per evolution. */
public final class ChteicoRunnerActivity extends Activity {
    private Runner runner;
    private TextView pauseControl;
    @Override public void onCreate(Bundle state){super.onCreate(state);
        int bg=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
        int fg=getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(13),dp(20),dp(13),dp(12));page.setBackgroundColor(bg);
        setContentView(page);SafeSystemBars.matchBackground(this,bg);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);page.addView(toolbar);
        TextView back=new TextView(this);back.setText("‹");back.setTextSize(28);back.setTextColor(fg);back.setGravity(Gravity.CENTER);
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));back.setOnClickListener(v->finish());
        TextView title=new TextView(this);title.setText("Прыжки через книги");title.setTextSize(19);title.setTypeface(null,1);title.setTextColor(fg);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView pause=new TextView(this);pauseControl=pause;pause.setText("Ⅱ");pause.setTextSize(22);pause.setTextColor(fg);pause.setGravity(Gravity.CENTER);
        toolbar.addView(pause,new LinearLayout.LayoutParams(dp(44),dp(44)));
        runner=new Runner(this);
        GradientDrawable card=new GradientDrawable();card.setColor(0xfffff9f2);card.setCornerRadius(dp(18));
        runner.setBackground(card);runner.setClipToOutline(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(260));lp.topMargin=dp(18);
        page.addView(runner,lp);
        pause.setOnClickListener(v->{runner.togglePause();pause.setText(runner.paused?"▶":"Ⅱ");});
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onPause(){if(runner!=null)runner.pauseClock();if(pauseControl!=null)pauseControl.setText("▶");super.onPause();}
    @Override protected void onResume(){super.onResume();if(runner!=null)runner.resumeClock();}
    private final class Runner extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),fontPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random=new Random();private final Bitmap[] frames=new Bitmap[3],books=new Bitmap[5];
        private final ArrayList<Obstacle> obstacles=new ArrayList<>();private long last=0;private float velocity=0,jump=0,distance=0,spawn=0;
        private boolean running=true,airborne=false,awarded=false,paused=false,holding=false;
        private float holdSeconds=0f,ground,playerSize;
        private int background,ink,stage,frame=0;
        private class Obstacle{float x,w,h;int image;Obstacle(float x,float w,float h,int i){this.x=x;this.w=w;this.h=h;image=i;}}
        Runner(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            background=0xfffff9f2;ink=0xff30221e;
            stage=Math.max(1,ChteicoStore.stage(c));int[] one,two,jmp;
            if(stage<=1){one=new int[]{R.drawable.chteico_game_chick_run1,R.drawable.chteico_game_chick_run2,R.drawable.chteico_game_chick_jump};}
            else if(stage==2){one=new int[]{R.drawable.chteico_game_reader_run1,R.drawable.chteico_game_reader_run2,R.drawable.chteico_game_reader_jump};}
            else{one=new int[]{R.drawable.chteico_game_adult_run1,R.drawable.chteico_game_adult_run2,R.drawable.chteico_game_adult_jump};}
            for(int i=0;i<3;i++)frames[i]=BitmapFactory.decodeResource(getResources(),one[i]);
            int[] ids={R.drawable.chteico_book_1,R.drawable.chteico_book_2,R.drawable.chteico_book_3,R.drawable.chteico_book_4,R.drawable.chteico_book_5};
            for(int i=0;i<5;i++)books[i]=BitmapFactory.decodeResource(getResources(),ids[i]);
            paint.setFilterBitmap(false);fontPaint.setTypeface(android.graphics.Typeface.MONOSPACE);fontPaint.setColor(ink);fontPaint.setTextSize(17*getResources().getDisplayMetrics().scaledDensity);
        }
        void pauseClock(){paused=true;holding=false;last=0;}
        void resumeClock(){last=0;invalidate();}
        void togglePause(){paused=!paused;if(paused)holding=false;last=0;invalidate();}
        private float dp(float n){return n*getResources().getDisplayMetrics().density;}
        private void restart(){running=true;awarded=false;airborne=false;holding=false;holdSeconds=0;
            distance=0;spawn=dp(230);velocity=0;jump=0;obstacles.clear();last=0;invalidate();}
        private void jump(){if(!airborne){velocity=dp((float)Math.sqrt(2f*1365f*30f));airborne=true;holding=true;holdSeconds=0;}}
        @Override public boolean onTouchEvent(MotionEvent event){
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    if(!running)restart();
                    else if(!paused)jump();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:holding=false;return true;
                default:return true;
            }
        }
        private void reward(){if(awarded)return;awarded=true;int xp=Math.max(0,(int)(distance/120));
            ChteicoStore.addGameExperience(getContext(),xp,distance);
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(background);
            long now=SystemClock.elapsedRealtime();float dt=(paused||last==0)?0:Math.min(.045f,(now-last)/1000f);last=paused?0:now;
            ground=getHeight()*.84f;playerSize=dp(stage==1?72:stage==2?88:108);
            float px=dp(38),py=ground-playerSize-jump;
            if(running&&!paused){float speed=dp(190+Math.min(240,distance/10));distance+=speed*dt/dp(1);
                frame=((int)(distance/21))%2;
                if(airborne){
                    float lift=0;
                    if(holding&&holdSeconds<.240f){float active=Math.min(dt,.240f-holdSeconds);holdSeconds+=active;lift=dp(1254)*active;}
                    velocity+=lift-dp(1365)*dt;
                    jump+=velocity*dt;
                    if(jump>=dp(90)){jump=dp(90);velocity=Math.min(0,velocity);holding=false;}
                    if(jump<=0){jump=0;velocity=0;airborne=false;holding=false;}
                }
                spawn-=speed*dt;
                if(spawn<=0){int t=random.nextInt(books.length);float h=dp(32+random.nextInt(49));float w=dp(28+random.nextInt(25));
                    obstacles.add(new Obstacle(getWidth()+w,w,h,t));spawn=dp(225+random.nextInt(160));}
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
            if(paused){fontPaint.setTextSize(dp(19));c.drawText("ПАУЗА",dp(14),getHeight()-dp(18),fontPaint);}
            else if(!running){fontPaint.setTextSize(dp(18));c.drawText("СТОЛКНОВЕНИЕ · ТАП — СНОВА",dp(10),getHeight()-dp(18),fontPaint);}
            else postInvalidateDelayed(16);
        }
    }
}
