package ru.reader.rsvp;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.content.pm.ServiceInfo;
import java.util.*;

/** Background playback for local audio and system TTS. Never stores private files in a public directory. */
public class AudioPlaybackService extends Service implements TextToSpeech.OnInitListener {
    public static final String CHANNEL="reader_audio", ACTION_STATE="ru.reader.rsvp.AUDIO_STATE";
    public static final String LOAD="load", PLAY="play", PAUSE="pause", SEEK="seek", NEXT="next", PREV="prev", STOP="stop", SPEED="speed", SLEEP="sleep", TTS="tts", VOICE="voice", CHAPTER="chapter";
    public static AudioPlaybackService instance;
    private MediaPlayer player;
    private TextToSpeech tts;
    private boolean ttsReady=false, ttsStartRequested=false, active=false, paused=true, ttsMode=false;
    private float speed=1.0f, pitch=1.0f;
    private int segment=0, ttsPosition=0, duration=0;
    private final ArrayList<Long> chapterDurations=new ArrayList<>();
    private long durationGeneration=0;
    private final ArrayList<Integer> chunkWordPositions=new ArrayList<>();
    private int ttsWordCount=0;
    private long sleepDeadline=0;
    private double pendingChapterFraction=0;
    private ArrayList<String> playlist=new ArrayList<>(), chunks=new ArrayList<>(), chapterNames=new ArrayList<>();
    private String title="Аудиокнига", currentBook="", bookId="";
    private Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(active){if(!ttsMode&&player!=null){try{if(player.isPlaying())saveProgress();}catch(Exception ignored){}}if(sleepDeadline>0&&System.currentTimeMillis()>=sleepDeadline){pausePlayback();sleepDeadline=0;}notifyState();handler.postDelayed(this,1000);}}};
    @Override public void onCreate(){super.onCreate();instance=this;android.app.NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Аудиокниги",NotificationManager.IMPORTANCE_LOW));tts=new TextToSpeech(this,this);handler.post(tick);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_STICKY;String action=intent.getAction();if(action==null)return START_STICKY;switch(action){
        case LOAD: {if(active)saveProgress(); if(tts!=null&&ttsMode)tts.stop(); String[] uris=intent.getStringArrayExtra("uris");playlist.clear();if(uris!=null)for(String u:uris)if(u!=null)playlist.add(u);title=intent.getStringExtra("title");if(title==null)title="Аудиокнига";
          bookId=intent.getStringExtra("bookId");if(bookId==null)bookId=playlistKey();getSharedPreferences("reader",0).edit().putString("audio_last_book",bookId).apply();
          chapterNames.clear();String[] names=intent.getStringArrayExtra("chapters");if(names!=null)chapterNames.addAll(Arrays.asList(names));
          ttsMode=false;active=true;loadChapterDurations();speed=getSharedPreferences("reader",0).getFloat("audio_speed",1f);
          segment=Math.max(0,Math.min(Math.max(0,playlist.size()-1),getSharedPreferences("reader",0).getInt("audio_segment:"+bookId,0)));
          int resume=getSharedPreferences("reader",0).getInt("audio_pos:"+bookId,0);
          prepareAudio(resume,intent.getBooleanExtra("autoplay",false));break;}
        case TTS: {if(active)saveProgress(); if(tts!=null)tts.stop(); title=intent.getStringExtra("title");currentBook=intent.getStringExtra("bookUri");final String source=currentBook;
          ttsMode=true;active=true;paused=true;releasePlayer();
          new Thread(()->{try{BookLoader.Book book=BookLoader.load(this,Uri.parse(source));handler.post(()->{if(!ttsMode||!source.equals(currentBook))return;makeChunks(book.text);int requestedWord=intent.getIntExtra("word_index",-1); int fallback=getSharedPreferences("reader",0).getInt("tts_pos:"+source,0); ttsPosition=requestedWord>=0?chunkForWord(requestedWord):Math.max(0,Math.min(chunks.size()-1,fallback));ttsStartRequested=true;startTts();});}catch(Exception ignored){handler.post(()->{paused=true;notifyState();});}}).start();break;}
        case PLAY: resume();break;case PAUSE: pausePlayback();break;
        case STOP: stopServicePlayback();break;
        case NEXT: jump(ttsMode?1:1);break;case PREV:jump(-1);break;
        case SEEK:seekTo(intent.getIntExtra("position",0));break;
        case SPEED: speed=Math.max(.5f,Math.min(3f,intent.getFloatExtra("speed",1)));if(tts!=null)tts.setSpeechRate(speed);if(!paused)applySpeed();saveProgress();notifyState();break;
        case SLEEP: {int minutes=intent.getIntExtra("minutes",0);sleepDeadline=minutes<=0?0:System.currentTimeMillis()+minutes*60000L;getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",sleepDeadline).apply();break;}
        case VOICE: {float p=intent.getFloatExtra("pitch",1f);pitch=Math.max(.6f,Math.min(1.5f,p));if(tts!=null)tts.setPitch(pitch);break;}
        case CHAPTER: {int target=intent.getIntExtra("chapter",0);if(!ttsMode&&target>=0&&target<playlist.size()){boolean shouldPlay=false;segment=target;pendingChapterFraction=Math.max(0,Math.min(1,intent.getDoubleExtra("fraction",0)));prepareAudio(0,shouldPlay);}break;}
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
    private void prepareAudio(int offset,boolean autoplay){releasePlayer();paused=true;duration=0;notifyState();if(playlist.isEmpty())return;try{Uri uri=Uri.parse(playlist.get(segment));player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(this,uri);player.setOnPreparedListener(mp->{duration=mp.getDuration();if(segment>=0&&segment<chapterDurations.size()&&duration>0)chapterDurations.set(segment,(long)duration);int target=pendingChapterFraction>0?(int)(pendingChapterFraction*duration):offset;pendingChapterFraction=0;mp.seekTo(Math.max(0,Math.min(target,duration)));if(autoplay){mp.start();paused=false;applySpeed();}else{paused=true;}saveProgress();notifyState();});player.setOnCompletionListener(mp->{if(segment+1<playlist.size()){segment++;prepareAudio(0,true);}else pausePlayback();});player.prepareAsync();}catch(Exception e){paused=true;notifyState();}}
    private void releasePlayer(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    private void applySpeed(){if(player!=null&&!paused)try{player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));}catch(Exception ignored){}}
    private void resume(){if(ttsMode){startTts();return;}if(player!=null)try{player.start();paused=false;applySpeed();}catch(Exception ignored){}notifyState();}
    private void pausePlayback(){if(ttsMode){if(tts!=null)tts.stop();paused=true;}else if(player!=null){try{if(player.isPlaying())player.pause();}catch(Exception ignored){}paused=true;}saveProgress();notifyState();}
    private void stopServicePlayback(){if(ttsMode){if(tts!=null)tts.stop();}else if(player!=null){try{if(player.isPlaying())player.pause();}catch(Exception ignored){}}paused=true;saveProgress();releasePlayer();active=false;Intent i=new Intent(ACTION_STATE);i.setPackage(getPackageName());sendBroadcast(i);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
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
    private void seekTo(int pos){if(ttsMode){ttsPosition=Math.max(0,Math.min(chunks.size()-1,pos));if(!paused)startTts();}else if(player!=null){player.seekTo(Math.max(0,Math.min(duration,pos)));}saveProgress();notifyState();}
    private void jump(int change){if(ttsMode){seekTo(ttsPosition+change);return;}if(change>0){if(segment+1<playlist.size()){segment++;prepareAudio(0,!paused);}else seekTo(getPosition()+30000);}else {if(getPosition()>15000)seekTo(getPosition()-15000);else if(segment>0){segment--;prepareAudio(0,!paused);}else seekTo(0);}}
    private int chunkForWord(int wordIndex){
        if(chunkWordPositions.isEmpty())return 0;
        int at=java.util.Collections.binarySearch(chunkWordPositions,Math.max(0,wordIndex));
        return Math.max(0,Math.min(chunkWordPositions.size()-1,at>=0?at:-at-2));
    }
    private void makeChunks(String full){
        chunks.clear();chunkWordPositions.clear();ttsWordCount=0;
        if(full==null)full="";
        // Keep a stable word offset for each spoken chunk, independently of the MP3 position.
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("\\S+").matcher(full);
        ArrayList<Integer> wordOffsets=new ArrayList<>();
        while(matcher.find())wordOffsets.add(matcher.start());
        ttsWordCount=wordOffsets.size();
        java.util.regex.Matcher sentences=java.util.regex.Pattern.compile("(?<=[.!?…])\\s+|\\n{2,}").matcher(full);
        int sentenceStart=0;
        do {
            int sentenceEnd=sentences.find()?sentences.start():full.length();
            for(int charStart=sentenceStart;charStart<sentenceEnd;charStart+=850){
                int charEnd=Math.min(sentenceEnd,charStart+850);
                String chunk=full.substring(charStart,charEnd);
                if(!chunk.trim().isEmpty()){
                    int wi=java.util.Collections.binarySearch(wordOffsets,charStart);
                    if(wi<0)wi=-wi-1;
                    chunkWordPositions.add(Math.max(0,Math.min(wi,Math.max(0,ttsWordCount-1))));
                    chunks.add(chunk);
                }
            }
            if(sentenceEnd==full.length())break;
            sentenceStart=sentences.end();
        }while(sentenceStart<full.length());
        if(chunks.isEmpty()){chunks.add(" ");chunkWordPositions.add(0);}
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
    private void startTts(){if(!ttsReady||tts==null||chunks.isEmpty()){ttsStartRequested=true;return;}ttsStartRequested=false;tts.stop();paused=false;tts.setSpeechRate(speed);tts.setPitch(pitch);speakChunk();notifyState();}
    private void speakChunk(){if(paused||tts==null||ttsPosition>=chunks.size()){paused=true;notifyState();return;}Bundle p=new Bundle();tts.speak(chunks.get(ttsPosition),TextToSpeech.QUEUE_FLUSH,p,"chunk_"+ttsPosition);saveProgress();}
    @Override public void onInit(int status){ttsReady=status==TextToSpeech.SUCCESS;if(ttsReady){tts.setLanguage(new Locale("ru"));tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){@Override public void onStart(String u){}@Override public void onDone(String u){handler.post(()->{if(!paused&&ttsMode){ttsPosition++;if(ttsPosition<chunks.size())speakChunk();else {paused=true;notifyState();}}});}@Override public void onError(String u){handler.post(()->{paused=true;notifyState();});}});if(active&&ttsMode&&ttsStartRequested)startTts();}}
    public int getPosition(){if(ttsMode)return ttsPosition;try{return player==null?0:player.getCurrentPosition();}catch(Exception e){return 0;}}
    public int getDuration(){return ttsMode?chunks.size():duration;}
    public boolean isPaused(){return paused;} public boolean isTtsMode(){return ttsMode;} public float getSpeed(){return speed;} public long getSleepRemainingMillis(){return sleepDeadline>0?Math.max(0,sleepDeadline-System.currentTimeMillis()):0;}public String getTitle(){return title;}public int getSegment(){return segment;} public int getChapterCount(){return playlist.size();} public String getChapterName(int n){return n>=0&&n<chapterNames.size()?chapterNames.get(n):"Глава "+(n+1);} public String getBookId(){return bookId;} public String getCurrentText(){return ttsMode&&ttsPosition>=0&&ttsPosition<chunks.size()?chunks.get(ttsPosition):"";}
    private void saveProgress(){android.content.SharedPreferences.Editor e=getSharedPreferences("reader",0).edit();if(ttsMode){e.putInt("tts_pos:"+currentBook,ttsPosition);saveTtsReadingPosition(e);}else{e.putInt("audio_pos:"+bookId,getPosition()).putInt("audio_segment:"+bookId,segment).putString("audio_current_title",title).putInt("audio_progress:"+bookId,audioProgressPercent());}e.putFloat("audio_speed",speed).putBoolean("audio_tts",ttsMode).apply();}
    private void notifyState(){Intent i=new Intent(ACTION_STATE);i.setPackage(getPackageName());sendBroadcast(i);if(active)postNotification();}
    private void postNotification(){Intent open=new Intent(this,AudioPlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent view=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent toggle=PendingIntent.getService(this,1,new Intent(this,AudioPlaybackService.class).setAction(paused?PLAY:PAUSE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent prev=PendingIntent.getService(this,2,new Intent(this,AudioPlaybackService.class).setAction(PREV),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent next=PendingIntent.getService(this,3,new Intent(this,AudioPlaybackService.class).setAction(NEXT),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent close=PendingIntent.getService(this,4,new Intent(this,AudioPlaybackService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Notification notification=builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(ttsMode?"Озвучивание книги":"Аудиокнига").setContentIntent(view).setDeleteIntent(close).setOnlyAlertOnce(true).setOngoing(false).addAction(android.R.drawable.ic_media_previous,"Назад",prev).addAction(paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,paused?"Играть":"Пауза",toggle).addAction(android.R.drawable.ic_media_next,"Вперёд",next).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Закрыть",close).setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0,1,2)).build();try{if(Build.VERSION.SDK_INT>=29)startForeground(41,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(41,notification);}catch(Exception ignored){}
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);saveProgress();releasePlayer();if(tts!=null){tts.stop();tts.shutdown();}instance=null;super.onDestroy();}
}
