package ru.reader.rsvp;
import android.app.*;
import android.content.*;
import android.os.*;

/** Foreground download service: sequential queue, persisted after every update. */
public class DownloadWorker extends Service {
    public static final String START="ru.reader.rsvp.DOWNLOAD_START",PAUSE="ru.reader.rsvp.DOWNLOAD_PAUSE",CANCEL="ru.reader.rsvp.DOWNLOAD_CANCEL";
    static final String CHANNEL="audio_downloads";volatile boolean halt=false, cancel=false;Thread thread;String source="";
    @Override public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Загрузки аудиокниг",NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    Notification note(String msg,int done,int total){Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);Intent open=new Intent(this,UnifiedDownloadManagerActivity.class)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_ID,"audio:"+source)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_TYPE,DownloadsRepository.AUDIO)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        android.app.PendingIntent click=android.app.PendingIntent.getActivity(this,
            Math.abs(source.hashCode()%15000)+4100,open,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?android.app.PendingIntent.FLAG_IMMUTABLE:0));
        n.setContentIntent(click);n.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Загрузка аудиокниги").setContentText(msg).setOnlyAlertOnce(true).setOngoing(true).setProgress(total,done,false);return n.build();}
    private final java.util.concurrent.ExecutorService commandLane=
        java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.ArrayDeque<String> waiting=new java.util.ArrayDeque<>();
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;
        String action=intent.getAction();String requested=intent.getStringExtra("source");
        if(requested==null||requested.isEmpty())return START_NOT_STICKY;
        // Foreground acknowledgement must not wait for full JSON/SAF queue load.
        if(START.equals(action)&&(thread==null||!thread.isAlive()))try{
            startForeground(4112,note("Подготовка загрузки",0,100));
        }
        catch(Exception error){android.util.Log.w("AudioDownloads","Foreground unavailable",error);}
        commandLane.execute(()->handleCommand(action,requested));
        return START_STICKY;
    }
    private void handleCommand(String action,String requested){
        if(PAUSE.equals(action)||CANCEL.equals(action)){
            boolean cancelled=CANCEL.equals(action);
            waiting.remove(requested);
            if(requested.equals(source)&&thread!=null&&thread.isAlive()){
                halt=true;cancel=cancelled;
            }
            BookImportManager.Job job=lookup(requested);
            if(job!=null){job.paused=!cancelled;job.cancelled=cancelled;
                job.status=cancelled?"cancelled":"paused";BookImportManager.put(this,job);}
            if(thread==null||!thread.isAlive())startNext();
            return;
        }
        if(!START.equals(action))return;
        if(requested.equals(source)&&thread!=null&&thread.isAlive())return;
        if(!waiting.contains(requested))waiting.addLast(requested);
        BookImportManager.Job job=lookup(requested);
        if(job!=null){job.paused=false;job.cancelled=false;job.status="queued";
            BookImportManager.put(this,job);}
        startNext();
    }
    /** Only one network worker at a time, but no Play command for another book is lost. */
    private void startNext(){
        if(thread!=null&&thread.isAlive())return;
        while(!waiting.isEmpty()){
            String next=waiting.removeFirst();
            BookImportManager.Job job=lookup(next);
            if(job==null||job.paused||job.cancelled)continue;
            source=next;halt=false;cancel=false;
            job.status="running";BookImportManager.put(this,job);
            thread=new Thread(()->runQueue(job),"AudioBookDownloader");
            thread.start();return;
        }
        stopForeground(true);stopSelf();
    }
    BookImportManager.Job lookup(String s){for(BookImportManager.Job j:BookImportManager.load(this))if(j.source.equals(s))return j;return null;}
    void runQueue(BookImportManager.Job job){try{
        // Artwork is downloaded independently; failure never prevents MP3 downloading.
        AudiobookCoverStore.ensure(this,job);
        exportAudioCover(job);
        BookImportManager.updateLibrary(this,job);
        int count=job.chapters.size();for(int i=0;i<count;i++){if(halt)break;if(i>=job.states.size()||job.states.get(i)==-1)continue;final int index=i;if(job.states.get(i)==100&&!BookImportManager.relocated(job,i).isEmpty()){
                try{
                    androidx.documentfile.provider.DocumentFile existing=androidx.documentfile.provider.DocumentFile.fromSingleUri(
                        this,android.net.Uri.parse(BookImportManager.relocated(job,i)));
                    if(existing!=null&&existing.isFile()&&existing.length()>1024)continue;
                }catch(Exception ignored){}
            }
            if(job.states.get(i)==100&&BookImportManager.trackFile(this,job,i).exists()&&KvuheDownloader.isPlayable(BookImportManager.trackFile(this,job,i))){
                publishAudioTrack(job,i);continue;}
            long[] recent={0,android.os.SystemClock.elapsedRealtime()};
            final long[] lastPersist={0};job.index=i;KvuheDownloader.download(this,job,i,new KvuheDownloader.Callback(){public boolean cancelled(){return halt;}public void progress(long done,long total){long now=android.os.SystemClock.elapsedRealtime();if(now-recent[1]<600&&done!=total)return;long elapsed=Math.max(1,now-recent[1]);long speed=(done-recent[0])*1000/elapsed;recent[0]=done;recent[1]=now;int p=total>0?(int)Math.min(99,done*100/total):0;job.states.set(index,p);job.bytes=done;job.total=total;if(now-lastPersist[0]>=3500L||done==total){
                    lastPersist[0]=now;BookImportManager.put(DownloadWorker.this,job);
                }
                DownloadChangeBus.progress(DownloadWorker.this,"audio:"+job.source,
                    "audio:"+job.source+":"+index,p,100,false);
                int completed=0;for(Integer x:job.states)if(x==100)completed++;String label=(completed+1)+"/"+count+" · "+(done/1024)+" KB · "+(speed/1024)+" KB/s";((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(4112,note(label,(completed*100+p)/Math.max(1,count),100));}});
            publishAudioTrack(job,i);
            job.states.set(i,100);BookImportManager.put(this,job);
            DownloadChangeBus.progress(this,"audio:"+job.source,
                "audio:"+job.source+":"+i,100,100,true);
            BookImportManager.updateLibrary(this,job);
        }if(!halt){job.status="finished";}else job.status=cancel?"cancelled":"paused";
        }catch(Exception e){job.status=halt?(cancel?"cancelled":"paused"):"error";job.error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}finally{
            BookImportManager.put(this,job);
            commandLane.execute(()->{thread=null;startNext();});
        }}
    private void exportAudioCover(BookImportManager.Job job){
        try{
            java.io.File source=AudiobookCoverStore.coverFile(this,job);
            if(!source.isFile()||source.length()<=0)return;
            java.io.File dir=new java.io.File(getCacheDir(),"audiobook_saf_cover_v165");
            if(!dir.isDirectory()&&!dir.mkdirs())return;
            java.io.File file=new java.io.File(dir,"cover.jpg");
            try(java.io.InputStream in=new java.io.FileInputStream(source);
                java.io.OutputStream out=new java.io.FileOutputStream(file)){
                byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            }
            DownloadFolderPicker.exportFile(this,DownloadFolderPicker.AUDIO,file,
                BookImportManager.safe(job.title)+"__"+Integer.toHexString(job.source.hashCode()));
            file.delete();
        }catch(Exception error){
            DownloadFolderPicker.recordIssue(this,DownloadFolderPicker.AUDIO,
                error instanceof java.io.IOException?(java.io.IOException)error:new java.io.IOException(error));
        }
    }
    /** Only a SHA-256 verified SAF export becomes the audio library's location. */
    private void publishAudioTrack(BookImportManager.Job job,int index)throws java.io.IOException{
        java.io.File track=BookImportManager.trackFile(this,job,index);
        android.net.Uri saved=DownloadFolderPicker.exportFile(this,DownloadFolderPicker.AUDIO,
            track,BookImportManager.safe(job.title)+"__"+Integer.toHexString(job.source.hashCode()));
        while(job.relocatedUris.size()<=index)job.relocatedUris.add("");
        job.relocatedUris.set(index,saved.toString());
        BookImportManager.put(this,job);BookImportManager.updateLibrary(this,job);
        // The SAF copy is now primary; only a SHA-256 matched, durably referenced copy
        // permits reclaiming the app-private complete MP3. Incomplete .part is retained.
        if(AudioSafDuplicates.matches(this,track,saved)
            &&AudioSafDuplicates.libraryReferences(this,job.source,saved.toString())
            &&(AudioPlaybackService.instance==null||!AudioPlaybackService.instance.isAudioPlaying())){
            if(!track.delete())android.util.Log.w("PROChitalka-SAF","Не удалось удалить временный MP3: "+track.getName());
        }
    }
    @Override public void onDestroy(){halt=true;commandLane.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent i){return null;}
}
