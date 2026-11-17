package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Single integrated download manager. Separate engines persist their own queues;
 *  the files tab contains their full chapter/file controls, never another details Activity.
 */
public final class UnifiedDownloadManagerActivity extends NavigationActivity {
    static final String EXTRA_ID="unified_download_id", EXTRA_TITLE="unified_download_title",
        EXTRA_COVER="unified_download_cover", EXTRA_TYPE="unified_download_type";
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Set<String> checked=new HashSet<>();
    private String id="",hintTitle="",hintCover="",hintType="",tab="files";
    private int bg,panel,ink,muted,accent,paletteSignature;
    private TextView bookTitle,metadata,percent,selectionLabel;
    private ImageView artwork,playPause,allIcon;
    private ProgressBar progress;
    private LinearLayout tabContainer,tabContent,infoList,selectionBar;
    private RecyclerView filesView;
    private ScrollView infoScroll;
    private DownloadsRepository.Item selected;
    private ArrayList<FileRow> rows=new ArrayList<>();
    // The TOC is loaded ONCE per book, never per four-second progress refresh.
    private final Map<String,ArrayList<String>> edgeTocCache=new HashMap<>();
    private FileAdapter adapter;
    private volatile boolean visible,loading;
    private boolean queuedReload,firstResume=true,rowsVerified;
    private long appliedRevision;
    private String lastCoverUri="",lastInfoKey="";
    private DownloadInfoSnapshot.Entry currentInfo;
    // Declare the debounce callback before the listener that uses it.
    // Java field initializers cannot read a later instance field by simple name.
    private final Runnable reloadDebounced=this::reload;
    private final DownloadChangeBus.Listener downloadListener=changed->{
        if(id.equals(changed)&&visible){
            main.removeCallbacks(reloadDebounced);main.postDelayed(reloadDebounced,350);
        }
    };
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable rounded(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String value,int size,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ink);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private void place(LinearLayout box,View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);box.addView(v,p);}
    private static String size(long n){return n<=0?"":String.format(Locale.getDefault(),"%.1f МБ",n/1048576.0);}
    private ImageView actionIcon(int resource,String title,Runnable action){
        // All standard actions use the selected ordinary button surface, not an accent outline.
        int fill=ReaderButtonColors.neutral(this,panel);
        ImageView image=new ImageView(this);image.setImageResource(resource);
        image.setColorFilter(ReaderButtonColors.contrastingText(fill));
        image.setContentDescription(title);image.setTooltipText(title);image.setPadding(dp(11),dp(11),dp(11),dp(11));
        image.setBackground(rounded(fill,12));image.setClickable(true);image.setFocusable(true);
        image.setOnClickListener(v->action.run());return image;
    }
    private ImageView bareMenuIcon(String title){
        ImageView image=new ImageView(this);image.setImageResource(R.drawable.ic_more_vertical);
        image.setColorFilter(ink);image.setPadding(dp(11),dp(11),dp(11),dp(11));
        image.setContentDescription(title);image.setTooltipText(title);
        // The vertical ellipsis must remain unframed in every files list.
        return image;
    }
    @Override public void onCreate(Bundle state){super.onCreate(state);
        id=getIntent().getStringExtra(EXTRA_ID);if(id==null||id.isEmpty()){finish();return;}
        hintTitle=getIntent().getStringExtra(EXTRA_TITLE);if(hintTitle==null)hintTitle="";
        hintCover=getIntent().getStringExtra(EXTRA_COVER);if(hintCover==null)hintCover="";
        hintType=getIntent().getStringExtra(EXTRA_TYPE);if(hintType==null)hintType="";
        if(state!=null)tab=state.getString("tab","files");
        // Display persisted Info immediately, before any asynchronous SAF or task checks.
        currentInfo=DownloadInfoSnapshot.load(this,id);
        bg=UiColorScheme.background(this);panel=UiColorScheme.neutral(this);accent=UiColorScheme.accent(this);
        ink=UiColorScheme.foreground(this);muted=UiColorScheme.muted(this);
        paletteSignature=UiColorScheme.paletteSignature(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);
        root.setPadding(dp(13),dp(24),dp(13),dp(9));setContentView(root);SafeSystemBars.matchBackground(this,bg);
        AppToolbar.add(this,root,"Менеджер");
        LinearLayout hero=new LinearLayout(this);hero.setGravity(Gravity.CENTER_VERTICAL);hero.setPadding(dp(11),dp(11),dp(11),dp(11));
        hero.setBackground(rounded(panel,17));place(root,hero,12);
        FrameLayout cover=new FrameLayout(this);cover.setBackground(rounded(UiColorScheme.blend(panel,ink,.10f),9));
        hero.addView(cover,new LinearLayout.LayoutParams(dp(78),dp(102)));
        ImageView placeholder=new ImageView(this);placeholder.setImageResource(R.drawable.ic_book);
        placeholder.setColorFilter(accent);placeholder.setPadding(dp(18),dp(25),dp(18),dp(25));
        cover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
        artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout description=new LinearLayout(this);description.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams side=new LinearLayout.LayoutParams(0,-2,1);side.leftMargin=dp(12);hero.addView(description,side);
        bookTitle=text(hintTitle.isEmpty()?"Загрузка":hintTitle,18,true);bookTitle.setMaxLines(3);description.addView(bookTitle);
        metadata=text("",12,false);metadata.setTextColor(muted);metadata.setMaxLines(2);place(description,metadata,6);
        ImageView arrow=new ImageView(this);arrow.setImageResource(R.drawable.ic_next_chapter);arrow.setColorFilter(muted);
        arrow.setContentDescription("Открыть произведение");
        hero.addView(arrow,new LinearLayout.LayoutParams(dp(23),dp(26)));
        hero.setContentDescription("Открыть произведение");hero.setOnClickListener(v->{if(selected!=null)openContent(selected);});
        LinearLayout progressLine=new LinearLayout(this);progressLine.setGravity(Gravity.CENTER_VERTICAL);place(root,progressLine,13);
        playPause=actionIcon(R.drawable.ic_play,"Продолжить загрузку",()->{if(selected!=null)control(selected);});
        progressLine.addView(playPause,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView progressTitle=text("Общий прогресс",14,true);
        LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(0,-2,1);middle.leftMargin=dp(10);progressLine.addView(progressTitle,middle);
        percent=text("0%",15,true);progressLine.addView(percent);
        allIcon=actionIcon(R.drawable.ic_autoload,"Скачать всё",()->{if(selected!=null)downloadAll(selected);});
        LinearLayout.LayoutParams full=new LinearLayout.LayoutParams(dp(44),dp(44));full.leftMargin=dp(10);progressLine.addView(allIcon,full);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
        UiColorScheme.tintProgress(progress,this);
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(7));bar.topMargin=dp(8);root.addView(progress,bar);
        tabContainer=new LinearLayout(this);tabContainer.setOrientation(LinearLayout.VERTICAL);place(root,tabContainer,15);
        tabContent=BrowserTabs.panel(this);root.addView(tabContent,new LinearLayout.LayoutParams(-1,0,1));
        filesView=new RecyclerView(this);filesView.setLayoutManager(new LinearLayoutManager(this));
        filesView.setHasFixedSize(false);adapter=new FileAdapter();adapter.setHasStableIds(true);filesView.setAdapter(adapter);
        // Keep its scroll offset and ViewHolders across timer updates and tab changes.
        tabContent.addView(filesView,new LinearLayout.LayoutParams(-1,0,1));
        infoScroll=new ScrollView(this);infoScroll.setFillViewport(false);
        infoList=new LinearLayout(this);infoList.setOrientation(LinearLayout.VERTICAL);infoScroll.addView(infoList);
        tabContent.addView(infoScroll,new LinearLayout.LayoutParams(-1,0,1));
        selectionBar=new LinearLayout(this);selectionBar.setGravity(Gravity.CENTER_VERTICAL);selectionBar.setVisibility(View.GONE);
        tabContent.addView(selectionBar,new LinearLayout.LayoutParams(-1,-2));
        selectionLabel=text("",12,true);selectionBar.addView(selectionLabel,new LinearLayout.LayoutParams(0,-2,1));
        TextView retry=text("В очередь",12,true);retry.setPadding(dp(7),dp(10),dp(7),dp(10));retry.setOnClickListener(v->selectedRows("retry"));selectionBar.addView(retry);
        TextView remove=text("Удалить",12,true);remove.setPadding(dp(7),dp(10),dp(7),dp(10));remove.setOnClickListener(v->selectedRows("delete"));selectionBar.addView(remove);
        TextView cancel=text("×",20,true);cancel.setPadding(dp(8),dp(7),dp(7),dp(7));cancel.setOnClickListener(v->{checked.clear();adapter.notifyDataSetChanged();updateSelectionBar();});selectionBar.addView(cancel);
        if(!hintCover.isEmpty()){
            lastCoverUri=hintCover;CoverImageLoader.show(artwork,hintCover,"edge".equals(hintType));
        }
        showTabs();
        appliedRevision=DownloadChangeBus.revision();
        // The card and its previous chapter list are available before the verification finishes.
        disk.execute(()->{
            ArrayList<FileRow> saved=DownloadRowsSnapshot.load(getApplicationContext(),id);
            main.post(()->{if(!isFinishing()&&!isDestroyed()&&!rowsVerified&&!saved.isEmpty()){
                rows=saved;adapter.submit(rows);
            }});
        });
        reload();
    }
    @Override protected void onResume(){super.onResume();
        if(paletteSignature!=UiColorScheme.paletteSignature(this)){recreate();return;}
        visible=true;DownloadChangeBus.add(downloadListener);
        ArrayList<String> missed=DownloadChangeBus.since(appliedRevision);
        if(!firstResume&&(missed==null||missed.contains(id)||selected==null))reload();
        firstResume=false;
    }
    @Override protected void onPause(){visible=false;DownloadChangeBus.remove(downloadListener);
        main.removeCallbacks(reloadDebounced);super.onPause();}
    @Override protected void onDestroy(){visible=false;DownloadChangeBus.remove(downloadListener);
        main.removeCallbacksAndMessages(null);disk.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){out.putString("tab",tab);super.onSaveInstanceState(out);}
    private void showTabs(){tabContainer.removeAllViews();
        android.widget.HorizontalScrollView tabs=BrowserTabs.strip(this,new String[]{"Файлы","Инфо"},
            "files".equals(tab)?0:1,index->{tab=index==0?"files":"info";showTabs();});
        // Explicit MATCH_PARENT and equal-width tabs prevent "Инфо" being scrolled off-screen.
        tabContainer.addView(tabs,new LinearLayout.LayoutParams(-1,dp(52)));
        switchTab();
    }
    private void switchTab(){boolean files="files".equals(tab);filesView.setVisibility(files?View.VISIBLE:View.GONE);
        infoScroll.setVisibility(files?View.GONE:View.VISIBLE);selectionBar.setVisibility(files&&!checked.isEmpty()?View.VISIBLE:View.GONE);
        if(!files&&currentInfo!=null)showInfo(currentInfo);
    }
    private void updateSelectionBar(){selectionLabel.setText("Выбрано: "+checked.size());
        selectionBar.setVisibility("files".equals(tab)&&!checked.isEmpty()?View.VISIBLE:View.GONE);
    }
    static final class FileRow {
        String key,name,status,detail="";
        int percent;long bytes;int ordinal;Object source;
        FileRow(String key,String name,String status,int percent,long bytes,int ordinal,Object source){
            this.key=key;this.name=name;this.status=status;this.percent=percent;this.bytes=bytes;this.ordinal=ordinal;this.source=source;
        }
        boolean same(FileRow other){return other!=null&&key.equals(other.key)&&name.equals(other.name)
            &&status.equals(other.status)&&detail.equals(other.detail)&&percent==other.percent&&bytes==other.bytes;}
    }
    private static final class Snapshot {DownloadsRepository.Item item;DownloadInfoSnapshot.Entry info;
        ArrayList<FileRow> files=new ArrayList<>();String error="";}
    private void reload(){
        if(disk.isShutdown())return;
        if(loading){queuedReload=true;return;}
        loading=true;
        final long requestedRevision=DownloadChangeBus.revision();
        disk.execute(()->{
            Snapshot snapshot=new Snapshot();
            try{
                snapshot.item=DownloadsRepository.loadOne(getApplicationContext(),id);
                if(snapshot.item!=null){
                    readRows(snapshot);
                    snapshot.info=buildInfo(snapshot.item); // Disk/SAF path lookups only on this executor.
                    DownloadRowsSnapshot.save(getApplicationContext(),id,snapshot.files);
                    DownloadInfoSnapshot.save(getApplicationContext(),snapshot.info);
                }
            }catch(Exception error){snapshot.error=error.getMessage()==null?error.toString():error.getMessage();}
            main.post(()->{
                loading=false;
                if(isFinishing()||isDestroyed())return;
                if(snapshot.item!=null){
                    rowsVerified=true;selected=snapshot.item;rows=snapshot.files;currentInfo=snapshot.info;
                    refreshHeader();adapter.submit(rows);
                    if("info".equals(tab)&&currentInfo!=null)showInfo(currentInfo);
                    appliedRevision=Math.max(appliedRevision,requestedRevision);
                }else if(selected==null){
                    metadata.setText(snapshot.error.isEmpty()?"Данные загрузки пока недоступны":snapshot.error);
                }
                if(queuedReload){queuedReload=false;reload();}
            });
        });
    }
    private void readRows(Snapshot s){DownloadsRepository.Item item=s.item;
        if(DownloadsRepository.AUDIO.equals(item.type)){
            BookImportManager.Job job=item.job;
            if(job==null)for(BookImportManager.Job next:BookImportManager.load(this))if(next.source.equals(item.source)){job=next;break;}
            if(job==null)return;
            Map<String,FileRow> existing=new HashMap<>();
            for(FileRow cached:rows)existing.put(cached.key,cached);
            for(int n=0;n<job.chapters.size();n++){
                int st=n<job.states.size()?job.states.get(n):-1;
                String state=st<0?"Не выбрано":st>=100?"Скачано":job.paused?"Пауза":
                    job.cancelled?"Отменено":st>0?"Скачивается":"Ожидает";
                String key="audio:"+job.source+":"+n;
                FileRow previous=existing.get(key);
                // A completed/paused/unchanged track has durable status. Reuse its size;
                // avoid slow SAF metadata calls for hundreds of already prepared chapters.
                boolean stable=st>=100||st<0||job.paused||job.cancelled;
                long savedBytes=previous!=null&&stable&&previous.status.equals(state)?previous.bytes:-1;
                if(savedBytes<0){
                    File track=BookImportManager.trackFile(this,job,n);
                    savedBytes=track.isFile()?track.length():0;
                    if(savedBytes==0){
                        String saved=BookImportManager.relocated(job,n);
                        if(saved.startsWith("content://"))try{
                            DocumentFile f=DocumentFile.fromSingleUri(this,Uri.parse(saved));
                            if(f!=null&&f.isFile())savedBytes=f.length();
                        }catch(Exception ignored){}
                    }
                }
                FileRow row=new FileRow("audio:"+job.source+":"+n,job.chapters.get(n).name,
                    state,Math.max(0,st),savedBytes,n,job);
                s.files.add(row);
            }
        }else if(DownloadsRepository.MANGA.equals(item.type)){
            for(MangaLibStorage.Job j:MangaLibStorage.jobs(this))if(item.source.equals(j.slug)){
                int percent=j.total>0?Math.min(100,j.done*100/j.total):"complete".equals(j.status)?100:0;
                FileRow row=new FileRow("manga:"+j.id(),"Том "+j.volume+" · Глава "+j.number+
                    (j.chapter.isEmpty()?"":" · "+j.chapter),j.status,percent,j.bytes,0,j);
                row.detail=j.error.isEmpty()?(j.total>0?"Страницы "+j.done+"/"+j.total:""):j.error;s.files.add(row);
            }
        }else if(DownloadsRepository.RANOBE.equals(item.type)){
            for(RanobeLibStorage.Job j:RanobeLibStorage.series(this,item.source)){
                FileRow row=new FileRow("ranobe:"+j.id(),"Том "+j.volume+" · Глава "+j.number+
                    (j.chapter.isEmpty()?"":" · "+j.chapter),j.status,"complete".equals(j.status)?100:0,j.bytes,0,j);
                row.detail=j.error;s.files.add(row);
            }
        }else if(DownloadsRepository.RANOBES.equals(item.type)){
            for(RanobesStorage.Job j:RanobesStorage.jobs(this,item.source)){
                FileRow row=new FileRow("ranobes:"+j.id(),"Том "+j.volume+" · "+j.title,j.status,
                    "complete".equals(j.status)?100:0,j.bytes,0,j);
                row.detail=j.error;s.files.add(row);
            }
        }else if(DownloadsRepository.EDGE.equals(item.type)){
            EdgeFullBookStore.Job job=item.fullJob!=null?item.fullJob:EdgeFullBookStore.find(this,item.bookUri,item.title);
            Map<Integer,EdgeFullBookStore.Task> tasks=new HashMap<>();
            if(job!=null)for(EdgeFullBookStore.Task t:job.tasks)tasks.put(t.chapter,t);
            ArrayList<String> chapterNames=edgeChapters(item);
            EdgeChapterArchive.ChapterSnapshot published=EdgeChapterArchive.snapshot(this,item.bookUri,item.title);
            for(int chapter=0;chapter<chapterNames.size();chapter++){
                EdgeFullBookStore.Task task=tasks.get(chapter);
                int saved=published.state(chapter);
                int percentage=task!=null&&task.total>0?(int)Math.min(99,100L*task.done/task.total):0;
                String status=task==null?"Не подготовлено":task.status;
                if(saved>0&&(task==null||!EdgeFullBookStore.RUNNING.equals(task.status))) {
                    percentage=100;status=saved==2?"Единый MP3 готов":"MP3-фрагменты сохранены";
                } else if(task!=null&&EdgeFullBookStore.DONE.equals(task.status))percentage=99;
                FileRow row=new FileRow("edge:"+chapter,chapterNames.get(chapter),status,
                    percentage,0,chapter,task);
                if(task!=null)row.detail=task.error;
                s.files.add(row);
            }
            EdgeFullBookStore.Task planner=tasks.get(EdgeFullBookStore.PLAN);
            if(planner!=null&&chapterNames.isEmpty()){
                FileRow row=new FileRow("edge:planner","Подготовка всех глав",planner.status,
                    planner.total>0?(int)Math.min(99,100L*planner.done/planner.total):0,0,EdgeFullBookStore.PLAN,planner);
                row.detail=planner.error;s.files.add(row);
            }
            // If an imported title's TOC is unavailable, still display persisted tasks.
            if(chapterNames.isEmpty()&&planner==null)for(EdgeFullBookStore.Task task:tasks.values()){
                if(task.chapter<0)continue;
                FileRow row=new FileRow("edge:"+task.chapter,"Глава "+(task.chapter+1),task.status,
                    task.total>0?(int)Math.min(100,100L*task.done/task.total):0,0,task.chapter,task);
                row.detail=task.error;s.files.add(row);
            }
        }else if(DownloadsRepository.LINK.equals(item.type)){
            LinkDownloadStore.Batch batch=item.link!=null?item.link:LinkDownloadStore.get(this,item.id.replaceFirst("^link:",""));
            if(batch==null)return;
            for(int n=0;n<batch.files.size();n++){
                LinkDownloadStore.Entry e=batch.files.get(n);
                int percentage=e.size>0?(int)Math.min(100,100*e.bytes/e.size):"completed".equals(e.status)?100:-1;
                FileRow row=new FileRow("link:"+batch.id+":"+n,e.displayTitle.isEmpty()?e.name:e.displayTitle,
                    e.status,percentage,e.bytes,n,e);row.detail=e.error;s.files.add(row);
            }
        }
    }
    private ArrayList<String> edgeChapters(DownloadsRepository.Item item){
        String key=item.bookUri==null?"":item.bookUri;
        ArrayList<String> cached=edgeTocCache.get(key);
        if(cached!=null)return cached;
        cached=EdgeTocSnapshot.load(getApplicationContext(),key);
        if(cached!=null){edgeTocCache.put(key,cached);return cached;}
        ArrayList<String> result=new ArrayList<>();
        if(!key.isEmpty())try{
            BookLoader.Book book=BookLoader.load(this,Uri.parse(key));
            if(book.chapters!=null)for(int n=0;n<book.chapters.size();n++)
                result.add(book.chapters.get(n).title);
            // Even books without structural chapters should be loaded only once.
            edgeTocCache.put(key,result);
            EdgeTocSnapshot.save(getApplicationContext(),key,result);
        }catch(Exception error){android.util.Log.w("PROChitalka-Edge","Оглавление пока недоступно",error);}
        // Transient SAF errors are not cached, allowing recovery after permission repair.
        return result;
    }
    private void refreshHeader(){DownloadsRepository.Item i=selected;if(i==null)return;
        bookTitle.setText(i.title);StringBuilder details=new StringBuilder();
        append(details,i.author);append(details,i.provider.isEmpty()?i.type:i.provider);append(details,i.format);
        if(!i.narrator.isEmpty())append(details,"Читает: "+i.narrator);
        metadata.setText(details.toString());
        if(!i.cover.isEmpty()&&!i.cover.equals(lastCoverUri)){
            lastCoverUri=i.cover;CoverImageLoader.show(artwork,i.cover,DownloadsRepository.EDGE.equals(i.type));
        }
        int value=i.coveragePercent;
        if(i.total>0&&value<=0)value=(int)Math.min(100,100L*i.done/i.total);
        if(i.ready)value=100;value=Math.max(0,Math.min(100,value));
        progress.setProgress(value);percent.setText(value+"%");
        playPause.setImageResource(i.running?R.drawable.ic_pause:R.drawable.ic_play);
        playPause.setContentDescription(i.running?"Пауза загрузки":"Продолжить загрузку");
        playPause.setEnabled(!i.ready);playPause.setAlpha(i.ready?.35f:1f);
        boolean remaining=!i.ready||i.total==0||i.done<i.total;
        allIcon.setEnabled(remaining);allIcon.setAlpha(remaining?1f:.35f);
    }
    private static void append(StringBuilder b,String value){if(value!=null&&!value.trim().isEmpty()){
        if(b.length()>0)b.append(" · ");b.append(value.trim());}}
    private static String category(DownloadsRepository.Item item){
        if(DownloadsRepository.AUDIO.equals(item.type))return DownloadFolderPicker.AUDIO;
        if(DownloadsRepository.MANGA.equals(item.type))return DownloadFolderPicker.MANGA;
        if(DownloadsRepository.EDGE.equals(item.type)||DownloadsRepository.RANOBE.equals(item.type)
            ||DownloadsRepository.RANOBES.equals(item.type))return DownloadFolderPicker.BOOK;
        return null;
    }
    private static int folderRequest(String key){return DownloadFolderPicker.AUDIO.equals(key)?DownloadFolderPicker.AUDIO_REQUEST:
        DownloadFolderPicker.MANGA.equals(key)?DownloadFolderPicker.MANGA_REQUEST:DownloadFolderPicker.BOOK_REQUEST;}
    private void detailLine(String name,String value){if(value==null||value.trim().isEmpty())return;
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(12),dp(10),dp(12));row.setBackground(rounded(bg,12));
        TextView caption=text(name,13,false);caption.setTextColor(muted);row.addView(caption);
        TextView display=text(value,13,true);LinearLayout.LayoutParams w=new LinearLayout.LayoutParams(0,-2,1);
        w.leftMargin=dp(10);row.addView(display,w);place(infoList,row,7);
    }
    /** Only trim the known storage label; the SAF document path itself stays intact. */
    private static String shortPath(String full){
        if(full.startsWith("Внутренняя память/"))return full.substring("Внутренняя память/".length());
        if(full.equals("Внутренняя память"))return "Корень накопителя";
        return full;
    }
    /** Prefer a verified per-work directory over the general configured category root.
     * Only document URIs actually stored by a provider can establish a deeper folder.
     */
    private String destinationPath(DownloadsRepository.Item item,String category){
        String actual=DownloadFolderPicker.directoryPath(this,category);
        String precise="";
        if(DownloadsRepository.MANGA.equals(item.type)){
            precise=DownloadFolderPicker.documentDirectoryPath(this,
                MangaLibStorage.externalDir(this,item.source),false);
        }else if(DownloadsRepository.RANOBE.equals(item.type)){
            precise=DownloadFolderPicker.documentDirectoryPath(this,
                RanobeLibStorage.externalDirUri(this,item.source),false);
        }else if(DownloadsRepository.RANOBES.equals(item.type)){
            precise=DownloadFolderPicker.documentDirectoryPath(this,item.bookUri,true);
        }else if(DownloadsRepository.AUDIO.equals(item.type)&&item.job!=null){
            for(String saved:item.job.relocatedUris){
                precise=DownloadFolderPicker.documentDirectoryPath(this,saved,true);
                if(!precise.isEmpty())break;
            }
        }
        return shortPath(precise.isEmpty()?actual:precise);
    }
    private void folderInfoRow(String actual,Runnable chooseAction){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10),dp(10),dp(4),dp(10));row.setBackground(rounded(bg,12));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        TextView caption=text("Файлы",12,false);caption.setTextColor(muted);labels.addView(caption);
        TextView path=text(actual,14,true);path.setMaxLines(2);
        path.setEllipsize(TextUtils.TruncateAt.START);
        path.setContentDescription("Путь к файлам: "+actual);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(4);labels.addView(path,p);
        path.post(()->fitPathToTwoLines(path,actual));
        if(chooseAction!=null){
            ImageView folder=actionIcon(R.drawable.ic_folder_audio,"Изменить папку",chooseAction);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(44),dp(44));lp.leftMargin=dp(6);
            row.addView(folder,lp);
        }
        place(infoList,row,8);
    }
    /** Android START ellipsize is inconsistent for multiline; remove parent segments first. */
    private static void fitPathToTwoLines(TextView view,String full){
        int width=view.getWidth();if(width<=0)return;
        String shown=full;
        for(int i=0;i<100;i++){
            android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(shown,0,shown.length(),
                view.getPaint(),width).setIncludePad(false).build();
            if(layout.getLineCount()<=2)break;
            String remainder=shown.startsWith("…/")?shown.substring(2):shown;
            int slash=remainder.indexOf('/');
            if(slash<0){shown=TextUtils.ellipsize(remainder,view.getPaint(),width*1.9f,
                TextUtils.TruncateAt.START).toString();break;}
            String shorter="…/"+remainder.substring(slash+1);
            if(shorter.equals(shown))break;
            shown=shorter;
        }
        view.setText(shown);
    }
    /** Build a compact snapshot in the disk executor, never in an Info tab layout callback. */
    private DownloadInfoSnapshot.Entry buildInfo(DownloadsRepository.Item i){
        DownloadInfoSnapshot.Entry data=new DownloadInfoSnapshot.Entry();
        data.id=i.id;data.type=i.type;data.status=i.status;data.description=i.description;
        data.done=i.done;data.total=i.total;data.bytes=i.downloadedBytes;
        String key=category(i);data.folderCategory=key==null?"":key;
        if(key!=null){
            data.path=destinationPath(i,key);
            data.issue=DownloadFolderPicker.lastIssue(this,key);
        }else if(DownloadsRepository.LINK.equals(i.type)&&i.link!=null){
            String actual=DownloadFolderPicker.treeDisplayPath(this,i.link.treeUri);
            if(actual.isEmpty())actual=i.link.treeLabel.isEmpty()?"Папка ссылки":i.link.treeLabel;
            if(!i.link.subfolder.isEmpty())actual+="/"+i.link.subfolder;
            data.path=shortPath(actual);
        }
        return data;
    }
    /** Rendering the Info tab reads no files and does not scan Edge TTS folders. */
    private void showInfo(DownloadInfoSnapshot.Entry data){
        String keySignature=data.signature();
        if(keySignature.equals(lastInfoKey)&&infoList.getChildCount()>0)return;
        lastInfoKey=keySignature;
        final int previousScroll=infoScroll.getScrollY();
        infoList.removeAllViews();
        detailLine("Состояние",data.status);
        if(data.total>0)detailLine("Готово",data.done+" из "+data.total);
        if(data.bytes>0)detailLine("Объём",size(data.bytes));
        if(!data.description.isEmpty())detailLine("Описание",data.description);
        if(!data.folderCategory.isEmpty()){
            final String key=data.folderCategory;
            folderInfoRow(data.path,()->DownloadFolderPicker.choose(this,folderRequest(key),key));
            if(!data.issue.isEmpty())detailLine("Ошибка сохранения",data.issue);
        }else if(!data.path.isEmpty())folderInfoRow(data.path,null);
        infoScroll.post(()->{if(!isFinishing()&&"info".equals(tab))infoScroll.scrollTo(0,previousScroll);});
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.BOOK_REQUEST,DownloadFolderPicker.BOOK,ok->{if(ok){EdgeChapterArchive.invalidate();DownloadInfoSnapshot.remove(this,id);
                currentInfo=null;lastInfoKey="";reload();}})
            ||DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.MANGA_REQUEST,DownloadFolderPicker.MANGA,ok->{if(ok){EdgeChapterArchive.invalidate();DownloadInfoSnapshot.remove(this,id);
                currentInfo=null;lastInfoKey="";reload();}})
            ||DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.AUDIO_REQUEST,DownloadFolderPicker.AUDIO,ok->{if(ok){EdgeChapterArchive.invalidate();DownloadInfoSnapshot.remove(this,id);
                currentInfo=null;lastInfoKey="";reload();}})){
            return; // Async completion callback refreshes once.
        }
    }
    private void control(DownloadsRepository.Item i){try{
        if(DownloadsRepository.AUDIO.equals(i.type)){
            Intent request=new Intent(this,DownloadWorker.class).setAction(i.running?DownloadWorker.PAUSE:DownloadWorker.START).putExtra("source",i.source);
            launch(request,!i.running);
        }else if(DownloadsRepository.MANGA.equals(i.type)){
            if(i.running)MangaLibQueue.pause(this,i.source);else MangaLibQueue.resume(this,i.source);
        }else if(DownloadsRepository.RANOBE.equals(i.type)){
            if(i.running)RanobeLibQueue.pause(this,i.source);else RanobeLibQueue.resume(this,i.source);
        }else if(DownloadsRepository.RANOBES.equals(i.type)){
            if(i.running)RanobesQueue.pause(this,i.source);else RanobesQueue.resume(this,i.source);
        }else if(DownloadsRepository.LINK.equals(i.type)){
            launch(new Intent(this,DirectLinkDownloadService.class).setAction(i.running?DirectLinkDownloadService.PAUSE:DirectLinkDownloadService.START)
                .putExtra(DirectLinkDownloadService.EXTRA_ID,i.id.replaceFirst("^link:","")),!i.running);
        }else if(DownloadsRepository.EDGE.equals(i.type)){
            launch(new Intent(this,EdgeFullBookService.class).setAction(i.running?EdgeFullBookService.PAUSE:EdgeFullBookService.RESUME)
                .putExtra(EdgeFullBookService.EXTRA_URI,i.bookUri),!i.running);
        }
        main.postDelayed(this::reload,450);
    }catch(Exception error){notifyUser("Ошибка загрузки: "+error.getMessage());}}
    private void launch(Intent request,boolean foreground){if(foreground&&Build.VERSION.SDK_INT>=26)startForegroundService(request);else startService(request);}
    private void enqueueEdge(DownloadsRepository.Item item,int chapter,boolean force){
        if(!EdgeFolderPicker.available(this)){
            notifyUser("Выберите папку Book для Edge TTS");DownloadFolderPicker.choose(this,DownloadFolderPicker.BOOK_REQUEST,DownloadFolderPicker.BOOK);return;
        }
        android.content.SharedPreferences pref=getSharedPreferences("reader",0);
        Intent request=new Intent(this,EdgeFullBookService.class).setAction(EdgeFullBookService.ADD)
            .putExtra(EdgeFullBookService.EXTRA_URI,item.bookUri)
            .putExtra(EdgeFullBookService.EXTRA_TITLE,item.title)
            .putExtra(EdgeFullBookService.EXTRA_VOICE,pref.getString("edge_voice","ru-RU-SvetlanaNeural"))
            .putExtra(EdgeFullBookService.EXTRA_RATE,pref.getString("edge_rate","+0%"))
            .putExtra(EdgeFullBookService.EXTRA_CHARS,EdgeSegmentSettings.get(pref))
            .putExtra(EdgeFullBookService.EXTRA_CHAPTER,chapter)
            .putExtra(EdgeFullBookService.EXTRA_FORCE,force);
        launch(request,true);main.postDelayed(this::reload,500);
    }
    private void workerAction(Callable<String> task){
        if(disk.isShutdown())return;
        disk.execute(()->{
            String message;
            try{message=task.call();}catch(Exception error){message="Ошибка: "+error.getMessage();}
            final String result=message;
            main.post(()->{if(isFinishing()||isDestroyed())return;
                if(result!=null&&!result.isEmpty())notifyUser(result);
                reload();});
        });
    }
    private void downloadAll(DownloadsRepository.Item i){
        if(DownloadsRepository.EDGE.equals(i.type)){enqueueEdge(i,-1,false);return;}
        if(DownloadsRepository.AUDIO.equals(i.type)){
            workerAction(()->{
                BookImportManager.Job job=null;
                for(BookImportManager.Job candidate:BookImportManager.load(this))if(i.source.equals(candidate.source)){job=candidate;break;}
                if(job==null)return "Список глав не найден";
                while(job.states.size()<job.chapters.size())job.states.add(-1);
                int count=0;for(int n=0;n<job.states.size();n++)if(job.states.get(n)<0){job.states.set(n,0);count++;}
                BookImportManager.put(this,job);
                final String source=job.source;
                main.post(()->launch(new Intent(this,DownloadWorker.class).setAction(DownloadWorker.START)
                    .putExtra("source",source),true));
                return count==0?"Продолжаем незавершённые главы":"Добавлено глав: "+count;
            });return;
        }
        if(DownloadsRepository.MANGA.equals(i.type)){
            workerAction(()->{
                ArrayList<MangaLibStorage.Job> missing=new ArrayList<>();
                for(MangaLibStorage.Job job:MangaLibStorage.jobs(this))if(i.source.equals(job.slug)&&!"complete".equals(job.status))missing.add(job);
                int queued=MangaLibQueue.enqueue(this,missing);
                return queued>0?"Добавлено глав: "+queued:"Все известные главы уже готовы. Новые можно выбрать в поиске манги";
            });return;
        }
        if(DownloadsRepository.RANOBE.equals(i.type)){
            workerAction(()->{
                RanobeLibApi api=new RanobeLibApi();RanobeLibApi.Title title=api.title(i.source);
                List<RanobeLibApi.Chapter> chapters=api.chapters(i.source);
                ArrayList<RanobeLibStorage.Job> missing=new ArrayList<>();Set<String> seen=new HashSet<>();
                for(RanobeLibApi.Chapter ch:chapters)if(seen.add(ch.volume+"|"+ch.number))
                    missing.add(RanobeLibStorage.Job.create(title,ch));
                RanobeLibStorage.rememberTitle(this,title);
                int count=RanobeLibQueue.enqueue(this,missing);
                return count>0?"Поставлено в очередь: "+count+" глав":"Готовые главы уже скачаны";
            });return;
        }
        if(DownloadsRepository.RANOBES.equals(i.type)){
            workerAction(()->{
                List<RanobesStorage.Job> existing=RanobesStorage.jobs(this,i.source);
                if(existing.isEmpty())return "Не найден адрес произведения";
                String url=existing.get(0).bookUrl;
                RanobesApi api=new RanobesApi();RanobesApi.Title title=api.title(url);
                RanobesApi.TocResult toc=api.loadToc(url,null);
                ArrayList<RanobesStorage.Job> jobs=new ArrayList<>();
                String language=i.source.substring(i.source.lastIndexOf(':')+1);
                for(RanobesApi.Chapter chapter:toc.chapters)jobs.add(RanobesStorage.Job.make(title,chapter,language));
                RanobesStorage.remember(this,title);
                int count=RanobesQueue.enqueue(this,jobs);
                return "Добавлено глав: "+count+(toc.partial?" · часть страниц оглавления недоступна":"");
            });return;
        }
        if(DownloadsRepository.LINK.equals(i.type)){
            launch(new Intent(this,DirectLinkDownloadService.class).setAction(DirectLinkDownloadService.START)
                .putExtra(DirectLinkDownloadService.EXTRA_ID,i.id.replaceFirst("^link:","")),true);
            notifyUser("Очередь файлов возобновлена");return;
        }
    }
    private void openContent(DownloadsRepository.Item i){
        Intent next=null;
        if(DownloadsRepository.AUDIO.equals(i.type))next=new Intent(this,AudioPlayerActivity.class)
            .putExtra("audio_book_id","url:"+i.source);
        else if(DownloadsRepository.RANOBE.equals(i.type)&&RanobeLibStorage.hasBook(this,i.source))
            next=new Intent(this,ReaderActivity.class).putExtra("book_uri",RanobeLibStorage.bookUri(this,i.source));
        else if(DownloadsRepository.RANOBES.equals(i.type)&&RanobesStorage.hasBook(this,i.source))
            next=new Intent(this,ReaderActivity.class).putExtra("book_uri",RanobesStorage.bookUri(this,i.source));
        else if(DownloadsRepository.EDGE.equals(i.type))next=new Intent(this,ReaderActivity.class)
            .putExtra("book_uri",i.bookUri).putExtra("reading_mode_override",3);
        else if(DownloadsRepository.MANGA.equals(i.type))next=new Intent(this,ComicReaderActivity.class)
            .putExtra("comic_uri",MangaLibStorage.comicUri(i.source));
        else if(DownloadsRepository.LINK.equals(i.type)&&i.link!=null){
            for(LinkDownloadStore.Entry entry:i.link.files)if(!entry.savedUri.isEmpty()){
                next=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(entry.savedUri),
                    LinkFileTypes.mime(entry.format)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);break;
            }
        }
        if(next!=null){try{startActivity(next);}catch(Exception ex){notifyUser("Не удалось открыть: "+ex.getMessage());}}
        else notifyUser("Произведение ещё не готово");
    }
    private void notifyUser(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void selectedRows(String action){
        // Cached rows can be selected before their store projection is loaded.
        // Resolve authoritative jobs at execution time, not to open the menu.
        if(checked.isEmpty())return;
        ArrayList<FileRow> chosen=new ArrayList<>();for(FileRow row:rows)if(checked.contains(row.key))chosen.add(row);
        if("delete".equals(action)){
            new SwipeDialogs.Builder(this).setTitle("Удалить выбранные файлы?")
                .setMessage("Выбрано элементов: "+chosen.size()+". Удаление готовых файлов необратимо.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)->{checked.clear();updateSelectionBar();
                    workerAction(()->{DownloadsRepository.Item target=currentActionItem();
                        int count=0;for(FileRow row:chosen)if(applyFileAction(row,"delete",target))count++;
                        return "Удалено записей: "+count;});
                }).show();
        }else{
            checked.clear();updateSelectionBar();workerAction(()->{
                DownloadsRepository.Item target=currentActionItem();
                int count=0;for(FileRow row:chosen)if(applyFileAction(row,"retry",target))count++;
                return "Обработано файлов: "+count;
            });
        }
    }
    /** Actions read the authoritative downloader store. A display-only cache has no source. */
    private DownloadsRepository.Item currentActionItem(){
        return DownloadsRepository.loadOne(getApplicationContext(),id);
    }
    private boolean applyFileAction(FileRow row,String action)throws Exception{
        return applyFileAction(row,action,currentActionItem());
    }
    private boolean applyFileAction(FileRow row,String action,DownloadsRepository.Item item)throws Exception{
        if(row==null||item==null||!id.equals(item.id))return false;
        if(DownloadsRepository.AUDIO.equals(item.type)){
            BookImportManager.Job job=null;
            for(BookImportManager.Job candidate:BookImportManager.load(this))if(item.source.equals(candidate.source)){job=candidate;break;}
            if(job==null||row.ordinal<0||row.ordinal>=job.chapters.size()
                ||!row.key.equals("audio:"+job.source+":"+row.ordinal)
                ||!row.name.equals(job.chapters.get(row.ordinal).name))return false;
            int n=row.ordinal;while(job.states.size()<=n)job.states.add(-1);
            if("delete".equals(action)){
                if(job.states.get(n)!=100&&job.states.get(n)<0)return false;
                File source=BookImportManager.trackFile(this,job,n);
                String uri=BookImportManager.relocated(job,n);
                if(!uri.isEmpty()){
                    DocumentFile saved=DocumentFile.fromSingleUri(this,Uri.parse(uri));
                    if(saved!=null&&saved.exists()&&!saved.delete())throw new java.io.IOException("Не удалён SAF MP3");
                    job.relocatedUris.set(n,"");
                }
                if(source.exists()&&!source.delete())throw new java.io.IOException("Не удалён локальный MP3");
                job.states.set(n,-1);BookImportManager.put(this,job);BookImportManager.updateLibrary(this,job);return true;
            }
            if(job.states.get(n)>=100&&BookImportManager.relocated(job,n).startsWith("content://"))return false;
            job.states.set(n,0);job.paused=false;job.cancelled=false;BookImportManager.put(this,job);
            final String source=job.source;
            main.post(()->launch(new Intent(this,DownloadWorker.class).setAction(DownloadWorker.START).putExtra("source",source),true));
            return true;
        }
        if(DownloadsRepository.MANGA.equals(item.type)){
            if(!row.key.startsWith("manga:"))return false;
            MangaLibStorage.Job j=MangaLibStorage.find(this,row.key.substring("manga:".length()));
            if(j==null||!item.source.equals(j.slug))return false;
            if("delete".equals(action)){
                if(MangaLibStorage.isUri(j.file)){
                    DocumentFile saved=DocumentFile.fromSingleUri(this,Uri.parse(j.file));
                    if(saved!=null&&saved.exists()&&!saved.delete())throw new java.io.IOException("Не удалось удалить CBZ");
                }else if(j.file!=null&&!j.file.isEmpty())new File(j.file).delete();
                j.file="";j.done=0;j.bytes=0;j.status="cancelled";MangaLibStorage.put(this,j);return true;
            }
            return MangaLibQueue.enqueue(this,Collections.singletonList(j))>0;
        }
        if(DownloadsRepository.RANOBE.equals(item.type)){
            if(!row.key.startsWith("ranobe:"))return false;
            RanobeLibStorage.Job j=RanobeLibStorage.find(this,row.key.substring("ranobe:".length()));
            if(j==null||!item.source.equals(j.slug))return false;
            if("delete".equals(action)){
                if(j.file!=null&&!j.file.isEmpty()&&new File(j.file).isFile()&&!new File(j.file).delete())
                    throw new java.io.IOException("Не удалось удалить HTML-главу");
                j.file="";j.status="cancelled";RanobeLibStorage.putAll(this,new ArrayList<>(Collections.singletonList(j)));return true;
            }
            return RanobeLibQueue.enqueue(this,Collections.singletonList(j))>0;
        }
        if(DownloadsRepository.RANOBES.equals(item.type)){
            if(!row.key.startsWith("ranobes:"))return false;
            RanobesStorage.Job j=RanobesStorage.find(this,row.key.substring("ranobes:".length()));
            if(j==null||!item.source.equals(j.sourceKey()))return false;
            if("delete".equals(action)){
                File file=RanobesStorage.chapter(this,j);
                if(file.isFile()&&!file.delete())throw new java.io.IOException("Не удалось удалить HTML-главу");
                j.file="";j.status="cancelled";RanobesStorage.put(this,j);return true;
            }
            return RanobesQueue.enqueue(this,Collections.singletonList(j))>0;
        }
        if(DownloadsRepository.EDGE.equals(item.type)){
            int chapter=row.ordinal;
            // Planner is a real queue task (PLAN=-2), not a normal chapter.
            boolean planner=chapter==EdgeFullBookStore.PLAN&&"edge:planner".equals(row.key);
            if(!planner&&!row.key.equals("edge:"+chapter))return false;
            if(chapter<0&&"delete".equals(action))return false;
            if("delete".equals(action)){
                if(chapter<0)return false;
                if(!EdgeChapterArchive.deleteChapter(this,item.bookUri,item.title,chapter))return false;
                EdgeFullBookStore.forgetChapter(this,item.bookUri,item.title,chapter);
                EdgeChapterArchive.invalidate();return true;
            }
            main.post(()->enqueueEdge(item,chapter,false));return true;
        }
        if(DownloadsRepository.LINK.equals(item.type)){
            String batchId=item.id.replaceFirst("^link:","");
            LinkDownloadStore.Batch batch=LinkDownloadStore.get(this,batchId);
            if(batch==null||row.ordinal<0||row.ordinal>=batch.files.size()
                ||!row.key.equals("link:"+batchId+":"+row.ordinal))return false;
            LinkDownloadStore.Entry entry=batch.files.get(row.ordinal);
            String currentTitle=entry.displayTitle.isEmpty()?entry.name:entry.displayTitle;
            if(!row.name.equals(currentTitle))return false;
            if("delete".equals(action)){
                if(!entry.savedUri.isEmpty()){
                    DocumentFile saved=DocumentFile.fromSingleUri(this,Uri.parse(entry.savedUri));
                    if(saved!=null&&saved.exists()&&!saved.delete())throw new java.io.IOException("Не удалось удалить файл");
                }
                entry.savedUri="";entry.status="cancelled";entry.bytes=0;
            }else{
                if("completed".equals(entry.status)&&!entry.savedUri.isEmpty())return false;
                entry.status="queued";entry.error="";
            }
            LinkDownloadStore.put(this,batch);
            if(!"delete".equals(action))main.post(()->launch(new Intent(this,DirectLinkDownloadService.class)
                .setAction(DirectLinkDownloadService.START).putExtra(DirectLinkDownloadService.EXTRA_ID,batchId),true));
            return true;
        }
        return false;
    }
    private void openFinishedFile(FileRow row){
        if(row==null)return;
        DownloadsRepository.Item book=selected;
        if(book==null){
            if(disk.isShutdown())return;
            disk.execute(()->{DownloadsRepository.Item loaded=currentActionItem();
                main.post(()->{if(isFinishing()||isDestroyed())return;
                    if(loaded==null){notifyUser("Загрузка больше не найдена");return;}
                    selected=loaded;openFinishedFile(row);});});
            return;
        }
        if(DownloadsRepository.LINK.equals(book.type)){
            String batchId=book.id.replaceFirst("^link:","");
            LinkDownloadStore.Batch batch=LinkDownloadStore.get(this,batchId);
            if(batch==null||row.ordinal<0||row.ordinal>=batch.files.size()
                ||!row.key.equals("link:"+batchId+":"+row.ordinal)){
                notifyUser("Файл больше не найден");return;
            }
            LinkDownloadStore.Entry file=batch.files.get(row.ordinal);
            if(file.savedUri==null||file.savedUri.isEmpty()||!"completed".equals(file.status)){
                notifyUser("Файл ещё не сохранён");return;
            }
            try{
                Intent opened;
                if("fb2".equalsIgnoreCase(file.format)||"epub".equalsIgnoreCase(file.format)
                    ||"html".equalsIgnoreCase(file.format)||"txt".equalsIgnoreCase(file.format)){
                    opened=new Intent(this,ReaderActivity.class).putExtra("book_uri",file.savedUri);
                }else{
                    opened=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(file.savedUri),
                        LinkFileTypes.mime(file.format)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivity(opened);
            }catch(Exception ex){notifyUser("Не удалось открыть файл: "+ex.getMessage());}
            return;
        }
        openContent(book);
    }
    private void linkBatchMenu(PopupMenu menu){
        if(selected==null||selected.link==null)return;
        String batchId=selected.id.replaceFirst("^link:","");
        boolean hasAudio=false;
        for(LinkDownloadStore.Entry file:selected.link.files)
            if("mp3".equalsIgnoreCase(file.format)&&"completed".equals(file.status))hasAudio=true;
        if(hasAudio)menu.getMenu().add("Добавить готовые MP3 в Аудиотеку").setOnMenuItemClickListener(m->{
            LinkDownloadStore.Batch current=LinkDownloadStore.get(this,batchId);
            if(current!=null){LinkDownloadStore.addToLibrary(this,current);notifyUser("MP3 добавлены в Аудиотеку");}
            return true;
        });
        menu.getMenu().add("Отменить оставшиеся загрузки").setOnMenuItemClickListener(m->{
            new SwipeDialogs.Builder(this).setTitle("Отменить загрузку файлов?")
                .setMessage("Готовые файлы останутся сохранёнными.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Отменить",(d,w)->launch(new Intent(this,DirectLinkDownloadService.class)
                    .setAction(DirectLinkDownloadService.CANCEL)
                    .putExtra(DirectLinkDownloadService.EXTRA_ID,batchId),false)).show();
            return true;
        });
        menu.getMenu().add("Открыть папку файлов").setOnMenuItemClickListener(m->{
            try{
                LinkDownloadStore.Batch current=LinkDownloadStore.get(this,batchId);
                if(current==null||current.treeUri.isEmpty())throw new IllegalStateException("Папка недоступна");
                Intent folder=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                folder.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if(Build.VERSION.SDK_INT>=26)folder.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse(current.treeUri));
                startActivity(folder);
            }catch(Exception ex){notifyUser("Не удалось открыть папку: "+ex.getMessage());}
            return true;
        });
    }
    private void withEdgeItem(int chapter,boolean force){
        DownloadsRepository.Item current=selected;
        if(current!=null&&DownloadsRepository.EDGE.equals(current.type)){
            enqueueEdge(current,chapter,force);return;
        }
        if(disk.isShutdown())return;
        disk.execute(()->{DownloadsRepository.Item resolved=currentActionItem();
            main.post(()->{if(isFinishing()||isDestroyed())return;
                if(resolved==null||!DownloadsRepository.EDGE.equals(resolved.type))
                    notifyUser("Произведение Edge TTS недоступно");
                else enqueueEdge(resolved,chapter,force);});});
    }
    private void fileMenu(ImageView anchor,FileRow row){
        if(row==null)return;
        // A row restored from DownloadRowsSnapshot deliberately has source=null.
        // Menu creation must NEVER depend on a background reload or an active Edge Task.
        PopupMenu menu=new PalettePopupMenu(this,anchor);
        menu.getMenu().add("В очередь / повторить").setOnMenuItemClickListener(m->{
            workerAction(()->{boolean ok=applyFileAction(row,"retry");return ok?"Задача добавлена":"Уже готово или выполняется";});return true;});
        if(row.percent==100){menu.getMenu().add("Открыть готовое").setOnMenuItemClickListener(m->{openFinishedFile(row);return true;});}
        if(selected!=null&&DownloadsRepository.LINK.equals(selected.type))linkBatchMenu(menu);
        if(row.key.startsWith("edge:")&&id.startsWith("edge:")&&row.ordinal>=0){
            menu.getMenu().add("Переозвучить главу").setOnMenuItemClickListener(m->{
                new SwipeDialogs.Builder(this).setTitle("Переподготовить главу?")
                    .setMessage("Сохранённые MP3 останутся до завершения новой озвучки.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Подготовить",(d,w)->withEdgeItem(row.ordinal,true)).show();return true;});
            menu.getMenu().add("Сохранить готовые MP3").setOnMenuItemClickListener(m->{
                workerAction(()->{DownloadsRepository.Item current=currentActionItem();
                    if(current==null||!DownloadsRepository.EDGE.equals(current.type))return "Произведение не найдено";
                    return EdgeChapterActions.saveCached(this,current.bookUri,current.title,row.ordinal);});return true;});
        }
        menu.getMenu().add("Удалить файл").setOnMenuItemClickListener(m->{
            new SwipeDialogs.Builder(this).setTitle("Удалить файл?").setMessage(row.name)
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(d,w)->workerAction(()->applyFileAction(row,"delete")?
                    "Файл удалён":"Не удалось удалить файл")).show();return true;});
        menu.show();
    }
    private final class FileAdapter extends RecyclerView.Adapter<FileAdapter.Holder>{
        private final ArrayList<FileRow> items=new ArrayList<>();
        FileAdapter(){setHasStableIds(true);}
        void submit(ArrayList<FileRow> incoming){
            boolean sameKeys=items.size()==incoming.size();
            if(sameKeys)for(int i=0;i<items.size();i++)if(!items.get(i).key.equals(incoming.get(i).key)){
                sameKeys=false;break;
            }
            if(!sameKeys){items.clear();items.addAll(incoming);notifyDataSetChanged();return;}
            for(int i=0;i<items.size();i++){
                FileRow previous=items.set(i,incoming.get(i));
                if(!previous.same(incoming.get(i)))notifyItemChanged(i);
            }
        }
        @Override public long getItemId(int i){return items.get(i).key.hashCode();}
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout card=new LinearLayout(UnifiedDownloadManagerActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(rounded(bg,12));
            RecyclerView.LayoutParams outer=new RecyclerView.LayoutParams(-1,-2);outer.bottomMargin=dp(8);card.setLayoutParams(outer);
            LinearLayout head=new LinearLayout(UnifiedDownloadManagerActivity.this);head.setGravity(Gravity.CENTER_VERTICAL);card.addView(head);
            TextView name=text("",14,true);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            ImageView complete=new ImageView(UnifiedDownloadManagerActivity.this);
            complete.setImageResource(R.drawable.ic_download_chapter_complete);
            complete.setImageTintList(android.content.res.ColorStateList.valueOf(UiColorScheme.accent(UnifiedDownloadManagerActivity.this)));
            complete.setContentDescription("Глава загружена");
            complete.setVisibility(View.GONE);
            LinearLayout.LayoutParams checkLp=new LinearLayout.LayoutParams(dp(16),dp(16));
            checkLp.rightMargin=dp(5);head.addView(complete,checkLp);
            ImageView more=bareMenuIcon("Действия файла");
            head.addView(more,new LinearLayout.LayoutParams(dp(40),dp(40)));
            TextView detail=text("",12,false);detail.setTextColor(muted);place(card,detail,3);
            ProgressBar bar=new ProgressBar(UnifiedDownloadManagerActivity.this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setMax(100);UiColorScheme.tintProgress(bar,UnifiedDownloadManagerActivity.this);
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(5));p.topMargin=dp(7);card.addView(bar,p);
            return new Holder(card,name,detail,bar,more,complete);
        }
        @Override public void onBindViewHolder(Holder h,int position){
            FileRow row=items.get(position);
            h.title.setText(row.name);h.title.setMaxLines(2);
            boolean edgeRow=row.key.startsWith("edge:");
            boolean chapterReady=edgeRow?row.percent==100&&
                (row.status.contains("сохранены")||row.status.contains("готов")):
                row.percent==100;
            h.completed.setVisibility(chapterReady?View.VISIBLE:View.GONE);
            StringBuilder status=new StringBuilder(row.status);
            if(row.percent>0&&row.percent<100)status.append(" · ").append(row.percent).append('%');
            if(row.bytes>0)status.append(" · ").append(size(row.bytes));
            if(!row.detail.isEmpty()&&!row.detail.equals(row.status))status.append(" · ").append(row.detail);
            h.detail.setText(status.toString());
            h.bar.setVisibility(row.percent>=0&&row.percent<100?View.VISIBLE:View.GONE);
            if(row.percent>=0)h.bar.setProgress(row.percent);
            h.itemView.setBackground(rounded(checked.contains(row.key)?UiColorScheme.selected(UnifiedDownloadManagerActivity.this):bg,12));
            h.more.setOnClickListener(v->fileMenu(h.more,row));
            h.itemView.setOnLongClickListener(v->{if(!checked.add(row.key))checked.remove(row.key);
                notifyItemChanged(h.getBindingAdapterPosition());updateSelectionBar();return true;});
            h.itemView.setOnClickListener(v->{
                if(!checked.isEmpty()){
                    if(!checked.add(row.key))checked.remove(row.key);
                    notifyItemChanged(h.getBindingAdapterPosition());updateSelectionBar();
                }else if(row.percent==100&&selected!=null)openFinishedFile(row);
                else fileMenu(h.more,row);
            });
        }
        @Override public int getItemCount(){return items.size();}
        final class Holder extends RecyclerView.ViewHolder{
            final TextView title,detail;final ProgressBar bar;final ImageView more,completed;
            Holder(View v,TextView name,TextView state,ProgressBar bar,ImageView menu,ImageView completed){
                super(v);title=name;detail=state;this.bar=bar;more=menu;this.completed=completed;
            }
        }
    }
}
