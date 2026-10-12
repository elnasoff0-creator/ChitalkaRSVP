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
    LinearLayout root,playRow; TextView title,info,elapsed,duration,spokenText,wholeProgress,speedAction,timerAction; SeekBar progress, totalProgress; TextView chapterElapsed, chapterDuration, chapterPicker, overallTime, overallPercent, overallRemaining, speedValue; android.widget.ImageView coverImage, playIcon;
    Handler handler=new Handler(Looper.getMainLooper()); boolean dragging=false, draggingTotal=false;String bookUri=""; AudioLibrary.Book selected;
    final int BACKGROUND=0xfff3e7d5, TEXT=0xff30221e, ACCENT=0xff94583b;
    BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refresh();}};
    Runnable updater=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);return v;}
    TextView control(String text,View.OnClickListener action){TextView t=label(text,19);t.setGravity(Gravity.CENTER);t.setBackground(bg(0xfffff8ef,13));t.setOnClickListener(action);t.setPadding(dp(10),dp(12),dp(10),dp(12));return t;}
    TextView topAction(int icon, String description, View.OnClickListener action){
        TextView view=control("",action);
        view.setContentDescription(description);
        android.graphics.drawable.Drawable drawable=getDrawable(icon);
        if(drawable!=null){drawable=drawable.mutate();drawable.setTint(TEXT);drawable.setBounds(0,0,dp(23),dp(23));view.setCompoundDrawables(drawable,null,null,null);}
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(11),0,0,0);
        return view;
    }
    LinearLayout bottomIconControl(int iconRes,String hint,boolean primary,int iconSizeDp,View.OnClickListener action){
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // Identical vertical slots for every button: no baseline-related vertical shift.
        FrameLayout buttonSlot=new FrameLayout(this);
        cell.addView(buttonSlot,new LinearLayout.LayoutParams(-1,dp(66)));
        FrameLayout holder=new FrameLayout(this);
        GradientDrawable buttonBackground;
        if(primary){
            buttonBackground=new GradientDrawable();
            buttonBackground.setShape(GradientDrawable.RECTANGLE);
            buttonBackground.setCornerRadius(dp(16));
            buttonBackground.setColor(ACCENT);
        }else{
            buttonBackground=bg(0xfffff8ef,13);
        }
        holder.setBackground(buttonBackground);
        holder.setClipToOutline(false);
        holder.setOnClickListener(action);
        holder.setContentDescription(primary?"Воспроизвести или поставить на паузу":hint);
        int holderSize=dp(43);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(holderSize,holderSize,Gravity.CENTER);
        buttonSlot.addView(holder,hp);
        ImageView icon=new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(primary?Color.WHITE:TEXT);
        FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(dp(iconSizeDp),dp(iconSizeDp),Gravity.CENTER);
        holder.addView(icon,ip);
        TextView cap=label(hint==null?"":hint,10);
        cap.setSingleLine(true);
        cap.setGravity(Gravity.CENTER);
        cell.addView(cap,new LinearLayout.LayoutParams(-1,dp(20)));
        if(primary)playIcon=icon;
        return cell;
    }
    void command(String action){startService(new Intent(this,AudioPlaybackService.class).setAction(action));handler.postDelayed(this::refresh,150);}
    @Override public void onCreate(Bundle b){super.onCreate(b);bookUri=getIntent().getStringExtra("book_uri");if(bookUri==null||bookUri.isEmpty())bookUri=getSharedPreferences("reader",0).getString("uri","");String id=getIntent().getStringExtra("audio_book_id");if(id!=null)for(AudioLibrary.Book item:AudioLibrary.load(this))if(id.equals(item.id)){selected=item;break;}draw();String audioAction=getIntent().getStringExtra("audio_action");if(audioAction!=null)handler.post(()->{switch(audioAction){case "import":importFolder();break;case "tts":loadTts(bookUri,getSharedPreferences("reader",0).getInt("index",0));break;case "voice":voiceMenu();break;case "marks":showMarks();break;case "cover":chooseCover();break;case "sleep":sleepMenu();break;case "speed":speedMenu();break;}});if(selected!=null&&(AudioPlaybackService.instance==null||AudioPlaybackService.instance.isTtsMode()||!selected.id.equals(AudioPlaybackService.instance.getBookId())))openSelected(selected);if(getIntent().getBooleanExtra("start_tts",false)&&!bookUri.isEmpty())loadTts(bookUri,getIntent().getIntExtra("word_index",0));}
    @Override protected void onResume(){super.onResume();
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},427);
if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,new IntentFilter(AudioPlaybackService.ACTION_STATE),Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,new IntentFilter(AudioPlaybackService.ACTION_STATE));handler.post(updater);}
    @Override protected void onPause(){handler.removeCallbacks(updater);try{unregisterReceiver(receiver);}catch(Exception ignored){}super.onPause();}
    void draw(){
        root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(BACKGROUND);
        root.setPadding(dp(12),dp(29),dp(12),dp(10));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(top,new LinearLayout.LayoutParams(-1,dp(54)));
        top.addView(topAction(R.drawable.ic_back,"Назад",v->finish()),new LinearLayout.LayoutParams(dp(44),dp(46)));
        chapterPicker=topAction(R.drawable.ic_audio_chapters,"Выбрать главу",v->chapters());
        top.addView(chapterPicker,new LinearLayout.LayoutParams(dp(48),dp(50)));
        LinearLayout speedGroup=new LinearLayout(this);speedGroup.setOrientation(1);speedGroup.setGravity(Gravity.CENTER);
        speedGroup.setBackground(bg(0xfffff8ef,15));
        android.widget.ImageView speedIcon=new android.widget.ImageView(this);
        speedIcon.setImageResource(R.drawable.ic_audio_speed);speedIcon.setColorFilter(TEXT);
        speedGroup.addView(speedIcon,new LinearLayout.LayoutParams(dp(25),dp(25)));
        speedValue=label("1×",12);speedValue.setGravity(Gravity.CENTER);
        speedGroup.addView(speedValue,new LinearLayout.LayoutParams(-1,dp(17)));
        speedGroup.setOnClickListener(v->speedMenu());speedGroup.setContentDescription("Скорость");
        top.addView(speedGroup,new LinearLayout.LayoutParams(dp(49),dp(51)));
        timerAction=topAction(R.drawable.ic_audio_timer,"Таймер сна",v->sleepMenu());
        top.addView(timerAction,new LinearLayout.LayoutParams(dp(46),dp(50)));
        top.addView(control("+",v->new AlertDialog.Builder(this).setTitle("Добавить аудиокнигу").setItems(new String[]{"Из папки","Импорт по ссылке"},(dialog,index)->{if(index==0)importFolder();else startActivity(new Intent(this,UrlImportActivity.class));}).show()),new LinearLayout.LayoutParams(dp(42),dp(50)));
        top.addView(topAction(R.drawable.ic_settings,"Аудионастройки",v->openAudioSettings()),new LinearLayout.LayoutParams(dp(46),dp(50)));
        title=label("Аудиокнига",21);title.setGravity(Gravity.CENTER);title.setTypeface(android.graphics.Typeface.SERIF,1);
        title.setMaxLines(2);title.setOnClickListener(v->selectBook());
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=dp(10);root.addView(title,tl);
        info=label("",14);info.setGravity(Gravity.CENTER);root.addView(info);
        spokenText=label("",14);spokenText.setGravity(Gravity.CENTER);root.addView(spokenText);
        TextView overallLabel=label("Вся аудиокнига",14);
        LinearLayout.LayoutParams overallLabelLp=new LinearLayout.LayoutParams(-1,-2);
        overallLabelLp.topMargin=dp(13);root.addView(overallLabel,overallLabelLp);
        totalProgress=new SeekBar(this);totalProgress.setMax(10000);root.addView(totalProgress);
        totalProgress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean from){}
            public void onStartTrackingTouch(SeekBar bar){draggingTotal=true;}
            public void onStopTrackingTouch(SeekBar bar){
                AudioPlaybackService service=AudioPlaybackService.instance;
                if(service!=null && service.getTotalDuration()>0)service.seekTotal(service.getTotalDuration()*bar.getProgress()/10000L);
                draggingTotal=false;refresh();
            }
        });
        LinearLayout overallTimes=new LinearLayout(this);
        overallTimes.setGravity(Gravity.CENTER_VERTICAL);root.addView(overallTimes);
        overallTime=label("— / —",12);overallTimes.addView(overallTime,new LinearLayout.LayoutParams(0,-2,1));
        overallPercent=label("—",12);overallPercent.setGravity(Gravity.CENTER);
        overallTimes.addView(overallPercent,new LinearLayout.LayoutParams(dp(55),-2));
        overallRemaining=label("—",12);overallRemaining.setGravity(Gravity.RIGHT);
        overallTimes.addView(overallRemaining,new LinearLayout.LayoutParams(0,-2,1));
        android.widget.FrameLayout coverArea=new android.widget.FrameLayout(this);
        coverArea.setBackground(bg(0xfffff8ef,26));
        LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(-1,0,1);
        cap.topMargin=dp(12);cap.bottomMargin=dp(12);root.addView(coverArea,cap);
        coverArea.setOnLongClickListener(v->{chooseCover();return true;});
        coverArea.setOnClickListener(v->chooseCover());
        coverImage=new ImageView(this);coverImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        coverArea.addView(coverImage,new android.widget.FrameLayout.LayoutParams(-1,-1));
        updateCover();
        wholeProgress=label("Текущая глава · 0%",15);wholeProgress.setGravity(Gravity.CENTER);root.addView(wholeProgress);
        progress=new SeekBar(this);progress.setMax(10000);root.addView(progress);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean from){}
            public void onStartTrackingTouch(SeekBar b){dragging=true;}
            public void onStopTrackingTouch(SeekBar b){AudioPlaybackService service=AudioPlaybackService.instance;
                if(service!=null && service.getDuration()>0)
                    seekPosition((int)((long)b.getProgress()*service.getDuration()/10000));
                dragging=false;refresh();}
        });
        LinearLayout times=new LinearLayout(this);times.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timesLp=new LinearLayout.LayoutParams(-1,-2);timesLp.topMargin=dp(4);root.addView(times,timesLp);
        chapterElapsed=label("00:00:00",13);times.addView(chapterElapsed,new LinearLayout.LayoutParams(0,-2,1));
        chapterDuration=label("00:00:00",13);chapterDuration.setGravity(Gravity.RIGHT);times.addView(chapterDuration,new LinearLayout.LayoutParams(0,-2,1));
        elapsed=chapterElapsed;duration=chapterDuration;
        playRow=new LinearLayout(this);playRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(90));pl.topMargin=dp(10);root.addView(playRow,pl);
        String[] hints={"Глава","1 мин","10 сек","","10 сек","1 мин","Глава"};
        int[] icons={R.drawable.ic_prev_chapter,R.drawable.ic_rewind_1m,R.drawable.ic_rewind_10s,R.drawable.ic_play,R.drawable.ic_forward_10s,R.drawable.ic_forward_1m,R.drawable.ic_next_chapter};
        final int[] offsets={0,-60000,-10000,0,10000,60000,0};
        for(int n=0;n<icons.length;n++){
            final int buttonIndex=n;
            LinearLayout cell=bottomIconControl(icons[n],hints[n],n==3,n==3?26:22,v->{
                if(buttonIndex==0)changeChapter(-1);
                else if(buttonIndex==6)changeChapter(1);
                else if(buttonIndex==3){AudioPlaybackService service=AudioPlaybackService.instance;command(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY);}
                else jump(offsets[buttonIndex]);
            });
            playRow.addView(cell,new LinearLayout.LayoutParams(0,-1,n==3?1.32f:1f));
        }
        refresh();
    }
    void chooseCover(){if(selected==null){Toast.makeText(this,"Выберите аудиокнигу в библиотеке",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,COVER_PICK);}
    void updateCover(){if(coverImage==null)return;String id=selected==null?"":selected.id;String uri=getSharedPreferences("reader",0).getString("audio_cover:"+id,"");if(uri.isEmpty()){coverImage.setImageDrawable(null);coverImage.setVisibility(View.INVISIBLE);return;}try{coverImage.setImageURI(Uri.parse(uri));coverImage.setVisibility(View.VISIBLE);}catch(Exception e){coverImage.setVisibility(View.INVISIBLE);}}
    void selectBook(){ArrayList<AudioLibrary.Book> all=AudioLibrary.load(this);if(all.isEmpty()){importFolder();return;}String[] names=new String[all.size()];for(int k=0;k<all.size();k++)names[k]=all.get(k).name;new AlertDialog.Builder(this).setTitle("Выбрать аудиокнигу (удерживайте обложку для изменения)").setItems(names,(d,n)->{openSelected(all.get(n));draw();}).setNeutralButton("Обложка выбранной книги",(d,w)->chooseCover()).setNegativeButton("Отмена",null).show();}
    void seekPosition(int pos){startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK).putExtra("position",pos));}
    void changeChapter(int delta){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null||s.isTtsMode())return;goChapter(Math.max(0,Math.min(s.getChapterCount()-1,s.getSegment()+delta)));}
    void goChapter(int n){startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER).putExtra("chapter",n));}
    void importFolder(){new AlertDialog.Builder(this).setTitle("Добавление аудиокниг").setItems(new String[]{"Выбранная папка — одна книга","Сканировать вложенные папки как отдельные книги"},(d,n)->{getSharedPreferences("reader",0).edit().putBoolean("audio_recursive",n==1).apply();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FOLDER_PICK);}).show();}
    void openAudioSettings(){startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true).putExtra("audio_book_id",selected==null?"":selected.id));}
    void openSelected(AudioLibrary.Book b){selected=b;Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("title",b.name);i.putExtra("bookId",b.id);i.putExtra("uris",b.uris.toArray(new String[0]));i.putExtra("chapters",b.chapters.toArray(new String[0]));i.putExtra("autoplay",false);startService(i);updateCover();}
    void jump(int by){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",Math.max(0,s.getPosition()+(s.isTtsMode()? (by>0?1:-1):by)));startService(i);}
    void importFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FILE_PICK);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;
        if(req==COVER_PICK&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(selected!=null)getSharedPreferences("reader",0).edit().putString("audio_cover:"+selected.id,data.getData().toString()).apply();updateCover();return;}
        if(req==FOLDER_PICK&&data.getData()!=null){Uri tree=data.getData();try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}final boolean recursive=getSharedPreferences("reader",0).getBoolean("audio_recursive",true);new Thread(()->{try{ArrayList<AudioLibrary.Book> books=AudioLibrary.importTree(this,tree,recursive);AudioLibrary.merge(this,books);runOnUiThread(()->{if(books.isEmpty())Toast.makeText(this,"Аудиофайлы не найдены",Toast.LENGTH_LONG).show();else{openSelected(books.get(0));draw();Toast.makeText(this,"Добавлено книг: "+books.size(),Toast.LENGTH_LONG).show();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Ошибка папки: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();return;}
        if(req!=FILE_PICK)return;ArrayList<String> uris=new ArrayList<>();if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)uris.add(data.getClipData().getItemAt(j).getUri().toString());}else if(data.getData()!=null)uris.add(data.getData().toString());for(String u:uris){try{getContentResolver().takePersistableUriPermission(Uri.parse(u),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}Collections.sort(uris);JSONArray arr=new JSONArray();for(String u:uris)arr.put(u);getSharedPreferences("reader",0).edit().putString("audio_playlist",arr.toString()).apply();if(!uris.isEmpty()){Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("uris",uris.toArray(new String[0]));i.putExtra("title","Аудиокнига");startService(i);}}
    void loadTts(String u,int index){if(u==null||u.isEmpty()){Toast.makeText(this,"Сначала откройте EPUB или FB2",Toast.LENGTH_SHORT).show();return;}title.setText("Загрузка текста…");Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS);i.putExtra("bookUri",u);i.putExtra("title","Озвучивание книги");i.putExtra("word_index",Math.max(0,index));startService(i);}
    void speedMenu(){final String[] labels={"0.5×","0.75×","1×","1.1×","1.25×","1.5×","1.75×","2×","2.5×","3×"};final float[] rates={.5f,.75f,1f,1.1f,1.25f,1.5f,1.75f,2f,2.5f,3f};new AlertDialog.Builder(this).setTitle("Скорость").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SPEED);i.putExtra("speed",rates[n]);startService(i);}).show();}
    void sleepMenu(){final int[] minutes={0,5,10,15,30,60};String[] labels={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};new AlertDialog.Builder(this).setTitle("Таймер сна").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SLEEP);i.putExtra("minutes",minutes[n]);startService(i);Toast.makeText(this,"Таймер сохранён",Toast.LENGTH_SHORT).show();}).show();}
    void voiceMenu(){String[] choices={"Обычный голос", "Ниже", "Выше", "Выбрать системный движок"};new AlertDialog.Builder(this).setTitle("Озвучивание текста").setItems(choices,(d,n)->{if(n==3){try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Настройки TTS недоступны",Toast.LENGTH_SHORT).show();}return;}Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.VOICE);i.putExtra("pitch",n==1?.8f:n==2?1.2f:1f);startService(i);}).show();}
    void chapters(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null){Toast.makeText(this,"Сначала выберите книгу",Toast.LENGTH_SHORT).show();return;}if(s.isTtsMode()){Toast.makeText(this,"Для TTS доступны переходы по фрагментам",Toast.LENGTH_SHORT).show();return;}int count=s.getChapterCount();if(count<1)return;String[] labels=new String[count];for(int j=0;j<count;j++)labels[j]=s.getChapterName(j);new AlertDialog.Builder(this).setTitle("Части аудиокниги").setSingleChoiceItems(labels,s.getSegment(),(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER);i.putExtra("chapter",n);startService(i);d.dismiss();}).setNegativeButton("Отмена",null).show();}
    void bookmark(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;String key="audio_marks:"+s.getTitle();Set<String> marks=new HashSet<>(getSharedPreferences("reader",0).getStringSet(key,new HashSet<>()));marks.add(String.valueOf(s.getPosition()));getSharedPreferences("reader",0).edit().putStringSet(key,marks).apply();Toast.makeText(this,"Аудиозакладка сохранена",Toast.LENGTH_SHORT).show();}
    void options(){String[] choices={"Добавить аудиокнигу","Аудиозакладки","Скорость","Таймер сна","Остановить звук"};new AlertDialog.Builder(this).setItems(choices,(d,n)->{if(n==0)importFiles();if(n==1)showMarks();if(n==2)speedMenu();if(n==3)sleepMenu();if(n==4)command(AudioPlaybackService.STOP);}).show();}
    void showMarks(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Set<String> ms=getSharedPreferences("reader",0).getStringSet("audio_marks:"+s.getTitle(),new HashSet<>());ArrayList<Integer> vals=new ArrayList<>();for(String v:ms)try{vals.add(Integer.parseInt(v));}catch(Exception ignored){}Collections.sort(vals);String[] names=new String[vals.size()];for(int j=0;j<vals.size();j++)names[j]=s.isTtsMode()?"Фрагмент "+vals.get(j):format(vals.get(j));new AlertDialog.Builder(this).setTitle("Аудиозакладки").setItems(names,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",vals.get(n));startService(i);}).setPositiveButton("Закрыть",null).show();}
    String formatLong(long ms){long sec=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    String format(int ms){long sec=ms/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    void refresh(){
        AudioPlaybackService service=AudioPlaybackService.instance;
        if(title==null||playIcon==null)return;
        if(service==null){playIcon.setImageResource(R.drawable.ic_play);playIcon.setColorFilter(Color.WHITE);return;}
        playIcon.setImageResource(service.isPaused()?R.drawable.ic_play:R.drawable.ic_pause);
        playIcon.setColorFilter(Color.WHITE);
        if(speedValue!=null){float rate=service.getSpeed();speedValue.setText((rate==(int)rate?Integer.toString((int)rate):Float.toString(rate))+"×");}
        if(timerAction!=null)timerAction.setAlpha(service.getSleepRemainingMillis()>0?1f:.70f);
        if(selected==null && !service.getBookId().isEmpty())for(AudioLibrary.Book item:AudioLibrary.load(this))if(item.id.equals(service.getBookId())){selected=item;updateCover();break;}
        title.setText(service.getTitle());
        info.setText(service.isTtsMode()?"Озвучивание · "+service.getSpeed()+"×":"Аудиокнига · "+service.getSpeed()+"×");
        if(chapterPicker!=null)chapterPicker.setContentDescription("Глава "+(service.getSegment()+1)+". Выбрать главу");
        int d=service.getDuration(), position=service.getPosition(), segments=Math.max(1,service.getChapterCount());
        long total=service.isTtsMode()?0:service.getTotalDuration();
        long totalPos=service.getTotalPosition();
        if(total>0){
            if(!draggingTotal)totalProgress.setProgress((int)(totalPos*10000/total));
            overallTime.setText(formatLong(totalPos)+" / "+formatLong(total));
            overallPercent.setText((totalPos*100/total)+"%");
            overallRemaining.setText("−"+formatLong(Math.max(0,total-totalPos)));
            totalProgress.setEnabled(true);
        }else{
            if(!draggingTotal)totalProgress.setProgress(0);
            overallTime.setText("Подсчёт длительности…");overallPercent.setText("—");overallRemaining.setText("—");
            totalProgress.setEnabled(false);
        }

        int whole=d>0?(int)((long)position*100/d):0;
        if(!dragging)progress.setProgress(d>0?(int)((long)position*10000/d):0);
        wholeProgress.setText(service.isTtsMode()?"Озвучивание текста · "+(service.getDuration()>0?(int)((long)position*100/service.getDuration()):0)+"%":"Глава "+(service.getSegment()+1)+" из "+segments+" · "+service.getChapterName(service.getSegment())+" · "+whole+"%");
        chapterElapsed.setText(service.isTtsMode()?"Фрагмент "+(position+1):format(position));
        chapterDuration.setText(service.isTtsMode()?"Всего "+d:format(d));
        spokenText.setVisibility(service.isTtsMode()?View.VISIBLE:View.GONE);
        if(service.isTtsMode())spokenText.setText(service.getCurrentText());
    }
}
