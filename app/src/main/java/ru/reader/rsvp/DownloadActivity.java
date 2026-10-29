package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
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
        build();
        bound=bindService(new Intent(this,EdgeStreamingService.class),edgeConnection,BIND_AUTO_CREATE);
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(25),dp(12),dp(12));root.setBackgroundColor(BG);setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView back=icon(R.drawable.ic_menu_lines,"Главное меню",44);
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));
        back.setOnClickListener(v->UniversalDrawer.show(this));
        toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        toolbarAction(toolbar,R.drawable.ic_search_audiobook,"Поиск аудиокниг",()->startActivity(new Intent(this,KvuheSearchActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_import_link,"Импорт по ссылке",()->startActivity(new Intent(this,LinkDownloadActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_autoload,"Подготовить книгу Edge",()->startActivity(new Intent(this,EdgeFullBookActivity.class)));
        toolbarAction(toolbar,R.drawable.ic_settings,"Настройки загрузок",()->startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true)));
        TextView heading=text("Загрузки",25,true);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);
        LinearLayout.LayoutParams hl=new LinearLayout.LayoutParams(-1,-2);hl.topMargin=dp(14);root.addView(heading,hl);
        String[] currentFilters={"Все загрузки","В процессе","Аудиокниги","Edge TTS","По ссылке"};
        TextView filterSelect=center(currentFilters[Math.max(0,Math.min(filter,4))]+"  ▾",16);
        filterSelect.setGravity(Gravity.CENTER_VERTICAL|Gravity.CENTER_HORIZONTAL);
        filterSelect.setBackground(bg(ReaderButtonColors.neutral(this,PANEL),15));
        filterSelect.setTextColor(ReaderButtonColors.contrastingText(ReaderButtonColors.neutral(this,PANEL)));
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(49));fl.topMargin=dp(14);root.addView(filterSelect,fl);
        filterSelect.setOnClickListener(v->{
            PopupMenu menu=new PopupMenu(this,filterSelect);
            String[] names={"Все загрузки","В процессе","Аудиокниги","Edge TTS","По ссылке"};
            for(int i=0;i<names.length;i++){final int ix=i;menu.getMenu().add(names[i]).setOnMenuItemClickListener(item->{
                filter=ix;filterSelect.setText(names[ix]+"  ▾");getSharedPreferences("reader",0).edit().putInt(FILTER_KEY,ix).apply();showCachedFilter();return true;});}
            menu.getMenu().add(1,101,99,"Удалить скачанные файлы")
                .setOnMenuItemClickListener(item->{confirmDeleteAllDownloads();return true;});
            menu.show();
        });
        summary=text("Загрузка данных…",13,false);summary.setTextColor(MUTED);summary.setGravity(Gravity.CENTER);
        summary.setPadding(0,dp(11),0,dp(11));root.addView(summary);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new JobsAdapter();adapter.setHasStableIds(true);list.setItemAnimator(null);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
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
        AudiobookCoverStore.backfill(this,()->{if(visible)refresh();});
        main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(edgeConnection);main.removeCallbacksAndMessages(null);scan.shutdownNow();super.onDestroy();}
    /** Never traverse book directories, parse JSON or stat MP3 on the UI thread.
     * Only one scan can run; refresh requests that arrive while it runs are merged
     * into the next periodic tick, so a slow SD card cannot create a backlog.
     */
    private void refresh(){
        if(!visible||adapter==null||refreshInFlight||scan.isShutdown())return;
        refreshInFlight=true;
        final int currentFilter=filter;
        // Read the bound Service only on the main thread. Background scanning
        // receives immutable values and never touches its mutable playback state.
        final String liveUri=edge!=null&&edge.isActive()?edge.bookUri():"";
        final String liveTitle=liveUri.isEmpty()?"":edge.title();
        final ArrayList<DisplayRow> before=new ArrayList<>(rows);
        scan.execute(()->{
            try{
                ArrayList<DownloadsRepository.Item> all=DownloadsRepository.load(getApplicationContext(),null);
                if(!liveUri.isEmpty()){
                    boolean found=false;
                    for(DownloadsRepository.Item item:all)if(liveUri.equals(item.bookUri)){
                        found=true;item.running=true;item.ready=false;
                        item.status="Идёт озвучивание";
                        break;
                    }
                    if(!found){
                        DownloadsRepository.Item live=new DownloadsRepository.Item();
                        live.type=DownloadsRepository.EDGE;live.id="edge:"+liveUri;
                        live.bookUri=liveUri;live.cover=liveUri;live.title=liveTitle;
                        live.status="Идёт озвучивание";live.description="Текущий аудиофрагмент";
                        live.running=true;all.add(0,live);
                    }
                }
                int active=0,done=0;
                for(DownloadsRepository.Item item:all){if(item.ready)done++;else active++;}
                ArrayList<DisplayRow> after=makeRows(all,currentFilter);
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
                final int running=active,complete=done;
                main.post(()->{
                    refreshInFlight=false;
                    if(!visible||adapter==null)return;
                    lastAll=all;
                    if(filter!=currentFilter){showCachedFilter();return;}
                    rows.clear();rows.addAll(after);
                    summary.setText("Онлайн-загрузки: "+all.size()+"  ·  В процессе: "+running+"  ·  Готово: "+complete);
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
        ArrayList<DisplayRow> next=makeRows(lastAll,filter);
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
    private static ArrayList<DisplayRow> makeRows(ArrayList<DownloadsRepository.Item> all,int filter){
        ArrayList<DisplayRow> result=new ArrayList<>();
        ArrayList<DownloadsRepository.Item> unfinished=new ArrayList<>(),ready=new ArrayList<>();
        for(DownloadsRepository.Item item:all){
            if(filter==1&&item.ready)continue;
            if(filter==2&&!DownloadsRepository.AUDIO.equals(item.type))continue;
            if(filter==3&&!DownloadsRepository.EDGE.equals(item.type))continue;
            if(filter==4&&!DownloadsRepository.LINK.equals(item.type))continue;
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
        if(DownloadsRepository.LINK.equals(item.type)){startActivity(new Intent(this,LinkDownloadDetailsActivity.class).putExtra(DirectLinkDownloadService.EXTRA_ID,item.link.id));}
        else if(DownloadsRepository.EDGE.equals(item.type)){
            openEdgeChapters(item);
        }else if(item.ready){
            startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_book_id","url:"+item.source));
        }else startActivity(new Intent(this,UrlImportActivity.class).putExtra("source",item.source));
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
    private void menu(View anchor,DownloadsRepository.Item item){
        if(DownloadsRepository.LINK.equals(item.type)){
            PopupMenu p=new PopupMenu(this,anchor);
            p.getMenu().add(0,1,0,"Список файлов");p.getMenu().add(0,2,1,"Добавить MP3 в библиотеку");
            if(!item.ready)p.getMenu().add(0,3,2,"Продолжить загрузку");
            p.getMenu().add(0,4,3,"Удалить из библиотеки/аудиотеки");p.getMenu().add(0,5,4,"Удалить скачанные файлы");
            p.setOnMenuItemClickListener(m->{switch(m.getItemId()){
                case 1:open(item);break;
                case 2:LinkDownloadStore.addToLibrary(this,item.link);Toast.makeText(this,"Добавлено в библиотеку",Toast.LENGTH_SHORT).show();break;
                case 3:linkCommand(item,DirectLinkDownloadService.START);break;
                case 4:case 5:final boolean erase=m.getItemId()==5;new SwipeDialogs.Builder(this).setTitle(erase?"Удалить загруженные MP3?":"Убрать задание из списка?")
                    .setMessage(erase?"Будут удалены файлы этого задания в выбранной папке.":"Файлы останутся на устройстве.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->deleteLink(item,erase)).show();break;
            }return true;});p.show();return;
        }
        PopupMenu popup=new PopupMenu(this,anchor);
        if(DownloadsRepository.EDGE.equals(item.type)){
            popup.getMenu().add(0,1,0,"Главы и загрузка");
            popup.getMenu().add(0,9,1,"Открыть Edge-плеер");
            if(item.fullJob!=null&&!item.ready)
                popup.getMenu().add(0,8,1,item.running?"Пауза полной подготовки":"Продолжить полную подготовку");
        }else{
            popup.getMenu().add(0,1,0,item.ready?"Слушать аудиокнигу":"Подробнее / главы");
            if(!item.ready)popup.getMenu().add(0,5,1,item.running?"Приостановить":"Продолжить загрузку");
        }
        popup.getMenu().add(0,3,2,"Открыть файлы");
        popup.getMenu().add(0,6,3,"Убрать из списка");
        popup.getMenu().add(0,4,4,"Удалить скачанные файлы");
        popup.setOnMenuItemClickListener(m->{
            switch(m.getItemId()){
                case 1:open(item);break;
                case 3:openFolder(item);break;
                case 4:confirmDelete(item,true);break;
                case 5:send(item,item.running?DownloadWorker.PAUSE:DownloadWorker.START);break;
                case 6:confirmDelete(item,false);break;
                case 8:fullCommand(item,item.running);break;
                case 9:listenEdge(item);break;
            }return true;
        });popup.show();
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
            TextView placeholder=text("♫",27,true);placeholder.setGravity(Gravity.CENTER);coverFrame.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            ImageView cover=new ImageView(DownloadActivity.this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);coverFrame.addView(cover,new FrameLayout.LayoutParams(-1,-1));
            CoverImageLoader.show(cover,item.cover,DownloadsRepository.EDGE.equals(item.type));
            LinearLayout detail=new LinearLayout(DownloadActivity.this);detail.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,-2,1);d.leftMargin=dp(12);top.addView(detail,d);
            TextView title=text(item.title,17,true);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setMaxLines(2);detail.addView(title);
            TextView type=text(DownloadsRepository.LINK.equals(item.type)?"↓ По ссылке":DownloadsRepository.EDGE.equals(item.type)?"▥ Edge TTS":"▤ Аудиокнига",12,false);type.setTextColor(ACCENT);
            LinearLayout.LayoutParams tagParams=new LinearLayout.LayoutParams(-2,-2);tagParams.topMargin=dp(5);detail.addView(type,tagParams);
            TextView info=text(item.description,12,false);info.setTextColor(MUTED);
            LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.topMargin=dp(6);detail.addView(info,il);
            TextView status=text(item.status,12,true);status.setTextColor(item.ready?GREEN:item.failed?0xffa13d36:ACCENT);detail.addView(status);
            if(!item.ready){
                ProgressBar progress=new ProgressBar(DownloadActivity.this,null,android.R.attr.progressBarStyleHorizontal);
                if(DownloadsRepository.EDGE.equals(item.type)){
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
            ImageView more=icon(android.R.drawable.ic_menu_more,"Действия загрузки",43);
            LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(dp(43),dp(43));
            moreParams.leftMargin=dp(5);top.addView(more,moreParams);
            more.setOnClickListener(v->menu(more,item));card.setOnClickListener(v->open(item));
        }
        TextView control(String caption){TextView b=center(caption,13);b.setBackground(bg(0xfff1e4d7,12));b.setTextColor(INK);return b;}
        @Override public int getItemCount(){return rows.size();}
        final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
