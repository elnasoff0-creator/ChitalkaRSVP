package ru.reader.rsvp;

import android.app.*;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;

/** Four modes, one ReaderActivity and a single global word index. */
public class ReaderActivity extends NavigationActivity {
    private static final int NORMAL=0,RSVP=1,CURSOR=2,TTS=3,NO_COLOR=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,speedPanel,controls,toolbar,toolbarStack,toolbarWpm,modeRow,readingFrame;
    private View toolbarSpacer;
    private TextView toolbarBookName,toolbarChapterName;
    private String lastAppliedFont="";
    private FrameLayout bottomBar;
    private LinearLayout contentsAction;
    private ImageButton contentsIcon;
    private LinearLayout bookHeader;
    private BookChapterIndex chapters=BookChapterIndex.empty();
    private BookChapterIndex actualChapters=BookChapterIndex.empty();
    private java.util.ArrayList<SentenceChunks.Chunk> ttsParts=new java.util.ArrayList<>();
    private SentenceIndex ttsSentenceIndex=SentenceIndex.fromText("");
    private int pendingTtsWord=-1;
    private long pendingTtsSelectedAt=0L;
    private String cachedTimingKey="",lastTtsError="";
    private java.util.List<EdgeWordTimings.Word> cachedTimingWords=java.util.Collections.emptyList();
    private String timingLoadingKey="",lastMissingTimingKey="";
    private long cachedTimingSidecarLength=-1L,cachedTimingSidecarModified=-1L;
    private final java.util.concurrent.ExecutorService timingReader=java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean toolbarExpanded=true;
    private FrameLayout sceneRoot,floatingPlayButton,rsvpPlayFrame;
    private TextView bookTitle;
    private ImageView floatingPlayIcon;
    private FrameLayout rsvpPane;
    private CountdownRingView countdownRing;
    private boolean waitingForStart=false;
    private boolean delayRsvpEnabled=false,delayCursorEnabled=false;
    private int delayRsvpSeconds=2,delayCursorSeconds=2;
    private float normalRsvpCenterOnScreen=-1f;
    private RecyclerView textList;
    private LinearLayoutManager layoutManager;
    private ChunkAdapter chunkAdapter;
    private TextView wordView,progressView,speedView;
    private ImageView playIcon,selectWordButton,modeIcon;
    private ImageButton toolbarModeAction,toolbarEngineAction,toolbarVoiceAction,toolbarThemeAction;
    private boolean rsvpScrollWords=false;
    private boolean resumeEdgeAfterFolderPick=false;
    private boolean ttsLocal=true,ttsBoundPlayer=false,ttsBoundStream=false,ttsPendingPlay=false;
    private EdgePlaybackService ttsPlayer;
    private EdgeStreamingService ttsStreamer;
    // A manual scroll suspends follow until one of the three explicit return actions.
    private boolean followSuspended=false,readerWasPaused=false;
    private TextView ttsFloatingCaption;
    private boolean lastRenderedTtsPlayState=false;
    private boolean playStateRendered=false;
    private String lastCaptionText="";
    private TextView toolbarSleepValue;
    private View toolbarSleepAction;
    private long lastSleepBadgeUpdate=-1L;
    private long launchStartedAt=0L;
    private long initialDrawElapsed=0L;
    private long edgePlayerBindStartedAt=0L,edgeStreamBindStartedAt=0L;
    private boolean ttsTickQueued=false;
    private long ttsTickDueAt=0L;
    private boolean localStateReceiverRegistered=false;
    private final BroadcastReceiver localStateReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(mode==TTS&&ttsLocal)scheduleTtsUi();
        }
    };
    private long navigationEpoch=0L;
    private long tocPreviewGeneration=0L; // invalidate delayed preview alignment on new user action
    private long lastIgnoredEpoch=-1L;

    private final EdgeStreamingService.Observer ttsStreamObserver=()->{if(ReaderActivity.this.mode==TTS)scheduleTtsUi();};
    private final EdgePlaybackService.Listener ttsPlayerListener=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean completed){if(mode==TTS)scheduleTtsUi();}
        @Override public void onPlaybackStateChanged(){if(mode==TTS)scheduleTtsUi();}
    };
    private final ServiceConnection ttsPlayerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(mode!=TTS||ttsLocal)return;
            ReaderDiagnosticLog.event("READER_STARTUP","edge_player_bind_ms="+(SystemClock.elapsedRealtime()-launchStartedAt)
                +" bind_wait_ms="+Math.max(0,SystemClock.elapsedRealtime()-edgePlayerBindStartedAt));
            ttsPlayer=((EdgePlaybackService.LocalBinder)binder).getService();
            ttsPlayer.prepareBook("edge:"+docUri);ttsPlayer.restoreSharedSleepTimer();
            ttsPlayer.setAutoContinue(true);ttsPlayer.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(ReaderActivity.this)));
            ttsPlayer.addListener(ttsPlayerListener);
            if(ttsPendingPlay&&ttsStreamer!=null){
                ttsPendingPlay=false;ttsStreamer.activatePreparedBook();
                ttsPlayer.play();ttsStreamer.ensurePlaySelection();
            }
            refreshTtsPosition();
        }
        @Override public void onServiceDisconnected(ComponentName name){ttsPlayer=null;}
    };
    private final ServiceConnection ttsStreamConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(mode!=TTS||ttsLocal)return;
            ReaderDiagnosticLog.event("READER_STARTUP","edge_stream_bind_ms="+(SystemClock.elapsedRealtime()-launchStartedAt)
                +" bind_wait_ms="+Math.max(0,SystemClock.elapsedRealtime()-edgeStreamBindStartedAt));
            ttsStreamer=((EdgeStreamingService.LocalBinder)binder).getService();
            ttsStreamer.openBook(docUri,index);ttsStreamer.addObserver(ttsStreamObserver);
            // Binding and entering TTS are not manual sentence jumps.
            if(pendingTtsWord>=0)ttsStreamer.setAnchor(pendingTtsWord,true);
            else ttsStreamer.setAnchor(index,false);
            ttsStreamer.setActive(true);
            if(ttsPendingPlay&&ttsPlayer!=null){
                ttsPendingPlay=false;ttsStreamer.activatePreparedBook();
                ttsPlayer.play();ttsStreamer.ensurePlaySelection();
            }else if(ttsPlayer!=null&&ttsPlayer.playRequested()
                    &&("edge:"+docUri).equals(ttsPlayer.currentBookId()))ttsStreamer.ensurePlaySelection();
        }
        @Override public void onServiceDisconnected(ComponentName name){ttsStreamer=null;}
    };
    private FocusLinesView focusLines;
    private ReadingWordMap map=ReadingWordMap.parse("");
    // Annotation data is loaded once with the book and never re-parses its HTML.
    private java.util.List<ReaderAnnotationsStore.Annotation> annotations=java.util.Collections.emptyList();
    private ReaderAnnotationsStore annotationStore;
    private java.util.List<ReaderBookmarks.Item> bookmarkItems=java.util.Collections.emptyList();
    private ReaderSelectionController selectionHandles;
    private boolean selectionDragging,selectionMenuAfterScroll;
    private PopupWindow selectionPopup;
    private int selectionFrom=-1,selectionTo=-1; // inclusive word indexes
    private long focusedNoteId;
    private boolean selectionLongPressed;
    private Runnable pendingLongPress;

    private BookChunkIndex chunks;
    private String body="",title="",author="",docUri="";
    private String[] words=new String[0];
    private int viewportWord=-1; // Browsing position; NEVER a playback/reading seek.
    private boolean manualViewportMovement=false;
    private int index=0,wpm=350,portion=1,size=36,bg=0xfff3e7d5,fg=0xff30221e,font=1,mode=RSVP;
    private int focusColor=0xffed3030,lineColor=0xff915439,linePosition=50;
    private int cursorBackground=0xff96583f,cursorText=NO_COLOR,cursorScale=100;
    private int normalSelectText=0xff915439,normalSelectScale=110,buttonColor=NO_COLOR,accentButtonColor=NO_COLOR;
    private int sentenceHighlightColor=0xffedca43,sentenceHighlightOpacity=50;
    private int pausePunctuationPercent=60,pauseLongPercent=50,pauseSentencePercent=120,pauseParagraphPercent=150;
    private boolean focusPoint=true,focusLinesEnabled=true,playing=false,selecting=false,selectedWordActive=false;
    private boolean userScrolling=false,restoringScroll=false;
    // Overlay controls replace the legacy immersive fullscreen preference entirely.
    private boolean normalChromeVisible=false;
    private final Runnable ttsUiTick=new Runnable(){@Override public void run(){
        ttsTickQueued=false;ttsTickDueAt=0L;
        if(mode!=TTS)return;
        refreshTtsPosition();
        long next=ttsCurrentlyPlaying()?180L:(sleepTimerActive()?15000L:-1L);
        if(next>0)queueTtsUi(next);
    }};
    private void queueTtsUi(long delay){
        if(mode!=TTS)return;
        long due=SystemClock.uptimeMillis()+delay;
        if(ttsTickQueued){
            // A Play, track-change or manual navigation must pre-empt a pending
            // 15-second pause/badge refresh. Never let an old timer starve UI.
            if(ttsTickDueAt<=due)return;
            handler.removeCallbacks(ttsUiTick);
        }
        ttsTickQueued=true;ttsTickDueAt=due;handler.postDelayed(ttsUiTick,delay);
    }
    private void scheduleTtsUi(){queueTtsUi(0L);}
    private void cancelTtsUi(){handler.removeCallbacks(ttsUiTick);ttsTickQueued=false;ttsTickDueAt=0L;}
    private float touchDownX,touchDownY;
    private long touchDownTime;
    private boolean touchMoved=false;
    private int swipeStartIndex=0,lastHighlightedChunk=-1;
    private float swipeStartY;
    private int scrollWordAccumulator=0;
    private long lastLightSave=0;
    private long chteicoLastInteraction=0;
    private long chteicoReadingSeconds=0;
    private int chteicoInitialIndex=0,chteicoLastWordIndex=0;
    private int fishingChapter=-1,fishingChapterSeconds=0,fishingChapterStartWord=-1;
    private int fishingTtsChapter=-1,fishingTtsStartWord=-1;
    private long fishingTtsTime=0,fishingTtsAccumulatedMs=0;


    private boolean chteicoResumed=false;
    private final Runnable chteicoReadTick=new Runnable(){@Override public void run(){
        if(!chteicoResumed)return;
        long now=SystemClock.elapsedRealtime();
        if(words.length>0 && !docUri.isEmpty() && (playing && !waitingForStart || now-chteicoLastInteraction<65000)){
            ChteicoStore.tickReading(ReaderActivity.this,mode,10);
            if(index>chteicoLastWordIndex)ChteicoStore.recordWords(ReaderActivity.this,index-chteicoLastWordIndex);
            chteicoLastWordIndex=index;
            chteicoReadingSeconds+=10;
            int visibleWord=mode==NORMAL&&viewportWord>=0?viewportWord:index;
            int readingChapter=chapterForWord(visibleWord);
            if(readingChapter>=0){
                if(fishingChapter<0){fishingChapter=readingChapter;fishingChapterStartWord=visibleWord;}
                else if(readingChapter!=fishingChapter){
                    if(fishingChapterSeconds>=30 && Math.abs(visibleWord-fishingChapterStartWord)>=25){
                        if(mode==TTS)ChteicoStore.awardAudioChapter(ReaderActivity.this,docUri,fishingChapter);
                        else ChteicoStore.awardReadChapter(ReaderActivity.this,docUri,fishingChapter);
                    }
                    fishingChapter=readingChapter;fishingChapterSeconds=0;fishingChapterStartWord=visibleWord;
                }else fishingChapterSeconds+=10;
            }
        }
        handler.postDelayed(this,10000);
    }};
    private final Runnable beginDelayedReading=new Runnable(){@Override public void run(){
        if(!playing||!waitingForStart)return;
        waitingForStart=false;removeCountdownRing();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));
    }};
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(!playing||words.length==0)return;
        int count=Math.min(portion,words.length-index);
        if(index+count>=words.length){
            if(ChteicoStore.completeBook(ReaderActivity.this,docUri))ChteicoNotifications.milestone(ReaderActivity.this,"Книга завершена!","Чтейцо радуется вашему достижению.");
            stop();save();return;
        }
        int previous=index;
        index+=count;
        if(mode==RSVP)updateWord();
        refreshHighlight(previous); // The paused RSVP word must never lose its highlight.
        if(mode==CURSOR&&!followSuspended)ensureWordVisible(false);
        updateProgress();saveLight(false);
        handler.postDelayed(this,delayForPortion(index,Math.min(portion,words.length-index)));
    }};

    @Override public void onCreate(Bundle state){super.onCreate(state);
        launchStartedAt=SystemClock.elapsedRealtime();
        prefs=getSharedPreferences("reader",MODE_PRIVATE);readPreferences();
        docUri=getIntent().getStringExtra("book_uri");
        if(docUri==null||docUri.isEmpty())docUri=prefs.getString("uri","");
        index=prefs.getInt("index",0);
        try{
            org.json.JSONArray library=new org.json.JSONArray(prefs.getString("library","[]"));
            for(int i=0;i<library.length();i++){
                org.json.JSONObject o=library.optJSONObject(i);
                if(o!=null&&docUri.equals(o.optString("uri"))){index=o.optInt("index",index);break;}
            }
        }catch(Exception ignored){}
        index=UnifiedReadingProgress.read(prefs,docUri,prefs.getInt("position:"+docUri,index));
        // The speech cursor is restored only after any explicit mode override.
        if(getIntent().getBooleanExtra("reset",false))index=0;
        int requestedMode=getIntent().getIntExtra("reading_mode_override",-1);
        if(requestedMode>=0&&requestedMode<=TTS){mode=requestedMode;
            prefs.edit().putInt("reading_mode",requestedMode).apply();}
        // All reading engines restore one canonical book position.
        if(mode==TTS&&!getIntent().getBooleanExtra("reset",false))
            index=UnifiedReadingProgress.read(prefs,docUri,index);
        if(getIntent().hasExtra("tts_mode_local")){
            ttsLocal=getIntent().getBooleanExtra("tts_mode_local",ttsLocal);
            prefs.edit().putBoolean("tts_last_local",ttsLocal).apply();
        }
        if(!getIntent().getBooleanExtra("reset",false)&&getIntent().hasExtra("word_index"))
            index=Math.max(0,getIntent().getIntExtra("word_index",index));
        if(!getIntent().getBooleanExtra("reset",false)&&!getIntent().hasExtra("word_index"))
            viewportWord=prefs.getInt("viewport:"+docUri,-1);
        else prefs.edit().remove("viewport:"+docUri).apply();
        if(viewportWord>=0)followSuspended=true;
        long drawStarted=SystemClock.elapsedRealtime();
        if(docUri.isEmpty())draw();
        else{
            // Do not build the heavy RecyclerView, its toolbar and all controls
            // twice: first with an empty book and then with the loaded session.
            FrameLayout loading=new FrameLayout(this);loading.setBackgroundColor(bg);
            TextView status=text("Загрузка книги…",16);status.setGravity(Gravity.CENTER);
            loading.addView(status,new FrameLayout.LayoutParams(-1,-1));
            setContentView(loading);SafeSystemBars.matchBackground(this,bg);
        }
        initialDrawElapsed=SystemClock.elapsedRealtime()-drawStarted;
        ReaderDiagnosticLog.event("READER_STARTUP","create_ms="+(SystemClock.elapsedRealtime()-launchStartedAt)+" first_ui_ms="+initialDrawElapsed);
        if(!docUri.isEmpty())load(Uri.parse(docUri),getIntent().getBooleanExtra("reset",false));
    }
    private void readPreferences(){
        wpm=prefs.getInt("wpm",350);portion=ReadingWordMap.portion(prefs.getInt("rsvp_portion",1));
        size=prefs.getInt("size",36);bg=prefs.getInt("bg",0xfff3e7d5);fg=prefs.getInt("fg",0xff30221e);font=prefs.getInt("font",1);
        mode=prefs.getInt("reading_mode",prefs.getBoolean("rsvp",true)?RSVP:NORMAL);mode=Math.max(0,Math.min(3,mode));
        rsvpScrollWords=prefs.getBoolean("rsvp_scroll_words",false);
        ttsLocal=prefs.getBoolean("tts_last_local",true);
        focusPoint=prefs.getBoolean("rsvp_focus_point",true);focusLinesEnabled=prefs.getBoolean("rsvp_focus_lines",true);
        focusColor=prefs.getInt("rsvp_focus_color",0xffed3030);lineColor=prefs.getInt("rsvp_line_color",0xff915439);
        linePosition=Math.max(0,Math.min(100,prefs.getInt("rsvp_line_position",50)));
        cursorBackground=prefs.getInt("cursor_background",0xff96583f);cursorText=prefs.getInt("cursor_text",NO_COLOR);
        cursorScale=Math.max(100,Math.min(180,prefs.getInt("cursor_scale",100)));
        sentenceHighlightColor=prefs.getInt("sentence_highlight_color",0xffedca43);
        sentenceHighlightOpacity=Math.max(0,Math.min(100,prefs.getInt("sentence_highlight_opacity",50)));
        normalSelectText=prefs.getInt("normal_select_text",0xff915439);
        normalSelectScale=Math.max(100,Math.min(180,prefs.getInt("normal_select_scale",110)));
        buttonColor=prefs.getInt("button_color",NO_COLOR);accentButtonColor=prefs.getInt("button_accent_color",NO_COLOR);

        delayRsvpEnabled=prefs.getBoolean("reading_start_delay_rsvp_enabled",false);
        delayCursorEnabled=prefs.getBoolean("reading_start_delay_cursor_enabled",false);
        delayRsvpSeconds=Math.max(1,Math.min(10,prefs.getInt("reading_start_delay_rsvp_seconds",2)));
        delayCursorSeconds=Math.max(1,Math.min(10,prefs.getInt("reading_start_delay_cursor_seconds",2)));
        pausePunctuationPercent=Math.max(0,Math.min(300,prefs.getInt("pausePunctuationPercent",60)));
        pauseLongPercent=Math.max(0,Math.min(300,prefs.getInt("pauseLongPercent",50)));
        pauseSentencePercent=Math.max(0,Math.min(400,prefs.getInt("pauseSentencePercent",120)));
        pauseParagraphPercent=Math.max(0,Math.min(400,prefs.getInt("pauseParagraphPercent",150)));
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private Typeface type(){return ReaderFontLibrary.selected(this,prefs);}
    private int panelColor(){return ReadingThemeCycle.isDark(bg)?0xff303238:0xfffff9f0;}
    private int buttonBg(){return buttonColor==NO_COLOR?panelColor():buttonColor;}
    private int accent(){return UiColorScheme.accent(this);}
    private int contrast(int value){return Color.red(value)*0.299+Color.green(value)*0.587+Color.blue(value)*0.114<140?Color.WHITE:0xff30221e;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(fg);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void add(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private ImageButton icon(int id,View.OnClickListener click){
        ImageButton b=new ImageButton(this);b.setImageResource(id);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(10),dp(10),dp(10),dp(10));b.setBackground(shape(buttonBg(),12));
        b.setColorFilter(contrast(buttonBg()));b.setOnClickListener(click);return b;
    }
    private void cycleReadingTheme(){
        ReadingThemeCycle.next(prefs);
        bg=prefs.getInt("bg",bg);fg=prefs.getInt("fg",fg);
        draw();
        saveLight(true);
    }
    private int modeDrawable(){return mode==NORMAL?R.drawable.ic_book:mode==RSVP?R.drawable.ic_mode_speedreading:mode==CURSOR?R.drawable.ic_mode_cursor:R.drawable.ic_microphone;}

    private int accentAtDraw=Integer.MIN_VALUE;
    private void draw(){
        dismissSelectionMenu();selectionHandles=null;selectionFrom=-1;selectionTo=-1;
        selectionDragging=false;selectionMenuAfterScroll=false;
        accentAtDraw=UiColorScheme.accent(this);
        lastAppliedFont=ReaderFontLibrary.choice(this,prefs);
        stop();
        sceneRoot=new FrameLayout(this);
        root=column();root.setPadding(0,0,0,0);root.setBackgroundColor(bg);
        sceneRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(sceneRoot);
        // Reset old insets when draw() replaces the scene; prevent cumulative padding.
        View androidContent=findViewById(android.R.id.content);
        if(androidContent!=null)androidContent.setPadding(0,0,0,0);
        SafeSystemBars.install(this);
        SafeSystemBars.matchBackground(this,bg);
        toolbarStack=column();
        int opaque=bg|0xff000000;
        android.graphics.drawable.GradientDrawable toolbarFade=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{opaque,(bg&0x00ffffff)|0xb0000000,(bg&0x00ffffff)});
        toolbarStack.setBackground(toolbarFade);
        toolbarStack.setPadding(dp(5),0,dp(5),dp(5));
        FrameLayout.LayoutParams toolbarOverlay=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        toolbarOverlay.setMargins(dp(8),0,dp(8),0);sceneRoot.addView(toolbarStack,toolbarOverlay);
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbarStack.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(44)));
        toolbar.addView(icon(R.drawable.ic_menu_lines,v->UniversalDrawer.show(this)),new LinearLayout.LayoutParams(dp(44),dp(44)));
        toolbarModeAction=icon(modeDrawable(),v->modeDialog());
        toolbarModeAction.setContentDescription("Режим чтения: Чтение, Скорочтение, Курсор, Озвучка");
        LinearLayout.LayoutParams toolbarModeLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        toolbarModeLp.leftMargin=dp(5);toolbar.addView(toolbarModeAction,1,toolbarModeLp);
        toolbarModeAction.setBackground(shape(accent(),12));toolbarModeAction.setColorFilter(contrast(accent()));
        // Colored XML VectorDrawable next to the navigation actions; never tint.
        toolbarThemeAction=icon(ReadingThemeCycle.icon(prefs),v->cycleReadingTheme());
        toolbarThemeAction.clearColorFilter();
        toolbarThemeAction.setContentDescription("Тема чтения: " + ReadingThemeCycle.name(prefs) + ". Нажмите для следующей темы");
        LinearLayout.LayoutParams themeLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        themeLp.leftMargin=dp(5);
        toolbar.addView(toolbarThemeAction,themeLp);
        toolbarBookName=text("",12);
        toolbarBookName.setSingleLine(true);toolbarBookName.setEllipsize(TextUtils.TruncateAt.END);
        toolbarBookName.setPadding(dp(9),dp(2),dp(9),dp(2));
        toolbarBookName.setGravity(Gravity.CENTER_VERTICAL);
        toolbarBookName.setBackground(shape(buttonBg(),10));
        toolbarBookName.setTextColor(contrast(buttonBg()));
        toolbarBookName.setOnClickListener(v->showBookmarks());
        // The title and chapter share one line BELOW the row of toolbar actions.
        // Keep a flexible spacer in the button row for stable right alignment.
        View titleSpacer=new View(this);
        toolbar.addView(titleSpacer,new LinearLayout.LayoutParams(0,dp(44),1));
        toolbarSpacer=titleSpacer;
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin=dp(2);
        toolbarStack.addView(toolbarBookName,titleLp);
        LinearLayout.LayoutParams bookmarkLp=new LinearLayout.LayoutParams(dp(44),dp(44));bookmarkLp.leftMargin=dp(5);
        // Bookmarks moved to overflow menu.
        toolbarEngineAction=icon(ttsLocal?R.drawable.ic_android_tts:R.drawable.ic_edge_tts,v->chooseTtsEngine());
        toolbarEngineAction.setContentDescription("Метод озвучивания");
        LinearLayout.LayoutParams engineLp=new LinearLayout.LayoutParams(dp(44),dp(44));engineLp.leftMargin=dp(5);
        toolbar.addView(toolbarEngineAction,engineLp);
        toolbarEngineAction.setBackground(shape(accent(),12));toolbarEngineAction.setColorFilter(contrast(accent()));
        toolbarVoiceAction=icon(R.drawable.ic_tts_engine,v->chooseTtsVoice());
        toolbarVoiceAction.setContentDescription("Голос озвучивания");
        LinearLayout.LayoutParams voiceLp=new LinearLayout.LayoutParams(dp(44),dp(44));voiceLp.leftMargin=dp(5);
        // Voice selection lives in the overflow menu; retain its handler for compatibility.
        FrameLayout sleepBadge=new FrameLayout(this);
        sleepBadge.setBackground(shape(buttonBg(),12));
        ImageView sleepIcon=new ImageView(this);sleepIcon.setImageResource(R.drawable.ic_sleep_timer);
        sleepIcon.setColorFilter(contrast(buttonBg()));
        sleepBadge.addView(sleepIcon);
        toolbarSleepValue=text("",11);toolbarSleepValue.setGravity(Gravity.CENTER);
        toolbarSleepValue.setSingleLine(true);toolbarSleepValue.setTextColor(contrast(buttonBg()));
        FrameLayout.LayoutParams sleepCounterLp=new FrameLayout.LayoutParams(-1,dp(16),Gravity.BOTTOM);
        sleepBadge.addView(toolbarSleepValue,sleepCounterLp);
        toolbarSleepValue.setVisibility(View.GONE);
        SleepTimerBadge.layout(sleepBadge,sleepIcon,toolbarSleepValue,false,22);
        sleepBadge.setOnClickListener(v->chooseTtsSleep());sleepBadge.setContentDescription("Таймер сна");
        toolbarSleepAction=sleepBadge;
        LinearLayout.LayoutParams sleepLp=new LinearLayout.LayoutParams(dp(44),dp(44));sleepLp.leftMargin=dp(5);
        toolbar.addView(sleepBadge,sleepLp);
        LinearLayout speedGauge=new LinearLayout(this);speedGauge.setGravity(Gravity.CENTER);speedGauge.setOrientation(LinearLayout.VERTICAL);
        speedGauge.setBackground(shape(buttonBg(),12));speedGauge.setPadding(dp(3),dp(2),dp(3),dp(2));
        ImageView speedNeedle=new ImageView(this);speedNeedle.setImageResource(R.drawable.ic_audio_speed);
        speedNeedle.setColorFilter(contrast(buttonBg()));
        speedGauge.addView(speedNeedle,new LinearLayout.LayoutParams(dp(20),dp(20)));
        speedView=text(String.valueOf(wpm),13);speedView.setTextColor(contrast(buttonBg()));
        speedView.setGravity(Gravity.CENTER);speedView.setPadding(0,0,0,0);
        speedGauge.addView(speedView);
        speedGauge.setContentDescription("Скорость чтения в словах в минуту");
        speedGauge.setOnClickListener(v->{
            if(mode==TTS){TtsWpm.dialog(this,()->{
                if(speedView!=null)speedView.setText(String.valueOf(TtsWpm.get(this)));
                if(ttsLocal)startService(new Intent(this,AudioPlaybackService.class).setAction("tts_wpm"));
                else if(ttsPlayer!=null)ttsPlayer.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(this)));
                updateProgress();
            });}else TtsWpm.dialogForReader(this,wpm,value->{
                wpm=value;if(speedView!=null)speedView.setText(String.valueOf(wpm));saveLight(true);
            });
        });
        toolbarWpm=speedGauge;
        LinearLayout.LayoutParams gaugeLp=new LinearLayout.LayoutParams(dp(44),dp(44));gaugeLp.leftMargin=dp(5);
        // The numeric WPM control belongs to the lower floating overlay, not the packed toolbar.
        LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(44),dp(44));settingsLp.leftMargin=dp(5);
        toolbar.addView(icon(R.drawable.ic_more_vertical,v->{
            ReaderSheets.overflow(this,mode==TTS,which->{if(which==0)showNotes();
                    else if(which==1)showBookmarks();
                    else if(mode==TTS&&which==2)chooseTtsVoice();
                    else if(which==(mode==TTS?4:3))openEdgeDictionary();
                    else if(which==(mode==TTS?5:4))showBookSearch();
                    else if(which==(mode==TTS?6:5))EdgeFolderPicker.show(this);
                    else if(!docUri.isEmpty())startActivity(new Intent(this,EdgeChapterDownloadsActivity.class)
                        .putExtra("book_uri",docUri).putExtra("book_title",title));});
        }),settingsLp);
        // The bookmark list is available via a dedicated toolbar/book menu action.
        // A long press on the book title does not alter the active reading position.
        toolbarChapterName=null; // Chapter appended after a middle dot inside the toolbar.
        if(words.length==0){applySystemBars(false);
            TextView intro=text("EPUB · FB2 · HTML\n\nОткройте книгу, чтобы начать чтение.",20);
            intro.setPadding(dp(24),dp(24),dp(24),dp(24));add(root,intro);
            Button pick=new Button(this);pick.setText("Открыть книгу");pick.setOnClickListener(v->pick());add(root,pick);return;
        }
        // Book cover and metadata are only displayed at the beginning of the book.
        bookHeader=column();bookHeader.setPadding(dp(7),dp(10),dp(7),dp(8));
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams coverLp=new LinearLayout.LayoutParams(-1,
            Math.min(dp(580),Math.round(getResources().getDisplayMetrics().widthPixels*1.44f)));
        bookHeader.addView(cover,coverLp);
        CoverImageLoader.show(cover,docUri,true);
        bookTitle=text(title,23);bookTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        bookTitle.setPadding(dp(5),dp(9),0,0);add(bookHeader,bookTitle);
        TextView meta=text(author+(author.isEmpty()?"":" · ")+words.length+" слов",13);
        meta.setPadding(dp(5),dp(2),0,dp(5));add(bookHeader,meta);
        // Keep the header in the view tree even when opening a book at a saved
        // middle position. If the reader scrolls back to the beginning,
        // the cover and metadata must become visible again without recreating UI.
        // The cover and metadata are adapter item 0, so they scroll with the book text.
        bookHeader.setVisibility(View.VISIBLE);
        if(toolbarBookName!=null)toolbarBookName.setText(title);
        updateChapterTitle();
        modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);
        progressView=text("",12);progressView.setGravity(Gravity.CENTER_VERTICAL);
        selectWordButton=new ImageView(this);selectWordButton.setImageResource(R.drawable.ic_select_word);
        selectWordButton.setPadding(dp(11),dp(11),dp(11),dp(11));selectWordButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        selectWordButton.setContentDescription("Выбор слова");
        selectWordButton.setOnClickListener(v->{selecting=!selecting;normalChromeVisible=true;
            // Enabling selection is NOT an implicit tap on an old word.
            selectedWordActive=false;updateSelectWordButton();refreshHighlight(index);applyFullscreenUi();});
        LinearLayout.LayoutParams selectionLp=new LinearLayout.LayoutParams(dp(44),dp(44));selectionLp.leftMargin=dp(5);
        toolbar.addView(selectWordButton,Math.max(1,toolbar.getChildCount()-2),selectionLp);
        modeIcon=null;
        readingFrame=column();readingFrame.setBackgroundColor(bg);
        readingFrame.setPadding(0,0,0,0);
        LinearLayout.LayoutParams frameLp=new LinearLayout.LayoutParams(-1,0,1);root.addView(readingFrame,frameLp);
        rsvpPane=new FrameLayout(this);wordView=text("",size);wordView.setGravity(Gravity.CENTER);
        rsvpPane.addView(wordView,new FrameLayout.LayoutParams(-1,-1));
        focusLines=new FocusLinesView(this);focusLines.setClickable(false);
        rsvpPane.addView(focusLines,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams rsvpLp=new LinearLayout.LayoutParams(-1,dp(192));rsvpLp.bottomMargin=dp(8);readingFrame.addView(rsvpPane,rsvpLp);
        wordView.setOnTouchListener((v,e)->{
            if(mode!=RSVP||!playing)return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){stop();swipeStartY=e.getY();swipeStartIndex=index;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){int old=index;index=map.clamp(swipeStartIndex+Math.round((swipeStartY-e.getY())/dp(25)));
                if(old!=index){updateWord();refreshHighlight(old);}return true;}
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){saveLight(true);return true;}
            return true;
        });
        layoutManager=new LinearLayoutManager(this);
        textList=new RecyclerView(this);textList.setLayoutManager(layoutManager);
        textList.setItemAnimator(null);textList.setHasFixedSize(false);
        textList.setBackgroundColor(Color.TRANSPARENT);
        chunkAdapter=new ChunkAdapter();textList.setAdapter(chunkAdapter);
        readingFrame.addView(textList,new LinearLayout.LayoutParams(-1,0,1));
        // Reader-only top caption, not a SYSTEM_ALERT_WINDOW. It does not
        // intercept gestures outside its own clickable surface.
        ttsFloatingCaption=new TextView(this);
        ttsFloatingCaption.setTextSize(16);
        ttsFloatingCaption.setTextColor(contrast(buttonBg()));
        ttsFloatingCaption.setBackground(shape(buttonBg(),16));
        ttsFloatingCaption.setPadding(dp(13),dp(10),dp(13),dp(10));
        ttsFloatingCaption.setMaxLines(Integer.MAX_VALUE);
        ttsFloatingCaption.setMaxHeight(dp(188));
        ttsFloatingCaption.setEllipsize(null);
        ttsFloatingCaption.setSingleLine(false);
        ttsFloatingCaption.setElevation(dp(12));
        ttsFloatingCaption.setGravity(Gravity.CENTER_VERTICAL);
        ttsFloatingCaption.setVisibility(View.GONE);
        ttsFloatingCaption.setOnClickListener(v->returnToActiveWord());
        FrameLayout.LayoutParams captionLp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        captionLp.setMargins(dp(8),dp(44),dp(8),0);
        sceneRoot.addView(ttsFloatingCaption,captionLp);
        textList.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrollStateChanged(RecyclerView rv,int state){
                if(state==RecyclerView.SCROLL_STATE_DRAGGING){
                    if(selectionFrom>=0&&!selectionDragging){selectionMenuAfterScroll=true;dismissSelectionMenu();}
                    chteicoTouch();userScrolling=true;manualViewportMovement=true;followSuspended=true;tocPreviewGeneration++;
                    if(mode==TTS)updateFloatingCaption();
                    if(mode==RSVP){stop();scrollWordAccumulator=0;}
                }
                if(state==RecyclerView.SCROLL_STATE_IDLE){
                    userScrolling=false;
                    if(manualViewportMovement){manualViewportMovement=false;rememberVisibleViewport();}
                    if(mode==RSVP){scrollWordAccumulator=0;setRsvpContextVisible(!playing);}
                    saveLight(true);
                    if(selectionMenuAfterScroll&&!selectionDragging&&selectionFrom>=0){
                        selectionMenuAfterScroll=false;
                        rv.post(()->{if(!selectionDragging&&selectionFrom>=0)showSelectionMenu();});
                    }
                }
            }
            @Override public void onScrolled(RecyclerView rv,int dx,int dy){
                if(selectionHandles!=null)selectionHandles.invalidate();
                if(dy!=0&&selectionPopup!=null&&!selectionDragging){
                    selectionMenuAfterScroll=true;dismissSelectionMenu();
                }
                if(userScrolling&&dy!=0)chteicoTouch();
                if(userScrolling&&!restoringScroll&&Math.abs(dy)>dp(1)){
                    setToolbarExpanded(dy<0);
                    if(contentsAction!=null)contentsAction.setVisibility(dy<0?View.VISIBLE:View.INVISIBLE);
                    if(toolbarWpm!=null)toolbarWpm.setVisibility(dy<0&&mode!=NORMAL?View.VISIBLE:View.INVISIBLE);

                }
                if(userScrolling&&mode==CURSOR&&playing&&!isActiveWordVisible()){
                    // Do not move the viewport; the reader must press Play explicitly.
                    stop();
                }
                // A dragged/flung viewport can lose the sentence after the finger is lifted.
                // Check every scroll movement, not only the initial DRAGGING event.
                if(followSuspended&&mode==TTS)updateFloatingCaption();
                // Scrolling the book is viewport navigation in every mode.
                // Even with the optional RSVP scroll control enabled, a chapter
                // preview must not silently seek the active reading cursor.
                // Selecting a new word requires a tap on that word.
            }
        });
        bottomBar=new FrameLayout(this);bottomBar.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,dp(74),Gravity.BOTTOM);
        bottomLp.setMargins(dp(8),0,dp(8),dp(8));sceneRoot.addView(bottomBar,bottomLp);
        LinearLayout left=new LinearLayout(this);left.setGravity(Gravity.CENTER_VERTICAL);
        left.setBackground(shape(buttonBg(),13));contentsAction=left;
        left.setOnClickListener(v->showToc());
        FrameLayout.LayoutParams leftLp=new FrameLayout.LayoutParams(Math.max(dp(105),
            (getResources().getDisplayMetrics().widthPixels/2)-dp(74)),dp(54),Gravity.LEFT|Gravity.CENTER_VERTICAL);
        bottomBar.addView(left,leftLp);
        contentsIcon=icon(R.drawable.ic_list_audio,v->showToc());contentsIcon.setContentDescription("Оглавление и прогресс");
        contentsIcon.setBackground(shape(accent(),12));contentsIcon.setColorFilter(contrast(accent()));
        left.addView(contentsIcon,new LinearLayout.LayoutParams(dp(42),dp(44)));
        progressView=text("",10);progressView.setTextColor(contrast(buttonBg()));
        progressView.setGravity(Gravity.CENTER);progressView.setMaxLines(2);
        left.addView(progressView,new LinearLayout.LayoutParams(0,dp(52),1));
        controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        bottomBar.addView(controls,new FrameLayout.LayoutParams(dp(70),dp(70),Gravity.CENTER));
        rsvpPlayFrame=new FrameLayout(this);rsvpPlayFrame.setBackground(shape(accent(),12));
        playIcon=new ImageView(this);playIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);playIcon.setColorFilter(contrast(accent()));
        rsvpPlayFrame.addView(playIcon,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
        rsvpPlayFrame.setOnClickListener(v->togglePlayback());
        controls.addView(rsvpPlayFrame,new LinearLayout.LayoutParams(dp(44),dp(44)));
        floatingPlayButton=null;floatingPlayIcon=null;
        // A shared floating WPM button on the right; Play/Pause stays geometrically centered.
        FrameLayout.LayoutParams lowerWpm=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        lowerWpm.rightMargin=dp(2);bottomBar.addView(toolbarWpm,lowerWpm);
        toolbarWpm.setBackground(shape(buttonBg(),12));
        for(int i=0;i<toolbarWpm.getChildCount();i++){View child=toolbarWpm.getChildAt(i);
            if(child instanceof ImageView)((ImageView)child).setColorFilter(contrast(buttonBg()));
            if(child instanceof TextView)((TextView)child).setTextColor(contrast(buttonBg()));}
        // Selection handles live in the reader scene, never in a system overlay.
        selectionHandles=new ReaderSelectionController(this,new ReaderSelectionController.Host(){
            public float[][] handlePositions(){return selectionPositions();}
            public boolean dragHandle(boolean start,float x,float y){return moveSelectionHandle(start,x,y);}
            public boolean autoScrollForHandle(boolean start,float x,float y){return autoScrollSelection(start,x,y);}
            public void startedHandleDrag(){
                selectionDragging=true;selectionMenuAfterScroll=false;dismissSelectionMenu();
                if(textList!=null)textList.stopScroll();
            }
            public void finishedHandleDrag(boolean canceled){
                selectionDragging=false;
                if(!canceled&&sceneRoot!=null)sceneRoot.post(()->{if(selectionFrom>=0)showSelectionMenu();});
            }
            public int handleColor(){return accent();}
        });
        sceneRoot.addView(selectionHandles,new FrameLayout.LayoutParams(-1,-1));
        speedPanel=null;
        updateModeUI();updatePlayButton();
        // Jump to the indexed chunk, not to the pixel position of an enormous TextView.
        textList.post(()->{if(viewportWord>=0)browseToWord(viewportWord);
            else if(index<=0)layoutManager.scrollToPositionWithOffset(0,0);
            else ensureWordVisible(true);});
    }
    private void cancelPendingLongPress(){
        if(pendingLongPress!=null){handler.removeCallbacks(pendingLongPress);pendingLongPress=null;}
    }
    private int wordAt(ChunkAdapter.Holder holder,float x,float y){
        int row=holder.getBindingAdapterPosition()-1;
        if(row<0||chunks==null||row>=chunks.size()||holder.value==null)return -1;
        android.text.Layout layout=holder.value.getLayout();if(layout==null)return -1;
        int line=layout.getLineForVertical(Math.max(0,(int)y-holder.value.getTotalPaddingTop()));
        int offset=layout.getOffsetForHorizontal(line,Math.max(0,x-holder.value.getTotalPaddingLeft()));
        return map.atCharacter(chunks.get(row).startChar+offset);
    }
    private void beginTextSelection(int word){
        if(words.length==0)return;
        selectionFrom=selectionTo=map.clamp(word);focusedNoteId=0;
        updateVisibleAnnotations();
        if(selectionHandles!=null)selectionHandles.invalidate();
        selectionMenuAfterScroll=false;showSelectionMenu();
    }
    private void dismissSelectionMenu(){
        if(selectionPopup!=null){try{selectionPopup.dismiss();}catch(Exception ignored){}selectionPopup=null;}
    }
    private void clearSelection(){
        dismissSelectionMenu();selectionFrom=selectionTo=-1;focusedNoteId=0;
        selectionMenuAfterScroll=false;selectionDragging=false;
        updateVisibleAnnotations();if(selectionHandles!=null)selectionHandles.invalidate();
    }
    private void updateVisibleAnnotations(){
        if(textList==null||chunkAdapter==null)return;
        for(int i=0;i<textList.getChildCount();i++){
            RecyclerView.ViewHolder h=textList.getChildViewHolder(textList.getChildAt(i));
            if(h instanceof ChunkAdapter.Holder){int row=h.getBindingAdapterPosition()-1;
                if(row>=0&&row<chunks.size())((ChunkAdapter.Holder)h).highlight(row);}
        }
    }
    private void renderAnnotations(ChunkAdapter.Holder holder,BookChunkIndex.Chunk chunk){
        int opacity=Math.max(15,Math.min(95,prefs.getInt("annotation_opacity",38)));
        int first=chunk.startChar,last=chunk.endChar;
        java.util.ArrayList<Integer> noteEnds=new java.util.ArrayList<>();
        java.util.ArrayList<Integer> bookmarkStarts=new java.util.ArrayList<>();
        for(ReaderAnnotationsStore.Annotation annotation:annotations){
            if(annotation.fromWord>=annotation.toWord||annotation.fromWord>=map.size())continue;
            int a=map.starts[map.clamp(annotation.fromWord)],b=map.ends[map.clamp(annotation.toWord-1)];
            if(a>=last||b<=first)continue;
            int start=Math.max(a,first)-first,end=Math.min(b,last)-first;
            if(annotation.color!=0){int color=(annotation.color&0x00ffffff)|(((opacity*255)/100)<<24);
                holder.span(new BackgroundColorSpan(color),start,end);}
            if(!annotation.note.isEmpty()){
                holder.span(new UnderlineSpan(),start,end);
                // The right-margin glyph appears only on the note's LAST text line.
                // Group same-line notes into one icon with a count, not overlapping icons.
                if(b>first&&b<=last)noteEnds.add(b-first);
            }
        }
        for(ReaderBookmarks.Item mark:bookmarkItems){
            if(mark.word<0||mark.word>=map.size())continue;
            int anchor=map.starts[mark.word];
            if(anchor>=first&&anchor<last)bookmarkStarts.add(anchor-first);
        }
        if(!noteEnds.isEmpty()||!bookmarkStarts.isEmpty()){
            int[] notes=new int[noteEnds.size()],marks=new int[bookmarkStarts.size()];
            for(int i=0;i<notes.length;i++)notes[i]=noteEnds.get(i);
            for(int i=0;i<marks.length;i++)marks[i]=bookmarkStarts.get(i);
            holder.span(new ReaderNoteIndicatorSpan(this,UiColorScheme.accent(this),notes,marks),0,holder.content.length());
        }
        if(selectionFrom>=0&&selectionTo>=selectionFrom&&selectionTo<map.size()){
            int a=map.starts[selectionFrom],b=map.ends[selectionTo];
            if(a<last&&b>first)holder.span(new BackgroundColorSpan(0x667a8cbb),
                Math.max(a,first)-first,Math.min(b,last)-first);
        }
    }
    private float[] positionForWord(int word,boolean beginning){
        if(chunks==null||textList==null||sceneRoot==null||word<0||word>=map.size())return null;
        int row=chunks.chunkForWord(word);
        RecyclerView.ViewHolder raw=textList.findViewHolderForAdapterPosition(row+1);
        if(!(raw instanceof ChunkAdapter.Holder))return null;
        TextView view=((ChunkAdapter.Holder)raw).value;
        if(view==null||view.getLayout()==null)return null;
        int offset=(beginning?map.starts[word]:map.ends[word])-chunks.get(row).startChar;
        android.text.Layout layout=view.getLayout();offset=Math.min(Math.max(0,offset),view.length());
        int line=layout.getLineForOffset(Math.max(0,Math.min(view.length()-1,beginning?offset:offset-1)));
        int[] where=new int[2],rootWhere=new int[2];view.getLocationOnScreen(where);sceneRoot.getLocationOnScreen(rootWhere);
        float x=where[0]-rootWhere[0]+view.getTotalPaddingLeft()+layout.getPrimaryHorizontal(offset);
        float y=where[1]-rootWhere[1]+view.getTotalPaddingTop()+(beginning?layout.getLineTop(line):layout.getLineBottom(line));
        int[] listOnScreen=new int[2];textList.getLocationOnScreen(listOnScreen);
        int absoluteY=(int)(y+rootWhere[1]);
        if(absoluteY<listOnScreen[1]+dp(1)||absoluteY>listOnScreen[1]+textList.getHeight()-dp(1))return null;
        return new float[]{x,y};
    }
    private float[][] selectionPositions(){
        if(selectionFrom<0||selectionTo<selectionFrom)return null;
        return new float[][]{positionForWord(selectionFrom,true),positionForWord(selectionTo,false)};
    }
    /** Moves the dragged endpoint across RecyclerView chunks; swap start/end roles at crossings. */
    private boolean moveSelectionHandle(boolean start,float sceneX,float sceneY){
        if(selectionFrom<0||sceneRoot==null||textList==null)return start;
        int[] rootPosition=new int[2],listPosition=new int[2];
        sceneRoot.getLocationOnScreen(rootPosition);textList.getLocationOnScreen(listPosition);
        float x=sceneX+rootPosition[0]-listPosition[0],y=sceneY+rootPosition[1]-listPosition[1];
        if(textList.getHeight()<=0)return start;
        float clampedY=Math.max(dp(2),Math.min(textList.getHeight()-dp(2),y));
        View child=textList.findChildViewUnder(Math.max(dp(1),Math.min(textList.getWidth()-dp(2),x)),clampedY);
        if(child==null)return start;
        RecyclerView.ViewHolder raw=textList.getChildViewHolder(child);
        if(!(raw instanceof ChunkAdapter.Holder))return start;
        ChunkAdapter.Holder holder=(ChunkAdapter.Holder)raw;
        TextView line=holder.value;if(line==null)return start;
        int[] absolute=new int[2];line.getLocationOnScreen(absolute);
        int word=wordAt(holder,sceneX+rootPosition[0]-absolute[0],
            clampedY+listPosition[1]-absolute[1]);
        if(word<0)return start;
        if(start){
            if(word>selectionTo){selectionFrom=selectionTo;selectionTo=word;start=false;}
            else selectionFrom=word;
        }else{
            if(word<selectionFrom){selectionTo=selectionFrom;selectionFrom=word;start=true;}
            else selectionTo=word;
        }
        dismissSelectionMenu();updateVisibleAnnotations();
        if(selectionHandles!=null)selectionHandles.invalidate();
        return start;
    }
    /** Called once per animation frame only while the user is holding a selection handle. */
    private boolean autoScrollSelection(boolean start,float sceneX,float sceneY){
        if(!selectionDragging||sceneRoot==null||textList==null)return false;
        int[] rootPos=new int[2],listPos=new int[2];sceneRoot.getLocationOnScreen(rootPos);
        textList.getLocationOnScreen(listPos);
        float y=sceneY+rootPos[1]-listPos[1];
        int edge=dp(62),height=textList.getHeight();if(height<=edge*2)return false;
        int delta=0;
        if(y<edge){delta=-Math.max(dp(2),Math.min(dp(24),Math.round((edge-y)/5f)));}
        else if(y>height-edge){delta=Math.max(dp(2),Math.min(dp(24),Math.round((y-(height-edge))/5f)));}
        if(delta==0||!textList.canScrollVertically(delta>0?1:-1))return false;
        textList.scrollBy(0,delta);
        return true;
    }
    private void showSelectionMenu(){
        dismissSelectionMenu();if(selectionFrom<0||sceneRoot==null)return;
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(dp(3),dp(3),dp(3),dp(3));
        actions.setBackground(shape(panelColor(),13));actions.setElevation(dp(9));
        final int[] icons={R.drawable.ic_reader_selection_color,R.drawable.ic_reader_selection_copy,
            R.drawable.ic_reader_selection_note,R.drawable.ic_reader_selection_dictionary,R.drawable.ic_bookmark};
        final String[] names={"Цвет","Копировать","Заметка","Словарь","Закладка"};
        final Runnable[] callbacks={this::pickSelectionColor,this::copySelectedText,this::editSelectedNote,
            this::dictionarySelected,this::bookmarkSelection};
        for(int i=0;i<icons.length;i++){
            final Runnable action=callbacks[i];LinearLayout tile=new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
            tile.setBackground(shape(UiColorScheme.blend(panelColor(),fg,.045f),11));
            ImageView image=new ImageView(this);image.setImageResource(icons[i]);image.setColorFilter(contrast(panelColor()));
            tile.addView(image,new LinearLayout.LayoutParams(dp(20),dp(20)));
            TextView label=text(names[i],10);label.setTextColor(contrast(panelColor()));label.setGravity(Gravity.CENTER);
            tile.addView(label,new LinearLayout.LayoutParams(-1,dp(19)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(74),dp(55));
            if(i>0)lp.leftMargin=dp(3);actions.addView(tile,lp);
            tile.setOnClickListener(v->{dismissSelectionMenu();action.run();});
        }
        int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(16),dp(385)),height=dp(63);
        HorizontalScrollView actionScroll=new HorizontalScrollView(this);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.addView(actions,new HorizontalScrollView.LayoutParams(-2,-1));
        selectionPopup=new PopupWindow(actionScroll,width,height,false);
        selectionPopup.setBackgroundDrawable(shape(panelColor(),13));selectionPopup.setOutsideTouchable(true);
        selectionPopup.setElevation(dp(10));
        float[][] positions=selectionPositions();
        float[] begin=positions==null?null:(positions[0]!=null?positions[0]:positions[1]);
        if(begin==null){selectionPopup=null;return;}
        int[] screen=new int[2];sceneRoot.getLocationOnScreen(screen);
        int x=(int)(screen[0]+begin[0]-width/2);
        x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-width,x));
        int y=(int)(screen[1]+begin[1]-height-dp(16));
        if(y<dp(20))y=(int)(screen[1]+begin[1]+dp(32));
        selectionPopup.showAtLocation(sceneRoot,Gravity.NO_GRAVITY,x,y);
    }
    private String selectedText(){
        if(selectionFrom<0||selectionTo<selectionFrom||selectionTo>=map.size())return "";
        return body.substring(map.starts[selectionFrom],map.ends[selectionTo]);
    }
    private String selectedContext(){
        if(selectionFrom<0||selectionFrom>=map.size())return "";
        SentenceChunks.Chunk sentence=ttsSentenceIndex.atWordRange(selectionFrom);
        if(sentence==null)return "";
        int from=Math.max(0,Math.min(body.length(),sentence.fromChar));
        int to=Math.max(from,Math.min(body.length(),sentence.toChar));
        return body.substring(from,Math.min(to,from+240));
    }
    private String bookAnnotationId(){return BookUriMigration.legacy(this,docUri);}
    private ReaderAnnotationsStore.Annotation annotationForRange(){
        if(focusedNoteId>0)for(ReaderAnnotationsStore.Annotation item:annotations)
            if(item.id==focusedNoteId)return item;
        for(ReaderAnnotationsStore.Annotation item:annotations)
            if(item.fromWord==selectionFrom&&item.toWord==selectionTo+1)return item;
        return null;
    }
    private void openAnnotationById(long id){
        for(ReaderAnnotationsStore.Annotation annotation:annotations)if(annotation.id==id){
            focusedNoteId=annotation.id;
            selectionFrom=annotation.fromWord;selectionTo=annotation.toWord-1;
            updateVisibleAnnotations();editSelectedNote();return;
        }
        Toast.makeText(this,"Заметка не найдена",Toast.LENGTH_SHORT).show();
    }
    private void saveSelectionAnnotation(int color,String note){
        if(selectionFrom<0)return;
        ReaderAnnotationsStore.Annotation existing=annotationForRange();
        ReaderAnnotationsStore.Annotation item=new ReaderAnnotationsStore.Annotation(existing==null?0:existing.id,
            bookAnnotationId(),selectionFrom,selectionTo+1,selectedText(),selectedContext(),color,note);
        try{
            if(annotationStore==null)annotationStore=new ReaderAnnotationsStore(this);
            annotationStore.save(item);annotations=annotationStore.load(bookAnnotationId());
            clearSelection();
        }catch(Exception error){Toast.makeText(this,"Не удалось сохранить: "+error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private void pickSelectionColor(){
        if(selectionFrom<0)return;
        String[] names={"Жёлтый","Зелёный","Голубой","Розовый","Удалить выделение"};
        int[] defaults={0xffe8d46b,0xff8ccba4,0xff8ab4ec,0xffe3a6c8};
        new SwipeDialogs.Builder(this).setTitle("Выделить цветом")
            .setItems(names,(d,choice)->{
                if(choice==4){ReaderAnnotationsStore.Annotation old=annotationForRange();
                    if(old!=null){if(old.note.isEmpty()){
                        if(annotationStore==null)annotationStore=new ReaderAnnotationsStore(this);
                        annotationStore.delete(old.id,bookAnnotationId());annotations=annotationStore.load(bookAnnotationId());clearSelection();
                    }else saveSelectionAnnotation(0x00000000,old.note);}return;}
                int color=prefs.getInt("annotation_color_"+choice,defaults[choice]);
                ReaderAnnotationsStore.Annotation old=annotationForRange();
                saveSelectionAnnotation(color,old==null?"":old.note);
            }).setNegativeButton("Отмена",null).show();
    }
    private void copySelectedText(){
        String selection=selectedText();if(selection.isEmpty())return;
        ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(
            android.content.ClipData.newPlainText("Цитата",selection));
        Toast.makeText(this,"Скопировано",Toast.LENGTH_SHORT).show();clearSelection();
    }
    private void editSelectedNote(){
        if(selectionFrom<0)return;
        ReaderAnnotationsStore.Annotation old=annotationForRange();
        LinearLayout layout=column();layout.setPadding(dp(18),dp(8),dp(18),0);
        TextView quote=text(selectedText(),13);quote.setMaxLines(4);quote.setPadding(dp(4),dp(7),dp(4),dp(7));add(layout,quote);
        EditText editor=new EditText(this);editor.setMinLines(3);editor.setMaxLines(8);
        editor.setTextColor(fg);editor.setHint("Текст заметки");editor.setText(old==null?"":old.note);add(layout,editor);
        AlertDialog.Builder build=new SwipeDialogs.Builder(this).setTitle("Заметка к тексту").setView(layout)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->
                saveSelectionAnnotation(old==null?prefs.getInt("annotation_color_0",0xffe8d46b):old.color,
                    editor.getText().toString()));
        if(old!=null)build.setNeutralButton("Удалить",(d,w)->{
            if(annotationStore==null)annotationStore=new ReaderAnnotationsStore(this);
            annotationStore.delete(old.id,bookAnnotationId());annotations=annotationStore.load(bookAnnotationId());clearSelection();});
        AlertDialog dialog=build.create();dialog.show();
        if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void dictionarySelected(){
        String chosen=selectedText();if(chosen.trim().isEmpty())return;
        EdgeDictionaryRuleDialog.show(this,null,EdgeDictionaryStore.REPLACE,chosen,selectedContext(),chosen,false,this::clearSelection);
    }
    /** Only the note anchors on the tapped visual line; no full-book re-layout. */
    private java.util.ArrayList<ReaderAnnotationsStore.Annotation> notesAtLine(
        ChunkAdapter.Holder holder,float localY){
        java.util.ArrayList<ReaderAnnotationsStore.Annotation> found=new java.util.ArrayList<>();
        if(holder.value==null||holder.value.getLayout()==null||map.size()==0)return found;
        int row=holder.getBindingAdapterPosition()-1;
        if(row<0||chunks==null||row>=chunks.size())return found;
        BookChunkIndex.Chunk chunk=chunks.get(row);
        android.text.Layout layout=holder.value.getLayout();
        int tapped=layout.getLineForVertical(Math.max(0,(int)localY-holder.value.getTotalPaddingTop()));
        for(ReaderAnnotationsStore.Annotation note:annotations){
            if(note.note==null||note.note.isEmpty()||note.toWord<=0)continue;
            int absolute=map.ends[map.clamp(note.toWord-1)];
            if(absolute<=chunk.startChar||absolute>chunk.endChar)continue;
            int offset=Math.max(0,Math.min(holder.value.length()-1,absolute-chunk.startChar-1));
            if(layout.getLineForOffset(offset)==tapped)found.add(note);
        }
        return found;
    }
    private java.util.ArrayList<ReaderBookmarks.Item> bookmarksAtLine(ChunkAdapter.Holder holder,float localY){
        java.util.ArrayList<ReaderBookmarks.Item> found=new java.util.ArrayList<>();
        if(holder.value==null||holder.value.getLayout()==null||map.size()==0)return found;
        int row=holder.getBindingAdapterPosition()-1;
        if(row<0||chunks==null||row>=chunks.size())return found;
        BookChunkIndex.Chunk chunk=chunks.get(row);android.text.Layout layout=holder.value.getLayout();
        int line=layout.getLineForVertical(Math.max(0,(int)localY-holder.value.getTotalPaddingTop()));
        for(ReaderBookmarks.Item mark:bookmarkItems){
            if(mark.word<0||mark.word>=map.size())continue;
            int anchor=map.starts[mark.word];
            if(anchor<chunk.startChar||anchor>=chunk.endChar)continue;
            int offset=Math.max(0,Math.min(holder.value.length()-1,anchor-chunk.startChar));
            if(layout.getLineForOffset(offset)==line)found.add(mark);
        }
        return found;
    }
    private void showBookmarkChoices(java.util.List<ReaderBookmarks.Item> marks){
        if(marks.isEmpty())return;
        ReaderBookmarks.Item chosen=marks.get(0);
        String excerpt=chosen.excerpt==null||chosen.excerpt.isEmpty()?"Закладка":chosen.excerpt;
        new SwipeDialogs.Builder(this).setTitle("Закладка").setMessage(excerpt)
            .setPositiveButton("Перейти",(dialog,which)->browseToWord(chosen.word))
            .setNeutralButton("Удалить",(dialog,which)->{
                ArrayList<ReaderBookmarks.Item> all=ReaderBookmarks.list(this,docUri);
                all.removeIf(b->b.word==chosen.word);ReaderBookmarks.store(this,docUri,all);
                bookmarkItems=ReaderBookmarks.list(this,docUri);updateVisibleAnnotations();
            }).setNegativeButton("Отмена",null).show();
    }
    private void showNoteChoices(java.util.List<ReaderAnnotationsStore.Annotation> candidates,View anchor,float localY){
        if(candidates.isEmpty())return;
        if(candidates.size()==1){openAnnotationById(candidates.get(0).id);return;}
        LinearLayout box=column();box.setPadding(dp(9),dp(8),dp(9),dp(9));
        box.setBackground(shape(panelColor(),16));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(top,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView heading=text("Заметки ("+candidates.size()+")",14);heading.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=text("×",22);close.setPadding(dp(10),0,dp(10),0);top.addView(close);
        HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout items=new LinearLayout(this);items.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(items);box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(88)));
        final PopupWindow[] popup=new PopupWindow[1];
        close.setOnClickListener(v->{if(popup[0]!=null)popup[0].dismiss();});
        for(ReaderAnnotationsStore.Annotation note:candidates){
            LinearLayout tile=column();tile.setPadding(dp(10),dp(7),dp(10),dp(7));
            int shade=note.color!=0?UiColorScheme.blend(panelColor(),note.color,.18f):UiColorScheme.blend(panelColor(),fg,.07f);
            tile.setBackground(shape(shade,12));
            ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_reader_selection_note);
            icon.setColorFilter(accent());tile.addView(icon,new LinearLayout.LayoutParams(dp(19),dp(19)));
            TextView text=text(note.note,12);text.setTextColor(contrast(shade));text.setMaxLines(3);
            text.setEllipsize(TextUtils.TruncateAt.END);tile.addView(text);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(145),dp(82));lp.rightMargin=dp(6);items.addView(tile,lp);
            tile.setOnClickListener(v->{if(popup[0]!=null)popup[0].dismiss();openAnnotationById(note.id);});
        }
        int screenWidth=getResources().getDisplayMetrics().widthPixels;
        int width=Math.min(screenWidth-dp(24),dp(390));
        PopupWindow window=new PopupWindow(box,width,dp(137),true);popup[0]=window;
        window.setBackgroundDrawable(shape(panelColor(),16));window.setOutsideTouchable(true);
        window.setElevation(dp(11));
        int[] location=new int[2];anchor.getLocationOnScreen(location);
        int x=Math.max(dp(8),screenWidth-width-dp(10));
        int y=(int)(location[1]+localY)+dp(24);
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        if(y+dp(150)>screenHeight-dp(16))y=(int)(location[1]+localY)-dp(153);
        window.showAtLocation(sceneRoot,Gravity.NO_GRAVITY,x,Math.max(dp(25),y));
    }
    private class ChunkAdapter extends RecyclerView.Adapter<ChunkAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final TextView value;
            Spannable content;
            final ArrayList<Object> applied=new ArrayList<>();
            Holder(TextView t){super(t);value=t;}
            Holder(FrameLayout frame){super(frame);value=null;}
            void highlight(int chunkId){
                if(content==null||value==null)return;
                for(Object span:applied)content.removeSpan(span);applied.clear();
                if(words.length==0)return;
                BookChunkIndex.Chunk c=chunks.get(chunkId);
                // Persistent marks are independent of the changing playback highlight.
                renderAnnotations(this,c);
                int first=map.clamp(index);
                if(first<c.startWord||first>=c.endWord)return;
                int endWord=Math.min(c.endWord-1,Math.min(words.length-1,first+portion-1));
                int from=map.starts[first]-c.startChar,to=map.ends[endWord]-c.startChar;
                if(from<0||to>content.length()||to<=from)return;
                if(mode==CURSOR){
                    if(cursorBackground!=NO_COLOR)span(new BackgroundColorSpan(cursorBackground),from,to);
                    if(cursorText!=NO_COLOR)span(new ForegroundColorSpan(cursorText),from,to);
                    if(cursorScale>100)span(new RelativeSizeSpan(cursorScale/100f),from,to);
                }else if(mode==TTS){
                    SentenceChunks.Chunk sentence=ttsSentenceIndex.atWordRange(first);
                    if(sentence!=null){
                        int sentenceFrom=Math.max(0,sentence.fromChar-c.startChar);
                        int sentenceTo=Math.min(content.length(),sentence.toChar-c.startChar);
                        if(sentenceTo>sentenceFrom){
                            int alpha=(sentenceHighlightOpacity*255+50)/100;
                            int shade=(sentenceHighlightColor&0x00ffffff)|(alpha<<24);
                            span(new BackgroundColorSpan(shade),sentenceFrom,sentenceTo);
                        }
                    }
                    // The word is stronger than the sentence background, using
                    // exact TTS range start when the engine provides WordBoundary.
                    span(new BackgroundColorSpan(0xffffda00),from,to);
                }else if(mode==RSVP){
                    // Paused RSVP context uses the same typography as ordinary word selection.
                    int selectedEnd=map.ends[first]-c.startChar;
                    span(new BackgroundColorSpan(0x66ffc94e),from,selectedEnd);
                    if(normalSelectText!=NO_COLOR)span(new ForegroundColorSpan(normalSelectText),from,selectedEnd);
                    if(normalSelectScale>100)span(new RelativeSizeSpan(normalSelectScale/100f),from,selectedEnd);
                }else if(mode==NORMAL&&selecting&&selectedWordActive){
                    if(normalSelectText!=NO_COLOR)span(new ForegroundColorSpan(normalSelectText),from,to);
                    if(normalSelectScale>100)span(new RelativeSizeSpan(normalSelectScale/100f),from,to);
                }
            }
            void span(Object obj,int first,int last){content.setSpan(obj,first,last,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);applied.add(obj);}
        }
        @Override public int getItemViewType(int position){return position==0?1:0;}
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            if(type==1){FrameLayout headerSlot=new FrameLayout(ReaderActivity.this);
                headerSlot.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
                return new Holder(headerSlot);}
            TextView item=text("",Math.max(12,(int)(size*.60f)));item.setTypeface(type());
            item.setLineSpacing(dp(Math.max(8,size/3)),1.12f);
            // Reserve a constant left gutter for annotations; total horizontal padding
            // stays 49 dp as before, preserving line wrapping regardless of marks.
            item.setPadding(dp(24),dp(7),dp(25),dp(7));
            item.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            Holder holder=new Holder(item);
            // Consume DOWN in selection mode so Android delivers UP for word tapping;
            // the parent RecyclerView can still intercept a scrolling drag.
            item.setClickable(true);
            item.setOnTouchListener((view,event)->{
                int action=event.getActionMasked();
                if(action==MotionEvent.ACTION_DOWN){
                    touchDownX=event.getX();touchDownY=event.getY();touchDownTime=event.getEventTime();
                    touchMoved=false;selectionLongPressed=false;
                    cancelPendingLongPress();
                    final float x=event.getX(),y=event.getY();
                    if(x>dp(26)){
                        pendingLongPress=()->{
                            if(touchMoved||textList==null||textList.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE)return;
                            int word=wordAt(holder,x,y);
                            if(word>=0){selectionLongPressed=true;item.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                beginTextSelection(word);}
                        };
                        handler.postDelayed(pendingLongPress,ViewConfiguration.getLongPressTimeout());
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_MOVE){
                    int slop=ViewConfiguration.get(ReaderActivity.this).getScaledTouchSlop();
                    if(Math.abs(event.getX()-touchDownX)>slop||Math.abs(event.getY()-touchDownY)>slop){
                        touchMoved=true;cancelPendingLongPress();
                    }
                    return true;
                }
                if(action==MotionEvent.ACTION_CANCEL){cancelPendingLongPress();touchMoved=true;return true;}
                if(action!=MotionEvent.ACTION_UP)return true;
                cancelPendingLongPress();
                if(selectionLongPressed||touchMoved||event.getEventTime()-touchDownTime>=ViewConfiguration.getLongPressTimeout())return true;
                if(selectionFrom>=0){clearSelection();return true;}
                if(event.getX()<=dp(26)){
                    java.util.ArrayList<ReaderAnnotationsStore.Annotation> onLine=notesAtLine(holder,event.getY());
                    java.util.ArrayList<ReaderBookmarks.Item> marks=bookmarksAtLine(holder,event.getY());
                    if(!onLine.isEmpty()&&!marks.isEmpty()){
                        new SwipeDialogs.Builder(ReaderActivity.this).setTitle("Отметки в строке")
                            .setItems(new String[]{"Заметки ("+onLine.size()+")","Закладка"},(dialog,which)->{
                                if(which==0)showNoteChoices(onLine,item,event.getY());
                                else showBookmarkChoices(marks);
                            }).setNegativeButton("Отмена",null).show();
                        return true;
                    }
                    if(!onLine.isEmpty()){showNoteChoices(onLine,item,event.getY());return true;}
                    if(!marks.isEmpty()){showBookmarkChoices(marks);return true;}
                }
                boolean selectingWord=(mode==CURSOR)||(mode==NORMAL&&selecting)||(mode==RSVP&&!playing)||(mode==TTS);
                int tapped=wordAt(holder,event.getX(),event.getY());if(tapped<0)return true;
                if(mode==NORMAL&&!selecting){
                    java.util.ArrayList<ReaderAnnotationsStore.Annotation> onWord=new java.util.ArrayList<>();
                    for(ReaderAnnotationsStore.Annotation note:annotations)
                        if(!note.note.isEmpty()&&note.contains(tapped))onWord.add(note);
                    if(!onWord.isEmpty()){showNoteChoices(onWord,item,event.getY());return true;}
                }
                if(!selectingWord)return true;
                int old=index;selectedWordActive=true;
                abandonViewportPreview();
                if(mode==TTS)selectTtsWord(tapped);else index=tapped;
                refreshHighlight(old);updateProgress();saveLight(true);
                return true;
            });
            return holder;
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            if(position==0){
                FrameLayout slot=(FrameLayout)holder.itemView;
                if(bookHeader.getParent() instanceof android.view.ViewGroup)
                    ((android.view.ViewGroup)bookHeader.getParent()).removeView(bookHeader);
                slot.addView(bookHeader,new FrameLayout.LayoutParams(-1,-2));return;
            }
            int chunkIndex=position-1;
            BookChunkIndex.Chunk c=chunks.get(chunkIndex);
            String slice=body.substring(c.startChar,c.endChar);
            holder.value.setText(slice,TextView.BufferType.SPANNABLE);
            holder.content=(Spannable)holder.value.getText();holder.applied.clear();holder.highlight(chunkIndex);
        }
        @Override public int getItemCount(){return chunks==null?0:chunks.size()+1;}
        void updateVisible(int chunkId){
            if(chunkId<0||chunkId>=getItemCount())return;
            RecyclerView.ViewHolder row=textList.findViewHolderForAdapterPosition(chunkId+1);
            if(row instanceof Holder)((Holder)row).highlight(chunkId);
        }
    }
    private void refreshHighlight(int previousWord){
        if(chunks==null||chunkAdapter==null||words.length==0)return;
        int current=chunks.chunkForWord(index),old=chunks.chunkForWord(previousWord);
        chunkAdapter.updateVisible(old);
        if(old!=current)chunkAdapter.updateVisible(current);
        lastHighlightedChunk=current;
    }
    private void updateModeUI(){
        if(rsvpPane==null)return;
        rsvpPane.setVisibility(mode==RSVP&&playing?View.VISIBLE:View.GONE);
        if(focusLines!=null){focusLines.setVisibility(mode==RSVP&&focusLinesEnabled?View.VISIBLE:View.GONE);focusLines.invalidate();}
        if(modeIcon!=null){modeIcon.setImageResource(modeDrawable());modeIcon.setColorFilter(contrast(buttonBg()));}
        if(toolbarModeAction!=null){toolbarModeAction.setImageResource(modeDrawable());
            toolbarModeAction.setBackground(shape(accent(),12));toolbarModeAction.setColorFilter(contrast(accent()));}
        if(speedView!=null)speedView.setText(String.valueOf(mode==TTS?TtsWpm.get(this):wpm));
        if(selectWordButton!=null){selectWordButton.setVisibility(mode==NORMAL?View.VISIBLE:View.GONE);updateSelectWordButton();}
        if(toolbarEngineAction!=null){toolbarEngineAction.setVisibility(mode==TTS?View.VISIBLE:View.GONE);
            toolbarEngineAction.setBackground(shape(accent(),12));toolbarEngineAction.setColorFilter(contrast(accent()));}
        if(toolbarVoiceAction!=null)toolbarVoiceAction.setVisibility(mode==TTS?View.VISIBLE:View.GONE);
        if(toolbarSleepAction!=null)toolbarSleepAction.setVisibility(mode==TTS?View.VISIBLE:View.GONE);
        if(toolbarWpm!=null)toolbarWpm.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(contentsAction!=null){contentsAction.setBackground(shape(buttonBg(),13));
            if(contentsIcon!=null){contentsIcon.setBackground(shape(accent(),12));
                contentsIcon.setColorFilter(contrast(accent()));}
            if(progressView!=null)progressView.setTextColor(contrast(buttonBg()));}
        if(toolbarWpm!=null){toolbarWpm.setBackground(shape(buttonBg(),12));
            if(speedView!=null)speedView.setTextColor(contrast(buttonBg()));}
        ReaderToolbarController.measure(toolbar,toolbarSpacer);
        if(controls!=null)controls.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(speedPanel!=null)speedPanel.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(toolbarWpm!=null)toolbarWpm.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(textList!=null)textList.setVisibility(mode==RSVP&&playing?View.GONE:View.VISIBLE);
        if(mode==RSVP)updateWord();
        refreshHighlight(index);updateProgress();applyFullscreenUi();
    }
    /** The reader content never changes size when floating controls are shown/hidden. */
    private void applyFullscreenUi(){
        if(root==null||words.length==0||readingFrame==null)return;
        if(toolbarStack!=null)toolbarStack.setVisibility(toolbarExpanded?View.VISIBLE:View.INVISIBLE);
        if(modeRow!=null)modeRow.setVisibility(View.GONE);
        if(controls!=null)controls.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(bottomBar!=null)bottomBar.setVisibility(View.VISIBLE);
        if(toolbarWpm!=null)toolbarWpm.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(floatingPlayButton!=null)floatingPlayButton.setVisibility(View.GONE);
        if(mode==RSVP&&playing&&textList!=null)textList.setVisibility(View.GONE);
        if(rsvpPane!=null){
            LinearLayout.LayoutParams paneLp=(LinearLayout.LayoutParams)rsvpPane.getLayoutParams();
            if(paneLp!=null){
                if(mode==RSVP&&playing){paneLp.height=0;paneLp.weight=1f;paneLp.bottomMargin=0;}
                else{paneLp.height=dp(192);paneLp.weight=0f;paneLp.bottomMargin=0;}
                rsvpPane.setLayoutParams(paneLp);
            }
        }
        root.setPadding(0,0,0,0);
        readingFrame.setPadding(0,0,0,0);
        readingFrame.setBackgroundColor(bg);
        applySystemBars(false);
    }
    // The overlay layout no longer uses legacy fullscreen positioning, but
    // the RSVP, cursor and TTS playback controls must retain their handlers.
    private void removeCountdownRing(){
        if(countdownRing==null)return;
        ViewGroup parent=(ViewGroup)countdownRing.getParent();
        if(parent!=null)parent.removeView(countdownRing);
        countdownRing=null;
    }
    private void showCountdownRing(int seconds){
        removeCountdownRing();
        if(sceneRoot==null)return;
        countdownRing=new CountdownRingView(this,seconds,accent());
        if(mode==RSVP&&rsvpPane!=null){
            int focusHeight=focusLinesEnabled?dp(90):0;
            int wordHeight=wordView==null?dp(size):Math.max(Math.round(wordView.getTextSize()*1.25f),dp(size));
            int diameter=Math.max(dp(65),Math.max(focusHeight,wordHeight)+dp(30));
            rsvpPane.addView(countdownRing,0,new FrameLayout.LayoutParams(diameter,diameter,Gravity.CENTER));
        }else{
            sceneRoot.addView(countdownRing,new FrameLayout.LayoutParams(dp(124),dp(124),Gravity.CENTER));
        }
    }
    private void setRsvpContextVisible(boolean visible){
        if(textList==null||mode!=RSVP)return;
        if(textList.getVisibility()==(visible?View.VISIBLE:View.GONE))return;
        textList.setVisibility(visible?View.VISIBLE:View.GONE);
        // Never snap back to an old word when the RSVP context becomes visible.
    }
    private void stepRsvpByScroll(int dy){
        if(words.length==0||dy==0)return;
        int threshold=Math.max(6,dp(8));
        scrollWordAccumulator+=dy;
        int old=index;
        while(scrollWordAccumulator>=threshold&&index<words.length-1){
            index++;scrollWordAccumulator-=threshold;
        }
        while(scrollWordAccumulator<=-threshold&&index>0){
            index--;scrollWordAccumulator+=threshold;
        }
        if(old!=index){
            updateWord();refreshHighlight(old);updateProgress();saveLight(false);
        }
    }
    private void updateSelectWordButton(){
        if(selectWordButton==null)return;
        int background=selecting?accent():buttonBg();
        selectWordButton.setBackground(shape(background,14));
        selectWordButton.setColorFilter(contrast(background));
        selectWordButton.setAlpha(selecting?1f:.9f);
    }
    private void updateProgress(){
        if(progressView==null||words.length==0)return;
        int percent=(int)((index+1L)*100/words.length);
        if(mode==TTS){
            int rate=Math.max(100,TtsWpm.get(this));
            long elapsed=index*60L/rate,total=words.length*60L/rate;
            progressView.setText(formatReadingTime(elapsed)+" / "+formatReadingTime(total)+" · "+percent+"%");
        }else{
            progressView.setText((index+1)+" / "+words.length+" слов · "+percent+"%");
        }
    }
    private String formatReadingTime(long seconds){
        return (seconds/3600)+" ч "+((seconds%3600)/60)+" м";
    }
    private void updatePlayButton(){
        boolean active=mode==TTS?ttsCurrentlyPlaying():playing;
        if(mode==TTS){lastRenderedTtsPlayState=active;playStateRendered=true;}
        if(playIcon!=null)playIcon.setImageResource(active?R.drawable.ic_pause:R.drawable.ic_play);
        if(floatingPlayIcon!=null)floatingPlayIcon.setImageResource(active?R.drawable.ic_pause:R.drawable.ic_play);
    }
    private void togglePlayback(){
        chteicoTouch();
        if(mode==TTS){toggleTtsPlayback();return;}
        if(mode==NORMAL||words.length==0)return;
        if(playing){stop();saveLight(true);return;}
        returnToActiveWord();
        playing=true;waitingForStart=false;
        handler.removeCallbacks(ticker);handler.removeCallbacks(beginDelayedReading);
        if(mode==RSVP){
            setRsvpContextVisible(false);
            if(rsvpPane!=null)rsvpPane.setVisibility(View.VISIBLE);
        }
        updatePlayButton();applyFullscreenUi();
        int seconds=mode==RSVP&&delayRsvpEnabled?delayRsvpSeconds:
            mode==CURSOR&&delayCursorEnabled?delayCursorSeconds:0;
        if(seconds>0){
            waitingForStart=true;
            showCountdownRing(seconds);
            handler.postDelayed(beginDelayedReading,seconds*1000L);
        }else{
            handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));
        }
    }
    private void stop(){
        playing=false;waitingForStart=false;
        handler.removeCallbacks(ticker);handler.removeCallbacks(beginDelayedReading);
        removeCountdownRing();updatePlayButton();setRsvpContextVisible(true);
        if(rsvpPane!=null)rsvpPane.setVisibility(View.GONE);
        applyFullscreenUi();
        refreshHighlight(index); // The current word remains marked after stopping RSVP.
    }

    private class CountdownRingView extends View {
        private final Paint ring=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long started=SystemClock.uptimeMillis(),duration;
        CountdownRingView(Context ctx,int seconds,int color){super(ctx);
            duration=Math.max(1,seconds)*1000L;
            ring.setColor(Color.argb(165,Color.red(color),Color.green(color),Color.blue(color)));
            ring.setStyle(Paint.Style.STROKE);ring.setStrokeWidth(dp(5));
            ring.setStrokeCap(Paint.Cap.ROUND);
            setClickable(false);setFocusable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);
            long elapsed=Math.min(duration,Math.max(0,SystemClock.uptimeMillis()-started));
            float remaining=1f-elapsed/(float)duration;
            if(remaining<=0)return;
            float inset=ring.getStrokeWidth()/2f+dp(2);
            // One complete contour initially. Its leading edge erases clockwise from 12 o'clock.
            float progress=1f-remaining;
            canvas.drawArc(inset,inset,getWidth()-inset,getHeight()-inset,
                -90f+360f*progress,360f*remaining,false,ring);
            postInvalidateDelayed(16);
        }
    }
    private void applySystemBars(boolean immersive){
        // WindowInsets keep content below the status bar; transparent system bars
        // continue the book background without ever hiding system navigation.
        SafeSystemBars.matchBackground(this,bg);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
    }
    private void changeSpeed(int delta){wpm=Math.max(100,Math.min(1000,wpm+delta));if(speedView!=null)speedView.setText(String.valueOf(wpm));saveLight(true);
        if(playing&&!waitingForStart){handler.removeCallbacks(ticker);handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));}}
    private boolean paragraphAfter(int word){return word<words.length-1&&body.substring(map.ends[word],map.starts[word+1]).contains("\n\n");}
    private long delayForPortion(int first,int count){
        long base=ReadingWordMap.delayMillis(wpm,count);double extra=0;
        for(int i=first;i<Math.min(words.length,first+count);i++){
            String value=words[i];
            if(pauseLongPercent>0&&value.length()>=12)extra+=pauseLongPercent/100.0;
            if(pausePunctuationPercent>0&&value.matches(".*[,;:—–]$"))extra+=pausePunctuationPercent/100.0;
            if(pauseSentencePercent>0&&(value.endsWith(".")||value.endsWith("!")||value.endsWith("?")||value.endsWith("…")||value.endsWith("»")))extra+=pauseSentencePercent/100.0;
            if(pauseParagraphPercent>0&&paragraphAfter(i))extra+=pauseParagraphPercent/100.0;
        }
        return (long)(base*(1+extra/Math.max(1,count)));
    }
    private void updateWord(){
        if(wordView==null||words.length==0)return;
        int count=Math.min(portion,words.length-index);StringBuilder display=new StringBuilder();
        for(int i=0;i<count;i++){if(i>0)display.append(' ');display.append(words[index+i]);}
        SpannableString span=new SpannableString(display.toString());
        if(focusPoint&&focusColor!=NO_COLOR){
            int chosen=Math.max(0,(count-1)/2),start=0;
            for(int i=0;i<chosen;i++)start+=words[index+i].length()+1;
            int offset=start+ReadingWordMap.orp(words[index+chosen].length());
            if(offset>=0&&offset<span.length())span.setSpan(new ForegroundColorSpan(focusColor),offset,offset+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        wordView.setTextSize(size);wordView.setTypeface(type(),Typeface.BOLD);wordView.setText(span);
        if(focusLines!=null)focusLines.invalidate();updateProgress();
    }
    private void updateChapterTitle(){
        if(toolbarBookName==null)return;
        int at=viewportWord>=0?viewportWord:index;
        int charAt=map.starts.length>0?map.starts[map.clamp(at)]:-1;
        BookChapterIndex.Chapter shown=charAt>=0?actualChapters.atChar(charAt):null;
        String name=shown==null?"":shown.title;
        toolbarBookName.setText(title+(name.isEmpty()?"":" · "+name));
    }
    private int chapterForWord(int word){
        if(chapters==null||map.starts.length==0)return -1;
        int pos=map.starts[map.clamp(word)],result=-1;
        for(int i=0;i<chapters.size();i++)if(chapters.get(i).startChar<=pos)result=i;
        return result;
    }
    /** Human-readable chapter label for the shared reading cursor.
     * chapterForWord() is an int TOC index, not a title to store in library metadata.
     */
    private String chapterTitleForWord(int word){
        if(map.starts.length==0)return "";
        BookChapterIndex.Chapter chapter=actualChapters.atChar(map.starts[map.clamp(word)]);
        if(chapter!=null&&chapter.title!=null&&!chapter.title.trim().isEmpty())return chapter.title;
        int chapterIndex=chapterForWord(word);
        return chapterIndex>=0?"Глава "+(chapterIndex+1):"";
    }
    private boolean isActiveWordVisible(){
        if(textList==null||layoutManager==null||chunks==null||words.length==0)return false;
        int c=chunks.chunkForWord(map.clamp(index));
        RecyclerView.ViewHolder item=textList.findViewHolderForAdapterPosition(c+1);
        if(!(item instanceof ChunkAdapter.Holder))return false;
        TextView v=((ChunkAdapter.Holder)item).value;
        android.text.Layout l=v.getLayout();if(l==null)return false;
        int offset=Math.max(0,Math.min(v.length(),map.starts[map.clamp(index)]-chunks.get(c).startChar));
        int line=l.getLineForOffset(offset);
        int top=item.itemView.getTop()+v.getTotalPaddingTop()+l.getLineTop(line);
        int bottom=item.itemView.getTop()+v.getTotalPaddingTop()+l.getLineBottom(line);
        // Toolbar and transport controls are floating overlays over the RecyclerView.
        return bottom>dp(85)&&top<textList.getHeight()-dp(90);
    }
    /** Center the active line; far-away chapters jump close then settle gently. */
    private void ensureWordVisible(boolean explicitReturn){
        if(mode==RSVP&&playing)return;
        if(!explicitReturn&&followSuspended)return;
        if(textList==null||textList.getVisibility()!=View.VISIBLE||layoutManager==null||chunks==null||words.length==0)return;
        int chunk=chunks.chunkForWord(map.clamp(index));
        RecyclerView.ViewHolder item=textList.findViewHolderForAdapterPosition(chunk+1);
        if(!(item instanceof ChunkAdapter.Holder)){
            if(!explicitReturn && chunk>=layoutManager.findFirstVisibleItemPosition()-1
                && chunk<=layoutManager.findLastVisibleItemPosition()-1)return;
            restoringScroll=true;
            layoutManager.scrollToPositionWithOffset(chunk+1,Math.max(dp(65),textList.getHeight()/2));
            textList.post(()->{
                if(!followSuspended&&textList!=null&&textList.getVisibility()==View.VISIBLE)
                    centerActiveVisibleLine(false);
                restoringScroll=false;
            });
            return;
        }
        centerActiveVisibleLine(!explicitReturn);
    }
    private void centerActiveVisibleLine(boolean gentle){
        if(textList==null||layoutManager==null||chunks==null||words.length==0)return;
        int chunk=chunks.chunkForWord(map.clamp(index));
        RecyclerView.ViewHolder item=textList.findViewHolderForAdapterPosition(chunk+1);
        if(!(item instanceof ChunkAdapter.Holder))return;
        TextView v=((ChunkAdapter.Holder)item).value;
        android.text.Layout layout=v.getLayout();if(layout==null)return;
        int offset=Math.max(0,Math.min(v.length(),map.starts[map.clamp(index)]-chunks.get(chunk).startChar));
        int line=layout.getLineForOffset(offset);
        int center=item.itemView.getTop()+v.getTotalPaddingTop()
            +(layout.getLineTop(line)+layout.getLineBottom(line))/2;
        int viewportCenter=(dp(80)+textList.getHeight()-dp(84))/2;
        int delta=center-viewportCenter;
        if(Math.abs(delta)<=dp(9))return;
        restoringScroll=true;
        if(Math.abs(delta)>textList.getHeight()*2){
            // The line's offset INSIDE the same chunk must be subtracted here;
            // jumping to the chunk's first line repeatedly would loop or flicker.
            int relativeLineCenter=v.getTotalPaddingTop()
                +(layout.getLineTop(line)+layout.getLineBottom(line))/2;
            layoutManager.scrollToPositionWithOffset(chunk+1,viewportCenter-relativeLineCenter);
            textList.post(()->{
                restoringScroll=false;
                if(!followSuspended)centerActiveVisibleLine(true);
            });
        }else{
            textList.smoothScrollBy(0,delta);
            textList.postDelayed(()->restoringScroll=false,300);
        }
    }
    /** Any *visible portion* of the highlighted sentence keeps the overlay hidden.
     * Geometry is measured against the readable center between toolbar and controls,
     * not the full-screen RecyclerView that extends beneath floating buttons.
     */
    private boolean isActiveSentenceVisible(){
        if(textList==null||layoutManager==null||chunks==null||words.length==0)return false;
        SentenceChunks.Chunk sentence=ttsSentenceIndex.atWordRange(map.clamp(index));
        if(sentence==null)return isActiveWordVisible();
        int topLimit=dp(88),bottomLimit=textList.getHeight()-dp(96);
        if(bottomLimit<=topLimit)return isActiveWordVisible();
        int first=layoutManager.findFirstVisibleItemPosition();
        int last=layoutManager.findLastVisibleItemPosition();
        for(int adapterPos=Math.max(1,first);adapterPos<=last;adapterPos++){
            RecyclerView.ViewHolder holder=textList.findViewHolderForAdapterPosition(adapterPos);
            if(!(holder instanceof ChunkAdapter.Holder))continue;
            TextView v=((ChunkAdapter.Holder)holder).value;
            if(v==null||v.getLayout()==null)continue;
            BookChunkIndex.Chunk block=chunks.get(adapterPos-1);
            int start=Math.max(sentence.fromChar,block.startChar);
            int end=Math.min(sentence.toChar,block.endChar);
            if(end<=start)continue;
            android.text.Layout lines=v.getLayout();
            int fromOffset=Math.max(0,Math.min(v.length(),start-block.startChar));
            int toOffset=Math.max(fromOffset,Math.min(v.length()-1,end-block.startChar-1));
            int firstLine=lines.getLineForOffset(fromOffset);
            int lastLine=lines.getLineForOffset(toOffset);
            int top=holder.itemView.getTop()+v.getTotalPaddingTop()+lines.getLineTop(firstLine);
            int bottom=holder.itemView.getTop()+v.getTotalPaddingTop()+lines.getLineBottom(lastLine);
            if(bottom>topLimit&&top<bottomLimit)return true;
        }
        return false;
    }
    private void updateFloatingCaption(){
        if(ttsFloatingCaption==null)return;
        String previousCaption=lastCaptionText;
        if(mode!=TTS||!followSuspended||!ttsCurrentlyPlaying()||words.length==0||isActiveSentenceVisible()){
            ttsFloatingCaption.setVisibility(View.GONE);return;
        }
        SentenceChunks.Chunk part=ttsSentenceIndex.atWordRange(map.clamp(index));
        if(part==null){String text=words[map.clamp(index)];
            if(!text.equals(lastCaptionText)){lastCaptionText=text;ttsFloatingCaption.setText(text);}}
        else{
            int first=Math.max(0,part.fromChar);
            // System Android TTS synthesizers often provide no reliable
            // WordBoundary offsets for cached audio; the reading index then
            // stays at the first word until the next sentence. Show the entire
            // active sentence immediately so the caption never freezes after
            // a single word. Online Edge continues its progressive transcript.
            boolean missingWordTiming=false;
            if(!ttsLocal&&ttsStreamer!=null){
                EdgeStreamingService.Clip playingClip=ttsStreamer.currentClip();
                if(playingClip!=null){
                    boolean cached=!cachedTimingWords.isEmpty()
                        &&(playingClip.key+":"+playingClip.start).equals(cachedTimingKey);
                    missingWordTiming=!cached&&ttsStreamer.liveWords(playingClip).isEmpty();
                }
            }
            // Without WordBoundary metadata, show the sentence rather than a
            // misleading one-word caption until exact timing data arrives.
            int through=(ttsLocal||missingWordTiming)?Math.min(body.length(),part.toChar)
                : Math.min(body.length(),map.ends[map.clamp(index)]);
            String content=body.substring(first,Math.max(first,through)).trim();
            String next=content.isEmpty()?words[map.clamp(index)]:content;
            if(!next.equals(lastCaptionText)){lastCaptionText=next;ttsFloatingCaption.setText(next);}

        }
        boolean captionWasHidden=ttsFloatingCaption.getVisibility()!=View.VISIBLE;
        ttsFloatingCaption.setVisibility(View.VISIBLE);
        if(!captionWasHidden&&previousCaption.equals(lastCaptionText))return;
        ttsFloatingCaption.post(()->{
            if(ttsFloatingCaption!=null&&ttsFloatingCaption.getLayout()!=null){
                android.text.Layout layout=ttsFloatingCaption.getLayout();
                int hidden=Math.max(0,layout.getHeight()-ttsFloatingCaption.getHeight()+ttsFloatingCaption.getTotalPaddingBottom());
                ttsFloatingCaption.scrollTo(0,hidden);
            }
        });
    }
    private void abandonViewportPreview(){
        tocPreviewGeneration++;
        viewportWord=-1;followSuspended=false;userScrolling=false;manualViewportMovement=false;
        if(prefs!=null&&!docUri.isEmpty())prefs.edit().remove("viewport:"+docUri).apply();
    }
    private void returnToActiveWord(){
        abandonViewportPreview(); // Explicit Play or caption tap returns to the live cursor.
        if(ttsFloatingCaption!=null)ttsFloatingCaption.setVisibility(View.GONE);
        if(textList!=null)textList.post(()->ensureWordVisible(true));
    }
    private void openEdgeDictionary(){
        Intent intent=new Intent(this,EdgeDictionaryActivity.class);
        // Load the currently spoken/last selected sentence, not a separately highlighted word.
        if(words.length>0){
            SentenceChunks.Chunk sentence=ttsSentenceIndex.atWordRange(map.clamp(index));
            if(sentence!=null&&sentence.toChar>sentence.fromChar)
                intent.putExtra(EdgeDictionaryActivity.EXTRA_SENTENCE,
                    body.substring(Math.max(0,sentence.fromChar),Math.min(body.length(),sentence.toChar)));
        }
        if(!docUri.isEmpty())intent.putExtra("dictionary_book_uri",docUri)
            .putExtra("dictionary_selected_word_index",map.clamp(index));
        startActivityForResult(intent,12212);
    }
    private void showBookSearch(){
        if(body.isEmpty())return;
        EditText query=new EditText(this);query.setSingleLine(true);query.setHint("Поиск по всей книге");
        LinearLayout panel=column();panel.setPadding(dp(16),0,dp(16),0);panel.addView(query);
        TextView result=text("Введите слово или фразу",13);add(panel,result);
        ScrollView scroll=new ScrollView(this);LinearLayout matches=column();scroll.addView(matches);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(290)));
        final android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this)
            .setTitle("Поиск по книге").setView(panel).setNegativeButton("Закрыть",null).create();
        Runnable update=()->{
            matches.removeAllViews();String needle=query.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
            if(needle.isEmpty()){result.setText("Введите слово или фразу");return;}
            String haystack=body.toLowerCase(java.util.Locale.ROOT);int offset=0,count=0;java.util.ArrayList<Integer> positions=new java.util.ArrayList<>();
            while((offset=haystack.indexOf(needle,offset))>=0){count++;if(positions.size()<100)positions.add(offset);offset+=Math.max(1,needle.length());}
            result.setText("Найдено: "+count+(count>100?" · первые 100 результатов":""));
            for(int position:positions){
                String context=body.substring(Math.max(0,position-48),Math.min(body.length(),position+needle.length()+65)).replaceAll("\\s+"," ");
                TextView entry=text(context,15);entry.setPadding(dp(8),dp(12),dp(8),dp(12));
                matches.addView(entry,new LinearLayout.LayoutParams(-1,-2));
                entry.setOnClickListener(v->{int previous=index;index=map.atCharacter(position);selectedWordActive=true;
                    abandonViewportPreview();
                    refreshHighlight(previous);
                    updateProgress();ensureWordVisible(true);saveLight(true);dialog.dismiss();});
            }
        };
        query.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}
            public void afterTextChanged(android.text.Editable e){}
        });dialog.show();
    }
    private void modeDialog(){
        ReaderSheets.modes(this,mode,selected->{
            setMode(selected);
        });
    }
    /** Opening a chapter during speech is browsing, NOT a TTS seek.  Keep
     * the spoken word/index, Edge anchor, audio state and highlights intact.
     * The floating caption follows the speech while the viewport stays here.
     */
    private void browseToWord(int word){
        if(textList==null||layoutManager==null||chunks==null||words.length==0)return;
        // RSVP hides the text viewport during playback: show it for TOC browsing,
        // but NEVER change the saved playing word until an explicit tap.
        if(mode==RSVP&&playing)stop();
        int target=map.clamp(word),chunk=chunks.chunkForWord(target);
        final long generation=++tocPreviewGeneration;
        viewportWord=target;followSuspended=true;userScrolling=false;restoringScroll=true;updateChapterTitle();
        if(prefs!=null&&!docUri.isEmpty())prefs.edit().putInt("viewport:"+docUri,target).apply();
        ReaderDiagnosticLog.event("NAVIGATION","TOC_VIEW_ONLY target="+target+
            " playing="+index+" chapter="+chapterForWord(target));
        layoutManager.scrollToPositionWithOffset(chunk+1,dp(108));
        // RecyclerView may not have laid out the destination when a first post()
        // executes. Retry across actual frames, without seeking the audio clock.
        textList.postOnAnimation(()->alignTocPreview(target,chunk,generation,0));
    }
    private void alignTocPreview(int target,int chunk,long generation,int attempt){
        if(textList==null||layoutManager==null||chunks==null
            ||generation!=tocPreviewGeneration||!followSuspended){restoringScroll=false;return;}
        RecyclerView.ViewHolder holder=textList.findViewHolderForAdapterPosition(chunk+1);
        if(!(holder instanceof ChunkAdapter.Holder)||
                ((ChunkAdapter.Holder)holder).value.getLayout()==null){
            if(attempt<12){
                layoutManager.scrollToPositionWithOffset(chunk+1,dp(108));
                textList.postOnAnimation(()->alignTocPreview(target,chunk,generation,attempt+1));
            }else{restoringScroll=false;updateFloatingCaption();
                ReaderDiagnosticLog.event("NAVIGATION","TOC_LAYOUT_TIMEOUT word="+target);}
            return;
        }
        TextView value=((ChunkAdapter.Holder)holder).value;
        android.text.Layout lines=value.getLayout();
        int offset=Math.max(0,Math.min(value.length(),map.starts[target]-chunks.get(chunk).startChar));
        int line=lines.getLineForOffset(offset);
        int lineY=value.getTotalPaddingTop()+lines.getLineTop(line);
        int top=holder.itemView.getTop()+lineY;
        int delta=top-dp(108);
        if(Math.abs(delta)>dp(2)){
            // Offset inside the chunk is essential for chapters starting mid-block.
            layoutManager.scrollToPositionWithOffset(chunk+1,dp(108)-lineY);
            if(attempt<12){
                textList.postOnAnimation(()->alignTocPreview(target,chunk,generation,attempt+1));return;
            }
        }
        restoringScroll=false;
        ReaderDiagnosticLog.event("NAVIGATION","TOC_LANDED word="+target+" offset="+delta);
        updateFloatingCaption();
    }
    /** A genuine gesture can move the viewport but cannot seek the reading engine. */
    private void rememberVisibleViewport(){
        if(textList==null||layoutManager==null||chunks==null||words.length==0)return;
        int row=layoutManager.findFirstVisibleItemPosition()-1;
        if(row<0){viewportWord=0;
            prefs.edit().putInt("viewport:"+docUri,0).apply();return;
        }
        if(row>=chunks.size())return;
        int word=chunks.get(row).startWord;
        RecyclerView.ViewHolder h=textList.findViewHolderForAdapterPosition(row+1);
        if(h instanceof ChunkAdapter.Holder){
            TextView v=((ChunkAdapter.Holder)h).value;
            if(v!=null&&v.getLayout()!=null){
                int offset=v.getLayout().getLineForVertical(Math.max(0,dp(98)-h.itemView.getTop()-v.getTotalPaddingTop()));
                word=map.atCharacter(chunks.get(row).startChar+v.getLayout().getLineStart(offset));
            }
        }
        viewportWord=map.clamp(word);updateChapterTitle();
        if(prefs!=null&&!docUri.isEmpty())prefs.edit().putInt("viewport:"+docUri,viewportWord).apply();
    }
    private void showToc(){
        UiNavigationSheet.showContents(this,chapters!=null&&chapters.size()>0?chapters:BookChapterIndex.empty(),
            chunks,body,map,viewportWord>=0?viewportWord:index,word->{
            if(word<0||word>=words.length)return;
            // TOC always browses the visible text. It never stops playback,
            // selects the first sentence, touches index, or saves a new reading cursor.
            browseToWord(word);
        });
    }
    private void setToolbarExpanded(boolean show){
        if(toolbarExpanded==show)return;
        toolbarExpanded=show;
        ReaderToolbarController.reveal(toolbarStack,show,106);
    }
    /** Switch back to ordinary reading without losing the current word or book. */
    void openReadingFromDrawer(){
        // Reader itself owns the current mode; drawer navigation must not reset it.
        if(toolbarStack!=null)setToolbarExpanded(true);
    }

    private void setMode(int next){if(next<0||next>TTS||next==mode)return;
        final long modeSelectedAt=SystemClock.elapsedRealtime();
        if(next==TTS)ReaderDiagnosticLog.event("READER_STARTUP","tts_mode_selected engine="+(ttsLocal?"local":"edge"));
        chteicoTouch();stop();int old=index,previousMode=mode;
        // Switching modes reuses the same book cursor for all reading engines.
        if(previousMode==TTS){saveLight(true);releaseTts();}
        else if(next==TTS)saveLight(true);
        index=map.clamp(UnifiedReadingProgress.read(prefs,docUri,index));
        pendingTtsWord=-1;
        mode=next;selecting=false;selectedWordActive=false;normalChromeVisible=false;toolbarExpanded=true;
        ReaderToolbarController.reset(toolbarStack);
        updateModeUI();refreshHighlight(old);
        if(!followSuspended)returnToActiveWord();
        saveLight(true);
        if(mode==TTS){
            ReaderDiagnosticLog.event("READER_STARTUP","tts_ui_ready_ms="+
                (SystemClock.elapsedRealtime()-modeSelectedAt));
            startTtsSession();
        }
    }
    private void chteicoTouch(){chteicoLastInteraction=SystemClock.elapsedRealtime();}
    private void chteicoCompleteIfReachedEnd(){
        if(words.length<300||docUri==null||docUri.isEmpty())return;
        boolean atEnd=index>=words.length-3;
        if(mode==NORMAL && textList!=null && !textList.canScrollVertically(1)
            && (index>chteicoInitialIndex||chteicoReadingSeconds>=60))atEnd=true;
        if(atEnd&&(chteicoReadingSeconds>=50||index>chteicoInitialIndex+30)){
            if(fishingChapter>=0&&fishingChapterSeconds>=30){
                if(mode==TTS)ChteicoStore.awardAudioChapter(this,docUri,fishingChapter);
                else ChteicoStore.awardReadChapter(this,docUri,fishingChapter);
            }
            if(ChteicoStore.completeBook(this,docUri))ChteicoNotifications.milestone(this,"Книга завершена!","Чтейцо стал ещё ближе к взрослой стадии.");
        }
    }
    private void saveLight(boolean force){
        if(prefs==null)return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastLightSave<4000)return;
        lastLightSave=now;
        SharedPreferences.Editor editor=prefs.edit().putString("uri",docUri)
            .putInt("reading_mode",mode).putBoolean("rsvp",mode==RSVP).putInt("wpm",wpm);
        editor.apply();
        if(!docUri.isEmpty()&&words.length>0)
            UnifiedReadingProgress.save(this,docUri,index,words.length,chapterTitleForWord(index),false);
    }
    private void save(){
        saveLight(true);
        if(words.length==0||docUri.isEmpty())return;
        // Update library JSON only when pausing/leaving/changing book, never on each word.
        try{
            org.json.JSONArray old=new org.json.JSONArray(prefs.getString("library","[]"));
            org.json.JSONArray updated=new org.json.JSONArray();org.json.JSONObject current=null;
            for(int i=0;i<old.length();i++){
                org.json.JSONObject obj=old.optJSONObject(i);if(obj==null)continue;
                if(docUri.equals(obj.optString("uri")))current=obj;else updated.put(obj);
            }
            if(current==null)current=new org.json.JSONObject();
            current.put("uri",docUri);current.put("title",title);current.put("author",author);
            int libraryIndex=UnifiedReadingProgress.read(prefs,docUri,index);
            current.put("index",libraryIndex);current.put("wordCount",words.length);
            current.put("progress",(libraryIndex+1)*100/Math.max(1,words.length));
            if(map.starts.length>0){
                BookChapterIndex.Chapter chapter=actualChapters.atChar(map.starts[map.clamp(libraryIndex)]);
                if(chapter!=null)current.put("chapter",chapter.title);
            }
            current.put("openedAt",System.currentTimeMillis());updated.put(current);
            prefs.edit().putString("library",updated.toString()).apply();
            UnifiedReadingProgress.save(this,docUri,libraryIndex,words.length,
                chapterTitleForWord(libraryIndex),true);
        }catch(Exception ignored){}
    }
    private void bookmarkSelection(){
        if(selectionFrom<0||selectionTo<selectionFrom||docUri.isEmpty())return;
        ReaderBookmarkSentences.Range selected=ReaderBookmarkSentences.choose(
            selectionFrom,selectionTo,map.size(),ttsSentenceIndex);
        ReaderBookmarks.Item item=new ReaderBookmarks.Item();
        item.word=selected.from;item.endWord=selected.to+1;item.created=System.currentTimeMillis();
        item.chapter=chapterTitleForWord(selected.from);
        item.excerpt=ReaderBookmarkSentences.excerpt(body,map,selected);
        item.title="Закладка";
        ReaderBookmarks.add(this,docUri,item);
        bookmarkItems=ReaderBookmarks.list(this,docUri);
        Toast.makeText(this,"Закладка добавлена",Toast.LENGTH_SHORT).show();clearSelection();
    }
    private void showBookmarks(){openMarks(false);}
    private void showNotes(){openMarks(true);}
    private void openMarks(boolean notes){
        if(docUri.isEmpty())return;
        // Existing bookmarks from earlier versions did not store their text.
        // Derive previews from the current in-memory book once, never reopen HTML.
        if(!notes)ReaderBookmarks.fillMissingExcerpts(this,docUri,body,map);
        startActivityForResult(new Intent(this,BookmarksActivity.class)
            .putExtra("book_uri",docUri).putExtra("book_title",title)
            .putExtra("show_notes",notes),12210);
    }
    private void pick(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,7);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==EdgeFolderPicker.REQUEST){
            EdgeFolderPicker.accept(this,req,result,data);
            boolean shouldResume=resumeEdgeAfterFolderPick;
            resumeEdgeAfterFolderPick=false;
            if(EdgeFolderPicker.available(this)&&shouldResume)toggleTtsPlayback();
            return;
        }
        if(req==12212&&result==RESULT_OK&&data!=null&&ttsStreamer!=null&&words.length>0){
            int selected=map.clamp(data.getIntExtra("dictionary_selected_word_index",index));
            // New speech revision makes every affected clip's key change; old audio
            // remains a historical cache, never reused for a modified dictionary.
            ttsStreamer.refreshSelectedClip(selected);
            cachedTimingKey="";cachedTimingWords=java.util.Collections.emptyList();
            if(ttsPendingPlay&&ttsPlayer!=null){
                ttsPendingPlay=false;ttsStreamer.activatePreparedBook();
                ttsPlayer.play();ttsStreamer.ensurePlaySelection();
            }else if(ttsPlayer!=null&&ttsPlayer.playRequested()
                    &&("edge:"+docUri).equals(ttsPlayer.currentBookId()))ttsStreamer.ensurePlaySelection();
            return;
        }
        if(req==12210){
            // Returning from either bookmarks or notes must update only lightweight
            // annotations; never reload or reparse the open book and its word map.
            try(ReaderAnnotationsStore store=new ReaderAnnotationsStore(this)){
                annotations=store.load(bookAnnotationId());
                updateVisibleAnnotations();
            }catch(Exception ex){
                ReaderDiagnosticLog.event("ANNOTATIONS","manager_refresh_failed type="+ex.getClass().getSimpleName());
            }
        }
        if(req==12210&&!docUri.isEmpty()){
            bookmarkItems=ReaderBookmarks.list(this,docUri);
            updateVisibleAnnotations();
        }
        if(req==12210&&result==RESULT_OK&&data!=null&&data.hasExtra("word_index")&&words.length>0){
            int at=map.clamp(data.getIntExtra("word_index",0)),old=index;
            abandonViewportPreview();selectedWordActive=true;
            if(mode==TTS)selectTtsWord(at);else index=at;
            refreshHighlight(old);updateProgress();saveLight(true);
            browseToWord(at);
            long noteId=data.getLongExtra("annotation_id",0L);
            if(noteId>0&&sceneRoot!=null)sceneRoot.post(()->openAnnotationById(noteId));
            return;
        }
        if(req==7&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            load(uri,true);
        }
    }
    private void load(Uri uri,boolean reset){
        final long requestAt=SystemClock.elapsedRealtime();
        new Thread(()->{
        try{
            // Reuse exactly one immutable book, word map, chapter index and UI
            // chunk index for all four reading modes and their speech engines.
            UnifiedBookSession session=UnifiedBookSession.open(this,uri);
            long openedAt=SystemClock.elapsedRealtime();
            ReaderDiagnosticLog.event("READER_STARTUP","book_prepare_ms="+(openedAt-requestAt)+" words="+session.words.words.length);
            BookLoader.Book book=session.book;
            final String loadedText=book.text;
            final String loadedTitle=BookTitleStore.title(this,BookTitleStore.TEXT,uri.toString(),book.title);
            final String loadedAuthor=book.author;
            final ReadingWordMap prepared=session.words;
            final BookChapterIndex parsedChapters=book.chapters;
            // Cache only lightweight chapter labels once; bookmark lists never parse book HTML.
            ReaderChapterLabels.saveIfMissing(this,uri.toString(),prepared,parsedChapters);
            final BookChunkIndex indexed=session.blocks;
            java.util.List<ReaderAnnotationsStore.Annotation> bookAnnotations;
            try(ReaderAnnotationsStore store=new ReaderAnnotationsStore(this)){
                bookAnnotations=store.load(BookUriMigration.legacy(this,uri.toString()));
            }
            catch(Exception failure){bookAnnotations=java.util.Collections.emptyList();
                ReaderDiagnosticLog.event("ANNOTATIONS","read_failed type="+failure.getClass().getSimpleName());}
            final java.util.List<ReaderAnnotationsStore.Annotation> preparedAnnotations=bookAnnotations;
            runOnUiThread(()->{
                long uiBegin=SystemClock.elapsedRealtime();
                stop();title=loadedTitle;author=loadedAuthor==null?"":loadedAuthor;body=loadedText;
                map=prepared;words=prepared.words;chunks=indexed;
                annotations=preparedAnnotations;selectionFrom=-1;selectionTo=-1;
                actualChapters=parsedChapters==null?BookChapterIndex.empty():parsedChapters;
                chapters=parsedChapters!=null&&parsedChapters.size()>0?parsedChapters:BookChapterIndex.empty();
                ttsParts=session.speechSegments;ttsSentenceIndex=session.sentenceIndex;docUri=uri.toString();
                bookmarkItems=ReaderBookmarks.list(this,docUri);
                prefs.edit().putString("book_title:"+docUri,title)
                    .putString("book_author:"+docUri,author)
                    .putInt("book_words:"+docUri,words.length).apply();
                if(reset)index=0;
                else if(!getIntent().hasExtra("word_index"))
                    index=map.clamp(UnifiedReadingProgress.read(prefs,docUri,index));
                else index=map.clamp(index);
                if(!reset&&!getIntent().hasExtra("word_index")){
                    viewportWord=prefs.getInt("viewport:"+docUri,-1);
                    if(viewportWord<0)followSuspended=false;
                }
                chteicoInitialIndex=index;chteicoLastWordIndex=index;chteicoReadingSeconds=0;chteicoTouch();
                ChteicoStore.openBook(this,docUri,mode);
                draw();saveLight(true);
                if(sceneRoot!=null)sceneRoot.post(()->ReaderDiagnosticLog.event("READER_STARTUP",
                    "first_reader_frame_ms="+(SystemClock.elapsedRealtime()-requestAt)));
                // Do not contact Edge merely because a book was opened in a non-TTS
                // mode. Speculative startup requests multiplied handshakes on mode
                // changes and triggered HTTP 429 even before the user selected Edge.
                if(mode==TTS)startTtsSession();
                ReaderDiagnosticLog.event("READER_STARTUP","loaded_ui_ms="+(SystemClock.elapsedRealtime()-uiBegin)+
                    " total_ms="+(SystemClock.elapsedRealtime()-requestAt)+" count="+words.length);
            });
        }catch(Exception ex){runOnUiThread(()->new SwipeDialogs.Builder(this).setMessage("Не удалось открыть книгу: "+ex.getMessage()).setPositiveButton("OK",null).show());}
    }).start();}
    private void settings(){if(mode!=TTS)stop();startActivity(new Intent(this,SettingsActivity.class).putExtra("rsvp_tab",true));}
    private void audioMenu(){
        if(docUri.isEmpty()){Toast.makeText(this,"Сначала откройте книгу",Toast.LENGTH_SHORT).show();return;}
        openAudioEngine(prefs.getBoolean("tts_last_local",true));
    }
    private void openAudioEngine(boolean androidTts){
        ttsLocal=androidTts;prefs.edit().putBoolean("tts_last_local",androidTts).apply();
        setMode(TTS);
    }
    private void startTtsSession(){
        if(mode!=TTS||docUri.isEmpty()||words.length==0)return;
        cancelTtsUi();
        ttsPendingPlay=false;
        ReaderTtsController.begin(this,ttsLocal,docUri,index);
        if(!ttsLocal){
            edgePlayerBindStartedAt=SystemClock.elapsedRealtime();
            ttsBoundPlayer=bindService(new Intent(this,EdgePlaybackService.class),ttsPlayerConnection,BIND_AUTO_CREATE);
            edgeStreamBindStartedAt=SystemClock.elapsedRealtime();
            ttsBoundStream=bindService(new Intent(this,EdgeStreamingService.class),ttsStreamConnection,BIND_AUTO_CREATE);
            ReaderDiagnosticLog.event("READER_STARTUP","edge_bind_requested_ms="+
                (edgePlayerBindStartedAt-launchStartedAt));
        }
        scheduleTtsUi();
        updatePlayButton();updateProgress();updateSleepTimerBadge();
    }
    private void releaseTts(){
        cancelTtsUi();ttsPendingPlay=false;pendingTtsWord=-1;
        cachedTimingKey="";timingLoadingKey="";
        cachedTimingSidecarLength=-1L;cachedTimingSidecarModified=-1L;
        cachedTimingWords=java.util.Collections.emptyList();lastTtsError="";
        if(ttsLocal){
            // Leaving the Activity must not stop a background speech session.
        }else{
            if(ttsPlayer!=null){ttsPlayer.removeListener(ttsPlayerListener);}
            if(ttsStreamer!=null){ttsStreamer.removeObserver(ttsStreamObserver);}
            if(ttsBoundStream){unbindService(ttsStreamConnection);ttsBoundStream=false;}
            if(ttsBoundPlayer){unbindService(ttsPlayerConnection);ttsBoundPlayer=false;}
            ttsStreamer=null;ttsPlayer=null;
        }
    }
    /** Buffering is still an active Play request: TOC must browse rather than seek. */
    private boolean ttsPlaybackRequested(){
        if(ttsLocal){
            AudioPlaybackService local=AudioPlaybackService.instance;
            return local!=null&&local.isTtsMode()&&!local.isPaused();
        }
        return ttsPendingPlay||(ttsPlayer!=null&&ttsPlayer.playRequested()
            &&("edge:"+docUri).equals(ttsPlayer.currentBookId()));
    }
    private boolean ttsCurrentlyPlaying(){
        if(ttsLocal){AudioPlaybackService s=AudioPlaybackService.instance;
            return s!=null&&s.isTtsMode()&&!s.isPaused();}
        return ttsPendingPlay||ttsPlayer!=null&&ttsPlayer.playRequested()
            &&("edge:"+docUri).equals(ttsPlayer.currentBookId());
    }
    private void toggleTtsPlayback(){
        if(!ttsLocal&&!EdgeFolderPicker.available(this)&&!ttsCurrentlyPlaying()){
            resumeEdgeAfterFolderPick=true;
            new SwipeDialogs.Builder(this).setTitle("Папка аудиофайлов Edge TTS")
                .setMessage("Перед первой озвучкой выберите папку для сохранения MP3 по книгам и главам. Без выбора сохранение в Android/data не начинается.")
                .setNegativeButton("Отмена",(d,w)->resumeEdgeAfterFolderPick=false)
                .setPositiveButton("Использовать эту папку",(d,w)->EdgeFolderPicker.choose(this)).show();
            return;
        }
        // Rewind is owned by the playback services, not the on-screen button.
        // This also covers Bluetooth/headset and MediaSession commands.
        if(!ttsCurrentlyPlaying())returnToActiveWord();
        if(ttsLocal){ReaderTtsController.localPauseOrResume(this);
        }else if(ttsPlayer!=null){
            boolean thisBook=("edge:"+docUri).equals(ttsPlayer.currentBookId());
            if(thisBook&&ttsPlayer.playRequested())ttsPlayer.pause();
            else if(ttsStreamer!=null){
                ttsStreamer.activatePreparedBook();ttsPlayer.play();ttsStreamer.ensurePlaySelection();
            }else ttsPendingPlay=true;
        }else ttsPendingPlay=!ttsPendingPlay;
        updatePlayButton();scheduleTtsUi();
    }
    private void selectTtsWord(int at){
        // A tap selects the sentence; the book's UI block is never reloaded.
        abandonViewportPreview();
        index=map.clamp(at);
        index=ttsSentenceIndex.beginning(index);
        pendingTtsWord=index;
        pendingTtsSelectedAt=SystemClock.uptimeMillis();
        navigationEpoch++;
        ReaderDiagnosticLog.event("NAVIGATION","USER_SELECT chapter="+chapterForWord(index)+" word="+index+" epoch="+navigationEpoch);
        prefs.edit().putInt("tts_word:"+docUri,index).apply();
        boolean autoplay=prefs.getBoolean("tts_tap_autoplay",false);
        if(ttsLocal){
            ReaderTtsController.localSelect(this,index);
        }else if(ttsStreamer!=null){
            boolean audible=ttsPlayer!=null&&ttsPlayer.isPlaying();
            if(ttsPlayer!=null)ttsPlayer.pause();
            ttsStreamer.setAnchor(index,true);
            if(autoplay||audible){if(ttsPlayer!=null){ttsPlayer.play();ttsStreamer.ensurePlaySelection();}else ttsPendingPlay=true;}
        }
    }
    private void refreshTtsPosition(){
        if(mode!=TTS||words.length==0)return;
        int actual=-1;
        if(ttsLocal){AudioPlaybackService s=AudioPlaybackService.instance;
            if(s!=null&&s.isTtsMode())actual=map.atCharacter(s.currentTtsWordRange()[0]);
        }else if(ttsStreamer!=null){
            EdgeStreamingService.Clip clip=ttsStreamer.currentClip();
            String problem=ttsStreamer.error();
            if(ttsPlayer!=null&&ttsPlayer.playbackError()!=null&&!ttsPlayer.playbackError().isEmpty())
                problem=ttsPlayer.playbackError();
            // A 429 is recorded in Logcat rather than surfaced as a Toast.
            // Media3 may report the stream failure separately from EdgeStreamingService.
            if(ttsStreamer.lastRateLimit() || (problem!=null &&
                    (problem.contains("HTTP 429") ||
                     problem.contains("Edge TTS ограничил запросы"))))problem="";
            if(problem!=null&&!problem.isEmpty()&&!problem.equals(lastTtsError)){
                lastTtsError=problem;Toast.makeText(this,problem,Toast.LENGTH_LONG).show();
            }else if(problem==null||problem.isEmpty())lastTtsError="";
            if(clip!=null&&ttsPlayer!=null&&(clip.key+":"+clip.start).equals(ttsPlayer.currentKey())){
                String timingKey=clip.key+":"+clip.start;
                java.util.List<EdgeWordTimings.Word> ticks;
                if(clip.ready()){
                    java.io.File sidecar=EdgeWordTimings.sidecar(clip.file);
                    long sideLength=sidecar.isFile()?sidecar.length():0L;
                    long sideModified=sidecar.isFile()?sidecar.lastModified():0L;
                    boolean changed=timingKey.equals(cachedTimingKey)&&cachedTimingWords.isEmpty()
                        &&(sideLength!=cachedTimingSidecarLength||sideModified!=cachedTimingSidecarModified);
                    if((!timingKey.equals(cachedTimingKey)||changed)&&!timingKey.equals(timingLoadingKey)){
                        timingLoadingKey=timingKey;
                        final java.io.File file=clip.file;
                        final String spoken=clip.text;
                        final String requested=timingKey;
                        timingReader.execute(()->{
                            java.util.List<EdgeWordTimings.Word> loaded=EdgeWordTimings.read(file,spoken);
                            handler.post(()->{
                                if(!requested.equals(timingLoadingKey))return;
                                timingLoadingKey="";cachedTimingWords=loaded;
                                cachedTimingKey=requested;
                                java.io.File side=EdgeWordTimings.sidecar(file);
                                cachedTimingSidecarLength=side.isFile()?side.length():0L;
                                cachedTimingSidecarModified=side.isFile()?side.lastModified():0L;
                                if(loaded.isEmpty()&&!requested.equals(lastMissingTimingKey)){
                                    lastMissingTimingKey=requested;
                                    ReaderDiagnosticLog.event("HIGHLIGHT","WORD_TIMINGS_EMPTY clip="+requested);
                                }
                                scheduleTtsUi();
                            });
                        });
                    }
                    ticks=timingKey.equals(cachedTimingKey)&&!cachedTimingWords.isEmpty()
                        ?cachedTimingWords:ttsStreamer.liveWords(clip);
                }else ticks=ttsStreamer.liveWords(clip);
                int at=EdgeWordTimings.at(ticks,ttsPlayer.positionMs());
                if(at>=0)actual=map.atCharacter(ttsStreamer.charForWord(clip.start)+ticks.get(at).from);
                else if(ticks.isEmpty()&&ttsPlayer.isPlaying())actual=clip.start;
            }
        }
        boolean active=ttsCurrentlyPlaying();
        if(pendingTtsWord>=0){
            SentenceChunks.Chunk target=ttsSentenceIndex.atWordRange(pendingTtsWord);
            if(target!=null&&actual>=target.fromWord&&actual<target.toWord)pendingTtsWord=-1;
            else{
                EdgeStreamingService.Clip activeClip=ttsStreamer==null?null:ttsStreamer.currentClip();
                if(activeClip!=null&&ttsPlayer!=null&&ttsPlayer.isPlaying()
                    &&(activeClip.key+":"+activeClip.start).equals(ttsPlayer.currentKey())
                    &&activeClip.start>=0&&activeClip.end>activeClip.start
                    &&target!=null&&actual>=activeClip.start&&actual<activeClip.end
                    &&((activeClip.start>=target.fromWord&&activeClip.start<target.toWord)
                        ||SystemClock.uptimeMillis()-pendingTtsSelectedAt>8000L)){
                    // Playback has advanced to a verified active clip. The former
                    // tap guard is no longer allowed to hold back WordBoundary.
                    ReaderDiagnosticLog.event("NAVIGATION","PENDING_SELECTION_RELEASED word="+actual);
                    pendingTtsWord=-1;
                }else{
                if(actual>=0&&lastIgnoredEpoch!=navigationEpoch){
                    lastIgnoredEpoch=navigationEpoch;
                    ReaderDiagnosticLog.event("NAVIGATION","NAVIGATION_IGNORED_STALE epoch="+navigationEpoch+
                        " actual="+actual+" selected="+pendingTtsWord);
                }
                actual=-1; // stale callback from the previous streamed sentence
                }
            }
        }
        // Edge and local TTS are credited only for listening across chapter boundaries.
        if(mode==TTS&&active&&actual>=0&&actual<words.length){
            int chapter=chapterForWord(actual);
            long time=SystemClock.elapsedRealtime();
            if(chapter>=0){
                if(fishingTtsChapter<0){
                    fishingTtsChapter=chapter;fishingTtsStartWord=actual;
                    fishingTtsAccumulatedMs=0;
                }else if(chapter!=fishingTtsChapter){
                    if(fishingTtsAccumulatedMs>=30000L && Math.abs(actual-fishingTtsStartWord)>=25)
                        ChteicoStore.awardAudioChapter(this,docUri,fishingTtsChapter);
                    fishingTtsChapter=chapter;fishingTtsStartWord=actual;fishingTtsAccumulatedMs=0;
                }else if(fishingTtsTime>0){
                    fishingTtsAccumulatedMs+=Math.min(700L,Math.max(0,time-fishingTtsTime));
                }
            }
            fishingTtsTime=time;
        }else fishingTtsTime=0;
        if(active&&actual>=0&&actual<words.length&&actual!=index){
            int old=index;index=actual;refreshHighlight(old);updateProgress();
            saveLight(false);
            // A manual drag owns the viewport until overlay, mode return, or Play.
            if(!followSuspended)ensureWordVisible(false);
        }
        if(followSuspended&&mode==TTS)updateFloatingCaption();
        updatePlayButtonIfChanged();
        updateSleepTimerBadge();
    }
    private void updatePlayButtonIfChanged(){
        boolean now=ttsCurrentlyPlaying();
        if(!playStateRendered||lastRenderedTtsPlayState!=now){
            lastRenderedTtsPlayState=now;playStateRendered=true;updatePlayButton();
        }
    }
    private void chooseTtsEngine(){
        if(mode!=TTS)return;
        UiNavigationSheet.showTopCustom(this,"Способ озвучивания",(choices,dialog)->{
        int[] icons={R.drawable.ic_android_tts,R.drawable.ic_edge_tts};
        String[] titles={"Google TTS — локальный","Microsoft Edge TTS — онлайн"};
        String[] details={"Работает без интернета с установленным системным движком",
            "Нейросетевые голоса, потоковое MP3 и кэширование"};
        for(int i=0;i<2;i++){
            final boolean localChoice=i==0;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18),dp(13),dp(18),dp(13));
            ImageView picture=new ImageView(this);picture.setImageResource(icons[i]);
            row.addView(picture,new LinearLayout.LayoutParams(dp(28),dp(28)));
            LinearLayout labels=column();labels.setPadding(dp(13),0,dp(10),0);
            row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            TextView primary=text(titles[i],16);primary.setSingleLine(false);labels.addView(primary);
            TextView hint=text(details[i],12);hint.setSingleLine(false);hint.setMaxLines(4);hint.setAlpha(.72f);labels.addView(hint);
            if(ttsLocal==localChoice)row.addView(text("✓",20));
            row.setOnClickListener(v->{dialog.dismiss();
                if(localChoice==ttsLocal)return;
                int position=index;releaseTts();ttsLocal=localChoice;
                prefs.edit().putBoolean("tts_last_local",localChoice)
                    .putInt("tts_word:"+docUri,position).apply();
                if(toolbarEngineAction!=null)toolbarEngineAction.setImageResource(
                    localChoice?R.drawable.ic_android_tts:R.drawable.ic_edge_tts);
                startTtsSession();
            });
            row.setMinimumHeight(dp(86));choices.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        });
    }
    private void chooseTtsVoice(){
        if(mode!=TTS)return;
        if(ttsLocal){LocalVoiceCatalog.show(this,()->startService(new Intent(this,AudioPlaybackService.class)
            .setAction(AudioPlaybackService.ENGINE)));return;}
        String[] names={"Светлана","Дмитрий"};String[] keys={"ru-RU-SvetlanaNeural","ru-RU-DmitryNeural"};
        int selected=prefs.getString("edge_voice",keys[0]).contains("Dmitry")?1:0;
        UiNavigationSheet.showTopChoices(this,"Голос Microsoft Edge",names,selected,n->{
            prefs.edit().putString("edge_voice",keys[n]).apply();
            if(ttsStreamer!=null)ttsStreamer.choose(keys[n],null);
        });
    }
    private int chapterEndWord(int startWord){
        if(chapters==null||chapters.size()==0||map.starts.length==0)return map.starts.length;
        BookChapterIndex.Chapter chapter=chapters.atChar(map.starts[map.clamp(startWord)]);
        return chapter==null?map.starts.length:map.atCharacter(chapter.endChar);
    }
    private int chapterEndAfter(int fromWord,int count){
        if(chapters==null||chapters.size()==0){
            // Books without trustworthy TOC use their existing text blocks.
            if(chunks!=null&&chunks.size()>0){
                int block=chunks.chunkForWord(map.clamp(fromWord));
                return chunks.get(Math.min(chunks.size()-1,block+Math.max(1,count)-1)).endWord;
            }
            return map.starts.length;
        }
        int startChar=map.starts.length>0?map.starts[map.clamp(fromWord)]:0;
        int current=0;
        for(int i=0;i<chapters.size();i++)if(chapters.get(i).startChar<=startChar)current=i;
        int endIdx=Math.min(chapters.size()-1,current+Math.max(1,count)-1);
        return map.atCharacter(chapters.get(endIdx).endChar);
    }
    private long sleepDeadlineRemaining(){
        if(ttsLocal){
            AudioPlaybackService local=AudioPlaybackService.instance;
            if(local!=null&&local.isTtsMode())return local.getSleepRemainingMillis();
        }else if(ttsPlayer!=null)return ttsPlayer.sleepRemainingMs();
        return Math.max(0L,prefs.getLong(PlaybackSleepCountdown.TTS_KEY,0L));
    }
    private int currentPlaybackWord(){
        if(ttsLocal){AudioPlaybackService local=AudioPlaybackService.instance;
            if(local!=null&&local.isTtsMode()){
                int[] spoken=local.currentTtsWordRange();
                return map.clamp(map.atCharacter(spoken[0]));
            }
        }else if(ttsPlayer!=null&&ttsStreamer!=null){
            EdgeStreamingService.Clip playingClip=ttsStreamer.currentClip();
            if(playingClip!=null&&playingClip.start<=index&&index<playingClip.end)return map.clamp(index);
            if(playingClip!=null)return map.clamp(playingClip.start);
        }
        return map.clamp(index);
    }
    private int sleepTargetWord(){
        if(ttsLocal){AudioPlaybackService local=AudioPlaybackService.instance;
            if(local!=null&&local.isTtsMode()&&local.ttsSleepEndWord()>=0)
                return local.ttsSleepEndWord();
            return prefs.getInt(PlaybackSleepCountdown.TTS_CHAPTER_KEY,-1);
        }
        if(ttsPlayer!=null)return ttsPlayer.sleepChapterEndWord();
        return prefs.getInt(PlaybackSleepCountdown.TTS_CHAPTER_KEY,-1);
    }
    private long sleepChapterRemaining(){
        int end=sleepTargetWord();if(end<0||words.length==0)return -1;
        int remaining=Math.max(0,end-currentPlaybackWord());
        return (remaining*60000L)/Math.max(1,TtsWpm.get(this));
    }
    private boolean sleepTimerActive(){
        return sleepDeadlineRemaining()>0||sleepTargetWord()>=0;
    }
    private void updateSleepTimerBadge(){
        if(mode!=TTS||toolbarSleepValue==null)return;
        long fixed=sleepDeadlineRemaining();
        long chapter=fixed<=0?sleepChapterRemaining():-1L;
        String label=fixed>0?((fixed+59999L)/60000L)+"м":
            chapter>=0?((chapter+59999L)/60000L)+"м":"";
        if(!label.contentEquals(toolbarSleepValue.getText())){
            toolbarSleepValue.setText(label);
            if(toolbarSleepAction!=null)toolbarSleepAction.setContentDescription(label.isEmpty()?"Таймер сна выключен":
                "Таймер сна, осталось "+label+(chapter>=0?" до конца главы":""));
        }
        if(toolbarSleepAction instanceof FrameLayout){
            FrameLayout badge=(FrameLayout)toolbarSleepAction;
            for(int i=0;i<badge.getChildCount();i++)if(badge.getChildAt(i) instanceof ImageView){
                SleepTimerBadge.layout(badge,(ImageView)badge.getChildAt(i),toolbarSleepValue,!label.isEmpty(),22);
                break;
            }
        }
    }
    private void chooseTtsSleep(){
        if(mode!=TTS)return;
        SleepTimerSheet.open(this,new SleepTimerSheet.Listener(){
            @Override public void minutes(int minutes){
                prefs.edit().remove(PlaybackSleepCountdown.TTS_CHAPTER_KEY).apply();
                if(ttsLocal)startService(new Intent(ReaderActivity.this,AudioPlaybackService.class)
                    .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",minutes));
                else if(ttsPlayer!=null)ttsPlayer.setSleepMinutes(minutes);
                updateSleepTimerBadge();scheduleTtsUi();
            }
            @Override public void chapters(int count){
                int end=chapterEndAfter(currentPlaybackWord(),count);
                if(ttsLocal){
                    prefs.edit().putInt(PlaybackSleepCountdown.TTS_CHAPTER_KEY,end).apply();
                    startService(new Intent(ReaderActivity.this,AudioPlaybackService.class)
                        .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",-count).putExtra("end_word",end));
                }else if(ttsPlayer!=null)ttsPlayer.setSleepChapterEnd(end);
                updateSleepTimerBadge();scheduleTtsUi();
            }
            @Override public long remainingMillis(int count){
                int position=currentPlaybackWord(),end=chapterEndAfter(position,count);
                return Math.max(0,end-position)*60000L/Math.max(1,TtsWpm.get(ReaderActivity.this));
            }
        });
    }
    @Override protected void onResume(){super.onResume();
        if(readerWasPaused&&words.length>0&&!followSuspended) returnToActiveWord();
        readerWasPaused=false;
        if(!localStateReceiverRegistered){
            IntentFilter stateFilter=new IntentFilter(AudioPlaybackService.ACTION_STATE);
            if(Build.VERSION.SDK_INT>=33)this.registerReceiver(localStateReceiver,stateFilter,Context.RECEIVER_NOT_EXPORTED);
            else this.registerReceiver(localStateReceiver,stateFilter);
            localStateReceiverRegistered=true;
        }
        if(mode==TTS)scheduleTtsUi();
        chteicoResumed=true;chteicoTouch();handler.removeCallbacks(chteicoReadTick);handler.postDelayed(chteicoReadTick,10000);
        if(prefs!=null){
        int oldBg=bg,oldFg=fg,oldSize=size,oldFont=font,oldMode=mode,oldPortion=portion;
        int oldCursorBg=cursorBackground,oldCursorText=cursorText,oldCursorScale=cursorScale;
        int oldNormalText=normalSelectText,oldNormalScale=normalSelectScale,oldButton=buttonColor,oldAccent=accentButtonColor;
        int oldHighlightColor=sentenceHighlightColor,oldHighlightOpacity=sentenceHighlightOpacity;
        int oldFocus=focusColor,oldLineColor=lineColor,oldLinePos=linePosition;
        boolean oldPoint=focusPoint,oldLines=focusLinesEnabled;

        readPreferences();
        // Speech-only punctuation/dictionary settings can change while the
        // settings screen is open; refresh the Edge cache identity on return.
        if(ttsStreamer!=null)ttsStreamer.refreshSettings();
        int wantedMode=mode;
        if(root!=null&&words.length>0&&wantedMode!=oldMode){
            mode=oldMode;setMode(wantedMode);
        }
        if(root!=null&&words.length>0&&(!lastAppliedFont.equals(ReaderFontLibrary.choice(this,prefs))||oldBg!=bg||oldFg!=fg||oldSize!=size||oldFont!=font||oldMode!=mode||oldPortion!=portion
            ||oldCursorBg!=cursorBackground||oldCursorText!=cursorText||oldCursorScale!=cursorScale||oldNormalText!=normalSelectText
            ||oldNormalScale!=normalSelectScale||oldButton!=buttonColor||oldAccent!=accentButtonColor||accentAtDraw!=UiColorScheme.accent(this)
            ||oldHighlightColor!=sentenceHighlightColor||oldHighlightOpacity!=sentenceHighlightOpacity||oldFocus!=focusColor||oldLineColor!=lineColor
            ||oldLinePos!=linePosition||oldPoint!=focusPoint||oldLines!=focusLinesEnabled))draw();
        else if(words.length>0)updateVisibleAnnotations();

    }}
    @Override protected void onPause(){
        readerWasPaused=true;
        cancelTtsUi();
        if(localStateReceiverRegistered){try{this.unregisterReceiver(localStateReceiver);}catch(Exception ignored){}
            localStateReceiverRegistered=false;}
        chteicoResumed=false;handler.removeCallbacks(chteicoReadTick);
        chteicoCompleteIfReachedEnd();save();stop();super.onPause();
    }
    @Override protected void onDestroy(){
        cancelPendingLongPress();dismissSelectionMenu();
        if(annotationStore!=null){annotationStore.close();annotationStore=null;}
        timingReader.shutdownNow();if(mode==TTS)releaseTts();stop();applySystemBars(false);super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}
    private class FocusLinesView extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        FocusLinesView(Context ctx){super(ctx);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!focusLinesEnabled||mode!=RSVP)return;
            paint.setColor(lineColor==NO_COLOR?fg:lineColor);paint.setStrokeWidth(dp(1));paint.setStyle(Paint.Style.STROKE);
            float x=getWidth()*linePosition/100f,mid=getHeight()/2f,half=dp(45);
            canvas.drawLine(0,mid-half,getWidth(),mid-half,paint);canvas.drawLine(0,mid+half,getWidth(),mid+half,paint);
            paint.setStrokeWidth(dp(2));canvas.drawLine(x,mid-half,x,mid-half+dp(9),paint);
            canvas.drawLine(x,mid+half-dp(9),x,mid+half,paint);
        }
    }
}
