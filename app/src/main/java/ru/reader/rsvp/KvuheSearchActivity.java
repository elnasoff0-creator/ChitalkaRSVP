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
import android.view.inputmethod.EditorInfo;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;

/** Native result cards, asynchronous HTML search; a selection opens the existing importer. */
public final class KvuheSearchActivity extends Activity {
    private static final int BG=0xfff3e7d5,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949,WHITE=0xfffff9f0;
    private EditText query;
    private TextView state;
    private Button more,searchButton;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<KvuheSearchParser.Entry> found=new ArrayList<>();
    private final HashSet<String> ids=new HashSet<>();
    private String next="";
    private int requestId;
    private boolean pending;
    private ResultAdapter adapter;
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextColor(INK);v.setTextSize(size);return v;}
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setGravity(Gravity.CENTER);b.setTextColor(INK);b.setTextSize(15);b.setBackground(shape(WHITE,16));return b;}
    @Override public void onCreate(Bundle state){super.onCreate(state);build();}
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(13),dp(27),dp(13),dp(13));setContentView(root);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setPadding(dp(11),dp(11),dp(11),dp(11));back.setColorFilter(INK);back.setBackground(shape(WHITE,15));back.setOnClickListener(v->BackNavigation.back(this));
        header.addView(back,new LinearLayout.LayoutParams(dp(49),dp(49)));
        TextView caption=text("Поиск аудиокниги",23);caption.setTypeface(Typeface.SERIF,Typeface.BOLD);caption.setGravity(Gravity.CENTER);
        header.addView(caption,new LinearLayout.LayoutParams(0,-1,1));
        View counterbalance=new View(this);header.addView(counterbalance,new LinearLayout.LayoutParams(dp(49),dp(49)));
        LinearLayout search=new LinearLayout(this);search.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,dp(54));sl.topMargin=dp(16);root.addView(search,sl);
        query=new EditText(this);query.setSingleLine(true);query.setTextSize(16);query.setHint("Название, автор или чтец");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        query.setBackground(shape(WHITE,15));query.setPadding(dp(12),0,dp(12),0);
        search.addView(query,new LinearLayout.LayoutParams(0,-1,1));
        searchButton=button("Найти");searchButton.setTextColor(Color.WHITE);searchButton.setBackground(shape(ACCENT,15));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(84),-1);bp.leftMargin=dp(8);search.addView(searchButton,bp);
        searchButton.setOnClickListener(v->startNewSearch());query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){startNewSearch();return true;}return false;});
        this.state=text("Введите название и нажмите «Найти»",13);this.state.setTextColor(MUTED);this.state.setPadding(dp(2),dp(13),0,dp(8));root.addView(this.state);
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));list.setHasFixedSize(false);adapter=new ResultAdapter();list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        more=button("Показать ещё");more.setVisibility(View.GONE);more.setOnClickListener(v->fetch(next));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,dp(48));mp.topMargin=dp(8);root.addView(more,mp);
    }
    private void startNewSearch(){
        String q=query.getText().toString().trim();if(q.isEmpty()){query.setError("Введите название книги");return;}
        requestId++;pending=false;next="";ids.clear();found.clear();adapter.notifyDataSetChanged();
        fetch(KvuheSearchParser.searchUrl(q));
    }
    private void fetch(String url){
        if(pending||url==null||url.isEmpty())return;
        pending=true;final int generation=requestId;more.setVisibility(View.GONE);searchButton.setEnabled(false);
        state.setText(found.isEmpty()?"Поиск аудиокниг…":"Загрузка следующей страницы…");
        new Thread(()->{
            try{KvuheSearchParser.Page result=KvuheSearchParser.fetch(url);
                runOnUiThread(()->{
                    if(generation!=requestId||isFinishing())return;
                    int additions=0;
                    for(KvuheSearchParser.Entry e:result.entries)if(ids.add(e.url)){found.add(e);additions++;}
                    next=result.nextUrl;
                    // Never offer a next-page link pointing to the exact current page.
                    if(next.equals(url))next="";
                    pending=false;searchButton.setEnabled(true);adapter.notifyDataSetChanged();more.setVisibility(next.isEmpty()?View.GONE:View.VISIBLE);
                    state.setText(found.isEmpty()?"Ничего не найдено. Попробуйте изменить запрос.":"Найдено в списке: "+found.size()+" · выберите нужную озвучку");
                });
            }catch(Exception ex){runOnUiThread(()->{if(generation!=requestId||isFinishing())return;pending=false;searchButton.setEnabled(true);
                state.setText("Ошибка поиска: "+(ex.getMessage()==null?"повторите попытку":ex.getMessage()));
                more.setVisibility(found.isEmpty()?View.GONE:View.VISIBLE);
                if(!found.isEmpty())more.setText("Повторить загрузку");
            });}
        },"knigavuhe-search").start();
    }
    private void open(KvuheSearchParser.Entry entry){
        startActivity(new Intent(this,UrlImportActivity.class).putExtra("source",entry.url).putExtra("auto_fetch",true));
    }
    private final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder>{
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(KvuheSearchActivity.this);card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.TOP);
            card.setPadding(dp(11),dp(12),dp(11),dp(12));card.setBackground(shape(WHITE,18));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(11);card.setLayoutParams(lp);
            ImageView cover=new ImageView(KvuheSearchActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.setBackground(shape(0xffeadccc,12));card.addView(cover,new LinearLayout.LayoutParams(dp(102),dp(146)));
            LinearLayout details=new LinearLayout(KvuheSearchActivity.this);details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams info=new LinearLayout.LayoutParams(0,-2,1);info.leftMargin=dp(12);card.addView(details,info);
            TextView title=text("",17);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setMaxLines(3);details.addView(title);
            TextView author=text("",12);author.setTextColor(MUTED);details.addView(author);
            TextView desc=text("",13);desc.setMaxLines(3);desc.setTextColor(INK);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,-2);dl.topMargin=dp(5);details.addView(desc,dl);
            TextView duration=text("",12);duration.setTextColor(MUTED);details.addView(duration);
            TextView choose=text("Выбрать аудиокнигу  ›",13);choose.setTextColor(0xff8c5335);choose.setPadding(0,dp(7),0,0);details.addView(choose);
            return new Holder(card,cover,title,author,desc,duration);
        }
        @Override public void onBindViewHolder(Holder holder,int position){KvuheSearchParser.Entry e=found.get(position);
            holder.title.setText(e.title);
            String by=(e.author.isEmpty()?"":e.author)+(e.reader.isEmpty()?"":" · "+e.reader);
            holder.author.setText(by.trim());holder.author.setVisibility(by.trim().isEmpty()?View.GONE:View.VISIBLE);
            holder.desc.setText(e.description);holder.desc.setVisibility(e.description.isEmpty()?View.GONE:View.VISIBLE);
            String tail=(e.genre.isEmpty()?"":e.genre)+(e.duration.isEmpty()?"":" · "+e.duration);
            holder.duration.setText(tail);holder.duration.setVisibility(tail.isEmpty()?View.GONE:View.VISIBLE);
            holder.cover.setTag(null);CoverImageLoader.show(holder.cover,e.cover,false);
            holder.itemView.setOnClickListener(v->open(e));
        }
        @Override public int getItemCount(){return found.size();}
        final class Holder extends RecyclerView.ViewHolder{
            final ImageView cover;final TextView title,author,desc,duration;
            Holder(View item,ImageView c,TextView t,TextView a,TextView d,TextView time){super(item);cover=c;title=t;author=a;desc=d;duration=time;}
        }
    }
}
