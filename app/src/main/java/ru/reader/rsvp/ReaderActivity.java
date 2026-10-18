package ru.reader.rsvp;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Ordinary, RSVP and inline Cursor reading share the same word-indexed progress. */
public class ReaderActivity extends Activity {
    private static final int NORMAL=0, RSVP=1, CURSOR=2;
    SharedPreferences prefs;
    LinearLayout root,readingFrame,controls,speedPanel;
    FrameLayout rsvpPane;
    ScrollView normalScroll;
    TextView normalText,wordView,progressView,speedView,modeButton,selectWordButton;
    ImageView playIcon;
    FocusLinesView focusLines;
    Spannable normalContent;
    final ArrayList<Object> cursorSpans=new ArrayList<>();
    final Handler handler=new Handler(Looper.getMainLooper());
    ReadingWordMap map=ReadingWordMap.parse("");
    String title="",author="",body="",docUri="";
    String[] words=new String[0];
    int index=0,wpm=350,portion=1,size=36,bg=0xfff3e7d5,fg=0xff30221e,font=1;
    int mode=RSVP,normalY=0,focusColor=0xffed3030,lineColor=0xff915439,linePosition=50;
    int cursorBackground=0xff96583f,cursorText=0xffffffff;
    boolean focusPoint=true,focusLinesEnabled=true,playing=false,selecting=false,selectedWordActive=false,restoringScroll=false;
    boolean pausePunctuation=true,pauseLong=true,pauseSentence=true;
    float swipeStartY=0;int swipeStartIndex=0;
    final String[] fontNames={"Без засечек","С засечками","Моноширинный"};

    final Runnable ticker=new Runnable(){@Override public void run(){
        if(!playing || words.length==0)return;
        if(mode==NORMAL){stop();return;}
        int shown=Math.min(portion,words.length-index);
        if(index+shown>=words.length){stop();save();return;}
        index+=shown;
        if(mode==RSVP)updateWord(); else updateCursor(true);
        save();handler.postDelayed(this,delayForPortion(index,Math.min(portion,words.length-index)));
    }};

