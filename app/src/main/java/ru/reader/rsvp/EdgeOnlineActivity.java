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
    private BookChunkIndex textBlocks;
    private BookChapterIndex chapters=BookChapterIndex.empty();
    private boolean pendingEdgePlay=false;
    private String lastEdgeError="";
    private EdgeStreamingService downloads;
    private EdgePlaybackService edge;
    private boolean boundDownload,boundPlayer;
    private int background,foreground,accent;
    private LinearLayout root;
    private ScrollView scroller;
    private LinearLayout topToolbar;
    private LinearLayout bookHeaderTts;
    private TextView coverMetadata,topBookName;
    private boolean topToolbarVisible=true;
    private TextView reading,bookTitle,progress,offlineProgress,speed;
    private ImageButton modeButton,downloadsButton;
    private TextView sleepRemaining;
    private View sleepControl;
    private View toolbarSpeedControl;
    private View tocControl;
    private FrameLayout playbackBar;
    private boolean switchShouldPlay=false;
    private int switchedWord=-1;
    private int styledButtonColor=Integer.MIN_VALUE,styledAccentColor=Integer.MIN_VALUE;
    private ImageView playIcon;
    private String displayedText="";
    private int windowStart=-1,windowEnd=-1,lastStart=-1,lastEnd=-1;
    private int lastSentenceStart=-1,lastSentenceEnd=-1;
    private int selectedWord=-1;
    // Keep a manual jump visible until the selected TTS sentence becomes active.
    private int pendingPositionWord=-1;
    private int cachedCoverage=-1;
    private long nextCoverageAt=0;
    private long nextWordSaveAt=0;
    private boolean userTouch=false,offlinePrompt=false;
    private long manualScrollUntil=0;
    private boolean programmaticScroll=false,manualScrollSeen=false;
    private final Runnable clearAutomaticScroll=()->programmaticScroll=false;
    private float fingerX,fingerY;
    private final EdgeStreamingService.Observer downloadObserver=this::refresh;
    private final EdgePlaybackService.Listener edgeObserver=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean done){refresh();}
        @Override public void onPlaybackStateChanged(){refresh();}
    };
    private final ServiceConnection downloadConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder binder){
            if(local)return;
            downloads=((EdgeStreamingService.LocalBinder)binder).getService();
            int word=switchedWord>=0?switchedWord:getIntent().getIntExtra("word_index",getSharedPreferences("reader",0).getInt("tts_word:"+uri,0));
            downloads.openBook(uri,word);
            if(downloads.isReady())downloads.setAnchor(word,true);
            downloads.setActive(true);
            downloads.addObserver(downloadObserver);
            if(edge!=null&&edge.playRequested())downloads.ensurePlaySelection();
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
            if(switchShouldPlay||pendingEdgePlay){
                switchShouldPlay=false;pendingEdgePlay=false;
                edge.play();
                if(downloads!=null)downloads.ensurePlaySelection();
            }
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
            // Ordinary listening binds the light streaming controller. No foreground
            // download service or download notification is started.
            // A regular streaming coordinator, not a foreground download notification.
            startService(new Intent(this,EdgeStreamingService.class)
                .setAction(EdgeStreamingService.ACTION_BEGIN)
                .putExtra(EdgeStreamingService.EXTRA_URI,uri)
                .putExtra(EdgeStreamingService.EXTRA_ANCHOR,
                    switchedWord>=0?switchedWord:getSharedPreferences("reader",0).getInt("tts_word:"+uri,0)));
            boundPlayer=bindService(new Intent(this,EdgePlaybackService.class),playerConnection,BIND_AUTO_CREATE);
            boundDownload=bindService(new Intent(this,EdgeStreamingService.class),downloadConnection,BIND_AUTO_CREATE);
        }
        ui.post(ticker);
    }
    private void readBook(){
        final String requested=uri;
        new Thread(()->{
            try{
                BookLoader.Book book=UnifiedBookSession.open(this,android.net.Uri.parse(requested)).book;
                final String body=book.text==null?"":book.text;
                final String title=book.title==null?"Книга":book.title;
                final ArrayList<Integer> offsets=SentenceChunks.wordPositions(body);
                ui.post(()->{
                    if(!requested.equals(uri)||isFinishing())return;
                    full=body;chapters=book.chapters;
                    wordStarts=offsets;sentences=SentenceChunks.split(body);
                    textBlocks=new BookChunkIndex(body,offsets,560);
                    name=title;bookTitle.setText(name);
                    if(topBookName!=null)topBookName.setText(name);
                    if(bookHeaderTts!=null){
                        View heading=bookHeaderTts.findViewWithTag("bookHeaderTitle");
                        if(heading instanceof TextView)((TextView)heading).setText(name);
                        if(coverMetadata!=null)coverMetadata.setText(wordStarts.size()+" слов · "+(chapters==null?0:chapters.size())+" глав");
                    }
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
        int available=getResources().getDisplayMetrics().widthPixels-dp(20);
        int width=Math.min(dp(44),Math.max(dp(34),(available-dp(5)*6)/7));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,width);
        if(!first)lp.leftMargin=dp(5);
        bar.addView(item,lp);
    }
    private void createUi(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        root.setPadding(dp(10),dp(8),dp(10),dp(8));setContentView(root);
        SafeSystemBars.matchBackground(this,background);
        styledButtonColor=ReaderButtonColors.neutral(this,0xfffff9f0);
        styledAccentColor=ReaderButtonColors.accent(this,0xff915439);
        LinearLayout toolbar=new LinearLayout(this);topToolbar=toolbar;toolbar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(54)));
        ImageButton back=toolbarIcon(R.drawable.ic_back,"Назад",()->BackNavigation.back(this));
        addToolbarButton(toolbar,back,true);
        ImageButton readModes=toolbarIcon(R.drawable.ic_book,"Режим чтения",()->
            UiNavigationSheet.showModes(this,3,chosen->openReadingMode(chosen)));
        addToolbarButton(toolbar,readModes,false);
        toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
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
        toolbarSpeedControl=gauge;
        ImageButton settings=toolbarIcon(R.drawable.ic_more_vertical,"Дополнительно",()->{
            UiNavigationSheet.showActions(this,"Дополнительно",
                new String[]{"Закладки","Настройки"},
                new int[]{R.drawable.ic_bookmark,R.drawable.ic_settings},n->{
                    if(n==0)saveBookmark();
                    else startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true));
                });
        });
        addToolbarButton(toolbar,settings,false);
        topBookName=label("",12);topBookName.setSingleLine(true);
        topBookName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topBookName.setPadding(dp(8),0,0,0);
        topBookName.setVisibility(View.GONE);
        root.addView(topBookName,new LinearLayout.LayoutParams(-1,dp(20)));
        // Header is shown at the beginning; a compact title appears with the revealed toolbar.
        bookTitle=label("",14);
        scroller=new ScrollView(this);scroller.setFillViewport(true);scroller.setBackground(bg(background,10));
        scroller.setOnTouchListener((v,e)->{
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                fingerX=e.getX();fingerY=e.getY();userTouch=true;
                manualScrollUntil=android.os.SystemClock.uptimeMillis()+3000;
            }else if(action==MotionEvent.ACTION_MOVE){
                if(Math.abs(e.getY()-fingerY)>dp(10)||Math.abs(e.getX()-fingerX)>dp(10)){
                    manualScrollSeen=true;
                    manualScrollUntil=android.os.SystemClock.uptimeMillis()+3000;
                }
            }else if(action==MotionEvent.ACTION_UP){
                boolean tap=Math.abs(e.getY()-fingerY)<dp(10)
                    &&Math.abs(e.getX()-fingerX)<dp(10);
                userTouch=false;
                if(tap&&reading!=null&&reading.getLayout()!=null&&windowStart>=0){
                    int y=Math.max(0,(int)e.getY()+scroller.getScrollY()
                        -reading.getTop()-reading.getTotalPaddingTop());
                    int x=Math.max(0,(int)e.getX()-reading.getLeft()-reading.getTotalPaddingLeft());
                    int line=reading.getLayout().getLineForVertical(y);
                    int offset=reading.getLayout().getOffsetForHorizontal(line,x);
                    selectWord(wordFromChar(Math.min(full.length(),windowStart+offset)));
                }else manualScrollUntil=android.os.SystemClock.uptimeMillis()+3000;
            }else if(action==MotionEvent.ACTION_CANCEL){
                userTouch=false;manualScrollUntil=android.os.SystemClock.uptimeMillis()+3000;
            }
            // Never consume the gesture. ScrollView must retain fling handling.
            return false;
        });
        scroller.setOnScrollChangeListener((v,x,y,oldX,oldY)->{
            if(!programmaticScroll&&Math.abs(y-oldY)>dp(2)){
                setTopToolbarVisible(y<oldY);
                if(toolbarSpeedControl!=null)toolbarSpeedControl.setVisibility(y<oldY?View.VISIBLE:View.INVISIBLE);
                if(tocControl!=null)tocControl.setVisibility(y<oldY?View.VISIBLE:View.INVISIBLE);
            }
            if(!programmaticScroll&&y!=oldY){
                manualScrollSeen=true;manualScrollUntil=android.os.SystemClock.uptimeMillis()+3000;
            }
        });
        LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,0,1);sl.topMargin=dp(12);root.addView(scroller,sl);
        reading=label("Ожидание текста…",Math.max(18,Math.min(35,getSharedPreferences("reader",0).getInt("size",36)*2/3)));
        reading.setTypeface(getSharedPreferences("reader",0).getInt("font",1)==1?Typeface.SERIF:Typeface.DEFAULT);
        reading.setLineSpacing(dp(7),1.16f);reading.setPadding(dp(12),dp(12),dp(12),dp(14));
        reading.setTextIsSelectable(false);
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        bookHeaderTts=new LinearLayout(this);bookHeaderTts.setOrientation(LinearLayout.VERTICAL);
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bookHeaderTts.addView(cover,new LinearLayout.LayoutParams(-1,dp(190)));
        CoverImageLoader.show(cover,uri,true);
        TextView titleCard=label("",23);titleCard.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        titleCard.setTag("bookHeaderTitle");bookHeaderTts.addView(titleCard);
        coverMetadata=label("",12);bookHeaderTts.addView(coverMetadata);
        bookHeaderTts.setPadding(dp(12),dp(5),dp(12),dp(10));
        page.addView(bookHeaderTts);
        page.addView(reading,new LinearLayout.LayoutParams(-1,-2));
        scroller.addView(page);
        // Exact center anchor is independent of left and right content widths.
        playbackBar=new FrameLayout(this);
        LinearLayout.LayoutParams controlsLp=new LinearLayout.LayoutParams(-1,dp(70));
        controlsLp.topMargin=dp(5);root.addView(playbackBar,controlsLp);
        progress=label("0 ч 0 мин / ~0 ч 0 мин",11);
        progress.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);progress.setMaxLines(2);
        int sideWidth=Math.max(dp(115),
            (getResources().getDisplayMetrics().widthPixels-dp(20)-dp(76))/2);
        LinearLayout leftControls=new LinearLayout(this);
        leftControls.setGravity(Gravity.CENTER_VERTICAL);
        leftControls.setBackground(bg(styledButtonColor,13));
        leftControls.setOnClickListener(v->openTextBlocks());
        ImageButton blocksButton=toolbarIcon(R.drawable.ic_list_audio,
            "Менеджер текстовых блоков",this::openTextBlocks);
        blocksButton.setBackground(bg(styledAccentColor,12));
        blocksButton.setColorFilter(ReaderButtonColors.contrastingText(styledAccentColor));
        tocControl=leftControls;
        leftControls.addView(blocksButton,new LinearLayout.LayoutParams(dp(41),dp(44)));
        LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(0,dp(62),1);
        textLp.leftMargin=dp(4);leftControls.addView(progress,textLp);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(sideWidth,dp(65),Gravity.START|Gravity.CENTER_VERTICAL);
        playbackBar.addView(leftControls,pp);
        FrameLayout playArea=new FrameLayout(this);
        int playBackground=ReaderButtonColors.accent(this,0xff915439);
        playArea.setBackground(bg(playBackground,19));
        playIcon=new ImageView(this);playIcon.setImageResource(R.drawable.ic_play);
        playIcon.setColorFilter(ReaderButtonColors.contrastingText(playBackground));
        playArea.addView(playIcon,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
        playArea.setContentDescription("Воспроизведение / пауза");
        playArea.setOnClickListener(v->togglePlay());
        playbackBar.addView(playArea,new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER));
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
    private void setTopToolbarVisible(boolean visible){
        if(topToolbar==null||topToolbarVisible==visible)return;
        topToolbarVisible=visible;
        if(topBookName!=null)topBookName.setVisibility(visible&&windowStart>0?View.VISIBLE:View.GONE);
        // Hide optional WPM/TOC affordances, not the other navigation controls.
        topToolbar.animate().cancel();topToolbar.setAlpha(1f);topToolbar.setVisibility(View.VISIBLE);
    }
    private void showWindow(int anchor,boolean center){
        if(full.isEmpty())return;
        int first,last;
        if(textBlocks!=null&&textBlocks.size()>0){
            // Render only the selected prepared UI block and immediate neighbors.
            // The full book string remains the data model, not a giant TextView.
            int chunk=textBlocks.chunkForWord(wordFromChar(anchor));
            first=textBlocks.get(Math.max(0,chunk-1)).startChar;
            last=textBlocks.get(Math.min(textBlocks.size()-1,chunk+1)).endChar;
        }else{
            first=Math.max(0,anchor-1600);
            last=Math.min(full.length(),anchor+4500);
        }
        if(first==windowStart&&last==windowEnd){
            if(center)scroller.post(()->scrollToChar(anchor,true));
            return;
        }
        windowStart=first;windowEnd=last;lastStart=lastEnd=-1;
        if(bookHeaderTts!=null)bookHeaderTts.setVisibility(first==0?View.VISIBLE:View.GONE);
        if(topBookName!=null)topBookName.setVisibility(topToolbarVisible&&first>0?View.VISIBLE:View.GONE);
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
        if(force||android.os.SystemClock.uptimeMillis()>=manualScrollUntil){
            if(force||y<current+dp(35)||y>current+height-dp(80)){
                programmaticScroll=true;
                ui.removeCallbacks(clearAutomaticScroll);
                scroller.smoothScrollTo(0,Math.max(0,y-height/2));
                ui.postDelayed(clearAutomaticScroll,700);
            }
        }
    }
    private void mark(int from,int to,boolean scroll){
        if(full.isEmpty()||reading==null||from<0)return;
        SentenceChunks.Chunk sentence=SentenceChunks.forWord(sentences,wordFromChar(from));
        int sentenceFrom=sentence==null?from:sentence.fromChar;
        int sentenceTo=sentence==null?Math.max(to,from+1):sentence.toChar;
        boolean freeToFollow=android.os.SystemClock.uptimeMillis()>=manualScrollUntil;
        if((sentenceFrom<windowStart+180||sentenceTo>windowEnd-300)&&freeToFollow)
            showWindow(sentenceFrom,false);
        if(sentenceFrom<windowStart||sentenceFrom>=windowEnd)return;
        if(from==lastStart&&to==lastEnd&&sentenceFrom==lastSentenceStart&&sentenceTo==lastSentenceEnd)return;
        Spannable text=(Spannable)reading.getText();
        for(BackgroundColorSpan span:text.getSpans(0,text.length(),BackgroundColorSpan.class))text.removeSpan(span);
        for(ForegroundColorSpan span:text.getSpans(0,text.length(),ForegroundColorSpan.class))text.removeSpan(span);
        int sf=Math.max(0,sentenceFrom-windowStart),st=Math.min(text.length(),sentenceTo-windowStart);
        if(sf<st){
            android.content.SharedPreferences p=getSharedPreferences("reader",0);
            int color=p.getInt("sentence_highlight_color",0xffedca43);
            int opacity=Math.max(0,Math.min(100,p.getInt("sentence_highlight_opacity",50)));
            int shade=(color&0x00ffffff)|(((opacity*255+50)/100)<<24);
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
        if(scroll&&!userTouch&&freeToFollow)scrollToChar(sentenceFrom,false);
    }
    private void selectWord(int word){
        if(wordStarts.isEmpty())return;
        SentenceChunks.Chunk sentence=SentenceChunks.forWord(sentences,Math.max(0,Math.min(word,wordStarts.size()-1)));
        selectedWord=sentence==null?word:sentence.fromWord;
        selectedWord=Math.max(0,Math.min(selectedWord,wordStarts.size()-1));
        pendingPositionWord=selectedWord;
        updatePositionTime(selectedWord); // Update immediately, before any network request.
        boolean auto=getSharedPreferences("reader",0).getBoolean("tts_tap_autoplay",false);
        getSharedPreferences("reader",0).edit().putInt("tts_word:"+uri,selectedWord).apply();
        if(local){
            AudioPlaybackService svc=AudioPlaybackService.instance;
            startService(new Intent(this,AudioPlaybackService.class)
                .setAction(svc!=null&&svc.isTtsMode()?"tts_select_word":AudioPlaybackService.TTS)
                .putExtra("bookUri",uri).putExtra("word_index",selectedWord)
                .putExtra("auto_on_selection",auto));
        }else if(downloads!=null){
            // Preserve an active or pending Play across a tap and service binding.
            boolean wasAudible=(edge!=null&&edge.playRequested())||pendingEdgePlay;
            if(edge!=null)edge.pause();
            downloads.setAnchor(selectedWord,true);
            if(auto||wasAudible){
                if(edge!=null){edge.play();downloads.ensurePlaySelection();}
                else pendingEdgePlay=true;
            }
        }else if(!local){
            // Book/service may still be binding; remember the requested chapter.
            switchedWord=selectedWord;
            if(auto)pendingEdgePlay=true;
        }
        int from=sentence==null?wordChar(selectedWord):sentence.fromChar;
        int end=sentence==null?Math.min(full.length(),from+1):sentence.toChar;
        // An explicit tap/manager jump always centers, even after manual scrolling.
        manualScrollUntil=0;manualScrollSeen=false;
        showWindow(from,true);
        mark(from,end,true);
    }
    private void openTextBlocks(){
        if(textBlocks==null||textBlocks.size()==0){
            Toast.makeText(this,"Текст ещё загружается",Toast.LENGTH_SHORT).show();return;
        }
        TtsTextBlockManager.show(this,textBlocks,chapters,full,
            ReaderButtonColors.neutral(this,0xfffff9f0),foreground,wordChar(selectedWord>=0?selectedWord:liveWord()),
            chapterChar->{
                int at=SentenceChunks.lowerBound(wordStarts,chapterChar);
                selectWord(Math.max(0,Math.min(wordStarts.size()-1,at)));
            },
            part->{
                // The UI block controls navigation; audio still uses SentenceChunks.
                selectWord(part.startWord);
                showWindow(part.startChar,true);
            });
    }
    private void togglePlay(){
        if(local){AudioPlaybackService service=AudioPlaybackService.instance;
            if(service!=null&&!service.isPaused()&&pendingPositionWord<0)selectedWord=liveWord();
            startService(new Intent(this,AudioPlaybackService.class).setAction(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY));
        }else if(edge!=null){
            if(edge.playRequested()){
                pendingEdgePlay=false;
                if(pendingPositionWord<0)selectedWord=liveWord();edge.pause();
            } else {
                pendingEdgePlay=false;
                edge.play();if(downloads!=null)downloads.ensurePlaySelection();
            }
        }else{
            // The user can tap Play before bindService callbacks have run.
            // Remember intent rather than silently dropping the tap.
            pendingEdgePlay=!pendingEdgePlay;
            if(pendingEdgePlay)Toast.makeText(this,"Подключение к Edge TTS…",Toast.LENGTH_SHORT).show();
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
        String[] choices={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин","Конец главы"};
        UiNavigationSheet.showTopChoices(this,"Таймер сна",choices,-1,n->{
                int duration=n==6?-1:minutes[n];
                BookChapterIndex.Chapter chapter=chapters==null?null:chapters.atChar(wordChar(liveWord()));
                int end=chapter==null?wordStarts.size():wordFromChar(chapter.endChar);
                if(local){startService(new Intent(this,AudioPlaybackService.class)
                    .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",duration).putExtra("end_word",end));}
                else if(edge!=null){if(duration==-1)edge.setSleepChapterEnd(end);else edge.setSleepMinutes(duration);}
                Toast.makeText(this,duration==0?"Таймер выключен":"Таймер установлен",Toast.LENGTH_SHORT).show();
            });
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
        offlinePrompt=false;
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
            // Switching engine does not launch foreground downloader.
            // A regular streaming coordinator, not a foreground download notification.
            startService(new Intent(this,EdgeStreamingService.class)
                .setAction(EdgeStreamingService.ACTION_BEGIN)
                .putExtra(EdgeStreamingService.EXTRA_URI,uri)
                .putExtra(EdgeStreamingService.EXTRA_ANCHOR,
                    switchedWord>=0?switchedWord:getSharedPreferences("reader",0).getInt("tts_word:"+uri,0)));
            boundPlayer=bindService(new Intent(this,EdgePlaybackService.class),playerConnection,BIND_AUTO_CREATE);
            boundDownload=bindService(new Intent(this,EdgeStreamingService.class),downloadConnection,BIND_AUTO_CREATE);
        }
        refresh();
    }
    private void saveBookmark(){
        if(wordStarts.isEmpty())return;
        android.content.SharedPreferences preferences=getSharedPreferences("reader",0);
        String key="bookmark:"+uri;
        java.util.Set<String> saved=new java.util.HashSet<>(preferences.getStringSet(key,java.util.Collections.emptySet()));
        saved.add(String.valueOf(liveWord()));
        preferences.edit().putStringSet(key,saved).apply();
        Toast.makeText(this,"Закладка добавлена",Toast.LENGTH_SHORT).show();
    }
    private void openReadingMode(int selected){
        if(selected<0||selected>3||selected==3)return;
        int word=selectedWord>=0?selectedWord:liveWord();
        getSharedPreferences("reader",0).edit()
            .putInt("reading_mode",selected)
            .putInt("position:"+uri,word)
            .putInt("tts_word:"+uri,word).apply();
        if(edge!=null)edge.pause();
        if(downloads!=null)downloads.setActive(false);
        if(local&&AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode()){
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.PAUSE));
        }
        startActivity(new Intent(this,ReaderActivity.class).putExtra("book_uri",uri));
        finish();
    }
    private void chooseMethod(){
        UiNavigationSheet.showTopCustom(this,"Способ озвучивания",(rows,dialog)->{
            addEngineChoice(rows,dialog,R.drawable.ic_android_tts,"Системный TTS",
                "Установленные голоса Samsung, Google и других",true);
            addEngineChoice(rows,dialog,R.drawable.ic_edge_tts,"Microsoft Edge TTS",
                "Онлайн-озвучка · сохранение MP3",false);
        });
    }
    private void addEngineChoice(LinearLayout parent,android.app.Dialog dialog,
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
            UiNavigationSheet.showTopChoices(this,"Голос Microsoft Edge",names,checked,n->{
                getSharedPreferences("reader",0).edit().putString("edge_voice",keys[n]).apply();
                if(downloads!=null)downloads.choose(keys[n],null);
                refresh();
            });
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
            EdgeStreamingService.Clip clip=downloads.currentClip();
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
            textBlocks=new BookChunkIndex(full,wordStarts,560);
            chapters=downloads.chapterIndex();
            selectedWord=Math.max(0,Math.min(Math.max(0,wordStarts.size()-1),downloads.anchor()));
            showWindow(wordChar(selectedWord),true);
        }
        if(!local&&downloads!=null){
            String problem=downloads.error();
            if(edge!=null&&edge.playbackError()!=null&&!edge.playbackError().isEmpty())
                problem=edge.playbackError();
            if(problem!=null&&!problem.isEmpty()&&!problem.equals(lastEdgeError)){
                lastEdgeError=problem;
                Toast.makeText(this,problem,Toast.LENGTH_LONG).show();
            }else if(problem==null||problem.isEmpty())lastEdgeError="";
        }
        if(full.isEmpty())return;
        boolean playing=local?(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isTtsMode()&&!AudioPlaybackService.instance.isPaused()):edge!=null&&edge.isPlaying();
        // While the first MP3 packet is still buffering, Play is already active:
        // show Pause so the user can cancel instead of thinking the tap failed.
        boolean buffering=!local&&((edge!=null&&edge.playRequested())||pendingEdgePlay);
        playIcon.setImageResource(playing||buffering?R.drawable.ic_pause:R.drawable.ic_play);
        speed.setText(String.valueOf(TtsWpm.get(this)));
        updateSleepCountdown();
        int actualWord=liveWord();
        int word=(!playing&&selectedWord>=0)?selectedWord:actualWord;
        boolean waitingForSelectedSentence=false;
        if(pendingPositionWord>=0){
            SentenceChunks.Chunk target=SentenceChunks.forWord(sentences,pendingPositionWord);
            if(playing&&target!=null&&actualWord>=target.fromWord&&actualWord<target.toWord){
                pendingPositionWord=-1;word=actualWord;
            }else{
                word=pendingPositionWord;
                waitingForSelectedSentence=true;
            }
        }
        int start=wordChar(word),end=start;
        if(playing&&!waitingForSelectedSentence)selectedWord=word;
        if(local){
            AudioPlaybackService svc=AudioPlaybackService.instance;
            int[] range=playing&&!waitingForSelectedSentence&&svc!=null&&svc.isTtsMode()?svc.currentTtsWordRange():new int[]{start,start};
            if(playing&&!waitingForSelectedSentence){start=range[0];end=range[1];}
            else {SentenceChunks.Chunk part=SentenceChunks.forWord(sentences,word);
                if(part!=null){start=part.fromChar;end=part.toChar;}}
        }else{
            EdgeStreamingService.Clip clip=downloads==null?null:downloads.currentClip();
            List<EdgeWordTimings.Word> ticks=clip==null?Collections.emptyList():
                (clip.ready()?EdgeWordTimings.read(clip.file,clip.text):
                    (downloads==null?Collections.emptyList():downloads.liveWords(clip)));
            int selected=clip!=null&&edge!=null&&(clip.key+":"+clip.start).equals(edge.currentKey())?
                EdgeWordTimings.at(ticks,edge.positionMs()):-1;
            if(selected>=0&&!waitingForSelectedSentence){
                start=downloads.charForWord(clip.start)+ticks.get(selected).from;
                end=downloads.charForWord(clip.start)+ticks.get(selected).to;
            }else{
                SentenceChunks.Chunk part=SentenceChunks.forWord(sentences,word);
                start=part==null?0:part.fromChar;end=part==null?0:part.toChar;
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
        if(manualScrollSeen&&playing&&
                android.os.SystemClock.uptimeMillis()>=manualScrollUntil){
            manualScrollSeen=false;
            SentenceChunks.Chunk currently=SentenceChunks.forWord(sentences,word);
            if(currently!=null)showWindow(currently.fromChar,true);
        }
        // Position in the book, not the accumulated number of minutes listened.
        updatePositionTime(word);
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
    /** Estimate the book timeline from global word offsets and the current WPM.
     * This is independent of cached MP3s, elapsed listening sessions and UI windows. */
    private void updatePositionTime(int globalWord){
        if(progress==null)return;
        String position=TtsPositionTimeline.label(wordStarts.size(),globalWord,TtsWpm.get(this));
        if(!position.contentEquals(progress.getText()))progress.setText(position);
    }
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
