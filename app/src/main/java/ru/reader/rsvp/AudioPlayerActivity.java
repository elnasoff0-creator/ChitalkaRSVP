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
    private static final int FILE_PICK=301, FOLDER_PICK=302, COVER_PICK=303;
    LinearLayout root,playRow,chapterTicks; TextView title,info,elapsed,duration,play,spokenText,wholeProgress,speedAction,timerAction; SeekBar progress, chapterProgress; TextView chapterElapsed, chapterDuration, chapterPicker; android.widget.ImageView coverImage;
    Handler handler=new Handler(Looper.getMainLooper()); boolean dragging=false;String bookUri=""; AudioLibrary.Book selected;
    final int BACKGROUND=0xfff3e7d5, TEXT=0xff30221e, ACCENT=0xff94583b;
    BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refresh();}};
    Runnable updater=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);return v;}
    TextView control(String text,View.OnClickListener action){TextView t=label(text,19);t.setGravity(Gravity.CENTER);t.setBackground(bg(0xfffff8ef,20));t.setOnClickListener(action);t.setPadding(dp(10),dp(12),dp(10),dp(12));return t;}
    TextView topAction(int icon, String description, View.OnClickListener action){
        TextView view=control("",action);
        view.setContentDescription(description);
        android.graphics.drawable.Drawable drawable=getDrawable(icon);
        if(drawable!=null){drawable=drawable.mutate();drawable.setTint(TEXT);drawable.setBounds(0,0,dp(23),dp(23));view.setCompoundDrawables(drawable,null,null,null);}
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(13),0,0,0);
        return view;
    }
    void command(String action){startService(new Intent(this,AudioPlaybackService.class).setAction(action));handler.postDelayed(this::refresh,150);}
    @Override public void onCreate(Bundle b){super.onCreate(b);bookUri=getIntent().getStringExtra("book_uri");if(bookUri==null)bookUri="";String id=getIntent().getStringExtra("audio_book_id");if(id!=null)for(AudioLibrary.Book item:AudioLibrary.load(this))if(id.equals(item.id)){selected=item;break;}draw();String audioAction=getIntent().getStringExtra("audio_action");if(audioAction!=null)handler.post(()->{switch(audioAction){case "import":importFolder();break;case "tts":loadTts(bookUri,0);break;case "voice":voiceMenu();break;case "marks":showMarks();break;case "cover":chooseCover();break;case "sleep":sleepMenu();break;case "speed":speedMenu();break;}});if(selected!=null&&(AudioPlaybackService.instance==null||!selected.id.equals(AudioPlaybackService.instance.getBookId())))openSelected(selected);if(getIntent().getBooleanExtra("start_tts",false)&&!bookUri.isEmpty())loadTts(bookUri,getIntent().getIntExtra("word_index",0));}
    @Override protected void onResume(){super.onResume();if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(AudioPlaybackService.ACTION_STATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,new IntentFilter(AudioPlaybackService.ACTION_STATE));handler.post(updater);}
    @Override protected void onPause(){handler.removeCallbacks(updater);try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onPause();}
    void draw(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(12),dp(29),dp(12),dp(10));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));
        top.addView(topAction(R.drawable.ic_back,"Назад",v->finish()),new LinearLayout.LayoutParams(dp(44),dp(46)));
        chapterPicker=topAction(R.drawable.ic_library,"Выбрать главу",v->chapters());
        top.addView(chapterPicker,new LinearLayout.LayoutParams(0,dp(46),1));
        speedAction=topAction(R.drawable.ic_audio_speed,"Скорость",v->speedMenu());
        top.addView(speedAction,new LinearLayout.LayoutParams(dp(48),dp(46)));
        timerAction=topAction(R.drawable.ic_audio_timer,"Таймер сна",v->sleepMenu());
        top.addView(timerAction,new LinearLayout.LayoutParams(dp(48),dp(46)));
        top.addView(control("+",v->importFolder()),new LinearLayout.LayoutParams(dp(45),dp(46)));
        top.addView(topAction(R.drawable.ic_settings,"Аудионастройки",v->openAudioSettings()),new LinearLayout.LayoutParams(dp(48),dp(46)));
        title=label("Аудиокнига",21);title.setGravity(Gravity.CENTER);title.setTypeface(android.graphics.Typeface.SERIF,1);
        title.setMaxLines(2);title.setOnClickListener(v->selectBook());
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=dp(10);root.addView(title,tl);
        info=label("",14);info.setGravity(Gravity.CENTER);root.addView(info);
        spokenText=label("",14);spokenText.setGravity(Gravity.CENTER);root.addView(spokenText);
        android.widget.FrameLayout coverArea=new android.widget.FrameLayout(this);
        coverArea.setBackground(bg(0xfffff8ef,26));
        LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(-1,0,1);
        cap.topMargin=dp(12);cap.bottomMargin=dp(12);root.addView(coverArea,cap);
        coverImage=new ImageView(this);coverImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        coverArea.addView(coverImage,new android.widget.FrameLayout.LayoutParams(-1,-1));
        updateCover();
        wholeProgress=label("Общий прогресс: 0%",15);wholeProgress.setGravity(Gravity.CENTER);root.addView(wholeProgress);
        chapterTicks=new LinearLayout(this);chapterTicks.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(chapterTicks,new LinearLayout.LayoutParams(-1,dp(16)));drawTicks();
        progress=new SeekBar(this);progress.setMax(10000);root.addView(progress);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean from){}
            public void onStartTrackingTouch(SeekBar b){dragging=true;}
            public void onStopTrackingTouch(SeekBar b){AudioPlaybackService service=AudioPlaybackService.instance;
                if(service!=null){int n=Math.max(1,service.getChapterCount());double section=b.getProgress()/10000.0*n;
                int next=Math.min(n-1,(int)section);double fraction=section-next;
                if(next==service.getSegment())seekPosition((int)(fraction*service.getDuration()));
                else{Intent i=new Intent(AudioPlayerActivity.this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER).putExtra("chapter",next).putExtra("fraction",fraction);startService(i);}}
                dragging=false;refresh();}
        });
        playRow=new LinearLayout(this);playRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(92));pl.topMargin=dp(14);root.addView(playRow,pl);
        TextView prev=control("|◀",v->changeChapter(-1));playRow.addView(prev,new LinearLayout.LayoutParams(0,dp(62),1));
        TextView rewind=control("−30",v->jump(-30000));playRow.addView(rewind,new LinearLayout.LayoutParams(0,dp(62),1));
        play=control("▶",v->{AudioPlaybackService service=AudioPlaybackService.instance;command(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY);});
        play.setTextColor(Color.WHITE);play.setBackground(bg(ACCENT,99));play.setTextSize(28);
        playRow.addView(play,new LinearLayout.LayoutParams(dp(80),dp(80)));
        TextView forward=control("+30",v->jump(30000));playRow.addView(forward,new LinearLayout.LayoutParams(0,dp(62),1));
        TextView next=control("▶|",v->changeChapter(1));playRow.addView(next,new LinearLayout.LayoutParams(0,dp(62),1));
        TextView chapterLabel=label("Прогресс главы",14);chapterLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cl=new LinearLayout.LayoutParams(-1,-2);cl.topMargin=dp(10);root.addView(chapterLabel,cl);
        chapterProgress=new SeekBar(this);chapterProgress.setMax(10000);root.addView(chapterProgress);
        chapterProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int val,boolean from){}
            public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            public void onStopTrackingTouch(SeekBar bar){AudioPlaybackService service=AudioPlaybackService.instance;
                if(service!=null)seekPosition((int)((long)bar.getProgress()*service.getDuration()/10000));dragging=false;refresh();}
        });
        LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);root.addView(times);
        chapterElapsed=label("00:00:00",13);times.addView(chapterElapsed,new LinearLayout.LayoutParams(0,-2,1));
        chapterDuration=label("00:00:00",13);times.addView(chapterDuration);
        elapsed=chapterElapsed;duration=chapterDuration;
        refresh();
    }
    void chooseCover(){if(selected==null){Toast.makeText(this,"Выберите аудиокнигу в библиотеке",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,COVER_PICK);}
    void updateCover(){if(coverImage==null)return;String id=selected==null?"":selected.id;String uri=getSharedPreferences("reader",0).getString("audio_cover:"+id,"");if(uri.isEmpty()){coverImage.setImageDrawable(null);coverImage.setVisibility(View.INVISIBLE);return;}try{coverImage.setImageURI(Uri.parse(uri));coverImage.setVisibility(View.VISIBLE);}catch(Exception e){coverImage.setVisibility(View.INVISIBLE);}}
    void drawTicks(){if(chapterTicks==null)return;chapterTicks.removeAllViews();int n=selected==null?1:Math.max(1,selected.uris.size());for(int j=0;j<Math.min(n,100);j++){View tick=new View(this);tick.setBackgroundColor(ACCENT);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(j%5==0?14:7),1);lp.rightMargin=dp(1);chapterTicks.addView(tick,lp);final int chapter=j;tick.setOnClickListener(v->goChapter(chapter));}}
    void selectBook(){ArrayList<AudioLibrary.Book> all=AudioLibrary.load(this);if(all.isEmpty()){importFolder();return;}String[] names=new String[all.size()];for(int k=0;k<all.size();k++)names[k]=all.get(k).name;new AlertDialog.Builder(this).setTitle("Выбрать аудиокнигу").setItems(names,(d,n)->{openSelected(all.get(n));draw();}).setNegativeButton("Отмена",null).show();}
    void seekPosition(int pos){startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK).putExtra("position",pos));}
    void changeChapter(int delta){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null||s.isTtsMode())return;goChapter(Math.max(0,Math.min(s.getChapterCount()-1,s.getSegment()+delta)));}
    void goChapter(int n){startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER).putExtra("chapter",n));}
    void importFolder(){new AlertDialog.Builder(this).setTitle("Добавление аудиокниг").setItems(new String[]{"Выбранная папка — одна книга","Сканировать вложенные папки как отдельные книги"},(d,n)->{getSharedPreferences("reader",0).edit().putBoolean("audio_recursive",n==1).apply();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FOLDER_PICK);}).show();}
    void openAudioSettings(){startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true).putExtra("audio_book_id",selected==null?"":selected.id));}
    void openSelected(AudioLibrary.Book b){selected=b;Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("title",b.name);i.putExtra("bookId",b.id);i.putExtra("uris",b.uris.toArray(new String[0]));i.putExtra("chapters",b.chapters.toArray(new String[0]));i.putExtra("autoplay",true);startService(i);drawTicks();updateCover();}
    void jump(int by){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",Math.max(0,s.getPosition()+(s.isTtsMode()? (by>0?1:-1):by)));startService(i);}
    void importFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FILE_PICK);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;
        if(req==COVER_PICK&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(selected!=null)getSharedPreferences("reader",0).edit().putString("audio_cover:"+selected.id,data.getData().toString()).apply();updateCover();return;}
        if(req==FOLDER_PICK&&data.getData()!=null){Uri tree=data.getData();try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}final boolean recursive=getSharedPreferences("reader",0).getBoolean("audio_recursive",true);new Thread(()->{try{ArrayList<AudioLibrary.Book> books=AudioLibrary.importTree(this,tree,recursive);AudioLibrary.merge(this,books);runOnUiThread(()->{if(books.isEmpty())Toast.makeText(this,"Аудиофайлы не найдены",Toast.LENGTH_LONG).show();else{openSelected(books.get(0));draw();Toast.makeText(this,"Добавлено книг: "+books.size(),Toast.LENGTH_LONG).show();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Ошибка папки: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();return;}
        if(req!=FILE_PICK)return;ArrayList<String> uris=new ArrayList<>();if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)uris.add(data.getClipData().getItemAt(j).getUri().toString());}else if(data.getData()!=null)uris.add(data.getData().toString());for(String u:uris){try{getContentResolver().takePersistableUriPermission(Uri.parse(u),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}Collections.sort(uris);JSONArray arr=new JSONArray();for(String u:uris)arr.put(u);getSharedPreferences("reader",0).edit().putString("audio_playlist",arr.toString()).apply();if(!uris.isEmpty()){Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("uris",uris.toArray(new String[0]));i.putExtra("title","Аудиокнига");startService(i);}}
    void loadTts(String u,int index){if(u==null||u.isEmpty()){Toast.makeText(this,"Сначала откройте EPUB или FB2",Toast.LENGTH_SHORT).show();return;}title.setText("Загрузка текста…");Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS);i.putExtra("bookUri",u);i.putExtra("title","Озвучивание книги");i.putExtra("position",Math.max(0,index/22));startService(i);}
    void speedMenu(){final String[] labels={"0.5×","0.75×","1×","1.1×","1.25×","1.5×","1.75×","2×","2.5×","3×"};final float[] rates={.5f,.75f,1f,1.1f,1.25f,1.5f,1.75f,2f,2.5f,3f};new AlertDialog.Builder(this).setTitle("Скорость").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SPEED);i.putExtra("speed",rates[n]);startService(i);}).show();}
    void sleepMenu(){final int[] minutes={0,5,10,15,30,60};String[] labels={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};new AlertDialog.Builder(this).setTitle("Таймер сна").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SLEEP);i.putExtra("minutes",minutes[n]);startService(i);Toast.makeText(this,"Таймер сохранён",Toast.LENGTH_SHORT).show();}).show();}
    void voiceMenu(){String[] choices={"Обычный голос", "Ниже", "Выше", "Выбрать системный движок"};new AlertDialog.Builder(this).setTitle("Озвучивание текста").setItems(choices,(d,n)->{if(n==3){try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Настройки TTS недоступны",Toast.LENGTH_SHORT).show();}return;}Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.VOICE);i.putExtra("pitch",n==1?.8f:n==2?1.2f:1f);startService(i);}).show();}
    void chapters(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null){Toast.makeText(this,"Сначала выберите книгу",Toast.LENGTH_SHORT).show();return;}if(s.isTtsMode()){Toast.makeText(this,"Для TTS доступны переходы по фрагментам",Toast.LENGTH_SHORT).show();return;}int count=s.getChapterCount();if(count<1)return;String[] labels=new String[count];for(int j=0;j<count;j++)labels[j]=s.getChapterName(j);new AlertDialog.Builder(this).setTitle("Части аудиокниги").setSingleChoiceItems(labels,s.getSegment(),(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER);i.putExtra("chapter",n);startService(i);d.dismiss();}).setNegativeButton("Отмена",null).show();}
    void bookmark(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;String key="audio_marks:"+s.getTitle();Set<String> marks=new HashSet<>(getSharedPreferences("reader",0).getStringSet(key,new HashSet<>()));marks.add(String.valueOf(s.getPosition()));getSharedPreferences("reader",0).edit().putStringSet(key,marks).apply();Toast.makeText(this,"Аудиозакладка сохранена",Toast.LENGTH_SHORT).show();}
    void options(){String[] choices={"Добавить аудиокнигу","Аудиозакладки","Скорость","Таймер сна","Остановить звук"};new AlertDialog.Builder(this).setItems(choices,(d,n)->{if(n==0)importFiles();if(n==1)showMarks();if(n==2)speedMenu();if(n==3)sleepMenu();if(n==4)command(AudioPlaybackService.STOP);}).show();}
    void showMarks(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Set<String> ms=getSharedPreferences("reader",0).getStringSet("audio_marks:"+s.getTitle(),new HashSet<>());ArrayList<Integer> vals=new ArrayList<>();for(String v:ms)try{vals.add(Integer.parseInt(v));}catch(Exception ignored){}Collections.sort(vals);String[] names=new String[vals.size()];for(int j=0;j<vals.size();j++)names[j]=s.isTtsMode()?"Фрагмент "+vals.get(j):format(vals.get(j));new AlertDialog.Builder(this).setTitle("Аудиозакладки").setItems(names,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",vals.get(n));startService(i);}).setPositiveButton("Закрыть",null).show();}
    String format(int ms){long sec=ms/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    void refresh(){
        AudioPlaybackService service=AudioPlaybackService.instance;
        if(title==null||play==null)return;
        if(service==null){play.setText("▶");return;}
        play.setText(service.isPaused()?"▶":"Ⅱ");
        if(speedAction!=null)speedAction.setContentDescription("Скорость "+service.getSpeed()+"×");
        if(timerAction!=null)timerAction.setAlpha(service.getSleepRemainingMillis()>0?1f:.70f);
        if(selected==null && !service.getBookId().isEmpty())for(AudioLibrary.Book item:AudioLibrary.load(this))if(item.id.equals(service.getBookId())){selected=item;updateCover();drawTicks();break;}
        title.setText(service.getTitle());
        info.setText(service.isTtsMode()?"Озвучивание · "+service.getSpeed()+"×":"Аудиокнига · "+service.getSpeed()+"×");
        if(chapterPicker!=null)chapterPicker.setContentDescription("Глава "+(service.getSegment()+1)+". Выбрать главу");
        int d=service.getDuration(), position=service.getPosition(), segments=Math.max(1,service.getChapterCount());
        int whole=(int)((service.getSegment()+(d>0?(double)position/d:0))*10000/segments);
        if(!dragging){progress.setProgress(whole);chapterProgress.setProgress(d>0?(int)((long)position*10000/d):0);}
        wholeProgress.setText("Прослушано: "+(whole/100)+"% · Глава "+(service.getSegment()+1)+" из "+segments);
        chapterElapsed.setText(service.isTtsMode()?"Фрагмент "+(position+1):format(position));
        chapterDuration.setText(service.isTtsMode()?"Всего "+d:format(d));
        spokenText.setVisibility(service.isTtsMode()?View.VISIBLE:View.GONE);
        if(service.isTtsMode())spokenText.setText(service.getCurrentText());
    }
}
