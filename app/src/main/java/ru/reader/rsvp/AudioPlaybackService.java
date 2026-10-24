package ru.reader.rsvp;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.KeyEvent;
import android.net.Uri;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.pm.ServiceInfo;
import java.util.*;

/** Background playback for local audio and system TTS. Never stores private files in a public directory. */
public class AudioPlaybackService extends Service implements TextToSpeech.OnInitListener {
    public static final String CHANNEL="reader_audio", ACTION_STATE="ru.reader.rsvp.AUDIO_STATE";
    public static final String LOAD="load", PLAY="play", PAUSE="pause", SEEK="seek", NEXT="next", PREV="prev", STOP="stop", SPEED="speed", SLEEP="sleep", RENAME="rename", SEEK_RELATIVE="seek_relative", TTS="tts", VOICE="voice", CHAPTER="chapter", ENGINE="engine";
    public static AudioPlaybackService instance;
    private MediaPlayer player;
    private TextToSpeech tts;
    private MediaPlayer ttsAudio;
    private int localGeneration=0,localPlayingIndex=-1;
    private final java.util.concurrent.ConcurrentHashMap<Integer,java.util.List<int[]>> localWordFrames=new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Integer,Integer> localSampleRates=new java.util.concurrent.ConcurrentHashMap<>();
    private long pendingLocalSeek=0;
    private int requestedLocalChar=-1;
    private final HashSet<Integer> localPending=new HashSet<>();
    private void releaseLocalAudio(){
        if(ttsAudio!=null){try{ttsAudio.stop();ttsAudio.reset();ttsAudio.release();}catch(Exception ignored){}ttsAudio=null;}
        localPlayingIndex=-1;
    }
    private void resetLocalSession(){localGeneration++;localPending.clear();localWordFrames.clear();localSampleRates.clear();releaseLocalAudio();}
    private java.io.File localFile(int index){
        String engine=getSharedPreferences("reader",0).getString("tts_engine","");
        String voice=getSharedPreferences("reader",0).getString("tts_voice","");
        return LocalTtsCache.file(this,currentBook,engine,voice,chunks.get(index));
    }
    private void synthesizeLocal(int index){
        if(tts==null||!ttsReady||index<0||index>=chunks.size()||localPending.contains(index))return;
        java.io.File f=localFile(index);
        if(f.isFile()&&f.length()>256)return;
        localPending.add(index);
        java.io.File pending=new java.io.File(f.getAbsolutePath()+".part");
        if(pending.exists())pending.delete();
        int generation=localGeneration;
        // Keep synthesis neutral. WPM is applied by MediaPlayer when reading a file.
        tts.setSpeechRate(1f);tts.setPitch(pitch);
        int status=tts.synthesizeToFile(chunks.get(index),new Bundle(),pending,"local_"+generation+"_"+index);
        if(status!=TextToSpeech.SUCCESS){localPending.remove(index);if(index==ttsPosition)fallbackLocalSpeech(index);}
    }
    private void prepareLocalAhead(){
        int ahead=1; // single internal next-sentence preparation
        for(int i=ttsPosition+1;i<Math.min(chunks.size(),ttsPosition+ahead+1);i++)synthesizeLocal(i);
    }
    private void playLocalFile(int index){
        if(!ttsMode||paused||index!=ttsPosition)return;
        java.io.File f=localFile(index);
        if(localPending.contains(index))return; // Do not read a partially synthesized file.
        if(!f.isFile()||f.length()<256){synthesizeLocal(index);return;}
        releaseLocalAudio();localPlayingIndex=index;final int generation=localGeneration;
        try{
            MediaPlayer sound=new MediaPlayer();ttsAudio=sound;
            sound.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            sound.setDataSource(f.getAbsolutePath());
            sound.setOnPreparedListener(mp->{if(mp!=ttsAudio||localGeneration!=generation||paused)return;
                final Runnable begin=()->{
                    if(mp!=ttsAudio||localGeneration!=generation||paused)return;
                    try{
                        mp.start();
                        mp.setPlaybackParams(mp.getPlaybackParams().setSpeed(Math.min(5f,TtsWpm.rate(this))));
                    }catch(Exception e){fallbackLocalSpeech(index);}
                    notifyState();
                };
                long seek=pendingLocalSeek;pendingLocalSeek=0;
                if(seek>0){
                    mp.setOnSeekCompleteListener(prepared->{if(prepared==ttsAudio)begin.run();});
                    try{mp.seekTo((int)Math.min(Math.max(0,mp.getDuration()-1),seek));}
                    catch(Exception ignored){begin.run();}
                }else begin.run();
            });
            sound.setOnCompletionListener(mp->{if(localGeneration!=generation||!ttsMode||paused)return;
                releaseLocalAudio();ttsPosition++;saveProgress();if(ttsPosition<chunks.size())speakChunk();else{paused=true;audioFocus.release();notifyState();}});
            sound.setOnErrorListener((mp,what,extra)->{fallbackLocalSpeech(index);return true;});
            sound.prepareAsync();prepareLocalAhead();
        }catch(Exception e){fallbackLocalSpeech(index);}
    }
    private void fallbackLocalSpeech(int index){
        releaseLocalAudio();if(tts!=null&&!paused&&ttsMode){
            tts.setSpeechRate(TtsWpm.rate(this));tts.speak(chunks.get(index),TextToSpeech.QUEUE_FLUSH,new Bundle(),"fallback_"+localGeneration+"_"+index);
        }
    }

