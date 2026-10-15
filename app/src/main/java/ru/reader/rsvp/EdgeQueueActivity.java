package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/** Full screen queue UI; a bound background service owns ALL download decisions. */
public final class EdgeQueueActivity extends Activity {
    private static final int BG=0xfff3e7d5,INK=0xff30221e,MUTED=0xff716259;
    private EdgeDownloadService download;
    private EdgePlaybackService player;
    private boolean downloadBound,playerBound;
    private ListView list;
    private TextView heading,subtitle;
    private Button autoload;
    private ArrayList<EdgeDownloadService.Clip> clips=new ArrayList<>();
    private final EdgeDownloadService.Observer observer=this::refresh;
    private final ServiceConnection queueConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder binder){
            download=((EdgeDownloadService.LocalBinder)binder).getService();
            String uri=getIntent().getStringExtra(EdgeDownloadService.EXTRA_URI);
            if(uri!=null&&!uri.isEmpty()) download.openBook(uri,-1);
            download.addObserver(observer);refresh();
        }
        @Override public void onServiceDisconnected(ComponentName n){download=null;refresh();}
    };
    private final ServiceConnection playerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder binder){player=((EdgePlaybackService.LocalBinder)binder).getService();}
        @Override public void onServiceDisconnected(ComponentName n){player=null;}
    };
    int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    GradientDrawable background(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(15));if(stroke!=0)d.setStroke(dp(2),stroke);return d;}
    Button action(String title){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextColor(INK);b.setTextSize(14);b.setBackground(background(0xfffff9f0,0));return b;}
    @Override public void onCreate(Bundle b){super.onCreate(b);draw();downloadBound=bindService(new Intent(this,EdgeDownloadService.class),queueConnection,BIND_AUTO_CREATE);
        playerBound=bindService(new Intent(this,EdgePlaybackService.class),playerConnection,BIND_AUTO_CREATE);}
    private void draw(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(12),dp(25),dp(12),dp(12));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));
        Button back=action("‹");back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        heading=new TextView(this);heading.setText("Список очереди");heading.setTextSize(22);heading.setTypeface(android.graphics.Typeface.SERIF,1);heading.setTextColor(INK);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(8);top.addView(heading,titleLp);
        Button folder=action("▱");folder.setContentDescription("Открыть папку аудио");
        folder.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_folder_audio,0,0,0);
        folder.setOnClickListener(v->openFolder());top.addView(folder,new LinearLayout.LayoutParams(dp(46),dp(48)));
        subtitle=new TextView(this);subtitle.setTextColor(MUTED);subtitle.setTextSize(13);
        subtitle.setPadding(dp(5),dp(6),dp(5),dp(10));root.addView(subtitle);
        autoload=action("Автоподгрузка");autoload.setOnClickListener(v->toggle());root.addView(autoload,new LinearLayout.LayoutParams(-1,dp(46)));
        list=new ListView(this);list.setDividerHeight(1);list.setBackground(background(0xfffff9f0,0));
        list.setAdapter(new BaseAdapter(){
            @Override public int getCount(){return clips.size();}
            @Override public Object getItem(int position){return clips.get(position);}
            @Override public long getItemId(int position){return position;}
            @Override public View getView(int pos,View old,android.view.ViewGroup parent){
                LinearLayout row=old instanceof LinearLayout?(LinearLayout)old:new LinearLayout(EdgeQueueActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(12),dp(11),dp(12),dp(11));row.removeAllViews();
                EdgeDownloadService.Clip c=clips.get(pos);
                boolean downloading=download!=null&&download.generatingStart()==c.start;
                boolean current=download!=null&&download.anchor()==c.start;
                boolean redownloading=download!=null && download.isRedownloading(c.start);
                String status=c.played?"Прослушан":downloading?"Скачивается":redownloading?"Перезагрузка":c.ready()?(current?"Текущий":"Готов"):"Ожидает";
                int tint=c.played?0xff999999:current?0xff356443:INK;
                LinearLayout header=new LinearLayout(EdgeQueueActivity.this);
                header.setOrientation(LinearLayout.HORIZONTAL);
                header.setGravity(Gravity.CENTER_VERTICAL);
                TextView title=new TextView(EdgeQueueActivity.this);
                title.setText("Фрагмент "+(pos+1)+" · "+status);
                title.setTextColor(tint);title.setTextSize(15);
                title.setTypeface(android.graphics.Typeface.DEFAULT,1);
                header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
                ImageButton reload=new ImageButton(EdgeQueueActivity.this);
                reload.setImageResource(R.drawable.ic_retry_fragment);
                reload.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                reload.setPadding(dp(9),dp(9),dp(9),dp(9));
                reload.setBackground(background(0xfff3e7d5,0));
                reload.setContentDescription("Перезагрузить фрагмент "+(pos+1));
                reload.setFocusable(false);
                reload.setOnClickListener(v->confirmRedownload(c,pos+1));
                LinearLayout.LayoutParams reloadLp=new LinearLayout.LayoutParams(dp(43),dp(43));
                reloadLp.leftMargin=dp(8);
                header.addView(reload,reloadLp);
                ImageButton delete=new ImageButton(EdgeQueueActivity.this);
                delete.setImageResource(android.R.drawable.ic_menu_delete);
                delete.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                delete.setPadding(dp(9),dp(9),dp(9),dp(9));
                delete.setBackground(background(0xfff8e8e4,0));
                delete.setContentDescription("Удалить аудио фрагмента "+(pos+1));
                delete.setFocusable(false);
                delete.setOnClickListener(v->confirmDelete(c,pos+1));
                LinearLayout.LayoutParams deleteLp=new LinearLayout.LayoutParams(dp(43),dp(43));
                deleteLp.leftMargin=dp(6);
                header.addView(delete,deleteLp);
                row.addView(header,new LinearLayout.LayoutParams(-1,-2));
                TextView size=new TextView(EdgeQueueActivity.this);size.setTextColor(c.played?0xff999999:MUTED);size.setTextSize(12);
                long bytes=downloading?download.progressBytes():c.bytes;int chunks=downloading?download.progressChunks():c.chunks;
                size.setText(c.text.length()+" символов · "+chunks+" чанков · "+String.format(Locale.ROOT,"%.1f",bytes/1024.0)+" КБ");row.addView(size);
                if(downloading){
                    android.widget.ProgressBar progress=new android.widget.ProgressBar(EdgeQueueActivity.this,null,android.R.attr.progressBarStyleHorizontal);
                    progress.setIndeterminate(true);row.addView(progress,new LinearLayout.LayoutParams(-1,dp(6)));
                }
                return row;
            }
        });
        LinearLayout.LayoutParams listLp=new LinearLayout.LayoutParams(-1,0,1);listLp.topMargin=dp(10);root.addView(list,listLp);
        list.setOnItemClickListener((p,v,index,id)->{
            EdgeDownloadService.Clip c=clips.get(index);
            if(!c.ready()||download==null||player==null){Toast.makeText(this,"Дождитесь готовности аудиофайла",Toast.LENGTH_SHORT).show();return;}
            // Explicit user choice is a playback command. Download queue cannot start audio itself.
            download.setAnchor(c.start,true);
            player.play();
            finish();
        });
        Button clear=action("Очистить список и файлы");clear.setContentDescription("Удалить все MP3 и очередь после подтверждения");
        clear.setTextColor(0xffba322d);clear.setBackground(background(0xfffff9f0,0xffd44b44));
        clear.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_clear_audio,0,0,0);
        clear.setOnClickListener(v->confirmClear());LinearLayout.LayoutParams clearLp=new LinearLayout.LayoutParams(-1,dp(48));clearLp.topMargin=dp(12);root.addView(clear,clearLp);
    }
    private void refresh(){
        if(download==null){if(subtitle!=null)subtitle.setText("Подключение к фоновой очереди…");return;}
        clips=download.snapshot();
        if(subtitle!=null)subtitle.setText("Готово: "+download.countReady()+" · всего: "+clips.size()
                +(download.error().isEmpty()?"":"\n"+download.error())
                +(player==null||player.playbackError().isEmpty()?"":"\n"+player.playbackError()));
        if(autoload!=null)autoload.setText(download.isActive()?"Приостановить автоподгрузку":"Включить автоподгрузку");
        if(list!=null)((BaseAdapter)list.getAdapter()).notifyDataSetChanged();
    }
    private void toggle(){if(download==null)return;
        if(download.isActive()) download.setActive(false);
        else {Intent i=new Intent(this,EdgeDownloadService.class).setAction(EdgeDownloadService.ACTION_BEGIN)
                .putExtra(EdgeDownloadService.EXTRA_URI,download.bookUri()).putExtra(EdgeDownloadService.EXTRA_ANCHOR,download.anchor());
            androidx.core.content.ContextCompat.startForegroundService(this,i);}
        refresh();
    }
    private void confirmRedownload(EdgeDownloadService.Clip clip,int ordinal) {
        if(download==null)return;
        new AlertDialog.Builder(this)
                .setTitle("Перезагрузить фрагмент №"+ordinal+"?")
                .setMessage("Будет удалён только аудиофайл этого фрагмента и создан заново. Остальные файлы, книга и история очереди сохранятся. Edge TTS сохраняет MP3 напрямую. При ошибке соединения повторная загрузка может не помочь.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Перезагрузить",(dialog,which)->{
                    if(download==null)return;
                    String uri=download.bookUri();
                    if(!download.isActive()){
                        Intent start=new Intent(this,EdgeDownloadService.class).setAction(EdgeDownloadService.ACTION_BEGIN)
                                .putExtra(EdgeDownloadService.EXTRA_URI,uri)
                                .putExtra(EdgeDownloadService.EXTRA_ANCHOR,download.anchor());
                        androidx.core.content.ContextCompat.startForegroundService(this,start);
                    }
                    boolean accepted=download.redownloadClip(clip.start);
                    if(accepted)Toast.makeText(this,"Фрагмент №"+ordinal+" поставлен на повторную загрузку",Toast.LENGTH_LONG).show();
                    else Toast.makeText(this,download.error(),Toast.LENGTH_LONG).show();
                    refresh();
                }).show();
    }


    private void confirmDelete(EdgeDownloadService.Clip clip,int ordinal) {
        if(download==null)return;
        new AlertDialog.Builder(this)
                .setTitle("Удалить аудио фрагмента №"+ordinal+"?")
                .setMessage("Будет удалён только сохранённый MP3 этого фрагмента. Сам текст, очередь и остальные файлы сохранятся. При включённой автоподгрузке этот фрагмент может быть скачан снова при необходимости.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить",(dialog,which)->{
                    if(download==null)return;
                    boolean accepted=download.deleteClipAudio(clip.start);
                    if(accepted)Toast.makeText(this,"Аудио фрагмента №"+ordinal+" удалено",Toast.LENGTH_LONG).show();
                    else Toast.makeText(this,download.error(),Toast.LENGTH_LONG).show();
                    refresh();
                }).show();
    }

    private void openFolder(){File directory=download==null?null:download.folder();if(directory==null){Toast.makeText(this,"Папка ещё не готова",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,EdgeFileExplorerActivity.class).putExtra("book_folder",directory.getAbsolutePath()));}
    private void confirmClear(){if(download==null)return;
        new AlertDialog.Builder(this).setTitle("Удалить MP3 этой книги?")
                .setMessage("Все аудиофрагменты, очередь и история прослушивания будут удалены. Сам текст книги сохранится.")
                .setNegativeButton("Отмена",null).setPositiveButton("Удалить",(d,w)->download.clearAudio(()->{
                    Toast.makeText(this,"MP3 и очередь очищены",Toast.LENGTH_SHORT).show();refresh();
                })).show();
    }
    @Override protected void onDestroy(){if(download!=null)download.removeObserver(observer);
        if(downloadBound)unbindService(queueConnection);if(playerBound)unbindService(playerConnection);super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
