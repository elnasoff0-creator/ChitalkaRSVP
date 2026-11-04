package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/** One-tap foreground playback; newest tapped box supersedes pending and playing clips. */
public final class ComicOcrSpeechService extends Service {
    static final String ACTION_SPEAK="ru.reader.rsvp.comic.OCR_SPEAK";
    static final String ACTION_STOP="ru.reader.rsvp.comic.OCR_STOP";
    static final String EXTRA_TEXT="ocr_text";
    private static final int NOTIFICATION_ID=1932;
    private final AtomicInteger sequence=new AtomicInteger();
    private EdgeTtsClient request;private MediaPlayer player;private File sound;
    static void speak(Context context,String text){
        Intent i=new Intent(context,ComicOcrSpeechService.class).setAction(ACTION_SPEAK).putExtra(EXTRA_TEXT,text);
        androidx.core.content.ContextCompat.startForegroundService(context,i);
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        int serial=sequence.incrementAndGet();release();
        if(ACTION_STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        String text=intent.getStringExtra(EXTRA_TEXT);
        if(!ACTION_SPEAK.equals(intent.getAction())||text==null||text.trim().isEmpty()){stopSelf();return START_NOT_STICKY;}
        if(text.length()>1800)text=text.substring(0,1800);
        ensureForeground();
        AudioModeCoordinator.pauseLocal(this);
        sendBroadcast(new Intent(AudioModeCoordinator.STOP_EDGE).setPackage(getPackageName()));
        android.content.SharedPreferences p=getSharedPreferences("reader",0);
        String voice=p.getString("edge_voice","ru-RU-SvetlanaNeural"),rate=p.getString("edge_rate","+0%");
        EdgeSpeechTextTransform.Result transformed=EdgeSpeechTextTransform.Options.read(p).apply(text);
        if(transformed.skipSynthesis()||transformed.spoken.trim().isEmpty()){stopSelf();return START_NOT_STICKY;}
        request=EdgeTtsClient.synthesize(transformed.spoken,voice,rate,new EdgeTtsClient.Callback(){
            @Override public void progress(int chunks,long bytes){}
            @Override public void finished(byte[] mp3,int chunks,String error){
                new android.os.Handler(getMainLooper()).post(()->{
                    if(serial!=sequence.get())return;
                    request=null;
                    if(error!=null||mp3==null||mp3.length<128){android.widget.Toast.makeText(ComicOcrSpeechService.this,
                        "Ошибка озвучивания OCR"+(error==null?"":": "+error),android.widget.Toast.LENGTH_SHORT).show();stopSelf();return;}
                    try{
                        sound=File.createTempFile("comic-ocr-",".mp3",getCacheDir());
                        try(FileOutputStream out=new FileOutputStream(sound)){out.write(mp3);}
                        player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                        player.setDataSource(sound.getAbsolutePath());
                        player.setOnCompletionListener(m->stopSelf());
                        player.setOnErrorListener((m,what,extra)->{stopSelf();return true;});
                        player.prepare();player.start();
                    }catch(Exception e){stopSelf();}
                });
            }
        });
        return START_NOT_STICKY;
    }
    private void ensureForeground(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("comic_ocr_speech","Озвучка комиксов",NotificationManager.IMPORTANCE_LOW));
        Intent stop=new Intent(this,ComicOcrSpeechService.class).setAction(ACTION_STOP);
        android.app.PendingIntent pending=android.app.PendingIntent.getService(this,0,stop,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new NotificationCompat.Builder(this,"comic_ocr_speech")
            .setSmallIcon(R.drawable.ic_tts_engine).setContentTitle("ПРОЧиталка · OCR")
            .setContentText("Озвучивание выбранного блока")
            .setOngoing(true).addAction(0,"Остановить",pending).build();
        startForeground(NOTIFICATION_ID,notification);
    }
    private void release(){if(request!=null){request.cancel();request=null;}
        if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}
        if(sound!=null){sound.delete();sound=null;}}
    @Override public void onDestroy(){sequence.incrementAndGet();release();super.onDestroy();}
}
