package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.Locale;
import java.util.List;
import java.util.Collections;

/**
 * UI ONLY. No WebView or download scheduling lives in the playback Activity.
 * Media buttons -> EdgePlaybackService. Autoload -> EdgeDownloadService.
 * The download service observes playback position; never Play/Pause/Stop events.
 */
public final class EdgeOnlineActivity extends Activity {
    private static final int BG=0xfff3e7d5,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949;
    private int primaryColor(){return ReaderButtonColors.accent(this,ACCENT);}
    private int neutralColor(){return ReaderButtonColors.neutral(this,0xfffff9f0);}
    private int primaryInk(){return ReaderButtonColors.contrastingText(primaryColor());}
    private int neutralInk(){return ReaderButtonColors.contrastingText(neutralColor());}
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){refresh();main.postDelayed(this,500);}};
    private final Runnable wordTicker=new Runnable(){@Override public void run(){updateWordHighlight();main.postDelayed(this,85);}};
    private String currentTimingKey="";
    private long currentTimingStamp=-1;
    private List<EdgeWordTimings.Word> timedWords=Collections.emptyList();
    private int lastHighlightedWord=-2;
    private String bookUri="";
    private String coverLoadedFor="";
    private EdgePlaybackService playback;
    private EdgeDownloadService downloads;
    private boolean playerBound,downloaderBound,dragging;
    private TextView progressText,queueStatus,fileLabel,fileTime,fragmentText,downloadStatus,bookTitleView;
    private Button voiceChoice,methodChoice;
    private boolean askedOffline=false;
    private String previousFragment="";
    private ImageView playIcon;
    private TextView speedValue, sleepValue;
    private SeekBar seek;
    private ScrollView textScroller;
    private ImageView coverImage;
    private TextView coverPlaceholder;
    private final EdgeDownloadService.Observer queueObserver=this::refresh;
    private final EdgePlaybackService.Listener playbackObserver=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean completedPrevious){refresh();}
        @Override public void onPlaybackStateChanged(){refresh();}
    };
    private final ServiceConnection playerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            playback=((EdgePlaybackService.LocalBinder)binder).getService();
            playback.prepareBook("edge:"+bookUri);
            playback.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(EdgeOnlineActivity.this)));
            refresh();
            playback.addListener(playbackObserver);
        }
        @Override public void onServiceDisconnected(ComponentName name){playback=null;refresh();}
    };
    private final ServiceConnection queueConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            downloads=((EdgeDownloadService.LocalBinder)binder).getService();
            int word=Math.max(0,getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+bookUri,0)));
            downloads.openBook(bookUri,word);
            downloads.setActive(true); // automatic, always on for the playback window
            downloads.addObserver(queueObserver);
            refresh();
            loadCover();
        }
        @Override public void onServiceDisconnected(ComponentName name){downloads=null;refresh();}
    };
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable rounded(int fill,int radius){GradientDrawable gd=new GradientDrawable();gd.setColor(fill);gd.setCornerRadius(dp(radius));return gd;}
    private TextView text(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextColor(INK);t.setTextSize(size);return t;}
    /** Icon-only controls are FrameLayouts so no TextView font baseline can offset them. */
    private FrameLayout iconButton(int icon,String hint,boolean primary,boolean speed){
        FrameLayout box=new FrameLayout(this);
        box.setBackground(rounded(primary?primaryColor():neutralColor(),15));
        box.setContentDescription(hint);box.setClickable(true);box.setFocusable(true);
        if(speed || icon==R.drawable.ic_audio_timer){
            LinearLayout group=new LinearLayout(this);group.setOrientation(LinearLayout.VERTICAL);
            group.setGravity(Gravity.CENTER);
            ImageView graphic=new ImageView(this);graphic.setImageResource(icon);
            graphic.setColorFilter(neutralInk());graphic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            group.addView(graphic,new LinearLayout.LayoutParams(dp(21),dp(21)));
            TextView counter=text(speed?"1×":"",11);counter.setGravity(Gravity.CENTER);
            if(speed)speedValue=counter;else {sleepValue=counter;counter.setVisibility(View.GONE);}
            group.addView(counter,new LinearLayout.LayoutParams(-1,dp(16)));
            box.addView(group,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        }else{
            ImageView graphic=new ImageView(this);graphic.setImageResource(icon);
            graphic.setColorFilter(primary?primaryInk():neutralInk());
            graphic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            box.addView(graphic,new FrameLayout.LayoutParams(dp(primary?27:24),dp(primary?27:24),Gravity.CENTER));
            if(primary)playIcon=graphic;
        }
        return box;
    }
    private Button captionButton(String value,int icon,boolean primary){
        Button b=new Button(this);b.setAllCaps(false);b.setText(value);b.setTextSize(12);b.setTextColor(primary?primaryInk():neutralInk());
        b.setBackground(rounded(primary?primaryColor():neutralColor(),14));b.setPadding(dp(8),0,dp(8),0);
        b.setGravity(Gravity.CENTER);
        b.setCompoundDrawablePadding(dp(7));
        b.setCompoundDrawablesWithIntrinsicBounds(icon,0,0,0);
        for(android.graphics.drawable.Drawable d:b.getCompoundDrawables())if(d!=null)d.setTint(primary?primaryInk():neutralInk());
        return b;
    }
    private void startDownload(){
        if(downloads==null||!downloads.isReady()){
            Toast.makeText(this,"Подождите загрузки текста книги",Toast.LENGTH_SHORT).show();return;
        }
        if(downloads.isActive()){downloads.setActive(false);return;}
        Intent i=new Intent(this,EdgeDownloadService.class).setAction(EdgeDownloadService.ACTION_BEGIN)
                .putExtra(EdgeDownloadService.EXTRA_URI,bookUri).putExtra(EdgeDownloadService.EXTRA_ANCHOR,downloads.anchor());
        try{ContextCompat.startForegroundService(this,i);}
        catch(Exception e){Toast.makeText(this,"Невозможно запустить фоновую загрузку: "+e.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        bookUri=getIntent().getStringExtra("book_uri");
        getSharedPreferences("reader",0).edit().putString("last_media_kind","text").apply();
        if(bookUri==null||bookUri.isEmpty())bookUri=getSharedPreferences("reader",0).getString("uri","");
        if(AudioPlaybackService.instance!=null){
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
        }
        buildUi();
        // Always begin filling the playback window automatically; no manual prefetch toggle.
        Intent download=new Intent(this,EdgeDownloadService.class).setAction(EdgeDownloadService.ACTION_BEGIN)
            .putExtra(EdgeDownloadService.EXTRA_URI,bookUri)
            .putExtra(EdgeDownloadService.EXTRA_ANCHOR,Math.max(0,getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+bookUri,0))));
        try{ContextCompat.startForegroundService(this,download);}
        catch(Exception ex){Toast.makeText(this,"Автоподготовка ограничена Android: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        playerBound=bindService(new Intent(this,EdgePlaybackService.class),playerConnection,BIND_AUTO_CREATE);
        downloaderBound=bindService(new Intent(this,EdgeDownloadService.class),queueConnection,BIND_AUTO_CREATE);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1123);
        loadCover();
    }
    private void buildUi(){
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(BG);
        screen.setPadding(dp(12),dp(22),dp(12),dp(10));setContentView(screen);

        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);screen.addView(top,new LinearLayout.LayoutParams(-1,dp(50)));
        FrameLayout back=iconButton(R.drawable.ic_back,"Назад",false,false);back.setOnClickListener(v->BackNavigation.back(this));top.addView(back,new LinearLayout.LayoutParams(dp(46),dp(46)));
        // Leave the title area empty: this screen is identified by the book artwork and controls.
        View spacer=new View(this);
        top.addView(spacer,new LinearLayout.LayoutParams(0,dp(45),1));
        int[] toolbarIcons={R.drawable.ic_list_audio,R.drawable.ic_folder_audio,R.drawable.ic_audio_speed,R.drawable.ic_audio_timer,R.drawable.ic_settings};
        String[] toolbarHints={"Список очереди","Папка аудио","Скорость воспроизведения","Таймер сна","Настройки Онлайн"};
        for(int i=0;i<toolbarIcons.length;i++){
            final int index=i;
            FrameLayout btn=iconButton(toolbarIcons[i],toolbarHints[i],false,i==2);
            btn.setOnClickListener(v->{
                switch(index){
                    case 0:startActivity(new Intent(this,EdgeQueueActivity.class).putExtra(EdgeDownloadService.EXTRA_URI,bookUri));break;
                    case 1:openFolder();break;
                    case 2:showSpeedMenu();break;
                    case 3:showSleepMenu();break;
                    default:startActivity(new Intent(this,SettingsActivity.class).putExtra("online_tab",true));break;
                }
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(46),dp(46));
            if(i>0)lp.leftMargin=dp(5);
            top.addView(btn,lp);
        }

        LinearLayout quickRow=new LinearLayout(this);quickRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams quickLp=new LinearLayout.LayoutParams(-1,dp(44));quickLp.topMargin=dp(8);screen.addView(quickRow,quickLp);
        methodChoice=captionButton("Edge ▾",R.drawable.ic_tts_engine,true);
        methodChoice.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Способ озвучивания")
            .setItems(new String[]{"Системный TTS · локальные голоса","Microsoft Edge · онлайн"},(d,n)->{
                if(n==0){if(downloads!=null)downloads.setActive(false);startActivity(new Intent(this,AudioPlayerActivity.class)
                    .putExtra("book_uri",bookUri).putExtra("start_tts",true)
                    .putExtra("word_index",Math.max(0,downloads==null?0:downloads.anchor())));}
            }).setNegativeButton("Отмена",null).show());
        quickRow.addView(methodChoice,new LinearLayout.LayoutParams(0,-1,1.2f));
        voiceChoice=captionButton("Голос ▾",R.drawable.ic_microphone,false);
        voiceChoice.setOnClickListener(v->chooseVoice());
        LinearLayout.LayoutParams voiceLp=new LinearLayout.LayoutParams(0,-1,1);voiceLp.leftMargin=dp(7);quickRow.addView(voiceChoice,voiceLp);

        LinearLayout bookCard=new LinearLayout(this);bookCard.setOrientation(LinearLayout.HORIZONTAL);bookCard.setBackground(rounded(0xfffff9f0,18));
        bookCard.setPadding(dp(12),dp(12),dp(12),dp(12));LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,dp(142));cardLp.topMargin=dp(10);screen.addView(bookCard,cardLp);
        FrameLayout coverFrame=new FrameLayout(this);coverFrame.setBackground(rounded(0xff4a352b,16));bookCard.addView(coverFrame,new LinearLayout.LayoutParams(dp(92),-1));
        coverPlaceholder=text("Книга",14);coverPlaceholder.setTextColor(0xfff7e5c7);coverPlaceholder.setGravity(Gravity.CENTER);coverPlaceholder.setTypeface(Typeface.SERIF,Typeface.BOLD);
        coverPlaceholder.setPadding(dp(8),dp(8),dp(8),dp(8));coverFrame.addView(coverPlaceholder,new FrameLayout.LayoutParams(-1,-1));
        coverImage=new ImageView(this);coverImage.setScaleType(ImageView.ScaleType.CENTER_CROP);coverImage.setVisibility(View.GONE);coverFrame.addView(coverImage,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout bookInfo=new LinearLayout(this);bookInfo.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(0,-1,1);infoLp.leftMargin=dp(12);bookCard.addView(bookInfo,infoLp);
        bookTitleView=text("Загрузка книги…",18);bookTitleView.setTypeface(Typeface.SERIF,Typeface.BOLD);bookTitleView.setMaxLines(3);bookInfo.addView(bookTitleView);
        progressText=text("Прочитано: 0%",13);progressText.setPadding(0,dp(8),0,dp(2));bookInfo.addView(progressText);
        queueStatus=text("Ожидание готовых фрагментов",12);queueStatus.setTextColor(MUTED);bookInfo.addView(queueStatus);

        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,dp(60));controlsLp.topMargin=dp(10);screen.addView(controls,controlsLp);
        int[] icons={R.drawable.ic_prev_chapter,R.drawable.ic_rewind_10s,R.drawable.ic_play,R.drawable.ic_forward_10s,R.drawable.ic_next_chapter};
        String[] hints={"Предыдущий фрагмент","Назад на 10 секунд","Play / Pause","Вперёд на 10 секунд","Следующий фрагмент"};
        for(int i=0;i<icons.length;i++){
            final int index=i;FrameLayout b=iconButton(icons[i],hints[i],i==2,false);
            b.setOnClickListener(v->{
                if(playback==null)return;
                switch(index){
                    case 0:playback.previous();break;
                    case 1:playback.rewind10();break;
                    case 2:if(playback.isPlaying())playback.pause();else{startService(new Intent(this,EdgePlaybackService.class));playback.play();}break;
                    case 3:playback.forward10();break;
                    case 4:playback.next();break;
                }
                refresh();
            });
            FrameLayout slot=new FrameLayout(this);
            slot.addView(b,new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.CENTER));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(55),i==2?1.17f:1);
            if(i!=0)lp.leftMargin=dp(4);controls.addView(slot,lp);

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

        textScroller=new ScrollView(this);textScroller.setFillViewport(true);textScroller.setBackground(rounded(0xfffff9f0,18));
        LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(-1,0,1);textLp.topMargin=dp(10);screen.addView(textScroller,textLp);
        fragmentText=text("Ожидание книги и аудиофрагментов…",19);fragmentText.setTypeface(Typeface.SERIF);fragmentText.setLineSpacing(dp(5),1.1f);
        fragmentText.setPadding(dp(18),dp(15),dp(18),dp(15));fragmentText.setTextIsSelectable(false);textScroller.addView(fragmentText);
        downloadStatus=text("Ожидание подключения",12);downloadStatus.setTextColor(MUTED);
        downloadStatus.setPadding(dp(5),dp(7),dp(5),dp(6));screen.addView(downloadStatus);
    }
    private String speedLabel(float speed){
        String value=String.format(Locale.US,"%.2f",speed);
        if(value.endsWith("00"))value=value.substring(0,value.length()-3);
        else if(value.endsWith("0"))value=value.substring(0,value.length()-1);
        return value+"×";
    }
    private String format(long ms){long t=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%02d:%02d",t/60,t%60);}
    private void openFolder(){File directory=downloads==null?null:downloads.folder();if(directory==null){Toast.makeText(this,"Папка ещё не готова",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,EdgeFileExplorerActivity.class).putExtra("book_folder",directory.getAbsolutePath()));}
    private void chooseVoice(){
        if(downloads==null)return;
        String[] voices={"Светлана · ru-RU", "Дмитрий · ru-RU"};
        String[] keys={"ru-RU-SvetlanaNeural","ru-RU-DmitryNeural"};
        new AlertDialog.Builder(this)
            .setTitle("Смена голоса")
            .setMessage("Смена голоса заменит уже скачанные аудиофайлы новыми фрагментами. Продолжить?")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Продолжить",(confirm,which)->new AlertDialog.Builder(this)
                .setTitle("Голос")
                .setItems(voices,(dialog,index)->{downloads.choose(keys[index],null);refresh();})
                .setNegativeButton("Отмена",null)
                .show())
            .show();
    }
    private void showSpeedMenu(){
        if(playback==null){Toast.makeText(this,"Плеер ещё подключается",Toast.LENGTH_SHORT).show();return;}
        TtsWpm.dialog(this,()->{playback.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(this)));refresh();});
    }
    private void showSleepMenu(){
        if(playback==null){Toast.makeText(this,"Плеер ещё подключается",Toast.LENGTH_SHORT).show();return;}
        final int[] minutes={0,5,10,15,30,60};
        String[] labels={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};
        new AlertDialog.Builder(this).setTitle("Таймер сна")
                .setItems(labels,(d,which)->{playback.setSleepMinutes(minutes[which]);refresh();Toast.makeText(this,"Таймер сохранён",Toast.LENGTH_SHORT).show();})
                .setNegativeButton("Отмена",null).show();
    }
    private void loadCover(){
        if(bookUri==null||bookUri.isEmpty()||bookUri.equals(coverLoadedFor))return;
        coverLoadedFor=bookUri;
        coverImage.setVisibility(View.GONE);
        new Thread(()->{
            try{
                byte[] bytes=CoverLoader.get(getApplicationContext(),bookUri);
                final android.graphics.Bitmap bitmap=bytes==null?null:android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                main.post(()->{
                    if(isFinishing())return;
                    if(bitmap!=null){coverImage.setImageBitmap(bitmap);coverImage.setVisibility(View.VISIBLE);}else coverImage.setVisibility(View.GONE);
                });
            }catch(Exception ignored){main.post(()->coverImage.setVisibility(View.GONE));}
        }).start();
    }
    private boolean networkAvailable(){
        try{android.net.ConnectivityManager cm=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            android.net.Network n=cm.getActiveNetwork();android.net.NetworkCapabilities cap=cm.getNetworkCapabilities(n);
            return cap!=null&&cap.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }catch(Exception ignored){return true;}
    }
    private void refresh(){
        if(progressText==null)return;
        if(playback!=null){
            if(speedValue!=null)speedValue.setText(String.valueOf(TtsWpm.get(this)));
            if(sleepValue!=null){long ms=playback.sleepRemainingMs();
                sleepValue.setText(ms<=0?"":ms<60000?Math.max(1,ms/1000)+"с":(ms+59999)/60000+"м");
                sleepValue.setVisibility(ms>0?View.VISIBLE:View.GONE);
            }
            int index=playback.trackIndex();
            fileLabel.setText("Фрагмент №"+(downloads!=null?Math.max(1,downloads.currentOrdinal()):Math.max(1,index+1)));
            long duration=playback.durationMs(),position=playback.positionMs();
            if(!dragging){fileTime.setText(format(position)+" / "+format(duration));seek.setProgress(duration>0?(int)Math.min(1000,position*1000/duration):0);}
            if(playIcon!=null)playIcon.setImageResource(playback.isPlaying()?R.drawable.ic_pause:R.drawable.ic_play);
        }
        if(playback==null&&sleepValue!=null){sleepValue.setText("");sleepValue.setVisibility(View.GONE);}
        if(downloads==null){downloadStatus.setText("Подключение фоновой очереди…");return;}
        loadCover();
        String bookTitle=downloads.title();
        bookTitleView.setText(bookTitle);
        coverPlaceholder.setText(bookTitle.replace(' ','\n'));
        int n=downloads.countReady();int current=downloads.currentOrdinal();
        if(n==0){queueStatus.setTextColor(MUTED);queueStatus.setText("Ожидание готовых фрагментов");}
        else {queueStatus.setTextColor(0xff305c35);queueStatus.setText("Фрагменты: "+Math.max(1,current)+"/"+n);}
        int words=downloads.words(),anchor=downloads.anchor();
        int percent=words>0?(int)Math.min(100,100L*(anchor+1)/words):0;
        progressText.setText("Прочитано: "+percent+"% · слово "+(anchor+1)+" из "+words);

        updateWordHighlight();
        String selected=downloads.voice();
        voiceChoice.setText(selected.contains("Dmitry")?"Дмитрий ▾":"Светлана ▾");
        String state=downloads.generatingStart()>=0?"Скачивается: "+downloads.progressChunks()+" чанков · "+(downloads.progressBytes()/1024)+" КБ"
                :"Автоматическая подготовка следующих фрагментов";
        if(playback!=null){
            if(playback.playbackSpeed()!=1f)state+=" · "+TtsWpm.get(this)+" слов/мин";
            long sleep=playback.sleepRemainingMs();
            if(sleep>0)state+=" · таймер сна "+Math.max(1,(sleep+59999)/60000)+" мин";
        }
        if(!askedOffline && downloads.words()>0 && !networkAvailable() && playback!=null && !playback.isPlaying()){
            EdgeDownloadService.Clip candidate=downloads.currentClip();
            if(candidate==null||!candidate.ready()){
                askedOffline=true;
                new AlertDialog.Builder(this).setTitle("Нет подключения к интернету")
                    .setMessage("Сохранённые аудиофрагменты закончились. Продолжить через системный TTS?")
                    .setPositiveButton("Продолжить",(d,w)->startActivity(new Intent(this,AudioPlayerActivity.class)
                        .putExtra("book_uri",bookUri).putExtra("start_tts",true).putExtra("word_index",Math.max(0,downloads.anchor()))))
                    .setNegativeButton("Отмена",null).show();
            }
        }
        EdgeDownloadService.Clip statusClip=downloads.currentClip();
        if(statusClip!=null&&statusClip.ready()&&timedWords.isEmpty())
            state+=" · Старый MP3 без меток слов: для точной подсветки перескачайте фрагмент через очередь";
        if(!downloads.error().isEmpty())state+=" · "+downloads.error();
        if(playback!=null&&!playback.playbackError().isEmpty())state+=" · "+playback.playbackError();
        downloadStatus.setText(state);
    }
    /** Synchronize from the MP3 source-audio clock and real WordBoundary timestamps.
     * No guessed word positions for legacy clips that lack metadata.
     */
    private void updateWordHighlight(){
        if(downloads==null||fragmentText==null)return;
        EdgeDownloadService.Clip clip=downloads.currentClip();
        if(clip==null){
            if(!previousFragment.isEmpty()){
                previousFragment="";currentTimingKey="";timedWords=Collections.emptyList();
                lastHighlightedWord=-2;
            }
            return;
        }
        String text=clip.text;
        if(!text.equals(previousFragment)){
            previousFragment=text;lastHighlightedWord=-2;
            fragmentText.setText(text);
            textScroller.scrollTo(0,0);
        }
        String key=clip.key+":"+clip.start;
        File mp3=clip.file;
        long stamp=mp3==null?-1:EdgeWordTimings.sidecar(mp3).lastModified();
        if(!key.equals(currentTimingKey)||stamp!=currentTimingStamp){
            currentTimingKey=key;currentTimingStamp=stamp;
            timedWords=mp3==null?Collections.emptyList():EdgeWordTimings.read(mp3,text);
            lastHighlightedWord=-2;
        }
        int selected=-1;
        if(playback!=null&&key.equals(playback.currentKey())){
            selected=EdgeWordTimings.at(timedWords,playback.positionMs());
        }
        if(selected==lastHighlightedWord)return;
        lastHighlightedWord=selected;
        android.text.SpannableString styled=new android.text.SpannableString(text);
        if(selected>=0&&selected<timedWords.size()){
            EdgeWordTimings.Word word=timedWords.get(selected);
            styled.setSpan(new android.text.style.BackgroundColorSpan(0xffa9d5d0),
                    word.from,word.to,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        fragmentText.setText(styled);
    }
    @Override protected void onResume(){super.onResume();if(downloads!=null){downloads.refreshSettings();downloads.setActive(true);}
        if(playback!=null){playback.setAutoContinue(true);playback.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(this)));}
        main.post(ticker);main.post(wordTicker);loadCover();}
    @Override protected void onPause(){main.removeCallbacks(ticker);main.removeCallbacks(wordTicker);super.onPause();}
    @Override protected void onDestroy(){if(downloads!=null)downloads.removeObserver(queueObserver);if(playback!=null)playback.removeListener(playbackObserver);
        if(downloaderBound)unbindService(queueConnection);if(playerBound)unbindService(playerConnection);main.removeCallbacks(wordTicker);main.removeCallbacksAndMessages(null);super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