    private PlaybackAudioFocus audioFocus;
    private MediaSession mediaSession;
    private boolean interruptedByExternalAudio;
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && active && !paused)
                pausePlayback();
        }
    };
    private boolean ttsReady=false, ttsStartRequested=false, active=false, paused=true, ttsMode=false;
    private float speed=1.0f, pitch=1.0f;
    private int segment=0, ttsPosition=0, duration=0;
    private final ArrayList<Long> chapterDurations=new ArrayList<>();
    private long durationGeneration=0;
    private final ArrayList<Integer> chunkWordPositions=new ArrayList<>();
    private final ArrayList<Integer> ttsChunkCharPositions=new ArrayList<>();
    private int ttsWordCount=0;
    private long sleepDeadline=0;
    private double pendingChapterFraction=0;
    // Keep the intended offset while MediaPlayer is preparing/seeking, so a background
    // shutdown can never overwrite saved progress with its temporary 00:00 position.
    private int preparedPosition=0;
    private boolean playerReady=false, seekPending=false, rewindOnNextPlay=false;
    private boolean playAfterSeek=false, playWhenPrepared=false;
    private boolean userPausedWhilePreparing=false;
    private long pendingRelativeWhilePreparing=0;
    private long lastSavedPosition=0;

    private ArrayList<String> playlist=new ArrayList<>(), chunks=new ArrayList<>(), chapterNames=new ArrayList<>();
    private String title="Аудиокнига", currentBook="", bookId="";
    private Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(active){if(!ttsMode&&player!=null){try{if(player.isPlaying()){saveProgress();ChteicoStore.tickAudio(AudioPlaybackService.this,bookId,1);}}catch(Exception ignored){}}if(ttsMode&&!paused && ((ttsAudio!=null&&ttsAudio.isPlaying())||(ttsAudio==null&&tts!=null&&tts.isSpeaking()))){ChteicoStore.tickAudio(AudioPlaybackService.this,currentBook,1);
                    android.content.SharedPreferences t=getSharedPreferences("reader",0);
                    t.edit().putLong("tts_listened_seconds:"+currentBook,
                        t.getLong("tts_listened_seconds:"+currentBook,0)+1).apply();}if(sleepDeadline>0&&System.currentTimeMillis()>=sleepDeadline){pausePlayback();sleepDeadline=0;getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",0).apply();}notifyState();handler.postDelayed(this,1000);}}};
    @Override public void onCreate(){super.onCreate();instance=this;android.app.NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Аудиокниги",NotificationManager.IMPORTANCE_LOW));audioFocus=new PlaybackAudioFocus(this, () -> {
            interruptedByExternalAudio = true;
            if (active && !paused) pausePlayback();
        });
        setupMediaSession();
        IntentFilter noisyFilter=new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisyReceiver,noisyFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver,noisyFilter);
        sleepDeadline=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0);
        if(sleepDeadline<=System.currentTimeMillis())sleepDeadline=0;
        String preferred=getSharedPreferences("reader",0).getString("tts_engine","");
        tts=preferred.isEmpty()?new TextToSpeech(this,this):new TextToSpeech(this,this,preferred);
        handler.post(tick);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_STICKY;String action=intent.getAction();if(action==null)return START_STICKY;switch(action){
        case LOAD: {AudioModeCoordinator.stopEdge(this);interruptedByExternalAudio=false;if(active)saveProgress(); if(tts!=null&&ttsMode)tts.stop();resetLocalSession(); String[] uris=intent.getStringArrayExtra("uris");playlist.clear();if(uris!=null)for(String u:uris)if(u!=null)playlist.add(u);title=intent.getStringExtra("title");if(title==null)title="Аудиокнига";
          bookId=intent.getStringExtra("bookId");if(bookId==null)bookId=playlistKey();ChteicoStore.openAudio(this,bookId);getSharedPreferences("reader",0).edit().putString("audio_last_book",bookId).apply();
          chapterNames.clear();String[] names=intent.getStringArrayExtra("chapters");if(names!=null)chapterNames.addAll(Arrays.asList(names));
          ttsMode=false;active=true;loadChapterDurations();speed=getSharedPreferences("reader",0).getFloat("audio_speed",1f);
          segment=Math.max(0,Math.min(Math.max(0,playlist.size()-1),getSharedPreferences("reader",0).getInt("audio_segment:"+bookId,0)));
          int resume=getSharedPreferences("reader",0).getInt("audio_pos:"+bookId,0);
          rewindOnNextPlay=false;playWhenPrepared=false;playAfterSeek=false;
          userPausedWhilePreparing=false;pendingRelativeWhilePreparing=0;
          prepareAudio(resume,intent.getBooleanExtra("autoplay",false));break;}
        case TTS: {AudioModeCoordinator.stopEdge(this);
          sleepDeadline=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0L);
          if(sleepDeadline<=System.currentTimeMillis())sleepDeadline=0L;
          interruptedByExternalAudio=false;if(active)saveProgress(); if(tts!=null)tts.stop();resetLocalSession(); title="Загрузка книги…";currentBook=intent.getStringExtra("bookUri");ChteicoStore.openAudio(this,currentBook);final String source=currentBook;
          ttsMode=true;active=true;paused=true;releasePlayer();
          getSharedPreferences("reader",0).edit().putString("last_media_kind","text").apply();
          new Thread(()->{try{BookLoader.Book book=BookLoader.load(this,Uri.parse(source));handler.post(()->{if(!ttsMode||!source.equals(currentBook))return;
              title=book.title==null||book.title.trim().isEmpty()?"Книга":book.title.trim();
              makeChunks(book.text);int requestedWord=intent.getIntExtra("word_index",-1); int fallback=getSharedPreferences("reader",0).getInt("tts_pos:"+source,0); ttsPosition=requestedWord>=0?chunkForWord(requestedWord):Math.max(0,Math.min(chunks.size()-1,fallback));ttsStartRequested=intent.getBooleanExtra("auto_on_selection",false);
              paused=true;
              if(ttsStartRequested)startTts();
              notifyState();});}catch(Exception ignored){handler.post(()->{paused=true;notifyState();});}}).start();break;}
        case PLAY: AudioModeCoordinator.stopEdge(this);resume();break;case PAUSE: pausePlayback();break;
        case STOP: stopServicePlayback();break;
        case NEXT: jump(ttsMode?1:1);break;case PREV:jump(-1);break;
        case SEEK:seekTo(intent.getIntExtra("position",0));break;
        case "tts_select_word": {
            if(ttsMode){
                int target=Math.max(0,intent.getIntExtra("word_index",0));
                // No seek to an arbitrary word: navigate to the start of its sentence.
                boolean auto=getSharedPreferences("reader",0).getBoolean("tts_tap_autoplay",false);
                if(!paused)pausePlayback();
                ttsPosition=chunkForWord(target);
                pendingLocalSeek=0;
                resetLocalSession();
                saveProgress();notifyState();
                if(auto)resume();
            }
            break;
        }
        case SEEK_RELATIVE:seekRelative(intent.getIntExtra("delta_ms",0),!paused);break;
        case SPEED: speed=Math.max(.5f,Math.min(3f,intent.getFloatExtra("speed",1)));if(!paused)applySpeed();saveProgress();notifyState();break;
        case "tts_wpm": {
            if(ttsAudio!=null&&!paused){try{ttsAudio.setPlaybackParams(ttsAudio.getPlaybackParams().setSpeed(Math.min(5f,TtsWpm.rate(this))));}catch(Exception ignored){}}
            else if(tts!=null&&ttsMode)tts.setSpeechRate(TtsWpm.rate(this));notifyState();break;
        }
        case RENAME: {String id=intent.getStringExtra("bookId"),newTitle=intent.getStringExtra("title");
            if(!ttsMode && id!=null && id.equals(bookId) && newTitle!=null && !newTitle.trim().isEmpty())title=newTitle.trim();break;}
        case SLEEP: {int minutes=intent.getIntExtra("minutes",0);sleepDeadline=minutes<=0?0:System.currentTimeMillis()+minutes*60000L;getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",sleepDeadline).apply();break;}
        case ENGINE: {boolean wasPlaying=active&&ttsMode&&!paused;
            if(tts!=null){tts.stop();tts.shutdown();}
            resetLocalSession();if(ttsMode)paused=true;
            ttsReady=false;ttsStartRequested=wasPlaying;
            String selected=getSharedPreferences("reader",0).getString("tts_engine","");
            tts=selected.isEmpty()?new TextToSpeech(this,this):new TextToSpeech(this,this,selected);break;}
        case VOICE: {float p=intent.getFloatExtra("pitch",1f);pitch=Math.max(.6f,Math.min(1.5f,p));if(tts!=null)tts.setPitch(pitch);break;}
        case CHAPTER: {int target=intent.getIntExtra("chapter",0);if(!ttsMode&&target>=0&&target<playlist.size()){boolean shouldPlay=!paused;segment=target;pendingChapterFraction=Math.max(0,Math.min(1,intent.getDoubleExtra("fraction",0)));prepareAudio(0,shouldPlay);}break;}
    }notifyState();return START_STICKY;}
    // Probe audio durations off the main thread so a large library never freezes the UI.
    private void loadChapterDurations(){
        final ArrayList<String> copy=new ArrayList<>(playlist);
        final String currentId=bookId;
        final long generation=++durationGeneration;
        chapterDurations.clear();
        for(int k=0;k<copy.size();k++)chapterDurations.add(0L);
        new Thread(()->{
            ArrayList<Long> result=new ArrayList<>();
            for(String path:copy){
                long ms=0;
                android.media.MediaMetadataRetriever mmr=new android.media.MediaMetadataRetriever();
                try{
                    mmr.setDataSource(getApplicationContext(),Uri.parse(path));
                    String value=mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if(value!=null)ms=Math.max(0,Long.parseLong(value));
                }catch(Exception ignored){}finally{try{mmr.release();}catch(Exception ignored){}}
                result.add(ms);
            }
            handler.post(()->{
                if(generation!=durationGeneration||!currentId.equals(bookId)||ttsMode)return;
                chapterDurations.clear();chapterDurations.addAll(result);notifyState();
            });
        },"audiobook-duration-scan").start();
    }
    public long getTotalDuration(){long sum=0;for(Long ms:chapterDurations){if(ms==null||ms<=0)return 0;sum+=ms;}return sum;}
    public long getTotalPosition(){long position=Math.max(0,getPosition());for(int n=0;n<Math.min(segment,chapterDurations.size());n++)position+=chapterDurations.get(n);return position;}
    public void seekTotal(long target){
        if(ttsMode)return;
        long total=getTotalDuration();if(total<=0)return;
        long remaining=Math.max(0,Math.min(total-1,target));
        for(int k=0;k<chapterDurations.size();k++){
            long len=chapterDurations.get(k);
            if(remaining<len||k==chapterDurations.size()-1){
                if(k==segment)seekTo((int)Math.min(Integer.MAX_VALUE,remaining));
                else{boolean autoplay=!paused;segment=k;prepareAudio((int)Math.min(Integer.MAX_VALUE,remaining),autoplay);}
                return;
            }remaining-=len;
        }
    }
    private String playlistKey(){return playlist.isEmpty()?"":playlist.get(0);}
    private void prepareAudio(int offset,boolean autoplay){prepareAudioTarget(offset,false,autoplay);}
    /** Prepare one chapter and resolve a signed offset into adjacent chapters if needed. */
    private void prepareAudioTarget(long requestedOffset,boolean fromChapterEnd,boolean autoplay){
        releasePlayer();playerReady=false;seekPending=false;paused=true;duration=0;
        preparedPosition=(int)Math.max(0,Math.min(Integer.MAX_VALUE,fromChapterEnd?0:requestedOffset));
        notifyState();if(playlist.isEmpty())return;
        final int preparedChapter=segment;
        try{
            Uri uri=Uri.parse(playlist.get(segment));player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(this,uri);
            player.setOnPreparedListener(mp->{
                if(mp!=player||preparedChapter!=segment)return;
                duration=mp.getDuration();
                if(segment>=0&&segment<chapterDurations.size()&&duration>0)chapterDurations.set(segment,(long)duration);
                long desired=fromChapterEnd?duration+requestedOffset:requestedOffset;
                if(pendingRelativeWhilePreparing!=0){desired+=pendingRelativeWhilePreparing;pendingRelativeWhilePreparing=0;}
                if(pendingChapterFraction>0){desired=(long)(pendingChapterFraction*duration);pendingChapterFraction=0;}
                int direction=AudioSeekBounds.step(segment,playlist.size(),duration,desired);
                if(direction<0){segment--;prepareAudioTarget(desired,true,autoplay);return;}
                if(direction>0){segment++;prepareAudioTarget(AudioSeekBounds.remaining(desired,duration,direction),false,autoplay);return;}
                int target=AudioSeekBounds.clamped(desired,duration);
                preparedPosition=target;playerReady=true;
                AudioEqualizer.attach(this,mp.getAudioSessionId());
                // Don't let a pending seek be recorded as position zero.
                seekPending=target>0;
                saveProgress();
                Runnable startAfterSeek=()->{
                    if(mp!=player||preparedChapter!=segment)return;
                    seekPending=false;
                    preparedPosition=target;
                    if((autoplay||playWhenPrepared||playAfterSeek)&&!userPausedWhilePreparing
                            &&!interruptedByExternalAudio&&audioFocus.acquire()){
                        try{mp.start();paused=false;applySpeed();}catch(Exception ignored){paused=true;}
                    }else paused=true;
                    playWhenPrepared=false;playAfterSeek=false;userPausedWhilePreparing=false;
                    saveProgress();notifyState();
                };
                if(target>0){
                    seekPending=true;
                    mp.setOnSeekCompleteListener(done->startAfterSeek.run());
                    mp.seekTo(target);
                }else startAfterSeek.run();
            });
            player.setOnCompletionListener(mp->{if(mp!=player)return;if(segment+1<playlist.size()){
                segment++;rewindOnNextPlay=false;prepareAudio(0,true);
            }else {if(ChteicoStore.completeAudio(this,bookId))ChteicoNotifications.milestone(this,"Аудиокнига завершена!","Чтейцо радуется вашему достижению.");pausePlayback();}});
            player.prepareAsync();
        }catch(Exception e){paused=true;saveProgress();notifyState();}
    }
    /** Relative timeline seeking automatically continues through chapter boundaries. */
    private void seekRelative(int deltaMs,boolean autoplay){
        if(ttsMode){seekTo(ttsPosition+(deltaMs>=0?1:-1));return;}
        if(playlist.isEmpty())return;
        if(!playerReady){
            pendingRelativeWhilePreparing+=deltaMs;
            if(autoplay)playWhenPrepared=true;
            return;
        }
        long wanted=(long)getPosition()+deltaMs;
        int direction=AudioSeekBounds.step(segment,playlist.size(),duration,wanted);
        if(direction<0){segment--;prepareAudioTarget(wanted,true,autoplay);return;}
        if(direction>0){segment++;prepareAudioTarget(AudioSeekBounds.remaining(wanted,duration,direction),false,autoplay);return;}
        playAfterSeek=autoplay&&paused;
        seekTo(AudioSeekBounds.clamped(wanted,duration));
        if(playAfterSeek&&!seekPending){playAfterSeek=false;startPlayerNow();}
    }
    private void startPlayerNow(){
        if(player==null||!playerReady||seekPending)return;
        if(!audioFocus.acquire()){paused=true;notifyState();return;}
        try{player.start();paused=false;applySpeed();}catch(Exception ignored){paused=true;}
        notifyState();
    }
    private void releasePlayer(){playerReady=false;seekPending=false;if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    private void applySpeed(){if(player!=null&&!paused)try{player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));}catch(Exception ignored){}}
    private void resume(){
        interruptedByExternalAudio=false;
        if(ttsMode){
            if(ttsAudio!=null&&localPlayingIndex==ttsPosition){
                if(audioFocus.acquire()){try{ttsAudio.start();paused=false;}catch(Exception e){releaseLocalAudio();startTts();}notifyState();}
            }else startTts();return;
        }
        if(rewindOnNextPlay){
            rewindOnNextPlay=false;
            int seconds=AudioPlaybackPrefs.pauseRewindSeconds(this);
            if(seconds>0){seekRelative(-seconds*1000,true);return;}
        }
        if(!playerReady){playWhenPrepared=true;userPausedWhilePreparing=false;return;}
        if(seekPending){playAfterSeek=true;userPausedWhilePreparing=false;return;}
        startPlayerNow();
    }
    private void pausePlayback(){
        userPausedWhilePreparing=!playerReady;
        playWhenPrepared=false;playAfterSeek=false;
        boolean wasPlaying=!paused;
        if(ttsMode){
            if(ttsAudio!=null){try{ttsAudio.pause();}catch(Exception ignored){} }
            else if(tts!=null){tts.stop();localPending.clear();}paused=true;
        }
        else if(player!=null){try{if(player.isPlaying())player.pause();}catch(Exception ignored){}paused=true;
            if(wasPlaying)rewindOnNextPlay=true;
        }
        saveProgress();audioFocus.release();notifyState();
    }
    private void stopServicePlayback(){if(ttsMode){if(tts!=null)tts.stop();resetLocalSession();}else if(player!=null){try{if(player.isPlaying())player.pause();}catch(Exception ignored){}}paused=true;saveProgress();audioFocus.release();releasePlayer();active=false;Intent i=new Intent(ACTION_STATE);i.setPackage(getPackageName());sendBroadcast(i);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    private int audioProgressPercent(){
        if(ttsMode||playlist.isEmpty()||bookId==null||bookId.isEmpty())return 0;
        long total=getTotalDuration();
        if(total>0){
            long position=Math.max(0,getTotalPosition());
            int percent=(int)Math.max(0,Math.min(100,position*100/Math.max(1,total)));
            if(segment>=playlist.size()-1&&duration>0&&getPosition()>=Math.max(0,duration-700))percent=100;
            return percent;
        }
        if(duration>0){
            float chapters=Math.max(1,playlist.size());
            float partial=Math.max(0f,Math.min(1f,getPosition()/(float)Math.max(1,duration)));
            return Math.max(0,Math.min(100,Math.round((segment+partial)*100f/chapters)));
        }
        return Math.max(0,Math.min(100,segment*100/Math.max(1,playlist.size())));
    }
    private void seekTo(int pos){
        if(ttsMode){ttsPosition=Math.max(0,Math.min(chunks.size()-1,pos));resetLocalSession();if(!paused)startTts();}
        else if(player!=null){
            int wanted=Math.max(0,Math.min(duration,pos));preparedPosition=wanted;
            if(playerReady){
                seekPending=true;
                MediaPlayer thisPlayer=player;
                thisPlayer.setOnSeekCompleteListener(mp->{if(player!=mp)return;seekPending=false;preparedPosition=mp.getCurrentPosition();
                    boolean shouldStart=playAfterSeek;playAfterSeek=false;
                    if(shouldStart)startPlayerNow();
                    saveProgress();notifyState();});
                try{thisPlayer.seekTo(wanted);}catch(Exception ignored){seekPending=false;}
            }
        }
        saveProgress();notifyState();
    }
    private void jump(int change){if(ttsMode){seekTo(ttsPosition+change);return;}if(change>0){if(segment+1<playlist.size()){segment++;prepareAudio(0,!paused);}else seekTo(getPosition()+30000);}else {if(getPosition()>15000)seekTo(getPosition()-15000);else if(segment>0){segment--;prepareAudio(0,!paused);}else seekTo(0);}}
    private int chunkForWord(int wordIndex){
        if(chunkWordPositions.isEmpty())return 0;
        int at=java.util.Collections.binarySearch(chunkWordPositions,Math.max(0,wordIndex));
        return Math.max(0,Math.min(chunkWordPositions.size()-1,at>=0?at:-at-2));
    }
    private void makeChunks(String full){
        chunks.clear();chunkWordPositions.clear();ttsChunkCharPositions.clear();
        java.util.ArrayList<SentenceChunks.Chunk> parts=SentenceChunks.split(full);
        ttsWordCount=SentenceChunks.wordPositions(full==null?"":full).size();
        for(SentenceChunks.Chunk part:parts){
            chunks.add(part.text);
            chunkWordPositions.add(part.fromWord);
            ttsChunkCharPositions.add(part.fromChar);
        }
        if(chunks.isEmpty()) {chunks.add(" ");chunkWordPositions.add(0);ttsChunkCharPositions.add(0);}
    }
    private void saveTtsReadingPosition(android.content.SharedPreferences.Editor e){
        if(currentBook==null||currentBook.isEmpty())return;
        int at=chunkWordPositions.isEmpty()?0:chunkWordPositions.get(Math.max(0,Math.min(ttsPosition,chunkWordPositions.size()-1)));
        e.putInt("tts_word:"+currentBook,at);
        try {
            android.content.SharedPreferences prefs=getSharedPreferences("reader",0);
            org.json.JSONArray arr=new org.json.JSONArray(prefs.getString("library","[]"));
            for(int i=0;i<arr.length();i++){
                org.json.JSONObject item=arr.optJSONObject(i);
                if(item!=null && currentBook.equals(item.optString("uri"))){
                    item.put("index",at);
                    item.put("progress",ttsWordCount<=0?0:Math.min(100,(int)((long)(at+1)*100/ttsWordCount)));
                    item.put("openedAt",System.currentTimeMillis());
                    e.putString("library",arr.toString());
                    break;
                }
            }
            if(currentBook.equals(prefs.getString("uri","")))e.putInt("index",at);
        }catch(Exception ignored){}
    }
    private void startTts(){
        if(!ttsReady||tts==null||chunks.isEmpty()){ttsStartRequested=true;return;}
        if(interruptedByExternalAudio)return;
        if(!audioFocus.acquire()){paused=true;notifyState();return;}
        ttsStartRequested=false;tts.stop();localPending.clear();releaseLocalAudio();paused=false;
        speakChunk();notifyState();
    }
    private void speakChunk(){
        if(paused||tts==null||ttsPosition>=chunks.size()){paused=true;notifyState();return;}
        playLocalFile(ttsPosition);saveProgress();
    }
    @Override public void onInit(int status){ttsReady=status==TextToSpeech.SUCCESS;if(ttsReady){
        tts.setLanguage(new Locale("ru"));
        String name=getSharedPreferences("reader",0).getString("tts_voice","");
        if(!name.isEmpty()&&tts.getVoices()!=null)for(android.speech.tts.Voice v:tts.getVoices()){
            if(name.equals(v.getName())&&!v.isNetworkConnectionRequired()){tts.setVoice(v);break;}
        }
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String u){}
            @Override public void onBeginSynthesis(String id,int sampleRate,int audioFormat,int channelCount){
                if(!id.startsWith("local_"))return;
                try{String[] p=id.split("_");int g=Integer.parseInt(p[1]),idx=Integer.parseInt(p[2]);
                    if(g==localGeneration)localSampleRates.put(idx,sampleRate);
                }catch(Exception ignored){}
            }
            @Override public void onRangeStart(String id,int start,int end,int frame){
                if(!id.startsWith("local_"))return;
                try{String[] p=id.split("_");int g=Integer.parseInt(p[1]),idx=Integer.parseInt(p[2]);
                    if(g!=localGeneration||end<=start)return;
                    localWordFrames.computeIfAbsent(idx,k->java.util.Collections.synchronizedList(new java.util.ArrayList<>()))
                        .add(new int[]{start,end,frame});
                }catch(Exception ignored){}
            }
            @Override public void onDone(String u){handler.post(()->{
                if(u.startsWith("local_")){
                    String[] parts=u.split("_");if(parts.length!=3)return;
                    try{int g=Integer.parseInt(parts[1]),i=Integer.parseInt(parts[2]);
                        if(g!=localGeneration)return;
                        java.io.File target=localFile(i);
                        java.io.File pending=new java.io.File(target.getAbsolutePath()+".part");
                        if(pending.isFile()&&pending.length()>256){
                            if(target.exists())target.delete();
                            if(!pending.renameTo(target)){localPending.remove(i);if(i==ttsPosition)fallbackLocalSpeech(i);return;}
                            java.util.List<int[]> frames=localWordFrames.remove(i);
                            int rate=localSampleRates.containsKey(i)?localSampleRates.remove(i):0;
                            LocalTtsWordTimings.write(target,rate,frames);
                            getSharedPreferences("reader",0).edit()
                                .putBoolean("tts_word_checked:"+getSharedPreferences("reader",0).getString("tts_engine","")+":"+getSharedPreferences("reader",0).getString("tts_voice",""),true)
                                .putBoolean("tts_word_boundary:"+getSharedPreferences("reader",0).getString("tts_engine","")+":"+getSharedPreferences("reader",0).getString("tts_voice",""),frames!=null&&frames.size()>1&&rate>0).apply();
                        }
                        localPending.remove(i);
                        if(ttsMode&&!paused&&i==ttsPosition&&ttsAudio==null){
                            if(target.isFile()&&target.length()>256)playLocalFile(i);
                            else fallbackLocalSpeech(i);
                        }
                    }catch(Exception ignored){}
                    return;
                }
                if(!paused&&ttsMode&&u.startsWith("fallback_")){
                    ttsPosition++;if(ttsPosition<chunks.size())speakChunk();else{paused=true;audioFocus.release();notifyState();}
                }
            });}
            @Override public void onError(String u){handler.post(()->{
                if(u.startsWith("local_")){try{String[] parts=u.split("_");int g=Integer.parseInt(parts[1]),i=Integer.parseInt(parts[2]);
                    if(g!=localGeneration)return;
                    java.io.File partial=new java.io.File(localFile(i).getAbsolutePath()+".part");if(partial.exists())partial.delete();
                    localPending.remove(i);if(i==ttsPosition&&!paused)fallbackLocalSpeech(i);
                }catch(Exception ignored){}return;}
                paused=true;notifyState();
            });}
        });
        if(active&&ttsMode&&ttsStartRequested)startTts();
    }}

    /** Absolute source-text range. A voice without reliable callback metadata highlights the chunk. */
    public int[] currentTtsWordRange(){
        if(!ttsMode||ttsPosition<0||ttsPosition>=chunks.size())return new int[]{0,0};
        int base=ttsChunkCharPositions.get(ttsPosition);
        String chunk=chunks.get(ttsPosition);
        if(ttsAudio!=null){
            java.util.List<LocalTtsWordTimings.Mark> marks=LocalTtsWordTimings.read(localFile(ttsPosition));
            if(marks.size()>1){
                try{LocalTtsWordTimings.Mark mark=LocalTtsWordTimings.at(marks,ttsAudio.getCurrentPosition());
                    if(mark!=null&&mark.end<=chunk.length())return new int[]{base+mark.start,base+mark.end};
                }catch(Exception ignored){}
            }
        }
        return new int[]{base,base+chunk.length()};
    }
    public int getTtsAnchorWord(){return chunkWordPositions.isEmpty()?0:chunkWordPositions.get(Math.max(0,Math.min(ttsPosition,chunkWordPositions.size()-1)));}
    public int getTtsTotalWords(){return ttsWordCount;}
    public int getPosition(){
        if(ttsMode)return ttsPosition;
        if(player==null||!playerReady||seekPending)return preparedPosition;
        try{return player.getCurrentPosition();}catch(Exception e){return preparedPosition;}
    }
    public int getDuration(){return ttsMode?chunks.size():duration;}
    public boolean isPaused(){return paused;} public boolean isTtsMode(){return ttsMode;} public float getSpeed(){return speed;} public long getSleepRemainingMillis(){return sleepDeadline>0?Math.max(0,sleepDeadline-System.currentTimeMillis()):0;}public String getTitle(){return title;}public String getTtsBookUri(){return ttsMode?currentBook:"";}public int getSegment(){return segment;} public int getChapterCount(){return playlist.size();} public String getChapterName(int n){return n>=0&&n<chapterNames.size()?chapterNames.get(n):"Глава "+(n+1);} public String getBookId(){return bookId;} public String getCurrentText(){return ttsMode&&ttsPosition>=0&&ttsPosition<chunks.size()?chunks.get(ttsPosition):"";}
    private void saveProgress(){android.content.SharedPreferences.Editor e=getSharedPreferences("reader",0).edit();if(ttsMode){e.putInt("tts_pos:"+currentBook,ttsPosition);saveTtsReadingPosition(e);}else{e.putInt("audio_pos:"+bookId,getPosition()).putInt("audio_segment:"+bookId,segment).putString("audio_current_title",title).putInt("audio_progress:"+bookId,audioProgressPercent()).putFloat("audio_speed",speed);}e.putBoolean("audio_tts",ttsMode).apply();}
    private void notifyState(){updateMediaSession();Intent i=new Intent(ACTION_STATE);i.setPackage(getPackageName());sendBroadcast(i);if(active)postNotification();}
    private void postNotification(){Intent open=ttsMode?new Intent(this,EdgeOnlineActivity.class).putExtra("book_uri",currentBook).putExtra("tts_mode_local",true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP):new Intent(this,AudioPlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent view=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent toggle=PendingIntent.getService(this,1,new Intent(this,AudioPlaybackService.class).setAction(paused?PLAY:PAUSE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent prev=PendingIntent.getService(this,2,new Intent(this,AudioPlaybackService.class).setAction(PREV),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent next=PendingIntent.getService(this,3,new Intent(this,AudioPlaybackService.class).setAction(NEXT),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent close=PendingIntent.getService(this,4,new Intent(this,AudioPlaybackService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.MediaStyle mediaStyle=new Notification.MediaStyle().setShowActionsInCompactView(0,1,2);
        if(mediaSession!=null)mediaStyle.setMediaSession(mediaSession.getSessionToken());
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Notification notification=builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(ttsMode?"Озвучивание книги":"Аудиокнига").setContentIntent(view).setDeleteIntent(close).setOnlyAlertOnce(true).setOngoing(false).addAction(android.R.drawable.ic_media_previous,"Назад",prev).addAction(paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,paused?"Играть":"Пауза",toggle).addAction(android.R.drawable.ic_media_next,"Вперёд",next).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Закрыть",close).setStyle(mediaStyle).build();try{if(Build.VERSION.SDK_INT>=29)startForeground(41,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(41,notification);}catch(Exception ignored){}
    }
    private void setupMediaSession() {
        mediaSession = new MediaSession(this, "ChitalkaRSVP-audio");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { AudioModeCoordinator.stopEdge(AudioPlaybackService.this);resume(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onStop() { stopServicePlayback(); }
            @Override public void onSkipToNext() { jump(1); }
            @Override public void onSkipToPrevious() { jump(-1); }
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent e=intent==null?null:intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (e!=null && e.getAction()==KeyEvent.ACTION_DOWN && e.getRepeatCount()==0) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            if(paused)onPlay();else onPause();return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY:onPlay();return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:onPause();return true;
                        case KeyEvent.KEYCODE_MEDIA_STOP:onStop();return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:onSkipToNext();return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:onSkipToPrevious();return true;
                    }
                }
                return super.onMediaButtonEvent(intent);
            }
        }, handler);
        mediaSession.setActive(true);
        updateMediaSession();
    }
    private void updateMediaSession() {
        if(mediaSession==null) return;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE
                |PlaybackState.ACTION_PLAY_PAUSE|PlaybackState.ACTION_STOP
                |PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS;
        int state = !active?PlaybackState.STATE_STOPPED:
                paused?PlaybackState.STATE_PAUSED:PlaybackState.STATE_PLAYING;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state, ttsMode?PlaybackState.PLAYBACK_POSITION_UNKNOWN:getPosition(),
                        paused?0f:speed).build());
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);try{unregisterReceiver(noisyReceiver);}catch(Exception ignored){}audioFocus.release();if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();mediaSession=null;}saveProgress();releasePlayer();releaseLocalAudio();if(tts!=null){tts.stop();tts.shutdown();}AudioEqualizer.release();instance=null;super.onDestroy();}
}
