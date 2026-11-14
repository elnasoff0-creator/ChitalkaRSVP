package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One existing toolbar action per category; provider cards open below the toolbar. */
final class DownloadSourcePicker {
    private DownloadSourcePicker() {}
    static final class Provider {
        final String id,title,description;
        final Class<? extends Activity> screen;
        Provider(String id,String title,String description,Class<? extends Activity> screen){
            this.id=id;this.title=title;this.description=description;this.screen=screen;
        }
    }
    static final String RANOBE="ranobe",MANGA="manga",AUDIO="audio";
    private static final Provider[] RANOBE_SOURCES={
        new Provider("ranobelib","RanobeLIB","Каталог, тома и HTML-главы",RanobeLibSearchActivity.class),
        new Provider("ranobes","Ranobes.net","Оригинал, машинный перевод, полное оглавление",RanobesSearchActivity.class)
    };
    private static final Provider[] MANGA_SOURCES={
        new Provider("mangalib","MangaLib","Манга и CBZ-главы",MangaLibSearchActivity.class)
    };
    private static final Provider[] AUDIO_SOURCES={
        new Provider("kvuhe","Книга в ухе","Аудиокниги и MP3-главы",KvuheSearchActivity.class)
    };
    private static int dp(Activity a,int value){return Math.round(value*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable surface(int fill,int border,int radius,Activity a){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(a,radius));
        if(border!=0)d.setStroke(dp(a,1),border);return d;
    }
    static void show(Activity activity,String category){
        Provider[] sources=RANOBE.equals(category)?RANOBE_SOURCES:MANGA.equals(category)?MANGA_SOURCES:AUDIO_SOURCES;
        String heading=RANOBE.equals(category)?"Поиск ранобэ":MANGA.equals(category)?"Поиск манги":"Поиск аудиокниг";
        int bg=UiColorScheme.background(activity),panel=UiColorScheme.neutral(activity),accent=UiColorScheme.accent(activity);
        int fg=UiColorScheme.prefs(activity).getInt("fg",0xff30221e);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity,13),dp(activity,14),dp(activity,13),dp(activity,16));
        content.setBackground(surface(bg,0,20,activity));
        TextView title=new TextView(activity);title.setText(heading);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        title.setTextSize(19);title.setTextColor(fg);title.setPadding(dp(activity,6),0,0,dp(activity,10));
        content.addView(title);
        AlertDialog dialog=new SwipeDialogs.Builder(activity).fromTop().setView(content).create();
        for(Provider provider:sources){
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(activity,12),dp(activity,12),dp(activity,12),dp(activity,12));
            card.setBackground(surface(panel,UiColorScheme.blend(bg,accent,.27f),16,activity));
            LinearLayout.LayoutParams cardParams=new LinearLayout.LayoutParams(-1,-2);
            cardParams.topMargin=dp(activity,7);content.addView(card,cardParams);
            ImageView icon=new ImageView(activity);
            int resource=RANOBE.equals(category)?R.drawable.ic_book:MANGA.equals(category)?R.drawable.ic_manga_search:R.drawable.ic_search_audiobook;
            icon.setImageResource(resource);icon.setColorFilter(accent);icon.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));
            card.addView(icon,new LinearLayout.LayoutParams(dp(activity,44),dp(activity,44)));
            LinearLayout text=new LinearLayout(activity);text.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams txtParams=new LinearLayout.LayoutParams(0,-2,1);txtParams.leftMargin=dp(activity,8);card.addView(text,txtParams);
            TextView name=new TextView(activity);name.setText(provider.title);name.setTextSize(16);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            name.setTextColor(fg);text.addView(name);
            TextView description=new TextView(activity);description.setText(provider.description);description.setTextSize(12);
            description.setTextColor(fg);description.setAlpha(.75f);text.addView(description);
            TextView arrow=new TextView(activity);arrow.setText("›");arrow.setTextColor(accent);arrow.setTextSize(25);
            card.addView(arrow,new LinearLayout.LayoutParams(dp(activity,25),-2));
            card.setOnClickListener(v->{dialog.dismiss();activity.startActivity(new Intent(activity,provider.screen)
                .putExtra("content_source_id",provider.id));});
            card.setContentDescription(provider.title+". "+provider.description);
        }
        dialog.setCanceledOnTouchOutside(true);
        Window window=dialog.getWindow();if(window!=null){
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.TOP|Gravity.CENTER_HORIZONTAL);
            WindowManager.LayoutParams params=window.getAttributes();params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
            // Below app toolbar (window itself is already inset below the status bar).
            params.y=dp(activity,66);
            params.width=Math.min(activity.getResources().getDisplayMetrics().widthPixels-dp(activity,24),dp(activity,440));
            params.height=WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
        dialog.show();
    }
}
