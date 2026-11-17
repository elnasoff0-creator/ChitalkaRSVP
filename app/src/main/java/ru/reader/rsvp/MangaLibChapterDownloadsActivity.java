package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** v174: event-driven, virtualized MangaLib chapter progress. No 2.5 s full re-render. */
public final class MangaLibChapterDownloadsActivity extends NavigationActivity {
    private int BG,PANEL,INK,MUTED,ACCENT,paletteSignature;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String slug="",series="";
    private TextView summary,action,bookTitle;
    private RecyclerView chapters;
    private ChapterAdapter adapter;
    private boolean visible;
    private int refreshGeneration;
    private boolean refreshScheduled;
    private final DownloadChangeBus.Listener listener=id->{
        if(id!=null&&id.equals("manga:"+slug.trim().toLowerCase(Locale.ROOT)))requestRefresh();
    };
    private final Runnable deferredRefresh=()->{refreshScheduled=false;drawStatus();};
    private void readPalette(){
        BG=UiColorScheme.background(this);PANEL=UiColorScheme.neutral(this);
        INK=UiColorScheme.foreground(this);MUTED=UiColorScheme.muted(this);
        ACCENT=UiColorScheme.accent(this);paletteSignature=UiColorScheme.paletteSignature(this);
    }
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable surface(){GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(14));return d;}
    private TextView text(String value,int size,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(INK);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private TextView button(String name,Runnable command){
        TextView v=text(name,14,true);v.setGravity(Gravity.CENTER);v.setSingleLine(true);
        v.setPadding(dp(10),dp(10),dp(10),dp(10));v.setBackground(surface());
        v.setOnClickListener(w->command.run());return v;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        readPalette();slug=getIntent().getStringExtra("mangalib_slug");
        if(slug==null||slug.isEmpty()){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);root.setPadding(dp(14),dp(22),dp(14),dp(8));
        setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        TextView menu=button("☰",()->UniversalDrawer.show(this));
        header.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(46)));
        TextView title=text("Загрузка манги",22,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(12);
        header.addView(title,titleLp);
        bookTitle=text("Манга · MangaLib",15,false);bookTitle.setTextColor(MUTED);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(12);
        root.addView(bookTitle,bp);
        summary=text("Получение информации о страницах…",14,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);
        root.addView(summary,sp);
        LinearLayout controls=new LinearLayout(this);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(12);
        root.addView(controls,cp);
        TextView all=button("Скачать все",()->io.execute(()->{
            int count=MangaLibQueue.resume(this,slug);
            main.post(()->{
                if(!visible)return;
                if(count==0)android.widget.Toast.makeText(this,"Нет глав для повторной загрузки",
                    android.widget.Toast.LENGTH_SHORT).show();
                requestRefresh();
            });
        }));
        controls.addView(all,new LinearLayout.LayoutParams(0,dp(47),1));
        action=button("Пауза",()->io.execute(()->{
            boolean running=false;
            for(MangaLibStorage.Job j:MangaLibStorage.jobs(this))if(slug.equals(j.slug)
                &&("running".equals(j.status)||"queued".equals(j.status))){running=true;break;}
            if(running)MangaLibQueue.pause(this,slug);else MangaLibQueue.resume(this,slug);
            main.post(this::requestRefresh);
        }));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(47),1);
        ap.leftMargin=dp(8);controls.addView(action,ap);
        TextView hint=text("Нажмите на главу для повторной загрузки. Скачанные страницы сохраняются при паузе.",12,false);
        hint.setTextColor(MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(9);root.addView(hint,hp);
        chapters=new RecyclerView(this);
        chapters.setLayoutManager(new LinearLayoutManager(this));
        chapters.setItemAnimator(null);adapter=new ChapterAdapter();chapters.setAdapter(adapter);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);
        lp.topMargin=dp(10);root.addView(chapters,lp);
        drawStatus();
    }
    private static String state(MangaLibStorage.Job j){
        switch(j.status){case "running":return "скачивается";case "queued":return "в очереди";
            case "paused":return "приостановлено";case "complete":return "готово";
            case "failed":return "ошибка";case "blocked":return "недоступно";
            case "cancelled":return "отменено";default:return "ожидание";}
    }
    private static String megabytes(long bytes){return String.format(Locale.ROOT,"%.1f МБ",bytes/1048576.0);}
    private static int compare(String a,String b){
        try{return Double.compare(Double.parseDouble(a),Double.parseDouble(b));}
        catch(Exception e){return a.compareTo(b);}
    }
    private static final class Row {
        final String id,title,detail,error,volume;
        final int percent;final MangaLibStorage.Job job;
        Row(String id,String title,String detail,String error,String volume,int percent,MangaLibStorage.Job job){
            this.id=id;this.title=title;this.detail=detail;this.error=error;
            this.volume=volume;this.percent=percent;this.job=job;
        }
        boolean header(){return job==null;}
        boolean same(Row r){return percent==r.percent&&title.equals(r.title)
            &&detail.equals(r.detail)&&error.equals(r.error);}
    }
    private static final class Totals {
        int count,done,pages,unknown;long bytes;
        void add(MangaLibStorage.Job job){count++;done+=job.done;pages+=job.total;
            if(job.total==0)unknown++;bytes+=job.bytes;}
    }
    private static final class Snapshot {
        final ArrayList<Row> rows=new ArrayList<>();String name="",summary="";boolean active;
    }
    private Snapshot computeSnapshot(){
        Snapshot result=new Snapshot();ArrayList<MangaLibStorage.Job> items=new ArrayList<>();
        Totals sum=new Totals();int completed=0;
        for(MangaLibStorage.Job job:MangaLibStorage.jobs(this))if(slug.equals(job.slug)){
            items.add(job);sum.add(job);if(result.name.isEmpty())result.name=job.series;
            if("complete".equals(job.status))completed++;
            if("running".equals(job.status)||"queued".equals(job.status))result.active=true;
        }
        items.sort((a,b)->{int d=compare(a.volume,b.volume);return d!=0?d:compare(a.number,b.number);});
        String percent=sum.unknown>0?("Глав готово: "+completed+"/"+sum.count):
            sum.pages>0?(100*sum.done/Math.max(1,sum.pages))+"%":"подготовка";
        String pages=sum.unknown>0?(sum.done==0?"Страницы: подготовка":
            "Страниц скачано: "+sum.done):"Страниц: "+sum.done+"/"+sum.pages;
        result.summary="Глав: "+sum.count+" · "+pages+" · "+percent+" · "+megabytes(sum.bytes);
        LinkedHashMap<String,Totals> volumes=new LinkedHashMap<>();
        for(MangaLibStorage.Job j:items){
            Totals stats=volumes.get(j.volume);
            if(stats==null){stats=new Totals();volumes.put(j.volume,stats);}stats.add(j);
        }
        String previous=null;
        for(MangaLibStorage.Job j:items){
            if(previous==null||!previous.equals(j.volume)){
                previous=j.volume;Totals stats=volumes.get(previous);
                String vPages=stats.unknown>0?(stats.done==0?"подготовка страниц":
                    "скачано "+stats.done+" стр."):(stats.done+"/"+stats.pages+" стр.");
                result.rows.add(new Row("volume:"+previous,"Том "+previous+" · "+stats.count+
                    " глав · "+vPages+" · "+megabytes(stats.bytes),"","",previous,0,null));
            }
            int pct=j.total==0?0:100*j.done/Math.max(1,j.total);
            String jPages=j.total==0?(j.done==0?"подготовка страниц":
                "скачано "+j.done+" стр."):(j.done+"/"+j.total+" стр.");
            result.rows.add(new Row("chapter:"+j.id(),j.chapter,
                jPages+" · "+megabytes(j.bytes)+" · "+state(j),j.error==null?"":j.error,
                j.volume,pct,j));
        }
        return result;
    }
    private void requestRefresh(){
        if(!visible)return;
        if(refreshScheduled)return;
        refreshScheduled=true;
        main.postDelayed(deferredRefresh,320L); // bounded coalescing, never starve progress
    }
    private void drawStatus(){
        final int generation=++refreshGeneration;
        io.execute(()->{
            Snapshot next=computeSnapshot();
            main.post(()->{
                if(isFinishing()||isDestroyed()||generation!=refreshGeneration)return;
                if(bookTitle!=null)bookTitle.setText(next.name.isEmpty()?"Манга · MangaLib":next.name);
                if(summary!=null)summary.setText(next.summary);
                if(action!=null)action.setText(next.active?"Пауза":"Продолжить");
                if(adapter!=null)adapter.submit(next.rows);
            });
        });
    }
    private final class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.Holder> {
        private final AsyncListDiffer<Row> differ=new AsyncListDiffer<>(this,
            new DiffUtil.ItemCallback<Row>(){
                @Override public boolean areItemsTheSame(Row a,Row b){return a.id.equals(b.id);}
                @Override public boolean areContentsTheSame(Row a,Row b){return a.same(b);}
            });
        void submit(List<Row> rows){differ.submitList(new ArrayList<>(rows));}
        @Override public int getItemViewType(int at){return differ.getCurrentList().get(at).header()?0:1;}
        @Override public int getItemCount(){return differ.getCurrentList().size();}
        final class Holder extends RecyclerView.ViewHolder {
            TextView title,percent,description,error;ProgressBar bar;
            Holder(View view){super(view);}
        }
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            if(type==0){TextView header=text("",14,true);
                header.setPadding(dp(4),dp(9),dp(4),dp(9));
                Holder holder=new Holder(header);holder.title=header;return holder;
            }
            LinearLayout card=new LinearLayout(MangaLibChapterDownloadsActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);card.setBackground(surface());
            card.setPadding(dp(12),dp(10),dp(12),dp(10));
            RecyclerView.LayoutParams params=new RecyclerView.LayoutParams(-1,-2);
            params.bottomMargin=dp(8);card.setLayoutParams(params);
            Holder holder=new Holder(card);
            LinearLayout row=new LinearLayout(MangaLibChapterDownloadsActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);
            holder.title=text("",15,true);row.addView(holder.title,new LinearLayout.LayoutParams(0,-2,1));
            holder.percent=text("",14,true);holder.percent.setTextColor(ACCENT);row.addView(holder.percent);
            holder.bar=new ProgressBar(MangaLibChapterDownloadsActivity.this,null,
                android.R.attr.progressBarStyleHorizontal);
            holder.bar.setMax(100);UiColorScheme.tintProgress(holder.bar,MangaLibChapterDownloadsActivity.this);
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(6));
            bp.topMargin=dp(7);card.addView(holder.bar,bp);
            holder.description=text("",12,false);holder.description.setTextColor(MUTED);
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);
            mp.topMargin=dp(5);card.addView(holder.description,mp);
            holder.error=text("",12,false);holder.error.setTextColor(UiColorScheme.error(MangaLibChapterDownloadsActivity.this));
            card.addView(holder.error);
            return holder;
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            Row row=differ.getCurrentList().get(position);
            holder.title.setText(row.title);
            if(row.header())return;
            holder.percent.setText(row.job.total==0?"—":row.percent+"%");
            holder.bar.setProgress(row.percent);
            holder.description.setText(row.detail);
            holder.error.setText(row.error);
            holder.error.setVisibility(row.error.isEmpty()?View.GONE:View.VISIBLE);
            holder.itemView.setOnClickListener(v->{
                MangaLibStorage.Job job=row.job;
                if("complete".equals(job.status)){
                    startActivity(new Intent(MangaLibChapterDownloadsActivity.this,ComicReaderActivity.class)
                        .putExtra("comic_uri",MangaLibStorage.comicUri(slug)));
                }else if(!"running".equals(job.status)&&!"queued".equals(job.status)){
                    io.execute(()->{
                        ArrayList<MangaLibStorage.Job> one=new ArrayList<>();one.add(job);
                        MangaLibQueue.enqueue(MangaLibChapterDownloadsActivity.this,one);
                        main.post(MangaLibChapterDownloadsActivity.this::requestRefresh);
                    });
                }
            });
        }
    }
    @Override protected void onResume(){super.onResume();
        if(paletteSignature!=UiColorScheme.paletteSignature(this)){recreate();return;}
        visible=true;DownloadChangeBus.add(listener);drawStatus();
    }
    @Override protected void onPause(){visible=false;DownloadChangeBus.remove(listener);
        main.removeCallbacks(deferredRefresh);refreshScheduled=false;super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdown();super.onDestroy();}
}
