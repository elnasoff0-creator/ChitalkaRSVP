package ru.reader.rsvp;

import android.app.*;
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

/** Three modes, one global word index, lazily bound text chunks. */
public class ReaderActivity extends Activity {
    private static final int NORMAL=0,RSVP=1,CURSOR=2,NO_COLOR=0;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private LinearLayout root,speedPanel,controls,toolbar,toolbarWpm,modeRow,readingFrame;
    private FrameLayout sceneRoot,floatingPlayButton,rsvpPlayFrame;
    private TextView bookTitle;
    private ImageView floatingPlayIcon;
    private FrameLayout rsvpPane;
    private CountdownRingView countdownRing;
    private boolean waitingForStart=false;
    private boolean delayRsvpEnabled=false,delayCursorEnabled=false,fullscreenRsvpPositionManual=false;
    private int delayRsvpSeconds=2,delayCursorSeconds=2,fullscreenRsvpPositionPercent=0;
    private float normalRsvpCenterOnScreen=-1f;
    private RecyclerView textList;
    private LinearLayoutManager layoutManager;
    private ChunkAdapter chunkAdapter;
    private TextView wordView,progressView,speedView,modeButton;
    private ImageView playIcon,selectWordButton,modeIcon;
    private FocusLinesView focusLines;
    private ReadingWordMap map=ReadingWordMap.parse("");
    private BookChunkIndex chunks;
    private String body="",title="",author="",docUri="";
    private String[] words=new String[0];
    private int index=0,wpm=350,portion=1,size=36,bg=0xfff3e7d5,fg=0xff30221e,font=1,mode=RSVP;
    private int focusColor=0xffed3030,lineColor=0xff915439,linePosition=50;
    private int cursorBackground=0xff96583f,cursorText=NO_COLOR,cursorScale=100;
    private int normalSelectText=0xff915439,normalSelectScale=110,buttonColor=NO_COLOR,accentButtonColor=NO_COLOR;
    private int pausePunctuationPercent=60,pauseLongPercent=50,pauseSentencePercent=120,pauseParagraphPercent=150;
    private boolean focusPoint=true,focusLinesEnabled=true,playing=false,selecting=false,selectedWordActive=false;
    private boolean userScrolling=false,restoringScroll=false;
    private boolean fullscreenNormal=false,fullscreenRsvp=false,fullscreenCursor=false;
    private boolean normalChromeVisible=false,immersiveActive=false;
    private int originalSystemUiVisibility=0;
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
        if(mode!=RSVP||chunks.chunkForWord(previous)!=chunks.chunkForWord(index))refreshHighlight(previous);
        if(mode==CURSOR)ensureWordVisible(false);
        updateProgress();saveLight(false);
        handler.postDelayed(this,delayForPortion(index,Math.min(portion,words.length-index)));
    }};

    @Override public void onCreate(Bundle state){super.onCreate(state);
        originalSystemUiVisibility=getWindow().getDecorView().getSystemUiVisibility();
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
        draw();if(!docUri.isEmpty())load(Uri.parse(docUri),getIntent().getBooleanExtra("reset",false));
    }
    private void readPreferences(){
        wpm=prefs.getInt("wpm",350);portion=ReadingWordMap.portion(prefs.getInt("rsvp_portion",1));
        size=prefs.getInt("size",36);bg=prefs.getInt("bg",0xfff3e7d5);fg=prefs.getInt("fg",0xff30221e);font=prefs.getInt("font",1);
        mode=prefs.getInt("reading_mode",prefs.getBoolean("rsvp",true)?RSVP:NORMAL);mode=Math.max(0,Math.min(2,mode));
        focusPoint=prefs.getBoolean("rsvp_focus_point",true);focusLinesEnabled=prefs.getBoolean("rsvp_focus_lines",true);
        focusColor=prefs.getInt("rsvp_focus_color",0xffed3030);lineColor=prefs.getInt("rsvp_line_color",0xff915439);
        linePosition=Math.max(0,Math.min(100,prefs.getInt("rsvp_line_position",50)));
        cursorBackground=prefs.getInt("cursor_background",0xff96583f);cursorText=prefs.getInt("cursor_text",NO_COLOR);
        cursorScale=Math.max(100,Math.min(180,prefs.getInt("cursor_scale",100)));
        normalSelectText=prefs.getInt("normal_select_text",0xff915439);
        normalSelectScale=Math.max(100,Math.min(180,prefs.getInt("normal_select_scale",110)));
        buttonColor=prefs.getInt("button_color",NO_COLOR);accentButtonColor=prefs.getInt("button_accent_color",NO_COLOR);
        fullscreenNormal=prefs.getBoolean("reading_fullscreen_normal",false);
        fullscreenRsvp=prefs.getBoolean("reading_fullscreen_rsvp",false);
        fullscreenCursor=prefs.getBoolean("reading_fullscreen_cursor",false);
        delayRsvpEnabled=prefs.getBoolean("reading_start_delay_rsvp_enabled",false);
        delayCursorEnabled=prefs.getBoolean("reading_start_delay_cursor_enabled",false);
        delayRsvpSeconds=Math.max(1,Math.min(10,prefs.getInt("reading_start_delay_rsvp_seconds",2)));
        delayCursorSeconds=Math.max(1,Math.min(10,prefs.getInt("reading_start_delay_cursor_seconds",2)));
        fullscreenRsvpPositionManual=prefs.getBoolean("rsvp_fullscreen_position_manual",false);
        fullscreenRsvpPositionPercent=Math.max(-100,Math.min(100,prefs.getInt("rsvp_fullscreen_position",0)));
        pausePunctuationPercent=Math.max(0,Math.min(300,prefs.getInt("pausePunctuationPercent",60)));
        pauseLongPercent=Math.max(0,Math.min(300,prefs.getInt("pauseLongPercent",50)));
        pauseSentencePercent=Math.max(0,Math.min(400,prefs.getInt("pauseSentencePercent",120)));
        pauseParagraphPercent=Math.max(0,Math.min(400,prefs.getInt("pauseParagraphPercent",150)));
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private Typeface type(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
    private int panelColor(){return bg==0xff1e2025?0xff303238:0xfffff9f0;}
    private int buttonBg(){return buttonColor==NO_COLOR?panelColor():buttonColor;}
    private int accent(){return accentButtonColor==NO_COLOR?0xff915439:accentButtonColor;}
    private int contrast(int value){return Color.red(value)*0.299+Color.green(value)*0.587+Color.blue(value)*0.114<140?Color.WHITE:0xff30221e;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(fg);return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    private void add(LinearLayout parent,View child){parent.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private ImageButton icon(int id,View.OnClickListener click){
        ImageButton b=new ImageButton(this);b.setImageResource(id);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setPadding(dp(13),dp(13),dp(13),dp(13));b.setBackground(shape(buttonBg(),16));
        b.setColorFilter(contrast(buttonBg()));b.setOnClickListener(click);return b;
    }
    private int modeDrawable(){return mode==NORMAL?R.drawable.ic_book:mode==RSVP?R.drawable.ic_speed:R.drawable.ic_mode_cursor;}

    private void draw(){
        stop();
        sceneRoot=new FrameLayout(this);
        root=column();root.setPadding(dp(10),dp(20),dp(10),dp(7));root.setBackgroundColor(bg);
        sceneRoot.addView(root,new FrameLayout.LayoutParams(-1,-1));
        setContentView(sceneRoot);
        toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);add(root,toolbar);
        toolbar.addView(icon(R.drawable.ic_back,v->BackNavigation.back(this)),new LinearLayout.LayoutParams(dp(52),dp(53)));
        toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));
        toolbar.addView(icon(R.drawable.ic_bookmark,v->bookmark()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        toolbar.addView(icon(R.drawable.ic_microphone,v->audioMenu()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        LinearLayout speedGauge=new LinearLayout(this);speedGauge.setGravity(Gravity.CENTER);speedGauge.setOrientation(LinearLayout.VERTICAL);
        speedGauge.setBackground(shape(buttonBg(),14));speedGauge.setPadding(dp(3),dp(2),dp(3),dp(2));
        ImageView speedNeedle=new ImageView(this);speedNeedle.setImageResource(R.drawable.ic_audio_speed);
        speedNeedle.setColorFilter(contrast(buttonBg()));
        speedGauge.addView(speedNeedle,new LinearLayout.LayoutParams(dp(20),dp(20)));
        speedView=text(String.valueOf(wpm),13);speedView.setTextColor(contrast(buttonBg()));
        speedView.setGravity(Gravity.CENTER);speedView.setPadding(0,0,0,0);
        speedGauge.addView(speedView);
        speedGauge.setContentDescription("Скорость чтения в словах в минуту");
        speedGauge.setOnClickListener(v->TtsWpm.dialogForReader(this,wpm,value->{
            wpm=value;if(speedView!=null)speedView.setText(String.valueOf(wpm));saveLight(true);
        }));
        toolbarWpm=speedGauge;
        toolbar.addView(speedGauge,new LinearLayout.LayoutParams(dp(53),dp(53)));
        toolbar.addView(icon(R.drawable.ic_settings,v->settings()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        if(words.length==0){applySystemBars(false);
            TextView intro=text("EPUB · FB2 · HTML\n\nОткройте книгу, чтобы начать чтение.",20);
            intro.setPadding(dp(24),dp(24),dp(24),dp(24));add(root,intro);
            Button pick=new Button(this);pick.setText("Открыть книгу");pick.setOnClickListener(v->pick());add(root,pick);return;
        }
        bookTitle=text(title,19);bookTitle.setTypeface(Typeface.SERIF,Typeface.BOLD);
        bookTitle.setSingleLine(true);bookTitle.setEllipsize(TextUtils.TruncateAt.END);
        bookTitle.setPadding(dp(12),dp(12),dp(12),dp(12));add(root,bookTitle);
        modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);add(root,modeRow);
        progressView=text("",13);progressView.setGravity(Gravity.CENTER_VERTICAL);
        modeRow.addView(progressView,new LinearLayout.LayoutParams(0,dp(49),1));
        selectWordButton=new ImageView(this);selectWordButton.setImageResource(R.drawable.ic_select_word);
        selectWordButton.setPadding(dp(10),dp(10),dp(10),dp(10));selectWordButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        selectWordButton.setContentDescription("Выбрать стартовое слово");
        selectWordButton.setOnClickListener(v->{selecting=!selecting;if(selecting)normalChromeVisible=true;
            updateSelectWordButton();refreshHighlight(index);applyFullscreenUi();
            Toast.makeText(this,selecting?"Выбор слова включён":"Выбор слова выключен",Toast.LENGTH_SHORT).show();});
        modeRow.addView(selectWordButton,new LinearLayout.LayoutParams(dp(47),dp(47)));
        modeIcon=new ImageView(this);modeIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        modeIcon.setPadding(dp(10),dp(10),dp(10),dp(10));
        modeIcon.setBackground(shape(buttonBg(),14));modeIcon.setOnClickListener(v->modeDialog());
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(47),dp(47));iconLp.leftMargin=dp(5);modeRow.addView(modeIcon,iconLp);
        modeButton=text("",14);modeButton.setGravity(Gravity.CENTER);modeButton.setPadding(dp(12),0,dp(12),0);
        modeButton.setOnClickListener(v->modeDialog());
        LinearLayout.LayoutParams modeLp=new LinearLayout.LayoutParams(-2,dp(47));modeLp.leftMargin=dp(5);modeRow.addView(modeButton,modeLp);

        readingFrame=column();readingFrame.setBackground(shape(panelColor(),23));readingFrame.setPadding(dp(9),dp(10),dp(9),dp(10));
        LinearLayout.LayoutParams frameLp=new LinearLayout.LayoutParams(-1,0,1);frameLp.setMargins(0,dp(7),0,dp(8));root.addView(readingFrame,frameLp);
        rsvpPane=new FrameLayout(this);wordView=text("",size);wordView.setGravity(Gravity.CENTER);
        rsvpPane.addView(wordView,new FrameLayout.LayoutParams(-1,-1));
        focusLines=new FocusLinesView(this);focusLines.setClickable(false);
        rsvpPane.addView(focusLines,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams rsvpLp=new LinearLayout.LayoutParams(-1,dp(192));rsvpLp.bottomMargin=dp(8);readingFrame.addView(rsvpPane,rsvpLp);
        wordView.setOnTouchListener((v,e)->{
            if(mode!=RSVP)return false;
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
        textList.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrollStateChanged(RecyclerView rv,int state){
                if(state==RecyclerView.SCROLL_STATE_DRAGGING){
                    chteicoTouch();userScrolling=true;
                    if(mode==RSVP){stop();scrollWordAccumulator=0;}
                }
                if(state==RecyclerView.SCROLL_STATE_IDLE){
                    userScrolling=false;
                    if(mode==RSVP){scrollWordAccumulator=0;setRsvpContextVisible(!playing);if(!playing)ensureWordVisible(false);}
                    saveLight(true);
                }
            }
            @Override public void onScrolled(RecyclerView rv,int dx,int dy){
                if(userScrolling&&dy!=0)chteicoTouch();
                if(!userScrolling||restoringScroll||words.length==0)return;
                if(mode!=RSVP)return;
                stepRsvpByScroll(dy);
            }
        });
        controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setBackgroundColor(Color.TRANSPARENT);add(root,controls);
        rsvpPlayFrame=new FrameLayout(this);rsvpPlayFrame.setBackground(shape(accent(),20));
        playIcon=new ImageView(this);playIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);playIcon.setColorFilter(contrast(accent()));
        rsvpPlayFrame.addView(playIcon,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));
        rsvpPlayFrame.setOnClickListener(v->togglePlayback());
        controls.addView(rsvpPlayFrame,new LinearLayout.LayoutParams(dp(72),dp(72)));

        // A separate overlay keeps only Pause visible in immersive playback.
        floatingPlayButton=new FrameLayout(this);floatingPlayButton.setBackground(shape(accent(),20));
        floatingPlayIcon=new ImageView(this);floatingPlayIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        floatingPlayIcon.setColorFilter(contrast(accent()));
        floatingPlayButton.addView(floatingPlayIcon,new FrameLayout.LayoutParams(dp(29),dp(29),Gravity.CENTER));
        floatingPlayButton.setOnClickListener(v->togglePlayback());
        FrameLayout.LayoutParams floatLp=new FrameLayout.LayoutParams(dp(68),dp(68),Gravity.RIGHT|Gravity.BOTTOM);
        floatLp.setMargins(dp(12),dp(12),dp(20),dp(24));
        sceneRoot.addView(floatingPlayButton,floatLp);floatingPlayButton.setVisibility(View.GONE);
        // WPM is displayed in the top toolbar for both Cursor and RSVP.
        speedPanel=null;
        updateModeUI();updatePlayButton();
        // Jump to the indexed chunk, not to the pixel position of an enormous TextView.
        textList.post(()->ensureWordVisible(true));
    }
    private class ChunkAdapter extends RecyclerView.Adapter<ChunkAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final TextView value;
            Spannable content;
            final ArrayList<Object> applied=new ArrayList<>();
            Holder(TextView t){super(t);value=t;}
            void highlight(int chunkId){
                if(content==null)return;
                for(Object span:applied)content.removeSpan(span);applied.clear();
                if(words.length==0)return;
                BookChunkIndex.Chunk c=chunks.get(chunkId);
                int first=map.clamp(index);
                if(first<c.startWord||first>=c.endWord)return;
                int endWord=Math.min(c.endWord-1,Math.min(words.length-1,first+portion-1));
                int from=map.starts[first]-c.startChar,to=map.ends[endWord]-c.startChar;
                if(from<0||to>content.length()||to<=from)return;
                if(mode==CURSOR){
                    if(cursorBackground!=NO_COLOR)span(new BackgroundColorSpan(cursorBackground),from,to);
                    if(cursorText!=NO_COLOR)span(new ForegroundColorSpan(cursorText),from,to);
                    if(cursorScale>100)span(new RelativeSizeSpan(cursorScale/100f),from,to);
                }else if(mode==RSVP){
                    // Paused RSVP context uses the same typography as ordinary word selection.
                    int selectedEnd=map.ends[first]-c.startChar;
                    if(normalSelectText!=NO_COLOR)span(new ForegroundColorSpan(normalSelectText),from,selectedEnd);
                    if(normalSelectScale>100)span(new RelativeSizeSpan(normalSelectScale/100f),from,selectedEnd);
                }else if(mode==NORMAL&&(selecting||selectedWordActive)){
                    if(normalSelectText!=NO_COLOR)span(new ForegroundColorSpan(normalSelectText),from,to);
                    if(normalSelectScale>100)span(new RelativeSizeSpan(normalSelectScale/100f),from,to);
                }
            }
            void span(Object obj,int first,int last){content.setSpan(obj,first,last,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);applied.add(obj);}
        }
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int type){
            TextView item=text("",Math.max(12,(int)(size*.60f)));item.setTypeface(type());
            item.setLineSpacing(dp(Math.max(8,size/3)),1.12f);
            item.setPadding(dp(14),dp(7),dp(14),dp(7));
            item.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            Holder holder=new Holder(item);
            // Consume DOWN in selection mode so Android delivers UP for word tapping;
            // the parent RecyclerView can still intercept a scrolling drag.
            item.setClickable(true);
            item.setOnTouchListener((view,event)->{
                boolean selectingWord=(mode==CURSOR)||(mode==NORMAL&&selecting);
                boolean togglingChrome=(mode==NORMAL&&fullscreenNormal&&!selecting);
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
                int row=holder.getBindingAdapterPosition();if(row==RecyclerView.NO_POSITION)return true;
                android.text.Layout l=item.getLayout();if(l==null)return true;
                int y=(int)event.getY()-item.getTotalPaddingTop();
                int line=l.getLineForVertical(Math.max(0,y));
                int offset=l.getOffsetForHorizontal(line,Math.max(0,event.getX()-item.getTotalPaddingLeft()));
                int old=index;index=map.atCharacter(chunks.get(row).startChar+offset);
                selectedWordActive=true;refreshHighlight(old);updateProgress();saveLight(true);
                return true;
            });
            return holder;
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            BookChunkIndex.Chunk c=chunks.get(position);
            String slice=body.substring(c.startChar,c.endChar);
            holder.value.setText(slice,TextView.BufferType.SPANNABLE);
            holder.content=(Spannable)holder.value.getText();holder.applied.clear();holder.highlight(position);
        }
        @Override public int getItemCount(){return chunks==null?0:chunks.size();}
        void updateVisible(int chunkId){
            if(chunkId<0||chunkId>=getItemCount())return;
            RecyclerView.ViewHolder row=textList.findViewHolderForAdapterPosition(chunkId);
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
        rsvpPane.setVisibility(mode==RSVP?View.VISIBLE:View.GONE);
        if(focusLines!=null){focusLines.setVisibility(mode==RSVP&&focusLinesEnabled?View.VISIBLE:View.GONE);focusLines.invalidate();}
        if(modeButton!=null){modeButton.setText(mode==NORMAL?"Обычный ▾":mode==RSVP?"Скорочтение ▾":"Курсор ▾");
            modeButton.setBackground(shape(accent(),14));modeButton.setTextColor(contrast(accent()));}
        if(modeIcon!=null){modeIcon.setImageResource(modeDrawable());modeIcon.setColorFilter(contrast(buttonBg()));}
        if(selectWordButton!=null){selectWordButton.setVisibility(mode==NORMAL?View.VISIBLE:View.GONE);updateSelectWordButton();}
        if(controls!=null)controls.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(speedPanel!=null)speedPanel.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(toolbarWpm!=null)toolbarWpm.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(textList!=null)textList.setVisibility(mode==RSVP&&playing?View.GONE:View.VISIBLE);
        if(mode==RSVP)updateWord();
        refreshHighlight(index);updateProgress();applyFullscreenUi();
    }
    /** Immersive mode is a view-state change: do not rebuild the book or text index. */
    private void applyFullscreenUi(){
        if(root==null||words.length==0||readingFrame==null)return;
        boolean readingFullscreen=mode==NORMAL&&fullscreenNormal&&!normalChromeVisible&&!selecting;
        boolean playbackFullscreen=playing&&((mode==RSVP&&fullscreenRsvp)||(mode==CURSOR&&fullscreenCursor));
        boolean immersive=readingFullscreen||playbackFullscreen;

        if(toolbar!=null)toolbar.setVisibility(immersive?View.GONE:View.VISIBLE);
        if(bookTitle!=null)bookTitle.setVisibility(immersive?View.GONE:View.VISIBLE);
        if(modeRow!=null)modeRow.setVisibility(immersive?View.GONE:View.VISIBLE);
        if(speedPanel!=null)speedPanel.setVisibility(immersive?View.GONE:(mode==NORMAL?View.GONE:View.VISIBLE));
        if(controls!=null)controls.setVisibility(immersive?View.GONE:(mode==NORMAL?View.GONE:View.VISIBLE));
        if(floatingPlayButton!=null)floatingPlayButton.setVisibility(playbackFullscreen?View.VISIBLE:View.GONE);
        root.setPadding(immersive?0:dp(10),immersive?0:dp(20),immersive?0:dp(10),immersive?0:dp(7));
        readingFrame.setPadding(immersive?0:dp(9),immersive?0:dp(10),immersive?0:dp(9),immersive?0:dp(10));
        readingFrame.setBackground(immersive?shape(bg,0):shape(panelColor(),23));
        LinearLayout.LayoutParams frameLp=(LinearLayout.LayoutParams)readingFrame.getLayoutParams();
        if(frameLp!=null){
            int gap=immersive?0:dp(7);
            int bottom=immersive?0:dp(8);
            if(frameLp.topMargin!=gap||frameLp.bottomMargin!=bottom){
                frameLp.setMargins(0,gap,0,bottom);readingFrame.setLayoutParams(frameLp);
            }
        }
        if(rsvpPane!=null){
            LinearLayout.LayoutParams paneLp=(LinearLayout.LayoutParams)rsvpPane.getLayoutParams();
            if(paneLp!=null){
                boolean rsvpFullscreen=mode==RSVP&&playbackFullscreen;
                // Fixed-height pane: a weighted fullscreen pane used to recenter the word on Play.
                int height=rsvpFullscreen?dp(128):dp(192);
                int bottom=rsvpFullscreen?0:dp(8);
                int top=rsvpFullscreen?paneLp.topMargin:0;
                if(paneLp.height!=height||paneLp.weight!=0f||paneLp.bottomMargin!=bottom||paneLp.topMargin!=top){
                    paneLp.height=height;paneLp.weight=0f;
                    paneLp.bottomMargin=bottom;paneLp.topMargin=top;
                    rsvpPane.setLayoutParams(paneLp);
                }
                if(rsvpFullscreen)readingFrame.post(this::positionFullscreenRsvpPane);
            }
        }
        if(mode==RSVP&&playing&&textList!=null)textList.setVisibility(View.GONE);
        applySystemBars(immersive);
    }
    private void rememberRsvpScreenPosition(){
        if(rsvpPane==null||rsvpPane.getHeight()<=0)return;
        int[] location=new int[2];rsvpPane.getLocationOnScreen(location);
        normalRsvpCenterOnScreen=location[1]+rsvpPane.getHeight()/2f;
        android.util.DisplayMetrics real=new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(real);
        float margin=dp(16)+dp(64);
        float upper=margin,lower=Math.max(upper+1,real.heightPixels-margin);
        float ratio=Math.max(0f,Math.min(1f,(normalRsvpCenterOnScreen-upper)/(lower-upper)));
        int approxPercent=Math.round(ratio*200f-100f);
        prefs.edit().putInt("rsvp_fullscreen_auto_percent",approxPercent).apply();
    }
    private void positionFullscreenRsvpPane(){
        if(!playing||mode!=RSVP||!fullscreenRsvp||rsvpPane==null||readingFrame==null)return;
        if(readingFrame.getHeight()<=0||rsvpPane.getHeight()<=0)return;
        int[] parentLocation=new int[2];readingFrame.getLocationOnScreen(parentLocation);
        float paneHalf=rsvpPane.getHeight()/2f;
        float edge=dp(16)+paneHalf;
        float upper=parentLocation[1]+edge;
        float lower=parentLocation[1]+readingFrame.getHeight()-edge;
        if(lower<upper)lower=upper;
        float target;
        if(fullscreenRsvpPositionManual){
            target=upper+(lower-upper)*(fullscreenRsvpPositionPercent+100f)/200f;
        }else if(normalRsvpCenterOnScreen>0f){
            target=normalRsvpCenterOnScreen;
        }else{
            target=(upper+lower)/2f;
        }
        target=Math.max(upper,Math.min(lower,target));
        int margin=Math.max(0,Math.round(target-parentLocation[1]-paneHalf));
        LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)rsvpPane.getLayoutParams();
        if(params.topMargin!=margin){params.topMargin=margin;rsvpPane.setLayoutParams(params);}
    }
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
            // The ring is behind the word and lines, so it never covers the text.
            int focusHeight=focusLinesEnabled?dp(90):0;
            int wordHeight=wordView==null?dp(size):Math.max(Math.round(wordView.getTextSize()*1.25f),dp(size));
            int diameter=Math.max(dp(65),Math.max(focusHeight,wordHeight)+dp(30));
            rsvpPane.addView(countdownRing,0,new FrameLayout.LayoutParams(diameter,diameter,Gravity.CENTER));
        }else{
            // Cursor: centered on the device view, irrespective of selected word position.
            sceneRoot.addView(countdownRing,new FrameLayout.LayoutParams(dp(124),dp(124),Gravity.CENTER));
        }
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
        immersiveActive=immersive;
        View decor=getWindow().getDecorView();
        int flags=immersive?
            (originalSystemUiVisibility|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION):originalSystemUiVisibility;
        if(decor.getSystemUiVisibility()!=flags)decor.setSystemUiVisibility(flags);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus&&immersiveActive)applySystemBars(true);
    }
    private void setRsvpContextVisible(boolean visible){
        if(textList==null||mode!=RSVP)return;
        if(textList.getVisibility()==(visible?View.VISIBLE:View.GONE))return;
        textList.setVisibility(visible?View.VISIBLE:View.GONE);
        if(visible)textList.post(()->ensureWordVisible(true));
    }
    private void stepRsvpByScroll(int dy){
        if(words.length==0||dy==0)return;
        int threshold=Math.max(6,dp(8));
        scrollWordAccumulator+=dy;
        int old=index;
        while(scrollWordAccumulator>=threshold&&index<words.length-1){
            index++;
            scrollWordAccumulator-=threshold;
        }
        while(scrollWordAccumulator<=-threshold&&index>0){
            index--;
            scrollWordAccumulator+=threshold;
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
    private void updateProgress(){if(progressView!=null&&words.length>0)progressView.setText((index+1)+" / "+words.length+" слов · "+((index+1)*100/words.length)+"%");}
    private void updatePlayButton(){
        if(playIcon!=null)playIcon.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play);
        if(floatingPlayIcon!=null)floatingPlayIcon.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play);
    }
    private void togglePlayback(){chteicoTouch();
        if(playing){stop();saveLight(true);return;}
        if(words.length==0)return;
        if(mode==RSVP&&fullscreenRsvp)rememberRsvpScreenPosition();
        playing=true;waitingForStart=false;
        handler.removeCallbacks(ticker);handler.removeCallbacks(beginDelayedReading);
        if(mode==RSVP)setRsvpContextVisible(false);
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
        applyFullscreenUi();
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
    private void ensureWordVisible(boolean force){
        if(textList==null||textList.getVisibility()!=View.VISIBLE||layoutManager==null||chunks==null||words.length==0)return;
        int chunk=chunks.chunkForWord(index);
        int start=layoutManager.findFirstVisibleItemPosition(),end=layoutManager.findLastVisibleItemPosition();
        if(force||chunk<start||chunk>end){
            restoringScroll=true;
            layoutManager.scrollToPositionWithOffset(chunk,dp(14));
            // Scroll to the selected word inside the chunk, not merely its first line.
            textList.post(()->{
                if(textList==null||textList.getVisibility()!=View.VISIBLE)return;
                RecyclerView.ViewHolder holder=textList.findViewHolderForAdapterPosition(chunk);
                if(holder instanceof ChunkAdapter.Holder){
                    TextView value=((ChunkAdapter.Holder)holder).value;
                    android.text.Layout lay=value.getLayout();
                    if(lay!=null){
                        int local=Math.max(0,map.starts[map.clamp(index)]-chunks.get(chunk).startChar);
                        int line=lay.getLineForOffset(Math.min(local,lay.getText().length()));
                        int selectedY=holder.itemView.getTop()+value.getTotalPaddingTop()+lay.getLineTop(line);
                        textList.scrollBy(0,selectedY-Math.max(dp(18),textList.getHeight()/3));
                    }
                }
                handler.postDelayed(()->restoringScroll=false,120);
            });
            return;
        }
        RecyclerView.ViewHolder existing=textList.findViewHolderForAdapterPosition(chunk);
        if(!(existing instanceof ChunkAdapter.Holder))return;
        ChunkAdapter.Holder h=(ChunkAdapter.Holder)existing;
        android.text.Layout l=h.value.getLayout();if(l==null)return;
        int offset=map.starts[map.clamp(index)]-chunks.get(chunk).startChar;
        int line=l.getLineForOffset(Math.max(0,offset));
        int y=h.itemView.getTop()+h.value.getTotalPaddingTop()+l.getLineTop(line);
        int bottom=h.itemView.getTop()+h.value.getTotalPaddingTop()+l.getLineBottom(line);
        if(y<dp(28)||bottom>textList.getHeight()-dp(28)){
            int delta=y<dp(28)?y-dp(64):bottom-textList.getHeight()+dp(65);
            restoringScroll=true;textList.scrollBy(0,delta);
            handler.postDelayed(()->restoringScroll=false,120);
        }
    }
    private void modeDialog(){
        new SwipeDialogs.Builder(this).setTitle("Режим чтения")
            .setSingleChoiceItems(new String[]{"Обычный","Скорочтение","Курсор"},mode,(dialog,n)->{setMode(n);dialog.dismiss();})
            .setNegativeButton("Отмена",null).show();
    }
    private void setMode(int next){if(next<0||next>2||next==mode)return;
        chteicoTouch();stop();int old=index;mode=next;selecting=false;normalChromeVisible=false;
        updateModeUI();refreshHighlight(old);ensureWordVisible(true);saveLight(true);
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
            ReaderBookCache.CachedBook cached=ReaderBookCache.load(this,uri);
            BookLoader.Book book=null;ReadingWordMap prepared;
            if(cached!=null&&cached.map.size()>0){prepared=cached.map;}
            else{
                book=BookLoader.load(this,uri);
                if(book.text.isEmpty())throw new Exception("Нет текста");
                prepared=ReadingWordMap.parse(book.text);
                if(prepared.size()==0)throw new Exception("Не найдены слова");
                ReaderBookCache.store(this,uri,book.title,book.author,book.text,prepared);
            }
            final String loadedText=cached!=null?cached.text:book.text;
            final String loadedTitle=cached!=null?cached.title:book.title;
            final String loadedAuthor=cached!=null?cached.author:book.author;
            final BookChunkIndex indexed=new BookChunkIndex(loadedText,prepared,560);
            runOnUiThread(()->{
                stop();title=loadedTitle;author=loadedAuthor==null?"":loadedAuthor;body=loadedText;
                map=prepared;words=prepared.words;chunks=indexed;docUri=uri.toString();
                if(reset)index=0;else index=map.clamp(index);
                chteicoInitialIndex=index;chteicoLastWordIndex=index;chteicoReadingSeconds=0;chteicoTouch();
                ChteicoStore.openBook(this,docUri,mode);
                draw();saveLight(true);
            });
        }catch(Exception ex){runOnUiThread(()->new SwipeDialogs.Builder(this).setMessage("Не удалось открыть книгу: "+ex.getMessage()).setPositiveButton("OK",null).show());}
    }).start();}
    private void settings(){stop();startActivity(new Intent(this,SettingsActivity.class).putExtra("rsvp_tab",true));}
    private void audioMenu(){
        if(docUri.isEmpty()){Toast.makeText(this,"Сначала откройте книгу",Toast.LENGTH_SHORT).show();return;}
        openAudioEngine(prefs.getBoolean("tts_last_local",true));
    }
    private void openAudioEngine(boolean androidTts){
        stop();save();
        Intent intent=new Intent(this,EdgeOnlineActivity.class);
        intent.putExtra("book_uri",docUri);intent.putExtra("word_index",index);
        intent.putExtra("tts_mode_local",androidTts);
        startActivity(intent);
    }
    @Override protected void onResume(){super.onResume();
        chteicoResumed=true;chteicoTouch();handler.removeCallbacks(chteicoReadTick);handler.postDelayed(chteicoReadTick,10000);
        if(prefs!=null){
        int oldBg=bg,oldFg=fg,oldSize=size,oldFont=font,oldMode=mode,oldPortion=portion;
        int oldCursorBg=cursorBackground,oldCursorText=cursorText,oldCursorScale=cursorScale;
        int oldNormalText=normalSelectText,oldNormalScale=normalSelectScale,oldButton=buttonColor,oldAccent=accentButtonColor;
        int oldFocus=focusColor,oldLineColor=lineColor,oldLinePos=linePosition;
        boolean oldPoint=focusPoint,oldLines=focusLinesEnabled;
        boolean oldFullNormal=fullscreenNormal,oldFullRsvp=fullscreenRsvp,oldFullCursor=fullscreenCursor;
        readPreferences();
        if(root!=null&&words.length>0&&(oldBg!=bg||oldFg!=fg||oldSize!=size||oldFont!=font||oldMode!=mode||oldPortion!=portion
            ||oldCursorBg!=cursorBackground||oldCursorText!=cursorText||oldCursorScale!=cursorScale||oldNormalText!=normalSelectText
            ||oldNormalScale!=normalSelectScale||oldButton!=buttonColor||oldAccent!=accentButtonColor||oldFocus!=focusColor||oldLineColor!=lineColor
            ||oldLinePos!=linePosition||oldPoint!=focusPoint||oldLines!=focusLinesEnabled))draw();
        else if(oldFullNormal!=fullscreenNormal||oldFullRsvp!=fullscreenRsvp||oldFullCursor!=fullscreenCursor){
            if(mode==NORMAL&&fullscreenNormal&&!oldFullNormal)normalChromeVisible=false;
            applyFullscreenUi();
        }
    }}
    @Override protected void onPause(){
        chteicoResumed=false;handler.removeCallbacks(chteicoReadTick);
        chteicoCompleteIfReachedEnd();save();stop();super.onPause();
    }
    @Override protected void onDestroy(){stop();applySystemBars(false);super.onDestroy();}
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
