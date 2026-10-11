package ru.reader.rsvp;

import android.app.*;import android.os.*;import android.content.*;import android.graphics.Color;import android.graphics.Typeface;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.text.*;import android.view.*;import android.widget.*;import android.view.MotionEvent;import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.RelativeSizeSpan;import java.util.*;

public class ReaderActivity extends Activity {
  android.content.SharedPreferences prefs; LinearLayout root,bar; TextView wordView,progressView,speedView; ScrollView normalScroll; TextView normalText; LinearLayout readingFrame; TextView modeButton,playButton; View rsvpPane; long lastScrollSave=0; Handler handler=new Handler(Looper.getMainLooper());
  String title="",author="",body=""; String[] words=new String[0]; int index=0,wpm=350,display=1,size=36,bg=0xfff3e7d5,fg=0xff30221e,font=0; boolean rsvp=true,playing=false,swipeSeeking=false,pausePunctuation=true,pauseLong=true,pauseSentence=true; float swipeStartY=0; int swipeStartIndex=0; int normalY=0; String docUri=""; String[] fontNames={"Без засечек","С засечками","Моноширинный"};
  Runnable ticker=new Runnable(){public void run(){if(!playing)return;if(rsvp){if(index<words.length-1)index++;else{stop();return;}updateWord();}else if(normalScroll!=null){normalScroll.smoothScrollBy(0,Math.max(2,size/4));normalY=normalScroll.getScrollY();}if(index%8==0)save();handler.postDelayed(this,rsvp?delayForWord():80);}};
  @Override public void onCreate(Bundle b){
    super.onCreate(b);
    prefs=getSharedPreferences("reader",MODE_PRIVATE);
    wpm=prefs.getInt("wpm",350);display=prefs.getInt("display",1);
    size=prefs.getInt("size",36);bg=prefs.getInt("bg",0xfff3e7d5);
    fg=prefs.getInt("fg",0xff30221e);font=prefs.getInt("font",1);
    rsvp=prefs.getBoolean("rsvp",true);pausePunctuation=prefs.getBoolean("pausePunctuation",true);pauseLong=prefs.getBoolean("pauseLong",true);pauseSentence=prefs.getBoolean("pauseSentence",true);
    docUri=getIntent().getStringExtra("book_uri");
    if(docUri==null || docUri.isEmpty()) docUri=prefs.getString("uri","");
    index=prefs.getInt("index",0);
    normalY=prefs.getInt("scroll",0);
    try {
      org.json.JSONArray arr=new org.json.JSONArray(prefs.getString("library","[]"));
      for(int i=0;i<arr.length();i++){
        org.json.JSONObject o=arr.optJSONObject(i);
        if(o!=null && docUri.equals(o.optString("uri"))){
          index=o.optInt("index",0);normalY=o.optInt("scroll",0);break;
        }
      }
    } catch(Exception ignored){}
    if(getIntent().getBooleanExtra("reset",false)){index=0;normalY=0;}
    draw();
    if(!docUri.isEmpty())load(Uri.parse(docUri),getIntent().getBooleanExtra("reset",false));
  }

