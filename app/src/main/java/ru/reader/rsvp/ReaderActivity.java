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
    private LinearLayout root,speedPanel,controls;
    private FrameLayout rsvpPane;
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
    private int normalSelectText=0xff915439,normalSelectScale=110,buttonColor=NO_COLOR;
    private int pausePunctuationPercent=60,pauseLongPercent=50,pauseSentencePercent=120,pauseParagraphPercent=150;
    private boolean focusPoint=true,focusLinesEnabled=true,playing=false,selecting=false,selectedWordActive=false;
    private boolean userScrolling=false,restoringScroll=false;
    private int swipeStartIndex=0,lastHighlightedChunk=-1;
    private float swipeStartY;
    private long lastLightSave=0;
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(!playing||words.length==0)return;
        int count=Math.min(portion,words.length-index);
        if(index+count>=words.length){stop();save();return;}
        int previous=index;
        index+=count;
        if(mode==RSVP)updateWord();
        if(mode!=RSVP||chunks.chunkForWord(previous)!=chunks.chunkForWord(index))refreshHighlight(previous);
        if(mode==CURSOR)ensureWordVisible(false);
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
        buttonColor=prefs.getInt("button_color",NO_COLOR);
        pausePunctuationPercent=Math.max(0,Math.min(300,prefs.getInt("pausePunctuationPercent",60)));
        pauseLongPercent=Math.max(0,Math.min(300,prefs.getInt("pauseLongPercent",50)));
        pauseSentencePercent=Math.max(0,Math.min(400,prefs.getInt("pauseSentencePercent",120)));
        pauseParagraphPercent=Math.max(0,Math.min(400,prefs.getInt("pauseParagraphPercent",150)));
    }
    private int dp(int value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
    private Typeface type(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
    private int panelColor(){return bg==0xff1e2025?0xff303238:0xfffff9f0;}
    private int buttonBg(){return buttonColor==NO_COLOR?panelColor():buttonColor;}
    private int accent(){return buttonColor==NO_COLOR?0xff915439:buttonColor;}
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
        stop();root=column();root.setPadding(dp(10),dp(20),dp(10),dp(7));root.setBackgroundColor(bg);setContentView(root);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);add(root,toolbar);
        toolbar.addView(icon(R.drawable.ic_back,v->BackNavigation.back(this)),new LinearLayout.LayoutParams(dp(52),dp(53)));
        TextView heading=text("Читалка",23);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);heading.setSingleLine();
        toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(icon(R.drawable.ic_bookmark,v->bookmark()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        toolbar.addView(icon(R.drawable.ic_settings,v->settings()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        toolbar.addView(icon(R.drawable.ic_microphone,v->audioMenu()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        if(words.length==0){
            TextView intro=text("EPUB · FB2 · HTML\n\nОткройте книгу, чтобы начать чтение.",20);
            intro.setPadding(dp(24),dp(24),dp(24),dp(24));add(root,intro);
            Button pick=new Button(this);pick.setText("Открыть книгу");pick.setOnClickListener(v->pick());add(root,pick);return;
        }
        TextView bookTitle=text(title,19);bookTitle.setTypeface(Typeface.SERIF,Typeface.BOLD);
        bookTitle.setSingleLine(true);bookTitle.setEllipsize(TextUtils.TruncateAt.END);
        bookTitle.setPadding(dp(12),dp(12),dp(12),dp(12));add(root,bookTitle);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);add(root,top);
        progressView=text("",13);progressView.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(progressView,new LinearLayout.LayoutParams(0,dp(49),1));
        selectWordButton=new ImageView(this);selectWordButton.setImageResource(R.drawable.ic_select_word);
        selectWordButton.setPadding(dp(10),dp(10),dp(10),dp(10));selectWordButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        selectWordButton.setContentDescription("Выбрать стартовое слово");
        selectWordButton.setOnClickListener(v->{selecting=!selecting;updateSelectWordButton();refreshHighlight(index);
            Toast.makeText(this,selecting?"Выбор слова включён":"Выбор слова выключен",Toast.LENGTH_SHORT).show();});
        top.addView(selectWordButton,new LinearLayout.LayoutParams(dp(47),dp(47)));
        modeIcon=new ImageView(this);modeIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        modeIcon.setPadding(dp(10),dp(10),dp(10),dp(10));
        modeIcon.setBackground(shape(buttonBg(),14));modeIcon.setOnClickListener(v->modeDialog());
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(47),dp(47));iconLp.leftMargin=dp(5);top.addView(modeIcon,iconLp);
        modeButton=text("",14);modeButton.setGravity(Gravity.CENTER);modeButton.setPadding(dp(12),0,dp(12),0);
        modeButton.setOnClickListener(v->modeDialog());
        LinearLayout.LayoutParams modeLp=new LinearLayout.LayoutParams(-2,dp(47));modeLp.leftMargin=dp(5);top.addView(modeButton,modeLp);

        LinearLayout frame=column();frame.setBackground(shape(panelColor(),23));frame.setPadding(dp(9),dp(10),dp(9),dp(10));
        LinearLayout.LayoutParams frameLp=new LinearLayout.LayoutParams(-1,0,1);frameLp.setMargins(0,dp(7),0,dp(8));root.addView(frame,frameLp);
        rsvpPane=new FrameLayout(this);wordView=text("",size);wordView.setGravity(Gravity.CENTER);
        rsvpPane.addView(wordView,new FrameLayout.LayoutParams(-1,-1));
        focusLines=new FocusLinesView(this);focusLines.setClickable(false);
        rsvpPane.addView(focusLines,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams rsvpLp=new LinearLayout.LayoutParams(-1,dp(192));rsvpLp.bottomMargin=dp(8);frame.addView(rsvpPane,rsvpLp);
        wordView.setOnTouchListener((v,e)->{
            if(mode!=RSVP)return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){stop();swipeStartY=e.getY();swipeStartIndex=index;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){index=map.clamp(swipeStartIndex+Math.round((swipeStartY-e.getY())/dp(25)));
                updateWord();refreshHighlight(index);return true;}
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){saveLight(true);return true;}
            return true;
        });
        layoutManager=new LinearLayoutManager(this);
        textList=new RecyclerView(this);textList.setLayoutManager(layoutManager);
        textList.setItemAnimator(null);textList.setHasFixedSize(false);
        textList.setBackgroundColor(Color.TRANSPARENT);
        chunkAdapter=new ChunkAdapter();textList.setAdapter(chunkAdapter);
        frame.addView(textList,new LinearLayout.LayoutParams(-1,0,1));
        textList.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrollStateChanged(RecyclerView rv,int state){
                if(state==RecyclerView.SCROLL_STATE_DRAGGING)userScrolling=true;
                if(state==RecyclerView.SCROLL_STATE_IDLE){userScrolling=false;saveLight(true);}
            }
            @Override public void onScrolled(RecyclerView rv,int dx,int dy){
                if(!userScrolling||restoringScroll||words.length==0)return;
                int first=layoutManager.findFirstVisibleItemPosition();if(first<0||first>=chunks.size())return;
                RecyclerView.ViewHolder found=textList.findViewHolderForAdapterPosition(first);
                if(!(found instanceof ChunkAdapter.Holder))return;
                ChunkAdapter.Holder holder=(ChunkAdapter.Holder)found;
                android.text.Layout l=holder.value.getLayout();if(l==null)return;
                int offsetY=Math.max(0,(-holder.itemView.getTop())+dp(8));
                int line=l.getLineForVertical(offsetY);
                int local=l.getLineStart(line);
                int next=map.atCharacter(chunks.get(first).startChar+local);
                if(next!=index){int old=index;index=next;refreshHighlight(old);if(mode==RSVP)updateWord();updateProgress();saveLight(false);}
            }
        });
        controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setBackground(shape(panelColor(),22));add(root,controls);
        FrameLayout playFrame=new FrameLayout(this);playFrame.setBackground(shape(accent(),20));
        playIcon=new ImageView(this);playIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);playIcon.setColorFilter(contrast(accent()));
        playFrame.addView(playIcon,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));
        playFrame.setOnClickListener(v->{if(playing)stop();else{playing=true;handler.removeCallbacks(ticker);
            handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));}updatePlayButton();});
        controls.addView(playFrame,new LinearLayout.LayoutParams(dp(72),dp(72)));
        speedPanel=column();speedPanel.setBackground(shape(panelColor(),18));speedPanel.setPadding(dp(8),dp(8),dp(8),dp(8));add(root,speedPanel);
        LinearLayout speedRow=new LinearLayout(this);speedRow.setGravity(Gravity.CENTER_VERTICAL);add(speedPanel,speedRow);
        ImageView gauge=new ImageView(this);gauge.setImageResource(R.drawable.ic_speed);gauge.setColorFilter(fg);
        speedRow.addView(gauge,new LinearLayout.LayoutParams(dp(28),dp(28)));
        speedView=text(wpm+" слов/мин",17);speedView.setGravity(Gravity.CENTER);
        speedRow.addView(speedView,new LinearLayout.LayoutParams(0,dp(48),1));
        Button minus=new Button(this);minus.setText("−10");minus.setBackground(shape(buttonBg(),14));
        minus.setTextColor(contrast(buttonBg()));minus.setOnClickListener(v->changeSpeed(-10));
        speedRow.addView(minus,new LinearLayout.LayoutParams(dp(52),dp(52)));
        Button plus=new Button(this);plus.setText("+10");plus.setBackground(shape(buttonBg(),14));
        plus.setTextColor(contrast(buttonBg()));plus.setOnClickListener(v->changeSpeed(10));
        speedRow.addView(plus,new LinearLayout.LayoutParams(dp(52),dp(52)));
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
                    // Context is static until the active word actually changes chunk.
                    span(new BackgroundColorSpan(0x336f746a),from,to);
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
                if(mode!=NORMAL||!selecting)return false;
                if(event.getAction()==MotionEvent.ACTION_DOWN)return true;
                if(event.getAction()!=MotionEvent.ACTION_UP)return false;
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
        if(mode==RSVP)updateWord();
        refreshHighlight(index);updateProgress();
    }
    private void updateSelectWordButton(){
        if(selectWordButton==null)return;
        int background=selecting?accent():buttonBg();
        selectWordButton.setBackground(shape(background,14));
        selectWordButton.setColorFilter(contrast(background));
        selectWordButton.setAlpha(selecting?1f:.9f);
    }
    private void updateProgress(){if(progressView!=null&&words.length>0)progressView.setText((index+1)+" / "+words.length+" слов · "+((index+1)*100/words.length)+"%");}
    private void updatePlayButton(){if(playIcon!=null)playIcon.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play);}
    private void stop(){playing=false;handler.removeCallbacks(ticker);updatePlayButton();}
    private void changeSpeed(int delta){wpm=Math.max(100,Math.min(1000,wpm+delta));if(speedView!=null)speedView.setText(wpm+" слов/мин");saveLight(true);
        if(playing){handler.removeCallbacks(ticker);handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));}}
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
        if(textList==null||layoutManager==null||chunks==null||words.length==0)return;
        int chunk=chunks.chunkForWord(index);
        int start=layoutManager.findFirstVisibleItemPosition(),end=layoutManager.findLastVisibleItemPosition();
        if(force||chunk<start||chunk>end){
            restoringScroll=true;layoutManager.scrollToPositionWithOffset(chunk,dp(14));
            handler.postDelayed(()->restoringScroll=false,130);return;
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
            restoringScroll=true;textList.smoothScrollBy(0,delta);
            handler.postDelayed(()->restoringScroll=false,230);
        }
    }
    private void modeDialog(){
        new AlertDialog.Builder(this).setTitle("Режим чтения")
            .setSingleChoiceItems(new String[]{"Обычный","Скорочтение","Курсор"},mode,(dialog,n)->{setMode(n);dialog.dismiss();})
            .setNegativeButton("Отмена",null).show();
    }
    private void setMode(int next){if(next<0||next>2||next==mode)return;
        stop();int old=index;mode=next;selecting=false;updateModeUI();refreshHighlight(old);ensureWordVisible(true);saveLight(true);
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
                draw();saveLight(true);
            });
        }catch(Exception ex){runOnUiThread(()->new AlertDialog.Builder(this).setMessage("Не удалось открыть книгу: "+ex.getMessage()).setPositiveButton("OK",null).show());}
    }).start();}
    private void settings(){stop();startActivity(new Intent(this,SettingsActivity.class).putExtra("rsvp_tab",true));}
    private void audioMenu(){
        if(docUri.isEmpty()){Toast.makeText(this,"Сначала откройте книгу",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Способ озвучивания")
            .setItems(new String[]{"Системный Android TTS","Edge TTS · онлайн"},(d,which)->{
                stop();save();Intent intent=new Intent(this,which==0?AudioPlayerActivity.class:EdgeOnlineActivity.class);
                intent.putExtra("book_uri",docUri);intent.putExtra("word_index",index);
                if(which==0)intent.putExtra("start_tts",true);startActivity(intent);
            }).show();
    }
    @Override protected void onResume(){super.onResume();if(prefs!=null){
        int oldBg=bg,oldFg=fg,oldSize=size,oldFont=font,oldMode=mode,oldPortion=portion;
        int oldCursorBg=cursorBackground,oldCursorText=cursorText,oldCursorScale=cursorScale;
        int oldNormalText=normalSelectText,oldNormalScale=normalSelectScale,oldButton=buttonColor;
        int oldFocus=focusColor,oldLineColor=lineColor,oldLinePos=linePosition;
        boolean oldPoint=focusPoint,oldLines=focusLinesEnabled;
        readPreferences();
        if(root!=null&&words.length>0&&(oldBg!=bg||oldFg!=fg||oldSize!=size||oldFont!=font||oldMode!=mode||oldPortion!=portion
            ||oldCursorBg!=cursorBackground||oldCursorText!=cursorText||oldCursorScale!=cursorScale||oldNormalText!=normalSelectText
            ||oldNormalScale!=normalSelectScale||oldButton!=buttonColor||oldFocus!=focusColor||oldLineColor!=lineColor
            ||oldLinePos!=linePosition||oldPoint!=focusPoint||oldLines!=focusLinesEnabled))draw();
    }}
    @Override protected void onPause(){save();stop();super.onPause();}
    @Override protected void onDestroy(){stop();super.onDestroy();}
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
