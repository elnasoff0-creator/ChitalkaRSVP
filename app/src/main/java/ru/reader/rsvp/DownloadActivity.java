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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** Unified online-download dashboard. Neither downloader is owned/started by this screen. */
public final class DownloadActivity extends Activity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949,GREEN=0xff2b6145;
    private static final String FILTER_KEY="downloads_filter_v2";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean visible,bound;
    private EdgeDownloadService edge;
    private LinearLayout filters;
    private TextView summary;
    private RecyclerView list;
    private JobsAdapter adapter;
    private int filter;
    private final ArrayList<DisplayRow> rows=new ArrayList<>();
    private final Runnable ticker=new Runnable(){@Override public void run(){if(!visible)return;refresh();main.postDelayed(this,1700);}};
    private final ServiceConnection edgeConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){edge=((EdgeDownloadService.LocalBinder)b).getService();refresh();}
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
        bound=bindService(new Intent(this,EdgeDownloadService.class),edgeConnection,BIND_AUTO_CREATE);
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(25),dp(12),dp(12));root.setBackgroundColor(BG);setContentView(root);
        FrameLayout top=new FrameLayout(this);root.addView(top,new LinearLayout.LayoutParams(-1,dp(53)));
        TextView heading=text("Загрузки",25,true);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);heading.setGravity(Gravity.CENTER);
        top.addView(heading,new FrameLayout.LayoutParams(-1,-1));
        ImageView back=icon(R.drawable.ic_back,"Назад",50);FrameLayout.LayoutParams bl=new FrameLayout.LayoutParams(dp(49),dp(49),Gravity.START|Gravity.CENTER_VERTICAL);
        top.addView(back,bl);back.setOnClickListener(v->BackNavigation.back(this));

        LinearLayout actions=new LinearLayout(this);
        LinearLayout.LayoutParams al=new LinearLayout.LayoutParams(-1,dp(120));al.topMargin=dp(12);root.addView(actions,al);
        TextView fullBook=center("▤  Подготовить всю книгу Edge TTS",15);
        fullBook.setTypeface(Typeface.DEFAULT,Typeface.BOLD);fullBook.setBackground(bg(PANEL,17));
        LinearLayout.LayoutParams fullLp=new LinearLayout.LayoutParams(-1,dp(49));fullLp.topMargin=dp(9);
        root.addView(fullBook,fullLp);
        fullBook.setOnClickListener(v->startActivity(new Intent(this,EdgeFullBookActivity.class)));
        addMainAction(actions,R.drawable.ic_search_audiobook,"Поиск аудиокниги",()->startActivity(new Intent(this,KvuheSearchActivity.class)),false);
        addMainAction(actions,R.drawable.ic_import_link,"Скачать по ссылке",()->startActivity(new Intent(this,LinkDownloadActivity.class)),true);

        HorizontalScrollView scrolling=new HorizontalScrollView(this);scrolling.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(-1,dp(52));fl.topMargin=dp(12);root.addView(scrolling,fl);
        filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);scrolling.addView(filters);
        updateFilterButtons();
        summary=text("Загрузка данных…",13,false);summary.setTextColor(MUTED);summary.setGravity(Gravity.CENTER);
        summary.setPadding(0,dp(11),0,dp(11));root.addView(summary);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new JobsAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
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
    private void updateFilterButtons(){
        filters.removeAllViews();String[] labels={"Все","В процессе","Аудиокниги","Edge TTS","По ссылке"};
        for(int i=0;i<labels.length;i++){
            final int n=i;TextView v=center(labels[i],14);v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
            v.setPadding(dp(14),0,dp(14),0);v.setMinWidth(dp(78));
            int fill=n==filter?ReaderButtonColors.accent(this,ACCENT):ReaderButtonColors.neutral(this,PANEL);v.setTextColor(ReaderButtonColors.contrastingText(fill));v.setBackground(bg(fill,17));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(45));if(i>0)lp.leftMargin=dp(7);filters.addView(v,lp);
            v.setOnClickListener(view->{filter=n;getSharedPreferences("reader",0).edit().putInt(FILTER_KEY,n).apply();updateFilterButtons();refresh();});
        }
    }
    @Override protected void onResume(){super.onResume();visible=true;
        AudiobookCoverStore.backfill(this,()->{if(visible)refresh();});
        main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){if(bound)unbindService(edgeConnection);main.removeCallbacksAndMessages(null);super.onDestroy();}
    private void refresh(){if(list==null||adapter==null)return;
        ArrayList<DownloadsRepository.Item> all=DownloadsRepository.load(this,edge);
        int active=0,done=0;for(DownloadsRepository.Item item:all){if(item.ready)done++;else active++;}
        summary.setText("Онлайн-загрузки: "+all.size()+"  ·  В процессе: "+active+"  ·  Готово: "+done);
        rows.clear();ArrayList<DownloadsRepository.Item> unfinished=new ArrayList<>(),ready=new ArrayList<>();
        for(DownloadsRepository.Item item:all){
            if(filter==1&&item.ready)continue;
            if(filter==2&&!DownloadsRepository.AUDIO.equals(item.type))continue;
            if(filter==3&&!DownloadsRepository.EDGE.equals(item.type))continue;
            if(filter==4&&!DownloadsRepository.LINK.equals(item.type))continue;
            (item.ready?ready:unfinished).add(item);
        }
        if(!unfinished.isEmpty()){
            rows.add(new DisplayRow("В процессе",null));for(DownloadsRepository.Item item:unfinished)rows.add(new DisplayRow(null,item));
        }
        if(!ready.isEmpty()){
            rows.add(new DisplayRow("Готово",null));for(DownloadsRepository.Item item:ready)rows.add(new DisplayRow(null,item));
        }
        if(rows.isEmpty())rows.add(new DisplayRow(filter==0?"Загрузок пока нет":"Нет загрузок в этом разделе",null));
        adapter.notifyDataSetChanged();
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
            if(item.fullJob!=null&&item.ready){
                EdgeFullBookStore.Job job=item.fullJob;
                SharedPreferences prefs=getSharedPreferences("reader",0);
                if(!job.voice.equals(prefs.getString("edge_voice","ru-RU-SvetlanaNeural"))
                    ||!job.rate.equals(prefs.getString("edge_rate","+0%"))
                    ||job.chars!=EdgeSegmentSettings.get(prefs)){
                    new AlertDialog.Builder(this).setTitle("Прослушать готовую аудиокнигу")
                        .setMessage("Для воспроизведения подготовленных MP3 будут выбраны голос и длина фрагмента, использованные при полной озвучке. Продолжить?")
                        .setNegativeButton("Отмена",null).setPositiveButton("Слушать",(d,w)->{
                            prefs.edit().putString("edge_voice",job.voice).putString("edge_rate",job.rate)
                                .putInt(EdgeSegmentSettings.KEY,job.chars).apply();
                            startActivity(new Intent(this,EdgeOnlineActivity.class).putExtra("book_uri",item.bookUri));
                        }).show();return;
                }
            }
            startActivity(new Intent(this,EdgeOnlineActivity.class).putExtra("book_uri",item.bookUri));
        }else if(item.ready){
            startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_book_id","url:"+item.source));
        }else startActivity(new Intent(this,UrlImportActivity.class).putExtra("source",item.source));
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
    private void queue(DownloadsRepository.Item item){
        startActivity(new Intent(this,EdgeQueueActivity.class).putExtra(EdgeDownloadService.EXTRA_URI,item.bookUri));
    }
    private void confirmCancel(DownloadsRepository.Item item){
        new AlertDialog.Builder(this).setTitle("Отменить загрузку?")
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
        new AlertDialog.Builder(this).setTitle(what)
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
    private void menu(View anchor,DownloadsRepository.Item item){
        if(DownloadsRepository.LINK.equals(item.type)){
            PopupMenu p=new PopupMenu(this,anchor);
            p.getMenu().add(0,1,0,"Список файлов");p.getMenu().add(0,2,1,"Добавить MP3 в библиотеку");
            if(!item.ready)p.getMenu().add(0,3,2,"Продолжить загрузку");
            p.getMenu().add(0,4,3,"Убрать из списка");p.getMenu().add(0,5,4,"Удалить скачанные файлы");
            p.setOnMenuItemClickListener(m->{switch(m.getItemId()){
                case 1:open(item);break;
                case 2:LinkDownloadStore.addToLibrary(this,item.link);Toast.makeText(this,"Добавлено в библиотеку",Toast.LENGTH_SHORT).show();break;
                case 3:linkCommand(item,DirectLinkDownloadService.START);break;
                case 4:case 5:final boolean erase=m.getItemId()==5;new AlertDialog.Builder(this).setTitle(erase?"Удалить загруженные MP3?":"Убрать задание из списка?")
                    .setMessage(erase?"Будут удалены файлы этого задания в выбранной папке.":"Файлы останутся на устройстве.")
                    .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->deleteLink(item,erase)).show();break;
            }return true;});p.show();return;
        }
        PopupMenu popup=new PopupMenu(this,anchor);
        if(DownloadsRepository.EDGE.equals(item.type)){
            popup.getMenu().add(0,1,0,"Открыть Edge-плеер");
            popup.getMenu().add(0,2,1,"Открыть очередь");
            popup.getMenu().add(0,7,1,"Подготовить всю книгу");
            if(item.fullJob!=null&&!item.ready)
                popup.getMenu().add(0,8,1,item.running?"Пауза полной подготовки":"Продолжить полную подготовку");
        }else{
            popup.getMenu().add(0,1,0,item.ready?"Слушать аудиокнигу":"Подробнее / главы");
            if(!item.ready)popup.getMenu().add(0,5,1,"Продолжить загрузку");
        }
        popup.getMenu().add(0,3,2,"Открыть файлы");
        popup.getMenu().add(0,6,3,"Убрать из списка");
        popup.getMenu().add(0,4,4,"Удалить вместе с MP3");
        popup.setOnMenuItemClickListener(m->{
            switch(m.getItemId()){
                case 1:open(item);break;
                case 2:queue(item);break;
                case 3:openFolder(item);break;
                case 4:confirmDelete(item,true);break;
                case 5:send(item,DownloadWorker.START);break;
                case 6:confirmDelete(item,false);break;
                case 7:startActivity(new Intent(this,EdgeFullBookActivity.class)
                    .putExtra("book_uri",item.bookUri).putExtra("book_title",item.title));break;
                case 8:fullCommand(item,item.running);break;
            }return true;
        });popup.show();
    }
    private final class JobsAdapter extends RecyclerView.Adapter<JobsAdapter.Holder>{
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
                progress.setMax(Math.max(1,item.total));progress.setProgress(Math.min(item.done,item.total));
                if(item.total==0)progress.setIndeterminate(item.running);
                LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(7));pl.topMargin=dp(9);detail.addView(progress,pl);
            }
            LinearLayout controls=new LinearLayout(DownloadActivity.this);controls.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams ctl=new LinearLayout.LayoutParams(-1,dp(44));ctl.topMargin=dp(10);card.addView(controls,ctl);
            if(DownloadsRepository.LINK.equals(item.type) && !item.ready){
                TextView pause=control(item.running?"Пауза":"Продолжить");controls.addView(pause,new LinearLayout.LayoutParams(0,dp(42),1));pause.setOnClickListener(v->linkCommand(item,item.running?DirectLinkDownloadService.PAUSE:DirectLinkDownloadService.START));
                TextView details=control("Файлы");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);p.leftMargin=dp(6);controls.addView(details,p);details.setOnClickListener(v->open(item));
            }else if(DownloadsRepository.EDGE.equals(item.type)){
                if(item.fullJob!=null&&!item.ready){
                    TextView pause=control(item.running?"Пауза":"Продолжить");
                    controls.addView(pause,new LinearLayout.LayoutParams(0,dp(42),1));
                    pause.setOnClickListener(v->fullCommand(item,item.running));
                }else if(item.ready){
                    TextView listen=control("Слушать офлайн");
                    controls.addView(listen,new LinearLayout.LayoutParams(0,dp(42),1));
                    listen.setOnClickListener(v->open(item));
                }else{
                    TextView prepare=control("Подготовить всю книгу");
                    controls.addView(prepare,new LinearLayout.LayoutParams(0,dp(42),1));
                    prepare.setOnClickListener(v->startActivity(new Intent(DownloadActivity.this,EdgeFullBookActivity.class)
                        .putExtra("book_uri",item.bookUri).putExtra("book_title",item.title)));
                }
                TextView queueButton=control("Фрагменты");
                LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(0,dp(42),1);qlp.leftMargin=dp(6);
                controls.addView(queueButton,qlp);queueButton.setOnClickListener(v->queue(item));
            }else if(!item.ready){
                TextView pause=control(item.running?"Пауза":"Продолжить");controls.addView(pause,new LinearLayout.LayoutParams(0,dp(42),1));
                pause.setOnClickListener(v->send(item,item.running?DownloadWorker.PAUSE:DownloadWorker.START));
                TextView cancel=control("Отмена");LinearLayout.LayoutParams cb=new LinearLayout.LayoutParams(0,dp(42),1);cb.leftMargin=dp(7);controls.addView(cancel,cb);
                cancel.setOnClickListener(v->confirmCancel(item));
            }else{
                TextView done=control("✓ Скачано");done.setTextColor(GREEN);done.setOnClickListener(v->open(item));
                controls.addView(done,new LinearLayout.LayoutParams(0,dp(42),1));
            }
            ImageView more=icon(android.R.drawable.ic_menu_more,"Дополнительные действия",43);
            LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(dp(43),dp(43));moreParams.leftMargin=dp(8);controls.addView(more,moreParams);
            more.setOnClickListener(v->menu(more,item));card.setOnClickListener(v->open(item));
        }
        TextView control(String caption){TextView b=center(caption,13);b.setBackground(bg(0xfff1e4d7,12));b.setTextColor(INK);return b;}
        @Override public int getItemCount(){return rows.size();}
        final class Holder extends RecyclerView.ViewHolder{Holder(View v){super(v);}}
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