  int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);} Typeface type(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
  void stop(){playing=false;handler.removeCallbacks(ticker);updatePlayButton();}
  void save(){
    prefs.edit().putInt("wpm",wpm).putInt("display",display).putInt("size",size)
      .putInt("bg",bg).putInt("fg",fg).putInt("font",font)
      .putBoolean("rsvp",rsvp).putBoolean("pausePunctuation",pausePunctuation).putBoolean("pauseLong",pauseLong).putBoolean("pauseSentence",pauseSentence).putString("uri",docUri)
      .putInt("index",index).putInt("scroll",normalY).apply();
    if(docUri.isEmpty() || words.length==0)return;
    try{
      org.json.JSONArray arr=new org.json.JSONArray(prefs.getString("library","[]"));
      org.json.JSONObject current=null;
      org.json.JSONArray out=new org.json.JSONArray();
      for(int i=0;i<arr.length();i++){
        org.json.JSONObject item=arr.optJSONObject(i);
        if(item==null)continue;
        if(docUri.equals(item.optString("uri")))current=item;
        else out.put(item);
      }
      if(current==null)current=new org.json.JSONObject();
      current.put("uri",docUri);current.put("title",title);
      current.put("author",author);
      current.put("index",index);current.put("scroll",normalY);
      current.put("progress",(index+1)*100/Math.max(1,words.length));
      current.put("openedAt",System.currentTimeMillis());
      out.put(current);
      prefs.edit().putString("library",out.toString()).apply();
    }catch(Exception ignored){}
  }

  TextView text(String s,int sp){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(fg);return t;}
  LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  void pad(View v,int n){v.setPadding(dp(n),dp(n),dp(n),dp(n));}
  void add(LinearLayout p,View c){p.addView(c,new LinearLayout.LayoutParams(-1,-2));}
  Button button(String s,View.OnClickListener l){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setOnClickListener(l);return b;}
  void rowButton(LinearLayout row,String s,View.OnClickListener click){Button b=button(s,click);row.addView(b,new LinearLayout.LayoutParams(0,dp(58),1));}
  GradientDrawable shape(int fill,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));return d;}
  int panelColor(){return (bg==0xff1e2025)?0xff303238:0xfffff9f0;}
  void setup(){root=column();root.setBackgroundColor(bg);setContentView(root);}
  void showMode(){if(rsvpPane==null||normalScroll==null)return;rsvpPane.setVisibility(rsvp?View.VISIBLE:View.GONE);normalScroll.setVisibility(rsvp?View.GONE:View.VISIBLE);if(modeButton!=null)modeButton.setText(rsvp?"📖 Обычный":"⚡ Скоростной");updateProgress();}
  void toggleMode(){stop();if(rsvp){rsvp=false;normalScroll.post(()->{int range=Math.max(0,normalText.getHeight()-normalScroll.getHeight());normalScroll.scrollTo(0,(int)((long)range*index/Math.max(1,words.length-1)));});}else{rsvp=true;}showMode();save();}

  void draw(){
    stop();setup();root.setPadding(dp(10),dp(20),dp(10),dp(7));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);add(root,top);
    Button menuBtn=button("←",v->finish());top.addView(menuBtn,new LinearLayout.LayoutParams(dp(52),dp(53)));
    TextView name=text(title.isEmpty()?"Читалка":"Читалка",23);name.setTypeface(Typeface.SERIF,Typeface.BOLD);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
    Button mark=button("⭐",v->bookmark());top.addView(mark,new LinearLayout.LayoutParams(dp(53),dp(53)));
    Button config=button("☷",v->settings());top.addView(config,new LinearLayout.LayoutParams(dp(53),dp(53)));
    if(words.length==0){TextView intro=text("EPUB · FB2 · HTML\n\nОткройте книгу, чтобы начать чтение.\nЗакладки, положение и настройки сохраняются автоматически.",20);pad(intro,24);add(root,intro);add(root,button("＋  Открыть книгу",v->pick()));return;}
    TextView bookTitle=text(title,19);bookTitle.setTypeface(Typeface.SERIF,Typeface.BOLD);bookTitle.setSingleLine(true);bookTitle.setEllipsize(TextUtils.TruncateAt.END);pad(bookTitle,12);add(root,bookTitle);
    LinearLayout modeRow=new LinearLayout(this);modeRow.setGravity(Gravity.CENTER_VERTICAL);add(root,modeRow);
    progressView=text("",13);progressView.setGravity(Gravity.CENTER_VERTICAL);modeRow.addView(progressView,new LinearLayout.LayoutParams(0,dp(49),1));
    Button mode=button("",v->toggleMode());modeButton=mode;mode.setTextColor(0xffffffff);mode.setBackground(shape(0xff915439,19));modeRow.addView(mode,new LinearLayout.LayoutParams(dp(174),dp(48)));
    readingFrame=column();readingFrame.setBackground(shape(panelColor(),23));readingFrame.setPadding(dp(9),dp(12),dp(9),dp(12));LinearLayout.LayoutParams frameParams=new LinearLayout.LayoutParams(-1,0,1);frameParams.setMargins(0,dp(7),0,dp(8));root.addView(readingFrame,frameParams);
    wordView=text("",size);wordView.setGravity(Gravity.CENTER);wordView.setTypeface(type(),Typeface.BOLD);
    readingFrame.addView(wordView,new LinearLayout.LayoutParams(-1,-1));rsvpPane=wordView;
    wordView.setOnTouchListener((v,event)->{
      if(!rsvp || words.length==0)return false;
      if(event.getAction()==MotionEvent.ACTION_DOWN){stop();swipeSeeking=true;swipeStartY=event.getY();swipeStartIndex=index;showSeekingWords();return true;}
      if(event.getAction()==MotionEvent.ACTION_MOVE){int delta=Math.round((swipeStartY-event.getY())/dp(26));index=Math.max(0,Math.min(words.length-1,swipeStartIndex+delta));showSeekingWords();return true;}
      if(event.getAction()==MotionEvent.ACTION_UP || event.getAction()==MotionEvent.ACTION_CANCEL){swipeSeeking=false;updateWord();save();return true;}
      return true;
    });
    normalScroll=new ScrollView(this);normalText=text(body,Math.max(12,(int)(size*.60)));normalText.setTypeface(type());normalText.setLineSpacing(dp(6),1.16f);pad(normalText,14);normalScroll.addView(normalText);readingFrame.addView(normalScroll,new LinearLayout.LayoutParams(-1,-1));
    normalScroll.setOnScrollChangeListener((View.OnScrollChangeListener)(v,x,y,oldX,oldY)->{if(rsvp||y==oldY)return;normalY=y;int range=Math.max(1,normalText.getHeight()-normalScroll.getHeight());index=Math.min(words.length-1,Math.round((float)y/range*(words.length-1)));updateProgress();long now=android.os.SystemClock.uptimeMillis();if(now-lastScrollSave>800){lastScrollSave=now;save();}});
    normalScroll.post(()->{if(!rsvp){normalScroll.scrollTo(0,normalY);}});
    LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);controls.setBackground(shape(panelColor(),22));add(root,controls);
    Button play=button("▶",v->{if(playing){stop();}else{playing=true;handler.post(ticker);}updatePlayButton();});playButton=play;play.setTextColor(-1);play.setBackground(shape(0xff915439,99));controls.addView(play,new LinearLayout.LayoutParams(-1,dp(64)));
    LinearLayout speedPanel=column();add(root,speedPanel);speedView=text("Скорость чтения       "+wpm+" слов/мин",15);pad(speedView,4);add(speedPanel,speedView);
    SeekBar seek=new SeekBar(this);seek.setMax(900);seek.setProgress(wpm-100);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean from){wpm=p+100;speedView.setText("Скорость чтения       "+wpm+" слов/мин");if(from)save();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});add(speedPanel,seek);
    Button wordDisplay=button("Показ слов  ▾",v->displayDialog());add(root,wordDisplay);
    showMode();updateWord();
  }
  void updatePlayButton(){if(playButton!=null)playButton.setText(playing?"❚❚":"▶");}
  void updateProgress(){if(progressView!=null)progressView.setText((index+1)+" / "+words.length+" слов · "+((index+1)*100/Math.max(1,words.length))+"%");}
  long delayForWord(){
    long base=Math.max(60,60000L/Math.max(100,wpm));
    if(words.length==0)return base;
    String w=words[Math.min(index,words.length-1)];
    double mult=1.0;
    if(pauseLong && w.length()>=12)mult+=0.5;
    if(pausePunctuation && w.matches(".*[,;:—–]$"))mult+=0.6;
    if(pauseSentence && (w.endsWith(".") || w.endsWith("!") || w.endsWith("?") || w.endsWith("…") || w.endsWith("»")))mult+=1.2;
    return (long)(base*mult);
  }
  void showSeekingWords(){
    if(wordView==null || words.length==0)return;
    int lo=Math.max(0,index-5),hi=Math.min(words.length,index+6);
    StringBuilder b=new StringBuilder();int start=-1,end=-1;
    for(int i=lo;i<hi;i++){
      if(i>lo)b.append("\n");
      if(i==index)start=b.length();
      b.append(words[i]);
      if(i==index)end=b.length();
    }
    SpannableString span=new SpannableString(b.toString());
    span.setSpan(new ForegroundColorSpan(0xff999999),0,span.length(),33);
    if(start>=0){span.setSpan(new ForegroundColorSpan(0xff101010),start,end,33);span.setSpan(new StyleSpan(Typeface.BOLD),start,end,33);span.setSpan(new RelativeSizeSpan(1.5f),start,end,33);}
    wordView.setTextSize(Math.max(16,size*0.65f));wordView.setText(span);updateProgress();
  }
  void updateWord(){if(wordView!=null&&words.length>0){String current=words[Math.min(index,words.length-1)];String before=index>0?words[index-1]:"";String after=index+1<words.length?words[index+1]:"";String result=display==0?current:display==1?before+"\n"+current+"\n"+after:current+"\n"+after;android.text.SpannableString span=new android.text.SpannableString(result);int begin=result.indexOf(current,display==1?before.length():0);if(begin>=0){span.setSpan(new android.text.style.RelativeSizeSpan(1.0f),begin,begin+current.length(),33);span.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),begin,begin+current.length(),33);}if(begin>0)span.setSpan(new android.text.style.RelativeSizeSpan(.43f),0,begin,33);if(begin+current.length()<result.length())span.setSpan(new android.text.style.RelativeSizeSpan(.43f),begin+current.length(),result.length(),33);wordView.setText(span);wordView.setTextSize(size);wordView.setTypeface(type());}updateProgress();}
  void step(int by){if(rsvp){index=Math.max(0,Math.min(words.length-1,index+by));updateWord();}else if(normalScroll!=null){normalScroll.smoothScrollBy(0,by*normalScroll.getHeight()*3/4);}save();}
  void menu(){String[] items={"Открыть книгу","Закладки","Настройки","Скорочтение","Обычное чтение"};new AlertDialog.Builder(this).setItems(items,(d,n)->{if(n==0)pick();if(n==1)bookmarks();if(n==2)settings();if(n==3||n==4){if(rsvp!=(n==3))toggleMode();}}).show();}
  void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,7);}
  @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req==7&&res==RESULT_OK&&data!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}load(u,true);}}
  void load(Uri uri,boolean reset){new Thread(()->{try{BookLoader.Book book=BookLoader.load(this,uri);if(book.text.isEmpty())throw new Exception("Нет текста");runOnUiThread(()->{stop();title=book.title;author=book.author==null?"":book.author;body=book.text;words=body.split("\\s+");docUri=uri.toString();if(reset){index=0;normalY=0;}else index=Math.max(0,Math.min(index,words.length-1));save();draw();});}catch(Exception e){runOnUiThread(()->new AlertDialog.Builder(this).setMessage("Не удалось открыть книгу: "+e.getMessage()).setPositiveButton("ОК",null).show());}}).start();}
  void bookmark(){if(words.length==0)return;String key="bookmark:"+docUri;Set<String> set=new HashSet<>(prefs.getStringSet(key,new HashSet<>()));set.add(String.valueOf(index));prefs.edit().putStringSet(key,set).apply();Toast.makeText(this,"Закладка добавлена",Toast.LENGTH_SHORT).show();}
  void bookmarks(){if(words.length==0)return;ArrayList<Integer> list=new ArrayList<>();for(String s:prefs.getStringSet("bookmark:"+docUri,new HashSet<>()))try{list.add(Integer.parseInt(s));}catch(Exception ignored){}Collections.sort(list);String[] rows=new String[list.size()];for(int i=0;i<list.size();i++){int n=list.get(i);rows[i]=(n+1)+" · "+words[Math.min(n,words.length-1)];}new AlertDialog.Builder(this).setTitle("Закладки").setItems(rows,(d,which)->{index=list.get(which);normalY=0;save();draw();}).setNeutralButton("Удалить все",(d,w)->{prefs.edit().remove("bookmark:"+docUri).apply();}).setPositiveButton("Закрыть",null).show();}
  void displayDialog(){String[] opts={"Только одно слово","Предыдущее и следующее","Текущее и следующее"};new AlertDialog.Builder(this).setTitle("Показ слов в скорочтении").setSingleChoiceItems(opts,display,(d,i)->{display=i;save();d.dismiss();updateWord();}).setNegativeButton("Отмена",null).show();}
  void settings(){String[] options={"Паузы при скорочтении","Режим показа слов","Шрифт","Размер шрифта","Цвет текста","Цвет фона","Тема: светлая","Тема: сепия","Тема: ночная","Тема: мягкая зелёная","Закладки","Открыть книгу"};new AlertDialog.Builder(this).setTitle("Настройки чтения").setItems(options,(d,n)->{switch(n){case 0:pauseDialog();break;case 1:displayDialog();break;case 2:fontDialog();break;case 3:sizeDialog();break;case 4:colorDialog(false);break;case 5:colorDialog(true);break;case 6:theme(0xffffffff,0xff202020);break;case 7:theme(0xfff3e7d5,0xff30221e);break;case 8:theme(0xff1e2025,0xffe8e5db);break;case 9:theme(0xffdfe9d9,0xff223a29);break;case 10:bookmarks();break;case 11:pick();break;}}).show();}
  void pauseDialog(){
    String[] names={"Пауза после запятых и знаков пунктуации","Пауза на длинных словах (12+ букв)","Пауза в конце предложения"};
    boolean[] enabled={pausePunctuation,pauseLong,pauseSentence};
    new AlertDialog.Builder(this).setTitle("Дополнительные паузы")
      .setMultiChoiceItems(names,enabled,(d,which,on)->enabled[which]=on)
      .setPositiveButton("Сохранить",(d,w)->{pausePunctuation=enabled[0];pauseLong=enabled[1];pauseSentence=enabled[2];save();})
      .setNegativeButton("Отмена",null).show();
  }
  void theme(int b,int f){bg=b;fg=f;save();draw();}
  void fontDialog(){new AlertDialog.Builder(this).setTitle("Шрифт").setSingleChoiceItems(fontNames,font,(d,i)->{font=i;save();d.dismiss();draw();}).setNegativeButton("Отмена",null).show();}
  void sizeDialog(){SeekBar seek=new SeekBar(this);seek.setMax(60);seek.setProgress(size-16);new AlertDialog.Builder(this).setTitle("Размер шрифта: от 16 до 76").setView(seek).setPositiveButton("Применить",(d,w)->{size=seek.getProgress()+16;save();draw();}).setNegativeButton("Отмена",null).show();}
  void colorDialog(boolean background){LinearLayout c=column();pad(c,20);int color=background?bg:fg;String[] labels={"Красный","Зелёный","Синий"};SeekBar[] sliders=new SeekBar[3];for(int i=0;i<3;i++){add(c,text(labels[i],16));SeekBar s=new SeekBar(this);s.setMax(255);s.setProgress(i==0?Color.red(color):i==1?Color.green(color):Color.blue(color));sliders[i]=s;add(c,s);}new AlertDialog.Builder(this).setTitle(background?"Цвет фона (RGB)":"Цвет шрифта (RGB)").setView(c).setPositiveButton("Применить",(d,w)->{int n=Color.rgb(sliders[0].getProgress(),sliders[1].getProgress(),sliders[2].getProgress());if(background)bg=n;else fg=n;save();draw();}).setNegativeButton("Отмена",null).show();}
  @Override protected void onPause(){super.onPause();save();stop();} @Override protected void onDestroy(){stop();super.onDestroy();}
}
