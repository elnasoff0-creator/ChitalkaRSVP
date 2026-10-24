package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashSet;

/** Public-site audiobook/series results. No MP3 is requested until a book is selected. */
public final class KvuheSearchActivity extends Activity {
    private static final int BG=0xfff3e7d5,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949,WHITE=0xfffff9f0;
    private EditText query;
    private TextView state,booksTab,seriesTab;
    private Button more,searchButton;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<KvuheSearchParser.Entry> books=new ArrayList<>(),series=new ArrayList<>(),shown=new ArrayList<>();
    private final HashSet<String> bookIds=new HashSet<>(),seriesIds=new HashSet<>(),visitedPages=new HashSet<>();
    private String next="",failedPage="";
    private int requestId,totalBooks=-1,totalSeries=-1,tab=0;
    private boolean pending;
    private ResultAdapter adapter;
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(INK);v.setTextSize(size);return v;}
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setGravity(Gravity.CENTER);b.setTextColor(INK);b.setTextSize(15);b.setBackground(shape(WHITE,16));return b;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);build();}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(dp(13),dp(24),dp(13),dp(12));setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setPadding(dp(10),dp(10),dp(10),dp(10));
        back.setColorFilter(INK);back.setBackground(shape(WHITE,15));back.setOnClickListener(v->BackNavigation.back(this));
        back.setContentDescription("Назад");header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView heading=text("Поиск аудиокниги",23);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);heading.setGravity(Gravity.CENTER);
        header.addView(heading,new LinearLayout.LayoutParams(0,-1,1));header.addView(new View(this),new LinearLayout.LayoutParams(dp(49),dp(49)));
        LinearLayout search=new LinearLayout(this);search.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(54));sl.topMargin=dp(16);root.addView(search,sl);
        query=new EditText(this);query.setSingleLine(true);query.setTextSize(16);query.setHint("Название, автор или чтец");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);query.setBackground(shape(WHITE,15));query.setPadding(dp(12),0,dp(12),0);
        search.addView(query,new LinearLayout.LayoutParams(0,-1,1));
        searchButton=button("Найти");searchButton.setTextColor(Color.WHITE);searchButton.setBackground(shape(ACCENT,15));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(82),-1);bp.leftMargin=dp(7);search.addView(searchButton,bp);
        searchButton.setOnClickListener(v->startNewSearch());query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){startNewSearch();return true;}return false;});
        LinearLayout tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(43));tp.topMargin=dp(11);root.addView(tabs,tp);
        booksTab=text("Книги",14);seriesTab=text("Циклы",14);
        for(int i=0;i<2;i++){
            final int selected=i;TextView t=i==0?booksTab:seriesTab;t.setGravity(Gravity.CENTER);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);if(i!=0)lp.leftMargin=dp(9);tabs.addView(t,lp);
            t.setOnClickListener(v->{tab=selected;update();});
        }
        state=text("Введите название и нажмите «Найти»",13);state.setTextColor(MUTED);state.setPadding(dp(2),dp(11),0,dp(10));root.addView(state);
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new ResultAdapter();list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        more=button("Показать ещё");more.setVisibility(View.GONE);more.setOnClickListener(v->fetch(failedPage.isEmpty()?next:failedPage));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(48));mp.topMargin=dp(8);root.addView(more,mp);update();
    }
    private void startNewSearch(){
        String q=query.getText().toString().trim();if(q.isEmpty()){query.setError("Введите название книги");return;}
        requestId++;pending=false;next="";failedPage="";totalBooks=-1;totalSeries=-1;
        books.clear();series.clear();bookIds.clear();seriesIds.clear();visitedPages.clear();update();
        fetch(KvuheSearchParser.searchUrl(q));
    }
    private void fetch(String url){
        if(pending||url==null||url.isEmpty()||visitedPages.contains(url))return;
        pending=true;final int generation=requestId;more.setVisibility(View.GONE);searchButton.setEnabled(false);
        state.setText(books.isEmpty()&&series.isEmpty()?"Поиск аудиокниг…":"Загрузка следующей страницы…");
        new Thread(()->{
            try{
                KvuheSearchParser.Page page=KvuheSearchParser.fetch(url);
                runOnUiThread(()->{
                    if(generation!=requestId||isFinishing())return;
                    visitedPages.add(url);int added=0;
                    for(KvuheSearchParser.Entry e:page.entries)if(bookIds.add(e.url)){books.add(e);added++;}
                    for(KvuheSearchParser.Entry e:page.series)if(seriesIds.add(e.url))series.add(e);
                    if(page.totalBooks>=0)totalBooks=page.totalBooks;
                    if(page.totalSeries>=0)totalSeries=page.totalSeries;
                    next=page.nextUrl;
                    // Never loop indefinitely if the website ignores page=N or repeats results.
                    if(next.equals(url)||visitedPages.contains(next)||added==0&&!books.isEmpty())next="";
                    if(totalBooks>=0&&books.size()>=totalBooks)next="";
                    failedPage="";pending=false;searchButton.setEnabled(true);
                    if(books.isEmpty()&&!series.isEmpty())tab=1;
                    update();
                });
            }catch(Exception ex){runOnUiThread(()->{
                if(generation!=requestId||isFinishing())return;
                pending=false;searchButton.setEnabled(true);failedPage=url;
                state.setText("Ошибка поиска: "+(ex.getMessage()==null?"повторите попытку":ex.getMessage()));
                more.setText("Повторить загрузку");more.setVisibility(View.VISIBLE);
            });}
        },"knigavuhe-search").start();
    }
    private void update(){
        if(adapter==null)return;
        booksTab.setText("Книги"+(totalBooks>=0?" · "+totalBooks:""));
        seriesTab.setText("Циклы"+(totalSeries>=0?" · "+totalSeries:""));
        booksTab.setTextColor(tab==0?Color.WHITE:INK);booksTab.setBackground(shape(tab==0?ACCENT:WHITE,14));
        seriesTab.setTextColor(tab==1?Color.WHITE:INK);seriesTab.setBackground(shape(tab==1?ACCENT:WHITE,14));
        shown.clear();shown.addAll(tab==0?books:series);adapter.notifyDataSetChanged();
        if(!pending){
            if(books.isEmpty()&&series.isEmpty())state.setText(visitedPages.isEmpty()?"Введите название и нажмите «Найти»":"Ничего не найдено. Попробуйте другое название.");
            else state.setText(tab==0?("Книг в списке: "+books.size()+(totalBooks>=0?" из "+totalBooks:"")+" · выберите озвучку")
                    :("Циклов в списке: "+series.size()+(totalSeries>=0?" из "+totalSeries:"")));
        }
        more.setText(failedPage.isEmpty()?"Показать ещё":"Повторить загрузку");
        more.setVisibility(tab==0&&!pending&&(!failedPage.isEmpty()||!next.isEmpty())?View.VISIBLE:View.GONE);
    }
    private void open(KvuheSearchParser.Entry e){
        Intent i=new Intent(this,tab==0?KvuheBookActivity.class:KvuheSeriesActivity.class);
        i.putExtra("source",e.url).putExtra("title",e.title).putExtra("author",e.author)
         .putExtra("reader",e.reader).putExtra("description",e.description).putExtra("cover",e.cover)
         .putExtra("auto_fetch",true);
        startActivity(i);
    }
    private final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(KvuheSearchActivity.this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.TOP);
            card.setPadding(dp(10),dp(11),dp(10),dp(11));card.setBackground(shape(WHITE,18));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(10);card.setLayoutParams(lp);
            ImageView cover=new ImageView(KvuheSearchActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(shape(0xffeadccc,12));card.addView(cover,new LinearLayout.LayoutParams(dp(94),dp(130)));
            LinearLayout info=new LinearLayout(KvuheSearchActivity.this);info.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(0,-2,1);ilp.leftMargin=dp(12);card.addView(info,ilp);
            TextView title=text("",18);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setMaxLines(3);info.addView(title);
            TextView author=text("",12);author.setTextColor(MUTED);info.addView(author);
            TextView description=text("",13);description.setMaxLines(3);description.setTextColor(INK);info.addView(description);
            TextView duration=text("",12);duration.setTextColor(MUTED);info.addView(duration);
            TextView choose=text("Открыть книгу ›",13);choose.setTextColor(0xff8c5335);choose.setPadding(0,dp(8),0,0);info.addView(choose);
            return new Holder(card,cover,title,author,description,duration,choose);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            KvuheSearchParser.Entry e=shown.get(position);
            h.title.setText(e.title);String meta=(e.author.isEmpty()?"":e.author)+(e.reader.isEmpty()?"":" · "+e.reader);
            h.author.setText(meta.trim());h.author.setVisibility(meta.trim().isEmpty()?View.GONE:View.VISIBLE);
            h.description.setText(e.description);h.description.setVisibility(e.description.isEmpty()?View.GONE:View.VISIBLE);
            String tail=(e.genre.isEmpty()?"":e.genre)+(e.duration.isEmpty()?"":" · "+e.duration);
            h.duration.setText(tail);h.duration.setVisibility(tail.isEmpty()?View.GONE:View.VISIBLE);
            h.choose.setText(tab==0?"Открыть аудиокнигу ›":"Открыть цикл ›");
            CoverImageLoader.show(h.cover,e.cover,false);
            h.itemView.setOnClickListener(v->open(e));
        }
        @Override public int getItemCount(){return shown.size();}
        final class Holder extends RecyclerView.ViewHolder{
            final ImageView cover;final TextView title,author,description,duration,choose;
            Holder(View root,ImageView c,TextView t,TextView a,TextView d,TextView time,TextView open){super(root);cover=c;title=t;author=a;description=d;duration=time;choose=open;}
        }
    }
}
