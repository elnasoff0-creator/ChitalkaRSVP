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
import android.widget.ImageView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
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
public final class EdgeChapterDownloadsActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String uri="",title="";
    private int pendingFolderChapter=-2;
    private TextView summary,pauseButton,totalPercent,totalDetail;
    private ProgressBar totalProgress;
    private LinearLayout entries;
    private boolean visible,loading;
    private long lastSegmentsAt;
    private ArrayList<EdgeOfflineIndex.Segment> lastSegments=new ArrayList<>();
    private final java.util.Set<Integer> merging=new java.util.HashSet<>();
    private final ArrayList<Entry> displayed=new ArrayList<>();
    private final ArrayList<ChapterViews> chapterViews=new ArrayList<>();
    private ArrayList<Integer> offsets=new ArrayList<>();
    private BookChapterIndex toc=BookChapterIndex.empty();
    private static final class Entry {
        String title,state="",error="";int chapter,from,to,saved,jobDone,jobTotal;
        EdgeOfflineIndex.Stats stats;
    }
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
        TextView back=button("☰",()->UniversalDrawer.show(this));header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(46)));
        TextView label=text("Загрузка озвучки",22,true);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        lp.leftMargin=dp(10);header.addView(label,lp);
        TextView book=text(title,15,false);book.setTextColor(MUTED);LinearLayout.LayoutParams bookLp=new LinearLayout.LayoutParams(-1,-2);
        bookLp.topMargin=dp(13);root.addView(book,bookLp);
        summary=text("Проверка сохранённых MP3…",14,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin=dp(12);root.addView(summary,sp);
        LinearLayout overall=new LinearLayout(this);overall.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ol=new LinearLayout.LayoutParams(-1,-2);ol.topMargin=dp(9);
        root.addView(overall,ol);
        TextView overallTitle=text("Общий прогресс книги",14,true);
        overall.addView(overallTitle,new LinearLayout.LayoutParams(0,-2,1));
        totalPercent=text("0%",14,true);overall.addView(totalPercent);
        totalProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        totalProgress.setMax(100);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(8));
        op.topMargin=dp(6);root.addView(totalProgress,op);
        totalDetail=text("Ожидаем подготовку глав",12,false);totalDetail.setTextColor(MUTED);
        LinearLayout.LayoutParams od=new LinearLayout.LayoutParams(-1,-2);od.topMargin=dp(5);
        root.addView(totalDetail,od);
        LinearLayout controls=new LinearLayout(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(12);root.addView(controls,cp);
        TextView all=button("Подготовить все",()->command(-1));controls.addView(all,new LinearLayout.LayoutParams(0,dp(47),1));
        pauseButton=button("Пауза",this::toggle);pauseButton.setSingleLine(true);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(47),1);
        pp.leftMargin=dp(8);controls.addView(pauseButton,pp);
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
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(EdgeFolderPicker.accept(this,req,result,data)){
            int chapter=pendingFolderChapter;pendingFolderChapter=-2;
            if(chapter!=-2&&EdgeFolderPicker.available(this))command(chapter);
        }
    }
    private void command(int chapter){command(chapter,false);}
    private void command(int chapter,boolean force){
        if(!EdgeFolderPicker.available(this)){
            pendingFolderChapter=chapter;
            new SwipeDialogs.Builder(this).setTitle("Папка Edge TTS")
                .setMessage("Выберите папку для сохранения завершённых аудиоглав.")
                .setNegativeButton("Отмена",(d,w)->pendingFolderChapter=-2)
                .setPositiveButton("Использовать эту папку",(d,w)->EdgeFolderPicker.choose(this)).show();
            return;
        }
        for(int i=0;i<displayed.size();i++)if(chapter==-1||displayed.get(i).chapter==chapter){
            displayed.get(i).state=force?"Перезагрузка":"В очереди";
            if(i<chapterViews.size())chapterViews.get(i).details.setText(displayed.get(i).state);
        }
        android.content.SharedPreferences pref=getSharedPreferences("reader",0);
        Intent intent=new Intent(this,EdgeFullBookService.class).setAction(EdgeFullBookService.ADD)
            .putExtra(EdgeFullBookService.EXTRA_URI,uri).putExtra(EdgeFullBookService.EXTRA_TITLE,title)
            .putExtra(EdgeFullBookService.EXTRA_VOICE,pref.getString("edge_voice","ru-RU-SvetlanaNeural"))
            .putExtra(EdgeFullBookService.EXTRA_RATE,pref.getString("edge_rate","+0%"))
            .putExtra(EdgeFullBookService.EXTRA_CHARS,EdgeSegmentSettings.get(pref))
            .putExtra(EdgeFullBookService.EXTRA_CHAPTER,chapter)
            .putExtra(EdgeFullBookService.EXTRA_FORCE,force);
        launch(intent);
    }
    private void toggle(){
        EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
        if(job==null){command(-1);return;}
        boolean running=EdgeFullBookService.isServiceAlive()&&
            (EdgeFullBookStore.RUNNING.equals(job.status)||EdgeFullBookStore.QUEUED.equals(job.status)
            ||EdgeFullBookStore.WAITING.equals(job.status)||EdgeFullBookStore.SAVING.equals(job.status));
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
            try{
                EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
                long now=android.os.SystemClock.elapsedRealtime();
                // File scans are throttled. Job states refresh independently on each tick.
                if(lastSegmentsAt==0||now-lastSegmentsAt>11000L){
                    lastSegments=EdgeAudioManager.segments(audio);lastSegmentsAt=now;
                }
                ArrayList<EdgeOfflineIndex.Segment> segments=lastSegments;
                EdgeOfflineIndex.Stats total=EdgeOfflineIndex.stats(segments,0,positions.size());
                EdgeChapterArchive.ChapterSnapshot saved=EdgeChapterArchive.snapshot(this,uri,title);
                ArrayList<Entry> result=new ArrayList<>();
                if(chapters.size()==0){
                    Entry item=new Entry();item.title="Вся книга";item.chapter=-1;
                    item.from=0;item.to=positions.size();item.stats=total;result.add(item);
                }else for(int i=0;i<chapters.size();i++){
                    BookChapterIndex.Chapter chapter=chapters.get(i);Entry item=new Entry();
                    item.title=chapter.title;item.chapter=i;
                    item.from=SentenceChunks.lowerBound(positions,chapter.startChar);
                    item.to=Math.max(item.from,SentenceChunks.lowerBound(positions,chapter.endChar));
                    item.stats=EdgeOfflineIndex.stats(segments,item.from,item.to);result.add(item);
                }
                for(Entry item:result){
                    item.saved=saved.state(Math.max(0,item.chapter));
                    if(merging.contains(item.chapter)){item.state="Объединение MP3";continue;}
                    EdgeFullBookStore.Task task=EdgeFullBookStore.task(job,item.chapter);
                    if(task!=null){item.jobDone=task.done;item.jobTotal=task.total;}
                    boolean pending=task!=null&&(EdgeFullBookStore.QUEUED.equals(task.status)||
                        EdgeFullBookStore.RUNNING.equals(task.status)||
                        EdgeFullBookStore.WAITING.equals(task.status)||
                        EdgeFullBookStore.SAVING.equals(task.status));
                    // A running/failed reload takes precedence over the old
                    // published MP3; keep error feedback even if the backup is playable.
                    boolean failed=task!=null&&EdgeFullBookStore.FAILED.equals(task.status);
                    if(!pending&&!failed&&item.saved==2){item.state="Готов единый MP3";continue;}
                    if(!pending&&!failed&&item.saved==1){item.state="Фрагменты готовы";continue;}
                    if(task!=null){
                        if(EdgeFullBookStore.QUEUED.equals(task.status))item.state="В очереди";
                        else if(EdgeFullBookStore.RUNNING.equals(task.status))item.state="Идёт загрузка";
                        else if(EdgeFullBookStore.WAITING.equals(task.status))item.state="Ожидание Edge TTS";
                        else if(EdgeFullBookStore.SAVING.equals(task.status))item.state="Сохранение / сборка MP3";
                        else if(EdgeFullBookStore.PAUSED.equals(task.status)){
                            item.state="Приостановлено";item.error=task.error;
                        }
                        else if(EdgeFullBookStore.FAILED.equals(task.status)){
                            item.state="Ошибка";item.error=task.error;
                        }else if(EdgeFullBookStore.DONE.equals(task.status))item.state="Подготовка завершена";
                    }
                    if(item.state.isEmpty())item.state=item.stats.complete()?"Фрагменты во временном кэше":"Нажмите для загрузки";
                }
                int published=0,pendingCount=0,activeChapter=-1;
                long weighted=0,weightTotal=0;
                String activeState="";
                for(Entry entry:result){
                    if(entry.saved>0)published++;
                    EdgeFullBookStore.Task task=EdgeFullBookStore.task(job,entry.chapter);
                    int pct=entry.saved>0?100:entry.stats.percent();
                    if(task!=null){
                        if(task.total>0&&!EdgeFullBookStore.DONE.equals(task.status))
                            pct=Math.max(pct,Math.min(98,task.done*100/task.total));
                        if(EdgeFullBookStore.QUEUED.equals(task.status)||EdgeFullBookStore.RUNNING.equals(task.status)||
                            EdgeFullBookStore.WAITING.equals(task.status)||EdgeFullBookStore.SAVING.equals(task.status))pendingCount++;
                        if(activeChapter==-1&&(EdgeFullBookStore.RUNNING.equals(task.status)||
                            EdgeFullBookStore.WAITING.equals(task.status)||EdgeFullBookStore.SAVING.equals(task.status))){
                            activeChapter=entry.chapter;activeState=entry.title+" · "+entry.state;
                        }
                    }
                    if(entry.saved==0)pct=Math.min(pct,99); // 100% only after SAF publication
                    long w=Math.max(1,entry.to-entry.from);weighted+=w*pct;weightTotal+=w;
                }
                final int finalPublished=published,finalPending=pendingCount;
                final boolean allPublished=!result.isEmpty()&&published==result.size();
                final int bookPercent=allPublished?100:weightTotal==0?0:(int)(weighted/weightTotal);
                final String currentStage=activeState;
                main.post(()->{
                    loading=false;if(!visible)return;
                    boolean running=EdgeFullBookService.isServiceAlive()&&job!=null&&
                        (EdgeFullBookStore.RUNNING.equals(job.status)||
                        EdgeFullBookStore.QUEUED.equals(job.status)||EdgeFullBookStore.WAITING.equals(job.status)||
                        EdgeFullBookStore.SAVING.equals(job.status));
                    if(pauseButton!=null)pauseButton.setText(running?"Пауза":"Продолжить");
                    summary.setText(allPublished?"Завершено · Все главы сохранены":
                        "Сохранено глав: "+finalPublished+" из "+result.size()+
                        " · MP3 в кэше: "+total.files+" · "+String.format(Locale.ROOT,"%.1f МБ",total.bytes/1048576.0));
                    totalProgress.setProgress(bookPercent);totalPercent.setText(bookPercent+"%");
                    totalDetail.setText(allPublished?"Завершено · Готово глав: "+finalPublished+" из "+result.size():
                        "Готово: "+finalPublished+" из "+result.size()+" · В очереди/работе: "+
                        finalPending+(currentStage.isEmpty()?"":" · "+currentStage));
                    if(chapterViews.size()!=result.size()||!sameChapters(result)){
                        entries.removeAllViews();chapterViews.clear();displayed.clear();
                        for(Entry entry:result){addEntry(entry);displayed.add(entry);}
                    }else for(int i=0;i<result.size();i++){
                        Entry item=result.get(i),old=displayed.get(i);
                        ChapterViews view=chapterViews.get(i);
                        if(!sameStats(old,item)){
                            boolean updating="В очереди".equals(item.state)||"Идёт загрузка".equals(item.state)||
                                "Ожидание Edge TTS".equals(item.state)||"Сохранение / сборка MP3".equals(item.state);
                            EdgeFullBookStore.Task currentTask=EdgeFullBookStore.task(job,item.chapter);
                            int pct=item.saved>0&&!updating?100:item.stats.percent();
                            if(currentTask!=null&&currentTask.total>0&&updating)
                                pct=Math.max(pct,Math.min(98,currentTask.done*100/currentTask.total));
                            if(item.saved==0)pct=Math.min(pct,99);
                            view.percent.setText(updating&&item.saved>0?"↻":pct+"%");view.bar.setProgress(updating&&item.saved>0?0:pct);
                            view.details.setText(details(item));
                            view.details.setTextColor(stateColor(item));
                            view.percent.setTextColor(stateColor(item));
                            view.merge.setVisibility(item.saved==1?View.VISIBLE:View.GONE);
                            view.merge.setEnabled(item.saved==1);
                        }
                        displayed.set(i,item);
                    }
                });
            }catch(Exception error){
                main.post(()->{loading=false;if(visible)summary.setText("Ошибка проверки глав: "+error.getMessage());});
            }
        });
    }
    private static boolean sameStats(Entry a,Entry b){
        return a.saved==b.saved&&a.stats.covered==b.stats.covered&&a.stats.bytes==b.stats.bytes&&
            a.stats.files==b.stats.files&&a.jobDone==b.jobDone&&a.jobTotal==b.jobTotal&&
            a.state.equals(b.state)&&a.error.equals(b.error);
    }
    private boolean sameChapters(ArrayList<Entry> next){
        if(displayed.size()!=next.size())return false;
        for(int i=0;i<next.size();i++)if(displayed.get(i).chapter!=next.get(i).chapter
            ||!displayed.get(i).title.equals(next.get(i).title))return false;
        return true;
    }
    private static int stateColor(Entry item){
        if("Ошибка".equals(item.state))return 0xffa13d36;
        if(item.saved>0)return 0xff2b6145;
        if("В очереди".equals(item.state)||"Идёт загрузка".equals(item.state)||
            "Ожидание Edge TTS".equals(item.state)||"Сохранение / сборка MP3".equals(item.state)||
            "Объединение MP3".equals(item.state))return 0xff276db1;
        return MUTED;
    }
    private static String details(Entry item){
        return item.state+((item.error==null||item.error.isEmpty())?"":" · "+item.error)+
            (item.saved>0?"":" · "+item.stats.files+" MP3");
    }
    private static final class ChapterViews {
        final TextView percent,details;final ProgressBar bar;final ImageView merge;
        ChapterViews(TextView percent,TextView details,ProgressBar bar,ImageView merge){
            this.percent=percent;this.details=details;this.bar=bar;this.merge=merge;
        }
    }
    private ImageView actionIcon(int resource,String hint,Runnable action){
        ImageView view=new ImageView(this);view.setImageResource(resource);view.setColorFilter(INK);
        view.setContentDescription(hint);view.setPadding(dp(7),dp(7),dp(7),dp(7));
        view.setBackground(rounded(BG));view.setOnClickListener(v->action.run());return view;
    }
    private void addEntry(Entry item){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(PANEL));card.setPadding(dp(12),dp(10),dp(12),dp(10));
        LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.bottomMargin=dp(8);
        entries.addView(card,outer);
        LinearLayout headline=new LinearLayout(this);headline.setGravity(Gravity.CENTER_VERTICAL);card.addView(headline);
        TextView name=text(item.title,15,true);headline.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        int initialPct=item.saved>0?100:item.stats.percent();
        if(item.jobTotal>0&&item.saved==0)initialPct=Math.max(initialPct,Math.min(98,item.jobDone*100/item.jobTotal));
        if(item.saved==0)initialPct=Math.min(initialPct,99);
        TextView percent=text(initialPct+"%",14,true);
        percent.setTextColor(stateColor(item));headline.addView(percent);
        ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);progress.setProgress(initialPct);
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(6));bar.topMargin=dp(7);card.addView(progress,bar);
        TextView meta=text(details(item),12,false);
        meta.setTextColor(stateColor(item));LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);ml.topMargin=dp(5);card.addView(meta,ml);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(6);card.addView(actions,ap);
        ImageView merge=actionIcon(R.drawable.ic_edge_merge_chapter,"Преобразовать файлы главы в один MP3",
            ()->mergeChapter(item.chapter));
        merge.setVisibility(item.saved==1?View.VISIBLE:View.GONE);
        actions.addView(merge,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView finalize=text("Сохранить",12,true);
        finalize.setGravity(Gravity.CENTER);finalize.setBackground(rounded(BG));
        finalize.setContentDescription("Завершить сохранение без повторной озвучки");
        finalize.setVisibility(item.saved==0&&item.stats.files>0?View.VISIBLE:View.GONE);
        finalize.setOnClickListener(v->finishCachedChapter(item.chapter));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,dp(38));fp.leftMargin=dp(6);
        finalize.setPadding(dp(8),0,dp(8),0);actions.addView(finalize,fp);
        ImageView reload=actionIcon(R.drawable.ic_retry_fragment,"Перезагрузить главу",()->confirmReload(item.chapter));
        LinearLayout.LayoutParams rr=new LinearLayout.LayoutParams(dp(38),dp(38));rr.leftMargin=dp(8);actions.addView(reload,rr);
        ImageView delete=actionIcon(R.drawable.ic_edge_delete_chapter,"Удалить главу",()->confirmDelete(item.chapter));
        LinearLayout.LayoutParams dd=new LinearLayout.LayoutParams(dp(38),dp(38));dd.leftMargin=dp(8);actions.addView(delete,dd);
        card.setOnClickListener(v->{
            Entry current=item;
            for(Entry cached:displayed)if(cached.chapter==item.chapter){current=cached;break;}
            if(current.saved==0)command(item.chapter);
            else Toast.makeText(this,current.state,Toast.LENGTH_SHORT).show();
        });
        chapterViews.add(new ChapterViews(percent,meta,progress,merge));
    }
    /** Re-publish existing fragments ONLY. Never synthesize or discard them here. */
    private void finishCachedChapter(int chapter){
        if(!EdgeFolderPicker.available(this)){
            Toast.makeText(this,"Сначала выберите папку Edge TTS",Toast.LENGTH_LONG).show();return;
        }
        if(!merging.add(chapter))return;
        for(int i=0;i<displayed.size();i++)if(displayed.get(i).chapter==chapter&&i<chapterViews.size())
            chapterViews.get(i).details.setText("Проверка MP3 и завершение записи…");
        worker.execute(()->{
            String message="";boolean success=false;
            try{
                EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
                if(job==null)throw new Exception("Очередь озвучивания не найдена. Готовые MP3 не удалены");
                JSONArray all=EdgeFullBookStore.queue(job);
                if(all.length()==0)throw new Exception("Нет сохранённого списка фрагментов; повторная озвучка не запущена");
                if(chapter<0||chapter>=toc.size())throw new Exception("Оглавление выбранной главы не найдено");
                int start=SentenceChunks.lowerBound(offsets,toc.get(chapter).startChar);
                int end=SentenceChunks.lowerBound(offsets,toc.get(chapter).endChar);
                JSONArray selected=new JSONArray();int missing=0;
                for(int i=0;i<all.length();i++){
                    JSONObject row=all.optJSONObject(i);if(row==null)continue;
                    if(row.optInt("end",0)<=start||row.optInt("start",0)>=end)continue;
                    selected.put(row);
                    File mp3=new File(job.mp3Folder(),row.optString("key","")+".mp3");
                    if(!EdgeFullBookStore.ready(mp3))missing++;
                }
                if(selected.length()==0)throw new Exception("В очереди нет фрагментов для этой главы");
                if(missing>0)throw new Exception("Не хватает "+missing+" из "+selected.length()+
                    " MP3. Готовые фрагменты сохранены; докачайте только отсутствующие");
                boolean single=EdgeChapterArchive.autoMerge(this);
                int published=single?
                    EdgeChapterArchive.publish(this,uri,title,job.mp3Folder(),selected,toc,offsets):
                    EdgeChapterArchive.publishFragments(this,uri,title,job.mp3Folder(),selected,toc,offsets);
                EdgeChapterArchive.invalidate();
                success=published>0&&EdgeChapterArchive.snapshot(this,uri,title).state(chapter)>0;
                if(!success)throw new Exception(EdgeChapterArchive.publicationError().isEmpty()?"Проверка SAF не пройдена":EdgeChapterArchive.publicationError());
                EdgeFullBookStore.markPublished(job,chapter);
                message="Глава сохранена из готовых MP3 без новой озвучки";
            }catch(Exception ex){message="Не удалось завершить сохранение: "+ex.getMessage();
                ReaderDiagnosticLog.event("EDGE_EXPORT","manual_finalize_failed type="+ex.getClass().getSimpleName());}
            final String info=message;final boolean ok=success;
            main.post(()->{merging.remove(chapter);lastSegmentsAt=0;
                Toast.makeText(this,info,Toast.LENGTH_LONG).show();reload();});
        });
    }
    private void confirmReload(int chapter){
        new SwipeDialogs.Builder(this).setTitle("Перезагрузить главу?")
            .setMessage("Глава будет озвучена заново. Предыдущая сохранённая версия останется до завершения обновления.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Перезагрузить",(d,w)->command(chapter,true)).show();
    }
    private void confirmDelete(int chapter){
        new SwipeDialogs.Builder(this).setTitle("Удалить главу?")
            .setMessage("Будут удалены MP3, фрагменты и WordBoundary только выбранной главы.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(d,w)->{
                EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
                // Cancel only the selected chapter: never interrupt another book chapter.
                launch(new Intent(this,EdgeFullBookService.class).setAction(EdgeFullBookService.STOP_CHAPTER)
                    .putExtra(EdgeFullBookService.EXTRA_URI,uri)
                    .putExtra(EdgeFullBookService.EXTRA_CHAPTER,chapter));
                worker.execute(()->{
                    boolean removed=EdgeChapterArchive.deleteChapter(this,uri,title,Math.max(0,chapter));
                    if(removed){
                        EdgeFullBookStore.forgetChapter(this,uri,title,chapter);
                        EdgeFullBookStore.Job current=EdgeFullBookStore.find(this,uri,title);
                        if(current!=null){
                            JSONArray queue=EdgeFullBookStore.queue(current);
                            for(int i=0;i<queue.length();i++){
                                JSONObject row=queue.optJSONObject(i);if(row==null)continue;
                                if(chapter>=0&&chapter<toc.size()){
                                    int start=SentenceChunks.lowerBound(offsets,toc.get(chapter).startChar);
                                    int end=SentenceChunks.lowerBound(offsets,toc.get(chapter).endChar);
                                    if(row.optInt("end")<=start||row.optInt("start")>=end)continue;
                                }
                                String key=row.optString("key","");if(!key.matches("[a-f0-9]{64}"))continue;
                                new File(current.mp3Folder(),key+".mp3").delete();
                                new File(current.mp3Folder(),key+".mp3.words.json").delete();
                            }
                        }
                    }
                    lastSegmentsAt=0;
                    main.post(()->{Toast.makeText(this,removed?"Глава удалена":"Не удалось удалить главу",Toast.LENGTH_SHORT).show();reload();});
                });
            }).show();
    }
    private void mergeChapter(int chapter){
        merging.add(chapter);
        for(int i=0;i<displayed.size();i++)if(displayed.get(i).chapter==chapter&&i<chapterViews.size())
            chapterViews.get(i).details.setText("Объединение MP3");
        worker.execute(()->{
            EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,title);
            JSONArray queue=job==null?new JSONArray():EdgeFullBookStore.queue(job);
            if(queue.length()==0)queue=EdgeChapterArchive.queueFromSAF(this,uri,title,Math.max(0,chapter));
            File folder=new File(EdgeBookStorage.bookFolder(this,uri,title),"EdgeTTS");
            boolean merged=EdgeChapterArchive.mergeChapter(this,uri,title,Math.max(0,chapter),folder,queue,toc,offsets);
            lastSegmentsAt=0;
            main.post(()->{
                merging.remove(chapter);
                Toast.makeText(this,merged?"Глава преобразована в MP3":"Не удалось объединить файлы главы",Toast.LENGTH_LONG).show();reload();
            });
        });
    }
    @Override protected void onResume(){super.onResume();visible=true;main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){visible=false;main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
