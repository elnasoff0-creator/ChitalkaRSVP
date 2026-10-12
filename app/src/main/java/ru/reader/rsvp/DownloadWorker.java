package ru.reader.rsvp;
import android.app.*;
import android.content.*;
import android.os.*;

/** Foreground download service: sequential queue, persisted after every update. */
public class DownloadWorker extends Service {
    public static final String START="ru.reader.rsvp.DOWNLOAD_START",PAUSE="ru.reader.rsvp.DOWNLOAD_PAUSE",CANCEL="ru.reader.rsvp.DOWNLOAD_CANCEL";
    static final String CHANNEL="audio_downloads";volatile boolean halt=false, cancel=false;Thread thread;String source="";
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Загрузки аудиокниг",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    Notification note(String msg,int done,int total){Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);n.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Загрузка аудиокниги").setContentText(msg).setOnlyAlertOnce(true).setOngoing(true).setProgress(total,done,false);return n.build();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){if(intent==null)return START_NOT_STICKY;String action=intent.getAction();String requested=intent.getStringExtra("source");if(requested==null)return START_NOT_STICKY;
        if(PAUSE.equals(action)||CANCEL.equals(action)){halt=true;cancel=CANCEL.equals(action);BookImportManager.Job j=lookup(requested);if(j!=null){j.paused=!cancel;j.cancelled=cancel;j.status=cancel?"cancelled":"paused";BookImportManager.put(this,j);}return START_NOT_STICKY;}
        if(START.equals(action)){if(thread!=null&&thread.isAlive())return START_STICKY;source=requested;halt=false;cancel=false;BookImportManager.Job j=lookup(source);if(j==null)return START_NOT_STICKY;j.paused=false;j.cancelled=false;j.status="running";BookImportManager.put(this,j);startForeground(4112,note(j.title,0,100));thread=new Thread(()->runQueue(j),"AudioBookDownloader");thread.start();}return START_STICKY;}
    BookImportManager.Job lookup(String s){for(BookImportManager.Job j:BookImportManager.load(this))if(j.source.equals(s))return j;return null;}
    void runQueue(BookImportManager.Job job){try{int count=job.chapters.size();for(int i=0;i<count;i++){if(halt)break;if(i>=job.states.size()||job.states.get(i)==-1)continue;final int index=i;if(job.states.get(i)==100&&BookImportManager.trackFile(this,job,i).exists()&&KvuheDownloader.isPlayable(BookImportManager.trackFile(this,job,i)))continue;
            long[] recent={0,android.os.SystemClock.elapsedRealtime()};job.index=i;KvuheDownloader.download(this,job,i,new KvuheDownloader.Callback(){public boolean cancelled(){return halt;}public void progress(long done,long total){long now=android.os.SystemClock.elapsedRealtime();if(now-recent[1]<600&&done!=total)return;long elapsed=Math.max(1,now-recent[1]);long speed=(done-recent[0])*1000/elapsed;recent[0]=done;recent[1]=now;int p=total>0?(int)Math.min(99,done*100/total):0;job.states.set(index,p);job.bytes=done;job.total=total;BookImportManager.put(DownloadWorker.this,job);int completed=0;for(Integer x:job.states)if(x==100)completed++;String label=(completed+1)+"/"+count+" · "+(done/1024)+" KB · "+(speed/1024)+" KB/s";((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(4112,note(label,(completed*100+p)/Math.max(1,count),100));}});
            job.states.set(i,100);BookImportManager.put(this,job);BookImportManager.updateLibrary(this,job);
        }if(!halt){job.status="finished";}else job.status=cancel?"cancelled":"paused";
        }catch(Exception e){job.status=halt?(cancel?"cancelled":"paused"):"error";job.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}finally{BookImportManager.put(this,job);stopForeground(true);stopSelf();}}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
