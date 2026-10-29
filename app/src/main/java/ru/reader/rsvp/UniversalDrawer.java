package ru.reader.rsvp;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Activity-owned left-side navigation overlay. No SYSTEM_ALERT_WINDOW permission. */
final class UniversalDrawer {
    private UniversalDrawer(){}
    private static int dp(Activity a,int v){return (int)(a.getResources().getDisplayMetrics().density*v+.5f);}
    private static GradientDrawable background(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;}
    static void show(Activity a){
        final SharedPreferences p=a.getSharedPreferences("reader",0);
        int bg=p.getInt("bg",0xfff3e7d5),fg=p.getInt("fg",0xff30221e);
        int ink=ReadingThemeCycle.isDark(bg)?Color.WHITE:fg;
        Dialog menu=new Dialog(a);menu.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setBackgroundColor(bg);
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(a,16),dp(a,32),dp(a,16),dp(a,20));scroll.addView(panel);
        TextView title=new TextView(a);title.setText("ПРОЧиталка");title.setTextSize(23);title.setTextColor(ink);
        title.setPadding(dp(a,10),dp(a,8),0,dp(a,28));panel.addView(title);
        item(a,menu,panel,R.drawable.ic_home,"Главный экран",MainActivity.class,null,ink);
        item(a,menu,panel,R.drawable.ic_book,"Чтение",ReaderActivity.class,p.getString("uri",""),ink);
        item(a,menu,panel,R.drawable.ic_mode_speedreading,"Скорочтение",ReaderActivity.class,p.getString("uri",""),ink,1);
        item(a,menu,panel,R.drawable.ic_microphone,"Озвучивание",ReaderActivity.class,p.getString("uri",""),ink,3);
        item(a,menu,panel,R.drawable.ic_audio_chapters,"Аудиокнига",AudioPlayerActivity.class,null,ink);
        item(a,menu,panel,R.drawable.ic_chteico_nav,"Чтейцо",ChteicoActivity.class,null,ink);
        item(a,menu,panel,R.drawable.ic_stats_nav,"Статистика",ChteicoActivity.class,null,ink);
        item(a,menu,panel,R.drawable.ic_downloads,"Загрузки",DownloadActivity.class,null,ink);
        item(a,menu,panel,R.drawable.ic_settings,"Настройки",SettingsActivity.class,null,ink);
        item(a,menu,panel,R.drawable.ic_info_help,"О приложении",ActivityInfo.class,null,ink);
        menu.setContentView(scroll);
        Window w=menu.getWindow();if(w!=null){
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attr=w.getAttributes();attr.gravity=Gravity.LEFT|Gravity.TOP;
            attr.width=Math.min(dp(a,340),Math.round(a.getResources().getDisplayMetrics().widthPixels*.84f));
            attr.height=WindowManager.LayoutParams.MATCH_PARENT;attr.dimAmount=.42f;
            w.setAttributes(attr);w.setWindowAnimations(android.R.style.Animation_Dialog);
        }
        menu.show();if(w!=null)w.setLayout(Math.min(dp(a,340),Math.round(a.getResources().getDisplayMetrics().widthPixels*.84f)),-1);
    }
    private static void item(Activity a,Dialog menu,LinearLayout root,int icon,String name,Class<? extends Activity> target,String uri,int ink){
        item(a,menu,root,icon,name,target,uri,ink,-1);
    }
    private static void item(Activity a,Dialog menu,LinearLayout root,int icon,String name,Class<? extends Activity> target,String uri,int ink,int mode){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,12),dp(a,9),dp(a,12),dp(a,9));
        ImageView image=new ImageView(a);image.setImageResource(icon);image.setColorFilter(ink);
        row.addView(image,new LinearLayout.LayoutParams(dp(a,25),dp(a,25)));
        TextView text=new TextView(a);text.setText(name);text.setTextColor(ink);text.setTextSize(16);text.setPadding(dp(a,15),0,0,0);
        row.addView(text);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(a,55));rp.bottomMargin=dp(a,5);
        root.addView(row,rp);
        row.setOnClickListener(v->{menu.dismiss();
            Intent open=new Intent(a,target);
            if(target==ReaderActivity.class){
                if(uri!=null&&!uri.isEmpty())open.putExtra("book_uri",uri);
                if(mode>=0)open.putExtra("reading_mode_override",mode);
            }
            if(target==MainActivity.class)open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            if(target==a.getClass() && mode<0)return;
            a.startActivity(open);
        });
    }
}
