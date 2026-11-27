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
import android.view.MotionEvent;
import android.view.ViewConfiguration;
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
import androidx.recyclerview.widget.DefaultItemAnimator;
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
    // The dashboard uses the same configurable palette as the rest of the app.
    private int BG,PANEL,INK,MUTED,ACCENT,GREEN,ERROR,paletteSignature;
    private void readPalette(){
        BG=UiColorScheme.background(this);PANEL=UiColorScheme.neutral(this);
        INK=UiColorScheme.foreground(this);MUTED=UiColorScheme.muted(this);
        ACCENT=UiColorScheme.accent(this);GREEN=UiColorScheme.success(this);
        ERROR=UiColorScheme.error(this);paletteSignature=UiColorScheme.paletteSignature(this);
    }
    private static final int TRANSFER_REQUEST=12521;
    private static final int BULK_TRANSFER_REQUEST=12559;
    private static final int EDGE_AUDIOBOOK_REQUEST=12522;
    private DownloadsRepository.Item pendingEdgeAudiobook;
    private DownloadsRepository.Item pendingTransfer;
    private String pendingTransferId="",pendingEdgeAudiobookId="";
    private static final String FILTER_KEY="downloads_filter_v2";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean visible,bound,refreshInFlight;
    private final ExecutorService selectionCheck=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"DownloadSelectedVerify");t.setDaemon(true);return t;});
    private String revealedSwipeId="";
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
    private static volatile boolean coversIndexedThisProcess;
    private long mangaCoversAt;
    private int archiveRevision;
    private int filterGeneration;
    private long appliedEventRevision=0;
    private final java.util.Set<String> pendingIds=new java.util.HashSet<>();
    private final DownloadChangeBus.Listener downloadListener=id->{
        if(!DownloadChangeBus.hasLiveProgress(id))queueUpdate(id);
    };
    private final DownloadChangeBus.ProgressListener progressListener=event->{
        if(!visible||event.kind==DownloadChangeBus.Kind.COMMAND_STATE)return;
        DownloadsRepository.Item changed=null;
        for(DownloadsRepository.Item item:lastAll)if(event.id.equals(item.id)){changed=item;break;}
        if(changed==null)return;
        DownloadProgressTracker.Summary progress=DownloadProgressTracker.summary(event.id);
        if(progress!=null&&progress.hasPlan&&progress.percent>=0){
            changed.coveragePercent=progress.percent;
            if(changed.total>0)changed.done=Math.min(changed.total,
                (int)((long)changed.total*progress.percent/100L));
        }
        if(event.kind==DownloadChangeBus.Kind.CHAPTER_PROGRESS){
            changed.running=true;changed.status="Скачивается";
        }
        for(int i=0;i<rows.size();i++)if(rows.get(i).item!=null&&event.id.equals(rows.get(i).item.id)){
            adapter.notifyItemChanged(i,"fast-progress");break;
        }
        // Only when the last chapter is committed reconcile durable flags and the green badge.
        if(event.committed&&progress!=null&&progress.hasPlan&&progress.chapters>0&&progress.finished>=progress.chapters){
            queueUpdate(event.id);
        }
    };
    private final Runnable pendingRunner=this::flushUpdates;
    /** A downloader event invalidates one card; pending events are merged over 350 ms. */
    private void queueUpdate(String id){
        if(id==null||id.isEmpty())return;
        if(Looper.myLooper()!=Looper.getMainLooper()){
            main.post(()->queueUpdate(id));return;
        }
        pendingIds.add(id);
        if(visible){main.removeCallbacks(pendingRunner);main.postDelayed(pendingRunner,350);}
    }
    private void flushUpdates(){
        if(!visible||scan.isShutdown())return;
        if(refreshInFlight){main.postDelayed(pendingRunner,250);return;}
        if(pendingIds.isEmpty())return;
        final java.util.HashSet<String> ids=new java.util.HashSet<>(pendingIds);
        final long batchRevision=DownloadChangeBus.revision();
        pendingIds.clear();
        refreshInFlight=true;
        scan.execute(()->{
            java.util.HashMap<String,DownloadsRepository.Item> updates=new java.util.HashMap<>();
            try{
                for(String id:ids){
                    DownloadsRepository.Item found=DownloadsRepository.loadOne(getApplicationContext(),id);
                    updates.put(id,found);
                }
            }catch(Exception error){
                android.util.Log.w("DownloadActivity","Selected updates failed",error);
                main.post(()->{refreshInFlight=false;pendingIds.addAll(ids);});return;
            }
            main.post(()->{
                refreshInFlight=false;
                if(!visible){pendingIds.addAll(ids);return;}
                ArrayList<DownloadsRepository.Item> updated=new ArrayList<>(lastAll);
                for(String id:ids){
                    DownloadsRepository.Item item=updates.get(id);
                    for(int index=updated.size()-1;index>=0;index--)
                        if(id.equals(updated.get(index).id))updated.remove(index);
                    if(item!=null){
                        if(item.title!=null)item.title=BookTitleStore.downloadTitle(getApplicationContext(),item);
                        updated.add(item);
                    }
                }
                lastAll=updated;sharedSnapshot=new ArrayList<>(updated);
                showCachedFilter();updateSummary();
                appliedEventRevision=Math.max(appliedEventRevision,batchRevision);
                final ArrayList<DownloadsRepository.Item> persisted=new ArrayList<>(updated);
                scan.execute(()->DownloadListSnapshot.save(getApplicationContext(),persisted));
                if(!pendingIds.isEmpty())main.postDelayed(pendingRunner,100);
            });
        });
    }
    private void updateSummary(){
        int active=0,done=0;
        java.util.Set<String> hidden=DownloadArchiveStore.hiddenIds(this);
        for(DownloadsRepository.Item item:lastAll){
            if(hidden.contains(item.id))continue;
            if(item.ready)done++;else active++;
        }
        summary.setText("Онлайн-загрузки: "+(active+done)+"  ·  В процессе: "+active+"  ·  Готово: "+done);
    }
    private void refreshCurrent(String id){
        queueUpdate(id);
        if(visible)main.postDelayed(pendingRunner,100);
    }
    private void recoverActiveCards(){
        for(DownloadsRepository.Item item:lastAll)if(item.running)queueUpdate(item.id);
    }
    private final ServiceConnection edgeConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){edge=((EdgeStreamingService.LocalBinder)b).getService();
            if(edge!=null&&!edge.bookUri().isEmpty())refreshCurrent("edge:"+edge.bookUri());}
        @Override public void onServiceDisconnected(ComponentName n){edge=null;recoverActiveCards();}
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
        readPalette();
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
        LinearLayout topBar=new LinearLayout(this);topBar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topBar,new LinearLayout.LayoutParams(-1,dp(52)));
        // Keep every toolbar control within the available width: no horizontal scrolling.
        ImageView back=icon(R.drawable.ic_menu_lines,"Главное меню",44);
        topBar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        back.setOnClickListener(v->UniversalDrawer.show(this));
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
        topBar.addView(toolbar,new LinearLayout.LayoutParams(0,dp(52),1));
        final String[] actionNames={"Поиск аудиокниг","Поиск манги","Поиск ранобэ",
            "Импорт по ссылке","Передача по Wi-Fi"};
        final int[] actionImages={R.drawable.ic_search_audiobook,R.drawable.ic_manga_search,
            R.drawable.ic_ranobelib_search,R.drawable.ic_import_link,R.drawable.ic_wifi_transfer};
        final Runnable[] actions={
            ()->DownloadSourcePicker.show(this,DownloadSourcePicker.AUDIO),
            ()->DownloadSourcePicker.show(this,DownloadSourcePicker.MANGA),
            ()->DownloadSourcePicker.show(this,DownloadSourcePicker.RANOBE),
            ()->startActivity(new Intent(this,LinkDownloadActivity.class)),
            ()->startActivity(new Intent(this,WebTransferActivity.class))
        };
        ArrayList<ImageView> quick=new ArrayList<>();
        for(int i=0;i<actions.length;i++)quick.add(toolbarAction(toolbar,actionImages[i],actionNames[i],actions[i]));
        ImageView more=toolbarAction(toolbar,R.drawable.ic_more_vertical,"Дополнительные действия",()->{});
        more.setVisibility(View.GONE);
        more.setOnClickListener(v->{
            PopupMenu menu=new PalettePopupMenu(this,more);
            for(int i=0;i<actions.length;i++)if(quick.get(i).getVisibility()!=View.VISIBLE){
                final int index=i;
                menu.getMenu().add(actionNames[i]).setOnMenuItemClickListener(item->{actions[index].run();return true;});
            }
            menu.show();
        });
        // At 320dp or with larger display scaling, secondary actions move to this overflow.
        // Width changes (rotation, split screen) restore available buttons automatically.
        toolbar.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->{
            int available=right-left,slot=dp(49);
            int visibleCount=available>=quick.size()*slot?quick.size():
                Math.max(0,Math.min(quick.size(),available/slot-1));
            for(int i=0;i<quick.size();i++)quick.get(i).setVisibility(i<visibleCount?View.VISIBLE:View.GONE);
            more.setVisibility(visibleCount<quick.size()?View.VISIBLE:View.GONE);
        });
        LinearLayout headingRow=new LinearLayout(this);
        headingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,dp(52));hl.topMargin=dp(11);
        root.addView(headingRow,hl);
        TextView heading=text("Загрузки",25,true);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);
        headingRow.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView archive=icon(R.drawable.ic_download_archive,"Архив загрузок",44);
        headingRow.addView(archive,new LinearLayout.LayoutParams(dp(44),dp(44)));
        archive.setOnClickListener(v->startActivity(new Intent(this,DownloadArchiveActivity.class)));
        String[] currentFilters={"Все загрузки","В процессе","Аудиокниги","Edge TTS","По ссылке","Манга","Ранобэ","Wi-Fi"};
        TextView filterSelect=center(currentFilters[Math.max(0,Math.min(filter,7))]+"  ▾",16);
        filterSelect.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
        filterSelect.setBackground(bg(ReaderButtonColors.neutral(this,PANEL),15));
        filterSelect.setTextColor(ReaderButtonColors.contrastingText(ReaderButtonColors.neutral(this,PANEL)));
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(49));fl.topMargin=dp(14);root.addView(filterSelect,fl);
        filterSelect.setOnClickListener(v->{
            PopupMenu menu=new PalettePopupMenu(this,filterSelect);
            String[] names={"Все загрузки","В процессе","Аудиокниги","Edge TTS","По ссылке","Манга","Ранобэ","Wi-Fi"};
            for(int i=0;i<names.length;i++){final int ix=i;menu.getMenu().add(names[i]).setOnMenuItemClickListener(item->{
                filter=ix;filterSelect.setText(names[ix]+"  ▾");getSharedPreferences("reader",0).edit().putInt(FILTER_KEY,ix).apply();showCachedFilter();return true;});}
            // Full disk reconciliation is a secondary action, not another toolbar button.
            menu.getMenu().add(1,103,97,"Проверить загрузки")
                .setOnMenuItemClickListener(item->{confirmFullScan();return true;});
            menu.getMenu().add(1,102,98,"Перенести всё из внутреннего хранилища")
                .setOnMenuItemClickListener(item->{confirmBulkTransfer();return true;});
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
    private void confirmFullScan(){
        new SwipeDialogs.Builder(this).setTitle("Проверить загрузки?")
            .setMessage("Полностью сверить задания и файлы во всех хранилищах. Может занять время.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Проверить",(d,w)->refresh()).show();
    }
    private ImageView toolbarAction(LinearLayout toolbar,int drawable,String hint,Runnable action){
        ImageView button=icon(drawable,hint,44);
        int fill=ReaderButtonColors.neutral(this,PANEL);
        button.setBackground(bg(fill,16));button.setColorFilter(ReaderButtonColors.contrastingText(fill));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(44),dp(44));lp.leftMargin=dp(5);
        toolbar.addView(button,lp);button.setOnClickListener(v->action.run());return button;
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

    @Override protected void onResume(){super.onResume();
        if(paletteSignature!=UiColorScheme.paletteSignature(this)){recreate();return;}
        visible=true;
        ArrayList<DownloadsRepository.Item> previous=sharedSnapshot;
        if(previous==null||previous.isEmpty())previous=DownloadListSnapshot.load(this);
        if(previous!=null&&!previous.isEmpty()){
            lastAll=new ArrayList<>(previous);showCachedFilter();updateSummary();
        }
        DownloadChangeBus.add(downloadListener);DownloadChangeBus.addProgress(progressListener);
        ArrayList<String> missing=DownloadChangeBus.since(appliedEventRevision);
        if(missing==null){refresh();}
        else{for(String changed:missing)queueUpdate(changed);}
        recoverActiveCards();
        if(lastAll.isEmpty())refresh();
        else if(!pendingIds.isEmpty())main.post(pendingRunner);
        // Cover metadata discovery runs only on a missing-cover cache, never on a timer.
        if(!coversIndexedThisProcess){
            coversIndexedThisProcess=true;
            new Thread(()->AudiobookCoverStore.backfill(getApplicationContext(),()->{
                if(visible)for(DownloadsRepository.Item item:lastAll)
                    if(DownloadsRepository.AUDIO.equals(item.type))refreshCurrent(item.id);
            }),"DownloadCoverIndex").start();
        }
    }
    @Override protected void onPause(){visible=false;DownloadChangeBus.remove(downloadListener);DownloadChangeBus.removeProgress(progressListener);
        main.removeCallbacks(pendingRunner);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(edgeConnection);
        DownloadChangeBus.remove(downloadListener);DownloadChangeBus.removeProgress(progressListener);main.removeCallbacksAndMessages(null);
        scan.shutdownNow();selectionCheck.shutdownNow();super.onDestroy();}
    /** Never traverse book directories, parse JSON or stat MP3 on the UI thread.
     * Only one explicit full reconciliation can run; ordinary progress uses
     * per-item events rather than periodic scanning.
     */
    private void refresh(){
        if(!visible||adapter==null||refreshInFlight||scan.isShutdown())return;
        long now=android.os.SystemClock.elapsedRealtime();
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
                    updateSummary();
                    if(!pendingIds.isEmpty())main.postDelayed(pendingRunner,100);
                });
            }catch(Exception error){
                android.util.Log.w("DownloadActivity","Index scan failed",error);
                main.post(()->{refreshInFlight=false;if(visible&&rows.isEmpty())summary.setText("Не удалось загрузить список");});
            }
        });
    }
    /** Filter changes only touch the already indexed snapshot, never wait on storage. */
    /** Diff large lists in the scanner thread, publish only the current generation. */
    private void showCachedFilter(){
        if(adapter==null||scan.isShutdown())return;
        final int epoch=++filterGeneration;
        final int wantedFilter=filter;
        final ArrayList<DownloadsRepository.Item> snapshot=new ArrayList<>(lastAll);
        final ArrayList<DisplayRow> previous=new ArrayList<>(rows);
        scan.execute(()->{
            final java.util.Set<String> hidden=DownloadArchiveStore.hiddenIds(getApplicationContext());
            final ArrayList<DisplayRow> next=makeRows(snapshot,wantedFilter,hidden);
            DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
                @Override public int getOldListSize(){return previous.size();}
                @Override public int getNewListSize(){return next.size();}
                @Override public boolean areItemsTheSame(int a,int b){
                    return rowId(previous.get(a)).equals(rowId(next.get(b)));
                }
                @Override public boolean areContentsTheSame(int a,int b){
                    return rowState(previous.get(a)).equals(rowState(next.get(b)));
                }
            },false);
            main.post(()->{
                if(epoch!=filterGeneration||adapter==null||filter!=wantedFilter)return;
                rows.clear();rows.addAll(next);diff.dispatchUpdatesTo(adapter);
            });
        });
    }
    private static String rowId(DisplayRow row){
        return row.item==null?"section:"+row.section:row.item.id;
    }
    private static String rowState(DisplayRow row){
        if(row.item==null)return row.section;
        DownloadsRepository.Item i=row.item;
        return i.title+"\n"+i.description+"\n"+i.status+"\n"+i.cover+"\n"+
            i.ready+":"+i.confirmedReady+":"+i.running+":"+i.failed+":"+i.paused+":"+i.done+":"+i.total+":"+i.coveragePercent;
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
            if(filter==6&&!DownloadsRepository.RANOBE.equals(item.type)&&!DownloadsRepository.RANOBES.equals(item.type))continue;
            if(filter==7&&!DownloadsRepository.WIFI.equals(item.type))continue;
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
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->send(checked, command));return;}
        Intent i=new Intent(this,DownloadWorker.class).setAction(command).putExtra("source",item.source);
        try{if(Build.VERSION.SDK_INT>=26&&DownloadWorker.START.equals(command))startForegroundService(i);else startService(i);}
        catch(Exception ex){Toast.makeText(this,"Ошибка: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        main.postDelayed(()->refreshCurrent(item.id),250);
    }
    private void open(DownloadsRepository.Item item){
        if(DownloadsRepository.WIFI.equals(item.type)){
            wifiDetails(item);return;
        }
        // A saved ID opens the unified manager immediately; disk checks run there
        // asynchronously. A completed audiobook must NOT auto-open the player.
        startActivity(new Intent(this,UnifiedDownloadManagerActivity.class)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_ID,item.id)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_TITLE,item.title)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_COVER,item.cover)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_TYPE,item.type));
    }
    private void wifiDetails(DownloadsRepository.Item item){
        WebTransferDownloadStore.Entry e=WebTransferDownloadStore.one(this,item.id);
        if(e==null){Toast.makeText(this,"Запись Wi-Fi не найдена",Toast.LENGTH_SHORT).show();return;}
        String details="Источник: Wi-Fi\nФайл: "+e.name+"\nСостояние: "+item.status+
            "\nПолучено: "+item.downloadedBytes+" из "+item.totalBytes+" байт";
        if(!e.message.isEmpty())details+="\n"+e.message;
        new SwipeDialogs.Builder(this).setTitle("Передача по Wi-Fi")
            .setMessage(details).setPositiveButton("Закрыть",null).show();
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
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->fullCommand(checked, pause));return;}
        if(item.fullJob==null)return;
        Intent intent=new Intent(this,EdgeFullBookService.class)
            .setAction(pause?EdgeFullBookService.PAUSE:EdgeFullBookService.RESUME)
            .putExtra(EdgeFullBookService.EXTRA_URI,item.bookUri);
        try{
            if(!pause&&Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            main.postDelayed(()->refreshCurrent(item.id),350);
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
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->eraseAudio(checked, files));return;}
        if(item.job==null)return;
        send(item,DownloadWorker.CANCEL);
        new Thread(()->{BookImportManager.remove(this,item.job,files);runOnUiThread(()->refreshCurrent(item.id));},"remove-audiobook-task").start();
    }
    private void openFolder(DownloadsRepository.Item item){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->openFolder(checked));return;}
        if(DownloadsRepository.EDGE.equals(item.type)){
            EdgeFolderPicker.browse(this);return;
        }
        if(item.folder==null||!item.folder.isDirectory()){Toast.makeText(this,"Папка аудио пока не создана",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,EdgeFileExplorerActivity.class).putExtra("book_folder",item.folder.getAbsolutePath()));
    }
    private void eraseEdge(DownloadsRepository.Item item,boolean files){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->eraseEdge(checked, files));return;}
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
        refreshCurrent(item.id);
    }
    private void confirmDelete(DownloadsRepository.Item item,boolean files){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->confirmDelete(checked, files));return;}
        String what=files?"Удалить загрузку и файлы?":"Убрать задание из списка?";
        new SwipeDialogs.Builder(this).setTitle(what)
            .setMessage(files?"Аудиофайлы будут удалены. Для Edge исходная текстовая книга останется в библиотеке.":"Скачанные файлы сохранятся на устройстве.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(d,w)->{
                if(DownloadsRepository.EDGE.equals(item.type))eraseEdge(item,files);else eraseAudio(item,files);
            }).show();
    }
    private void linkCommand(DownloadsRepository.Item item,String command){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->linkCommand(checked, command));return;}
        Intent i=new Intent(this,DirectLinkDownloadService.class).setAction(command).putExtra(DirectLinkDownloadService.EXTRA_ID,item.link.id);
        try{if(Build.VERSION.SDK_INT>=26&&DirectLinkDownloadService.START.equals(command))startForegroundService(i);else startService(i);}
        catch(Exception ex){Toast.makeText(this,"Ошибка: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        main.postDelayed(()->refreshCurrent(item.id),300);
    }
    private void deleteLink(DownloadsRepository.Item item,boolean files){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->deleteLink(checked, files));return;}
        if(item.running){Toast.makeText(this,"Сначала поставьте загрузку на паузу",Toast.LENGTH_LONG).show();return;}
        if(files){for(LinkDownloadStore.Entry e:item.link.files){
            if(!e.savedUri.isEmpty())try{androidx.documentfile.provider.DocumentFile doc=androidx.documentfile.provider.DocumentFile.fromSingleUri(this,android.net.Uri.parse(e.savedUri));if(doc!=null)doc.delete();}catch(Exception ignored){}
        }}
        LinkDownloadStore.remove(this,item.link.id);refreshCurrent(item.id);
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
                &&job.file!=null&&MangaLibStorage.exists(this,job.file))complete.add(job);
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
                String location=complete.get(index).file;
                String name=MangaLibStorage.isUri(location)?"Глава "+complete.get(index).number+".cbz":new File(location).getName();
                getSharedPreferences("reader",0).edit()
                    .putString("mangalib_export_pending",location).apply();
                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                save.addCategory(Intent.CATEGORY_OPENABLE);save.setType("application/zip");
                save.putExtra(Intent.EXTRA_TITLE,name);
                startActivityForResult(save,12520);
            }).setNegativeButton("Отмена",null).show();
    }
    private static int compareMangaNumber(String a,String b){
        try{return Double.compare(Double.parseDouble(a),Double.parseDouble(b));}
        catch(Exception ignored){return a.compareToIgnoreCase(b);}
    }
    private void confirmBulkTransfer(){
        new SwipeDialogs.Builder(this).setTitle("Массовый перенос книг")
            .setMessage("Переместить все неактивные загрузки MangaLib и RanobeLIB, " +
                "а также готовые аудиокниги и текстовые файлы, принадлежащие приложению. " +
                "Каждый файл сверяется по SHA-256. Активные задания будут пропущены. " +
                "Рабочие кэши Edge TTS и импортированные пользовательские оригиналы останутся без изменений.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Выбрать папку",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent,BULK_TRANSFER_REQUEST);
            }).show();
    }
    private void transferDialog(DownloadsRepository.Item item){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->transferDialog(checked));return;}
        if(item.running){Toast.makeText(this,"Сначала приостановите загрузку",Toast.LENGTH_LONG).show();return;}
        new SwipeDialogs.Builder(this).setTitle("Перенести файлы")
            .setMessage("Выберите папку назначения. Каждый файл проверяется по SHA-256. " +
                "Для RanobeLIB, MangaLib, аудиокниг и ссылок обновляются пути, " +
                "после чего принадлежащие приложению оригиналы удаляются. " +
                "Edge TTS пока сохраняет рабочую озвучку для подсветки текста. " +
                "Чужие файлы не удаляются; файлы с другими контрольными суммами не перезаписываются.")
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
            MangaLibStorage.Job job=jobs.get(i);
            String filename=MangaLibStorage.isUri(job.file)?"Глава "+job.number+".cbz":new File(job.file).getName();
            files[i]="Том "+job.volume+" / "+filename+" · "+(job.bytes/1024)+" КБ";
        }
        new SwipeDialogs.Builder(this).setTitle("Открыть папку · "+folder.getName())
            .setItems(files,(d,i)->exportMangaChapter(slug))
            .setNeutralButton("Путь к папке",(d,w)->new SwipeDialogs.Builder(this)
                .setTitle("Папка файлов")
                .setMessage(MangaLibStorage.externalDir(this,slug).isEmpty()
                    ? folder.getAbsolutePath()+"\n\nИспользуйте экспорт CBZ или «Перенести файлы».":
                    "Сохранено во внешней папке: "+MangaLibStorage.externalDir(this,slug))
                .setPositiveButton("Закрыть",null).show())
            .setNegativeButton("Закрыть",null).show();
    }
    private void chooseMangaFilesToDelete(DownloadsRepository.Item item){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->chooseMangaFilesToDelete(checked));return;}
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
                                MangaLibStorage.Job job=jobs.get(i);
                                boolean removedFile=false;
                                try{
                                    if(MangaLibStorage.isUri(job.file)){
                                        androidx.documentfile.provider.DocumentFile doc=androidx.documentfile.provider.DocumentFile.fromSingleUri(
                                            getApplicationContext(),Uri.parse(job.file));
                                        removedFile=doc!=null&&doc.isFile()&&doc.delete();
                                    }else{
                                        File file=new File(job.file);removedFile=file.isFile()&&file.delete();
                                    }
                                }catch(Exception ignored){}
                                if(!removedFile)continue;
                                job.file="";job.done=0;job.bytes=0;job.status="cancelled";MangaLibStorage.put(getApplicationContext(),job);removed++;
                            }
                            ComicCovers.invalidate(getApplicationContext(),MangaLibStorage.comicUri(item.source));
                            File previousCover=MangaDownloadCover.file(getApplicationContext(),item.source);
                            CoverImageLoader.invalidateLocal(previousCover.toURI().toString());
                            MangaDownloadCover.invalidate(getApplicationContext(),item.source);
                            java.util.ArrayList<MangaLibStorage.Job> remaining=MangaLibStorage.seriesJobs(getApplicationContext(),item.source);
                            if(!remaining.isEmpty())MangaDownloadCover.ensure(getApplicationContext(),remaining.get(0));
                            final int deleted=removed;
                            main.post(()->{Toast.makeText(this,"Удалено CBZ: "+deleted,Toast.LENGTH_SHORT).show();refreshCurrent(item.id);});
                        });
                    }).show();
            }).show();
    }
    private void renameDownload(DownloadsRepository.Item item){
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->renameDownload(checked));return;}
        BookRenameDialog.show(this,item.title,newName->{
            if(!BookTitleStore.renameDownload(this,item.id,item.type,item.bookUri,item.source,newName))return false;
            item.title=newName;
            for(DownloadsRepository.Item entry:lastAll)if(item.id.equals(entry.id))entry.title=newName;
            if(adapter!=null)adapter.notifyDataSetChanged();
            // A disk scan will restore stable job identities and reapply aliases.
            refreshCurrent(item.id);return true;
        });
    }
    private void resolvePreview(DownloadsRepository.Item item,java.util.function.Consumer<DownloadsRepository.Item> callback){
        if(item==null)return;
        if(!item.previewOnly){callback.accept(item);return;}
        final String selectedId=item.id;
        if(selectionCheck.isShutdown())return;
        selectionCheck.execute(()->{
            DownloadsRepository.Item verified=null;String error="";
            try{
                verified=DownloadsRepository.loadOne(getApplicationContext(),selectedId);
                if(verified==null)error="Задание не найдено в хранилище";
            }catch(Exception ex){error=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();}
            final DownloadsRepository.Item result=verified;
            final String failure=error;
            main.post(()->{
                if(!visible||isFinishing()||isDestroyed())return;
                if(result==null){
                    new SwipeDialogs.Builder(this).setTitle("Загрузка недоступна")
                        .setMessage(failure+". Повторите проверку или откройте книгу напрямую.")
                        .setNegativeButton("Закрыть",null)
                        .setPositiveButton("Повторить",(d,w)->resolvePreview(item,callback)).show();
                    diskScannedAt=0;return;
                }
                for(int i=0;i<lastAll.size();i++)if(selectedId.equals(lastAll.get(i).id))lastAll.set(i,result);
                sharedSnapshot=new ArrayList<>(lastAll);
                showCachedFilter();callback.accept(result);
            });
        });
    }
    /** Do not call SAF or disk metadata checks while constructing an overflow menu. */
    private void openNovelIfAvailable(DownloadsRepository.Item item){
        if(selectionCheck.isShutdown())return;
        selectionCheck.execute(()->{
            boolean available=false;String uri=item.bookUri;
            try{
                if(DownloadsRepository.RANOBES.equals(item.type)){
                    available=RanobesStorage.hasBook(getApplicationContext(),item.source);
                    uri=RanobesStorage.bookUri(getApplicationContext(),item.source);
                }else if(DownloadsRepository.RANOBE.equals(item.type)){
                    available=RanobeLibStorage.stored(getApplicationContext(),
                        item.bookUri.startsWith("file:")?
                        new File(Uri.parse(item.bookUri).getPath()).getAbsolutePath():item.bookUri);
                }
            }catch(Exception ignored){}
            final String bookUri=uri;final boolean found=available;
            main.post(()->{
                if(isFinishing()||isDestroyed())return;
                if(!found)Toast.makeText(this,"Готовый HTML не найден",Toast.LENGTH_SHORT).show();
                else startActivity(new Intent(this,ReaderActivity.class).putExtra("book_uri",bookUri));
            });
        });
    }
    private void menu(View anchor,DownloadsRepository.Item item){
        if(DownloadsRepository.WIFI.equals(item.type)){
            PopupMenu popup=new PalettePopupMenu(this,anchor);
            popup.getMenu().add("Подробнее").setOnMenuItemClickListener(m->{wifiDetails(item);return true;});
            if(item.running){
                if("Импорт в библиотеку".equals(item.status)){
                    popup.getMenu().add("Сохранение файла…").setEnabled(false);
                }else popup.getMenu().add("Отменить передачу").setOnMenuItemClickListener(m->{
                    if(!WebTransferService.cancelTransfer(this,item.id))
                        Toast.makeText(this,"Передача уже завершена или сохраняется",Toast.LENGTH_SHORT).show();
                    refreshCurrent(item.id);return true;});
            }else{
                popup.getMenu().add("Удалить из истории").setOnMenuItemClickListener(m->{
                    new SwipeDialogs.Builder(this).setTitle("Удалить запись о передаче?")
                        .setMessage("Файл в библиотеке останется. Будет удалена только строка истории Wi-Fi.")
                        .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                            WebTransferDownloadStore.remove(this,item.id);refreshCurrent(item.id);
                        }).show();return true;});
            }
            popup.getMenu().add("Передача по Wi-Fi").setOnMenuItemClickListener(m->{
                startActivity(new Intent(this,WebTransferActivity.class));return true;});
            popup.show();return;
        }

        if(DownloadsRepository.RANOBES.equals(item.type)){
            PopupMenu popup=new PalettePopupMenu(this,anchor);
            popup.getMenu().add("Переименовать").setOnMenuItemClickListener(m->{renameDownload(item);return true;});
            if(item.ready)popup.getMenu().add("В архив").setOnMenuItemClickListener(m->{archive(item);return true;});
            popup.getMenu().add("Мета, файлы и обложка").setOnMenuItemClickListener(m->{open(item);return true;});
            popup.getMenu().add("Тома и главы").setOnMenuItemClickListener(m->{startActivity(new Intent(this,RanobesBookActivity.class).putExtra("ranobes_id",item.source));return true;});
            popup.getMenu().add("Читать HTML").setOnMenuItemClickListener(m->{openNovelIfAvailable(item);return true;});
            if(!item.ready){
                popup.getMenu().add(item.running?"Пауза":"Продолжить / повторить ошибки").setOnMenuItemClickListener(m->{
                    if(item.running)RanobesQueue.pause(this,item.source);else RanobesQueue.resume(this,item.source);
                    refreshCurrent(item.id);return true;
                });
                popup.getMenu().add("Отменить очередь").setOnMenuItemClickListener(m->{RanobesQueue.cancel(this,item.source);refreshCurrent(item.id);return true;});
            }
            popup.getMenu().add("Выбрать папку ранобэ").setOnMenuItemClickListener(m->{DownloadFolderPicker.choose(this,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.RANOBE);return true;});
            popup.show();return;
        }
        if(DownloadsRepository.RANOBE.equals(item.type)){
            PopupMenu popup=new PalettePopupMenu(this,anchor);
            popup.getMenu().add("Переименовать").setOnMenuItemClickListener(m->{renameDownload(item);return true;});
            if(item.ready)popup.getMenu().add("В архив").setOnMenuItemClickListener(m->{archive(item);return true;});
            popup.getMenu().add("Тома и главы").setOnMenuItemClickListener(m->{open(item);return true;});
            popup.getMenu().add("Читать HTML").setOnMenuItemClickListener(m->{openNovelIfAvailable(item);return true;});
            popup.getMenu().add(item.running?"Пауза":"Продолжить / повторить ошибки").setOnMenuItemClickListener(m->{
                if(item.running)RanobeLibQueue.pause(this,item.source);
                else RanobeLibQueue.resume(this,item.source);
                refreshCurrent(item.id);return true;});
            if(!item.ready)popup.getMenu().add("Отменить очередь").setOnMenuItemClickListener(m->{
                RanobeLibQueue.cancel(this,item.source);refreshCurrent(item.id);return true;});
            popup.getMenu().add("Перенести файлы в папку")
                .setOnMenuItemClickListener(m->{transferDialog(item);return true;});
            popup.getMenu().add("Удалить скачанную книгу и задания").setOnMenuItemClickListener(m->{
                new SwipeDialogs.Builder(this).setTitle("Удалить книгу RanobeLIB?")
                    .setMessage("HTML, главы и обложка этой книги будут удалены. Остальные произведения не затрагиваются.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->{
                        RanobeLibQueue.cancel(this,item.source);
                        scan.execute(()->{RanobeLibStorage.deleteSeries(getApplicationContext(),item.source);main.post(()->{refreshCurrent(item.id);});});
                    }).show();return true;});
            popup.show();return;
        }
        if(DownloadsRepository.MANGA.equals(item.type)){
            PopupMenu p=new PalettePopupMenu(this,anchor);
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
                    refreshCurrent(item.id);return true;
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
                refreshCurrent(item.id);return true;
            });
            p.getMenu().add(0,32,6,"Открыть папку").setOnMenuItemClickListener(m->{showMangaFolder(item.source);return true;});
            p.getMenu().add(0,33,7,"Удалить скачанные файлы").setOnMenuItemClickListener(m->{chooseMangaFilesToDelete(item);return true;});
            p.getMenu().add(0,31,5,"Перенести файлы").setOnMenuItemClickListener(m->{transferDialog(item);return true;});
            p.show();return;
        }
        if(DownloadsRepository.LINK.equals(item.type)){
            PopupMenu p=new PalettePopupMenu(this,anchor);
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
                case 2:resolvePreview(item,verified->{if(verified.link!=null)LinkDownloadStore.addToLibrary(this,verified.link);
                    Toast.makeText(this,"Добавлено в библиотеку",Toast.LENGTH_SHORT).show();});break;
                case 3:linkCommand(item,DirectLinkDownloadService.START);break;
                case 31:transferDialog(item);break;
                case 41:EdgeFolderPicker.show(this);break;
                case 4:case 5:final boolean erase=m.getItemId()==5;new SwipeDialogs.Builder(this).setTitle(erase?"Удалить загруженные MP3?":"Убрать задание из списка?")
                    .setMessage(erase?"Будут удалены файлы этого задания в выбранной папке.":"Файлы останутся на устройстве.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->deleteLink(item,erase)).show();break;
            }return true;});p.show();return;
        }
        PopupMenu popup=new PalettePopupMenu(this,anchor);
        popup.getMenu().add(0,77,0,"Переименовать");
        if(item.ready)popup.getMenu().add(0,81,0,"В архив");
        if(DownloadsRepository.EDGE.equals(item.type)){
            popup.getMenu().add(0,1,0,"Главы и загрузка");
            popup.getMenu().add(0,9,1,"Открыть Edge-плеер");
            popup.getMenu().add(0,42,2,"Перенести готовые аудиоглавы едиными файлами в аудиотеку");
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
        if(item!=null&&item.previewOnly){resolvePreview(item,checked->transferEdgeAudiobook(checked));return;}
        if(!DownloadsRepository.EDGE.equals(item.type))return;
        // Query the already committed SAF indices off the main thread, only for this action.
        new Thread(()->{
            final int count=EdgeChapterArchive.exportableCount(getApplicationContext(),item.bookUri,item.title);
            ArrayList<String> toc=EdgeTocSnapshot.load(getApplicationContext(),item.bookUri);
            final int total=toc==null||toc.isEmpty()?count:toc.size();
            runOnUiThread(()->{
                if(isFinishing())return;
                if(count==0){Toast.makeText(this,"Пока нет полностью сохранённых глав",Toast.LENGTH_LONG).show();return;}
                new SwipeDialogs.Builder(this).setTitle("Экспорт глав в аудиотеку")
                    .setMessage("Готово "+count+" из "+total+" глав. " +
                        "Каждая готовая глава станет отдельным MP3 внутри папки книги. " +
                        "Одна общая обложка cover; WordBoundary остаётся в Edge TTS.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton(count<total?"Экспортировать готовые главы":"Выбрать папку",(d,w)->{
                        pendingEdgeAudiobook=item;
                        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                        startActivityForResult(intent,EDGE_AUDIOBOOK_REQUEST);
                    }).show();
            });
        },"edge-export-count").start();
    }
    private void archive(DownloadsRepository.Item item){
        if(DownloadsRepository.WIFI.equals(item.type)){
            Toast.makeText(this,"Запись Wi-Fi уже сохраняется в истории загрузок",Toast.LENGTH_SHORT).show();return;
        }
        if(item.previewOnly){resolvePreview(item,this::archive);return;}
        if(!item.ready&&!item.paused){
            new SwipeDialogs.Builder(this).setTitle("Архивировать незавершённую загрузку?")
                .setMessage("Сначала приостановим задачу. Файлы и кэш останутся на месте, " +
                    "а после восстановления из архива загрузку можно продолжить.")
                .setNegativeButton("Отмена",(d,w)->{revealedSwipeId="";adapter.notifyDataSetChanged();})
                .setPositiveButton("Приостановить и в архив",(d,w)->{
                    if(pauseForArchive(item)){item.running=false;item.paused=true;archive(item);}
                }).show();
            return;
        }
        if(!DownloadArchiveStore.archive(getApplicationContext(),item)){
            Toast.makeText(this,"Не удалось сохранить запись архива",Toast.LENGTH_LONG).show();refreshCurrent(item.id);return;
        }
        archiveRevision++;
        revealedSwipeId="";
        showCachedFilter();
        Toast.makeText(this,"Перемещено в архив · файлы сохранены",Toast.LENGTH_SHORT).show();
    }
    private boolean pauseForArchive(DownloadsRepository.Item item){
        try{
            if(DownloadsRepository.RANOBES.equals(item.type))RanobesQueue.pause(this,item.source);
            else if(DownloadsRepository.RANOBE.equals(item.type))RanobeLibQueue.pause(this,item.source);
            else if(DownloadsRepository.MANGA.equals(item.type))MangaLibQueue.pause(this,item.source);
            else if(DownloadsRepository.LINK.equals(item.type))linkCommand(item,DirectLinkDownloadService.PAUSE);
            else if(DownloadsRepository.AUDIO.equals(item.type))send(item,DownloadWorker.PAUSE);
            else if(DownloadsRepository.EDGE.equals(item.type)&&item.fullJob!=null)fullCommand(item,true);
            else if(DownloadsRepository.EDGE.equals(item.type)){
                Toast.makeText(this,"Остановите текущее воспроизведение Edge TTS перед архивированием",Toast.LENGTH_LONG).show();return false;
            }else return false;
            return true;
        }catch(Exception error){Toast.makeText(this,"Не удалось приостановить: "+error.getMessage(),Toast.LENGTH_LONG).show();return false;}
    }
    /** Open the archive actions with a RIGHT swipe, without ItemTouchHelper's
     * swipe-to-dismiss pending-cleanup state (which intercepted button clicks).
     * Vertical RecyclerView scrolling and child clicks remain native.
     */
    private void attachArchiveSwipe(){
        final int slop=ViewConfiguration.get(this).getScaledTouchSlop();
        list.addOnItemTouchListener(new RecyclerView.OnItemTouchListener(){
            private RecyclerView.ViewHolder candidate;
            private View foreground,actions;
            private float downX,downY,initialOffset;
            private boolean dragging;
            @Override public boolean onInterceptTouchEvent(RecyclerView rv,MotionEvent e){
                int action=e.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){
                    candidate=null;foreground=null;actions=null;dragging=false;
                    downX=e.getX();downY=e.getY();
                    View row=rv.findChildViewUnder(downX,downY);
                    if(row==null)return false;
                    RecyclerView.ViewHolder holder=rv.getChildViewHolder(row);
                    int pos=holder.getBindingAdapterPosition();
                    if(pos<0||pos>=rows.size()||rows.get(pos).item==null)return false;
                    if(DownloadsRepository.WIFI.equals(rows.get(pos).item.type))return false;
                    LinearLayout itemRoot=(LinearLayout)holder.itemView;
                    if(itemRoot.getChildCount()==0||!(itemRoot.getChildAt(0) instanceof FrameLayout))return false;
                    FrameLayout frame=(FrameLayout)itemRoot.getChildAt(0);
                    if(frame.getChildCount()<2)return false;
                    candidate=holder;actions=frame.getChildAt(0);foreground=frame.getChildAt(1);
                    initialOffset=foreground.getTranslationX();
                    return false;
                }
                if(action==MotionEvent.ACTION_MOVE&&candidate!=null){
                    float dx=e.getX()-downX,dy=e.getY()-downY;
                    // A closed card opens to the right; an open card may close to the left.
                    if(ArchiveSwipeMath.horizontal(dx,dy,slop,initialOffset)){
                        dragging=true;
                        actions.setVisibility(View.VISIBLE);
                        float width=Math.max(dp(1),actions.getWidth());
                        foreground.setTranslationX(ArchiveSwipeMath.offset(initialOffset,dx,width));
                        rv.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)
                    clearGesture();
                return false;
            }
            @Override public void onTouchEvent(RecyclerView rv,MotionEvent e){
                if(!dragging||candidate==null||foreground==null)return;
                int action=e.getActionMasked();
                int pos=candidate.getBindingAdapterPosition();
                if(pos<0||pos>=rows.size()||rows.get(pos).item==null){clearGesture();return;}
                float limit=Math.max(dp(1),actions.getWidth());
                if(action==MotionEvent.ACTION_MOVE){
                    float offset=ArchiveSwipeMath.offset(initialOffset,e.getX()-downX,limit);
                    foreground.setTranslationX(offset);
                }else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                    DownloadsRepository.Item item=rows.get(pos).item;
                    boolean open=ArchiveSwipeMath.reveal(foreground.getTranslationX(),limit,
                        action==MotionEvent.ACTION_UP);
                    String old=revealedSwipeId;
                    revealedSwipeId=open?item.id:"";
                    foreground.animate().translationX(open?limit:0f).setDuration(140L).start();
                    if(!open)actions.setVisibility(View.INVISIBLE);
                    if(!old.isEmpty()&&!old.equals(revealedSwipeId)){
                        for(int i=0;i<rows.size();i++){
                            if(rows.get(i).item!=null&&old.equals(rows.get(i).item.id)){
                                if(i!=pos)adapter.notifyItemChanged(i);
                                break;
                            }
                        }
                    }
                    clearGesture();
                }
            }
            @Override public void onRequestDisallowInterceptTouchEvent(boolean disallow){}
            private void clearGesture(){candidate=null;foreground=null;actions=null;dragging=false;}
        });
    }
    private final class JobsAdapter extends RecyclerView.Adapter<JobsAdapter.Holder>{
        @Override public long getItemId(int position){return rowId(rows.get(position)).hashCode();}
        @Override public int getItemViewType(int p){return rows.get(p).item==null?0:1;}
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            LinearLayout root=new LinearLayout(DownloadActivity.this);root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new Holder(root);
        }
        private String staticSignature(DownloadsRepository.Item item){
            return item.id+"|"+item.title+"|"+
                (DownloadsRepository.WIFI.equals(item.type)?"":item.description)+"|"+item.cover+"|"+
                item.author+"|"+item.narrator+"|"+item.format+"|"+item.type+"|"+
                item.ready+"|"+item.confirmedReady;
        }
        private void updateFast(Holder h,DownloadsRepository.Item item){
            h.boundItem=item;
            if(h.infoText!=null&&!item.description.contentEquals(h.infoText.getText()))
                h.infoText.setText(item.description);
            if(h.currentStatus!=null){
                if(!item.status.contentEquals(h.currentStatus.getText()))h.currentStatus.setText(item.status);
                h.currentStatus.setTextColor(item.failed?ERROR:
                    item.ready||"Идёт озвучивание".equals(item.status)?GREEN:ACCENT);
            }
            if(h.progressBar!=null){
                if(DownloadsRepository.EDGE.equals(item.type)||DownloadsRepository.WIFI.equals(item.type)||
                   (DownloadsRepository.MANGA.equals(item.type)&&item.unknownTotal)){
                    h.progressBar.setMax(100);h.progressBar.setProgress(item.coveragePercent,true);
                    h.progressBar.setIndeterminate(false);
                }else{
                    h.progressBar.setMax(Math.max(1,item.total));
                    h.progressBar.setProgress(Math.min(item.done,item.total),true);
                    h.progressBar.setIndeterminate(item.total==0&&item.running);
                }
            }
            if(h.progressBar!=null)h.progressBar.setWorking(item.running&&!item.ready);
            if(h.swipeActions!=null&&h.swipeCard!=null){
                boolean opened=item.id.equals(revealedSwipeId);
                h.swipeActions.setVisibility(opened?View.VISIBLE:View.INVISIBLE);
                h.swipeCard.setTranslationX(opened?h.swipeActions.getWidth():0f);
            }
        }
        @Override public void onBindViewHolder(Holder h,int pos,java.util.List<Object> payloads){
            if(payloads.contains("fast-progress")&&rows.get(pos).item!=null
                &&rows.get(pos).item.id.equals(h.boundId)){
                updateFast(h,rows.get(pos).item);return;
            }
            onBindViewHolder(h,pos);
        }
        @Override public void onBindViewHolder(Holder h,int pos){
            DisplayRow row=rows.get(pos);
            if(row.item!=null&&row.item.id.equals(h.boundId)
                    &&staticSignature(row.item).equals(h.boundStatic)){
                updateFast(h,row.item);return;
            }
            LinearLayout root=(LinearLayout)h.itemView;root.removeAllViews();
            h.boundId="";h.boundStatic="";h.boundItem=row.item;
            h.currentStatus=null;h.infoText=null;h.progressBar=null;h.swipeActions=null;h.swipeCard=null;
            if(row.item==null){
                TextView label=text(row.section,23,true);label.setTypeface(Typeface.SERIF,Typeface.BOLD);
                label.setPadding(dp(2),dp(8),0,dp(12));root.addView(label);return;
            }
            DownloadsRepository.Item item=row.item;
            h.boundId=item.id;h.boundStatic=staticSignature(item);
            FrameLayout swipeFrame=new FrameLayout(DownloadActivity.this);
            LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.bottomMargin=dp(10);
            root.addView(swipeFrame,fp);
            LinearLayout swipeActions=new LinearLayout(DownloadActivity.this);
            swipeActions.setOrientation(LinearLayout.HORIZONTAL);swipeActions.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-1,Gravity.LEFT);
            swipeFrame.addView(swipeActions,ap);
            // An opened swipe always inherits the current theme. Never use hard-coded
            // purple/brown fills that become unreadable in a night/custom theme.
            int themedAccent=UiColorScheme.accent(DownloadActivity.this);
            int ordinary=UiColorScheme.neutral(DownloadActivity.this);
            TextView confirm=center("В архив",13);confirm.setTextColor(ReaderButtonColors.contrastingText(themedAccent));
            confirm.setBackground(bg(themedAccent,14));
            TextView cancel=center("Отмена",13);cancel.setTextColor(ReaderButtonColors.contrastingText(ordinary));
            cancel.setBackground(bg(ordinary,14));
            LinearLayout.LayoutParams archiveParams=new LinearLayout.LayoutParams(dp(80),-1);
            swipeActions.addView(confirm,archiveParams);
            LinearLayout.LayoutParams cancelParams=new LinearLayout.LayoutParams(dp(80),-1);
            cancelParams.leftMargin=dp(5);swipeActions.addView(cancel,cancelParams);
            confirm.setContentDescription("В архив");cancel.setContentDescription("Отмена");
            confirm.setOnClickListener(v->{if(h.boundItem!=null)archive(h.boundItem);});
            cancel.setOnClickListener(v->{revealedSwipeId="";int at=h.getBindingAdapterPosition();
                if(at>=0)notifyItemChanged(at);});
            swipeActions.setVisibility(item.id.equals(revealedSwipeId)?View.VISIBLE:View.INVISIBLE);
            LinearLayout card=new LinearLayout(DownloadActivity.this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(bg(PANEL,19));
            swipeFrame.addView(card,new FrameLayout.LayoutParams(-1,-2));
            h.swipeCard=card;h.swipeActions=swipeActions;
            // One layout-driven offset is authoritative across RecyclerView rebinds.
            // No delayed post() may overwrite an ongoing gesture.
            swipeFrame.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,oright,ob)->{
                boolean opened=item.id.equals(revealedSwipeId);
                swipeActions.setVisibility(opened?View.VISIBLE:View.INVISIBLE);
                card.setTranslationX(opened?swipeActions.getWidth():0f);
            });
            LinearLayout top=new LinearLayout(DownloadActivity.this);top.setGravity(Gravity.TOP);card.addView(top);
            FrameLayout coverFrame=new FrameLayout(DownloadActivity.this);coverFrame.setBackground(bg(UiColorScheme.blend(PANEL,INK,.09f),12));
            top.addView(coverFrame,new LinearLayout.LayoutParams(dp(item.ready?75:92),dp(item.ready?106:124)));
            TextView placeholder=text(DownloadsRepository.MANGA.equals(item.type)?"▣":(DownloadsRepository.RANOBE.equals(item.type)||DownloadsRepository.RANOBES.equals(item.type))?"▤":"♫",27,true);placeholder.setGravity(Gravity.CENTER);coverFrame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            ImageView cover=new ImageView(DownloadActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);coverFrame.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            CoverImageLoader.show(cover,item.cover,DownloadsRepository.EDGE.equals(item.type)||DownloadsRepository.WIFI.equals(item.type));
            LinearLayout detail=new LinearLayout(DownloadActivity.this);detail.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,-2,1);d.leftMargin=dp(12);top.addView(detail,d);
            TextView title=text(item.title,17,true);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setMaxLines(2);detail.addView(title);
            TextView type=text(DownloadsRepository.WIFI.equals(item.type)?"◉ Wi-Fi":DownloadsRepository.LINK.equals(item.type)?"↓ По ссылке":DownloadsRepository.EDGE.equals(item.type)?"▥ Edge TTS":DownloadsRepository.MANGA.equals(item.type)?"▣ Манга · MangaLib":DownloadsRepository.RANOBE.equals(item.type)?"▤ Ранобэ · RanobeLIB":DownloadsRepository.RANOBES.equals(item.type)?"▤ Ранобэ · Ranobes.net":"▤ Аудиокнига",12,false);type.setTextColor(ACCENT);
            LinearLayout.LayoutParams tagParams=new LinearLayout.LayoutParams(-2,-2);tagParams.topMargin=dp(5);detail.addView(type,tagParams);
            StringBuilder brief=new StringBuilder();
            if(item.author!=null&&!item.author.isEmpty())brief.append(item.author);
            if(item.narrator!=null&&!item.narrator.isEmpty()){
                if(brief.length()>0)brief.append(" · ");brief.append("Читает: ").append(item.narrator);
            }
            if(item.format!=null&&!item.format.isEmpty()){
                if(brief.length()>0)brief.append(" · ");brief.append(item.format);
            }
            if(brief.length()>0){TextView byline=text(brief.toString(),12,false);byline.setTextColor(MUTED);detail.addView(byline);}

            TextView info=text(item.description,12,false);h.infoText=info;info.setTextColor(MUTED);
            LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.topMargin=dp(6);detail.addView(info,il);
            LinearLayout statusLine=new LinearLayout(DownloadActivity.this);
            statusLine.setGravity(Gravity.CENTER_VERTICAL);detail.addView(statusLine);
            TextView status=text(item.status,12,true);h.currentStatus=status;
            status.setTextColor(item.failed?ERROR:item.ready||"Идёт озвучивание".equals(item.status)?GREEN:ACCENT);
            statusLine.addView(status,new LinearLayout.LayoutParams(0,-2,1));
            if(item.confirmedReady || (!DownloadsRepository.EDGE.equals(item.type)&&item.ready)){
                ImageView complete=new ImageView(DownloadActivity.this);
                complete.setImageResource(R.drawable.ic_download_chapter_complete);
                complete.setImageTintList(android.content.res.ColorStateList.valueOf(UiColorScheme.accent(DownloadActivity.this)));
                complete.setContentDescription(DownloadsRepository.WIFI.equals(item.type)?"Файл импортирован":"Все главы загружены");
                LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(18),dp(18));
                iconLp.leftMargin=dp(7);statusLine.addView(complete,iconLp);
            }
            if(!item.ready){
                AnimatedDownloadProgressBar progress=new AnimatedDownloadProgressBar(DownloadActivity.this);
                if(DownloadsRepository.EDGE.equals(item.type)||DownloadsRepository.WIFI.equals(item.type)){
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
                UiColorScheme.tintProgress(progress,DownloadActivity.this);
                h.progressBar=progress;progress.setWorking(item.running&&!item.ready);
                LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(7));pl.topMargin=dp(9);detail.addView(progress,pl);
            }
            // Every row exposes its actions through one compact overflow menu.
            // No duplicate file buttons or bulky download controls.
            ImageView more=icon(R.drawable.ic_more_vertical,"Действия загрузки",43);
            more.setBackground(null); // Only the ellipsis, with the full invisible hit target.
            more.setColorFilter(UiColorScheme.prefs(DownloadActivity.this).getInt("fg",INK));
            LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(dp(43),dp(43));
            moreParams.leftMargin=dp(5);top.addView(more,moreParams);
            more.setOnClickListener(v->{if(h.boundItem!=null)menu(more,h.boundItem);});card.setOnClickListener(v->{
                if(!revealedSwipeId.isEmpty()){
                    String previous=revealedSwipeId;revealedSwipeId="";
                    for(int k=0;k<rows.size();k++)if(rows.get(k).item!=null&&previous.equals(rows.get(k).item.id)){
                        notifyItemChanged(k);break;
                    }
                }else if(h.boundItem!=null)open(h.boundItem);
            });
        }
        TextView control(String caption){TextView b=center(caption,13);int fill=ReaderButtonColors.neutral(DownloadActivity.this,PANEL);b.setBackground(bg(fill,12));b.setTextColor(ReaderButtonColors.contrastingText(fill));return b;}
        @Override public int getItemCount(){return rows.size();}
        final class Holder extends RecyclerView.ViewHolder{
            String boundId="",boundStatic="";
            DownloadsRepository.Item boundItem;
            TextView currentStatus,infoText;AnimatedDownloadProgressBar progressBar;
            LinearLayout swipeCard,swipeActions;
            Holder(View v){super(v);}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(EdgeFolderPicker.accept(this,request,result,data))return;
        if(DownloadFolderPicker.receive(this,request,result,data,
            DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.BOOK,ok->{if(ok)refresh();})){return;}
        if(request==EDGE_AUDIOBOOK_REQUEST){
            DownloadsRepository.Item selected=pendingEdgeAudiobook;pendingEdgeAudiobook=null;
            if(result!=RESULT_OK||data==null||data.getData()==null||selected==null)return;
            Uri destination=data.getData();
            try{getContentResolver().takePersistableUriPermission(destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
            catch(Exception e){Toast.makeText(this,"Папка аудиокниг недоступна",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Экспорт готовых MP3-глав запущен…",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                try{
                    BookLoader.Book book=null;
                    try{book=BookLoader.load(this,Uri.parse(selected.bookUri));}
                    catch(Exception noToc){android.util.Log.w("EdgeExport","No table of contents; using chapter numbering",noToc);}
                    int moved=EdgeChapterArchive.exportAudiobook(getApplicationContext(),
                        selected.bookUri,selected.title,destination,book==null?null:book.chapters);
                    runOnUiThread(()->Toast.makeText(this,
                        "Аудиотека: готовых MP3-глав "+moved,Toast.LENGTH_LONG).show());
                }catch(Exception ex){runOnUiThread(()->Toast.makeText(this,
                    "Перенос не выполнен: "+ex.getMessage(),Toast.LENGTH_LONG).show());}
            },"edge-audiobook-transfer").start();
            return;
        }
        if(request==BULK_TRANSFER_REQUEST){
            if(result!=RESULT_OK||data==null||data.getData()==null)return;
            Uri destination=data.getData();
            try{getContentResolver().takePersistableUriPermission(destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }catch(Exception error){Toast.makeText(this,"Папка недоступна",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Массовый перенос запущен",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                BulkLibraryTransfer.Report report=BulkLibraryTransfer.move(getApplicationContext(),destination);
                runOnUiThread(()->{diskScannedAt=0;refresh();
                    new SwipeDialogs.Builder(this).setTitle("Результат переноса")
                        .setMessage(report.message()).setPositiveButton("Закрыть",null).show();});
            },"bulk-library-transfer-v158").start();return;
        }
        if(request==TRANSFER_REQUEST){
            DownloadsRepository.Item selected=pendingTransfer;
            String selectedId=selected==null?pendingTransferId:selected.id;
            pendingTransfer=null;pendingTransferId="";
            if(result!=RESULT_OK||data==null||data.getData()==null||selectedId.isEmpty())return;
            Uri destination=data.getData();
            try{getContentResolver().takePersistableUriPermission(destination,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}
            catch(Exception ex){Toast.makeText(this,"Нет доступа к выбранной папке",Toast.LENGTH_LONG).show();return;}
            Toast.makeText(this,"Проверяем и переносим файлы…",Toast.LENGTH_SHORT).show();
            new Thread(()->{
                try{
                    DownloadsRepository.Item target=DownloadsRepository.loadOne(getApplicationContext(),selectedId);
                    if(target==null)throw new java.io.IOException("Загрузка не найдена");
                    SafeDownloadTransfer.Result report=SafeDownloadTransfer.copy(getApplicationContext(),target,destination);
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
                File source=MangaLibStorage.localArchive(getApplicationContext(),path);
                String root=MangaLibStorage.root(getApplicationContext()).getCanonicalPath()+File.separator;
                if(!MangaLibStorage.isUri(path)&&!source.getCanonicalPath().startsWith(root))
                    throw new java.io.IOException("Недопустимый CBZ");
                if(!source.isFile()||!source.getName().endsWith(".cbz"))throw new java.io.IOException("CBZ не найден");
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
