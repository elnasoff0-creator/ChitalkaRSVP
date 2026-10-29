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
public class ReaderActivity extends Activity {
    private static final int NORMAL=0,RSVP=1,CURSOR=2,TTS=3,NO_COLOR=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,speedPanel,controls,toolbar,toolbarStack,toolbarWpm,modeRow,readingFrame;
    private View toolbarSpacer;
    private TextView toolbarBookName;
    private FrameLayout bottomBar;
    private LinearLayout contentsAction;
    private ImageButton contentsIcon;
    private LinearLayout bookHeader;
    private BookChapterIndex chapters=BookChapterIndex.empty();
    private java.util.ArrayList<SentenceChunks.Chunk> ttsParts=new java.util.ArrayList<>();
    private int pendingTtsWord=-1;
    private String cachedTimingKey="",lastTtsError="";
    private java.util.List<EdgeWordTimings.Word> cachedTimingWords=java.util.Collections.emptyList();
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
    private ImageButton toolbarModeAction,toolbarEngineAction,toolbarVoiceAction,toolbarSleepAction,toolbarThemeAction;
    private boolean rsvpScrollWords=false;
    private boolean ttsLocal=true,ttsBoundPlayer=false,ttsBoundStream=false,ttsPendingPlay=false;
    private EdgePlaybackService ttsPlayer;
    private EdgeStreamingService ttsStreamer;
    // A manual scroll suspends follow until one of the three explicit return actions.
    private boolean followSuspended=false,readerWasPaused=false;
    private TextView ttsFloatingCaption;
    private int dictionarySelectedWord=-1;
    private long navigationEpoch=0L;
    private long lastIgnoredEpoch=-1L;

