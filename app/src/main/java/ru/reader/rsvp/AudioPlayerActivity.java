package ru.reader.rsvp;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;

/** Standalone audio interface. Playback remains in AudioPlaybackService on navigation. */
public class AudioPlayerActivity extends Activity {
    private static final int FILE_PICK=301;
    LinearLayout root,playRow; TextView title,info,elapsed,duration,play,spokenText; SeekBar progress;
    Handler handler=new Handler(Looper.getMainLooper()); boolean dragging=false;String bookUri="";
    final int BACKGROUND=0xfff3e7d5, TEXT=0xff30221e, ACCENT=0xff94583b;
    BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refresh();}};
    Runnable updater=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);return v;}
    TextView control(String text,View.OnClickListener action){TextView t=label(text,19);t.setGravity(Gravity.CENTER);t.setBackground(bg(0xfffff8ef,20));t.setOnClickListener(action);t.setPadding(dp(10),dp(12),dp(10),dp(12));return t;}
    void command(String action){startService(new Intent(this,AudioPlaybackService.class).setAction(action));refresh();}
    @Override public void onCreate(Bundle b){super.onCreate(b);bookUri=getIntent().getStringExtra("book_uri");if(bookUri==null)bookUri="";draw();if(getIntent().getBooleanExtra("start_tts",false)&&!bookUri.isEmpty())loadTts(bookUri,getIntent().getIntExtra("word_index",0));}
    @Override protected void onResume(){super.onResume();if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(AudioPlaybackService.ACTION_STATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,new IntentFilter(AudioPlaybackService.ACTION_STATE));handler.post(updater);}
    @Override protected void onPause(){handler.removeCallbacks(updater);try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onPause();}
    void draw(){root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BACKGROUND);root.setPadding(dp(16),dp(16),dp(16),dp(16));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);root.addView(top);
        TextView back=control("‹",v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(55),dp(53)));
        TextView header=label("Аудиокнига",23);header.setTypeface(android.graphics.Typeface.SERIF,1);header.setGravity(Gravity.CENTER);top.addView(header,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView more=control("⋮",v->options());top.addView(more,new LinearLayout.LayoutParams(dp(55),dp(53)));
        ScrollView sc=new ScrollView(this);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));LinearLayout content=new LinearLayout(this);content.setOrientation(1);sc.addView(content);
        TextView cover=label("♫",95);cover.setGravity(Gravity.CENTER);cover.setTextColor(ACCENT);cover.setBackground(bg(0xfffff9f0,25));LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,dp(215));cl.topMargin=dp(20);content.addView(cover,cl);
        title=label("Выберите аудиокнигу",22);title.setGravity(Gravity.CENTER);title.setTypeface(android.graphics.Typeface.SERIF,1);LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=dp(16);content.addView(title,tl);
        info=label("MP3 · M4A · M4B · OGG · TTS",14);info.setGravity(Gravity.CENTER);content.addView(info);
        spokenText=label("",15);spokenText.setGravity(Gravity.CENTER);spokenText.setPadding(dp(12),dp(12),dp(12),dp(12));spokenText.setBackground(bg(0xfffff9f0,15));content.addView(spokenText);
        progress=new SeekBar(this);progress.setMax(1000);LinearLayout.LayoutParams sl=new LinearLayout.LayoutParams(-1,-2);sl.topMargin=dp(28);content.addView(progress,sl);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean from){}public void onStartTrackingTouch(SeekBar b){dragging=true;}public void onStopTrackingTouch(SeekBar b){AudioPlaybackService s=AudioPlaybackService.instance;if(s!=null){Intent i=new Intent(AudioPlayerActivity.this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",(int)((long)b.getProgress()*s.getDuration()/1000));startService(i);}dragging=false;}});
        LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);content.addView(times);elapsed=label("00:00",13);times.addView(elapsed,new LinearLayout.LayoutParams(0,-2,1));duration=label("00:00",13);times.addView(duration);
        playRow=new LinearLayout(this);playRow.setGravity(Gravity.CENTER);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(105));pl.topMargin=dp(18);content.addView(playRow,pl);
        TextView prev=control("−15",v->jump(-15000));playRow.addView(prev,new LinearLayout.LayoutParams(dp(67),dp(62)));
        play=control("▶",v->{AudioPlaybackService s=AudioPlaybackService.instance;command(s!=null&&!s.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY);});play.setTextColor(-1);play.setBackground(bg(ACCENT,80));play.setTextSize(27);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(dp(80),dp(80));pp.leftMargin=dp(18);pp.rightMargin=dp(18);playRow.addView(play,pp);
        TextView next=control("+30",v->jump(30000));playRow.addView(next,new LinearLayout.LayoutParams(dp(67),dp(62)));
        LinearLayout extras=new LinearLayout(this);extras.setGravity(Gravity.CENTER);LinearLayout.LayoutParams el=new LinearLayout.LayoutParams(-1,-2);el.topMargin=dp(10);content.addView(extras,el);
        extras.addView(control("Скорость",v->speedMenu()),new LinearLayout.LayoutParams(0,-2,1));extras.addView(control("Таймер",v->sleepMenu()),new LinearLayout.LayoutParams(0,-2,1));extras.addView(control("Главы",v->chapters()),new LinearLayout.LayoutParams(0,-2,1));
        TextView importButton=control("＋  Добавить аудиофайлы",v->importFiles());LinearLayout.LayoutParams il=new LinearLayout.LayoutParams(-1,-2);il.topMargin=dp(18);content.addView(importButton,il);
        TextView ttsBtn=control("Озвучить текущую книгу",v->loadTts(bookUri,0));content.addView(ttsBtn);
        content.addView(control("Голос TTS и высота тона",v->voiceMenu()));
        TextView bookmarkBtn=control("☆  Аудиозакладка",v->bookmark());content.addView(bookmarkBtn);
        refresh();
    }
    void jump(int by){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",Math.max(0,s.getPosition()+(s.isTtsMode()? (by>0?1:-1):by)));startService(i);}
    void importFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FILE_PICK);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(req!=FILE_PICK||res!=RESULT_OK||data==null)return;ArrayList<String> uris=new ArrayList<>();if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)uris.add(data.getClipData().getItemAt(j).getUri().toString());}else if(data.getData()!=null)uris.add(data.getData().toString());for(String u:uris){try{getContentResolver().takePersistableUriPermission(Uri.parse(u),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}Collections.sort(uris);JSONArray arr=new JSONArray();for(String u:uris)arr.put(u);getSharedPreferences("reader",0).edit().putString("audio_playlist",arr.toString()).apply();if(!uris.isEmpty()){Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("uris",uris.toArray(new String[0]));i.putExtra("title","Аудиокнига");startService(i);}}
    void loadTts(String u,int index){if(u==null||u.isEmpty()){Toast.makeText(this,"Сначала откройте EPUB или FB2",Toast.LENGTH_SHORT).show();return;}title.setText("Загрузка текста…");Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS);i.putExtra("bookUri",u);i.putExtra("title","Озвучивание книги");i.putExtra("position",Math.max(0,index/22));startService(i);}
    void speedMenu(){final String[] labels={"0.5×","0.75×","1×","1.1×","1.25×","1.5×","1.75×","2×","2.5×","3×"};final float[] rates={.5f,.75f,1f,1.1f,1.25f,1.5f,1.75f,2f,2.5f,3f};new AlertDialog.Builder(this).setTitle("Скорость").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SPEED);i.putExtra("speed",rates[n]);startService(i);}).show();}
    void sleepMenu(){final int[] minutes={0,5,10,15,30,60};String[] labels={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};new AlertDialog.Builder(this).setTitle("Таймер сна").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SLEEP);i.putExtra("minutes",minutes[n]);startService(i);Toast.makeText(this,"Таймер сохранён",Toast.LENGTH_SHORT).show();}).show();}
    void voiceMenu(){String[] choices={"Обычный голос", "Ниже", "Выше", "Выбрать системный движок"};new AlertDialog.Builder(this).setTitle("Озвучивание текста").setItems(choices,(d,n)->{if(n==3){try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Настройки TTS недоступны",Toast.LENGTH_SHORT).show();}return;}Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.VOICE);i.putExtra("pitch",n==1?.8f:n==2?1.2f:1f);startService(i);}).show();}
    void chapters(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null){Toast.makeText(this,"Сначала выберите книгу",Toast.LENGTH_SHORT).show();return;}if(s.isTtsMode()){Toast.makeText(this,"Для TTS доступны переходы по фрагментам",Toast.LENGTH_SHORT).show();return;}int count=s.getChapterCount();if(count<2){Toast.makeText(this,"В аудиокниге одна часть",Toast.LENGTH_SHORT).show();return;}String[] labels=new String[count];for(int j=0;j<count;j++)labels[j]="Часть "+(j+1);new AlertDialog.Builder(this).setTitle("Части аудиокниги").setSingleChoiceItems(labels,s.getSegment(),(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER);i.putExtra("chapter",n);startService(i);d.dismiss();}).setNegativeButton("Отмена",null).show();}
    void bookmark(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;String key="audio_marks:"+s.getTitle();Set<String> marks=new HashSet<>(getSharedPreferences("reader",0).getStringSet(key,new HashSet<>()));marks.add(String.valueOf(s.getPosition()));getSharedPreferences("reader",0).edit().putStringSet(key,marks).apply();Toast.makeText(this,"Аудиозакладка сохранена",Toast.LENGTH_SHORT).show();}
    void options(){String[] choices={"Добавить аудиокнигу","Аудиозакладки","Скорость","Таймер сна","Остановить звук"};new AlertDialog.Builder(this).setItems(choices,(d,n)->{if(n==0)importFiles();if(n==1)showMarks();if(n==2)speedMenu();if(n==3)sleepMenu();if(n==4)command(AudioPlaybackService.STOP);}).show();}
    void showMarks(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Set<String> ms=getSharedPreferences("reader",0).getStringSet("audio_marks:"+s.getTitle(),new HashSet<>());ArrayList<Integer> vals=new ArrayList<>();for(String v:ms)try{vals.add(Integer.parseInt(v));}catch(Exception ignored){}Collections.sort(vals);String[] names=new String[vals.size()];for(int j=0;j<vals.size();j++)names[j]=s.isTtsMode()?"Фрагмент "+vals.get(j):format(vals.get(j));new AlertDialog.Builder(this).setTitle("Аудиозакладки").setItems(names,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",vals.get(n));startService(i);}).setPositiveButton("Закрыть",null).show();}
    String format(int ms){long sec=ms/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    void refresh(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null||title==null)return;title.setText(s.getTitle());info.setText(s.isTtsMode()?"Озвучивание · "+s.getSpeed()+"×":"Аудиокнига · "+s.getSpeed()+"×");play.setText(s.isPaused()?"▶":"Ⅱ");int d=s.getDuration();int p=s.getPosition();if(!dragging)progress.setProgress(d==0?0:(int)(1000L*p/d));elapsed.setText(s.isTtsMode()?"Фрагмент "+(p+1):format(p));duration.setText(s.isTtsMode()?"Всего "+d:format(d));spokenText.setVisibility(s.isTtsMode()?View.VISIBLE:View.GONE);if(s.isTtsMode())spokenText.setText(s.getCurrentText());}
}
