package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.WeakHashMap;

/**
 * One real in-Activity left drawer, shared by every screen including MainActivity.
 * No Dialog, no separate navigation Activity and no SYSTEM_ALERT_WINDOW permission.
 * Contents, icon resources, animation, swipe and destinations have one owner.
 */
final class UniversalDrawer {
    private UniversalDrawer() {}
    private static final long SLIDE_MS = 220L;
    private static final WeakHashMap<Activity, Session> OPEN = new WeakHashMap<>();

    private static final class Session {
        final FrameLayout parent;
        final SwipeShade shade;
        final ScrollView panel;
        boolean closing;
        Session(FrameLayout parent,SwipeShade shade,ScrollView panel){this.parent=parent;this.shade=shade;this.panel=panel;}
    }

    private static int dp(Activity a,int n){return (int)(a.getResources().getDisplayMetrics().density*n+.5f);}
    private static GradientDrawable shape(Activity a,int fill,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(a,radius));return d;
    }
    private static FrameLayout content(Activity a){
        View v=a.findViewById(android.R.id.content);
        return v instanceof FrameLayout?(FrameLayout)v:null;
    }
    static boolean isOpen(Activity a){Session s=OPEN.get(a);return s!=null&&s.shade.getParent()==s.parent;}

    static void show(Activity a){
        if(a==null||a.isFinishing()||isOpen(a))return;
        final FrameLayout host=content(a);
        if(host==null)return;
        SharedPreferences p=a.getSharedPreferences("reader",Activity.MODE_PRIVATE);
        int bg=p.getInt("bg",0xfff3e7d5), fg=p.getInt("fg",0xff30221e);
        int soft=ReadingThemeCycle.isDark(bg)?0xff333842:0xfff5ede2;
        SwipeShade shade=new SwipeShade(a);
        shade.setBackgroundColor(0x92000000);
        shade.setAlpha(0f);
        shade.setClickable(true);
        host.addView(shade,new FrameLayout.LayoutParams(-1,-1));

        int width=Math.min(dp(a,340),(int)(a.getResources().getDisplayMetrics().widthPixels*.84f));
        ScrollView scroll=new ScrollView(a);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        scroll.setClickable(true);
        scroll.setTranslationX(-width);
        scroll.setVerticalScrollBarEnabled(false);
        shade.addView(scroll,new FrameLayout.LayoutParams(width,-1,Gravity.START));
        Session session=new Session(host,shade,scroll);
        OPEN.put(a,session);
        shade.setOnClickListener(v->close(a));
        scroll.setOnClickListener(v->{}); // Do not dismiss when the panel itself is tapped.
        shade.onSwipe=()->close(a);
        LinearLayout list=new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(a,18),dp(a,23),dp(a,18),dp(a,18));
        scroll.addView(list,new ScrollView.LayoutParams(-1,-2));

        LinearLayout branding=new LinearLayout(a);branding.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(a);logo.setImageResource(R.drawable.ic_launcher_foreground);
        logo.setBackground(shape(a,soft,14));logo.setClipToOutline(true);
        branding.addView(logo,new LinearLayout.LayoutParams(dp(a,43),dp(a,43)));
        TextView brand=new TextView(a);brand.setText("  ПРОЧиталка");brand.setTextSize(21);
        brand.setTextColor(fg);brand.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        branding.addView(brand);list.addView(branding);
        separator(a,list,fg,18,10);
        // Exactly the eight entries and the same drawable resources as the original MainActivity drawer.
        item(a,list,fg,R.drawable.ic_home,"Главный экран",()->home(a));
        item(a,list,fg,R.drawable.ic_book,"Чтение",()->reading(a));
        item(a,list,fg,R.drawable.ic_microphone,"Аудиокнига",()->audiobook(a));
        item(a,list,fg,R.drawable.ic_downloads,"Загрузки",()->open(a,DownloadActivity.class));
        item(a,list,fg,R.drawable.ic_chteico_nav,"Чтейцо",()->open(a,ChteicoActivity.class));
        separator(a,list,fg,14,10);
        item(a,list,fg,R.drawable.ic_settings,"Настройки",()->open(a,SettingsActivity.class));
        item(a,list,fg,R.drawable.ic_web_update,"Обновление",()->new UpdateManager(a).checkVersion());
        item(a,list,fg,R.drawable.ic_info_help,"Справка",()->open(a,ActivityInfo.class));

        scroll.post(()->{
            if(OPEN.get(a)!=session||session.closing)return;
            shade.animate().alpha(1f).setDuration(SLIDE_MS).start();
            scroll.animate().translationX(0f).setDuration(SLIDE_MS).start();
        });
    }

    private static void separator(Activity a,LinearLayout list,int fg,int above,int below){
        View line=new View(a);line.setBackgroundColor(fg&0x44ffffff);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(a,1));
        lp.topMargin=dp(a,above);lp.bottomMargin=dp(a,below);list.addView(line,lp);
    }
    private static void item(Activity a,LinearLayout list,int fg,int drawable,String title,Runnable action){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a,11),dp(a,12),dp(a,6),dp(a,12));
        ImageView icon=new ImageView(a);icon.setImageResource(drawable);icon.setColorFilter(fg);
        row.addView(icon,new LinearLayout.LayoutParams(dp(a,24),dp(a,24)));
        TextView label=new TextView(a);label.setText(title);label.setTextSize(17);label.setTextColor(fg);
        LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(-1,-2);textLp.leftMargin=dp(a,20);
        row.addView(label,textLp);
        row.setContentDescription(title);row.setFocusable(true);row.setClickable(true);
        row.setOnClickListener(v->close(a,action));
        list.addView(row,new LinearLayout.LayoutParams(-1,dp(a,57)));
    }

    static void discard(Activity a){
        Session s=OPEN.remove(a);
        if(s!=null){
            s.panel.animate().cancel();s.shade.animate().cancel();
            if(s.shade.getParent()==s.parent)s.parent.removeView(s.shade);
        }
    }
    static boolean closeIfOpen(Activity a){if(!isOpen(a))return false;close(a);return true;}
    static void close(Activity a){close(a,null);}
    static void close(Activity a,Runnable afterClose){
        Session s=OPEN.get(a);
        if(s==null||s.shade.getParent()!=s.parent){OPEN.remove(a);if(afterClose!=null)afterClose.run();return;}
        if(s.closing)return;
        s.closing=true;
        s.panel.animate().cancel();s.shade.animate().cancel();
        s.panel.animate().translationX(-s.panel.getWidth()).setDuration(205).start();
        s.shade.animate().alpha(0f).setDuration(205).withEndAction(()->{
            if(OPEN.get(a)==s)OPEN.remove(a);
            if(s.shade.getParent()==s.parent)s.parent.removeView(s.shade);
            if(afterClose!=null&&!a.isFinishing())afterClose.run();
        }).start();
    }

    private static void open(Activity a,Class<? extends Activity> target){
        if(a.getClass()==target)return;
        Intent i=new Intent(a,target);
        // Reuse already open screens instead of creating duplicate back-stack entries.
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        a.startActivity(i);
    }
    private static void home(Activity a){
        if(a instanceof MainActivity){MainActivity m=(MainActivity)a;m.screen=0;m.draw();return;}
        Intent i=new Intent(a,MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        a.startActivity(i);
    }
    private static void reading(Activity a){
        if(a instanceof ReaderActivity){((ReaderActivity)a).openReadingFromDrawer();return;}
        String uri=a.getSharedPreferences("reader",0).getString("uri","");
        if(uri==null||uri.isEmpty()){
            if(a instanceof MainActivity)((MainActivity)a).pick();
            else{
                Toast.makeText(a,"Выберите книгу на главном экране",Toast.LENGTH_SHORT).show();
                home(a);
            }
            return;
        }
        Intent i=new Intent(a,ReaderActivity.class).putExtra("book_uri",uri).putExtra("reading_mode_override",0);
        a.startActivity(i);
    }
    private static void audiobook(Activity a){
        if(a instanceof AudioPlayerActivity)return;
        SharedPreferences p=a.getSharedPreferences("reader",0);
        String id=p.getString("audio_last_book","");
        if(id!=null&&!id.isEmpty()){
            for(AudioLibrary.Book b:AudioLibrary.load(a)){
                if(id.equals(b.id)){
                    a.startActivity(new Intent(a,AudioPlayerActivity.class).putExtra("audio_book_id",id));
                    return;
                }
            }
        }
        if(a instanceof MainActivity){((MainActivity)a).openLastMedia();return;}
        // AudioPlayerActivity offers audiobook selection if there is no last book.
        a.startActivity(new Intent(a,AudioPlayerActivity.class));
    }

    /** Captures only an intentional horizontal swipe, leaves vertical menu scrolling intact. */
    private static final class SwipeShade extends FrameLayout {
        private float downX,downY;
        private boolean closingGesture;
        Runnable onSwipe;
        SwipeShade(Activity a){super(a);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            switch(e.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    downX=e.getX();downY=e.getY();closingGesture=false;break;
                case MotionEvent.ACTION_MOVE:
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    if(dx<-dp(getActivity(),65)&&Math.abs(dx)>Math.abs(dy)*1.4f){
                        closingGesture=true;return true;
                    }
                    break;
            }
            return super.onInterceptTouchEvent(e);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(closingGesture){
                if(e.getActionMasked()==MotionEvent.ACTION_UP){closingGesture=false;if(onSwipe!=null)onSwipe.run();}
                if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)closingGesture=false;
                return true;
            }
            return super.onTouchEvent(e);
        }
        private Activity getActivity(){return (Activity)getContext();}
    }
}
