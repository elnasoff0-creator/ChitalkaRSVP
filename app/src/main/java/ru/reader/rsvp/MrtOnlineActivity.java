package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * UI ONLY. No WebView or download scheduling lives in the playback Activity.
 * Media buttons -> MrtPlaybackService. Autoload -> MrtDownloadService.
 * The download service observes playback position; never Play/Pause/Stop events.
 */
public final class MrtOnlineActivity extends Activity {
    private static final int BG=0xfff3e7d5,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){refresh();main.postDelayed(this,500);}};
    private String bookUri="";
    private MrtPlaybackService playback;
    private MrtDownloadService downloads;
    private boolean playerBound,downloaderBound,dragging;
    private TextView progressText,queueStatus,fileLabel,fileTime,fragmentText,downloadStatus;
    private Button autoloadButton,playButton,voiceChoice,speedChoice,emotionChoice;
    private SeekBar seek;
    private ScrollView textScroller;
    private final MrtDownloadService.Observer queueObserver=this::refresh;
    private final MrtPlaybackService.Listener playbackObserver=new MrtPlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean completedPrevious){refresh();}
        @Override public void onPlaybackStateChanged(){refresh();}
    };
    private final ServiceConnection playerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            playback=((MrtPlaybackService.LocalBinder)binder).getService();
            playback.prepareBook(bookUri);
            playback.addListener(playbackObserver);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name){playback=null;refresh();}
    };
    private final ServiceConnection queueConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            downloads=((MrtDownloadService.LocalBinder)binder).getService();
            int word=Math.max(0,getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+bookUri,0)));
            downloads.openBook(bookUri,word);
            downloads.addObserver(queueObserver);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName name){downloads=null;refresh();}
    };
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable rounded(int fill,int radius){GradientDrawable gd=new GradientDrawable();gd.setColor(fill);gd.setCornerRadius(dp(radius));return gd;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(INK);t.setTextSize(size);return t;}
    private Button button(int icon,String hint,boolean primary){
        Button b=new Button(this);b.setText("");b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);
        b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setBackground(rounded(primary?ACCENT:0xfffff9f0,15));b.setContentDescription(hint);
        android.graphics.drawable.Drawable d=getDrawable(icon);
        if(d!=null){d=d.mutate();d.setTint(primary?Color.WHITE:INK);d.setBounds(0,0,dp(23),dp(23));b.setCompoundDrawables(d,null,null,null);}
        return b;
    }
    private Button captionButton(String value){Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(12);b.setTextColor(INK);
        b.setBackground(rounded(0xfffff9f0,14));b.setPadding(dp(4),0,dp(4),0);return b;}
    private void startDownload(){
        if(downloads==null||!downloads.isReady()){
            Toast.makeText(this,"Дождитесь загрузки текста и движка MRT",Toast.LENGTH_SHORT).show();return;
        }
        if(downloads.isActive()){downloads.setActive(false);return;}
        Intent i=new Intent(this,MrtDownloadService.class).setAction(MrtDownloadService.ACTION_BEGIN)
                .putExtra(MrtDownloadService.EXTRA_URI,bookUri).putExtra(MrtDownloadService.EXTRA_ANCHOR,downloads.anchor());
        try{ContextCompat.startForegroundService(this,i);}
        catch(Exception e){Toast.makeText(this,"Невозможно запустить фоновую загрузку: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        AudioModeCoordinator.stopEdge(this);
        bookUri=getIntent().getStringExtra("book_uri");
        if(bookUri==null||bookUri.isEmpty())bookUri=getSharedPreferences("reader",0).getString("uri","");
        // Only the playback service has MediaSession/AudioFocus.
        if(AudioPlaybackService.instance!=null){
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
        }
        buildUi();
        playerBound=bindService(new Intent(this,MrtPlaybackService.class),playerConnection,BIND_AUTO_CREATE);
        downloaderBound=bindService(new Intent(this,MrtDownloadService.class),queueConnection,BIND_AUTO_CREATE);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1123);
    }
    private void buildUi(){
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(BG);
        screen.setPadding(dp(12),dp(25),dp(12),dp(10));setContentView(screen);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);screen.addView(top,new LinearLayout.LayoutParams(-1,dp(50)));
        Button back=button(R.drawable.ic_back,"Назад",false);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(42),dp(46)));
        TextView title=text("Слушать онлайн · MRT",19);title.setTypeface(Typeface.SERIF,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-1,1);titleLp.leftMargin=dp(8);top.addView(title,titleLp);
        Button queue=button(R.drawable.ic_queue,"Список очереди",false);queue.setOnClickListener(v->startActivity(new Intent(this,MrtDownloadActivity.class)
                .putExtra(MrtDownloadService.EXTRA_URI,bookUri)));top.addView(queue,new LinearLayout.LayoutParams(dp(43),dp(45)));
        Button folder=button(R.drawable.ic_folder_audio,"Папка аудио",false);folder.setOnClickListener(v->openFolder());
        LinearLayout.LayoutParams folderLp=new LinearLayout.LayoutParams(dp(43),dp(45));folderLp.leftMargin=dp(5);top.addView(folder,folderLp);
        Button settings=button(R.drawable.ic_settings,"Настройки Онлайн",false);settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class).putExtra("online_tab",true)));
        LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(43),dp(45));settingsLp.leftMargin=dp(5);top.addView(settings,settingsLp);
        progressText=text("Прочитано: 0%",13);progressText.setPadding(dp(4),dp(12),0,dp(6));screen.addView(progressText);
        LinearLayout loadRow=new LinearLayout(this);loadRow.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(loadRow,new LinearLayout.LayoutParams(-1,dp(44)));
        autoloadButton=captionButton("Автоподгрузка");autoloadButton.setBackground(rounded(ACCENT,15));autoloadButton.setTextColor(Color.WHITE);
        autoloadButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_autoload,0,0,0);
        autoloadButton.setOnClickListener(v->startDownload());loadRow.addView(autoloadButton,new LinearLayout.LayoutParams(0,-1,1.1f));
        queueStatus=text("0/0  запустите автоподгрузку",12);queueStatus.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        LinearLayout.LayoutParams statLp=new LinearLayout.LayoutParams(0,-1,1);statLp.leftMargin=dp(9);loadRow.addView(queueStatus,statLp);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,dp(60));controlsLp.topMargin=dp(9);screen.addView(controls,controlsLp);
        int[] icons={R.drawable.ic_prev_chapter,R.drawable.ic_rewind_10s,R.drawable.ic_play,R.drawable.ic_forward_10s,R.drawable.ic_next_chapter};
        String[] hints={"Предыдущий MP3","Назад на 10 секунд","Play / Pause","Вперёд на 10 секунд","Следующий MP3"};
        for(int i=0;i<icons.length;i++){
            final int index=i;Button b=button(icons[i],hints[i],i==2);
            b.setOnClickListener(v->{
                if(playback==null)return;
                switch(index){case 0:playback.previous();break;case 1:playback.rewind10();break;
                    case 2:if(playback.isPlaying())playback.pause();else{
                        startService(new Intent(this,MrtPlaybackService.class));playback.play();
                    }break;case 3:playback.forward10();break;case 4:playback.next();break;}
                refresh();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(55),i==2?1.17f:1);
            if(i!=0)lp.leftMargin=dp(6);controls.addView(b,lp);
            if(i==2)playButton=b;
        }
        LinearLayout track=new LinearLayout(this);track.setOrientation(LinearLayout.VERTICAL);track.setPadding(dp(10),dp(8),dp(10),dp(8));
        track.setBackground(rounded(0xfffff9f0,15));LinearLayout.LayoutParams trackLp=new LinearLayout.LayoutParams(-1,-2);
        trackLp.topMargin=dp(9);screen.addView(track,trackLp);
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);track.addView(line);
        fileLabel=text("Фрагмент №—",14);line.addView(fileLabel,new LinearLayout.LayoutParams(0,-2,1));
        fileTime=text("00:00 / 00:00",12);fileTime.setTextColor(MUTED);line.addView(fileTime);
        seek=new SeekBar(this);seek.setMax(1000);track.addView(seek,new LinearLayout.LayoutParams(-1,dp(32)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser&&playback!=null){
                long d=playback.durationMs();fileTime.setText(format(d*value/1000)+" / "+format(d));}}
            @Override public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            @Override public void onStopTrackingTouch(SeekBar bar){dragging=false;if(playback!=null)playback.seekWithin(playback.durationMs()*bar.getProgress()/1000);}
        });
        LinearLayout selections=new LinearLayout(this);selections.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams selectLp=new LinearLayout.LayoutParams(-1,dp(48));selectLp.topMargin=dp(10);screen.addView(selections,selectLp);
        voiceChoice=captionButton("Голос ▾");speedChoice=captionButton("Скорость ▾");emotionChoice=captionButton("Эмоция ▾");
        Button[] controls2={voiceChoice,speedChoice,emotionChoice};String[] types={"voice","speed","emotion"};
        for(int i=0;i<3;i++){final String kind=types[i];LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,i==0?1.7f:1f);
            if(i!=0)lp.leftMargin=dp(6);selections.addView(controls2[i],lp);controls2[i].setOnClickListener(v->choose(kind));}
        textScroller=new ScrollView(this);textScroller.setFillViewport(true);textScroller.setBackground(rounded(0xfffff9f0,18));
        LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(-1,0,1);textLp.topMargin=dp(10);screen.addView(textScroller,textLp);
        fragmentText=text("Ожидание книги и аудиофрагментов…",19);fragmentText.setTypeface(Typeface.SERIF);fragmentText.setLineSpacing(dp(5),1.1f);
        fragmentText.setPadding(dp(18),dp(15),dp(18),dp(15));fragmentText.setTextIsSelectable(false);textScroller.addView(fragmentText);
        downloadStatus=text("Загрузчик ещё не подключён",12);downloadStatus.setTextColor(MUTED);
        downloadStatus.setPadding(dp(5),dp(7),dp(5),dp(6));screen.addView(downloadStatus);
    }
    private String format(long ms){long t=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",t/60,t%60);}
    private void openFolder(){File directory=downloads==null?null:downloads.folder();if(directory==null){Toast.makeText(this,"Папка ещё не готова",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,MrtFileExplorerActivity.class).putExtra("book_folder",directory.getAbsolutePath()));}
    private void choose(String kind){
        if(downloads==null)return;
        LinkedHashMap<String,String> map=downloads.options(kind);
        if(map.isEmpty()){Toast.makeText(this,"Ожидание настроек MRT",Toast.LENGTH_SHORT).show();return;}
        String[] keys=map.keySet().toArray(new String[0]);String[] labels=map.values().toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("voice".equals(kind)?"Голос":"speed".equals(kind)?"Скорость":"Эмоция")
                .setItems(labels,(dialog,index)->{downloads.selectOption(kind,keys[index]);refresh();})
                .setNegativeButton("Отмена",null).show();
    }
    private String chosen(String kind,String fallback){
        if(downloads==null)return fallback;
        SharedPreferences pref=getSharedPreferences("reader",0);
        String v=pref.getString("mrt_selected_"+kind,"");
        LinkedHashMap<String,String> m=downloads.options(kind);
        String label=m.get(v);return label==null||label.isEmpty()?fallback:label+" ▾";
    }
    private void refresh(){
        if(progressText==null)return;
        if(playback!=null){
            int index=playback.trackIndex();
            fileLabel.setText("Фрагмент №"+(downloads!=null?Math.max(1,downloads.currentOrdinal()):Math.max(1,index+1)));
            long duration=playback.durationMs(),position=playback.positionMs();
            if(!dragging){fileTime.setText(format(position)+" / "+format(duration));seek.setProgress(duration>0?(int)Math.min(1000,position*1000/duration):0);}
            android.graphics.drawable.Drawable img=getDrawable(playback.isPlaying()?R.drawable.ic_pause:R.drawable.ic_play);
            if(img!=null){img=img.mutate();img.setTint(Color.WHITE);img.setBounds(0,0,dp(23),dp(23));playButton.setCompoundDrawables(img,null,null,null);}
        }
        if(downloads==null){downloadStatus.setText("Подключение фоновой очереди…");return;}
        int n=downloads.countReady();int current=downloads.currentOrdinal();
        if(n==0)queueStatus.setText(android.text.Html.fromHtml("<font color='#30221E'>0/0</font> <font color='#b53930'>запустите автоподгрузку</font>",android.text.Html.FROM_HTML_MODE_LEGACY));
        else {queueStatus.setTextColor(0xff305c35);queueStatus.setText(Math.max(1,current)+"/"+n);}
        int words=downloads.words(),anchor=downloads.anchor();
        int percent=words>0?(int)Math.min(100,100L*(anchor+1)/words):0;
        progressText.setText("Прочитано: "+percent+"% · слово "+(anchor+1)+" из "+words);
        autoloadButton.setText(downloads.isActive()?"Остановить загрузку":"Автоподгрузка");
        MrtDownloadService.Clip clip=downloads.currentClip();
        if(clip!=null){String t=clip.text;if(!t.contentEquals(fragmentText.getText())){fragmentText.setText(t);textScroller.scrollTo(0,0);}}
        voiceChoice.setText(chosen("voice","Голос ▾"));speedChoice.setText(chosen("speed","Скорость ▾"));emotionChoice.setText(chosen("emotion","Эмоция ▾"));
        String state=downloads.generatingStart()>=0?"Скачивается: "+downloads.progressChunks()+" чанков · "+(downloads.progressBytes()/1024)+" КБ"
                :downloads.isActive()?"Автоподгрузка ожидает необходимости в новых MP3":"Автоподгрузка выключена";
        if(!downloads.error().isEmpty())state+=" · "+downloads.error();
        if(playback!=null&&!playback.playbackError().isEmpty())state+=" · "+playback.playbackError();
        downloadStatus.setText(state);
    }
    @Override protected void onResume(){super.onResume();if(downloads!=null)downloads.refreshSettings();if(playback!=null)playback.setAutoContinue(getSharedPreferences("reader",0).getBoolean("mrt_auto_continue",true));main.post(ticker);}
    @Override protected void onPause(){main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){if(downloads!=null)downloads.removeObserver(queueObserver);if(playback!=null)playback.removeListener(playbackObserver);
        if(downloaderBound)unbindService(queueConnection);if(playerBound)unbindService(playerConnection);main.removeCallbacksAndMessages(null);super.onDestroy();}
}
