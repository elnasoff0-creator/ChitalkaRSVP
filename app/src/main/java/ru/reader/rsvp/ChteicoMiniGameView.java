package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Random;

/** Two vector-only games embedded in the existing pet card; never opens a second Activity.
 * Uses the same pet up/down VectorDrawable as ChteicoPetView and two XML books.
 */
public final class ChteicoMiniGameView extends View {
    public static final int RUNNER=1, FLYER=2;
    private static final float GRAVITY=1365f;
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panel=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random=new Random();
    private final ArrayList<Obstacle> obstacles=new ArrayList<>();
    private final Drawable[] petFrames=new Drawable[2];
    private final Drawable openBook,standingBook;
    private final int mode,stage;
    private final Runnable exit;
    private final float density;
    private float distance=0,spawn=90,playerY=0,velocity=0,holdTime=0,ground;
    private long last=0;
    private boolean airborne=false,holding=false,paused=false,alive=true,rewarded=false;
    private int frame=0;
    private static final class Obstacle {
        float x,w,h,gapTop,gapBottom;int kind;
        Obstacle(float x,float w,float h,int kind){this.x=x;this.w=w;this.h=h;this.kind=kind;}
    }
    public ChteicoMiniGameView(Context context,int gameMode,Runnable onExit){
        super(context);mode=gameMode;exit=onExit;density=getResources().getDisplayMetrics().density;
        stage=ChteicoStore.stage(context);
        int branch=ChteicoStore.branch(context);
        petFrames[0]=getResources().getDrawable(ChteicoPetView.gameFrameResource(stage,branch,false),context.getTheme()).mutate();
        petFrames[1]=getResources().getDrawable(ChteicoPetView.gameFrameResource(stage,branch,true),context.getTheme()).mutate();
        openBook=getResources().getDrawable(R.drawable.ic_game_book_open,context.getTheme()).mutate();
        standingBook=getResources().getDrawable(R.drawable.ic_game_book_upright,context.getTheme()).mutate();
        ink.setColor(0xff30221e);ink.setTypeface(Typeface.DEFAULT_BOLD);ink.setTextSize(dp(13));
        panel.setColor(0xfff5e9dd);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        setContentDescription(mode==RUNNER?"Бегун: тап для прыжка, слева пауза, справа выход":
                "Летун: тап для взмаха, слева пауза, справа выход");
        setClickable(true);
    }
    private float dp(float n){return n*density;}
    public int gameMode(){return mode;}
    public void pauseClock(){paused=true;holding=false;last=0;invalidate();}
    public void stopClock(){paused=true;last=0;obstacles.clear();}
    private void reset(){distance=0;spawn=dp(130);playerY=0;velocity=0;holdTime=0;
        airborne=false;holding=false;paused=false;alive=true;rewarded=false;last=0;frame=0;obstacles.clear();
        if(mode==FLYER)playerY=Math.max(dp(70),getHeight()*.50f);
        invalidate();}
    private void earn(){if(rewarded)return;rewarded=true;
        int xp=Math.max(0,Math.min(30,(int)(distance/120)));
        if(mode==FLYER)ChteicoStore.addFlyerGameExperience(getContext(),xp,distance);
        else ChteicoStore.addGameExperience(getContext(),xp,distance);
    }
    private void drawVector(Canvas c,Drawable drawable,float x,float y,float w,float h){
        drawable.setBounds(Math.round(x),Math.round(y),Math.round(x+w),Math.round(y+h));drawable.draw(c);
    }
    private float petSize(){return dp(stage<=1?56:stage==2?65:73);}
    private RectF petRect(){
        float side=petSize(),left=dp(41);
        if(mode==RUNNER)return new RectF(left,ground-playerY-side,left+side,ground-playerY);
        return new RectF(left,playerY-side*.5f,left+side,playerY+side*.5f);
    }
    private boolean hits(RectF a,RectF b){return a.left<b.right&&a.right>b.left&&a.top<b.bottom&&a.bottom>b.top;}
    private void spawn(){
        if(mode==RUNNER){boolean upright=random.nextBoolean();float w=dp(upright?40:55),h=dp(upright?58:31);
            obstacles.add(new Obstacle(getWidth()+dp(15),w,h,upright?1:0));
            spawn=dp(185+random.nextInt(95));
        }else{
            float width=dp(42),gap=Math.min(getHeight()*.46f,dp(150));
            float center=getHeight()*(.40f+random.nextFloat()*.23f);
            Obstacle b=new Obstacle(getWidth()+dp(16),width,0,1);
            b.gapTop=Math.max(dp(58),center-gap/2f);
            b.gapBottom=Math.min(getHeight()-dp(20),center+gap/2f);
            obstacles.add(b);
            spawn=dp(195+random.nextInt(45));
        }
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        int w=getWidth(),h=getHeight();if(w<1||h<1)return;
        c.drawColor(0xfffff9f2);
        float dt=0;long now=SystemClock.elapsedRealtime();
        if(last!=0&&!paused&&alive)dt=Math.max(0,Math.min(.045f,(now-last)/1000f));
        last=paused?0:now;
        ground=h-dp(29);
        if(mode==FLYER&&playerY==0)playerY=h*.50f;
        float speed=dp(mode==RUNNER?185+Math.min(75,distance/30):146+Math.min(90,distance/25));
        if(alive&&!paused){
            distance+=speed*dt/density;
            if(mode==RUNNER){
                if(airborne){
                    if(holding&&holdTime<.23f){float active=Math.min(dt,.23f-holdTime);holdTime+=active;
                        velocity+=dp(1250)*active;}
                    velocity-=dp(GRAVITY)*dt;
                    playerY+=velocity*dt;
                    if(playerY>dp(93)){playerY=dp(93);velocity=Math.min(0,velocity);holding=false;}
                    if(playerY<=0){playerY=0;velocity=0;airborne=false;holding=false;}
                }
            }else{
                velocity+=dp(660)*dt;playerY+=velocity*dt;
                if(playerY<dp(52)||playerY+petSize()*.3f>ground){alive=false;earn();}
            }
            spawn-=speed*dt;if(spawn<=0)spawn();
            for(int i=obstacles.size()-1;i>=0;i--){Obstacle obstacle=obstacles.get(i);obstacle.x-=speed*dt;
                if(obstacle.x+obstacle.w<0){obstacles.remove(i);continue;}
                RectF a=petRect();a.inset(petSize()*.20f,petSize()*.18f);
                if(mode==RUNNER){
                    RectF b=new RectF(obstacle.x,ground-obstacle.h,obstacle.x+obstacle.w,ground);
                    b.inset(dp(5),dp(4));
                    if(hits(a,b)){alive=false;earn();}
                }else{
                    RectF top=new RectF(obstacle.x,0,obstacle.x+obstacle.w,obstacle.gapTop);
                    RectF bottom=new RectF(obstacle.x,obstacle.gapBottom,obstacle.x+obstacle.w,h);
                    if(hits(a,top)||hits(a,bottom)){alive=false;earn();}
                }
            }
            frame=(int)(distance/19)%2;
        }
        // Ground and vector obstacles share the pet's outline palette.
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeWidth(dp(2));
        if(mode==RUNNER)c.drawLine(0,ground,w,ground,ink);
        ink.setStyle(Paint.Style.FILL);
        for(Obstacle b:obstacles){
            if(mode==RUNNER)drawVector(c,b.kind==0?openBook:standingBook,b.x,ground-b.h,b.w,b.h);
            else{
                // One standing-book XML is stretched vertically to form both columns.
                drawVector(c,standingBook,b.x,-dp(10),b.w,b.gapTop+dp(15));
                drawVector(c,standingBook,b.x,b.gapBottom,b.w,h-b.gapBottom+dp(10));
            }
        }
        RectF pet=petRect();Drawable petSprite=petFrames[(mode==RUNNER?(airborne?1:frame):velocity<0?1:0)];
        drawVector(c,petSprite,pet.left,pet.top,pet.width(),pet.height());
        // Small HUD lives in the same game container, no separate activity toolbar.
        panel.setColor(0xfff0e6dc);c.drawRoundRect(new RectF(dp(7),dp(7),dp(43),dp(42)),dp(11),dp(11),panel);
        c.drawRoundRect(new RectF(w-dp(43),dp(7),w-dp(7),dp(42)),dp(11),dp(11),panel);
        ink.setTextSize(dp(21));ink.setColor(0xff30221e);
        c.drawText(paused?"▶":"Ⅱ",dp(17),dp(33),ink);
        c.drawText("×",w-dp(32),dp(33),ink);
        ink.setTextSize(dp(13));
        int score=(int)distance;
        c.drawText(""+score+" м",w*.47f,dp(27),ink);
        if(paused||!alive){
            panel.setColor(0xccfff9f2);c.drawRoundRect(new RectF(dp(12),h*.39f,w-dp(12),h*.67f),dp(16),dp(16),panel);
            ink.setTextSize(dp(16));
            String msg=paused?"ПАУЗА":"СТОЛКНОВЕНИЕ";
            c.drawText(msg,dp(26),h*.50f,ink);
            ink.setTextSize(dp(12));c.drawText(paused?"Тап для продолжения":"Тап — повторить",dp(26),h*.59f,ink);
        }else postInvalidateDelayed(16);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            float x=e.getX(),y=e.getY();
            if(y<dp(48)&&x>getWidth()-dp(51)){earn();stopClock();if(exit!=null)exit.run();return true;}
            if(y<dp(48)&&x<dp(50)){paused=!paused;holding=false;last=0;invalidate();return true;}
            if(!alive){reset();return true;}
            if(paused){paused=false;last=0;invalidate();return true;}
            if(mode==RUNNER){if(!airborne){airborne=true;velocity=dp(285);holding=true;holdTime=0;}}
            else velocity=-dp(230);
            return true;
        }
        if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){holding=false;return true;}
        return true;
    }
}
