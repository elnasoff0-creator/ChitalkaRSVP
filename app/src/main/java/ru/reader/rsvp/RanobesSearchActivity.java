package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Independent Ranobes HTML source, launched by the shared category button. */
public final class RanobesSearchActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e;
    private final ExecutorService net=Executors.newSingleThreadExecutor();
    private EditText query;private LinearLayout list;private TextView message;
    private int page=1,token;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(){GradientDrawable d=new GradientDrawable();d.setColor(UiColorScheme.neutral(this));d.setCornerRadius(dp(13));return d;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(INK);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String value,Runnable action){TextView t=text(value,15,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(10),dp(11),dp(10),dp(11));t.setBackground(shape());t.setOnClickListener(v->action.run());return t;}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(14),dp(22),dp(14),dp(10));root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView menu=new ImageView(this);menu.setImageResource(R.drawable.ic_menu_lines);menu.setColorFilter(INK);menu.setPadding(dp(10),dp(10),dp(10),dp(10));menu.setBackground(shape());menu.setOnClickListener(v->UniversalDrawer.show(this));toolbar.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView heading=text("Ranobes.net · Поиск",21,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(12);toolbar.addView(heading,hp);
        ImageView folder=new ImageView(this);folder.setImageResource(R.drawable.ic_folder_audio);folder.setColorFilter(INK);folder.setPadding(dp(10),dp(10),dp(10),dp(10));folder.setBackground(shape());folder.setOnClickListener(v->DownloadFolderPicker.choose(this,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.RANOBE));toolbar.addView(folder,new LinearLayout.LayoutParams(dp(44),dp(44)));
        query=new EditText(this);query.setSingleLine(true);query.setTextSize(15);query.setHint("Название или https://ranobes.net/novels/...");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setBackground(shape());query.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(51));qp.topMargin=dp(14);root.addView(query,qp);
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search(1);return true;}return false;});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(9);root.addView(button("Найти книги",()->search(1)),bp);
        message=text("Поиск по публичным страницам Ranobes. При HTTP 403/429/503 сайт может временно ограничивать запросы.",13,false);LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(12);root.addView(message,mp);
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(9);root.addView(scroll,sp);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        String initial=getIntent().getStringExtra("ranobes_query");if(initial==null)initial=getSharedPreferences("reader",0).getString("ranobes_last_search","");query.setText(initial);
        if(!initial.isEmpty())search(1);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.RANOBE);}
    private void search(int next){String entered=query.getText().toString().trim();if(entered.isEmpty())return;
        int request=++token;page=next;list.removeAllViews();message.setText("Поиск Ranobes.net…");
        getSharedPreferences("reader",0).edit().putString("ranobes_last_search",entered).apply();
        String url=RanobesApi.validBookUrl(entered);if(!url.isEmpty()){open(url);return;}
        net.execute(()->{try{
            List<RanobesApi.Title> found=new RanobesApi().search(entered,next);
            runOnUiThread(()->{if(isFinishing()||token!=request)return;message.setText(found.isEmpty()?"По этому запросу ничего не найдено или сайт не предоставил результаты.":"Результаты: "+found.size()+" · страница "+next);list.removeAllViews();
                for(RanobesApi.Title item:found)showTitle(item);
                LinearLayout pages=new LinearLayout(this);list.addView(pages);
                if(page>1)pages.addView(button("← Назад",()->search(page-1)),new LinearLayout.LayoutParams(0,dp(49),1));
                if(!found.isEmpty())pages.addView(button("Далее →",()->search(page+1)),new LinearLayout.LayoutParams(0,dp(49),1));
            });
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&token==request)message.setText("Не удалось выполнить поиск: "+e.getMessage());});}});
    }
    private void showTitle(RanobesApi.Title item){LinearLayout card=new LinearLayout(this);card.setPadding(dp(9),dp(9),dp(9),dp(9));card.setBackground(shape());card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);list.addView(card,lp);
        ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(img,new LinearLayout.LayoutParams(dp(78),dp(104)));if(!item.cover.isEmpty())CoverImageLoader.show(img,item.cover,false);
        LinearLayout side=new LinearLayout(this);side.setOrientation(LinearLayout.VERTICAL);side.setPadding(dp(12),0,0,0);card.addView(side,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=text(item.title,17,true);title.setMaxLines(3);side.addView(title);
        if(!item.author.isEmpty())side.addView(text(item.author,13,false));
        side.addView(text("Ranobes.net · Читать и скачать главы",12,false));
        card.setOnClickListener(v->open(item.url));
    }
    private void open(String url){startActivity(new Intent(this,RanobesBookActivity.class).putExtra("ranobes_url",url));}
    @Override protected void onDestroy(){token++;net.shutdownNow();super.onDestroy();}
}
