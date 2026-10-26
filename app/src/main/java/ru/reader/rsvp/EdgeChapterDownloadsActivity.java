package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Chapter-organized offline Edge manager; its percentages are verified MP3
 * coverage of actual book words, not incomplete request counters.
 */
public final class EdgeChapterDownloadsActivity extends Activity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String uri="",title="";
    private TextView summary;
    private LinearLayout entries;
    private boolean visible,loading;
    private final ArrayList<Entry> displayed=new ArrayList<>();
    private final ArrayList<ChapterViews> chapterViews=new ArrayList<>();
    private ArrayList<Integer> offsets=new ArrayList<>();
    private BookChapterIndex toc=BookChapterIndex.empty();
    private static final class Entry {String title; int chapter,from,to; EdgeOfflineIndex.Stats stats;}
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(!visible)return;reload();main.postDelayed(this,3000);
    }};
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable rounded(int fill){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(13));return d;}
    private TextView text(String value,int size,boolean bold){TextView view=new TextView(this);
        view.setText(value);view.setTextSize(size);view.setTextColor(INK);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
    }
    private TextView button(String name,Runnable action){TextView button=text(name,14,true);
        button.setGravity(Gravity.CENTER);button.setBackground(rounded(PANEL));
        button.setPadding(dp(9),dp(11),dp(9),dp(11));button.setOnClickListener(v->action.run());return button;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        uri=getIntent().getStringExtra("book_uri");
        title=getIntent().getStringExtra("book_title");
        if(uri==null||uri.isEmpty()){finish();return;}
        if(title==null||title.isEmpty())title="Книга";
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
        root.setPadding(dp(14),dp(24),dp(14),dp(10));setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        TextView back=button("‹",()->BackNavigation.back(this));header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        TextView label=text("Загрузка озвучки",22,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.leftMargin=dp(10);header.addView(label,lp);
        TextView book=text(title,15,false);book.setTextColor(MUTED);LinearLayout.LayoutParams bookLp=new LinearLayout.LayoutParams(-1,-2);
        bookLp.topMargin=dp(13);root.addView(book,bookLp);
        summary=text("Проверка сохранённых MP3…",14,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin=dp(12);root.addView(summary,sp);
        LinearLayout controls=new LinearLayout(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(12);root.addView(controls,cp);
        TextView all=button("Подготовить все",()->command(-1));controls.addView(all,new LinearLayout.LayoutParams(0,dp(47),1));
        TextView pause=button("Пауза / Продолжить",this::toggle);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(47),1);
        pp.leftMargin=dp(8);controls.addView(pause,pp);
        TextView hint=text("Нажмите на главу, чтобы подготовить только её. Скачанные MP3 сохраняются и при повторном запуске используются из кэша.",12,false);
        hint.setTextColor(MUTED);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(9);root.addView(hint,hp);
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);
        sl.topMargin=dp(10);root.addView(scroll,sl);
        entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);scroll.addView(entries);
        worker.execute(()->{
            try{
                BookLoader.Book b=BookLoader.load(this,Uri.parse(uri));
                ArrayList<Integer> words=SentenceChunks.wordPositions(b.text==null?"":b.text);
                BookChapterIndex chapters=b.chapters;
                main.post(()->{offsets=words;toc=chapters;reload();});
            }catch(Exception e){main.post(()->summary.setText("Не удалось открыть оглавление: "+e.getMessage()));}
        });
    }
    private void command(int chapter){
        android.content.SharedPreferences pref=getSharedPreferences("reader",0);
        Intent intent=new Intent(this,EdgeFullBookService.class).setAction(EdgeFullBookService.ADD)
            .putExtra(EdgeFullBookService.EXTRA_URI,uri).putExtra(EdgeFullBookService.EXTRA_TITLE,title)
            .putExtra(EdgeFullBookService.EXTRA_VOICE,pref.getString("edge_voice","ru-RU-SvetlanaNeural"))
            .putExtra(EdgeFullBookService.EXTRA_RATE,pref.getString("edge_rate","+0%"))
            .putExtra(EdgeFullBookService.EXTRA_CHARS,EdgeSegmentSettings.get(pref))
            .putExtra(EdgeFullBookService.EXTRA_CHAPTER,chapter);
        launch(intent);
    }
    private void toggle(){
        EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
        if(job==null){command(-1);return;}
        boolean running=EdgeFullBookStore.RUNNING.equals(job.status)||EdgeFullBookStore.QUEUED.equals(job.status);
        launch(new Intent(this,EdgeFullBookService.class)
            .setAction(running?EdgeFullBookService.PAUSE:EdgeFullBookService.RESUME)
            .putExtra(EdgeFullBookService.EXTRA_URI,uri));
    }
    private void launch(Intent request){try{
        if(Build.VERSION.SDK_INT>=26)startForegroundService(request);else startService(request);
        main.postDelayed(this::reload,700);
    }catch(Exception e){summary.setText("Не удалось запустить подготовку: "+e.getMessage());}}
    private void reload(){
        if(!visible||loading||offsets.isEmpty())return;
        loading=true;
        final File audio=new File(EdgeBookStorage.bookFolder(this,uri,title),"EdgeTTS");
        final ArrayList<Integer> positions=new ArrayList<>(offsets);
        final BookChapterIndex chapters=toc;
        worker.execute(()->{
            ArrayList<EdgeOfflineIndex.Segment> segments=EdgeAudioManager.segments(audio);
            EdgeOfflineIndex.Stats total=EdgeOfflineIndex.stats(segments,0,positions.size());
            ArrayList<Entry> result=new ArrayList<>();
            if(chapters.size()==0){
                Entry entry=new Entry();entry.title="Вся книга";entry.chapter=-1;entry.from=0;entry.to=positions.size();
                entry.stats=total;result.add(entry);
            }else for(int i=0;i<chapters.size();i++){
                BookChapterIndex.Chapter chapter=chapters.get(i);Entry entry=new Entry();
                entry.title=chapter.title;entry.chapter=i;
                entry.from=SentenceChunks.lowerBound(positions,chapter.startChar);
                entry.to=Math.max(entry.from,SentenceChunks.lowerBound(positions,chapter.endChar));
                entry.stats=EdgeOfflineIndex.stats(segments,entry.from,entry.to);result.add(entry);
            }
            EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
            main.post(()->{loading=false;if(!visible)return;
                String status=job==null?"":(" · "+(EdgeFullBookStore.RUNNING.equals(job.status)?"загрузка":job.status));
                summary.setText("Сохранено "+total.percent()+"% текста · "+total.files+" MP3 · "+
                    String.format(Locale.ROOT,"%.1f МБ",total.bytes/1048576.0)+status);
                if(chapterViews.size()!=result.size()||!sameChapters(result)){
                    entries.removeAllViews();chapterViews.clear();displayed.clear();
                    for(Entry entry:result){addEntry(entry);displayed.add(entry);}
                }else{
                    for(int i=0;i<result.size();i++){
                        Entry entry=result.get(i);
                        ChapterViews view=chapterViews.get(i);
                        if(!sameStats(displayed.get(i),entry)){
                            view.percent.setText(entry.stats.percent()+"%");
                            view.bar.setProgress(entry.stats.percent());
                            view.details.setText(details(entry));
                        }
                        displayed.set(i,entry);
                    }
                }
            });
        });
    }
    private static boolean sameStats(Entry a,Entry b){
        return a.stats.covered==b.stats.covered&&a.stats.bytes==b.stats.bytes&&a.stats.files==b.stats.files;
    }
    private boolean sameChapters(ArrayList<Entry> next){
        if(displayed.size()!=next.size())return false;
        for(int i=0;i<next.size();i++)if(displayed.get(i).chapter!=next.get(i).chapter
            ||!displayed.get(i).title.equals(next.get(i).title))return false;
        return true;
    }
    private static String details(Entry item){
        return item.stats.files+" MP3 · "+String.format(Locale.ROOT,"%.1f МБ",item.stats.bytes/1048576.0)
            +(item.stats.complete()?" · готово":" · нажмите для загрузки");
    }
    private static final class ChapterViews {
        final TextView percent,details;final ProgressBar bar;
        ChapterViews(TextView percent,TextView details,ProgressBar bar){this.percent=percent;this.details=details;this.bar=bar;}
    }
    private void addEntry(Entry item){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(PANEL));card.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.bottomMargin=dp(8);
        entries.addView(card,outer);
        LinearLayout headline=new LinearLayout(this);headline.setGravity(Gravity.CENTER_VERTICAL);card.addView(headline);
        TextView titleText=text(item.title,15,true);headline.addView(titleText,new LinearLayout.LayoutParams(0,-2,1));
        TextView progressText=text(item.stats.percent()+"%",14,true);progressText.setTextColor(ACCENT);headline.addView(progressText);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgress(item.stats.percent());
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(6));bar.topMargin=dp(7);card.addView(progress,bar);
        TextView meta=text(details(item),12,false);
        meta.setTextColor(MUTED);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);ml.topMargin=dp(5);card.addView(meta,ml);
        card.setOnClickListener(v->command(item.chapter));
        chapterViews.add(new ChapterViews(progressText,meta,progress));
    }
    @Override protected void onResume(){super.onResume();visible=true;main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){visible=false;main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
