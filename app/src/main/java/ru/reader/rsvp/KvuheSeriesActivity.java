package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

/** Cycle/series detail: browse its books, never download an entire series implicitly. */
public final class KvuheSeriesActivity extends Activity {
    private static final int BACK=0xfff3e7d5,WHITE=0xfffff9f0,INK=0xff30221e,GRAY=0xff716259;
    private final ArrayList<KvuheSearchParser.Entry> books=new ArrayList<>();
    private TextView state;private RecyclerView.Adapter<Row> adapter;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(17));return d;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextColor(INK);t.setTextSize(sp);return t;}
    @Override protected void onCreate(Bundle saved){super.onCreate(saved);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(13),dp(22),dp(13),dp(13));root.setBackgroundColor(BACK);setContentView(root);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(INK);back.setPadding(dp(10),dp(10),dp(10),dp(10));
        back.setBackground(bg(WHITE));back.setOnClickListener(v->BackNavigation.back(this));toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView heading=text("Цикл аудиокниг",23);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);heading.setGravity(Gravity.CENTER);
        toolbar.addView(heading,new LinearLayout.LayoutParams(0,-1,1));toolbar.addView(new View(this),new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView title=text(getIntent().getStringExtra("title"),21);title.setTypeface(Typeface.SERIF,Typeface.BOLD);
        title.setPadding(0,dp(17),0,dp(10));root.addView(title);
        state=text("Загружаю книги цикла…",14);state.setTextColor(GRAY);root.addView(state);
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        adapter=new RecyclerView.Adapter<Row>(){
            @Override public Row onCreateViewHolder(ViewGroup parent,int viewType){
                LinearLayout card=new LinearLayout(KvuheSeriesActivity.this);card.setOrientation(LinearLayout.HORIZONTAL);card.setPadding(dp(10),dp(10),dp(10),dp(10));card.setBackground(bg(WHITE));
                RecyclerView.LayoutParams clp=new RecyclerView.LayoutParams(-1,-2);clp.bottomMargin=dp(10);card.setLayoutParams(clp);
                ImageView cover=new ImageView(KvuheSeriesActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(cover,new LinearLayout.LayoutParams(dp(92),dp(128)));
                LinearLayout side=new LinearLayout(KvuheSeriesActivity.this);side.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,-2,1);s.leftMargin=dp(12);card.addView(side,s);
                TextView name=text("",18);name.setTypeface(Typeface.SERIF,Typeface.BOLD);side.addView(name);
                TextView extra=text("",13);extra.setTextColor(GRAY);side.addView(extra);
                TextView open=text("Открыть главы ›",13);open.setPadding(0,dp(9),0,0);side.addView(open);
                return new Row(card,cover,name,extra);
            }
            @Override public void onBindViewHolder(Row h,int position){KvuheSearchParser.Entry entry=books.get(position);
                h.name.setText(entry.title);h.extra.setText((entry.author+" · "+entry.reader).replaceAll("^\\s*·\\s*|\\s*·\\s*$",""));
                CoverImageLoader.show(h.cover,entry.cover,false);
                h.itemView.setOnClickListener(v->startActivity(new Intent(KvuheSeriesActivity.this,KvuheBookActivity.class)
                        .putExtra("source",entry.url).putExtra("title",entry.title).putExtra("author",entry.author)
                        .putExtra("reader",entry.reader).putExtra("description",entry.description).putExtra("cover",entry.cover).putExtra("auto_fetch",true)));
            }
            @Override public int getItemCount(){return books.size();}
        };list.setAdapter(adapter);
        String url=getIntent().getStringExtra("source");
        new Thread(()->{try{KvuheSearchParser.Page p=KvuheSearchParser.fetchSeries(url);
            runOnUiThread(()->{if(isFinishing())return;books.addAll(p.entries);adapter.notifyDataSetChanged();
                state.setText(books.isEmpty()?"В цикле не удалось найти ссылки на книги":"Аудиокниг в списке: "+books.size());});
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing())state.setText("Ошибка загрузки цикла: "+e.getMessage());});}},"knigavuhe-series").start();
    }
    private static final class Row extends RecyclerView.ViewHolder{
        final ImageView cover;final TextView name,extra;
        Row(View v,ImageView c,TextView n,TextView e){super(v);cover=c;name=n;extra=e;}
    }
}
