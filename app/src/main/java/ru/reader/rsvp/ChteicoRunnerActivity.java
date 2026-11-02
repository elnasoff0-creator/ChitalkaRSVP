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
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

/** Retro outlined runner: transparent-aware assets, baseline-aligned sprites and fair hitboxes. */
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
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG),fontPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random=new Random();private final Bitmap[] frames=new Bitmap[3],books=new Bitmap[5];
        private final Rect[] frameVisible=new Rect[3], bookVisible=new Rect[5];
        private final ArrayList<Obstacle> obstacles=new ArrayList<>();
        private long last=0;private float velocity=0,jump=0,distance=0,spawn=0;
        private boolean running=true,airborne=false,awarded=false,paused=false,holding=false;
        private float holdSeconds=0f,ground,playerSize;
        private int background,ink,stage,frame=0;
        // Height is visual height after removing transparent padding; width respects sprite aspect.
        private final float[] bookHeights={80f,34f,69f,38f,43f};
        private class Obstacle{float x,w,h;int image;Obstacle(float x,float w,float h,int i){this.x=x;this.w=w;this.h=h;image=i;}}
        Runner(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            background=0xfffff9f2;ink=0xff30221e;
            stage=Math.max(1,ChteicoStore.stage(c));int[] ids;
            if(stage<=1){ids=new int[]{R.drawable.chteico_game_chick_run1,R.drawable.chteico_game_chick_run2,R.drawable.chteico_game_chick_jump};}
            else if(stage==2){ids=new int[]{R.drawable.chteico_game_reader_run1,R.drawable.chteico_game_reader_run2,R.drawable.chteico_game_reader_jump};}
            else{ids=new int[]{R.drawable.chteico_game_adult_run1,R.drawable.chteico_game_adult_run2,R.drawable.chteico_game_adult_jump};}
            for(int i=0;i<3;i++){
                frames[i]=BitmapFactory.decodeResource(getResources(),ids[i]);
                frameVisible[i]=visibleBounds(frames[i]);
            }
            int[] booksIds={R.drawable.chteico_book_1,R.drawable.chteico_book_2,R.drawable.chteico_book_3,R.drawable.chteico_book_4,R.drawable.chteico_book_5};
            for(int i=0;i<5;i++){
                books[i]=BitmapFactory.decodeResource(getResources(),booksIds[i]);
                bookVisible[i]=visibleBounds(books[i]);
            }
            paint.setFilterBitmap(false);fontPaint.setTypeface(android.graphics.Typeface.MONOSPACE);fontPaint.setColor(ink);
            fontPaint.setTextSize(17*getResources().getDisplayMetrics().scaledDensity);
        }
        /** Measure actual alpha bounds, not the original 64x64 bitmap rectangle. */
        private Rect visibleBounds(Bitmap bmp){
            if(bmp==null)return new Rect(0,0,1,1);
            int left=bmp.getWidth(),top=bmp.getHeight(),right=0,bottom=0;
            for(int y=0;y<bmp.getHeight();y++)for(int x=0;x<bmp.getWidth();x++){
                if(Color.alpha(bmp.getPixel(x,y))<=16)continue;
                if(x<left)left=x;if(x+1>right)right=x+1;
                if(y<top)top=y;if(y+1>bottom)bottom=y+1;
            }
            return right>left&&bottom>top?new Rect(left,top,right,bottom):new Rect(0,0,bmp.getWidth(),bmp.getHeight());
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
        private RectF playerRect(float left,float feet,int pose){
            Bitmap bmp=frames[pose];float height=playerSize;
            float width=bmp==null?height:height*bmp.getWidth()/bmp.getHeight();
            // Same bottom/center anchor for all animation frames of this evolution.
            return new RectF(left,feet-height,left+width,feet);
        }
        private RunnerHitboxes.Box alphaWorld(Bitmap bmp,Rect visible,RectF dest){
            if(bmp==null)return new RunnerHitboxes.Box(dest.left,dest.top,dest.right,dest.bottom);
            float sx=dest.width()/bmp.getWidth(),sy=dest.height()/bmp.getHeight();
            return new RunnerHitboxes.Box(dest.left+visible.left*sx,dest.top+visible.top*sy,
                    dest.left+visible.right*sx,dest.top+visible.bottom*sy);
        }
        private RunnerHitboxes.Box petHitbox(RectF rect,int pose){
            return RunnerHitboxes.pet(alphaWorld(frames[pose],frameVisible[pose],rect));
        }
        private RunnerHitboxes.Box obstacleHitbox(Obstacle b){
            return RunnerHitboxes.book(alphaWorld(books[b.image],bookVisible[b.image],
                    new RectF(b.x,ground-b.h,b.x+b.w,ground)));
        }
        @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(background);
            long now=SystemClock.elapsedRealtime();float dt=(paused||last==0)?0:Math.min(.045f,(now-last)/1000f);last=paused?0:now;
            ground=getHeight()*.84f;playerSize=dp(stage==1?72:stage==2?88:108);
            float px=dp(38);
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
                if(spawn<=0){int kind=random.nextInt(books.length);
                    Bitmap bmp=books[kind];float h=dp(bookHeights[kind]);
                    float w=bmp==null?h:h*bmp.getWidth()/bmp.getHeight();
                    obstacles.add(new Obstacle(getWidth()+w,w,h,kind));spawn=dp(225+random.nextInt(160));}
                int pose=airborne?2:frame;
                RunnerHitboxes.Box body=petHitbox(playerRect(px,ground-jump,pose),pose);
                for(int i=obstacles.size()-1;i>=0;i--){Obstacle b=obstacles.get(i);b.x-=speed*dt;
                    if(b.x+b.w<0){obstacles.remove(i);continue;}
                    if(body.intersects(obstacleHitbox(b))){running=false;reward();}
                }
            }
            paint.setColor(ink);paint.setStrokeWidth(dp(2));c.drawLine(0,ground,getWidth(),ground,paint);
            for(Obstacle b:obstacles){Bitmap bitmap=books[b.image];if(bitmap!=null){paint.setColor(Color.WHITE);
                c.drawBitmap(bitmap,null,new RectF(b.x,ground-b.h,b.x+b.w,ground),paint);}}
            int pose=airborne?2:frame;Bitmap sprite=frames[pose];
            if(sprite!=null){paint.setColor(Color.WHITE);
                c.drawBitmap(sprite,null,playerRect(px,ground-jump,pose),paint);}
            fontPaint.setColor(ink);fontPaint.setTextSize(dp(16));c.drawText("ПУТЬ "+(int)distance,dp(14),dp(40),fontPaint);
            c.drawText("РЕКОРД "+ChteicoStore.prefs(getContext()).getInt("runner_best",0),dp(14),dp(65),fontPaint);
            if(paused){fontPaint.setTextSize(dp(19));c.drawText("ПАУЗА",dp(14),getHeight()-dp(18),fontPaint);}
            else if(!running){fontPaint.setTextSize(dp(18));c.drawText("СТОЛКНОВЕНИЕ · ТАП — СНОВА",dp(10),getHeight()-dp(18),fontPaint);}
            else postInvalidateDelayed(16);
        }
    }
}
