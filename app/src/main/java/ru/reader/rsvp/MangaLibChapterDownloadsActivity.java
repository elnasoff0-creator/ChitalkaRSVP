package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/** Detailed per-chapter manga download progress, matching the Edge audio manager. */
public final class MangaLibChapterDownloadsActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949;
    private final Handler main=new Handler(Looper.getMainLooper());
    private String slug="",series="";
    private TextView summary,action,bookTitle;
    private ScrollView scroll;
    private LinearLayout entries;
    private boolean visible;
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(!visible)return;drawStatus();main.postDelayed(this,2500);
    }};
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable surface(){GradientDrawable d=new GradientDrawable();d.setColor(PANEL);d.setCornerRadius(dp(14));return d;}
    private TextView text(String label,int size,boolean bold){
        TextView v=new TextView(this);v.setText(label);v.setTextSize(size);v.setTextColor(INK);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;
    }
    private TextView button(String title,Runnable command){
        TextView v=text(title,14,true);v.setGravity(Gravity.CENTER);v.setSingleLine(true);
        v.setPadding(dp(10),dp(10),dp(10),dp(10));v.setBackground(surface());
        v.setOnClickListener(w->command.run());return v;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        slug=getIntent().getStringExtra("mangalib_slug");
        if(slug==null||slug.isEmpty()){finish();return;}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);root.setPadding(dp(14),dp(22),dp(14),dp(8));
        setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        TextView menu=button("☰",()->UniversalDrawer.show(this));header.addView(menu,new LinearLayout.LayoutParams(dp(46),dp(46)));
        TextView title=text("Загрузка манги",22,true);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(12);
        header.addView(title,titleLp);
        bookTitle=text("Манга · MangaLib",15,false);bookTitle.setTextColor(MUTED);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(12);root.addView(bookTitle,bp);
        summary=text("Получение информации о страницах…",14,false);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.topMargin=dp(12);root.addView(summary,sp);
        LinearLayout controls=new LinearLayout(this);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(12);root.addView(controls,cp);
        TextView all=button("Скачать все",()->{
            int n=MangaLibQueue.resume(this,slug);
            if(n==0)android.widget.Toast.makeText(this,"Нет глав для повторной загрузки",android.widget.Toast.LENGTH_SHORT).show();
            drawStatus();
        });controls.addView(all,new LinearLayout.LayoutParams(0,dp(47),1));
        action=button("Пауза",()->{
            boolean running=false;
            for(MangaLibStorage.Job job:MangaLibStorage.jobs(this))if(slug.equals(job.slug)
                &&("running".equals(job.status)||"queued".equals(job.status))){running=true;break;}
            if(running)MangaLibQueue.pause(this,slug);else MangaLibQueue.resume(this,slug);
            drawStatus();
        });LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(47),1);
        ap.leftMargin=dp(8);controls.addView(action,ap);
        TextView hint=text("Нажмите на главу для повторной загрузки. Скачанные страницы остаются в кэше при паузе.",12,false);
        hint.setTextColor(MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);
        hp.topMargin=dp(9);root.addView(hint,hp);
        scroll=new ScrollView(this);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);
        lp.topMargin=dp(10);root.addView(scroll,lp);
        entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);scroll.addView(entries);
        drawStatus();
    }
    private static String state(MangaLibStorage.Job j){
        switch(j.status){case "running":return "скачивается";case "queued":return "в очереди";
            case "paused":return "приостановлено";case "complete":return "готово";
            case "failed":return "ошибка";case "blocked":return "недоступно";
            case "cancelled":return "отменено";default:return "ожидание";}
    }
    private static String megabytes(long bytes){return String.format(Locale.ROOT,"%.1f МБ",bytes/1048576.0);}
    private void drawStatus(){
        ArrayList<MangaLibStorage.Job> items=new ArrayList<>();long bytes=0;
        int current=0,total=0,unknown=0;boolean active=false;
        for(MangaLibStorage.Job j:MangaLibStorage.jobs(this))if(slug.equals(j.slug)){
            items.add(j);if(series.isEmpty())series=j.series;
            current+=j.done;total+=j.total;bytes+=j.bytes;
            if(j.total==0)unknown++;
            if("running".equals(j.status)||"queued".equals(j.status))active=true;
        }
        if(bookTitle!=null)bookTitle.setText(series.isEmpty()?"Манга · MangaLib":series);
        items.sort((a,b)->{
            int v=compare(a.volume,b.volume);return v!=0?v:compare(a.number,b.number);
        });
        summary.setText("Глав: "+items.size()+" · Страниц: "+current+"/"+(unknown>0?"?":total)
            +" · "+(total>0?(100*current/Math.max(1,total))+"%": "ожидание")+" · "+megabytes(bytes));
        if(action!=null)action.setText(active?"Пауза":"Продолжить");
        // Preserve the scroll position across status refreshes.
        final int previous=scroll.getScrollY();entries.removeAllViews();
        String lastVolume=null;
        for(MangaLibStorage.Job job:items){
            if(lastVolume==null||!lastVolume.equals(job.volume)){
                lastVolume=job.volume;
                int chapters=0,finished=0,all=0,unknownPages=0;long volumeBytes=0;
                for(MangaLibStorage.Job sibling:items)if(sibling.volume.equals(lastVolume)){
                    chapters++;finished+=sibling.done;all+=sibling.total;
                    if(sibling.total==0)unknownPages++;volumeBytes+=sibling.bytes;
                }
                TextView volume=text("Том "+lastVolume+" · "+chapters+" глав · "
                    +finished+"/"+(unknownPages>0?"?":all)+" стр. · "+megabytes(volumeBytes),14,true);
                volume.setPadding(dp(4),dp(9),dp(4),dp(9));entries.addView(volume);
            }
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(surface());card.setPadding(dp(12),dp(10),dp(12),dp(10));
            LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.bottomMargin=dp(8);
            entries.addView(card,outer);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);card.addView(row);
            TextView name=text(job.chapter,15,true);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView pc=text(job.total==0?"—":(job.done*100/Math.max(1,job.total))+"%",14,true);
            pc.setTextColor(UiColorScheme.accent(this));row.addView(pc);
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);bar.setProgress(job.total==0?0:100*job.done/Math.max(1,job.total));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(6));bp.topMargin=dp(7);card.addView(bar,bp);
            TextView meta=text(job.done+"/"+(job.total==0?"?":job.total)+" стр. · "+megabytes(job.bytes)
                +" · "+state(job),12,false);meta.setTextColor(MUTED);
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(5);card.addView(meta,mp);
            if(job.error!=null&&!job.error.isEmpty()){
                TextView error=text(job.error,12,false);error.setTextColor(0xffa13d36);card.addView(error);
            }
            card.setOnClickListener(v->{
                if("complete".equals(job.status)){
                    startActivity(new Intent(this,ComicReaderActivity.class)
                        .putExtra("comic_uri",MangaLibStorage.comicUri(slug)));
                }else if(!"running".equals(job.status)&&!"queued".equals(job.status)){
                    java.util.ArrayList<MangaLibStorage.Job> single=new java.util.ArrayList<>();single.add(job);
                    MangaLibQueue.enqueue(this,single);drawStatus();
                }
            });
        }
        scroll.post(()->scroll.scrollTo(0,previous));
    }
    private static int compare(String a,String b){
        try{return Double.compare(Double.parseDouble(a),Double.parseDouble(b));}
        catch(Exception e){return a.compareTo(b);}
    }
    @Override protected void onResume(){super.onResume();visible=true;main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacksAndMessages(null);super.onDestroy();}
}
