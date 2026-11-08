package ru.reader.rsvp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.view.View;
import java.util.Locale;
import java.util.Random;

/** Fishing lives entirely inside the original Chteico card. All art is XML VectorDrawable/shape. */
public final class ChteicoFishingView extends FrameLayout {
    private static final int READY=0, CASTING=1, FLOATING=2, REELING=3, CATCHING=4, RESULT=5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Random random=new Random();
    private final Runnable exit;
    private final TextView hud,status,shop;
    private final ImageView bobber,fish,zone,castMark,castCursor;
    private final FrameLayout meter,catchPanel,track;
    private final ProgressBar catchProgress;
    private final View close;
    private final int accent,ink;
    private int phase=READY,rarity=0;
    private boolean held=false,paused=false,stopped=false;
    private float cast=.0f,castDirection=1f,distance=0f,depth=0f,zoneY=.62f,fishY=.45f;
    private float fishVelocity=.27f,fishSteer=0f,catchAmount=.28f,castDistance=0f;
    private long lastMs=0;
    private String outcome="";
    private int displayedBait=Integer.MIN_VALUE,displayedGold=Integer.MIN_VALUE;
    private final Runnable clock=new Runnable(){@Override public void run(){
        if(stopped||paused||!isAttachedToWindow())return;
        long now=SystemClock.uptimeMillis();
        float dt=lastMs==0?0f:Math.min(.055f,Math.max(0f,(now-lastMs)/1000f));lastMs=now;
        update(dt);render();handler.postDelayed(this,16);
    }};
    public ChteicoFishingView(Context context,Runnable onExit){
        super(context);exit=onExit;
        LayoutInflater.from(context).inflate(R.layout.view_chteico_fishing,this,true);
        hud=findViewById(R.id.fishing_hud);status=findViewById(R.id.fishing_status);
        shop=findViewById(R.id.fishing_shop);close=findViewById(R.id.fishing_close);
        bobber=findViewById(R.id.fishing_bobber);fish=findViewById(R.id.fishing_fish);zone=findViewById(R.id.fishing_zone);
        meter=findViewById(R.id.fishing_cast_meter);castMark=findViewById(R.id.fishing_cast_optimum);
        castCursor=findViewById(R.id.fishing_cast_indicator);
        catchPanel=findViewById(R.id.fishing_catch_panel);track=findViewById(R.id.fishing_track);
        catchProgress=findViewById(R.id.fishing_catch_progress);
        accent=ReaderButtonColors.accent(context,0xff16a34a);
        int bg=context.getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
        ink=0xff30221e; // Fishing has a paper-colored XML surface in both themes.
        hud.setTextColor(ink);status.setTextColor(ink);shop.setTextColor(ink);
        castMark.setColorFilter(accent);zone.setColorFilter(accent);
        catchProgress.setProgressTintList(ColorStateList.valueOf(accent));
        setClickable(true);setFocusable(true);
        setContentDescription("Рыбалка: удерживайте палец в любой части игрового поля для заброса, подтягивания и ловли");
        render();
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();resumeClock();}
    @Override protected void onDetachedFromWindow(){stopClock();super.onDetachedFromWindow();}
    public void pauseClock(){paused=true;held=false;
        if(phase==CASTING)phase=READY;else if(phase==REELING)phase=FLOATING;
        handler.removeCallbacks(clock);lastMs=0;render();}
    public void resumeClock(){if(stopped)return;paused=false;lastMs=0;handler.removeCallbacks(clock);handler.post(clock);}
    public void stopClock(){stopped=true;paused=true;held=false;handler.removeCallbacks(clock);}
    private void update(float dt){
        if(phase==CASTING&&held){cast+=castDirection*dt*.93f;
            if(cast>=1){cast=2-cast;castDirection=-1;}else if(cast<=0){cast=-cast;castDirection=1;}}
        if(phase==REELING&&held){
            depth=Math.min(1f,depth+dt*.20f);
            // Bite checks are time based, never dependent on frame rate or idle time.
            double p=(.20+.28*distance)*dt;
            if(random.nextDouble()<1-Math.exp(-p)){hook();return;}
            if(depth>=.985f){finish("Поплавок вернулся. Поклёвки не было.");return;}
        }
        if(phase==CATCHING){
            fishSteer-=dt;
            if(fishSteer<=0){fishSteer=.3f+random.nextFloat()*.6f;fishVelocity=(random.nextFloat()-.5f)*1.3f;}
            fishY+=fishVelocity*dt;
            if(fishY<.05f){fishY=.05f;fishVelocity=Math.abs(fishVelocity);}
            if(fishY>.94f){fishY=.94f;fishVelocity=-Math.abs(fishVelocity);}
            zoneY+=dt*(held?-.53f:.34f);
            zoneY=Math.max(.0f,Math.min(.75f,zoneY));
            boolean inside=fishY>=zoneY-.04f&&fishY<=zoneY+.30f;
            catchAmount=Math.max(0,Math.min(1,catchAmount+dt*(inside?.26f:-.15f)));
            if(catchAmount>=1){successfulCatch();return;}
            if(catchAmount<=0){finish("Рыба сорвалась!");}
        }
    }
    private void hook(){
        phase=CATCHING;held=true;zoneY=.58f;fishY=.52f;fishSteer=.25f;catchAmount=.28f;
        // Rarity depends on how well the 80% casting target was hit.
        float tierBoost=distance;
        double roll=random.nextDouble();
        double myth=.001+.009*tierBoost,legend=.005+.035*tierBoost,rare=.025+.095*tierBoost,uncommon=.22+.06*tierBoost;
        if(roll<myth)rarity=4;
        else if(roll<myth+legend)rarity=3;
        else if(roll<myth+legend+rare)rarity=2;
        else if(roll<myth+legend+rare+uncommon)rarity=1;
        else rarity=0;
        int[] fishImages={R.drawable.ic_fishing_fish_common,R.drawable.ic_fishing_fish_uncommon,
                R.drawable.ic_fishing_fish_rare,R.drawable.ic_fishing_fish_legendary,R.drawable.ic_fishing_fish_mythical};
        fish.setImageResource(fishImages[rarity]);
    }
    private void successfulCatch(){
        float[] minimum={.2f,.5f,1f,2f,3f};float[] maximum={2f,3.5f,5f,8f,12f};
        float mass=minimum[rarity]+random.nextFloat()*(maximum[rarity]-minimum[rarity]);
        String[] names={"Обычная","Необычная","Редкая","Легендарная","Мифическая"};
        int[] multTimes2={2,3,5,8,14};
        int experience=Math.round(mass*20*multTimes2[rarity]/2f);
        int gold=Math.max(1,Math.round(mass*5*multTimes2[rarity]/2f));
        ChteicoStore.recordFishingCatch(getContext(),Math.round(mass*1000),experience,gold,rarity);
        finish(String.format(Locale.forLanguageTag("ru"),"Рыба поймана! %.1f кг · %s\n+%d XP · +%d золота",mass,names[rarity],experience,gold));
    }
    private void finish(String message){phase=RESULT;held=false;outcome=message;}
    private void startCast(){
        if(ChteicoStore.fishingBait(getContext())<1){
            new SwipeDialogs.Builder(getContext()).setTitle("Нет наживок")
              .setMessage("Получайте по 10 наживок за главу книги, прослушанную главу или каждые 10 новых страниц манги. Можно купить наживки за золото.")
              .setPositiveButton("Купить",(d,w)->showShop()).setNegativeButton("Закрыть",null).show();return;}
        cast=0;castDirection=1;phase=CASTING;held=true;outcome="";
    }
    private void finishCast(){
        if(!ChteicoStore.useFishingBait(getContext())){phase=READY;held=false;return;}
        distance=Math.max(.08f,Math.min(1,1-Math.abs(cast-.80f)/.80f));
        castDistance=distance;
        depth=.09f+(1-distance)*.73f;
        phase=FLOATING;held=false;
    }
    private void showShop(){
        String[] items={"1 наживка — 30 золота","10 наживок — 270 золота","50 наживок — 1200 золота"};
        final int[] amounts={1,10,50};
        new SwipeDialogs.Builder(getContext()).setTitle("Купить наживки · Золото: "+ChteicoStore.fishingGold(getContext()))
          .setItems(items,(d,which)->{
            if(!ChteicoStore.buyFishingBait(getContext(),amounts[which]))
                android.widget.Toast.makeText(getContext(),"Недостаточно золота",android.widget.Toast.LENGTH_SHORT).show();
            render();
          }).setNegativeButton("Отмена",null).show();
    }
    private void updateStatus(String message){
        if(!message.contentEquals(status.getText()))status.setText(message);
    }
    private void render(){
        int bait=ChteicoStore.fishingBait(getContext()),gold=ChteicoStore.fishingGold(getContext());
        if(bait!=displayedBait||gold!=displayedGold){
            displayedBait=bait;displayedGold=gold;
            hud.setText("Рыбалка · "+bait+" наж. · "+gold+" зол.");
        }
        meter.setVisibility(phase==CASTING?VISIBLE:GONE);
        bobber.setVisibility(phase==FLOATING||phase==REELING?VISIBLE:GONE);
        catchPanel.setVisibility(phase==CATCHING?VISIBLE:GONE);
        catchProgress.setVisibility(phase==CATCHING?VISIBLE:GONE);
        if(phase==CASTING){
            int available=Math.max(0,meter.getWidth()-(int)dp(6));
            castMark.setX(available*.80f);castCursor.setX(available*cast);
            updateStatus("Отпустите на акцентной метке 80%");
        }else if(phase==FLOATING){updateStatus("Удерживайте палец, чтобы подтягивать поплавок");}
        else if(phase==REELING){updateStatus("Подтягивание · поклёвка только при удерживании");}
        else if(phase==CATCHING){
            float height=Math.max(dp(140),track.getHeight());
            zone.setY((height-dp(54))*zoneY/.75f);
            fish.setY((height-dp(48))*fishY);
            catchProgress.setProgress(Math.round(catchAmount*100));
            updateStatus("Держите рыбку внутри акцентной зоны");
        }else if(phase==RESULT){updateStatus(outcome+" · Тап — новый заброс");}
        else updateStatus("Удерживайте палец для выбора силы заброса");
        if(bobber.getVisibility()==VISIBLE){
            float max=Math.max(0,getHeight()-dp(150));
            bobber.setX(Math.max(dp(9),getWidth()*.40f-dp(18)));
            bobber.setY(dp(55)+max*depth);
        }
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            float x=e.getX(),y=e.getY();
            if(y<dp(45)&&x>getWidth()-dp(44)){stopClock();if(exit!=null)exit.run();return true;}
            if(y<dp(45)&&x>getWidth()-dp(116)){showShop();return true;}
            if(phase==RESULT){phase=READY;outcome="";}
            if(phase==READY)startCast();
            else if(phase==FLOATING){phase=REELING;held=true;}
            else if(phase==CATCHING)held=true;
            render();return true;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            if(phase==CASTING)finishCast();
            else if(phase==REELING){phase=FLOATING;held=false;}
            else if(phase==CATCHING)held=false;
            else held=false;
            render();return true;
        }
        return true;
    }
}
