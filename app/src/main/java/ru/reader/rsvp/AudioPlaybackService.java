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
    private long sleepDeadline=0;
    private ArrayList<String> playlist=new ArrayList<>(), chunks=new ArrayList<>(), chapterNames=new ArrayList<>();
    private String title="Аудиокнига", currentBook="", bookId="";
    private Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){if(active){if(!ttsMode&&player!=null){try{if(player.isPlaying())saveProgress();}catch(Exception ignored){}}if(sleepDeadline>0&&System.currentTimeMillis()>=sleepDeadline){pausePlayback();sleepDeadline=0;}notifyState();handler.postDelayed(this,1000);}}};
    @Override public void onCreate(){super.onCreate();instance=this;android.app.NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Аудиокниги",NotificationManager.IMPORTANCE_LOW));tts=new TextToSpeech(this,this);handler.post(tick);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_STICKY;String action=intent.getAction();if(action==null)return START_STICKY;switch(action){
        case LOAD: {String[] uris=intent.getStringArrayExtra("uris");playlist.clear();if(uris!=null)for(String u:uris)if(u!=null)playlist.add(u);title=intent.getStringExtra("title");if(title==null)title="Аудиокнига";
          bookId=intent.getStringExtra("bookId");if(bookId==null)bookId=playlistKey();
          chapterNames.clear();String[] names=intent.getStringArrayExtra("chapters");if(names!=null)chapterNames.addAll(Arrays.asList(names));
          ttsMode=false;active=true;
          segment=Math.max(0,Math.min(Math.max(0,playlist.size()-1),getSharedPreferences("reader",0).getInt("audio_segment:"+bookId,0)));
          int resume=getSharedPreferences("reader",0).getInt("audio_pos:"+bookId,0);
          prepareAudio(resume,false);break;}
        case TTS: {title=intent.getStringExtra("title");currentBook=intent.getStringExtra("bookUri");final String source=currentBook;
          ttsMode=true;active=true;paused=true;releasePlayer();
          new Thread(()->{try{BookLoader.Book book=BookLoader.load(this,Uri.parse(source));handler.post(()->{if(!ttsMode||!source.equals(currentBook))return;makeChunks(book.text);ttsPosition=Math.max(0,Math.min(chunks.size()-1,intent.getIntExtra("position",getSharedPreferences("reader",0).getInt("tts_pos:"+source,0))));ttsStartRequested=true;startTts();});}catch(Exception ignored){handler.post(()->{paused=true;notifyState();});}}).start();break;}
        case PLAY: resume();break;case PAUSE: pausePlayback();break;
        case STOP: pausePlayback();stopForeground(STOP_FOREGROUND_REMOVE);active=false;stopSelf();break;
        case NEXT: jump(ttsMode?1:1);break;case PREV:jump(-1);break;
        case SEEK:seekTo(intent.getIntExtra("position",0));break;
        case SPEED: speed=Math.max(.5f,Math.min(3f,intent.getFloatExtra("speed",1)));if(tts!=null)tts.setSpeechRate(speed);applySpeed();saveProgress();break;
        case SLEEP: {int minutes=intent.getIntExtra("minutes",0);sleepDeadline=minutes<=0?0:System.currentTimeMillis()+minutes*60000L;break;}
        case VOICE: {float p=intent.getFloatExtra("pitch",1f);pitch=Math.max(.6f,Math.min(1.5f,p));if(tts!=null)tts.setPitch(pitch);break;}
        case CHAPTER: {int target=intent.getIntExtra("chapter",0);if(!ttsMode&&target>=0&&target<playlist.size()){segment=target;prepareAudio(0,!paused);}break;}
    }notifyState();return START_STICKY;}
    private String playlistKey(){return playlist.isEmpty()?"":playlist.get(0);}
    private void prepareAudio(int offset,boolean autoplay){releasePlayer();paused=true;duration=0;notifyState();if(playlist.isEmpty())return;try{Uri uri=Uri.parse(playlist.get(segment));player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());player.setDataSource(this,uri);player.setOnPreparedListener(mp->{duration=mp.getDuration();mp.seekTo(Math.max(0,Math.min(offset,duration)));applySpeed();if(autoplay)mp.start();paused=!autoplay;saveProgress();notifyState();});player.setOnCompletionListener(mp->{if(segment+1<playlist.size()){segment++;prepareAudio(0,true);}else pausePlayback();});player.prepareAsync();}catch(Exception e){paused=true;notifyState();}}
    private void releasePlayer(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    private void applySpeed(){if(player!=null)try{player.setPlaybackParams(player.getPlaybackParams().setSpeed(speed));}catch(Exception ignored){}}
    private void resume(){if(ttsMode){startTts();return;}if(player!=null)try{player.start();applySpeed();paused=false;}catch(Exception ignored){}notifyState();}
    private void pausePlayback(){if(ttsMode){if(tts!=null)tts.stop();paused=true;}else if(player!=null){try{if(player.isPlaying())player.pause();}catch(Exception ignored){}paused=true;}saveProgress();notifyState();}
    private void seekTo(int pos){if(ttsMode){ttsPosition=Math.max(0,Math.min(chunks.size()-1,pos));if(!paused)startTts();}else if(player!=null){player.seekTo(Math.max(0,Math.min(duration,pos)));}saveProgress();notifyState();}
    private void jump(int change){if(ttsMode){seekTo(ttsPosition+change);return;}if(change>0){if(segment+1<playlist.size()){segment++;prepareAudio(0,!paused);}else seekTo(getPosition()+30000);}else {if(getPosition()>15000)seekTo(getPosition()-15000);else if(segment>0){segment--;prepareAudio(0,!paused);}else seekTo(0);}}
    private void makeChunks(String full){chunks.clear();String[] sentences=full.split("(?<=[.!?…])\\s+|\\n{2,}");for(String s:sentences){s=s.trim();if(s.isEmpty())continue;for(int k=0;k<s.length();k+=850){int end=Math.min(s.length(),k+850);chunks.add(s.substring(k,end));}}if(chunks.isEmpty())chunks.add(full.length()>0?full:" ");}
    private void startTts(){if(!ttsReady||tts==null||chunks.isEmpty()){ttsStartRequested=true;return;}ttsStartRequested=false;tts.stop();paused=false;tts.setSpeechRate(speed);tts.setPitch(pitch);speakChunk();notifyState();}
    private void speakChunk(){if(paused||tts==null||ttsPosition>=chunks.size()){paused=true;notifyState();return;}Bundle p=new Bundle();tts.speak(chunks.get(ttsPosition),TextToSpeech.QUEUE_FLUSH,p,"chunk_"+ttsPosition);saveProgress();}
    @Override public void onInit(int status){ttsReady=status==TextToSpeech.SUCCESS;if(ttsReady){tts.setLanguage(new Locale("ru"));tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){@Override public void onStart(String u){}@Override public void onDone(String u){handler.post(()->{if(!paused&&ttsMode){ttsPosition++;if(ttsPosition<chunks.size())speakChunk();else {paused=true;notifyState();}}});}@Override public void onError(String u){handler.post(()->{paused=true;notifyState();});}});if(active&&ttsMode&&ttsStartRequested)startTts();}}
    public int getPosition(){if(ttsMode)return ttsPosition;try{return player==null?0:player.getCurrentPosition();}catch(Exception e){return 0;}}
    public int getDuration(){return ttsMode?chunks.size():duration;}
    public boolean isPaused(){return paused;} public boolean isTtsMode(){return ttsMode;} public float getSpeed(){return speed;}public String getTitle(){return title;}public int getSegment(){return segment;} public int getChapterCount(){return playlist.size();} public String getChapterName(int n){return n>=0&&n<chapterNames.size()?chapterNames.get(n):"Глава "+(n+1);} public String getBookId(){return bookId;} public String getCurrentText(){return ttsMode&&ttsPosition>=0&&ttsPosition<chunks.size()?chunks.get(ttsPosition):"";}
    private void saveProgress(){android.content.SharedPreferences.Editor e=getSharedPreferences("reader",0).edit();if(ttsMode)e.putInt("tts_pos:"+currentBook,ttsPosition);else e.putInt("audio_pos:"+bookId,getPosition()).putInt("audio_segment:"+bookId,segment);e.putFloat("audio_speed",speed).putString("audio_current_title",title).putBoolean("audio_tts",ttsMode).apply();}
    private void notifyState(){Intent i=new Intent(ACTION_STATE);i.setPackage(getPackageName());sendBroadcast(i);if(active)postNotification();}
    private void postNotification(){Intent open=new Intent(this,AudioPlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);PendingIntent view=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent toggle=PendingIntent.getService(this,1,new Intent(this,AudioPlaybackService.class).setAction(paused?PLAY:PAUSE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent prev=PendingIntent.getService(this,2,new Intent(this,AudioPlaybackService.class).setAction(PREV),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);PendingIntent next=PendingIntent.getService(this,3,new Intent(this,AudioPlaybackService.class).setAction(NEXT),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Notification notification=builder.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(title).setContentText(ttsMode?"Озвучивание книги":"Аудиокнига").setContentIntent(view).setOngoing(!paused).addAction(android.R.drawable.ic_media_previous,"Назад",prev).addAction(paused?android.R.drawable.ic_media_play:android.R.drawable.ic_media_pause,paused?"Играть":"Пауза",toggle).addAction(android.R.drawable.ic_media_next,"Вперёд",next).setStyle(new Notification.MediaStyle().setShowActionsInCompactView(0,1,2)).build();try{if(Build.VERSION.SDK_INT>=29)startForeground(41,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);else startForeground(41,notification);}catch(Exception ignored){}
    }
    @Override public android.os.IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){active=false;handler.removeCallbacks(tick);saveProgress();releasePlayer();if(tts!=null){tts.stop();tts.shutdown();}instance=null;super.onDestroy();}
}
