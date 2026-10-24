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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Set;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** One cursor-style read-aloud screen for on-device TTS and online Edge TTS.
 * The text, not a fragment playlist, is the navigation source of truth.
 */
public final class EdgeOnlineActivity extends Activity {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){refresh();ui.postDelayed(this,180);}};
    private boolean local;
    private String uri="",full="",name="Книга";
    private ArrayList<Integer> wordStarts=new ArrayList<>();
    private ArrayList<SentenceChunks.Chunk> sentences=new ArrayList<>();
    private EdgeDownloadService downloads;
    private EdgePlaybackService edge;
    private boolean boundDownload,boundPlayer;
    private int background,foreground,accent;
    private LinearLayout root;
    private ScrollView scroller;
    private TextView reading,bookTitle,progress,offlineProgress,speed;
    private ImageButton modeButton,downloadsButton;
    private TextView sleepRemaining;
    private View sleepControl;
    private FrameLayout playbackBar;
    private boolean switchShouldPlay=false;
    private int switchedWord=-1;
    private int styledButtonColor=Integer.MIN_VALUE,styledAccentColor=Integer.MIN_VALUE;
    private ImageView playIcon;
    private String displayedText="";
    private int windowStart=-1,windowEnd=-1,lastStart=-1,lastEnd=-1;
    private int lastSentenceStart=-1,lastSentenceEnd=-1;
    private int selectedWord=-1;
    private int requestedSeekWord=-1;
    private String requestedSeekKey="";
    private int cachedCoverage=-1;
    private long nextCoverageAt=0;
    private long nextWordSaveAt=0;
    private boolean userTouch=false,offlinePrompt=false;
    private float fingerX,fingerY;
    private final EdgeDownloadService.Observer downloadObserver=this::refresh;
    private final EdgePlaybackService.Listener edgeObserver=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean done){refresh();}
        @Override public void onPlaybackStateChanged(){refresh();}
    };
    private final ServiceConnection downloadConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder binder){
            if(local)return;
            downloads=((EdgeDownloadService.LocalBinder)binder).getService();
            int word=switchedWord>=0?switchedWord:getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+uri,0));
            downloads.openBook(uri,word);
            if(downloads.isReady())downloads.setAnchor(word,true);
            downloads.setActive(true);
            downloads.addObserver(downloadObserver);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName n){downloads=null;}
    };
    private final ServiceConnection playerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder binder){
            if(local)return;
            edge=((EdgePlaybackService.LocalBinder)binder).getService();
            edge.prepareBook("edge:"+uri);
            edge.restoreSharedSleepTimer();
            edge.setAutoContinue(true);
            edge.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(EdgeOnlineActivity.this)));
            edge.addListener(edgeObserver);
            if(switchShouldPlay){switchShouldPlay=false;edge.play();}
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName n){edge=null;}
    };
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}
    private GradientDrawable bg(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
    private TextView label(String value,int size){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(foreground);return t;}
    private Button action(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);return b;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SharedPreferences prefs=getSharedPreferences("reader",0);
        background=prefs.getInt("bg",0xfff3e7d5);
        foreground=prefs.getInt("fg",0xff30221e);
        accent=prefs.getInt("cursor_background",0xff96583f);
        local=getIntent().getBooleanExtra("tts_mode_local",prefs.getBoolean("tts_last_local",true));
        uri=getIntent().getStringExtra("book_uri");if(uri==null||uri.isEmpty())uri=prefs.getString("uri","");
        if(uri.isEmpty()){Toast.makeText(this,"Не выбрана книга",Toast.LENGTH_LONG).show();finish();return;}
        prefs.edit().putString("last_media_kind","text").apply();
        createUi();
        if(local)readBook();
        if(local){
            AudioModeCoordinator.stopEdge(this);
            Intent localStart=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS)
                .putExtra("bookUri",uri)
                .putExtra("word_index",getIntent().getIntExtra("word_index",prefs.getInt("tts_word:"+uri,0)));
            startService(localStart);
        }else{
            if(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode())
                startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
            int start=getIntent().getIntExtra("word_index",prefs.getInt("tts_word:"+uri,0));
            Intent job=new Intent(this,EdgeDownloadService.class).setAction(EdgeDownloadService.ACTION_BEGIN)
                .putExtra(EdgeDownloadService.EXTRA_URI,uri).putExtra(EdgeDownloadService.EXTRA_ANCHOR,start);
            try{ContextCompat.startForegroundService(this,job);}catch(Exception e){Toast.makeText(this,"Загрузка ограничена: "+e.getMessage(),Toast.LENGTH_SHORT).show();}
            boundPlayer=bindService(new Intent(this,EdgePlaybackService.class),playerConnection,BIND_AUTO_CREATE);
            boundDownload=bindService(new Intent(this,EdgeDownloadService.class),downloadConnection,BIND_AUTO_CREATE);
        }
        ui.post(ticker);
    }
    private void readBook(){
        final String requested=uri;
        new Thread(()->{
            try{
                BookLoader.Book book=BookLoader.load(this,android.net.Uri.parse(requested));
                final String body=book.text==null?"":book.text;
                final String title=book.title==null?"Книга":book.title;
                final ArrayList<Integer> offsets=SentenceChunks.wordPositions(body);
                ui.post(()->{
                    if(!requested.equals(uri)||isFinishing())return;
                    full=body;wordStarts=offsets;sentences=SentenceChunks.split(body);name=title;bookTitle.setText(name);
                    int from=getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+uri,0));
                    selectedWord=Math.max(0,Math.min(Math.max(0,wordStarts.size()-1),from));
                    showWindow(wordChar(selectedWord),true);
                    refresh();
                });
            }catch(Exception e){ui.post(()->{if(!isFinishing())Toast.makeText(this,"Ошибка чтения книги: "+e.getMessage(),Toast.LENGTH_LONG).show();});}
        },"tts-cursor-book").start();
    }
    private ImageButton toolbarIcon(int resource,String description,Runnable click){
        ImageButton item=new ImageButton(this);
        item.setImageResource(resource);
        item.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        item.setPadding(dp(10),dp(10),dp(10),dp(10));
        int color=ReaderButtonColors.neutral(this,0xfffff9f0);
        item.setColorFilter(ReaderButtonColors.contrastingText(color));
        item.setBackground(bg(color,12));
        item.setContentDescription(description);
        item.setOnClickListener(v->click.run());
        return item;
    }
    private void addToolbarButton(LinearLayout bar,View item,boolean first){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1f);
        if(!first)lp.leftMargin=dp(5);
        bar.addView(item,lp);
    }
    private void createUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        root.setPadding(dp(10),dp(8),dp(10),dp(8));setContentView(root);
        SafeSystemBars.matchBackground(this,background);
        styledButtonColor=ReaderButtonColors.neutral(this,0xfffff9f0);
        styledAccentColor=ReaderButtonColors.accent(this,0xff915439);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(54)));
        ImageButton back=toolbarIcon(R.drawable.ic_back,"Назад",()->BackNavigation.back(this));
        addToolbarButton(toolbar,back,true);
        modeButton=toolbarIcon(local?R.drawable.ic_android_tts:R.drawable.ic_edge_tts,
                "Способ озвучивания",this::chooseMethod);
        addToolbarButton(toolbar,modeButton,false);
        ImageButton voiceButton=toolbarIcon(R.drawable.ic_tts_engine,"Выбор голоса",this::chooseVoice);
        addToolbarButton(toolbar,voiceButton,false);
        // Same icon-plus-remaining-time convention as the MP3 audiobook timer.
        LinearLayout sleep=new LinearLayout(this);
        sleep.setOrientation(LinearLayout.VERTICAL);sleep.setGravity(Gravity.CENTER);
        sleep.setBackground(bg(styledButtonColor,12));
        ImageView clock=new ImageView(this);clock.setImageResource(R.drawable.ic_sleep_timer);
        clock.setColorFilter(ReaderButtonColors.contrastingText(styledButtonColor));
        sleep.addView(clock,new LinearLayout.LayoutParams(dp(20),dp(20)));
        sleepRemaining=label("",10);sleepRemaining.setSingleLine(true);
        sleepRemaining.setTextColor(ReaderButtonColors.contrastingText(styledButtonColor));
        sleepRemaining.setGravity(Gravity.CENTER);sleepRemaining.setVisibility(View.GONE);
        sleep.addView(sleepRemaining,new LinearLayout.LayoutParams(-1,dp(15)));
        sleep.setContentDescription("Таймер сна");sleep.setOnClickListener(v->sleepDialog());
        sleepControl=sleep;
        addToolbarButton(toolbar,sleep,false);
        LinearLayout gauge=new LinearLayout(this);
        gauge.setOrientation(LinearLayout.VERTICAL);
        gauge.setGravity(Gravity.CENTER);gauge.setBackground(bg(ReaderButtonColors.neutral(this,0xfffff9f0),12));
        ImageView needle=new ImageView(this);needle.setImageResource(R.drawable.ic_audio_speed);
        needle.setColorFilter(ReaderButtonColors.contrastingText(styledButtonColor));gauge.addView(needle,new LinearLayout.LayoutParams(dp(19),dp(19)));
        speed=label(String.valueOf(TtsWpm.get(this)),13);speed.setTextColor(ReaderButtonColors.contrastingText(styledButtonColor));speed.setGravity(Gravity.CENTER);
        gauge.addView(speed);gauge.setContentDescription("Скорость чтения в словах в минуту");
        gauge.setOnClickListener(v->TtsWpm.dialog(this,()->{
            speed.setText(String.valueOf(TtsWpm.get(this)));
            if(local)startService(new Intent(this,AudioPlaybackService.class).setAction("tts_wpm"));
            else if(edge!=null)edge.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(this)));
            refresh();
        }));
        addToolbarButton(toolbar,gauge,false);
        ImageButton settings=toolbarIcon(R.drawable.ic_settings,"Настройки аудио",()->{
            startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true));
        });
        addToolbarButton(toolbar,settings,false);
        // Book title is retained as state for metadata but intentionally absent from the compact toolbar.
        bookTitle=label("",14);
        scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setBackground(bg(background,10));
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);sl.topMargin=dp(12);root.addView(scroller,sl);
        reading=label("Ожидание текста…",Math.max(18,Math.min(35,getSharedPreferences("reader",0).getInt("size",36)*2/3)));
        reading.setTypeface(getSharedPreferences("reader",0).getInt("font",1)==1?Typeface.SERIF:Typeface.DEFAULT);
        reading.setLineSpacing(dp(7),1.16f);reading.setPadding(dp(12),dp(12),dp(12),dp(14));
        reading.setTextIsSelectable(false);reading.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){fingerX=e.getX();fingerY=e.getY();userTouch=true;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                if(Math.abs(e.getY()-fingerY)>dp(10)||Math.abs(e.getX()-fingerX)>dp(10)){
                    userTouch=false;return false;
                }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                userTouch=false;
                if(reading.getLayout()==null)return false;
                int line=reading.getLayout().getLineForVertical(Math.max(0,(int)e.getY()-reading.getTotalPaddingTop()));
                int index=reading.getLayout().getOffsetForHorizontal(line,Math.max(0,e.getX()-reading.getTotalPaddingLeft()));
                int charOffset=Math.max(0,Math.min(full.length(),windowStart+index));
                selectWord(wordFromChar(charOffset));userTouch=false;return true;
            }
            if(e.getAction()==MotionEvent.ACTION_CANCEL)userTouch=false;
            return false;
        });
        scroller.addView(reading);
        // Exact center anchor is independent of left and right content widths.
        playbackBar=new FrameLayout(this);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,dp(70));
        controlsLp.topMargin=dp(5);root.addView(playbackBar,controlsLp);
        progress=label("0 ч 0 мин / ~0 ч 0 мин",11);
        progress.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);progress.setMaxLines(2);
        int sideWidth=Math.max(dp(68),Math.min(dp(108),
            (getResources().getDisplayMetrics().widthPixels-dp(20)-dp(78))/2));
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(sideWidth,dp(65),Gravity.START|Gravity.CENTER_VERTICAL);
        playbackBar.addView(progress,pp);
        FrameLayout playArea=new FrameLayout(this);
        int playBackground=ReaderButtonColors.accent(this,0xff915439);
        playArea.setBackground(bg(playBackground,19));
        playIcon=new ImageView(this);playIcon.setImageResource(R.drawable.ic_play);
        playIcon.setColorFilter(ReaderButtonColors.contrastingText(playBackground));
        playArea.addView(playIcon,new FrameLayout.LayoutParams(dp(31),dp(31),Gravity.CENTER));
        playArea.setContentDescription("Воспроизведение / пауза");
        playArea.setOnClickListener(v->togglePlay());
        playbackBar.addView(playArea,new FrameLayout.LayoutParams(dp(72),dp(63),Gravity.CENTER));
        LinearLayout edgeStatus=new LinearLayout(this);edgeStatus.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(sideWidth,dp(63),Gravity.END|Gravity.CENTER_VERTICAL);
        playbackBar.addView(edgeStatus,ep);
        offlineProgress=label("0%",14);offlineProgress.setGravity(Gravity.CENTER);
        edgeStatus.addView(offlineProgress,new LinearLayout.LayoutParams(0,dp(58),1f));
        downloadsButton=toolbarIcon(R.drawable.ic_downloads,"Загрузки",()->{
            startActivity(new Intent(this,DownloadActivity.class));
        });
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(45),dp(45));
        dl.leftMargin=dp(3);edgeStatus.addView(downloadsButton,dl);
        edgeStatus.setVisibility(local?View.GONE:View.VISIBLE);
    }

    private int wordChar(int word){return wordStarts.isEmpty()?0:wordStarts.get(Math.max(0,Math.min(word,wordStarts.size()-1)));}
    private int wordFromChar(int character){return wordStarts.isEmpty()?0:Math.max(0,Math.min(wordStarts.size()-1,SentenceChunks.lowerBound(wordStarts,character)));}
    private void showWindow(int anchor,boolean center){
        if(full.isEmpty())return;
        int first=Math.max(0,anchor-1600),last=Math.min(full.length(),anchor+4500);
        // Prefer complete neighboring paragraphs when inexpensive.
        int boundary=full.lastIndexOf('\n',first);if(boundary>=0&&first-boundary<250)first=boundary+1;
        int tail=full.indexOf('\n',last);if(tail>=0&&tail-last<250)last=tail;
        if(first==windowStart&&last==windowEnd)return;
        windowStart=first;windowEnd=last;lastStart=lastEnd=-1;
        lastSentenceStart=lastSentenceEnd=-1;
        displayedText=full.substring(first,last);
        reading.setText(new SpannableStringBuilder(displayedText),TextView.BufferType.SPANNABLE);
        if(center)scroller.post(()->scrollToChar(anchor,true));
    }
    private void scrollToChar(int charOffset,boolean force){
        if(reading.getLayout()==null||windowStart<0)return;
        int localChar=Math.max(0,Math.min(reading.getText().length()-1,charOffset-windowStart));
        int y=reading.getLayout().getLineTop(reading.getLayout().getLineForOffset(localChar));
        int current=scroller.getScrollY(),height=scroller.getHeight();
        if(force||y<current+dp(35)||y>current+height-dp(80))scroller.smoothScrollTo(0,Math.max(0,y-height/3));
    }
    private void mark(int from,int to,boolean scroll){
        if(full.isEmpty()||reading==null||from<0)return;
        SentenceChunks.Chunk sentence=SentenceChunks.forWord(sentences,wordFromChar(from));
        int sentenceFrom=sentence==null?from:sentence.fromChar;
        int sentenceTo=sentence==null?Math.max(to,from+1):sentence.toChar;
        if(sentenceFrom<windowStart+180||sentenceTo>windowEnd-300)showWindow(sentenceFrom,true);
        if(from==lastStart&&to==lastEnd&&sentenceFrom==lastSentenceStart&&sentenceTo==lastSentenceEnd)return;
        Spannable text=(Spannable)reading.getText();
        for(BackgroundColorSpan span:text.getSpans(0,text.length(),BackgroundColorSpan.class))text.removeSpan(span);
        for(ForegroundColorSpan span:text.getSpans(0,text.length(),ForegroundColorSpan.class))text.removeSpan(span);
        int sf=Math.max(0,sentenceFrom-windowStart),st=Math.min(text.length(),sentenceTo-windowStart);
        if(sf<st){
            // 50% translucent sentence background; remain highlighted during pauses.
            int shade=Color.argb(128,Color.red(accent),Color.green(accent),Color.blue(accent));
            text.setSpan(new BackgroundColorSpan(shade),sf,st,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int a=Math.max(0,from-windowStart),b=Math.min(text.length(),to-windowStart);
        // Word highlight only when the engine provided an accurate range.
        if(a<b && (from>sentenceFrom||to<sentenceTo)){
            text.setSpan(new BackgroundColorSpan(accent),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int contrast=Color.red(accent)+Color.green(accent)+Color.blue(accent)<375?Color.WHITE:0xff30221e;
            text.setSpan(new ForegroundColorSpan(contrast),a,b,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        lastStart=from;lastEnd=to;lastSentenceStart=sentenceFrom;lastSentenceEnd=sentenceTo;
        if(scroll&&!userTouch)scrollToChar(sentenceFrom,false);
    }
    private void selectWord(int word){
        if(wordStarts.isEmpty())return;
        SentenceChunks.Chunk sentence=SentenceChunks.forWord(sentences,Math.max(0,Math.min(word,wordStarts.size()-1)));
        selectedWord=sentence==null?word:sentence.fromWord;
        selectedWord=Math.max(0,Math.min(selectedWord,wordStarts.size()-1));
        boolean auto=getSharedPreferences("reader",0).getBoolean("tts_tap_autoplay",false);
        getSharedPreferences("reader",0).edit().putInt("tts_word:"+uri,selectedWord).apply();
        if(local){
            AudioPlaybackService svc=AudioPlaybackService.instance;
            startService(new Intent(this,AudioPlaybackService.class)
                .setAction(svc!=null&&svc.isTtsMode()?"tts_select_word":AudioPlaybackService.TTS)
                .putExtra("bookUri",uri).putExtra("word_index",selectedWord)
                .putExtra("auto_on_selection",auto));
        }else if(downloads!=null){
            // A seek during active speech continues immediately on the newly
            // selected live stream. A seek while paused honours tap-autoplay.
            boolean wasAudible=edge!=null&&edge.playRequested();
            if(edge!=null)edge.pause();
            downloads.setAnchor(selectedWord,true);
            requestedSeekWord=-1;requestedSeekKey="";
            if((auto||wasAudible)&&edge!=null)edge.play();
        }
        int from=sentence==null?wordChar(selectedWord):sentence.fromChar;
        int end=sentence==null?Math.min(full.length(),from+1):sentence.toChar;
        mark(from,end,true);
    }
    private void togglePlay(){
        if(local){AudioPlaybackService service=AudioPlaybackService.instance;
            if(service!=null&&!service.isPaused())selectedWord=liveWord();
            startService(new Intent(this,AudioPlaybackService.class).setAction(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY));
        }else if(edge!=null){
            if(edge.playRequested()){selectedWord=liveWord();edge.pause();}
            else {if(downloads!=null)downloads.ensurePlaySelection();edge.play();}
        }
        refresh();
    }
    private void updateSleepCountdown(){
        if(sleepRemaining==null||sleepControl==null)return;
        long left=0;
        if(local&&AudioPlaybackService.instance!=null)
            left=AudioPlaybackService.instance.getSleepRemainingMillis();
        else if(!local&&edge!=null)
            left=edge.sleepRemainingMs();
        if(left<=0){
            long until=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0);
            left=Math.max(0,until-System.currentTimeMillis());
        }
        String formatted=left<=0?"":left<60000?Math.max(1,(left+999)/1000)+"с":(left+59999)/60000+"м";
        if(!formatted.contentEquals(sleepRemaining.getText()))sleepRemaining.setText(formatted);
        sleepRemaining.setVisibility(left>0?View.VISIBLE:View.GONE);
        sleepControl.setContentDescription(left<=0?"Таймер сна выключен":"Таймер сна: осталось "+formatted);
    }
    private void sleepDialog(){
        int[] minutes={0,5,10,15,30,60};
        String[] choices={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};
        new SwipeDialogs.Builder(this).setTitle("Таймер сна")
            .setItems(choices,(dialog,n)->{
                if(local){startService(new Intent(this,AudioPlaybackService.class)
                    .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",minutes[n]));}
                else if(edge!=null)edge.setSleepMinutes(minutes[n]);
                Toast.makeText(this,minutes[n]==0?"Таймер выключен":"Таймер установлен",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Отмена",null).show();
    }
    /** Switch voice engines in place. No new Activity, layout or text reload. */
    private void switchTo(boolean toLocal){
        if(toLocal==local)return;
        boolean wasPlaying=local?(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode()
                &&!AudioPlaybackService.instance.isPaused()):(edge!=null&&edge.isPlaying());
        int at=selectedWord>=0&&!wasPlaying?selectedWord:liveWord();
        switchedWord=Math.max(0,at);selectedWord=switchedWord;
        getSharedPreferences("reader",0).edit().putBoolean("tts_last_local",toLocal)
            .putInt("tts_word:"+uri,switchedWord).apply();
        if(local){
            if(AudioPlaybackService.instance!=null)startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
        }else{
            if(edge!=null){edge.pause();edge.removeListener(edgeObserver);}
            if(downloads!=null){downloads.removeObserver(downloadObserver);downloads.setActive(false);}
            if(boundDownload){unbindService(downloadConnection);boundDownload=false;}
            if(boundPlayer){unbindService(playerConnection);boundPlayer=false;}
            downloads=null;edge=null;
        }
        local=toLocal;
        offlinePrompt=false;requestedSeekWord=-1;requestedSeekKey="";
        if(modeButton!=null)modeButton.setImageResource(local?R.drawable.ic_android_tts:R.drawable.ic_edge_tts);
        if(downloadsButton!=null&&downloadsButton.getParent() instanceof View)
            ((View)downloadsButton.getParent()).setVisibility(local?View.GONE:View.VISIBLE);
        SentenceChunks.Chunk chosen=SentenceChunks.forWord(sentences,selectedWord);
        if(chosen!=null)mark(chosen.fromChar,chosen.toChar,true);
        if(local){
            AudioModeCoordinator.stopEdge(this);
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS)
                .putExtra("bookUri",uri).putExtra("word_index",switchedWord)
                .putExtra("auto_on_selection",wasPlaying));
        }else{
            switchShouldPlay=wasPlaying;
            Intent job=new Intent(this,EdgeDownloadService.class).setAction(EdgeDownloadService.ACTION_BEGIN)
                .putExtra(EdgeDownloadService.EXTRA_URI,uri).putExtra(EdgeDownloadService.EXTRA_ANCHOR,switchedWord);
            try{ContextCompat.startForegroundService(this,job);}catch(Exception ex){
                Toast.makeText(this,"Не удалось подключить Edge TTS",Toast.LENGTH_LONG).show();switchShouldPlay=false;
            }
            boundPlayer=bindService(new Intent(this,EdgePlaybackService.class),playerConnection,BIND_AUTO_CREATE);
            boundDownload=bindService(new Intent(this,EdgeDownloadService.class),downloadConnection,BIND_AUTO_CREATE);
        }
        refresh();
    }
    private void chooseMethod(){
        LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        android.app.AlertDialog dialog=new SwipeDialogs.Builder(this).setTitle("Способ озвучивания")
            .setView(rows).create();
        addEngineChoice(rows,dialog,R.drawable.ic_android_tts,"Системный TTS",
            "Установленные голоса Samsung, Google и других",true);
        addEngineChoice(rows,dialog,R.drawable.ic_edge_tts,"Microsoft Edge TTS",
            "Онлайн-озвучка · сохранение MP3",false);
        dialog.show();
    }
    private void addEngineChoice(LinearLayout parent,android.app.AlertDialog dialog,
                                 int icon,String title,String subtitle,boolean toLocal){
        LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(18),dp(13),dp(18),dp(13));
        ImageView picture=new ImageView(this);picture.setImageResource(icon);
        item.addView(picture,new LinearLayout.LayoutParams(dp(29),dp(29)));
        LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(12);item.addView(labels,lp);
        TextView primary=label(title,17);labels.addView(primary);
        TextView secondary=label(subtitle,12);labels.addView(secondary);
        if(local==toLocal){ImageView tick=new ImageView(this);tick.setImageResource(android.R.drawable.checkbox_on_background);
            item.addView(tick,new LinearLayout.LayoutParams(dp(22),dp(22)));}
        item.setOnClickListener(v->{dialog.dismiss();switchTo(toLocal);});
        parent.addView(item,new LinearLayout.LayoutParams(-1,-2));
    }
    private void chooseVoice(){
        if(!local){
            final String[] names={"Светлана", "Дмитрий"};
            final String[] keys={"ru-RU-SvetlanaNeural", "ru-RU-DmitryNeural"};
            String selected=getSharedPreferences("reader",0).getString("edge_voice","ru-RU-SvetlanaNeural");
            if(downloads!=null)selected=downloads.voice();
            int checked=selected.contains("Dmitry")?1:0;
            new SwipeDialogs.Builder(this).setTitle("Голос Microsoft Edge")
                .setSingleChoiceItems(names,checked,(dialog,n)->{
                    getSharedPreferences("reader",0).edit().putString("edge_voice",keys[n]).apply();
                    if(downloads!=null)downloads.choose(keys[n],null);
                    dialog.dismiss();refresh();
                }).setNegativeButton("Отмена",null).show();
            return;
        }
        LocalVoiceCatalog.show(this,()->{
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.ENGINE));
            refresh();
        });
    }

    private void openFolder(){
        File folder=downloads==null?null:downloads.folder();
        if(folder==null){Toast.makeText(this,"Файлы книги ещё не подготовлены",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,EdgeFileExplorerActivity.class).putExtra("book_folder",folder.getAbsolutePath()));
    }
    private int liveWord(){
        if(local){AudioPlaybackService svc=AudioPlaybackService.instance;
            if(svc!=null&&svc.isTtsMode())return wordFromChar(svc.currentTtsWordRange()[0]);
        }else if(downloads!=null){
            EdgeDownloadService.Clip clip=downloads.currentClip();
            if(clip!=null&&edge!=null&&(clip.key+":"+clip.start).equals(edge.currentKey())){
                List<EdgeWordTimings.Word> ticks=clip.ready()?EdgeWordTimings.read(clip.file,clip.text):downloads.liveWords(clip);
                int selected=EdgeWordTimings.at(ticks,edge.positionMs());
                if(selected>=0){int base=downloads.charForWord(clip.start);
                    return wordFromChar(base+ticks.get(selected).from);
                }
                return clip.start;
            }
            return downloads.anchor();
        }
        return Math.max(0,selectedWord);
    }
    private void refresh(){
        if(isFinishing()||reading==null)return;
        if(!local&&full.isEmpty()&&downloads!=null&&downloads.isReady()){
            full=downloads.fullText();name=downloads.title();bookTitle.setText(name);
            wordStarts=SentenceChunks.wordPositions(full);sentences=SentenceChunks.split(full);
            selectedWord=Math.max(0,Math.min(Math.max(0,wordStarts.size()-1),downloads.anchor()));
            showWindow(wordChar(selectedWord),true);
        }
        if(full.isEmpty())return;
        boolean playing=local?(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode()&&!AudioPlaybackService.instance.isPaused()):edge!=null&&edge.isPlaying();
        playIcon.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play);
        speed.setText(String.valueOf(TtsWpm.get(this)));
        updateSleepCountdown();
        int word=(!playing&&selectedWord>=0)?selectedWord:liveWord(),start=wordChar(word),end=start;
        if(playing)selectedWord=word;
        if(local){
            AudioPlaybackService svc=AudioPlaybackService.instance;
            int[] range=playing&&svc!=null&&svc.isTtsMode()?svc.currentTtsWordRange():new int[]{start,start};
            if(playing){start=range[0];end=range[1];}
            else {SentenceChunks.Chunk part=SentenceChunks.forWord(sentences,word);
                if(part!=null){start=part.fromChar;end=part.toChar;}}
        }else{
            EdgeDownloadService.Clip clip=downloads==null?null:downloads.currentClip();
            List<EdgeWordTimings.Word> ticks=clip==null?Collections.emptyList():
                (clip.ready()?EdgeWordTimings.read(clip.file,clip.text):
                    (downloads==null?Collections.emptyList():downloads.liveWords(clip)));
            int selected=clip!=null&&edge!=null&&(clip.key+":"+clip.start).equals(edge.currentKey())?
                EdgeWordTimings.at(ticks,edge.positionMs()):-1;
            if(selected>=0){
                start=downloads.charForWord(clip.start)+ticks.get(selected).from;
                end=downloads.charForWord(clip.start)+ticks.get(selected).to;
            }else{
                SentenceChunks.Chunk part=SentenceChunks.forWord(sentences,word);
                start=part==null?0:part.fromChar;end=part==null?0:part.toChar;
            }
            if(requestedSeekWord>=0&&clip!=null&&clip.ready()&&edge!=null){
                String key=clip.key+":"+clip.start;
                if(key.equals(edge.currentKey())&&!key.equals(requestedSeekKey)){
                    requestedSeekKey=key;
                    int desiredChar=downloads.charForWord(requestedSeekWord)-downloads.charForWord(clip.start);
                    for(EdgeWordTimings.Word tick:ticks)if(desiredChar>=tick.from&&desiredChar<tick.to){
                        edge.seekWithin(tick.startMs);break;
                    }
                    requestedSeekWord=-1;
                }
            }
            if(!playing&&edge!=null&&edge.playRequested()&&downloads!=null&&downloads.words()>0
                &&clip!=null&&!clip.ready()&&!networkAvailable()&&!offlinePrompt){
                offlinePrompt=true;
                new SwipeDialogs.Builder(this).setTitle("Нет подключения к интернету")
                    .setMessage("Сохранённые предложения закончились. Продолжить через системный TTS?")
                    .setPositiveButton("Продолжить",(d,w)->switchTo(true))
                    .setNegativeButton("Не сейчас",null).show();
            }
        }
        if(end>start)mark(start,end,playing);
        int total=wordStarts.size();int pct=total>0?(int)Math.min(100,word*100L/total):0;
        int wpm=TtsWpm.get(this);int totalMinutes=(int)Math.round(total/(double)wpm);
        int heardMinutes=(int)Math.round(word/(double)wpm);
        long heardSeconds=getSharedPreferences("reader",0).getLong("tts_listened_seconds:"+uri,0);
        progress.setText(time((int)(heardSeconds/60))+" / ~"+time(totalMinutes));
        long now=android.os.SystemClock.uptimeMillis();
        if(playing&&now>=nextWordSaveAt){
            getSharedPreferences("reader",0).edit().putInt("tts_word:"+uri,word).apply();
            nextWordSaveAt=now+3000;
        }
        if(local){offlineProgress.setText("");}
        else if(downloads!=null){
            if(now>=nextCoverageAt){cachedCoverage=downloads.savedPercent();nextCoverageAt=now+6000;}
            offlineProgress.setText(Math.max(0,cachedCoverage)+"%");
        }
    }
    private String time(int m){return (m/60)+" ч "+(m%60)+" мин";}
    private boolean networkAvailable(){
        try{android.net.ConnectivityManager mgr=(android.net.ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities c=mgr.getNetworkCapabilities(mgr.getActiveNetwork());
            return c!=null&&c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }catch(Exception ex){return true;}
    }
    @Override protected void onResume(){
        super.onResume();
        if(root!=null){
            int nextButton=ReaderButtonColors.neutral(this,0xfffff9f0);
            int nextAccent=ReaderButtonColors.accent(this,0xff915439);
            android.content.SharedPreferences prefs=getSharedPreferences("reader",0);
            int nextBg=prefs.getInt("bg",background),nextFg=prefs.getInt("fg",foreground);
            if(nextButton!=styledButtonColor||nextAccent!=styledAccentColor||nextBg!=background||nextFg!=foreground){
                int at=selectedWord>=0?selectedWord:liveWord();int offset=scroller==null?0:scroller.getScrollY();
                background=nextBg;foreground=nextFg;
                SafeSystemBars.matchBackground(this,background);
                createUi();windowStart=-1;windowEnd=-1;
                if(!full.isEmpty()){
                    showWindow(wordChar(at),false);
                    SentenceChunks.Chunk part=SentenceChunks.forWord(sentences,at);
                    if(part!=null)mark(part.fromChar,part.toChar,false);
                    scroller.post(()->scroller.scrollTo(0,offset));
                }
            }
        }
        ui.removeCallbacks(ticker);ui.post(ticker);
    }
    @Override protected void onPause(){ui.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){
        ui.removeCallbacksAndMessages(null);
        if(downloads!=null)downloads.removeObserver(downloadObserver);
        if(edge!=null)edge.removeListener(edgeObserver);
        if(boundDownload)unbindService(downloadConnection);
        if(boundPlayer)unbindService(playerConnection);
        super.onDestroy();
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
