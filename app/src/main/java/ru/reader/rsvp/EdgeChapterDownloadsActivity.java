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
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
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
    private int BG,PANEL,INK,MUTED,ACCENT,paletteSignature;
    private void readPalette(){BG=UiColorScheme.background(this);PANEL=UiColorScheme.neutral(this);
        INK=UiColorScheme.foreground(this);MUTED=UiColorScheme.muted(this);
        ACCENT=UiColorScheme.accent(this);paletteSignature=UiColorScheme.paletteSignature(this);
    }
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private String uri="",title="";
    private int pendingFolderChapter=-2;
    private TextView summary,pauseButton,allButton,totalPercent,totalDetail;
    private AnimatedDownloadProgressBar totalProgress;
    private RecyclerView entries;
    private final ChapterAdapter chapterAdapter=new ChapterAdapter();
    private boolean visible,loading;
    private long lastSegmentsAt;
    private ArrayList<EdgeOfflineIndex.Segment> lastSegments=new ArrayList<>();
    private final java.util.Set<Integer> merging=new java.util.HashSet<>();
    private final ArrayList<Entry> displayed=new ArrayList<>();
    private ArrayList<Integer> offsets=new ArrayList<>();
    private BookChapterIndex toc=BookChapterIndex.empty();
    private static final class Entry {
        String title,state="",error="";int chapter,from,to,saved,jobDone,jobTotal;
        EdgeOfflineIndex.Stats stats;
    }
    private final Runnable changesReload=this::reload;
    private final DownloadChangeBus.Listener changeListener=id->{
        if(!visible||id==null||!id.equals("edge:"+uri))return;
        main.removeCallbacks(changesReload);main.postDelayed(changesReload,250L);
    };
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
        readPalette();
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
        totalProgress=new AnimatedDownloadProgressBar(this);
        totalProgress.setMax(100);UiColorScheme.tintProgress(totalProgress,this);LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(8));
        op.topMargin=dp(6);root.addView(totalProgress,op);
        totalDetail=text("Ожидаем подготовку глав",12,false);totalDetail.setTextColor(MUTED);
        LinearLayout.LayoutParams od=new LinearLayout.LayoutParams(-1,-2);od.topMargin=dp(5);
        root.addView(totalDetail,od);
        LinearLayout controls=new LinearLayout(this);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(12);root.addView(controls,cp);
        allButton=button("Подготовить все",()->command(-1));controls.addView(allButton,new LinearLayout.LayoutParams(0,dp(47),1));
        pauseButton=button("Пауза",this::toggle);pauseButton.setSingleLine(true);
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(0,dp(47),1);
        pp.leftMargin=dp(8);controls.addView(pauseButton,pp);
        entries=new RecyclerView(this);entries.setLayoutManager(new LinearLayoutManager(this));
        entries.setItemAnimator(null);entries.setHasFixedSize(false);
        entries.setAdapter(chapterAdapter);
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);
        sl.topMargin=dp(10);root.addView(entries,sl);
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
        if(req==EdgeFolderPicker.REQUEST){
            final int chapter=pendingFolderChapter;pendingFolderChapter=-2;
            EdgeFolderPicker.accept(this,req,result,data,ok->{if(ok&&chapter!=-2)command(chapter);});
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
            chapterAdapter.notifyItemChanged(i);
        }
        // Show active download state instantly; reload reconciles with the service.
        if(allButton!=null&&chapter==-1)DownloadButtonFeedback.apply(allButton,PANEL,ACCENT,true,13);
        if(pauseButton!=null)DownloadButtonFeedback.apply(pauseButton,PANEL,ACCENT,true,13);
        if(totalProgress!=null)totalProgress.setWorking(true);
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
        if(pauseButton!=null){pauseButton.setText(running?"Продолжить":"Пауза");
            DownloadButtonFeedback.apply(pauseButton,PANEL,ACCENT,!running,13);}
        if(allButton!=null)DownloadButtonFeedback.apply(allButton,PANEL,ACCENT,!running,13);
        totalProgress.setWorking(!running);
        launch(new Intent(this,EdgeFullBookService.class)
            .setAction(running?EdgeFullBookService.PAUSE:EdgeFullBookService.RESUME)
            .putExtra(EdgeFullBookService.EXTRA_URI,uri));
    }
    private void launch(Intent request){try{
        if(Build.VERSION.SDK_INT>=26)startForegroundService(request);else startService(request);
        main.postDelayed(this::reload,700);
    }catch(Exception e){
        totalProgress.setWorking(false);
        if(allButton!=null)DownloadButtonFeedback.apply(allButton,PANEL,ACCENT,false,13);
        if(pauseButton!=null)DownloadButtonFeedback.apply(pauseButton,PANEL,ACCENT,false,13);
        summary.setText("Не удалось запустить подготовку: "+e.getMessage());
    }}
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
                EdgeOfflineIndex.CoverageIndex coverage=new EdgeOfflineIndex.CoverageIndex(segments);
                EdgeOfflineIndex.Stats total=coverage.stats(0,positions.size());
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
                    item.stats=coverage.stats(item.from,item.to);result.add(item);
                }
                for(Entry item:result){
                    item.saved=saved.state(Math.max(0,item.chapter));
                    if(merging.contains(item.chapter)){item.state="Сохранение фрагментов";continue;}
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
                        else if(EdgeFullBookStore.SAVING.equals(task.status))item.state="Сохранение фрагментов";
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
                    if(pauseButton!=null){
                        pauseButton.setText(running?"Пауза":"Продолжить");
                        DownloadButtonFeedback.apply(pauseButton,PANEL,ACCENT,running,13);
                    }
                    if(allButton!=null)DownloadButtonFeedback.apply(allButton,PANEL,ACCENT,running&&!allPublished,13);
                    totalProgress.setWorking(running&&!allPublished);
                    summary.setText(allPublished?"Завершено · Все главы сохранены":
                        "Сохранено глав: "+finalPublished+" из "+result.size()+
                        " · MP3 в кэше: "+total.files+" · "+String.format(Locale.ROOT,"%.1f МБ",total.bytes/1048576.0));
                    totalProgress.setProgress(bookPercent,true);totalPercent.setText(bookPercent+"%");
                    totalDetail.setText(allPublished?"Завершено · Готово глав: "+finalPublished+" из "+result.size():
                        "Готово: "+finalPublished+" из "+result.size()+" · В очереди/работе: "+
                        finalPending+(currentStage.isEmpty()?"":" · "+currentStage));
                    if(!sameChapters(result)){
                        displayed.clear();displayed.addAll(result);chapterAdapter.notifyDataSetChanged();
                    }else for(int i=0;i<result.size();i++){
                        Entry item=result.get(i),old=displayed.get(i);
                        displayed.set(i,item);
                        if(!sameStats(old,item))chapterAdapter.notifyItemChanged(i);
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
    private int stateColor(Entry item){
        if("Ошибка".equals(item.state))return UiColorScheme.error(this);
        if(item.saved>0)return UiColorScheme.success(this);
        if("В очереди".equals(item.state)||"Идёт загрузка".equals(item.state)||
            "Ожидание Edge TTS".equals(item.state)||"Сохранение фрагментов".equals(item.state)||
            "Сохранение фрагментов".equals(item.state))return ACCENT;
        return MUTED;
    }
    private static String details(Entry item){
        return item.state+((item.error==null||item.error.isEmpty())?"":" · "+item.error)+
            (item.saved>0?"":" · "+item.stats.files+" MP3");
    }
    private ImageView actionIcon(int resource,String hint,Runnable action){
        ImageView view=new ImageView(this);view.setImageResource(resource);view.setColorFilter(INK);
        view.setContentDescription(hint);view.setPadding(dp(7),dp(7),dp(7),dp(7));
        view.setBackground(rounded(BG));view.setOnClickListener(v->action.run());return view;
    }
    private final class ChapterAdapter extends RecyclerView.Adapter<ChapterHolder> {
        ChapterAdapter(){setHasStableIds(true);}
        @Override public long getItemId(int position){return displayed.get(position).chapter;}
        @Override public int getItemCount(){return displayed.size();}
        @Override public ChapterHolder onCreateViewHolder(android.view.ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(EdgeChapterDownloadsActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);card.setBackground(rounded(PANEL));
            card.setPadding(dp(12),dp(10),dp(12),dp(10));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.bottomMargin=dp(8);card.setLayoutParams(lp);
            LinearLayout headline=new LinearLayout(EdgeChapterDownloadsActivity.this);
            headline.setGravity(Gravity.CENTER_VERTICAL);card.addView(headline);
            TextView name=text("",15,true);headline.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView percent=text("0%",14,true);headline.addView(percent);
            ImageView completed=new ImageView(EdgeChapterDownloadsActivity.this);
            completed.setImageResource(R.drawable.ic_download_chapter_complete);
            completed.setImageTintList(android.content.res.ColorStateList.valueOf(UiColorScheme.accent(EdgeChapterDownloadsActivity.this)));
            completed.setContentDescription("Глава загружена");
            LinearLayout.LayoutParams completeLp=new LinearLayout.LayoutParams(dp(16),dp(16));
            completeLp.leftMargin=dp(6);headline.addView(completed,completeLp);
            AnimatedDownloadProgressBar progress=new AnimatedDownloadProgressBar(EdgeChapterDownloadsActivity.this);
            progress.setMax(100);UiColorScheme.tintProgress(progress,EdgeChapterDownloadsActivity.this);
            LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(6));bar.topMargin=dp(7);card.addView(progress,bar);
            TextView details=text("",12,false);LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,-2);
            ml.topMargin=dp(5);card.addView(details,ml);
            LinearLayout actions=new LinearLayout(EdgeChapterDownloadsActivity.this);
            actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=dp(6);card.addView(actions,ap);
            TextView finalize=text("Сохранить",12,true);finalize.setGravity(Gravity.CENTER);finalize.setBackground(rounded(BG));
            finalize.setContentDescription("Завершить сохранение без повторной озвучки");
            LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-2,dp(38));fp.leftMargin=dp(6);
            finalize.setPadding(dp(8),0,dp(8),0);actions.addView(finalize,fp);
            ImageView reload=actionIcon(R.drawable.ic_retry_fragment,"Перезагрузить главу",()->{});
            LinearLayout.LayoutParams rr=new LinearLayout.LayoutParams(dp(38),dp(38));rr.leftMargin=dp(8);actions.addView(reload,rr);
            ImageView delete=actionIcon(R.drawable.ic_edge_delete_chapter,"Удалить главу",()->{});
            LinearLayout.LayoutParams dd=new LinearLayout.LayoutParams(dp(38),dp(38));dd.leftMargin=dp(8);actions.addView(delete,dd);
            ChapterHolder h=new ChapterHolder(card,name,percent,details,progress,completed,finalize);
            // Actions always read the bound row index, not a recycled view's old chapter.
            finalize.setOnClickListener(v->{Entry row=current(h);if(row!=null)finishCachedChapter(row.chapter);});
            reload.setOnClickListener(v->{Entry row=current(h);if(row!=null)confirmReload(row.chapter);});
            delete.setOnClickListener(v->{Entry row=current(h);if(row!=null)confirmDelete(row.chapter);});
            card.setOnClickListener(v->{Entry row=current(h);if(row==null)return;
                if(row.saved==0)command(row.chapter);
                else Toast.makeText(EdgeChapterDownloadsActivity.this,row.state,Toast.LENGTH_SHORT).show();});
            return h;
        }
        @Override public void onBindViewHolder(ChapterHolder h,int position){
            Entry item=displayed.get(position);h.name.setText(item.title);
            boolean updating="В очереди".equals(item.state)||"Идёт загрузка".equals(item.state)||
                "Ожидание Edge TTS".equals(item.state)||"Сохранение фрагментов".equals(item.state);
            int pct=item.saved>0&&!updating?100:item.stats.percent();
            if(item.jobTotal>0&&updating)pct=Math.max(pct,Math.min(98,item.jobDone*100/item.jobTotal));
            if(item.saved==0)pct=Math.min(pct,99);
            h.percent.setText(updating&&item.saved>0?"↻":pct+"%");
            h.bar.setProgress(updating&&item.saved>0?0:pct,true);
            h.bar.setWorking(updating);
            h.details.setText(details(item));h.details.setTextColor(stateColor(item));h.percent.setTextColor(stateColor(item));
            h.completed.setVisibility(item.saved>0&&!updating?View.VISIBLE:View.GONE);
            h.finalize.setVisibility(item.saved==0&&item.stats.files>0?View.VISIBLE:View.GONE);
        }
    }
    private static final class ChapterHolder extends RecyclerView.ViewHolder {
        final TextView name,percent,details,finalize;final AnimatedDownloadProgressBar bar;final ImageView completed;
        ChapterHolder(View root,TextView name,TextView percent,TextView details,AnimatedDownloadProgressBar bar,ImageView completed,TextView finalize){
            super(root);this.name=name;this.percent=percent;this.details=details;this.bar=bar;this.completed=completed;this.finalize=finalize;
        }
    }
    private Entry current(ChapterHolder h){int pos=h.getBindingAdapterPosition();
        return pos>=0&&pos<displayed.size()?displayed.get(pos):null;}
    private void notifyChapter(int chapter){
        for(int i=0;i<displayed.size();i++)if(displayed.get(i).chapter==chapter){chapterAdapter.notifyItemChanged(i);break;}
    }
    /** Re-publish existing fragments ONLY. Never synthesize or discard them here. */
    private void finishCachedChapter(int chapter){
        if(!EdgeFolderPicker.available(this)){
            Toast.makeText(this,"Сначала выберите папку Edge TTS",Toast.LENGTH_LONG).show();return;
        }
        if(!merging.add(chapter))return;
        for(Entry entry:displayed)if(entry.chapter==chapter)entry.state="Сохранение фрагментов";
        notifyChapter(chapter);
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
                int published=EdgeChapterArchive.publishFragmentsOne(this,uri,title,job.mp3Folder(),
                    selected,toc,offsets,chapter);
                success=published>0; // publish() returns success only after the SAF manifest commits.
                if(!success)throw new Exception(EdgeChapterArchive.publicationError().isEmpty()?"Глава не сохранена":EdgeChapterArchive.publicationError());
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
    @Override protected void onResume(){super.onResume();
        if(paletteSignature!=UiColorScheme.paletteSignature(this)){recreate();return;}
        visible=true;DownloadChangeBus.add(changeListener);main.post(changesReload);}
    @Override protected void onPause(){visible=false;totalProgress.setWorking(false);DownloadChangeBus.remove(changeListener);
        main.removeCallbacks(changesReload);super.onPause();}
    @Override protected void onDestroy(){visible=false;DownloadChangeBus.remove(changeListener);
        main.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
