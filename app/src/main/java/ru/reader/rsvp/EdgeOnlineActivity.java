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
    private ImageButton modeButton;
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
            downloads=((EdgeDownloadService.LocalBinder)binder).getService();
            int word=getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+uri,0));
            downloads.openBook(uri,word);
            downloads.setActive(true);
            downloads.addObserver(downloadObserver);
            refresh();
        }
        @Override public void onServiceDisconnected(ComponentName n){downloads=null;}
    };
    private final ServiceConnection playerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder binder){
            edge=((EdgePlaybackService.LocalBinder)binder).getService();
            edge.prepareBook("edge:"+uri);
            edge.restoreSharedSleepTimer();
            edge.setAutoContinue(true);
            edge.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(EdgeOnlineActivity.this)));
            edge.addListener(edgeObserver);
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
        item.setColorFilter(foreground);
        item.setBackground(bg(0xfffff9f0,12));
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
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(54)));
        ImageButton back=toolbarIcon(R.drawable.ic_back,"Назад",()->BackNavigation.back(this));
        addToolbarButton(toolbar,back,true);
        // The original two engine icons are reused directly; engine selection no longer opens a dialog.
        modeButton=toolbarIcon(local?R.drawable.ic_android_tts:R.drawable.ic_edge_tts,
                local?"Переключить на Edge онлайн":"Переключить на системный TTS",()->switchTo(!local));
        addToolbarButton(toolbar,modeButton,false);
        ImageButton voiceButton=toolbarIcon(R.drawable.ic_tts_engine,"Выбор голоса",this::chooseVoice);
        addToolbarButton(toolbar,voiceButton,false);
        ImageButton sleep=toolbarIcon(R.drawable.ic_sleep_timer,"Таймер сна",this::sleepDialog);
        addToolbarButton(toolbar,sleep,false);
        ImageButton settings=toolbarIcon(R.drawable.ic_settings,"Настройки аудио",()->{
            startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true));
        });
        addToolbarButton(toolbar,settings,false);
        LinearLayout gauge=new LinearLayout(this);
        gauge.setOrientation(LinearLayout.VERTICAL);
        gauge.setGravity(Gravity.CENTER);gauge.setBackground(bg(0xfffff9f0,12));
        ImageView needle=new ImageView(this);needle.setImageResource(R.drawable.ic_audio_speed);
        needle.setColorFilter(foreground);gauge.addView(needle,new LinearLayout.LayoutParams(dp(19),dp(19)));
        speed=label(String.valueOf(TtsWpm.get(this)),13);speed.setGravity(Gravity.CENTER);
        gauge.addView(speed);gauge.setContentDescription("Скорость чтения в словах в минуту");
        gauge.setOnClickListener(v->TtsWpm.dialog(this,()->{
            speed.setText(String.valueOf(TtsWpm.get(this)));
            if(local)startService(new Intent(this,AudioPlaybackService.class).setAction("tts_wpm"));
            else if(edge!=null)edge.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(this)));
            refresh();
        }));
        addToolbarButton(toolbar,gauge,false);
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
        // The entire lower area is one compact row: listening time / Play-Pause / MP3 percentage / Downloads.
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,dp(69));
        controlsLp.topMargin=dp(5);root.addView(controls,controlsLp);
        progress=label("0 ч 0 мин / ~0 ч 0 мин",12);
        progress.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);progress.setMaxLines(2);
        controls.addView(progress,new LinearLayout.LayoutParams(0,dp(60),1.5f));
        LinearLayout playArea=new LinearLayout(this);playArea.setOrientation(LinearLayout.VERTICAL);
        playArea.setGravity(Gravity.CENTER);
        playArea.setBackground(bg(ReaderButtonColors.accent(this,0xff915439),19));
        playIcon=new ImageView(this);playIcon.setImageResource(R.drawable.ic_play);
        playIcon.setColorFilter(Color.WHITE);
        playArea.addView(playIcon,new LinearLayout.LayoutParams(dp(31),dp(31)));
        playArea.setContentDescription("Воспроизведение / пауза");
        playArea.setOnClickListener(v->togglePlay());
        controls.addView(playArea,new LinearLayout.LayoutParams(dp(72),dp(61)));
        offlineProgress=label("0%",14);offlineProgress.setGravity(Gravity.CENTER);
        controls.addView(offlineProgress,new LinearLayout.LayoutParams(0,dp(58),.45f));
        ImageButton downloadsButton=toolbarIcon(R.drawable.ic_downloads,"Загрузки",()->{
            startActivity(new Intent(this,DownloadActivity.class));
        });
        LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(dp(45),dp(45));
        dl.leftMargin=dp(3);controls.addView(downloadsButton,dl);
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
            if(edge!=null)edge.pause();
            downloads.setAnchor(selectedWord,true);
            requestedSeekWord=-1;requestedSeekKey="";
            if(auto&&edge!=null)edge.play();
        }
        int from=sentence==null?wordChar(selectedWord):sentence.fromChar;
        int end=sentence==null?Math.min(full.length(),from+1):sentence.toChar;
        mark(from,end,true);
    }
    private void togglePlay(){
        if(local){AudioPlaybackService service=AudioPlaybackService.instance;
            if(service!=null&&!service.isPaused())selectedWord=liveWord();
            startService(new Intent(this,AudioPlaybackService.class).setAction(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY));
        }else if(edge!=null){if(edge.isPlaying()){selectedWord=liveWord();edge.pause();}else edge.play();}
        refresh();
    }
    private void sleepDialog(){
        int[] minutes={0,5,10,15,30,60};
        String[] choices={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};
        new AlertDialog.Builder(this).setTitle("Таймер сна")
            .setItems(choices,(dialog,n)->{
                if(local){startService(new Intent(this,AudioPlaybackService.class)
                    .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",minutes[n]));}
                else if(edge!=null)edge.setSleepMinutes(minutes[n]);
                Toast.makeText(this,minutes[n]==0?"Таймер выключен":"Таймер установлен",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Отмена",null).show();
    }
    private void switchTo(boolean toLocal){
        if(toLocal==local)return;
        getSharedPreferences("reader",0).edit().putBoolean("tts_last_local",toLocal).apply();
        int at=liveWord();
        if(!local&&downloads!=null)downloads.setActive(false);
        if(local&&AudioPlaybackService.instance!=null)startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
        Intent target=new Intent(this,EdgeOnlineActivity.class).putExtra("book_uri",uri)
            .putExtra("word_index",at).putExtra("tts_mode_local",toLocal);
        startActivity(target);finish();
    }
    private void chooseVoice(){
        if(!local){
            final String[] names={"Светлана", "Дмитрий"};
            final String[] keys={"ru-RU-SvetlanaNeural", "ru-RU-DmitryNeural"};
            String selected=getSharedPreferences("reader",0).getString("edge_voice","ru-RU-SvetlanaNeural");
            if(downloads!=null)selected=downloads.voice();
            int checked=selected.contains("Dmitry")?1:0;
            new AlertDialog.Builder(this).setTitle("Голос Microsoft Edge")
                .setSingleChoiceItems(names,checked,(dialog,n)->{
                    getSharedPreferences("reader",0).edit().putString("edge_voice",keys[n]).apply();
                    if(downloads!=null)downloads.choose(keys[n],null);
                    dialog.dismiss();refresh();
                }).setNegativeButton("Отмена",null).show();
            return;
        }
        final android.content.SharedPreferences prefs=getSharedPreferences("reader",0);
        final String engineId=prefs.getString("tts_engine","");
        final AlertDialog loading=new AlertDialog.Builder(this)
            .setTitle("Локальные голоса").setMessage("Загрузка установленных голосов…")
            .setNegativeButton("Отмена",null).create();
        loading.show();
        final TextToSpeech[] synthesizer=new TextToSpeech[1];
        try{
            TextToSpeech.OnInitListener listener=status->runOnUiThread(()->{
                TextToSpeech tts=synthesizer[0];
                if(isFinishing()||!loading.isShowing()||tts==null){if(tts!=null)tts.shutdown();return;}
                if(status!=TextToSpeech.SUCCESS){
                    loading.setMessage("Системный TTS недоступен на устройстве.");tts.shutdown();return;
                }
                ArrayList<Voice> found=new ArrayList<>();
                Set<Voice> voices=tts.getVoices();
                if(voices!=null)for(Voice candidate:voices){
                    if(candidate.getLocale()!=null&&"ru".equals(candidate.getLocale().getLanguage())
                            &&!candidate.isNetworkConnectionRequired())found.add(candidate);
                }
                Collections.sort(found,(x,y)->x.getName().compareToIgnoreCase(y.getName()));
                final String activeEngine=engineId.isEmpty()?tts.getDefaultEngine():engineId;
                loading.dismiss();tts.shutdown();
                if(found.isEmpty()){
                    new AlertDialog.Builder(this).setTitle("Локальные голоса")
                        .setMessage("Русские локальные голоса не обнаружены. Откройте настройки для установки голосов.")
                        .setPositiveButton("Настройки голосов",(dialog,n)->startActivity(new Intent(this,TtsVoiceActivity.class)))
                        .setNegativeButton("Закрыть",null).show();return;
                }
                String saved=prefs.getString("tts_voice","");
                String[] labels=new String[found.size()];int active=-1;
                for(int i=0;i<found.size();i++){
                    Voice v=found.get(i);if(v.getName().equals(saved))active=i;
                    String name=v.getName();
                    String engineLabel=activeEngine.toLowerCase(Locale.ROOT).contains("samsung")?"Samsung":
                        activeEngine.toLowerCase(Locale.ROOT).contains("google")?"Google":"Системный TTS";
                    labels[i]=engineLabel+" · Русский голос "+(i+1)+"\n"+name;
                }
                new AlertDialog.Builder(this).setTitle("Локальные голоса")
                    .setSingleChoiceItems(labels,active,(dialog,index)->{
                        Voice choice=found.get(index);
                        prefs.edit().putString("tts_engine",activeEngine)
                            .putString("tts_voice",choice.getName()).apply();
                        startService(new Intent(this,AudioPlaybackService.class)
                            .setAction(AudioPlaybackService.ENGINE));
                        dialog.dismiss();
                    })
                    .setNeutralButton("Все голоса и движки",(dialog,n)->startActivity(new Intent(this,TtsVoiceActivity.class)))
                    .setNegativeButton("Отмена",null).show();
            });
            synthesizer[0]=engineId.isEmpty()?new TextToSpeech(this,listener)
                :new TextToSpeech(this,listener,engineId);
        }catch(Exception ex){loading.dismiss();
            new AlertDialog.Builder(this).setMessage("Не удалось загрузить голоса: "+ex.getMessage())
                .setPositiveButton("OK",null).show();}
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
            if(clip!=null&&edge!=null&&clip.ready()&&(clip.key+":"+clip.start).equals(edge.currentKey())){
                List<EdgeWordTimings.Word> ticks=EdgeWordTimings.read(clip.file,clip.text);
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
            List<EdgeWordTimings.Word> ticks=clip!=null&&clip.ready()?EdgeWordTimings.read(clip.file,clip.text):Collections.emptyList();
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
                new AlertDialog.Builder(this).setTitle("Нет подключения к интернету")
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
        if(local){offlineProgress.setText("0%");}
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
    @Override protected void onResume(){super.onResume();ui.removeCallbacks(ticker);ui.post(ticker);}
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
