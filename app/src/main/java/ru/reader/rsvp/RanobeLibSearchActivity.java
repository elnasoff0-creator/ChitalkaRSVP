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

/** Search by Russian/original title or paste a public RanobeLIB book/read URL. */
public final class RanobeLibSearchActivity extends NavigationActivity {
    private int bg=0xfff3e7d5,ink=0xff30221e,panel=0xfffff9f2;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private EditText query;private LinearLayout results;private TextView status;
    private int generation=0,page=1;private String lastQuery="";
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    private TextView label(String text,int size,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextColor(ink);v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView action(String name,Runnable action){TextView b=label(name,15,true);b.setGravity(Gravity.CENTER);b.setTextColor(UiColorScheme.onAccent(this));b.setBackground(shape(UiColorScheme.accent(this)));b.setPadding(dp(10),dp(12),dp(10),dp(12));b.setOnClickListener(v->action.run());return b;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        android.content.SharedPreferences theme=getSharedPreferences("reader",0);
        bg=theme.getInt("bg",bg);ink=theme.getInt("fg",ink);panel=UiColorScheme.neutral(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(20),dp(14),dp(10));root.setBackgroundColor(bg);setContentView(root);SafeSystemBars.matchBackground(this,bg);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar);
        ImageView menu=new ImageView(this);menu.setImageResource(R.drawable.ic_menu_lines);menu.setColorFilter(ink);menu.setPadding(dp(10),dp(10),dp(10),dp(10));menu.setBackground(shape(panel));menu.setContentDescription("Главное меню");menu.setOnClickListener(v->UniversalDrawer.show(this));toolbar.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView heading=label("RanobeLIB · Поиск ранобэ",19,true);heading.setGravity(Gravity.CENTER);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView folder=new ImageView(this);folder.setImageResource(R.drawable.ic_folder_audio);folder.setColorFilter(ink);folder.setPadding(dp(10),dp(10),dp(10),dp(10));folder.setBackground(shape(panel));folder.setContentDescription("Выбрать папку для HTML");folder.setOnClickListener(v->DownloadFolderPicker.choose(this,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.sourceKey("ranobelib")));toolbar.addView(folder,new LinearLayout.LayoutParams(dp(44),dp(44)));
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(ink);query.setTextSize(15);query.setHint("Название книги или URL RanobeLIB");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);query.setPadding(dp(12),dp(8),dp(12),dp(8));query.setBackground(shape(panel));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(50));qp.topMargin=dp(17);root.addView(query,qp);
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search(1);return true;}return false;});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(48));bp.topMargin=dp(9);root.addView(action("Найти книги",()->search(1)),bp);
        status=label("Поиск по каталогу RanobeLIB. Выберите произведение, том или отдельные главы.",13,false);status.setPadding(0,dp(12),0,dp(12));root.addView(status);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);scroll.addView(results);
        String initial=getIntent().getStringExtra("ranobelib_query");if(initial==null)initial=theme.getString("ranobelib_last_query","");query.setText(initial);if(!initial.isEmpty())search(1);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.sourceKey("ranobelib"));}
    private void search(int targetPage){String entered=query.getText().toString().trim();if(entered.isEmpty())return;
        int token=++generation;lastQuery=entered;page=targetPage;results.removeAllViews();status.setText("Поиск RanobeLIB…");
        getSharedPreferences("reader",0).edit().putString("ranobelib_last_query",entered).apply();
        network.execute(()->{
            try{
                String slug=RanobeLibApi.slugFromLink(entered);
                if(!slug.isEmpty()){runOnUiThread(()->{if(token==generation&&!isFinishing())open(slug);});return;}
                List<RanobeLibApi.Title> titles=new RanobeLibApi().search(entered,targetPage);
                runOnUiThread(()->{
                    if(token!=generation||isFinishing())return;
                    status.setText(titles.isEmpty()?"Ничего не найдено. Проверьте название.":"Страница "+page+" · найдено "+titles.size());results.removeAllViews();
                    for(RanobeLibApi.Title title:titles)showTitle(title);
                    LinearLayout pages=new LinearLayout(this);pages.setGravity(Gravity.CENTER);results.addView(pages);
                    if(page>1){TextView prev=action("← Назад",()->search(page-1));pages.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));}
                    if(!titles.isEmpty()){TextView next=action("Далее →",()->search(page+1));pages.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));}
                });
            }catch(Exception error){runOnUiThread(()->{if(token==generation&&!isFinishing()){status.setText("Ошибка: "+error.getMessage());Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}});}
        });
    }
    private void showTitle(RanobeLibApi.Title title){
        LinearLayout card=new LinearLayout(this);card.setBackground(shape(panel));card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(9);results.addView(card,cp);card.setOrientation(LinearLayout.HORIZONTAL);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new LinearLayout.LayoutParams(dp(76),dp(106)));
        CoverImageLoader.show(image,title.cover,false);
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(12),0,0,0);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=label(title.title,17,true);name.setMaxLines(3);labels.addView(name);
        if(!title.author.isEmpty()){TextView author=label(title.author,13,false);labels.addView(author);}
        if(title.chapters>0)labels.addView(label("Глав: "+title.chapters,12,false));
        if(!title.description.isEmpty()){TextView description=label(title.description,12,false);description.setMaxLines(3);labels.addView(description);}
        card.setOnClickListener(v->open(title.slug));
    }
    private void open(String slug){startActivity(new Intent(this,RanobeLibBookActivity.class).putExtra("ranobelib_slug",slug));}
    @Override protected void onDestroy(){generation++;network.shutdownNow();super.onDestroy();}
}
