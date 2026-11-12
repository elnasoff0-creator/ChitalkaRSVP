package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.DefaultItemAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unified online-download dashboard. Neither downloader is owned/started by this screen. */
public final class DownloadActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949,GREEN=0xff2b6145;
    private static final int TRANSFER_REQUEST=12521;
    private static final int EDGE_AUDIOBOOK_REQUEST=12522;
    private DownloadsRepository.Item pendingEdgeAudiobook;
    private DownloadsRepository.Item pendingTransfer;
    private String pendingTransferId="",pendingEdgeAudiobookId="";
    private static final String FILTER_KEY="downloads_filter_v2";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean visible,bound,refreshInFlight;
    private final ExecutorService scan=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"DownloadListDisk");t.setPriority(Thread.MIN_PRIORITY);return t;});
    private EdgeStreamingService edge;
    private TextView summary;
    private RecyclerView list;
    private JobsAdapter adapter;
    private int filter;
    private final ArrayList<DisplayRow> rows=new ArrayList<>();
    private ArrayList<DownloadsRepository.Item> lastAll=new ArrayList<>();
    private static volatile ArrayList<DownloadsRepository.Item> sharedSnapshot;
    private long diskScannedAt;
    private long mangaCoversAt;
    private int archiveRevision;
    private final Runnable ticker=new Runnable(){@Override public void run(){if(!visible)return;refresh();main.postDelayed(this,2600);}};
    private final ServiceConnection edgeConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){edge=((EdgeStreamingService.LocalBinder)b).getService();refresh();}
        @Override public void onServiceDisconnected(ComponentName n){edge=null;refresh();}
    };
    private static final class DisplayRow{
        final String section;final DownloadsRepository.Item item;
        DisplayRow(String section,DownloadsRepository.Item item){this.section=section;this.item=item;}
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String str,int size,boolean bold){TextView t=new TextView(this);t.setText(str);t.setTextSize(size);t.setTextColor(INK);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView center(String str,int size){TextView t=text(str,size,false);t.setGravity(Gravity.CENTER);return t;}
    private ImageView icon(int image,String hint,int size){ImageView v=new ImageView(this);v.setImageResource(image);v.setColorFilter(INK);v.setPadding(dp(11),dp(11),dp(11),dp(11));v.setBackground(bg(PANEL,16));v.setContentDescription(hint);return v;}
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        filter=getSharedPreferences("reader",0).getInt(FILTER_KEY,0);
        if(saved!=null){
            // Restoring an Activity must never traverse SAF directories on the UI thread.
            pendingTransferId=saved.getString("transfer_item_id","");
            pendingEdgeAudiobookId=saved.getString("edge_audiobook_item_id","");
        }
        build();
        bound=bindService(new Intent(this,EdgeStreamingService.class),edgeConnection,BIND_AUTO_CREATE);
    }
    @Override protected void onSaveInstanceState(Bundle state){
        if(pendingTransfer!=null||!pendingTransferId.isEmpty())state.putString("transfer_item_id",pendingTransfer==null?pendingTransferId:pendingTransfer.id);
        if(pendingEdgeAudiobook!=null||!pendingEdgeAudiobookId.isEmpty())state.putString("edge_audiobook_item_id",pendingEdgeAudiobook==null?pendingEdgeAudiobookId:pendingEdgeAudiobook.id);
        super.onSaveInstanceState(state);
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(25),dp(12),dp(12));root.setBackgroundColor(BG);setContentView(root);SafeSystemBars.matchBackground(this,BG);
        HorizontalScrollView toolbarScroll=new HorizontalScrollView(this);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        root.addView(toolbarScroll,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbarScroll.addView(toolbar,new HorizontalScrollView.LayoutParams(-2,dp(52)));
        ImageView back=icon(R.drawable.ic_menu_lines,"Главное меню",44);
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        back.setOnClickListener(v->UniversalDrawer.show(this));
        toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        toolbarAction(toolbar,R.drawable.ic_search_audiobook,"Поиск аудиокниг",()->startActivity(new Intent(this,KvuheSearchActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_manga_search,"Поиск манги · MangaLib",()->startActivity(new Intent(this,MangaLibSearchActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_ranobelib_search,"Поиск ранобэ · RanobeLIB",()->startActivity(new Intent(this,RanobeLibSearchActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_import_link,"Импорт по ссылке",()->startActivity(new Intent(this,LinkDownloadActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_autoload,"Подготовить книгу Edge",()->startActivity(new Intent(this,EdgeFullBookActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_download_archive,"Архив загрузок",()->startActivity(new Intent(this,DownloadArchiveActivity.class)));
        TextView heading=text("Загрузки",25,true);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);hl.topMargin=dp(14);root.addView(heading,hl);
        String[] currentFilters={"Все загрузки","В процессе","Аудиокниги","Edge TTS","По ссылке","Манга","Ранобэ"};
        TextView filterSelect=center(currentFilters[Math.max(0,Math.min(filter,6))]+"  ▾",16);
        filterSelect.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
        filterSelect.setBackground(bg(ReaderButtonColors.neutral(this,PANEL),15));
        filterSelect.setTextColor(ReaderButtonColors.contrastingText(ReaderButtonColors.neutral(this,PANEL)));
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(49));fl.topMargin=dp(14);root.addView(filterSelect,fl);
        filterSelect.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(this,filterSelect);
            String[] names={"Все загрузки","В процессе","Аудиокниги","Edge TTS","По ссылке","Манга","Ранобэ"};
            for(int i=0;i<names.length;i++){final int ix=i;menu.getMenu().add(names[i]).setOnMenuItemClickListener(item->{
                filter=ix;filterSelect.setText(names[ix]+"  ▾");getSharedPreferences("reader",0).edit().putInt(FILTER_KEY,ix).apply();showCachedFilter();return true;});}
            menu.getMenu().add(1,101,99,"Удалить скачанные файлы")
                .setOnMenuItemClickListener(item->{confirmDeleteAllDownloads();return true;});
            menu.show();
        });
        summary=text("Данные обновляются в фоне",13,false);summary.setTextColor(MUTED);summary.setGravity(Gravity.CENTER);
        summary.setPadding(0,dp(11),0,dp(11));root.addView(summary);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new JobsAdapter();adapter.setHasStableIds(true);
        DefaultItemAnimator animations=new DefaultItemAnimator();
        animations.setSupportsChangeAnimations(false);animations.setRemoveDuration(170);animations.setMoveDuration(170);
        list.setItemAnimator(animations);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        attachArchiveSwipe();
    }
    private void toolbarAction(LinearLayout toolbar,int drawable,String hint,Runnable action){
        ImageView button=icon(drawable,hint,44);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(44),dp(44));lp.leftMargin=dp(5);
        toolbar.addView(button,lp);button.setOnClickListener(v->action.run());
    }
    private void addMainAction(LinearLayout parent,int drawable,String title,Runnable click,boolean right){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);
        card.setBackground(bg(PANEL,19));card.setPadding(dp(6),dp(8),dp(6),dp(7));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-1,1);if(right)params.leftMargin=dp(10);parent.addView(card,params);
        ImageView image=new ImageView(this);image.setImageResource(drawable);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(image,new LinearLayout.LayoutParams(dp(61),dp(61)));
        TextView label=center(title,14);label.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);card.addView(label,lp);
        card.setContentDescription(title);card.setOnClickListener(v->click.run());
    }

    @Override protected void onResume(){super.onResume();visible=true;
        // Render a cached dashboard immediately. Disk/SAF is verified off-thread.
        ArrayList<DownloadsRepository.Item> previous=sharedSnapshot;
        if(previous==null||previous.isEmpty())previous=DownloadListSnapshot.load(this);
        if(previous!=null&&!previous.isEmpty()){
            lastAll=new ArrayList<>(previous);showCachedFilter();
            int finished=0;for(DownloadsRepository.Item item:lastAll)if(item.ready)finished++;
            summary.setText("Загрузок: "+lastAll.size()+" · Готово: "+finished+" · Обновляем…");
        }
        // Cover indexing previously traversed all downloaded tracks on the UI thread.
        // Move discovery and artwork checks away from Activity startup.
        new Thread(()->AudiobookCoverStore.backfill(getApplicationContext(),
            ()->{if(visible)refresh();}),"DownloadCoverIndex").start();
        main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(edgeConnection);main.removeCallbacksAndMessages(null);scan.shutdownNow();super.onDestroy();}
    /** Never traverse book directories, parse JSON or stat MP3 on the UI thread.
     * Only one scan can run; refresh requests that arrive while it runs are merged
     * into the next periodic tick, so a slow SD card cannot create a backlog.
     */
    private void refresh(){
        if(!visible||adapter==null||refreshInFlight||scan.isShutdown())return;
        long now=android.os.SystemClock.elapsedRealtime();
        if(!lastAll.isEmpty()&&now-diskScannedAt<7000L)return;
        refreshInFlight=true;
        final int currentFilter=filter;
        final int scannedRevision=archiveRevision;
        // Read the bound Service only on the main thread. Background scanning
        // receives immutable values and never touches its mutable playback state.
        final String liveUri=edge!=null&&edge.isActive()?edge.bookUri():"";
        final String liveTitle=liveUri.isEmpty()?"":edge.title();
        final ArrayList<DisplayRow> before=new ArrayList<>(rows);
        scan.execute(()->{
            try{
                ArrayList<DownloadsRepository.Item> all=DownloadsRepository.load(getApplicationContext(),null);
                boolean coversDue=android.os.SystemClock.elapsedRealtime()-mangaCoversAt>30000L;
                for(DownloadsRepository.Item entry:all)if(coversDue&&DownloadsRepository.MANGA.equals(entry.type)){
                    java.util.ArrayList<MangaLibStorage.Job> chapters=MangaLibStorage.seriesJobs(getApplicationContext(),entry.source);
                    if(!chapters.isEmpty()){
                        MangaDownloadCover.ensure(getApplicationContext(),chapters.get(0));
                        File cover=MangaDownloadCover.file(getApplicationContext(),entry.source);
                        if(cover.isFile()&&cover.length()>100)entry.cover=cover.toURI().toString();
                    }
                }
                if(!liveUri.isEmpty()){
                    boolean found=false;
                    for(DownloadsRepository.Item item:all)if(liveUri.equals(item.bookUri)){
                        found=true;item.running=true;item.ready=false;
                        item.status="Идёт озвучивание";item.failed=false;
                        break;
                    }
                    if(!found){
                        DownloadsRepository.Item live=new DownloadsRepository.Item();
                        live.type=DownloadsRepository.EDGE;live.id="edge:"+liveUri;
                        live.bookUri=liveUri;live.cover=liveUri;live.title=liveTitle;live.sourceTitle=liveTitle;
                        live.status="Идёт озвучивание";live.description="Текущий аудиофрагмент";
                        live.running=true;all.add(0,live);
                    }
                }
                for(DownloadsRepository.Item item:all)item.title=BookTitleStore.downloadTitle(getApplicationContext(),item);
                int active=0,done=0;
                java.util.Set<String> hiddenForSummary=DownloadArchiveStore.hiddenIds(getApplicationContext());
                for(DownloadsRepository.Item item:all){
                    if(hiddenForSummary.contains(item.id))continue;
                    if(item.ready)done++;else active++;
                }
                ArrayList<DisplayRow> after=makeRows(all,currentFilter,DownloadArchiveStore.hiddenIds(getApplicationContext()));
                DiffUtil.DiffResult changes=DiffUtil.calculateDiff(new DiffUtil.Callback(){
                    @Override public int getOldListSize(){return before.size();}
                    @Override public int getNewListSize(){return after.size();}
                    @Override public boolean areItemsTheSame(int a,int b){
                        return rowId(before.get(a)).equals(rowId(after.get(b)));
                    }
                    @Override public boolean areContentsTheSame(int a,int b){
                        return rowState(before.get(a)).equals(rowState(after.get(b)));
                    }
                },false);
                DownloadListSnapshot.save(getApplicationContext(),all);
                final int running=active,complete=done;
                main.post(()->{
                    refreshInFlight=false;
                    if(!visible||adapter==null)return;
                    diskScannedAt=android.os.SystemClock.elapsedRealtime();
                    if(coversDue)mangaCoversAt=diskScannedAt;
                    sharedSnapshot=new ArrayList<>(all);
                    lastAll=all;
                    if(!pendingTransferId.isEmpty()||!pendingEdgeAudiobookId.isEmpty())for(DownloadsRepository.Item found:all){
                        if(found.id.equals(pendingTransferId))pendingTransfer=found;
                        if(found.id.equals(pendingEdgeAudiobookId))pendingEdgeAudiobook=found;
                    }
                    if(filter!=currentFilter||archiveRevision!=scannedRevision){diskScannedAt=0;showCachedFilter();return;}
                    rows.clear();rows.addAll(after);
                    summary.setText("Онлайн-загрузки: "+(running+complete)+"  ·  В процессе: "+running+"  ·  Готово: "+complete);
                    changes.dispatchUpdatesTo(adapter);
                });
            }catch(Exception error){
                android.util.Log.w("DownloadActivity","Index scan failed",error);
                main.post(()->{refreshInFlight=false;if(visible&&rows.isEmpty())summary.setText("Не удалось загрузить список");});
            }
        });
    }
    /** Filter changes only touch the already indexed snapshot, never wait on storage. */
    private void showCachedFilter(){
        if(lastAll.isEmpty()){refresh();return;}
        ArrayList<DisplayRow> next=makeRows(lastAll,filter,DownloadArchiveStore.hiddenIds(this));
        ArrayList<DisplayRow> previous=new ArrayList<>(rows);
        DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
            @Override public int getOldListSize(){return previous.size();}
            @Override public int getNewListSize(){return next.size();}
            @Override public boolean areItemsTheSame(int old,int nextIndex){
                return rowId(previous.get(old)).equals(rowId(next.get(nextIndex)));
            }
            @Override public boolean areContentsTheSame(int old,int nextIndex){
                return rowState(previous.get(old)).equals(rowState(next.get(nextIndex)));
            }
        },false);
        rows.clear();rows.addAll(next);diff.dispatchUpdatesTo(adapter);
    }
    private static String rowId(DisplayRow row){
        return row.item==null?"section:"+row.section:row.item.id;
    }
    private static String rowState(DisplayRow row){
        if(row.item==null)return row.section;
        DownloadsRepository.Item i=row.item;
        return i.title+"\n"+i.description+"\n"+i.status+"\n"+i.cover+"\n"+
            i.ready+":"+i.running+":"+i.failed+":"+i.paused+":"+i.done+":"+i.total+":"+i.coveragePercent;
    }
    private static ArrayList<DisplayRow> makeRows(ArrayList<DownloadsRepository.Item> all,int filter,java.util.Set<String> hidden){
        ArrayList<DisplayRow> result=new ArrayList<>();
        ArrayList<DownloadsRepository.Item> unfinished=new ArrayList<>(),ready=new ArrayList<>();
        for(DownloadsRepository.Item item:all){
            if(hidden.contains(item.id))continue;
            if(filter==1&&item.ready)continue;
            if(filter==2&&!DownloadsRepository.AUDIO.equals(item.type))continue;
            if(filter==3&&!DownloadsRepository.EDGE.equals(item.type))continue;
            if(filter==4&&!DownloadsRepository.LINK.equals(item.type))continue;
            if(filter==5&&!DownloadsRepository.MANGA.equals(item.type))continue;
            if(filter==6&&!DownloadsRepository.RANOBE.equals(item.type))continue;
            (item.ready?ready:unfinished).add(item);
        }
        if(!unfinished.isEmpty()){
            result.add(new DisplayRow("В процессе",null));
            for(DownloadsRepository.Item item:unfinished)result.add(new DisplayRow(null,item));
        }
        if(!ready.isEmpty()){
            result.add(new DisplayRow("Готово",null));
            for(DownloadsRepository.Item item:ready)result.add(new DisplayRow(null,item));
        }
        if(result.isEmpty())result.add(new DisplayRow(filter==0?"Загрузок пока нет":"Нет загрузок в этом разделе",null));
        return result;
    }
    private void send(DownloadsRepository.Item item,String command){
        Intent i=new Intent(this,DownloadWorker.class).setAction(command).putExtra("source",item.source);
        try{if(Build.VERSION.SDK_INT>=26&&DownloadWorker.START.equals(command))startForegroundService(i);else startService(i);}
        catch(Exception ex){Toast.makeText(this,"Ошибка: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        main.postDelayed(this::refresh,250);
    }
    private void open(DownloadsRepository.Item item){
        if(item.previewOnly){Toast.makeText(this,"Обновляем сведения о загрузке…",Toast.LENGTH_SHORT).show();return;}
        if(DownloadsRepository.RANOBE.equals(item.type)){
            startActivity(new Intent(this,RanobeLibBookActivity.class).putExtra("ranobelib_slug",item.source));
            return;
        }
        if(DownloadsRepository.MANGA.equals(item.type)){
            // A series card always opens its volume/chapter manager, like audio downloads.
            openMangaChapters(item);
            return;
        }
        if(DownloadsRepository.LINK.equals(item.type)){startActivity(new Intent(this,LinkDownloadDetailsActivity.class).putExtra(DirectLinkDownloadService.EXTRA_ID,item.link.id));}
        else if(DownloadsRepository.EDGE.equals(item.type)){
            // After cache eviction a SAF-only book has no local job; ReaderActivity
            // rehydrates the saved chapter's clips and WordBoundary on opening.
            if(item.folder==null)listenEdge(item);else openEdgeChapters(item);
        }else if(item.ready){
            startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_book_id","url:"+item.source));
        }else startActivity(new Intent(this,UrlImportActivity.class).putExtra("source",item.source));
    }
    private void openMangaChapters(DownloadsRepository.Item item){
        startActivity(new Intent(this,MangaLibChapterDownloadsActivity.class)
            .putExtra("mangalib_slug",item.source));
    }
    private void openEdgeChapters(DownloadsRepository.Item item){
        startActivity(new Intent(this,EdgeChapterDownloadsActivity.class)
            .putExtra("book_uri",item.bookUri).putExtra("book_title",item.title));
    }
    private void listenEdge(DownloadsRepository.Item item){
        if(item.fullJob!=null){
            getSharedPreferences("reader",0).edit().putString("edge_voice",item.fullJob.voice)
                .putString("edge_rate",item.fullJob.rate).apply();
        }
        startActivity(new Intent(this,ReaderActivity.class)
            .putExtra("reading_mode_override",3).putExtra("book_uri",item.bookUri));
    }
    private void fullCommand(DownloadsRepository.Item item,boolean pause){
        if(item.fullJob==null)return;
        Intent intent=new Intent(this,EdgeFullBookService.class)
            .setAction(pause?EdgeFullBookService.PAUSE:EdgeFullBookService.RESUME)
            .putExtra(EdgeFullBookService.EXTRA_URI,item.bookUri);
        try{
            if(!pause&&Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            main.postDelayed(this::refresh,350);
        }catch(Exception e){Toast.makeText(this,"Ошибка Edge: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void confirmCancel(DownloadsRepository.Item item){
        new SwipeDialogs.Builder(this).setTitle("Отменить загрузку?")
            .setMessage("Можно сохранить уже скачанные главы или удалить файлы этой аудиокниги.")
            .setNegativeButton("Назад",null)
            .setNeutralButton("Сохранить файлы",(d,w)->send(item,DownloadWorker.CANCEL))
            .setPositiveButton("Удалить файлы",(d,w)->eraseAudio(item,true)).show();
    }
    private void eraseAudio(DownloadsRepository.Item item,boolean files){
        if(item.job==null)return;
        send(item,DownloadWorker.CANCEL);
        new Thread(()->{BookImportManager.remove(this,item.job,files);runOnUiThread(this::refresh);},"remove-audiobook-task").start();
    }
    private void openFolder(DownloadsRepository.Item item){
        if(DownloadsRepository.EDGE.equals(item.type)){
            EdgeFolderPicker.browse(this);return;
        }
        if(item.folder==null||!item.folder.isDirectory()){Toast.makeText(this,"Папка аудио пока не создана",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,EdgeFileExplorerActivity.class).putExtra("book_folder",item.folder.getAbsolutePath()));
    }
    private void eraseEdge(DownloadsRepository.Item item,boolean files){
        if(item.fullJob!=null&&item.running){
            Toast.makeText(this,"Сначала приостановите полную подготовку",Toast.LENGTH_LONG).show();return;
        }
        if(edge!=null&&item.bookUri.equals(edge.bookUri())&&edge.isActive())edge.setActive(false);
        if(item.fullJob!=null)fullCommand(item,true);
        if(files){
            if(edge!=null&&item.bookUri.equals(edge.bookUri()))edge.clearAudio(null);
            DownloadsRepository.hideEdge(this,item.bookUri);
            EdgeBookStorage.removeBook(this,item.bookUri);
        }else DownloadsRepository.hideEdge(this,item.bookUri);
        refresh();
    }
    private void confirmDelete(DownloadsRepository.Item item,boolean files){
        String what=files?"Удалить загрузку и файлы?":"Убрать задание из списка?";
        new SwipeDialogs.Builder(this).setTitle(what)
            .setMessage(files?"Аудиофайлы будут удалены. Для Edge исходная текстовая книга останется в библиотеке.":"Скачанные файлы сохранятся на устройстве.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(d,w)->{
                if(DownloadsRepository.EDGE.equals(item.type))eraseEdge(item,files);else eraseAudio(item,files);
            }).show();
    }
    private void linkCommand(DownloadsRepository.Item item,String command){
        Intent i=new Intent(this,DirectLinkDownloadService.class).setAction(command).putExtra(DirectLinkDownloadService.EXTRA_ID,item.link.id);
        try{if(Build.VERSION.SDK_INT>=26&&DirectLinkDownloadService.START.equals(command))startForegroundService(i);else startService(i);}
        catch(Exception ex){Toast.makeText(this,"Ошибка: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        main.postDelayed(this::refresh,300);
    }
    private void deleteLink(DownloadsRepository.Item item,boolean files){
        if(item.running){Toast.makeText(this,"Сначала поставьте загрузку на паузу",Toast.LENGTH_LONG).show();return;}
        if(files){for(LinkDownloadStore.Entry e:item.link.files){
            if(!e.savedUri.isEmpty())try{androidx.documentfile.provider.DocumentFile doc=androidx.documentfile.provider.DocumentFile.fromSingleUri(this,android.net.Uri.parse(e.savedUri));if(doc!=null)doc.delete();}catch(Exception ignored){}
        }}
        LinkDownloadStore.remove(this,item.link.id);refresh();
    }
    /** Explicitly confirmed cleanup of completed download jobs, never original book sources. */
    private void confirmDeleteAllDownloads(){
        new SwipeDialogs.Builder(this).setTitle("Удалить скачанные файлы?")
            .setMessage("Будут удалены загруженные файлы неактивных заданий. Задания, исходные книги и настройки сохраняются; активные загрузки пропускаются. Записи без файлов можно будет загрузить повторно.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(dialog,which)->deleteAllDownloads()).show();
    }
    private void deleteAllDownloads(){
        final String playingBook=edge!=null&&edge.isActive()?edge.bookUri():"";
        new Thread(()->{
            final ArrayList<DownloadsRepository.Item> items=DownloadsRepository.load(getApplicationContext(),null);
            int deleted=0,skipped=0;
            for(DownloadsRepository.Item item:items){
                if(item.running || (DownloadsRepository.EDGE.equals(item.type)&&item.bookUri.equals(playingBook))){skipped++;continue;}
                try{
                    if(DownloadsRepository.AUDIO.equals(item.type)&&item.job!=null){
                        BookImportManager.Job job=item.job;
                        for(int n=0;n<job.chapters.size();n++){
                            java.io.File file=BookImportManager.trackFile(this,job,n);
                            if(file.isFile()&&file.delete()&&n<job.states.size())job.states.set(n,0);
                        }
                        job.paused=true;job.status="paused";job.bytes=0;job.total=0;
                        BookImportManager.put(this,job);
                        deleted++;
                    }else if(DownloadsRepository.EDGE.equals(item.type)){
                        // Keep queue metadata, chapter index, source book and cover.
                        java.io.File folder=item.folder;
                        if(folder!=null&&folder.isDirectory()){
                            java.io.File[] cached=folder.listFiles();
                            if(cached!=null)for(java.io.File file:cached){
                                String name=file.getName();
                                if(file.isFile()&&(name.endsWith(".mp3")||name.endsWith(".mp3.words.json")
                                    ||name.endsWith(".mp3.part")))file.delete();
                            }
                        }
                        EdgeAudioManager.invalidate(folder);
                        deleted++;
                    }else if(DownloadsRepository.LINK.equals(item.type)&&item.link!=null){
                        for(LinkDownloadStore.Entry entry:item.link.files){
                            if(entry.savedUri.isEmpty())continue;
                            androidx.documentfile.provider.DocumentFile doc=androidx.documentfile.provider.DocumentFile.fromSingleUri(this,android.net.Uri.parse(entry.savedUri));
                            if(doc!=null&&doc.delete()){
                                entry.savedUri="";entry.status="queued";entry.bytes=0;
                            }
                        }
                        LinkDownloadStore.put(this,item.link);deleted++;
                    }
                }catch(Exception ignored){skipped++;}
            }
            final int ok=deleted,skip=skipped;
            runOnUiThread(()->{
                refresh();
                Toast.makeText(this,"Обработано загрузок: "+ok+(skip>0?" · пропущено: "+skip:""),Toast.LENGTH_LONG).show();
            });
        },"download-bulk-cleanup").start();
    }
    private void exportMangaChapter(String slug){
        ArrayList<MangaLibStorage.Job> complete=new ArrayList<>();
        for(MangaLibStorage.Job job:MangaLibStorage.jobs(this)){
            if(slug.equals(job.slug)&&"complete".equals(job.status)
                &&job.file!=null&&new File(job.file).isFile())complete.add(job);
        }
        if(complete.isEmpty()){
            Toast.makeText(this,"Скачанных глав для экспорта пока нет",Toast.LENGTH_SHORT).show();return;
        }
        complete.sort((a,b)->{
            int vol=compareMangaNumber(a.volume,b.volume);
            return vol==0?compareMangaNumber(a.number,b.number):vol;
        });
        String[] titles=new String[complete.size()];
        for(int i=0;i<complete.size();i++){
            MangaLibStorage.Job job=complete.get(i);
            titles[i]="Том "+job.volume+" · Глава "+job.number+" — "+job.chapter;
        }
        new SwipeDialogs.Builder(this).setTitle("Экспорт CBZ · выберите главу")
            .setItems(titles,(dialog,index)->{
                File file=new File(complete.get(index).file);
                getSharedPreferences("reader",0).edit()
                    .putString("mangalib_export_pending",file.getAbsolutePath()).apply();
                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("application/zip");
                save.putExtra(Intent.EXTRA_TITLE,file.getName());
                startActivityForResult(save,12520);
            }).setNegativeButton("Отмена",null).show();
    }
    private static int compareMangaNumber(String a,String b){
        try{return Double.compare(Double.parseDouble(a),Double.parseDouble(b));}
        catch(Exception ignored){return a.compareToIgnoreCase(b);}
    }
    private void transferDialog(DownloadsRepository.Item item){
        if(item.running){Toast.makeText(this,"Сначала приостановите загрузку",Toast.LENGTH_LONG).show();return;}
        new SwipeDialogs.Builder(this).setTitle("Перенести файлы")
            .setMessage("Выберите папку назначения. Копии проверяются по SHA-256. Для аудиокниг и файлов по ссылке пути в библиотеках обновляются, после чего старые файлы удаляются. Манга и Edge TTS пока сохраняют локальные оригиналы для корректного чтения. Существующие файлы не перезаписываются.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Выбрать папку",(d,w)->{
                pendingTransfer=item;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,TRANSFER_REQUEST);
            }).show();
    }
    private void showMangaFolder(String slug){
        java.io.File folder=new java.io.File(MangaLibStorage.root(this),slug);
        // App-private Android/data cannot be opened by arbitrary file managers.
        java.util.ArrayList<MangaLibStorage.Job> jobs=MangaLibStorage.seriesJobs(this,slug);
        if(jobs.isEmpty()){Toast.makeText(this,"Папка не содержит скачанных глав",Toast.LENGTH_SHORT).show();return;}
        String[] files=new String[jobs.size()];
        for(int i=0;i<jobs.size();i++){
            java.io.File file=new java.io.File(jobs.get(i).file);
            files[i]="Том "+jobs.get(i).volume+" / "+file.getName()+" · "+(file.length()/1024)+" КБ";
        }
        new SwipeDialogs.Builder(this).setTitle("Открыть папку · "+folder.getName())
            .setItems(files,(d,i)->exportMangaChapter(slug))
            .setNeutralButton("Путь к папке",(d,w)->new SwipeDialogs.Builder(this)
                .setTitle("Папка файлов")
                .setMessage(folder.getAbsolutePath()+"\n\nФайлы находятся в закрытом каталоге Android. Чтобы получить доступ из другой программы, используйте экспорт CBZ.")
                .setPositiveButton("Закрыть",null).show())
            .setNegativeButton("Закрыть",null).show();
    }
    private void chooseMangaFilesToDelete(DownloadsRepository.Item item){
        if(item.running){Toast.makeText(this,"Приостановите загрузку перед удалением",Toast.LENGTH_LONG).show();return;}
        java.util.ArrayList<MangaLibStorage.Job> jobs=MangaLibStorage.seriesJobs(this,item.source);
        if(jobs.isEmpty()){Toast.makeText(this,"Нет скачанных CBZ",Toast.LENGTH_SHORT).show();return;}
        String[] titles=new String[jobs.size()];boolean[] checked=new boolean[jobs.size()];
        for(int i=0;i<jobs.size();i++){MangaLibStorage.Job j=jobs.get(i);titles[i]="Том "+j.volume+" · "+j.chapter;}
        new SwipeDialogs.Builder(this).setTitle("Удалить скачанные файлы")
            .setMultiChoiceItems(titles,checked,(d,i,v)->checked[i]=v)
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Далее",(d,w)->{
                int count=0;for(boolean selected:checked)if(selected)count++;
                if(count==0){Toast.makeText(this,"Выберите тома",Toast.LENGTH_SHORT).show();return;}
                new SwipeDialogs.Builder(this).setTitle("Подтвердить удаление")
                    .setMessage("Будут удалены "+count+" скачанных CBZ. Действие нельзя отменить. Остальные тома и их прогресс останутся.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Удалить",(dialog,which)->{
                        scan.execute(()->{
                            int removed=0;
                            for(int i=0;i<jobs.size();i++)if(checked[i]){
                                MangaLibStorage.Job job=jobs.get(i);java.io.File file=new java.io.File(job.file);
                                if(file.isFile()&&!file.delete())continue;
                                job.file="";job.done=0;job.bytes=0;job.status="cancelled";MangaLibStorage.put(getApplicationContext(),job);removed++;
                            }
                            ComicCovers.invalidate(getApplicationContext(),MangaLibStorage.comicUri(item.source));
                            File previousCover=MangaDownloadCover.file(getApplicationContext(),item.source);
                            CoverImageLoader.invalidateLocal(previousCover.toURI().toString());
                            MangaDownloadCover.invalidate(getApplicationContext(),item.source);
                            java.util.ArrayList<MangaLibStorage.Job> remaining=MangaLibStorage.seriesJobs(getApplicationContext(),item.source);
                            if(!remaining.isEmpty())MangaDownloadCover.ensure(getApplicationContext(),remaining.get(0));
                            final int deleted=removed;
                            main.post(()->{Toast.makeText(this,"Удалено CBZ: "+deleted,Toast.LENGTH_SHORT).show();refresh();});
                        });
                    }).show();
            }).show();
    }
    private void renameDownload(DownloadsRepository.Item item){
        BookRenameDialog.show(this,item.title,newName->{
            if(!BookTitleStore.renameDownload(this,item.id,item.type,item.bookUri,item.source,newName))return false;
            item.title=newName;
            for(DownloadsRepository.Item entry:lastAll)if(item.id.equals(entry.id))entry.title=newName;
            if(adapter!=null)adapter.notifyDataSetChanged();
            // A disk scan will restore stable job identities and reapply aliases.
            diskScannedAt=0;refresh();return true;
        });
    }
    private void menu(View anchor,DownloadsRepository.Item item){
        if(item.previewOnly){Toast.makeText(this,"Проверяем состояние загрузки…",Toast.LENGTH_SHORT).show();return;}
        if(DownloadsRepository.RANOBE.equals(item.type)){
            PopupMenu popup=new PopupMenu(this,anchor);
            popup.getMenu().add("Переименовать").setOnMenuItemClickListener(m->{renameDownload(item);return true;});
            if(item.ready)popup.getMenu().add("В архив").setOnMenuItemClickListener(m->{archive(item);return true;});
            popup.getMenu().add("Тома и главы").setOnMenuItemClickListener(m->{open(item);return true;});
            if(new File(RanobeLibStorage.book(this,item.source).getAbsolutePath()).isFile())
                popup.getMenu().add("Читать HTML").setOnMenuItemClickListener(m->{
                    startActivity(new Intent(this,ReaderActivity.class).putExtra("book_uri",item.bookUri));return true;});
            popup.getMenu().add(item.running?"Пауза":"Продолжить / повторить ошибки").setOnMenuItemClickListener(m->{
                if(item.running)RanobeLibQueue.pause(this,item.source);
                else RanobeLibQueue.resume(this,item.source);
                diskScannedAt=0;refresh();return true;});
            if(!item.ready)popup.getMenu().add("Отменить очередь").setOnMenuItemClickListener(m->{
                RanobeLibQueue.cancel(this,item.source);diskScannedAt=0;refresh();return true;});
            popup.getMenu().add("Удалить скачанную книгу и задания").setOnMenuItemClickListener(m->{
                new SwipeDialogs.Builder(this).setTitle("Удалить книгу RanobeLIB?")
                    .setMessage("HTML, главы и обложка этой книги будут удалены. Остальные произведения не затрагиваются.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                        RanobeLibQueue.cancel(this,item.source);
                        scan.execute(()->{RanobeLibStorage.deleteSeries(getApplicationContext(),item.source);main.post(()->{diskScannedAt=0;refresh();});});
                    }).show();return true;});
            popup.show();return;
        }
        if(DownloadsRepository.MANGA.equals(item.type)){
            PopupMenu p=new PopupMenu(this,anchor);
            p.getMenu().add("Переименовать").setOnMenuItemClickListener(m->{renameDownload(item);return true;});
            if(item.ready)p.getMenu().add(0,81,0,"В архив").setOnMenuItemClickListener(m->{archive(item);return true;});
            p.getMenu().add(0,1,0,"Главы и загрузка").setOnMenuItemClickListener(m->{openMangaChapters(item);return true;});
            p.getMenu().add(0,4,1,"Найти другие главы").setOnMenuItemClickListener(m->{
                startActivity(new Intent(this,MangaLibSearchActivity.class)
                    .putExtra("mangalib_query",item.source));return true;
            });
            if(!item.ready)p.getMenu().add(0,5,2,item.running?"Пауза загрузки манги":"Продолжить загрузку манги")
                .setOnMenuItemClickListener(m->{
                    if(item.running)MangaLibQueue.pause(this,item.source);
                    else MangaLibQueue.resume(this,item.source);
                    refresh();return true;
                });
            // A series contains multiple CBZ files; choose the chapter to export.
            // Also available when only some chapters have completed.
            p.getMenu().add(0,3,3,"Экспортировать CBZ главы").setOnMenuItemClickListener(m->{
                exportMangaChapter(item.source);return true;
            });
            if(!item.ready)p.getMenu().add(0,2,4,"Отменить очередь этой манги").setOnMenuItemClickListener(m->{
                // Do not cancel the global serial WorkManager queue: other manga
                // may have pending chapters. Workers consult the persisted job state.
                MangaLibQueue.cancelSeries(this,item.source);
                refresh();return true;
            });
            p.getMenu().add(0,32,6,"Открыть папку").setOnMenuItemClickListener(m->{showMangaFolder(item.source);return true;});
            p.getMenu().add(0,33,7,"Удалить скачанные файлы").setOnMenuItemClickListener(m->{chooseMangaFilesToDelete(item);return true;});
            p.getMenu().add(0,31,5,"Перенести файлы").setOnMenuItemClickListener(m->{transferDialog(item);return true;});
            p.show();return;
        }
        if(DownloadsRepository.LINK.equals(item.type)){
            PopupMenu p=new PopupMenu(this,anchor);
            p.getMenu().add(0,77,0,"Переименовать");
            if(item.ready)p.getMenu().add(0,81,0,"В архив");
            p.getMenu().add(0,1,0,"Список файлов");p.getMenu().add(0,2,1,"Добавить MP3 в библиотеку");
            if(!item.ready)p.getMenu().add(0,3,2,"Продолжить загрузку");
            p.getMenu().add(0,4,3,"Удалить из библиотеки/аудиотеки");p.getMenu().add(0,5,4,"Удалить скачанные файлы");
            p.getMenu().add(0,31,5,"Перенести файлы");
            p.setOnMenuItemClickListener(m->{switch(m.getItemId()){
                case 77:renameDownload(item);break;
                case 81:archive(item);break;
                case 1:open(item);break;
                case 2:LinkDownloadStore.addToLibrary(this,item.link);Toast.makeText(this,"Добавлено в библиотеку",Toast.LENGTH_SHORT).show();break;
                case 3:linkCommand(item,DirectLinkDownloadService.START);break;
                case 31:transferDialog(item);break;
                case 41:EdgeFolderPicker.show(this);break;
                case 4:case 5:final boolean erase=m.getItemId()==5;new SwipeDialogs.Builder(this).setTitle(erase?"Удалить загруженные MP3?":"Убрать задание из списка?")
                    .setMessage(erase?"Будут удалены файлы этого задания в выбранной папке.":"Файлы останутся на устройстве.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->deleteLink(item,erase)).show();break;
            }return true;});p.show();return;
        }
        PopupMenu popup=new PopupMenu(this,anchor);
        popup.getMenu().add(0,77,0,"Переименовать");
        if(item.ready)popup.getMenu().add(0,81,0,"В архив");
        if(DownloadsRepository.EDGE.equals(item.type)){
            popup.getMenu().add(0,1,0,"Главы и загрузка");
            popup.getMenu().add(0,9,1,"Открыть Edge-плеер");
            if(item.ready)popup.getMenu().add(0,42,2,"Перенести в аудиокниги");
            if(item.fullJob!=null&&!item.ready)
                popup.getMenu().add(0,8,1,item.running?"Пауза полной подготовки":"Продолжить полную подготовку");
        }else{
            popup.getMenu().add(0,1,0,item.ready?"Слушать аудиокнигу":"Подробнее / главы");
            if(!item.ready)popup.getMenu().add(0,5,1,item.running?"Приостановить":"Продолжить загрузку");
        }
        if(DownloadsRepository.EDGE.equals(item.type))
            popup.getMenu().add(0,41,2,"Путь к файлам");
        popup.getMenu().add(0,3,2,"Открыть файлы");
        popup.getMenu().add(0,6,3,"Убрать из списка");
        popup.getMenu().add(0,4,4,"Удалить скачанные файлы");
        popup.getMenu().add(0,31,5,"Перенести файлы");
        popup.setOnMenuItemClickListener(m->{
            switch(m.getItemId()){
                case 77:renameDownload(item);break;
                case 81:archive(item);break;
                case 1:open(item);break;
                case 3:openFolder(item);break;
                case 4:confirmDelete(item,true);break;
                case 5:send(item,item.running?DownloadWorker.PAUSE:DownloadWorker.START);break;
                case 6:confirmDelete(item,false);break;
                case 8:fullCommand(item,item.running);break;
                case 9:listenEdge(item);break;
                case 31:transferDialog(item);break;
                case 41:EdgeFolderPicker.show(this);break;
                case 42:transferEdgeAudiobook(item);break;
            }return true;
        });popup.show();
    }
    private void transferEdgeAudiobook(DownloadsRepository.Item item){
        if(!DownloadsRepository.EDGE.equals(item.type)||!item.ready||item.running){
            Toast.makeText(this,"Книга ещё подготавливается",Toast.LENGTH_LONG).show();return;
        }
        new SwipeDialogs.Builder(this).setTitle("Перенести в аудиокниги")
            .setMessage("Выберите папку аудиокниг. В ней будет создана папка с названием книги, " +
                "содержащая только готовые MP3 всех глав. JSON не копируются. " +
                "Исходные файлы Edge TTS сохраняются для подсветки в ReaderActivity.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Выбрать папку",(d,w)->{
                pendingEdgeAudiobook=item;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,EDGE_AUDIOBOOK_REQUEST);
            }).show();
    }
    private void archive(DownloadsRepository.Item item){
        if(item.previewOnly){Toast.makeText(this,"Проверяем состояние загрузки…",Toast.LENGTH_SHORT).show();return;}
        if(!item.ready){Toast.makeText(this,"Можно архивировать только готовую загрузку",Toast.LENGTH_SHORT).show();return;}
        if(!DownloadArchiveStore.archive(getApplicationContext(),item)){
            Toast.makeText(this,"Не удалось сохранить запись архива",Toast.LENGTH_LONG).show();refresh();return;
        }
        archiveRevision++;
        showCachedFilter();
        Toast.makeText(this,"Перемещено в архив · файлы сохранены",Toast.LENGTH_SHORT).show();
    }
    /** ItemTouchHelper moves the entire download card with the finger, while its
     * standard horizontal gesture detector preserves vertical RecyclerView scroll. */
    private void attachArchiveSwipe(){
        ItemTouchHelper.Callback callback=new ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.RIGHT){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            // No drag-and-drop reordering: onMove is required by SimpleCallback.
            @Override public boolean onMove(RecyclerView rv,RecyclerView.ViewHolder from,RecyclerView.ViewHolder to){
                return false;
            }
            @Override public boolean isLongPressDragEnabled(){return false;}
            @Override public float getSwipeThreshold(RecyclerView.ViewHolder holder){return .34f;}
            @Override public int getSwipeDirs(RecyclerView rv,RecyclerView.ViewHolder holder){
                int p=holder.getBindingAdapterPosition();
                return p>=0&&p<rows.size()&&rows.get(p).item!=null&&
                    rows.get(p).item.ready&&!rows.get(p).item.previewOnly?ItemTouchHelper.RIGHT:0;
            }
            @Override public void onSwiped(RecyclerView.ViewHolder holder,int direction){
                int p=holder.getBindingAdapterPosition();
                if(p<0||p>=rows.size()||rows.get(p).item==null){adapter.notifyDataSetChanged();return;}
                DownloadsRepository.Item item=rows.get(p).item;
                archive(item);
            }
            @Override public void onChildDraw(Canvas canvas,RecyclerView rv,RecyclerView.ViewHolder holder,
                float dx,float dy,int actionState,boolean active){
                View v=holder.itemView;
                if(dx>0&&actionState==ItemTouchHelper.ACTION_STATE_SWIPE){
                    paint.setColor(0xffdeede2);
                    canvas.drawRoundRect(v.getLeft(),v.getTop()+dp(2),v.getLeft()+dx,v.getBottom()-dp(10),dp(14),dp(14),paint);
                    android.graphics.drawable.Drawable icon=ContextCompat.getDrawable(DownloadActivity.this,R.drawable.ic_download_archive);
                    if(icon!=null){int x=v.getLeft()+dp(18),y=v.getTop()+(v.getHeight()-dp(26))/2;
                        icon.setBounds(x,y,x+dp(26),y+dp(26));icon.draw(canvas);}
                }
                super.onChildDraw(canvas,rv,holder,dx,dy,actionState,active);
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(list);
    }
    private final class JobsAdapter extends RecyclerView.Adapter<JobsAdapter.Holder>{
        @Override public long getItemId(int position){return rowId(rows.get(position)).hashCode();}
        @Override public int getItemViewType(int p){return rows.get(p).item==null?0:1;}
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            LinearLayout root=new LinearLayout(DownloadActivity.this);root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(root);
        }
        @Override public void onBindViewHolder(Holder h,int pos){
            LinearLayout root=(LinearLayout)h.itemView;root.removeAllViews();DisplayRow row=rows.get(pos);
            if(row.item==null){
                TextView label=text(row.section,23,true);label.setTypeface(Typeface.SERIF,Typeface.BOLD);
                label.setPadding(dp(2),dp(8),0,dp(12));root.addView(label);return;
            }
            DownloadsRepository.Item item=row.item;
            LinearLayout card=new LinearLayout(DownloadActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(bg(PANEL,19));
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(10);root.addView(card,cp);
            LinearLayout top=new LinearLayout(DownloadActivity.this);top.setGravity(Gravity.TOP);card.addView(top);
            FrameLayout coverFrame=new FrameLayout(DownloadActivity.this);coverFrame.setBackground(bg(0xffe7d7c7,12));
            top.addView(coverFrame,new LinearLayout.LayoutParams(dp(item.ready?75:92),dp(item.ready?106:124)));
            TextView placeholder=text(DownloadsRepository.MANGA.equals(item.type)?"▣":DownloadsRepository.RANOBE.equals(item.type)?"▤":"♫",27,true);placeholder.setGravity(Gravity.CENTER);coverFrame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            ImageView cover=new ImageView(DownloadActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);coverFrame.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            CoverImageLoader.show(cover,item.cover,DownloadsRepository.EDGE.equals(item.type));
            LinearLayout detail=new LinearLayout(DownloadActivity.this);detail.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,-2,1);d.leftMargin=dp(12);top.addView(detail,d);
            TextView title=text(item.title,17,true);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setMaxLines(2);detail.addView(title);
            TextView type=text(DownloadsRepository.LINK.equals(item.type)?"↓ По ссылке":DownloadsRepository.EDGE.equals(item.type)?"▥ Edge TTS":DownloadsRepository.MANGA.equals(item.type)?"▣ Манга · MangaLib":DownloadsRepository.RANOBE.equals(item.type)?"▤ Ранобэ · RanobeLIB":"▤ Аудиокнига",12,false);type.setTextColor(ACCENT);
            LinearLayout.LayoutParams tagParams=new LinearLayout.LayoutParams(-2,-2);tagParams.topMargin=dp(5);detail.addView(type,tagParams);
            TextView info=text(item.description,12,false);info.setTextColor(MUTED);
            LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.topMargin=dp(6);detail.addView(info,il);
            TextView status=text(item.status,12,true);status.setTextColor(item.failed?0xffa13d36:item.ready||"Идёт озвучивание".equals(item.status)?GREEN:item.running?0xff276db1:ACCENT);detail.addView(status);
            if(!item.ready){
                ProgressBar progress=new ProgressBar(DownloadActivity.this,null,android.R.attr.progressBarStyleHorizontal);
                if(DownloadsRepository.EDGE.equals(item.type)){
                    progress.setMax(100);progress.setProgress(item.coveragePercent);
                    progress.setIndeterminate(false);
                }else if(DownloadsRepository.MANGA.equals(item.type)&&item.unknownTotal){
                    // Chapter completion is a truthful fallback while page counts are pending.
                    progress.setMax(100);progress.setProgress(item.coveragePercent);
                    progress.setIndeterminate(false);
                }else{
                    progress.setMax(Math.max(1,item.total));progress.setProgress(Math.min(item.done,item.total));
                    if(item.total==0)progress.setIndeterminate(item.running);
                }
                LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(7));pl.topMargin=dp(9);detail.addView(progress,pl);
            }
            // Every row exposes its actions through one compact overflow menu.
            // No duplicate file buttons or bulky download controls.
            ImageView more=icon(R.drawable.ic_more_vertical,"Действия загрузки",43);
            more.setBackground(bg(PANEL,16));
            LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(dp(43),dp(43));
            moreParams.leftMargin=dp(5);top.addView(more,moreParams);
            more.setOnClickListener(v->menu(more,item));card.setOnClickListener(v->open(item));
        }
        TextView control(String caption){TextView b=center(caption,13);b.setBackground(bg(0xfff1e4d7,12));b.setTextColor(INK);return b;}
        @Override public int getItemCount(){return rows.size();}
        final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(EdgeFolderPicker.accept(this,request,result,data))return;
        if(request==EDGE_AUDIOBOOK_REQUEST){
            DownloadsRepository.Item selected=pendingEdgeAudiobook;pendingEdgeAudiobook=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||selected==null)return;
            Uri destination=data.getData();
            try{getContentResolver().takePersistableUriPermission(destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
            catch(Exception e){Toast.makeText(this,"Папка аудиокниг недоступна",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Проверяем готовность MP3 глав…",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                try{
                    BookLoader.Book book=BookLoader.load(this,Uri.parse(selected.bookUri));
                    int moved=EdgeChapterArchive.exportAudiobook(getApplicationContext(),
                        selected.bookUri,selected.title,destination,book.chapters);
                    runOnUiThread(()->Toast.makeText(this,
                        "В аудиокниги добавлено MP3-глав: "+moved,Toast.LENGTH_LONG).show());
                }catch(Exception ex){runOnUiThread(()->Toast.makeText(this,
                    "Перенос не выполнен: "+ex.getMessage(),Toast.LENGTH_LONG).show());}
            },"edge-audiobook-transfer").start();
            return;
        }
        if(request==TRANSFER_REQUEST){
            DownloadsRepository.Item selected=pendingTransfer;pendingTransfer=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||selected==null)return;
            Uri destination=data.getData();
            try{getContentResolver().takePersistableUriPermission(destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
            catch(Exception ex){Toast.makeText(this,"Нет доступа к выбранной папке",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Проверяем и переносим файлы…",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                try{
                    SafeDownloadTransfer.Result report=SafeDownloadTransfer.copy(getApplicationContext(),selected,destination);
                    runOnUiThread(()->Toast.makeText(this,"Скопировано "+report.copied+", уже были: "+report.existing+", старых удалено: "+report.deleted+". "+report.note,Toast.LENGTH_LONG).show());
                }catch(Exception ex){runOnUiThread(()->Toast.makeText(this,
                    "Перенос остановлен: "+ex.getMessage()+". Исходные файлы сохранены.",Toast.LENGTH_LONG).show());}
            },"verified-download-transfer").start();
            return;
        }
        if(request!=12520||result!=RESULT_OK||data==null||data.getData()==null)return;
        String path=getSharedPreferences("reader",0).getString("mangalib_export_pending","");
        getSharedPreferences("reader",0).edit().remove("mangalib_export_pending").apply();
        Uri destination=data.getData();
        new Thread(()->{
            try{
                File source=new File(path);
                String root=MangaLibStorage.root(getApplicationContext()).getCanonicalPath()+File.separator;
                if(!source.getCanonicalPath().startsWith(root)||!source.isFile()||!source.getName().endsWith(".cbz"))
                    throw new java.io.IOException("Недопустимый CBZ");
                try(java.io.InputStream in=new java.io.FileInputStream(source);
                    java.io.OutputStream out=getContentResolver().openOutputStream(destination,"w")){
                    if(out==null)throw new java.io.IOException("Не удалось открыть папку назначения");
                    byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                runOnUiThread(()->Toast.makeText(this,"CBZ сохранён в выбранную папку",Toast.LENGTH_LONG).show());
            }catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Экспорт CBZ: "+e.getMessage(),Toast.LENGTH_LONG).show());}
        },"mangalib-export-cbz").start();
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
