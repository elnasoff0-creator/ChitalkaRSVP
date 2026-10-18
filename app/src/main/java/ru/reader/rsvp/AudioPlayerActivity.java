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
    LinearLayout root,playRow,overallTimes; FrameLayout coverArea; TextView overallLabel; ScrollView ttsTextFrame; TextView title,info,elapsed,duration,spokenText,wholeProgress,speedAction; View timerAction; TextView timerValue; SeekBar progress, totalProgress; TextView chapterElapsed, chapterDuration, overallTime, overallPercent, overallRemaining, speedValue; View chapterPicker; android.widget.ImageView coverImage, playIcon, coverPlaceholderIcon; TextView coverPlaceholderText;
    Handler handler=new Handler(Looper.getMainLooper()); boolean dragging=false, draggingTotal=false;String bookUri=""; AudioLibrary.Book selected;
    private int shownTtsPosition = -1;
    private int shownSeekSeconds=-1,shownSeekMinutes=-1;
    private boolean shownTtsMode = false;
    private boolean showingTtsCover = false;
    private String displayedTtsCoverUri = "";
    private int ttsCoverRequest = 0;
    final int BACKGROUND=0xfff3e7d5, TEXT=0xff30221e, ACCENT=0xff94583b;
    BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refresh();}};
    Runnable updater=new Runnable(){public void run(){refresh();handler.postDelayed(this,1000);}};
    int primaryColor(){return ReaderButtonColors.accent(this,ACCENT);}
    int neutralColor(){return ReaderButtonColors.neutral(this,0xfffff8ef);}
    int neutralInk(){return ReaderButtonColors.contrastingText(neutralColor());}
    int primaryInk(){return ReaderButtonColors.contrastingText(primaryColor());}
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView label(String s,int size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(TEXT);return v;}
    TextView control(String text,View.OnClickListener action){TextView t=label(text,19);t.setGravity(Gravity.CENTER);t.setTextColor(neutralInk());t.setBackground(bg(neutralColor(),13));t.setOnClickListener(action);t.setPadding(dp(10),dp(12),dp(10),dp(12));return t;}
    View topAction(int icon,String description,View.OnClickListener action){
        FrameLayout box=new FrameLayout(this);
        box.setBackground(bg(neutralColor(),14));
        box.setContentDescription(description);
        box.setOnClickListener(action);
        ImageView graphic=new ImageView(this);graphic.setImageResource(icon);
        graphic.setColorFilter(neutralInk());graphic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(graphic,new FrameLayout.LayoutParams(dp(24),dp(24),Gravity.CENTER));
        return box;
    }
    int audioUsage(String id){return id==null||id.isEmpty()?0:getSharedPreferences("reader",0).getInt("audio_usage:"+id,0);}
    void bumpAudioUsage(String id){if(id==null||id.isEmpty())return;getSharedPreferences("reader",0).edit().putInt("audio_usage:"+id,audioUsage(id)+1).apply();}
    int audioProgress(AudioLibrary.Book book){if(book==null)return 0;int stored=getSharedPreferences("reader",0).getInt("audio_progress:"+book.id,-1);if(stored>=0)return Math.max(0,Math.min(100,stored));int count=Math.max(1,book.uris.size());int seg=Math.max(0,Math.min(count-1,getSharedPreferences("reader",0).getInt("audio_segment:"+book.id,0)));return Math.max(0,Math.min(100,seg*100/count));}
    ArrayList<AudioLibrary.Book> sortedBooks(ArrayList<AudioLibrary.Book> all){Collections.sort(all,(a,b)->{boolean aDone=audioProgress(a)>=100,bDone=audioProgress(b)>=100;if(aDone!=bDone)return aDone?1:-1;int byPopularity=Integer.compare(audioUsage(b.id),audioUsage(a.id));if(byPopularity!=0)return byPopularity;return a.name.compareToIgnoreCase(b.name);});return all;}
    LinearLayout bottomIconControl(int iconRes,String hint,boolean primary,int iconSizeDp,View.OnClickListener action){
        LinearLayout cell=new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // Identical vertical slots for every button: no baseline-related vertical shift.
        FrameLayout buttonSlot=new FrameLayout(this);
        cell.addView(buttonSlot,new LinearLayout.LayoutParams(-1,dp(64)));
        FrameLayout holder=new FrameLayout(this);
        GradientDrawable buttonBackground;
        if(primary){
            buttonBackground=new GradientDrawable();
            buttonBackground.setShape(GradientDrawable.RECTANGLE);
            buttonBackground.setCornerRadius(dp(16));
            buttonBackground.setColor(primaryColor());
        }else{
            buttonBackground=bg(neutralColor(),13);
        }
        holder.setBackground(buttonBackground);
        holder.setClipToOutline(false);
        holder.setOnClickListener(action);
        holder.setContentDescription(primary?"Воспроизвести или поставить на паузу":hint);
        int holderSize=dp(48);
        FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(holderSize,holderSize,Gravity.CENTER);
        buttonSlot.addView(holder,hp);
        ImageView icon=new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setColorFilter(primary?primaryInk():neutralInk());
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
    @Override public void onCreate(Bundle b){super.onCreate(b);bookUri=getIntent().getStringExtra("book_uri");if(bookUri==null||bookUri.isEmpty())bookUri=getSharedPreferences("reader",0).getString("uri","");String id=getIntent().getStringExtra("audio_book_id");if(id!=null)for(AudioLibrary.Book item:AudioLibrary.load(this))if(id.equals(item.id)){selected=item;break;}if(getIntent().getBooleanExtra("count_usage",false)&&selected!=null)bumpAudioUsage(selected.id);draw();String audioAction=getIntent().getStringExtra("audio_action");if(audioAction!=null)handler.post(()->{switch(audioAction){case "tts":loadTts(bookUri,getSharedPreferences("reader",0).getInt("index",0));break;case "voice":voiceMenu();break;case "marks":showMarks();break;case "cover":chooseCover();break;case "sleep":sleepMenu();break;case "speed":speedMenu();break;}});boolean resumeAudio=getIntent().getBooleanExtra("resume_audio",false);
        if(selected!=null&&(AudioPlaybackService.instance==null||AudioPlaybackService.instance.isTtsMode()||!selected.id.equals(AudioPlaybackService.instance.getBookId())))openSelected(selected,resumeAudio);
        else if(resumeAudio&&selected!=null)command(AudioPlaybackService.PLAY);if(getIntent().getBooleanExtra("start_tts",false)&&!bookUri.isEmpty())loadTts(bookUri,getIntent().getIntExtra("word_index",0));}
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
        top.addView(topAction(R.drawable.ic_back,"Назад",v->BackNavigation.back(this)),new LinearLayout.LayoutParams(dp(48),dp(48)));
        chapterPicker=topAction(R.drawable.ic_list_audio,"Выбрать главу",v->chapters());
        top.addView(chapterPicker,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout speedGroup=new LinearLayout(this);speedGroup.setOrientation(1);speedGroup.setGravity(Gravity.CENTER);
        speedGroup.setBackground(bg(neutralColor(),15));
        android.widget.ImageView speedIcon=new android.widget.ImageView(this);
        speedIcon.setImageResource(R.drawable.ic_audio_speed);speedIcon.setColorFilter(TEXT);
        speedGroup.addView(speedIcon,new LinearLayout.LayoutParams(dp(25),dp(25)));
        speedValue=label("1×",12);speedValue.setGravity(Gravity.CENTER);
        speedGroup.addView(speedValue,new LinearLayout.LayoutParams(-1,dp(17)));
        speedGroup.setOnClickListener(v->speedMenu());speedGroup.setContentDescription("Скорость");
        top.addView(speedGroup,new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout timerGroup=new LinearLayout(this);timerGroup.setOrientation(LinearLayout.VERTICAL);
        timerGroup.setGravity(Gravity.CENTER);timerGroup.setBackground(bg(neutralColor(),15));
        ImageView timerIcon=new ImageView(this);timerIcon.setImageResource(R.drawable.ic_audio_timer);
        timerIcon.setColorFilter(TEXT);timerGroup.addView(timerIcon,new LinearLayout.LayoutParams(dp(23),dp(23)));
        timerValue=label("",11);timerValue.setGravity(Gravity.CENTER);timerValue.setSingleLine(true);timerValue.setVisibility(View.GONE);
        timerGroup.addView(timerValue,new LinearLayout.LayoutParams(-1,dp(17)));
        timerGroup.setOnClickListener(v->sleepMenu());timerGroup.setContentDescription("Таймер сна");
        timerAction=timerGroup;
        top.addView(timerGroup,new LinearLayout.LayoutParams(dp(48),dp(48)));
        top.addView(topAction(R.drawable.ic_settings,"Аудионастройки",v->openAudioSettings()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tl=new LinearLayout.LayoutParams(-1,-2);tl.topMargin=dp(10);root.addView(titleRow,tl);
        title=label("Аудиокнига",21);title.setGravity(Gravity.CENTER);title.setTypeface(android.graphics.Typeface.SERIF,1);
        title.setMaxLines(2);title.setOnClickListener(v->selectBook());
        titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        titleRow.addView(topAction(R.drawable.ic_edit_name,"Переименовать аудиокнигу",v->renameAudioBook()),new LinearLayout.LayoutParams(dp(43),dp(43)));
        info=label("",14);info.setGravity(Gravity.CENTER);info.setVisibility(View.GONE);root.addView(info);
        // Fixed-size text viewport: switching utterances cannot remeasure/shift cover,
        // progress, buttons or any other audiobook frames.
        spokenText=label("",14);
        spokenText.setGravity(Gravity.TOP | Gravity.START);
        spokenText.setPadding(dp(10),dp(7),dp(10),dp(7));
        spokenText.setTextIsSelectable(false);
        ttsTextFrame=new ScrollView(this);
        ttsTextFrame.setFillViewport(true);
        ttsTextFrame.setBackground(bg(0xfffff8ef,13));
        ttsTextFrame.addView(spokenText,new ScrollView.LayoutParams(-1,-2));
        root.addView(ttsTextFrame,new LinearLayout.LayoutParams(-1,dp(84)));
        shownTtsPosition=-1;shownTtsMode=false;ttsTextFrame.setVisibility(View.GONE);
        overallLabel=label("Вся аудиокнига",14);
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
        overallTimes=new LinearLayout(this);
        overallTimes.setGravity(Gravity.CENTER_VERTICAL);root.addView(overallTimes);
        overallTime=label("— / —",12);overallTimes.addView(overallTime,new LinearLayout.LayoutParams(0,-2,1));
        overallPercent=label("—",12);overallPercent.setGravity(Gravity.CENTER);
        overallTimes.addView(overallPercent,new LinearLayout.LayoutParams(dp(55),-2));
        overallRemaining=label("—",12);overallRemaining.setGravity(Gravity.RIGHT);
        overallTimes.addView(overallRemaining,new LinearLayout.LayoutParams(0,-2,1));
        coverArea=new android.widget.FrameLayout(this);
        coverArea.setBackground(bg(0xfffff8ef,26));
        LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(-1,0,1);
        cap.topMargin=dp(12);cap.bottomMargin=dp(12);root.addView(coverArea,cap);
        coverArea.setOnLongClickListener(v->{if(!showingTtsCover)chooseCover();return true;});
        coverArea.setOnClickListener(v->{if(!showingTtsCover)chooseCover();});
        LinearLayout coverPlaceholder=new LinearLayout(this);coverPlaceholder.setOrientation(LinearLayout.VERTICAL);coverPlaceholder.setGravity(Gravity.CENTER);
        coverPlaceholderIcon=new ImageView(this);coverPlaceholderIcon.setImageResource(R.drawable.ic_plus);coverPlaceholderIcon.setColorFilter(primaryColor());
        coverPlaceholder.addView(coverPlaceholderIcon,new LinearLayout.LayoutParams(dp(34),dp(34)));
        coverPlaceholderText=label("Выбрать обложку",14);coverPlaceholderText.setTextColor(0xff6d635a);coverPlaceholderText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams placeholderTextLp=new LinearLayout.LayoutParams(-2,-2);placeholderTextLp.topMargin=dp(10);coverPlaceholder.addView(coverPlaceholderText,placeholderTextLp);
        coverArea.addView(coverPlaceholder,new android.widget.FrameLayout.LayoutParams(-1,-1));
        coverImage=new ImageView(this);coverImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        coverArea.addView(coverImage,new android.widget.FrameLayout.LayoutParams(-1,-1));
        showingTtsCover=false;displayedTtsCoverUri="";ttsCoverRequest++;
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
        buildPlaybackButtons(false);
        refresh();
    }
    void buildPlaybackButtons(boolean isTts){
        playRow.removeAllViews();
        // TTS positions are text fragments, not seconds. MP3 audiobook keeps its own seek buttons.
        final int seconds=AudioPlaybackPrefs.seekSeconds(this), minutes=AudioPlaybackPrefs.seekMinutes(this);
        shownSeekSeconds=seconds;shownSeekMinutes=minutes;
        String[] hints=isTts?new String[]{"Фрагмент","","Фрагмент"}:
                new String[]{"Глава",minutes+" мин",seconds+" сек","",seconds+" сек",minutes+" мин","Глава"};
        int[] icons=isTts?new int[]{R.drawable.ic_prev_chapter,R.drawable.ic_play,R.drawable.ic_next_chapter}:
                new int[]{R.drawable.ic_prev_chapter,R.drawable.ic_rewind_1m,R.drawable.ic_rewind_10s,R.drawable.ic_play,R.drawable.ic_forward_10s,R.drawable.ic_forward_1m,R.drawable.ic_next_chapter};
        final int[] offsets={0,-minutes*60000,-seconds*1000,0,seconds*1000,minutes*60000,0};
        for(int n=0;n<icons.length;n++){
            final int k=n,center=isTts?1:3;
            LinearLayout cell=bottomIconControl(icons[n],hints[n],n==center,n==center?26:22,v->{
                if(isTts){
                    if(k==0)command(AudioPlaybackService.PREV);
                    else if(k==2)command(AudioPlaybackService.NEXT);
                    else {AudioPlaybackService service=AudioPlaybackService.instance;
                        command(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY);}
                }else{
                    if(k==0)changeChapter(-1);
                    else if(k==6)changeChapter(1);
                    else if(k==3){AudioPlaybackService service=AudioPlaybackService.instance;
                        command(service!=null&&!service.isPaused()?AudioPlaybackService.PAUSE:AudioPlaybackService.PLAY);}
                    else jump(offsets[k]);
                }
            });
            playRow.addView(cell,new LinearLayout.LayoutParams(0,-1,n==center?1.32f:1f));
        }
    }
    void updateTtsLayout(boolean inTts){
        // Reposition only on a mode switch: ordinary audiobooks keep their original layout.
        if(ttsTextFrame.getParent()!=null)root.removeView(ttsTextFrame);
        if(coverArea.getParent()!=null)root.removeView(coverArea);
        if(inTts){
            int insert=root.indexOfChild(info)+1;
            LinearLayout.LayoutParams coverLp=new LinearLayout.LayoutParams(-1,dp(170));
            coverLp.topMargin=dp(10);coverLp.bottomMargin=dp(7);
            root.addView(coverArea,insert,coverLp);
            LinearLayout.LayoutParams textLp=new LinearLayout.LayoutParams(-1,0,1);
            textLp.topMargin=dp(9);root.addView(ttsTextFrame,textLp);
        }else{
            root.addView(ttsTextFrame,root.indexOfChild(info)+1,new LinearLayout.LayoutParams(-1,dp(84)));
            LinearLayout.LayoutParams coverLp=new LinearLayout.LayoutParams(-1,0,1);
            coverLp.topMargin=dp(12);coverLp.bottomMargin=dp(12);
            root.addView(coverArea,root.indexOfChild(overallTimes)+1,coverLp);
        }
        overallLabel.setVisibility(inTts?View.GONE:View.VISIBLE);
        totalProgress.setVisibility(inTts?View.GONE:View.VISIBLE);
        overallTimes.setVisibility(inTts?View.GONE:View.VISIBLE);
        ttsTextFrame.setVisibility(inTts?View.VISIBLE:View.GONE);
        buildPlaybackButtons(inTts);
        chapterPicker.setContentDescription(inTts?"Выбрать фрагмент":"Выбрать главу");
    }
    void chooseCover(){if(selected==null){Toast.makeText(this,"Выберите аудиокнигу в библиотеке",Toast.LENGTH_LONG).show();return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,COVER_PICK);}
    void updateCover(){
        if(coverImage==null||showingTtsCover)return;
        String id=selected==null?"":selected.id;
        String uri=getSharedPreferences("reader",0).getString("audio_cover:"+id,"");
        if(uri.isEmpty())uri=AudiobookCoverStore.localUri(this,id);
        boolean hasCover=!uri.isEmpty();
        if(!hasCover){
            coverImage.setImageDrawable(null);coverImage.setVisibility(View.INVISIBLE);
            if(coverPlaceholderIcon!=null)coverPlaceholderIcon.setVisibility(View.VISIBLE);
            if(coverPlaceholderText!=null){coverPlaceholderText.setText("Выбрать обложку");coverPlaceholderText.setVisibility(View.VISIBLE);}
            return;
        }
        try{
            coverImage.setImageURI(Uri.parse(uri));coverImage.setVisibility(View.VISIBLE);
            if(coverPlaceholderIcon!=null)coverPlaceholderIcon.setVisibility(View.GONE);
            if(coverPlaceholderText!=null)coverPlaceholderText.setVisibility(View.GONE);
        }catch(Exception e){
            coverImage.setVisibility(View.INVISIBLE);
            if(coverPlaceholderIcon!=null)coverPlaceholderIcon.setVisibility(View.VISIBLE);
            if(coverPlaceholderText!=null)coverPlaceholderText.setVisibility(View.VISIBLE);
        }
    }
    /** Display the embedded EPUB/FB2 cover for system TTS, not the last audio library cover. */
    void showTtsCover(String uri){
        if(uri==null||uri.isEmpty()||coverImage==null)return;
        if(showingTtsCover&&uri.equals(displayedTtsCoverUri))return;
        showingTtsCover=true;displayedTtsCoverUri=uri;
        final int request=++ttsCoverRequest;
        coverImage.setImageDrawable(null);coverImage.setVisibility(View.INVISIBLE);
        if(coverPlaceholderIcon!=null){coverPlaceholderIcon.setImageResource(R.drawable.ic_book);coverPlaceholderIcon.setVisibility(View.VISIBLE);}
        if(coverPlaceholderText!=null){coverPlaceholderText.setText("Загрузка обложки…");coverPlaceholderText.setVisibility(View.VISIBLE);}
        new Thread(()->{
            android.graphics.Bitmap bitmap=null;
            try{
                byte[] bytes=CoverLoader.get(getApplicationContext(),uri);
                if(bytes!=null){
                    android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();
                    options.inJustDecodeBounds=true;
                    android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                    options.inSampleSize=Math.max(1,Math.max(options.outWidth/720,options.outHeight/1100));
                    options.inJustDecodeBounds=false;
                    bitmap=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                }
            }catch(Exception ignored){}
            final android.graphics.Bitmap resolved=bitmap;
            handler.post(()->{
                if(isFinishing()||request!=ttsCoverRequest||!showingTtsCover||!uri.equals(displayedTtsCoverUri))return;
                if(resolved!=null){
                    coverImage.setImageBitmap(resolved);coverImage.setVisibility(View.VISIBLE);
                    if(coverPlaceholderIcon!=null)coverPlaceholderIcon.setVisibility(View.GONE);
                    if(coverPlaceholderText!=null)coverPlaceholderText.setVisibility(View.GONE);
                }else{
                    coverImage.setVisibility(View.INVISIBLE);
                    if(coverPlaceholderText!=null)coverPlaceholderText.setText("Обложка отсутствует");
                }
            });
        },"tts-cover-loader").start();
    }
    void returnToAudioCover(){
        if(!showingTtsCover)return;
        showingTtsCover=false;displayedTtsCoverUri="";ttsCoverRequest++;
        if(coverPlaceholderIcon!=null)coverPlaceholderIcon.setImageResource(R.drawable.ic_plus);
        updateCover();
    }
    void renameAudioBook(){
        AudioPlaybackService service=AudioPlaybackService.instance;
        if(service!=null&&service.isTtsMode()){
            Toast.makeText(this,"Переименование текстовой книги доступно в библиотеке",Toast.LENGTH_LONG).show();return;
        }
        String id=selected!=null?selected.id:service!=null?service.getBookId():"";
        AudioLibrary.Book book=null;
        ArrayList<AudioLibrary.Book> all=AudioLibrary.load(this);
        for(AudioLibrary.Book item:all)if(item.id.equals(id)){book=item;break;}
        if(book==null){Toast.makeText(this,"Сначала выберите аудиокнигу из библиотеки",Toast.LENGTH_SHORT).show();return;}
        final AudioLibrary.Book editing=book;
        EditText nameField=new EditText(this);nameField.setSingleLine(true);nameField.setText(book.name);nameField.selectAll();
        LinearLayout editor=new LinearLayout(this);editor.setPadding(dp(20),dp(4),dp(20),0);editor.addView(nameField);
        new AlertDialog.Builder(this).setTitle("Изменить название аудиокниги").setView(editor)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,n)->{
                String next=nameField.getText().toString().trim();
                if(next.isEmpty()){Toast.makeText(this,"Название не может быть пустым",Toast.LENGTH_SHORT).show();return;}
                if(next.length()>180)next=next.substring(0,180);
                editing.name=next;editing.customName=true;editing.updated=System.currentTimeMillis();AudioLibrary.save(this,all);
                if(selected!=null&&selected.id.equals(editing.id))selected.name=next;
                title.setText(next);
                Intent change=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.RENAME);
                change.putExtra("bookId",editing.id);change.putExtra("title",next);startService(change);
            }).show();
    }
    void selectBook(){ArrayList<AudioLibrary.Book> all=sortedBooks(AudioLibrary.load(this));if(all.isEmpty()){Toast.makeText(this,"Добавьте аудиокнигу через + на главном экране",Toast.LENGTH_LONG).show();return;}String[] names=new String[all.size()];for(int k=0;k<all.size();k++)names[k]=all.get(k).name+" · "+audioProgress(all.get(k))+"%";new AlertDialog.Builder(this).setTitle("Выбрать аудиокнигу (удерживайте обложку для изменения)").setItems(names,(d,n)->{bumpAudioUsage(all.get(n).id);openSelected(all.get(n));draw();}).setNeutralButton("Обложка выбранной книги",(d,w)->chooseCover()).setNegativeButton("Отмена",null).show();}
    void seekPosition(int pos){startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK).putExtra("position",pos));}
    void changeChapter(int delta){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null||s.isTtsMode())return;goChapter(Math.max(0,Math.min(s.getChapterCount()-1,s.getSegment()+delta)));}
    void goChapter(int n){startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER).putExtra("chapter",n));}
    void importFolder(){new AlertDialog.Builder(this).setTitle("Добавление аудиокниг").setItems(new String[]{"Выбранная папка — одна книга","Сканировать вложенные папки как отдельные книги"},(d,n)->{getSharedPreferences("reader",0).edit().putBoolean("audio_recursive",n==1).apply();Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FOLDER_PICK);}).show();}
    void openAudioSettings(){startActivity(new Intent(this,SettingsActivity.class).putExtra("audio_tab",true).putExtra("audio_book_id",selected==null?"":selected.id));}
    void openSelected(AudioLibrary.Book b){openSelected(b,false);}
    void openSelected(AudioLibrary.Book b,boolean autoPlay){selected=b;AudiobookCoverStore.backfillOne(this,b.id,()->{if(selected!=null&&selected.id.equals(b.id))updateCover();});Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("title",b.name);i.putExtra("bookId",b.id);i.putExtra("uris",b.uris.toArray(new String[0]));i.putExtra("chapters",b.chapters.toArray(new String[0]));i.putExtra("autoplay",autoPlay);startService(i);updateCover();}
    void jump(int by){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;
        Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK_RELATIVE);
        i.putExtra("delta_ms",by);startService(i);
    }
    void importFiles(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,FILE_PICK);}
    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res!=RESULT_OK||data==null)return;
        if(req==COVER_PICK&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(selected!=null)getSharedPreferences("reader",0).edit().putString("audio_cover:"+selected.id,data.getData().toString()).apply();updateCover();return;}
        if(req==FOLDER_PICK&&data.getData()!=null){Uri tree=data.getData();try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}final boolean recursive=getSharedPreferences("reader",0).getBoolean("audio_recursive",true);new Thread(()->{try{ArrayList<AudioLibrary.Book> books=AudioLibrary.importTree(this,tree,recursive);AudioLibrary.merge(this,books);runOnUiThread(()->{if(books.isEmpty())Toast.makeText(this,"Аудиофайлы не найдены",Toast.LENGTH_LONG).show();else{bumpAudioUsage(books.get(0).id);openSelected(books.get(0));draw();Toast.makeText(this,"Добавлено книг: "+books.size(),Toast.LENGTH_LONG).show();}});}catch(Exception e){runOnUiThread(()->Toast.makeText(this,"Ошибка папки: "+e.getMessage(),Toast.LENGTH_LONG).show());}}).start();return;}
        if(req!=FILE_PICK)return;ArrayList<String> uris=new ArrayList<>();if(data.getClipData()!=null){for(int j=0;j<data.getClipData().getItemCount();j++)uris.add(data.getClipData().getItemAt(j).getUri().toString());}else if(data.getData()!=null)uris.add(data.getData().toString());for(String u:uris){try{getContentResolver().takePersistableUriPermission(Uri.parse(u),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}Collections.sort(uris);JSONArray arr=new JSONArray();for(String u:uris)arr.put(u);getSharedPreferences("reader",0).edit().putString("audio_playlist",arr.toString()).apply();if(!uris.isEmpty()){Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.LOAD);i.putExtra("uris",uris.toArray(new String[0]));i.putExtra("title","Аудиокнига");startService(i);}}
    void loadTts(String u,int index){
        if(u==null||u.isEmpty()){Toast.makeText(this,"Сначала откройте EPUB или FB2",Toast.LENGTH_SHORT).show();return;}
        bookUri=u;
        title.setText("Загрузка книги…");
        showTtsCover(u);
        Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.TTS);
        i.putExtra("bookUri",u);
        i.putExtra("word_index",Math.max(0,index));
        startService(i);
    }
    void speedMenu(){final String[] labels={"0.5×","0.75×","1×","1.1×","1.25×","1.5×","1.75×","2×","2.5×","3×"};final float[] rates={.5f,.75f,1f,1.1f,1.25f,1.5f,1.75f,2f,2.5f,3f};new AlertDialog.Builder(this).setTitle("Скорость").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SPEED);i.putExtra("speed",rates[n]);startService(i);}).show();}
    void sleepMenu(){final int[] minutes={0,5,10,15,30,60};String[] labels={"Выключить","5 мин","10 мин","15 мин","30 мин","60 мин"};new AlertDialog.Builder(this).setTitle("Таймер сна").setItems(labels,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SLEEP);i.putExtra("minutes",minutes[n]);startService(i);Toast.makeText(this,"Таймер сохранён",Toast.LENGTH_SHORT).show();}).show();}
    void voiceMenu(){String[] choices={"Обычный голос", "Ниже", "Выше", "Выбрать системный движок"};new AlertDialog.Builder(this).setTitle("Озвучивание текста").setItems(choices,(d,n)->{if(n==3){try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){Toast.makeText(this,"Настройки TTS недоступны",Toast.LENGTH_SHORT).show();}return;}Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.VOICE);i.putExtra("pitch",n==1?.8f:n==2?1.2f:1f);startService(i);}).show();}
    void chapters(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null){Toast.makeText(this,"Сначала выберите книгу",Toast.LENGTH_SHORT).show();return;}if(s.isTtsMode()){chooseTtsFragment(s);return;}int count=s.getChapterCount();if(count<1)return;String[] labels=new String[count];for(int j=0;j<count;j++)labels[j]=s.getChapterName(j);new AlertDialog.Builder(this).setTitle("Части аудиокниги").setSingleChoiceItems(labels,s.getSegment(),(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.CHAPTER);i.putExtra("chapter",n);startService(i);d.dismiss();}).setNegativeButton("Отмена",null).show();}
    void chooseTtsFragment(AudioPlaybackService s){
        int count=s.getDuration(),current=s.getPosition();
        if(count<=0)return;
        int start=Math.max(0,current-12),end=Math.min(count,start+25);
        String[] labels=new String[end-start];
        for(int i=start;i<end;i++)labels[i-start]="Фрагмент "+(i+1)+" из "+count;
        new AlertDialog.Builder(this).setTitle("Выбор фрагмента")
            .setSingleChoiceItems(labels,current-start,(d,index)->{
                seekPosition(start+index);d.dismiss();
            }).setNegativeButton("Отмена",null).show();
    }
    void bookmark(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;String key="audio_marks:"+s.getTitle();Set<String> marks=new HashSet<>(getSharedPreferences("reader",0).getStringSet(key,new HashSet<>()));marks.add(String.valueOf(s.getPosition()));getSharedPreferences("reader",0).edit().putStringSet(key,marks).apply();Toast.makeText(this,"Аудиозакладка сохранена",Toast.LENGTH_SHORT).show();}
    void options(){String[] choices={"Аудиозакладки","Скорость","Таймер сна","Остановить звук"};new AlertDialog.Builder(this).setItems(choices,(d,n)->{if(n==0)showMarks();if(n==1)speedMenu();if(n==2)sleepMenu();if(n==3)command(AudioPlaybackService.STOP);}).show();}
    void showMarks(){AudioPlaybackService s=AudioPlaybackService.instance;if(s==null)return;Set<String> ms=getSharedPreferences("reader",0).getStringSet("audio_marks:"+s.getTitle(),new HashSet<>());ArrayList<Integer> vals=new ArrayList<>();for(String v:ms)try{vals.add(Integer.parseInt(v));}catch(Exception ignored){}Collections.sort(vals);String[] names=new String[vals.size()];for(int j=0;j<vals.size();j++)names[j]=s.isTtsMode()?"Фрагмент "+vals.get(j):format(vals.get(j));new AlertDialog.Builder(this).setTitle("Аудиозакладки").setItems(names,(d,n)->{Intent i=new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.SEEK);i.putExtra("position",vals.get(n));startService(i);}).setPositiveButton("Закрыть",null).show();}
    String formatLong(long ms){long sec=Math.max(0,ms)/1000;return String.format(Locale.ROOT,"%d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    String format(int ms){long sec=ms/1000;return String.format(Locale.ROOT,"%02d:%02d:%02d",sec/3600,(sec/60)%60,sec%60);}
    private static void setTextIfDifferent(TextView view, String value) {
        if (view != null && !value.contentEquals(view.getText())) view.setText(value);
    }
    void refresh(){
        AudioPlaybackService service=AudioPlaybackService.instance;
        if(title==null||playIcon==null)return;
        if(service==null){playIcon.setImageResource(R.drawable.ic_play);playIcon.setColorFilter(Color.WHITE);return;}
        playIcon.setImageResource(service.isPaused()?R.drawable.ic_play:R.drawable.ic_pause);
        playIcon.setColorFilter(Color.WHITE);
        if(speedValue!=null){float rate=service.getSpeed();setTextIfDifferent(speedValue,(rate==(int)rate?Integer.toString((int)rate):Float.toString(rate))+"×");}
        if(timerAction!=null){
            long remaining=service.getSleepRemainingMillis();timerAction.setAlpha(remaining>0?1f:.70f);
            if(timerValue!=null){
                String counter=remaining<=0?"":remaining<60000?(Math.max(1,remaining/1000)+"с"):
                    ((remaining+59999)/60000)+"м";
                setTextIfDifferent(timerValue,counter);
                timerValue.setVisibility(remaining>0?View.VISIBLE:View.GONE);
                timerAction.setContentDescription(remaining<=0?"Таймер сна выключен":"Таймер сна: осталось "+counter);
            }
        }
        if(!service.isTtsMode() && (shownSeekSeconds!=AudioPlaybackPrefs.seekSeconds(this)
                ||shownSeekMinutes!=AudioPlaybackPrefs.seekMinutes(this)))buildPlaybackButtons(false);
        if(service.isTtsMode()){
            String source=service.getTtsBookUri();
            showTtsCover(source.isEmpty()?bookUri:source);
        }else{
            returnToAudioCover();
            if(selected==null && !service.getBookId().isEmpty())for(AudioLibrary.Book item:AudioLibrary.load(this))if(item.id.equals(service.getBookId())){selected=item;updateCover();break;}
        }
        setTextIfDifferent(title, service.getTitle());
        // Speed is displayed in the toolbar; no redundant book-type/speed subtitle.
        if(chapterPicker!=null)chapterPicker.setContentDescription(service.isTtsMode()?"Выбрать фрагмент":"Глава "+(service.getSegment()+1)+". Выбрать главу");
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
        setTextIfDifferent(wholeProgress,service.isTtsMode()?"Озвучивание текста · "+(service.getDuration()>0?(int)((long)position*100/service.getDuration()):0)+"%":"Глава "+(service.getSegment()+1)+" из "+segments+" · "+service.getChapterName(service.getSegment())+" · "+whole+"%");
        setTextIfDifferent(chapterElapsed,service.isTtsMode()?"Фрагмент "+(position+1):format(position));
        setTextIfDifferent(chapterDuration,service.isTtsMode()?"Всего "+d:format(d));
        boolean inTts = service.isTtsMode();
        // Fixed height only while in TTS; MP3 audiobook screen keeps its original layout.
        if (inTts != shownTtsMode) {
            shownTtsMode = inTts;
            shownTtsPosition = -1;
            updateTtsLayout(inTts);
        }
        if (inTts && shownTtsPosition != position) {
            shownTtsPosition = position;
            spokenText.setText(service.getCurrentText());
            ttsTextFrame.post(() -> { if (ttsTextFrame != null) ttsTextFrame.scrollTo(0,0); });
        }
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