    private final EdgeStreamingService.Observer ttsStreamObserver=()->{if(ReaderActivity.this.mode==TTS)refreshTtsPosition();};
    private final EdgePlaybackService.Listener ttsPlayerListener=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean completed){if(mode==TTS)refreshTtsPosition();}
        @Override public void onPlaybackStateChanged(){if(mode==TTS)refreshTtsPosition();}
    };
    private final ServiceConnection ttsPlayerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(mode!=TTS||ttsLocal)return;
            ttsPlayer=((EdgePlaybackService.LocalBinder)binder).getService();
            ttsPlayer.prepareBook("edge:"+docUri);ttsPlayer.restoreSharedSleepTimer();
            ttsPlayer.setAutoContinue(true);ttsPlayer.setPlaybackSpeed(Math.min(5f,TtsWpm.rate(ReaderActivity.this)));
            ttsPlayer.addListener(ttsPlayerListener);
            if(ttsPendingPlay){ttsPendingPlay=false;ttsPlayer.play();if(ttsStreamer!=null)ttsStreamer.ensurePlaySelection();}
            refreshTtsPosition();
        }
        @Override public void onServiceDisconnected(ComponentName name){ttsPlayer=null;}
    };
    private final ServiceConnection ttsStreamConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(mode!=TTS||ttsLocal)return;
            ttsStreamer=((EdgeStreamingService.LocalBinder)binder).getService();
            ttsStreamer.openBook(docUri,index);ttsStreamer.addObserver(ttsStreamObserver);
            // Binding and entering TTS are not manual sentence jumps.
            if(pendingTtsWord>=0)ttsStreamer.setAnchor(pendingTtsWord,true);
            else ttsStreamer.setAnchor(index,false);
            ttsStreamer.setActive(true);
            if(ttsPlayer!=null&&ttsPlayer.playRequested())ttsStreamer.ensurePlaySelection();
        }
        @Override public void onServiceDisconnected(ComponentName name){ttsStreamer=null;}
    };
    private FocusLinesView focusLines;
    private ReadingWordMap map=ReadingWordMap.parse("");
    private BookChunkIndex chunks;
    private String body="",title="",author="",docUri="";
    private String[] words=new String[0];
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
        if(mode==TTS){refreshTtsPosition();handler.postDelayed(this,180);}
    }};
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
    private boolean chteicoResumed=false;
    private final Runnable chteicoReadTick=new Runnable(){@Override public void run(){
        if(!chteicoResumed)return;
        long now=SystemClock.elapsedRealtime();
        if(words.length>0 && !docUri.isEmpty() && (playing && !waitingForStart || now-chteicoLastInteraction<65000)){
            ChteicoStore.tickReading(ReaderActivity.this,mode,10);
            if(index>chteicoLastWordIndex)ChteicoStore.recordWords(ReaderActivity.this,index-chteicoLastWordIndex);
            chteicoLastWordIndex=index;
            chteicoReadingSeconds+=10;
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
        index=prefs.getInt("position:"+docUri,index);
        if(!getIntent().getBooleanExtra("reset",false))index=prefs.getInt("tts_word:"+docUri,index);
        if(getIntent().getBooleanExtra("reset",false))index=0;
        int requestedMode=getIntent().getIntExtra("reading_mode_override",-1);
        if(requestedMode>=0&&requestedMode<=TTS){mode=requestedMode;
            prefs.edit().putInt("reading_mode",requestedMode).apply();}
        if(getIntent().hasExtra("tts_mode_local")){
            ttsLocal=getIntent().getBooleanExtra("tts_mode_local",ttsLocal);
            prefs.edit().putBoolean("tts_last_local",ttsLocal).apply();
        }
        if(!getIntent().getBooleanExtra("reset",false)&&getIntent().hasExtra("word_index"))
            index=Math.max(0,getIntent().getIntExtra("word_index",index));
        draw();if(!docUri.isEmpty())load(Uri.parse(docUri),getIntent().getBooleanExtra("reset",false));
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
    private Typeface type(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
    private int panelColor(){return ReadingThemeCycle.isDark(bg)?0xff303238:0xfffff9f0;}
    private int buttonBg(){return buttonColor==NO_COLOR?panelColor():buttonColor;}
    private int accent(){return accentButtonColor==NO_COLOR?0xff915439:accentButtonColor;}
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

    private void draw(){
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
        toolbarSpacer=new View(this);
        toolbar.addView(toolbarSpacer,new LinearLayout.LayoutParams(0,dp(1),1));
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
        toolbarSleepAction=icon(R.drawable.ic_sleep_timer,v->chooseTtsSleep());
        toolbarSleepAction.setContentDescription("Таймер сна");
        LinearLayout.LayoutParams sleepLp=new LinearLayout.LayoutParams(dp(44),dp(44));sleepLp.leftMargin=dp(5);
        toolbar.addView(toolbarSleepAction,sleepLp);
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
            ReaderSheets.overflow(this,mode==TTS,which->{if(which==0)bookmark();else if(which==1)settings();
                    else if(mode==TTS&&which==2)chooseTtsVoice();
                    else if(which==(mode==TTS?4:3))openEdgeDictionary();
                    else if(which==(mode==TTS?5:4))showBookSearch();
                    else if(!docUri.isEmpty())startActivity(new Intent(this,EdgeChapterDownloadsActivity.class)
                        .putExtra("book_uri",docUri).putExtra("book_title",title));});
        }),settingsLp);
        toolbarBookName=text("",13);
        toolbarBookName.setSingleLine(true);
        toolbarBookName.setEllipsize(TextUtils.TruncateAt.END);
        toolbarBookName.setPadding(dp(6),dp(4),dp(6),dp(3));
        toolbarStack.addView(toolbarBookName,new LinearLayout.LayoutParams(-1,dp(25)));
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
        modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);
        progressView=text("",12);progressView.setGravity(Gravity.CENTER_VERTICAL);
        selectWordButton=new ImageView(this);selectWordButton.setImageResource(R.drawable.ic_select_word);
        selectWordButton.setPadding(dp(11),dp(11),dp(11),dp(11));selectWordButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        selectWordButton.setContentDescription("Выбор слова");
        selectWordButton.setOnClickListener(v->{selecting=!selecting;normalChromeVisible=true;
            updateSelectWordButton();refreshHighlight(index);applyFullscreenUi();});
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
                    chteicoTouch();userScrolling=true;followSuspended=true;
                    if(mode==TTS)updateFloatingCaption();
                    if(mode==RSVP){stop();scrollWordAccumulator=0;}
                }
                if(state==RecyclerView.SCROLL_STATE_IDLE){
                    userScrolling=false;
                    if(mode==RSVP){scrollWordAccumulator=0;setRsvpContextVisible(!playing);}
                    saveLight(true);
                }
            }
            @Override public void onScrolled(RecyclerView rv,int dx,int dy){
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
                if(!userScrolling||restoringScroll||words.length==0)return;
                if(mode!=RSVP||playing||!rsvpScrollWords)return;
                stepRsvpByScroll(dy);
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
        speedPanel=null;
        updateModeUI();updatePlayButton();
        // Jump to the indexed chunk, not to the pixel position of an enormous TextView.
        textList.post(()->{if(index<=0)layoutManager.scrollToPositionWithOffset(0,0);
            else ensureWordVisible(true);});
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
                int first=map.clamp(index);
                // A manually selected Edge word stays highlighted independently of
                // the spoken sentence's start and moving WordBoundary cursor.
                if(mode==TTS&&dictionarySelectedWord>=c.startWord&&dictionarySelectedWord<c.endWord){
                    int selectedFrom=map.starts[dictionarySelectedWord]-c.startChar;
                    int selectedTo=map.ends[dictionarySelectedWord]-c.startChar;
                    if(selectedFrom>=0&&selectedTo<=content.length()&&selectedFrom<selectedTo)
                        span(new BackgroundColorSpan(0x99ffda00),selectedFrom,selectedTo);
                }
                if(first<c.startWord||first>=c.endWord)return;
                int endWord=Math.min(c.endWord-1,Math.min(words.length-1,first+portion-1));
                int from=map.starts[first]-c.startChar,to=map.ends[endWord]-c.startChar;
                if(from<0||to>content.length()||to<=from)return;
                if(mode==CURSOR){
                    if(cursorBackground!=NO_COLOR)span(new BackgroundColorSpan(cursorBackground),from,to);
                    if(cursorText!=NO_COLOR)span(new ForegroundColorSpan(cursorText),from,to);
                    if(cursorScale>100)span(new RelativeSizeSpan(cursorScale/100f),from,to);
                }else if(mode==TTS){
                    SentenceChunks.Chunk sentence=SentenceChunks.forWord(ttsParts,first);
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
                }else if(mode==NORMAL&&(selecting||selectedWordActive)){
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
            item.setPadding(dp(14),dp(7),dp(14),dp(7));
            item.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            Holder holder=new Holder(item);
            // Consume DOWN in selection mode so Android delivers UP for word tapping;
            // the parent RecyclerView can still intercept a scrolling drag.
            item.setClickable(true);
            item.setOnTouchListener((view,event)->{
                boolean selectingWord=(mode==CURSOR)||(mode==NORMAL&&selecting)
                    ||(mode==RSVP&&!playing&&!rsvpScrollWords)||(mode==TTS);
                boolean togglingChrome=false;
                if(!selectingWord&&!togglingChrome)return false;
                int eventType=event.getActionMasked();
                if(eventType==MotionEvent.ACTION_DOWN){
                    touchDownX=event.getX();touchDownY=event.getY();
                    touchDownTime=event.getEventTime();touchMoved=false;return true;
                }
                int slop=ViewConfiguration.get(ReaderActivity.this).getScaledTouchSlop();
                if(eventType==MotionEvent.ACTION_MOVE){
                    if(Math.abs(event.getX()-touchDownX)>slop||Math.abs(event.getY()-touchDownY)>slop)touchMoved=true;
                    return true;
                }
                if(eventType==MotionEvent.ACTION_CANCEL){touchMoved=true;return true;}
                if(eventType!=MotionEvent.ACTION_UP)return true;
                if(touchMoved||Math.abs(event.getX()-touchDownX)>slop||Math.abs(event.getY()-touchDownY)>slop
                    ||event.getEventTime()-touchDownTime>ViewConfiguration.getLongPressTimeout()
                    ||textList.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE)return true;
                if(togglingChrome){
                    normalChromeVisible=!normalChromeVisible;applyFullscreenUi();return true;
                }
                int row=holder.getBindingAdapterPosition()-1;if(row<0)return true;
                android.text.Layout l=item.getLayout();if(l==null)return true;
                int y=(int)event.getY()-item.getTotalPaddingTop();
                int line=l.getLineForVertical(Math.max(0,y));
                int offset=l.getOffsetForHorizontal(line,Math.max(0,event.getX()-item.getTotalPaddingLeft()));
                int old=index;index=map.atCharacter(chunks.get(row).startChar+offset);
                selectedWordActive=true;
                if(mode==TTS){dictionarySelectedWord=index;selectTtsWord(index);}
                refreshHighlight(old);
                if(mode==TTS&&dictionarySelectedWord>=0&&chunks!=null)
                    chunkAdapter.updateVisible(chunks.chunkForWord(dictionarySelectedWord));
                updateProgress();saveLight(true);
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
        return (seconds/3600)+" ч "+((seconds%3600)/60)+" мин";
    }
    private void updatePlayButton(){
        boolean active=mode==TTS?ttsCurrentlyPlaying():playing;
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
    private int chapterForWord(int word){
        if(chapters==null||map.starts.length==0)return -1;
        int pos=map.starts[map.clamp(word)],result=-1;
        for(int i=0;i<chapters.size();i++)if(chapters.get(i).startChar<=pos)result=i;
        return result;
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
    private void updateFloatingCaption(){
        if(ttsFloatingCaption==null)return;
        if(mode!=TTS||!followSuspended||!ttsCurrentlyPlaying()||words.length==0){
            ttsFloatingCaption.setVisibility(View.GONE);return;
        }
        SentenceChunks.Chunk part=SentenceChunks.forWord(ttsParts,map.clamp(index));
        if(part==null){ttsFloatingCaption.setText(words[map.clamp(index)]);}
        else{
            int first=Math.max(0,part.fromChar);
            int through=Math.min(body.length(),map.ends[map.clamp(index)]);
            // Progressive transcript: reveal only words whose playback boundary passed.
            String content=body.substring(first,Math.max(first,through)).trim();
            ttsFloatingCaption.setText(content.isEmpty()?words[map.clamp(index)]:content);
        }
        ttsFloatingCaption.setVisibility(View.VISIBLE);
        ttsFloatingCaption.post(()->{
            if(ttsFloatingCaption!=null&&ttsFloatingCaption.getLayout()!=null){
                android.text.Layout layout=ttsFloatingCaption.getLayout();
                int hidden=Math.max(0,layout.getHeight()-ttsFloatingCaption.getHeight()+ttsFloatingCaption.getTotalPaddingBottom());
                ttsFloatingCaption.scrollTo(0,hidden);
            }
        });
    }
    private void returnToActiveWord(){
        followSuspended=false;userScrolling=false;
        if(ttsFloatingCaption!=null)ttsFloatingCaption.setVisibility(View.GONE);
        if(textList!=null)textList.post(()->ensureWordVisible(true));
    }
    private void openEdgeDictionary(){
        Intent intent=new Intent(this,EdgeDictionaryActivity.class);
        if(dictionarySelectedWord>=0&&dictionarySelectedWord<words.length){
            intent.putExtra(EdgeDictionaryActivity.EXTRA_WORD,words[dictionarySelectedWord]);
            intent.putExtra(EdgeDictionaryActivity.EXTRA_CONTEXT,body.substring(Math.max(0,map.starts[dictionarySelectedWord]-70),Math.min(body.length(),map.ends[dictionarySelectedWord]+70)));
        }
        startActivity(intent);
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
                    followSuspended=false;
                    if(mode==TTS)dictionarySelectedWord=index;
                    refreshHighlight(previous);
                    if(chunks!=null&&mode==TTS)chunkAdapter.updateVisible(chunks.chunkForWord(dictionarySelectedWord));
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
    private void showToc(){
        UiNavigationSheet.showContents(this,chapters!=null&&chapters.size()>=3?chapters:BookChapterIndex.empty(),chunks,body,map,index,word->{
            if(word<0||word>=words.length)return;
            int previous=index;stop();index=word;selectedWordActive=true;
            refreshHighlight(previous);updateProgress();ensureWordVisible(true);saveLight(true);
        });
    }
    private void setToolbarExpanded(boolean show){
        if(toolbarExpanded==show)return;
        toolbarExpanded=show;
        ReaderToolbarController.reveal(toolbarStack,show,87);
    }
    private void setMode(int next){if(next<0||next>TTS||next==mode)return;
        chteicoTouch();stop();int old=index,previousMode=mode;
        if(previousMode==TTS)releaseTts();
        pendingTtsWord=-1;
        mode=next;selecting=false;normalChromeVisible=false;toolbarExpanded=true;
        ReaderToolbarController.reset(toolbarStack);
        updateModeUI();refreshHighlight(old);returnToActiveWord();saveLight(true);
        if(mode==TTS)startTtsSession();
    }
    private void chteicoTouch(){chteicoLastInteraction=SystemClock.elapsedRealtime();}
    private void chteicoCompleteIfReachedEnd(){
        if(words.length<300||docUri==null||docUri.isEmpty())return;
        boolean atEnd=index>=words.length-3;
        if(mode==NORMAL && textList!=null && !textList.canScrollVertically(1)
            && (index>chteicoInitialIndex||chteicoReadingSeconds>=60))atEnd=true;
        if(atEnd&&(chteicoReadingSeconds>=50||index>chteicoInitialIndex+30)){
            if(ChteicoStore.completeBook(this,docUri))ChteicoNotifications.milestone(this,"Книга завершена!","Чтейцо стал ещё ближе к взрослой стадии.");
        }
    }
    private void saveLight(boolean force){
        if(prefs==null)return;
        long now=SystemClock.elapsedRealtime();
        if(!force&&now-lastLightSave<4000)return;
        lastLightSave=now;
        prefs.edit().putString("uri",docUri).putInt("position:"+docUri,index).putInt("index",index)
            .putInt("reading_mode",mode).putBoolean("rsvp",mode==RSVP).putInt("wpm",wpm).apply();
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
            current.put("index",index);current.put("progress",(index+1)*100/Math.max(1,words.length));
            current.put("openedAt",System.currentTimeMillis());updated.put(current);
            prefs.edit().putString("library",updated.toString()).apply();
        }catch(Exception ignored){}
    }
    private void bookmark(){if(words.length==0)return;String key="bookmark:"+docUri;
        Set<String> saved=new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));saved.add(String.valueOf(index));
        prefs.edit().putStringSet(key,saved).apply();Toast.makeText(this,"Закладка добавлена",Toast.LENGTH_SHORT).show();
    }
    private void pick(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,7);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==7&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            load(uri,true);
        }
    }
    private void load(Uri uri,boolean reset){new Thread(()->{
        try{
            // Reuse exactly one immutable book, word map, chapter index and UI
            // chunk index for all four reading modes and their speech engines.
            UnifiedBookSession session=UnifiedBookSession.open(this,uri);
            BookLoader.Book book=session.book;
            final String loadedText=book.text;
            final String loadedTitle=book.title;
            final String loadedAuthor=book.author;
            final ReadingWordMap prepared=session.words;
            final BookChapterIndex parsedChapters=book.chapters;
            dictionarySelectedWord=-1;
            final BookChunkIndex indexed=session.blocks;
            runOnUiThread(()->{
                stop();title=loadedTitle;author=loadedAuthor==null?"":loadedAuthor;body=loadedText;
                map=prepared;words=prepared.words;chunks=indexed;
                chapters=parsedChapters!=null&&parsedChapters.size()>=3?parsedChapters:BookChapterIndex.empty();
                ttsParts=session.speechSegments;docUri=uri.toString();
                prefs.edit().putString("book_title:"+docUri,title)
                    .putString("book_author:"+docUri,author)
                    .putInt("book_words:"+docUri,words.length).apply();
                if(reset)index=0;else index=map.clamp(index);
                chteicoInitialIndex=index;chteicoLastWordIndex=index;chteicoReadingSeconds=0;chteicoTouch();
                ChteicoStore.openBook(this,docUri,mode);
                draw();saveLight(true);
                // Do not contact Edge merely because a book was opened in a non-TTS
                // mode. Speculative startup requests multiplied handshakes on mode
                // changes and triggered HTTP 429 even before the user selected Edge.
                if(mode==TTS)startTtsSession();
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
        handler.removeCallbacks(ttsUiTick);
        ttsPendingPlay=false;
        ReaderTtsController.begin(this,ttsLocal,docUri,index);
        if(!ttsLocal){
            ttsBoundPlayer=bindService(new Intent(this,EdgePlaybackService.class),ttsPlayerConnection,BIND_AUTO_CREATE);
            ttsBoundStream=bindService(new Intent(this,EdgeStreamingService.class),ttsStreamConnection,BIND_AUTO_CREATE);
        }
        handler.post(ttsUiTick);
        updatePlayButton();updateProgress();
    }
    private void releaseTts(){
        handler.removeCallbacks(ttsUiTick);ttsPendingPlay=false;pendingTtsWord=-1;
        cachedTimingKey="";cachedTimingWords=java.util.Collections.emptyList();lastTtsError="";
        if(ttsLocal){
            ReaderTtsController.stopLocal(this);
        }else{
            if(ttsPlayer!=null){ttsPlayer.pause();ttsPlayer.removeListener(ttsPlayerListener);}
            if(ttsStreamer!=null){ttsStreamer.removeObserver(ttsStreamObserver);ttsStreamer.setActive(false);}
            if(ttsBoundStream){unbindService(ttsStreamConnection);ttsBoundStream=false;}
            if(ttsBoundPlayer){unbindService(ttsPlayerConnection);ttsBoundPlayer=false;}
            ttsStreamer=null;ttsPlayer=null;
        }
    }
    private boolean ttsCurrentlyPlaying(){
        if(ttsLocal){AudioPlaybackService s=AudioPlaybackService.instance;
            return s!=null&&s.isTtsMode()&&!s.isPaused();}
        return ttsPendingPlay||ttsPlayer!=null&&ttsPlayer.playRequested();
    }
    private void toggleTtsPlayback(){
        if(!ttsCurrentlyPlaying())returnToActiveWord();
        if(ttsLocal){ReaderTtsController.localPauseOrResume(this);
        }else if(ttsPlayer!=null){
            if(ttsPlayer.playRequested())ttsPlayer.pause();
            else {ttsPlayer.play();if(ttsStreamer!=null)ttsStreamer.ensurePlaySelection();}
        }else ttsPendingPlay=!ttsPendingPlay;
        updatePlayButton();
    }
    private void selectTtsWord(int at){
        // A tap selects the sentence; the book's UI block is never reloaded.
        index=map.clamp(at);
        SentenceChunks.Chunk sentence=SentenceChunks.forWord(ttsParts,index);
        if(sentence!=null)index=sentence.fromWord;
        pendingTtsWord=index;
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
                    if(!timingKey.equals(cachedTimingKey)){
                        cachedTimingWords=EdgeWordTimings.read(clip.file,clip.text);
                        cachedTimingKey=timingKey;
                    }
                    ticks=cachedTimingWords;
                }else ticks=ttsStreamer.liveWords(clip);
                int at=EdgeWordTimings.at(ticks,ttsPlayer.positionMs());
                if(at>=0)actual=map.atCharacter(ttsStreamer.charForWord(clip.start)+ticks.get(at).from);
            }
        }
        boolean active=ttsCurrentlyPlaying();
        if(pendingTtsWord>=0){
            SentenceChunks.Chunk target=SentenceChunks.forWord(ttsParts,pendingTtsWord);
            if(target!=null&&actual>=target.fromWord&&actual<target.toWord)pendingTtsWord=-1;
            else{
                if(actual>=0&&lastIgnoredEpoch!=navigationEpoch){
                    lastIgnoredEpoch=navigationEpoch;
                    ReaderDiagnosticLog.event("NAVIGATION","NAVIGATION_IGNORED_STALE epoch="+navigationEpoch+
                        " actual="+actual+" selected="+pendingTtsWord);
                }
                actual=-1; // stale callback from the previous streamed sentence
            }
        }
        if(active&&actual>=0&&actual<words.length&&actual!=index){
            int old=index;index=actual;refreshHighlight(old);updateProgress();
            // A manual drag owns the viewport until overlay, mode return, or Play.
            if(!followSuspended)ensureWordVisible(false);
        }
        if(followSuspended&&mode==TTS)updateFloatingCaption();
        updatePlayButton();
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
        if(chapters==null||chapters.size()==0)return map.starts.length;
        int startChar=map.starts.length>0?map.starts[map.clamp(fromWord)]:0;
        int current=0;
        for(int i=0;i<chapters.size();i++)if(chapters.get(i).startChar<=startChar)current=i;
        int endIdx=Math.min(chapters.size()-1,current+Math.max(1,count)-1);
        return map.atCharacter(chapters.get(endIdx).endChar);
    }
    private void chooseTtsSleep(){
        if(mode!=TTS)return;
        SleepTimerSheet.open(this,new SleepTimerSheet.Listener(){
            @Override public void minutes(int minutes){
                if(ttsLocal)startService(new Intent(ReaderActivity.this,AudioPlaybackService.class)
                    .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",minutes));
                else if(ttsPlayer!=null)ttsPlayer.setSleepMinutes(minutes);
            }
            @Override public void chapters(int count){
                int end=chapterEndAfter(index,count);
                if(ttsLocal)startService(new Intent(ReaderActivity.this,AudioPlaybackService.class)
                    .setAction(AudioPlaybackService.SLEEP).putExtra("minutes",-count).putExtra("end_word",end));
                else if(ttsPlayer!=null)ttsPlayer.setSleepChapterEnd(end);
            }
            @Override public long remainingMillis(int count){
                int end=chapterEndAfter(index,count);
                return Math.max(0,end-index)*60000L/Math.max(1,TtsWpm.get(ReaderActivity.this));
            }
        });
    }
    @Override protected void onResume(){super.onResume();
        if(readerWasPaused&&words.length>0) returnToActiveWord();
        readerWasPaused=false;
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
        if(root!=null&&words.length>0&&(oldBg!=bg||oldFg!=fg||oldSize!=size||oldFont!=font||oldMode!=mode||oldPortion!=portion
            ||oldCursorBg!=cursorBackground||oldCursorText!=cursorText||oldCursorScale!=cursorScale||oldNormalText!=normalSelectText
            ||oldNormalScale!=normalSelectScale||oldButton!=buttonColor||oldAccent!=accentButtonColor
            ||oldHighlightColor!=sentenceHighlightColor||oldHighlightOpacity!=sentenceHighlightOpacity||oldFocus!=focusColor||oldLineColor!=lineColor
            ||oldLinePos!=linePosition||oldPoint!=focusPoint||oldLines!=focusLinesEnabled))draw();

    }}
    @Override protected void onPause(){
        readerWasPaused=true;
        chteicoResumed=false;handler.removeCallbacks(chteicoReadTick);
        chteicoCompleteIfReachedEnd();save();stop();super.onPause();
    }
    @Override protected void onDestroy(){if(mode==TTS)releaseTts();stop();applySystemBars(false);super.onDestroy();}
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