    @Override public void onCreate(Bundle state){super.onCreate(state);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        readPreferences();
        docUri=getIntent().getStringExtra("book_uri");
        if(docUri==null||docUri.isEmpty())docUri=prefs.getString("uri","");
        index=prefs.getInt("index",0);normalY=prefs.getInt("scroll",0);
        try{org.json.JSONArray library=new org.json.JSONArray(prefs.getString("library","[]"));
            for(int i=0;i<library.length();i++){org.json.JSONObject o=library.optJSONObject(i);
                if(o!=null&&docUri.equals(o.optString("uri"))){index=o.optInt("index",index);normalY=o.optInt("scroll",normalY);break;}}
        }catch(Exception ignored){}
        if(!getIntent().getBooleanExtra("reset",false))index=prefs.getInt("tts_word:"+docUri,index);
        if(getIntent().getBooleanExtra("reset",false)){index=0;normalY=0;}
        draw();if(!docUri.isEmpty())load(Uri.parse(docUri),getIntent().getBooleanExtra("reset",false));
    }
    void readPreferences(){
        wpm=prefs.getInt("wpm",350);portion=ReadingWordMap.portion(prefs.getInt("rsvp_portion",1));
        size=prefs.getInt("size",36);bg=prefs.getInt("bg",0xfff3e7d5);fg=prefs.getInt("fg",0xff30221e);font=prefs.getInt("font",1);
        mode=prefs.getInt("reading_mode",prefs.getBoolean("rsvp",true)?RSVP:NORMAL);mode=Math.max(NORMAL,Math.min(CURSOR,mode));
        pausePunctuation=prefs.getBoolean("pausePunctuation",true);pauseLong=prefs.getBoolean("pauseLong",true);pauseSentence=prefs.getBoolean("pauseSentence",true);
        focusPoint=prefs.getBoolean("rsvp_focus_point",true);focusLinesEnabled=prefs.getBoolean("rsvp_focus_lines",true);
        focusColor=prefs.getInt("rsvp_focus_color",0xffed3030);lineColor=prefs.getInt("rsvp_line_color",0xff915439);
        linePosition=Math.max(0,Math.min(100,prefs.getInt("rsvp_line_position",50)));
        cursorBackground=prefs.getInt("cursor_background",0xff96583f);cursorText=prefs.getInt("cursor_text",0xffffffff);
    }
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    Typeface type(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
    int panelColor(){return bg==0xff1e2025?0xff303238:0xfffff9f0;}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String s,int sp){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(fg);return v;}
    LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}
    void add(LinearLayout to,View child){to.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    Button button(String label,View.OnClickListener click){Button v=new Button(this);v.setAllCaps(false);v.setText(label);v.setOnClickListener(click);return v;}
    ImageButton topIcon(int resource,View.OnClickListener click){ImageButton b=new ImageButton(this);b.setImageResource(resource);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(13),dp(13),dp(13),dp(13));b.setColorFilter(fg);b.setBackground(shape(panelColor(),16));b.setOnClickListener(click);return b;}
    void stop(){playing=false;handler.removeCallbacks(ticker);updatePlayButton();}
    void save(){
        if(prefs==null)return;
        prefs.edit().putInt("wpm",wpm).putInt("rsvp_portion",portion).putInt("size",size).putInt("bg",bg).putInt("fg",fg).putInt("font",font)
            .putInt("reading_mode",mode).putBoolean("rsvp",mode==RSVP).putString("uri",docUri)
            .putBoolean("pausePunctuation",pausePunctuation).putBoolean("pauseLong",pauseLong).putBoolean("pauseSentence",pauseSentence)
            .putInt("index",index).putInt("scroll",normalY).apply();
        if(docUri.isEmpty()||words.length==0)return;
        try{org.json.JSONArray library=new org.json.JSONArray(prefs.getString("library","[]"));
            org.json.JSONArray out=new org.json.JSONArray();org.json.JSONObject book=null;
            for(int i=0;i<library.length();i++){org.json.JSONObject item=library.optJSONObject(i);if(item==null)continue;
                if(docUri.equals(item.optString("uri")))book=item;else out.put(item);}
            if(book==null)book=new org.json.JSONObject();book.put("uri",docUri);book.put("title",title);book.put("author",author);
            book.put("index",index);book.put("scroll",normalY);book.put("progress",(index+1)*100/Math.max(1,words.length));book.put("openedAt",System.currentTimeMillis());
            out.put(book);prefs.edit().putString("library",out.toString()).apply();
        }catch(Exception ignored){}
    }
    void setup(){root=column();root.setBackgroundColor(bg);root.setPadding(dp(10),dp(20),dp(10),dp(7));setContentView(root);}
    void audioMenu(){if(docUri.isEmpty()){Toast.makeText(this,"Сначала откройте книгу",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Способ озвучивания")
        .setItems(new String[]{"Системный Android TTS","Edge TTS · онлайн"},(d,which)->{
            stop();save();Intent i=new Intent(this,which==0?AudioPlayerActivity.class:EdgeOnlineActivity.class);
            i.putExtra("book_uri",docUri);i.putExtra("word_index",index);
            if(which==0)i.putExtra("start_tts",true);startActivity(i);
        }).show();
    }
    void draw(){
        stop();setup();
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);add(root,top);
        top.addView(topIcon(R.drawable.ic_back,v->BackNavigation.back(this)),new LinearLayout.LayoutParams(dp(52),dp(53)));
        TextView appTitle=text("Читалка",23);appTitle.setTypeface(Typeface.SERIF,Typeface.BOLD);appTitle.setSingleLine(true);
        top.addView(appTitle,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(topIcon(R.drawable.ic_bookmark,v->bookmark()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        top.addView(topIcon(R.drawable.ic_settings,v->settings()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        top.addView(topIcon(R.drawable.ic_microphone,v->audioMenu()),new LinearLayout.LayoutParams(dp(53),dp(53)));
        if(words.length==0){TextView intro=text("EPUB · FB2 · HTML\n\nОткройте книгу, чтобы начать чтение.\nЗакладки, положение и настройки сохраняются автоматически.",20);
            intro.setPadding(dp(24),dp(24),dp(24),dp(24));add(root,intro);add(root,button("＋  Открыть книгу",v->pick()));return;}
        TextView bookTitle=text(title,19);bookTitle.setTypeface(Typeface.SERIF,Typeface.BOLD);bookTitle.setSingleLine(true);bookTitle.setEllipsize(TextUtils.TruncateAt.END);
        bookTitle.setPadding(dp(12),dp(12),dp(12),dp(12));add(root,bookTitle);
        LinearLayout modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);add(root,modeRow);
        progressView=text("",13);progressView.setGravity(Gravity.CENTER_VERTICAL);modeRow.addView(progressView,new LinearLayout.LayoutParams(0,dp(49),1));
        selectWordButton=text("",13);selectWordButton.setGravity(Gravity.CENTER);selectWordButton.setBackground(shape(panelColor(),14));
        android.graphics.drawable.Drawable pointer=getDrawable(R.drawable.ic_select_word);
        if(pointer!=null){pointer=pointer.mutate();pointer.setTint(fg);pointer.setBounds(0,0,dp(22),dp(22));selectWordButton.setCompoundDrawables(null,pointer,null,null);}
        selectWordButton.setContentDescription("Выбрать стартовое слово для скорочтения");
        selectWordButton.setOnClickListener(v->{selecting=!selecting;showMode();Toast.makeText(this,selecting?"Коснитесь нужного слова в тексте":"Выбор стартового слова выключен",Toast.LENGTH_SHORT).show();});
        modeRow.addView(selectWordButton,new LinearLayout.LayoutParams(dp(47),dp(47)));
        modeButton=text("",14);modeButton.setTextColor(Color.WHITE);modeButton.setGravity(Gravity.CENTER);modeButton.setBackground(shape(0xff915439,17));
        modeButton.setOnClickListener(v->modeDialog());LinearLayout.LayoutParams modeLp=new LinearLayout.LayoutParams(dp(143),dp(48));modeLp.leftMargin=dp(5);modeRow.addView(modeButton,modeLp);

        readingFrame=column();readingFrame.setBackground(shape(panelColor(),23));readingFrame.setPadding(dp(9),dp(12),dp(9),dp(12));
        LinearLayout.LayoutParams frameLp=new LinearLayout.LayoutParams(-1,0,1);frameLp.setMargins(0,dp(7),0,dp(8));root.addView(readingFrame,frameLp);
        rsvpPane=new FrameLayout(this);wordView=text("",size);wordView.setGravity(Gravity.CENTER);wordView.setTypeface(type(),Typeface.BOLD);
        rsvpPane.addView(wordView,new FrameLayout.LayoutParams(-1,-1));
        focusLines=new FocusLinesView(this);focusLines.setClickable(false);rsvpPane.addView(focusLines,new FrameLayout.LayoutParams(-1,-1));
        readingFrame.addView(rsvpPane,new LinearLayout.LayoutParams(-1,-1));
        wordView.setOnTouchListener((v,e)->{
            if(mode!=RSVP||words.length==0)return false;
            if(e.getAction()==MotionEvent.ACTION_DOWN){stop();swipeStartY=e.getY();swipeStartIndex=index;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){int delta=Math.round((swipeStartY-e.getY())/dp(26));index=map.clamp(swipeStartIndex+delta);updateWord();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){updateWord();save();return true;}
            return true;
        });
        normalScroll=new ScrollView(this);normalScroll.setFillViewport(true);
        normalText=text(body,Math.max(12,(int)(size*.60f)));normalText.setTypeface(type());normalText.setLineSpacing(dp(Math.max(8,size/3)),1.12f);
        normalText.setPadding(dp(14),dp(14),dp(14),dp(14));
        normalText.setText(body,TextView.BufferType.SPANNABLE);normalContent=(Spannable)normalText.getText();
        normalScroll.addView(normalText);readingFrame.addView(normalScroll,new LinearLayout.LayoutParams(-1,-1));
        normalText.setOnTouchListener((v,e)->{
            if(mode!=NORMAL||!selecting||e.getAction()!=MotionEvent.ACTION_UP)return selecting;
            android.text.Layout layout=normalText.getLayout();if(layout==null)return true;
            int y=(int)e.getY()-normalText.getTotalPaddingTop()+normalText.getScrollY();
            int line=layout.getLineForVertical(Math.max(0,y));
            float x=e.getX()-normalText.getTotalPaddingLeft();
            int offset=layout.getOffsetForHorizontal(line,Math.max(0,x));
            index=map.atCharacter(offset);selecting=false;selectedWordActive=true;showMode();alignToWord(false);save();
            Toast.makeText(this,"Начало скорочтения: "+words[index],Toast.LENGTH_SHORT).show();return true;
        });
        normalScroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->{
            if(restoringScroll||mode!=NORMAL||y==oldY||words.length==0)return;
            normalY=y;
            android.text.Layout l=normalText.getLayout();
            if(l!=null){int line=l.getLineForVertical(y+dp(12));index=map.atCharacter(l.getLineStart(line));}
            updateProgress();save();
        });
        controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setBackground(shape(panelColor(),22));add(root,controls);
        FrameLayout playFrame=new FrameLayout(this);playFrame.setBackground(shape(0xff915439,20));
        playIcon=new ImageView(this);playIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);playIcon.setColorFilter(Color.WHITE);
        playFrame.addView(playIcon,new FrameLayout.LayoutParams(dp(30),dp(30),Gravity.CENTER));
        playFrame.setOnClickListener(v->{if(playing)stop();else{playing=true;handler.removeCallbacks(ticker);handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));}updatePlayButton();});
        controls.addView(playFrame,new LinearLayout.LayoutParams(dp(72),dp(72)));
        speedPanel=column();speedPanel.setBackground(shape(panelColor(),18));speedPanel.setPadding(dp(8),dp(8),dp(8),dp(8));add(root,speedPanel);
        LinearLayout speedRow=new LinearLayout(this);speedRow.setGravity(Gravity.CENTER_VERTICAL);add(speedPanel,speedRow);
        ImageView gauge=new ImageView(this);gauge.setImageResource(R.drawable.ic_speed);gauge.setColorFilter(fg);speedRow.addView(gauge,new LinearLayout.LayoutParams(dp(28),dp(28)));
        speedView=text(wpm+" слов/мин",17);speedView.setGravity(Gravity.CENTER);speedRow.addView(speedView,new LinearLayout.LayoutParams(0,dp(48),1));
        Button minus=button("−10",v->changeSpeed(-10));minus.setBackground(shape(panelColor(),14));speedRow.addView(minus,new LinearLayout.LayoutParams(dp(52),dp(52)));
        Button plus=button("+10",v->changeSpeed(10));plus.setBackground(shape(panelColor(),14));speedRow.addView(plus,new LinearLayout.LayoutParams(dp(52),dp(52)));
        showMode();updatePlayButton();
        normalScroll.post(()->{
            if(mode==NORMAL && !selecting && normalY>0){restoringScroll=true;normalScroll.scrollTo(0,normalY);normalScroll.post(()->restoringScroll=false);}
            else if(mode==CURSOR)updateCursor(true);
        });
    }
    void modeDialog(){
        String[] options={"Обычный","Скорочтение","Курсор"};
        new AlertDialog.Builder(this).setTitle("Режим чтения")
            .setSingleChoiceItems(options,mode,(d,choice)->{setMode(choice);d.dismiss();})
            .setNegativeButton("Отмена",null).show();
    }
    void setMode(int next){if(next<0||next>2||next==mode)return;
        stop();mode=next;selecting=false;selectedWordActive=false;showMode();
        if(mode==NORMAL||mode==CURSOR)alignToWord(true);
        if(mode==RSVP)updateWord();save();
    }
    void showMode(){
        if(rsvpPane==null||normalScroll==null)return;
        boolean rsvp=mode==RSVP;
        rsvpPane.setVisibility(rsvp?View.VISIBLE:View.GONE);
        normalScroll.setVisibility(rsvp?View.GONE:View.VISIBLE);
        if(focusLines!=null){focusLines.setVisibility(rsvp&&focusLinesEnabled?View.VISIBLE:View.GONE);focusLines.invalidate();}
        if(modeButton!=null)modeButton.setText(mode==NORMAL?"Обычный ▾":mode==RSVP?"Скорочтение ▾":"Курсор ▾");
        if(selectWordButton!=null){selectWordButton.setVisibility(mode==NORMAL?View.VISIBLE:View.GONE);selectWordButton.setBackground(shape(selecting?0xffd2a57d:panelColor(),14));}
        if(controls!=null)controls.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(speedPanel!=null)speedPanel.setVisibility(mode==NORMAL?View.GONE:View.VISIBLE);
        if(rsvp)updateWord();else updateCursor(mode==CURSOR||selecting);
        updateProgress();
    }
    void changeSpeed(int by){wpm=Math.max(100,Math.min(1000,wpm+by));if(speedView!=null)speedView.setText(wpm+" слов/мин");save();
        if(playing){handler.removeCallbacks(ticker);handler.postDelayed(ticker,delayForPortion(index,Math.min(portion,words.length-index)));}
    }
    void updatePlayButton(){if(playIcon!=null)playIcon.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play);}
    void updateProgress(){if(progressView!=null&&words.length>0)progressView.setText((index+1)+" / "+words.length+" слов · "+((index+1)*100/words.length)+"%");}
    long delayForPortion(int first,int count){
        long base=ReadingWordMap.delayMillis(wpm,count);if(words.length==0)return base;
        double extra=0;
        for(int i=first;i<Math.min(words.length,first+count);i++){
            String w=words[i];double mult=0;
            if(pauseLong&&w.length()>=12)mult+=0.5;
            if(pausePunctuation&&w.matches(".*[,;:—–]$"))mult+=0.6;
            if(pauseSentence&&(w.endsWith(".")||w.endsWith("!")||w.endsWith("?")||w.endsWith("…")||w.endsWith("»")))mult+=1.2;
            extra+=mult;
        }
        return (long)(base*(1+(extra/Math.max(1,count))*prefs.getInt("pausePercent",100)/100.0));
    }
    void updateWord(){
        if(wordView==null||words.length==0)return;
        int count=Math.min(portion,words.length-index);
        StringBuilder phrase=new StringBuilder();
        for(int i=0;i<count;i++){if(i>0)phrase.append(' ');phrase.append(words[index+i]);}
        SpannableString span=new SpannableString(phrase.toString());
        if(focusPoint){int chosen=Math.max(0,(count-1)/2),start=0;
            for(int i=0;i<chosen;i++)start+=words[index+i].length()+1;
            int at=start+ReadingWordMap.orp(words[index+chosen].length());
            if(at>=0&&at<span.length())span.setSpan(new ForegroundColorSpan(focusColor),at,at+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        wordView.setTextSize(size);wordView.setTypeface(type(),Typeface.BOLD);wordView.setText(span);
        if(focusLines!=null)focusLines.invalidate();updateProgress();
    }
    void clearCursorSpans(){if(normalContent!=null)for(Object obj:cursorSpans)normalContent.removeSpan(obj);cursorSpans.clear();}
    void markSpan(Object s,int first,int last){normalContent.setSpan(s,first,last,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);cursorSpans.add(s);}
    void updateCursor(boolean ensureVisible){
        if(normalText==null||normalContent==null||words.length==0)return;
        clearCursorSpans();
        if(mode==CURSOR||mode==NORMAL&&(selecting||selectedWordActive)){
            int start=map.starts[map.clamp(index)],end=map.ends[map.clamp(index+Math.min(portion,words.length-index)-1)];
            markSpan(new BackgroundColorSpan(cursorBackground),start,end);
            markSpan(new ForegroundColorSpan(cursorText),start,end);
            markSpan(new StyleSpan(Typeface.BOLD),start,end);
            float normalSp=Math.max(12,size*.60f);markSpan(new RelativeSizeSpan(Math.max(1f,size/normalSp)),start,end);
        }else if(mode==NORMAL&&!selecting){ // Preserve the selected starting word when returning to normal reading.
            int start=map.starts[map.clamp(index)],end=map.ends[map.clamp(index)];
            markSpan(new BackgroundColorSpan(0x55b58563),start,end);
        }
        if(ensureVisible)alignToWord(mode==CURSOR);
        updateProgress();
    }
    void alignToWord(boolean center){
        if(normalScroll==null||normalText==null||words.length==0)return;
        normalScroll.post(()->{
            if(normalText==null||normalText.getLayout()==null||normalScroll==null)return;
            android.text.Layout layout=normalText.getLayout();
            int line=layout.getLineForOffset(map.starts[map.clamp(index)]);
            int y=layout.getLineTop(line)+normalText.getTotalPaddingTop();
            int visibleTop=normalScroll.getScrollY(),visibleBottom=visibleTop+normalScroll.getHeight();
            if(center||y<visibleTop+dp(30)||layout.getLineBottom(line)>visibleBottom-dp(30)){
                int target=Math.max(0,y-(center?normalScroll.getHeight()/3:dp(36)));
                restoringScroll=true;normalScroll.smoothScrollTo(0,target);
                normalY=target;handler.postDelayed(()->restoringScroll=false,450);
            }
        });
    }
    void bookmark(){if(words.length==0)return;String key="bookmark:"+docUri;
        Set<String> s=new HashSet<>(prefs.getStringSet(key,Collections.emptySet()));s.add(String.valueOf(index));prefs.edit().putStringSet(key,s).apply();Toast.makeText(this,"Закладка добавлена",Toast.LENGTH_SHORT).show();
    }
    void bookmarks(){if(words.length==0)return;ArrayList<Integer> positions=new ArrayList<>();
        for(String s:prefs.getStringSet("bookmark:"+docUri,Collections.emptySet()))try{positions.add(map.clamp(Integer.parseInt(s)));}catch(Exception ignored){}
        Collections.sort(positions);String[] labels=new String[positions.size()];for(int i=0;i<labels.length;i++)labels[i]=(positions.get(i)+1)+" · "+words[positions.get(i)];
        new AlertDialog.Builder(this).setTitle("Закладки").setItems(labels,(d,which)->{index=positions.get(which);showMode();if(mode!=RSVP)alignToWord(true);save();})
            .setNeutralButton("Удалить все",(d,w)->prefs.edit().remove("bookmark:"+docUri).apply()).setNegativeButton("Закрыть",null).show();
    }
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,7);}
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);
        if(req==7&&result==RESULT_OK&&data!=null&&data.getData()!=null){Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            load(uri,true);
        }
    }
    void load(Uri uri,boolean reset){new Thread(()->{try{BookLoader.Book book=BookLoader.load(this,uri);if(book.text.isEmpty())throw new Exception("Нет текста");
        ReadingWordMap parsed=ReadingWordMap.parse(book.text);
        if(parsed.size()==0)throw new Exception("В книге не найдено слов");
        runOnUiThread(()->{
            stop();title=book.title;author=book.author==null?"":book.author;body=book.text;map=parsed;words=parsed.words;docUri=uri.toString();
            if(reset){index=0;normalY=0;}else index=map.clamp(index);
            save();draw();
        });}catch(Exception ex){runOnUiThread(()->new AlertDialog.Builder(this).setMessage("Не удалось открыть книгу: "+ex.getMessage()).setPositiveButton("ОК",null).show());}}).start();
    }
    void settings(){stop();startActivity(new Intent(this,SettingsActivity.class).putExtra("rsvp_tab",true));}
    @Override protected void onResume(){super.onResume();if(prefs!=null){
        int oldBg=bg,oldFg=fg,oldSize=size,oldFont=font,oldMode=mode,oldPortion=portion;boolean oldPoint=focusPoint,oldLines=focusLinesEnabled;
        int oldFocus=focusColor,oldLineColor=lineColor,oldLinePosition=linePosition;
        readPreferences();
        if(root!=null&&(oldBg!=bg||oldFg!=fg||oldSize!=size||oldFont!=font||oldMode!=mode||oldPortion!=portion
            ||oldPoint!=focusPoint||oldLines!=focusLinesEnabled||oldFocus!=focusColor||oldLineColor!=lineColor||oldLinePosition!=linePosition))draw();
    }}
    @Override protected void onPause(){save();stop();super.onPause();}
    @Override protected void onDestroy(){stop();super.onDestroy();}
    @Override public void onBackPressed(){BackNavigation.back(this);}

    private class FocusLinesView extends View {
        final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        FocusLinesView(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(!focusLinesEnabled||mode!=RSVP)return;
            p.setColor(lineColor);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
            float x=getWidth()*linePosition/100f,mid=getHeight()/2f,half=dp(48);
            canvas.drawLine(0,mid-half,getWidth(),mid-half,p);canvas.drawLine(0,mid+half,getWidth(),mid+half,p);
            p.setStrokeWidth(dp(2));canvas.drawLine(x,mid-half,x,mid-half+dp(10),p);canvas.drawLine(x,mid+half-dp(10),x,mid+half,p);
        }
    }
}
