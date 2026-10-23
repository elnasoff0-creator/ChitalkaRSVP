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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
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
    private TextView reading,bookTitle,progress,offlineProgress,playLabel,method,voice,speed;
    private ImageView playIcon;
    private String displayedText="";
    private int windowStart=-1,windowEnd=-1,lastStart=-1,lastEnd=-1;
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
        local=getIntent().getBooleanExtra("tts_mode_local",false);
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
    private void createUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        root.setPadding(dp(12),dp(18),dp(12),dp(10));setContentView(root);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(54)));
        Button back=action("‹");back.setTextSize(29);back.setOnClickListener(v->BackNavigation.back(this));
        toolbar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(50)));
        bookTitle=label("Загрузка книги…",17);bookTitle.setSingleLine(true);bookTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bookTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        toolbar.addView(bookTitle,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout gauge=new LinearLayout(this);gauge.setGravity(Gravity.CENTER_VERTICAL);gauge.setBackground(bg(0xfffff9f0,12));gauge.setPadding(dp(7),0,dp(7),0);
        ImageView needle=new ImageView(this);needle.setImageResource(R.drawable.ic_audio_speed);needle.setColorFilter(0xff30221e);
        gauge.addView(needle,new LinearLayout.LayoutParams(dp(22),dp(22)));
        speed=label(String.valueOf(TtsWpm.get(this)),16);speed.setPadding(dp(5),0,0,0);speed.setTextColor(0xff30221e);
        gauge.addView(speed);gauge.setOnClickListener(v->TtsWpm.dialog(this,()->{
            speed.setText(String.valueOf(TtsWpm.get(this)));
            if(local)startService(new Intent(this,AudioPlaybackService.class).setAction("tts_wpm"));
            else if(edge!=null)edge.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(this)));
            refresh();
        }));
        toolbar.addView(gauge,new LinearLayout.LayoutParams(-2,dp(45)));
        LinearLayout methods=new LinearLayout(this);methods.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ml=new LinearLayout.LayoutParams(-1,dp(46));ml.topMargin=dp(6);root.addView(methods,ml);
        method=label(local?"Системный TTS ▾":"Microsoft Edge ▾",14);
        method.setGravity(Gravity.CENTER);method.setBackground(bg(0xfffff9f0,12));
        method.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Способ озвучивания")
            .setItems(new String[]{"Системный TTS · локальные голоса","Microsoft Edge · онлайн"},(dialog,n)->switchTo(n==0))
            .setNegativeButton("Отмена",null).show());
        methods.addView(method,new LinearLayout.LayoutParams(0,-1,1.1f));
        voice=label(local?"Локальный голос ▾":"Голос Edge ▾",13);
        voice.setGravity(Gravity.CENTER);voice.setBackground(bg(0xfffff9f0,12));voice.setOnClickListener(v->chooseVoice());
        LinearLayout.LayoutParams vl=new LinearLayout.LayoutParams(0,-1,1);vl.leftMargin=dp(7);methods.addView(voice,vl);
        if(!local){
            Button folder=action("▣");folder.setContentDescription("Файлы аудиокниги");folder.setOnClickListener(v->openFolder());
            LinearLayout.LayoutParams fl=new LinearLayout.LayoutParams(dp(48),-1);fl.leftMargin=dp(5);methods.addView(folder,fl);
        }
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
        progress=label("Прочитано: 0%",13);progress.setPadding(dp(8),dp(8),0,dp(3));root.addView(progress);
        offlineProgress=label("",12);offlineProgress.setPadding(dp(8),0,0,dp(5));root.addView(offlineProgress);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        root.addView(controls,new LinearLayout.LayoutParams(-1,dp(67)));
        // Same central play/pause model as Cursor. No seconds, fragment or seek-bar controls.
        LinearLayout playArea=new LinearLayout(this);playArea.setOrientation(LinearLayout.VERTICAL);playArea.setGravity(Gravity.CENTER);
        playArea.setBackground(bg(ReaderButtonColors.accent(this,0xff915439),20));
        playIcon=new ImageView(this);playIcon.setImageResource(R.drawable.ic_play);playIcon.setColorFilter(Color.WHITE);
        playArea.addView(playIcon,new LinearLayout.LayoutParams(dp(31),dp(31)));
        playArea.setContentDescription("Воспроизведение / пауза");playArea.setOnClickListener(v->togglePlay());
        controls.addView(playArea,new LinearLayout.LayoutParams(dp(77),dp(61)));
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
        if(full.isEmpty()||reading==null||from<0||to<=from)return;
        if(from<windowStart+180||to>windowEnd-300)showWindow(from,true);
        if(from==lastStart&&to==lastEnd)return;
        Spannable text=(Spannable)reading.getText();
        for(BackgroundColorSpan span:text.getSpans(0,text.length(),BackgroundColorSpan.class))text.removeSpan(span);
        for(ForegroundColorSpan span:text.getSpans(0,text.length(),ForegroundColorSpan.class))text.removeSpan(span);
        int start=Math.max(0,from-windowStart),end=Math.min(text.length(),to-windowStart);
        if(start<end){text.setSpan(new BackgroundColorSpan(accent),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            int contrast=Color.red(accent)+Color.green(accent)+Color.blue(accent)<375?Color.WHITE:0xff30221e;
            text.setSpan(new ForegroundColorSpan(contrast),start,end,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        lastStart=from;lastEnd=to;
        if(scroll&&!userTouch)scrollToChar(from,false);
    }
    private void selectWord(int word){
        if(wordStarts.isEmpty())return;
        selectedWord=Math.max(0,Math.min(word,wordStarts.size()-1));
        getSharedPreferences("reader",0).edit().putInt("tts_word:"+uri,selectedWord).apply();
        if(local){
            AudioPlaybackService service=AudioPlaybackService.instance;
            if(service!=null&&service.isTtsMode())startService(new Intent(this,AudioPlaybackService.class)
                .setAction("tts_select_word").putExtra("word_index",selectedWord));
            else startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS)
                .putExtra("bookUri",uri).putExtra("word_index",selectedWord));
        }else if(downloads!=null){
            downloads.setAnchor(selectedWord,true);
            requestedSeekWord=selectedWord;requestedSeekKey="";
            if(edge!=null)edge.play();
        }
        int from=wordChar(selectedWord);
        SentenceChunks.Chunk sentence=SentenceChunks.forWord(sentences,selectedWord);
        if(local&&sentence!=null){mark(sentence.fromChar,sentence.toChar,true);}
        else {int end=selectedWord+1<wordStarts.size()?Math.min(full.length(),wordStarts.get(selectedWord+1)):full.length();mark(from,end,true);}
    }
    private void togglePlay(){
        if(local){AudioPlaybackService service=AudioPlaybackService.instance;
            startService(new Intent(this,AudioPlaybackService.class).setAction(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY));
        }else if(edge!=null){if(edge.isPlaying())edge.pause();else edge.play();}
        refresh();
    }
    private void switchTo(boolean toLocal){
        if(toLocal==local)return;
        int at=liveWord();
        if(!local&&downloads!=null)downloads.setActive(false);
        if(local&&AudioPlaybackService.instance!=null)startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.STOP));
        Intent target=new Intent(this,EdgeOnlineActivity.class).putExtra("book_uri",uri)
            .putExtra("word_index",at).putExtra("tts_mode_local",toLocal);
        startActivity(target);finish();
    }
    private void chooseVoice(){
        if(local){startActivity(new Intent(this,TtsVoiceActivity.class));return;}
        if(downloads==null)return;
        String[] choices={"Светлана · ru-RU-SvetlanaNeural","Дмитрий · ru-RU-DmitryNeural"};
        String[] keys={"ru-RU-SvetlanaNeural","ru-RU-DmitryNeural"};
        new AlertDialog.Builder(this).setTitle("Голос Edge")
            .setItems(choices,(d,n)->{downloads.choose(keys[n],null);refresh();})
            .setNegativeButton("Отмена",null).show();
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
        int word=liveWord(),start=wordChar(word),end;
        if(local){
            AudioPlaybackService svc=AudioPlaybackService.instance;
            int[] range=svc!=null&&svc.isTtsMode()?svc.currentTtsWordRange():new int[]{start,start};
            start=range[0];end=range[1];
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
            if(!playing&&downloads!=null&&downloads.words()>0&&clip!=null&&!clip.ready()&&!networkAvailable()&&!offlinePrompt){
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
        progress.setText("Позиция: "+pct+"% · Прослушано: "+time((int)(heardSeconds/60))
            +" · Всего: ~"+time(totalMinutes));
        long now=android.os.SystemClock.uptimeMillis();
        if(playing&&now>=nextWordSaveAt){
            getSharedPreferences("reader",0).edit().putInt("tts_word:"+uri,word).apply();
            nextWordSaveAt=now+3000;
        }
        if(local){offlineProgress.setText("Системный голос · временный кэш");voice.setText("Локальный голос ▾");}
        else if(downloads!=null){
            long now=android.os.SystemClock.uptimeMillis();
            if(now>=nextCoverageAt){cachedCoverage=downloads.savedPercent();nextCoverageAt=now+6000;}
            offlineProgress.setText("Сохранено офлайн: "+Math.max(0,cachedCoverage)+"% · Edge MP3");
            voice.setText(downloads.voice().contains("Dmitry")?"Дмитрий ▾":"Светлана ▾");
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
