package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Independent full-book producer. Does not bind to or control EdgePlaybackService. */
public final class EdgeFullBookService extends Service {
    static final String ADD="ru.reader.rsvp.edge.full.ADD";
    static final String RESUME="ru.reader.rsvp.edge.full.RESUME";
    static final String PAUSE="ru.reader.rsvp.edge.full.PAUSE";
    static final String STOP_CHAPTER="ru.reader.rsvp.edge.full.STOP_CHAPTER";
    static final String EXTRA_URI="book_uri",EXTRA_TITLE="book_title",EXTRA_VOICE="edge_voice";
    static final String EXTRA_RATE="edge_rate",EXTRA_CHARS="edge_chars",EXTRA_CHAPTER="edge_chapter";
    static final String EXTRA_FORCE="edge_force_reload";
    private static final String CHANNEL="edge_full_book";
    private static final int NOTIFICATION_ID=1472;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean destroyed,processing;
    private static volatile boolean serviceAlive;
    static boolean isServiceAlive(){return serviceAlive;}
    private volatile EdgeTtsClient currentClient;
    private volatile String synthesizingUri="";
    private volatile int synthesizingChapter=EdgeFullBookStore.PLAN;
    private int notificationCounter;

    @Override public void onCreate(){super.onCreate();
        EdgeFullBookStore.recoverInterrupted(this);serviceAlive=true;
        EdgeSpeechQueue.restoreCooldown(getSharedPreferences("reader",0)
                .getLong("edge_v2_retry_after_epoch_ms",0));
        if(Build.VERSION.SDK_INT>=26){NotificationManager nm=getSystemService(NotificationManager.class);
            if(nm!=null)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Edge TTS: вся книга",NotificationManager.IMPORTANCE_LOW));}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            String action=intent.getAction(),uri=intent.getStringExtra(EXTRA_URI);
            if(ADD.equals(action)&&uri!=null&&!uri.isEmpty()){
                String title=intent.getStringExtra(EXTRA_TITLE),voice=intent.getStringExtra(EXTRA_VOICE),rate=intent.getStringExtra(EXTRA_RATE);
                int chars=EdgeSegmentSettings.clamp(intent.getIntExtra(EXTRA_CHARS,EdgeSegmentSettings.DEFAULT));
                EdgeFullBookStore.Job created=EdgeFullBookStore.create(this,uri,title==null?"Книга":title,
                    voice==null?"ru-RU-SvetlanaNeural":voice,rate==null?"+0%":rate,chars);
                EdgeFullBookStore.chooseChapter(created,intent.getIntExtra(EXTRA_CHAPTER,-1),
                    intent.getBooleanExtra(EXTRA_FORCE,false));
                // Multiple chapter taps append independent durable tasks.
                // Never interrupt another chapter simply because it shares a book URI.
            }else if(STOP_CHAPTER.equals(action)&&uri!=null){
                int selected=intent.getIntExtra(EXTRA_CHAPTER,EdgeFullBookStore.PLAN);
                EdgeFullBookStore.stopChapter(this,uri,"",selected);
                if(uri.equals(synthesizingUri)&&selected==synthesizingChapter){
                    EdgeTtsClient active=currentClient;if(active!=null)active.cancel();
                }
            }else if((RESUME.equals(action)||PAUSE.equals(action))&&uri!=null){
                for(EdgeFullBookStore.Job job:EdgeFullBookStore.list(this))if(uri.equals(job.uri)){
                    if(PAUSE.equals(action))EdgeFullBookStore.pauseAll(job);
                    else EdgeFullBookStore.resumeAll(job);
                    if(PAUSE.equals(action)&&uri.equals(synthesizingUri)){
                        EdgeTtsClient client=currentClient;if(client!=null)client.cancel();
                    }
                    break;
                }
            }
        }
        if(!processing){processing=true;
            // startForegroundService callers require promotion within a few seconds.
            try{startForeground(NOTIFICATION_ID,notification("Подготовка очереди",0,0));}
            catch(Exception e){Log.w("EdgeFullBook","Foreground rejected",e);}
            worker.execute(this::drain);
        }
        return START_REDELIVER_INTENT;
    }
    private Notification notification(String name,int done,int total){
        Intent click=new Intent(this,DownloadActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,52,click,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_reader).setContentTitle("Edge TTS · подготовка всей книги")
            .setContentText(name+" · "+done+(total>0?" из "+total:""))
            .setContentIntent(pi).setOnlyAlertOnce(true).setOngoing(true);
        if(total>0)b.setProgress(total,Math.min(done,total),false);else b.setProgress(0,0,true);
        return b.build();
    }
    private void notifyProgress(String title,int done,int total){
        if(++notificationCounter%2!=0&&done!=total)return;
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(nm!=null)nm.notify(NOTIFICATION_ID,notification(title,done,total));
    }
    private void drain(){
        try{
            // Only one full-book synth job runs at once; queued books follow it automatically.
            while(!destroyed){
                EdgeFullBookStore.Job next=EdgeFullBookStore.next(this);
                if(next==null)break;
                runJob(next);
            }
        }finally{
            processing=false;
            // A command can arrive while drain is winding down; onStartCommand rechecks below.
            boolean awaiting=false;
            for(EdgeFullBookStore.Job job:EdgeFullBookStore.list(this)){
                if(EdgeFullBookStore.QUEUED.equals(job.status)){awaiting=true;break;}
            }
            if(awaiting&&!destroyed){processing=true;worker.execute(this::drain);}
            else {stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
        }
    }
    private void runJob(EdgeFullBookStore.Job job){
        if(job.chapter==EdgeFullBookStore.PLAN){
            try{
                BookLoader.Book book=BookLoader.load(this,Uri.parse(job.uri));
                EdgeChapterArchive.invalidate(); // avoid using a stale 9-second SAF snapshot
                EdgeChapterArchive.ChapterSnapshot snapshot=EdgeChapterArchive.snapshot(this,job.uri,job.title);
                EdgeFullBookStore.planMissing(job,book.chapters,snapshot);
            }catch(Exception error){
                EdgeFullBookStore.progress(job,0,0,"Не удалось открыть оглавление: "+error.getMessage());
                EdgeFullBookStore.status(job,EdgeFullBookStore.FAILED);
            }
            return;
        }
        if(!EdgeFolderPicker.available(this)){
            EdgeFullBookStore.status(job,EdgeFullBookStore.FAILED);
            EdgeFullBookStore.progress(job,0,0,"Сначала выберите папку Edge TTS");
            return;
        }
        EdgeFullBookStore.status(job,EdgeFullBookStore.RUNNING);
        try{
            BookLoader.Book book=BookLoader.load(this,Uri.parse(job.uri));
            String raw=book.text==null?"":book.text;
            if(raw.trim().isEmpty())throw new Exception("В книге нет текста для озвучки");
            // Use exactly the same segmentation and hash keys as the live reader.
            // Jobs never resynthesize cached MP3 solely because a previous version
            // offered a different configurable chunk size.
            ArrayList<EdgeTextSegments.Part> parts=EdgeTextSegments.all(raw,book.chapters,job.chars);
            if(parts.isEmpty())throw new Exception("Не удалось разбить текст на фрагменты");
            final EdgeSpeechTextTransform.Options speechOptions=
                EdgeDictionaryStore.get(this).options();
            JSONArray index=new JSONArray();
            for(EdgeTextSegments.Part part:parts){
                JSONObject o=new JSONObject();
                EdgeSpeechTextTransform.Result speech=speechOptions.apply(part.text);
                if(speech.skipSynthesis())continue; // no index/MP3/WebSocket for ignored parts
                String key=speech.cacheKey(job.voice,job.rate);
                o.put("start",part.start).put("end",part.end).put("text",part.text).put("key",key);
                index.put(o);
            }
            EdgeFullBookStore.writeQueue(job,index);
            DownloadsRepository.storeEdgeMeta(EdgeBookStorage.bookFolder(this,job.uri,job.title),
                job.uri,job.title,EdgeTextSegments.positions(raw).size());
            int count=index.length();
            BookChapterIndex.Chapter only=job.chapter>=0 && job.chapter<book.chapters.size()
                ?book.chapters.get(job.chapter):null;
            ArrayList<Integer> wordOffsets=EdgeTextSegments.positions(raw);
            int chapterStart=only==null?0:SentenceChunks.lowerBound(wordOffsets,only.startChar);
            int chapterEnd=only==null?wordOffsets.size():SentenceChunks.lowerBound(wordOffsets,only.endChar);
            if(job.chapter>=0&&only==null)throw new Exception("Выбранная глава недоступна");
            int eligible=0;
            for(int k=0;k<count;k++){
                JSONObject row=index.getJSONObject(k);
                if(only!=null&&(row.optInt("end",0)<=chapterStart||row.optInt("start",0)>=chapterEnd))continue;
                eligible++;
            }
            // Restore known persistent fragments after cache eviction, before synthesis.
            if(!job.forceReload)EdgeChapterArchive.restore(this,job.uri,job.title,job.mp3Folder());
            if(job.forceReload){
                for(int k=0;k<index.length();k++){
                    JSONObject row=index.getJSONObject(k);
                    if(only!=null&&(row.optInt("end",0)<=chapterStart||row.optInt("start",0)>=chapterEnd))continue;
                    File old=new File(job.mp3Folder(),row.getString("key")+".mp3");
                    if(old.isFile())old.delete();
                    File wordFile=new File(old.getAbsolutePath()+".words.json");
                    if(wordFile.isFile())wordFile.delete();
                }
                EdgeFullBookStore.clearForce(job);
            }
            int completed=readyInRange(job,index,chapterStart,chapterEnd,only!=null);
            EdgeFullBookStore.progress(job,completed,eligible,null);
            for(int i=0;i<count&&!destroyed;){
                if(!EdgeFullBookStore.running(job))return;
                JSONObject o=index.getJSONObject(i);String key=o.getString("key");
                if(only!=null && (o.optInt("end",0)<=chapterStart || o.optInt("start",0)>=chapterEnd)){
                    i++;continue; // never synthesize unselected chapters
                }
                File target=new File(job.mp3Folder(),key+".mp3");
                if(EdgeFullBookStore.ready(target)){i++;continue;}
                if(!EdgeGenerationGuard.claim(target.getAbsolutePath())){
                    Thread.sleep(500);continue;
                }
                try{
                    if(EdgeFullBookStore.ready(target)){i++;continue;}
                    boolean saved=false;
                    String problem="Неизвестная ошибка синтеза";
                    for(int attempt=0;attempt<4&&!destroyed;attempt++){
                        if(!EdgeFullBookStore.running(job))return;
                        if(EdgeSpeechQueue.remainingMs()>0){
                            // Short scheduler cooldown is not a failed chapter.
                            EdgeFullBookStore.status(job,EdgeFullBookStore.WAITING);
                            Thread.sleep(Math.min(500L,EdgeSpeechQueue.remainingMs()));
                            if(!EdgeFullBookStore.running(job))return;
                            EdgeFullBookStore.status(job,EdgeFullBookStore.RUNNING);
                            attempt--;continue;
                        }
                        Result result=synthesize(o.getString("text"),job,speechOptions);
                        if(!EdgeFullBookStore.running(job))return;
                        if(result.data!=null&&(EdgeAudioFormat.identify(result.data,result.data.length)!=null && ".mp3".equals(EdgeAudioFormat.identify(result.data,result.data.length).extension))){
                            try{
                                File tmp=new File(job.mp3Folder(),key+".full.part");
                                try(FileOutputStream out=new FileOutputStream(tmp,false)){
                                    out.write(result.data);out.getFD().sync();
                                }
                                if(target.exists()&&!EdgeFullBookStore.ready(target)&&!target.delete())
                                    throw new Exception("Не удалось заменить повреждённый MP3");
                                if(!target.exists()&&!tmp.renameTo(target))throw new Exception("Не удалось сохранить MP3");
                                saved=EdgeFullBookStore.ready(target);
                                if(saved){EdgeWordTimings.storeAligned(target,o.getString("text"),
                                    result.transformation.aligned(result.boundaries));
                                    EdgeAudioManager.invalidate(job.mp3Folder());}
                                tmp.delete();
                            }catch(Exception e){problem=e.getMessage();}
                        }else problem=result.error==null?"Сервис вернул не MP3":result.error;
                        if(saved)break;
                        if(problem!=null&&problem.contains("прерван ради активного чтения")){
                            // ReaderActivity preempted us; do not drop this chapter.
                            EdgeFullBookStore.status(job,EdgeFullBookStore.WAITING);
                            while(EdgeSpeechQueue.interactivePlaybackActive()&&!destroyed){
                                if(!EdgeFullBookStore.running(job))return;
                                Thread.sleep(500);
                            }
                            if(!EdgeFullBookStore.running(job))return;
                            EdgeFullBookStore.status(job,EdgeFullBookStore.RUNNING);
                            attempt--;continue;
                        }
                        if(problem!=null&&problem.contains("HTTP 429")){
                            getSharedPreferences("reader",0).edit().putLong(
                                "edge_v2_retry_after_epoch_ms",
                                System.currentTimeMillis()+EdgeSpeechQueue.remainingMs()).apply();
                            // Do not keep retrying a service that explicitly
                            // asked us to reduce the request rate.
                            Log.w("EdgeFullBook","HTTP 429; Retry-After="+
                                EdgeSpeechQueue.remainingMs()+"ms");
                            EdgeFullBookStore.progress(job,readyInRange(job,index,chapterStart,chapterEnd,only!=null),eligible,
                                "HTTP 429 · повторите после паузы");
                            EdgeFullBookStore.pauseAll(job); // avoid hammering 429 across queued chapters
                            return;
                        }
                        Thread.sleep(500L); // fixed local retry gap; 429 never auto-retries
                    }
                    if(!saved){
                        EdgeFullBookStore.progress(job,readyInRange(job,index,chapterStart,chapterEnd,only!=null),eligible,
                            "Фрагмент "+(i+1)+": "+problem);
                        EdgeFullBookStore.status(job,EdgeFullBookStore.FAILED);
                        return;
                    }
                }finally{EdgeGenerationGuard.release(target.getAbsolutePath());}
                i++;
                completed=readyInRange(job,index,chapterStart,chapterEnd,only!=null);
                EdgeFullBookStore.progress(job,completed,eligible,null);
                notifyProgress(job.title,completed,eligible);
            }
            // Complete chapters are saved as one MP3 + WordBoundary JSON under
            // the user's SAF tree. Per-clip audio is ONLY disposable playback cache.
            if(!EdgeFullBookStore.running(job))return;
            EdgeFullBookStore.status(job,EdgeFullBookStore.SAVING);
            int exported=EdgeChapterArchive.autoMerge(this)?
                EdgeChapterArchive.publish(this,job.uri,job.title,job.mp3Folder(),index,book.chapters,wordOffsets):
                EdgeChapterArchive.publishFragments(this,job.uri,job.title,job.mp3Folder(),index,book.chapters,wordOffsets);
            Log.i("EdgeFullBook","Verified chapters in SAF: "+exported);
            EdgeChapterArchive.invalidate();
            if(exported==0||job.chapter>=0&&
                EdgeChapterArchive.snapshot(this,job.uri,job.title).state(job.chapter)==0)
                throw new Exception("Не удалось записать завершённую главу в выбранную папку Edge TTS");
            // Keep the pre-existing in-app chapter dashboard functional while
            // all of its source files live in disposable internal cache.
            EdgeChapterLayout.publish(job,index,book.chapters,wordOffsets);
            if(EdgeFullBookStore.running(job)){
                completed=readyInRange(job,index,chapterStart,chapterEnd,only!=null);
                EdgeFullBookStore.progress(job,completed,eligible,null);
                // Ignored segments have no MP3 by design; count only actual
                // eligible queue entries when determining completion.
                int ready=0;
                for(int k=0;k<index.length();k++){
                    JSONObject row=index.getJSONObject(k);
                    if(only!=null&&(row.optInt("end",0)<=chapterStart ||
                        row.optInt("start",0)>=chapterEnd))continue;
                    if(EdgeFullBookStore.ready(new File(job.mp3Folder(),row.getString("key")+".mp3")))ready++;
                }
                EdgeFullBookStore.status(job,ready==eligible?EdgeFullBookStore.DONE:EdgeFullBookStore.FAILED);
            }
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){
            if(EdgeFullBookStore.running(job)){
                EdgeFullBookStore.Job refreshed=EdgeFullBookStore.find(this,job.uri,job.title);
                EdgeFullBookStore.Task current=EdgeFullBookStore.task(refreshed,job.chapter);
                EdgeFullBookStore.progress(job,current==null?0:current.done,
                    current==null?0:current.total,"Ошибка: "+e.getMessage());
                EdgeFullBookStore.status(job,EdgeFullBookStore.FAILED);
            }
            Log.w("EdgeFullBook","Cannot prepare book",e);
        }finally{synthesizingUri="";synthesizingChapter=EdgeFullBookStore.PLAN;currentClient=null;}
    }
    private static int readyInRange(EdgeFullBookStore.Job job,JSONArray index,
                                   int start,int end,boolean filtered){
        int count=0;
        for(int k=0;k<index.length();k++){
            JSONObject row=index.optJSONObject(k);if(row==null)continue;
            if(filtered&&(row.optInt("end",0)<=start||row.optInt("start",0)>=end))continue;
            String key=row.optString("key","");
            if(key.matches("[a-f0-9]{64}")&&EdgeFullBookStore.ready(
                new File(job.mp3Folder(),key+".mp3")))count++;
        }
        return count;
    }
    private static final class Result{volatile byte[] data;volatile String error;
        EdgeSpeechTextTransform.Result transformation;
        volatile java.util.List<EdgeWordTimings.Boundary> boundaries=java.util.Collections.emptyList();}
    private Result synthesize(String text,EdgeFullBookStore.Job job,
                              EdgeSpeechTextTransform.Options speechOptions) throws InterruptedException {
        Result result=new Result();CountDownLatch done=new CountDownLatch(1);
        result.transformation=speechOptions.apply(text);
        if(result.transformation.skipSynthesis()){
            result.data=new byte[0];return result;
        }
        synthesizingUri=job.uri;synthesizingChapter=job.chapter;
        currentClient=EdgeTtsClient.synthesize(result.transformation.spoken,job.voice,job.rate,
            EdgeSpeechQueue.BULK,new EdgeTtsClient.Callback(){
            @Override public void progress(int chunks,long bytes){}
            @Override public void finished(byte[] mp3,int chunks,String error){result.data=mp3;result.error=error;done.countDown();}
            @Override public void finished(byte[] mp3,int chunks,String error,
                    java.util.List<EdgeWordTimings.Boundary> boundaries){
                result.boundaries=boundaries;finished(mp3,chunks,error);
            }
        });
        // BULK waits behind live ReaderActivity playback. Publish that fact
        // to the chapter card and resume the status without timing out early.
        boolean wasWaiting=false,finished=false;
        int activeSeconds=0;
        while(activeSeconds<600&&!destroyed){
            if(done.await(1,TimeUnit.SECONDS)){finished=true;break;}
            if(!EdgeFullBookStore.running(job)){
                EdgeTtsClient client=currentClient;if(client!=null)client.cancel();
                result.error="Подготовка остановлена";break;
            }
            boolean waiting=EdgeSpeechQueue.interactivePlaybackActive();
            if(!waiting)activeSeconds++; // live playback wait does not consume timeout
            if(waiting!=wasWaiting){
                EdgeFullBookStore.status(job,waiting?EdgeFullBookStore.WAITING:EdgeFullBookStore.RUNNING);
                wasWaiting=waiting;
            }
        }
        if(!finished&&result.error==null){
            EdgeTtsClient client=currentClient;if(client!=null)client.cancel();
            result.error="Тайм-аут синтеза Edge TTS";
        }
        if(EdgeFullBookStore.running(job)&&wasWaiting)EdgeFullBookStore.status(job,EdgeFullBookStore.RUNNING);
        currentClient=null;synthesizingUri="";synthesizingChapter=EdgeFullBookStore.PLAN;
        return result;
    }
    @Override public void onTimeout(int startId,int fgsType){
        for(EdgeFullBookStore.Job job:EdgeFullBookStore.list(this))
            if(EdgeFullBookStore.RUNNING.equals(job.status)||EdgeFullBookStore.QUEUED.equals(job.status))
                EdgeFullBookStore.pauseAll(job);
        EdgeTtsClient client=currentClient;if(client!=null)client.cancel();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    @Override public void onDestroy(){destroyed=true;
        for(EdgeFullBookStore.Job job:EdgeFullBookStore.list(this))
            if(EdgeFullBookStore.RUNNING.equals(job.status)||EdgeFullBookStore.WAITING.equals(job.status)||
                EdgeFullBookStore.SAVING.equals(job.status))EdgeFullBookStore.pauseRunning(job);
        serviceAlive=false;
        EdgeTtsClient client=currentClient;if(client!=null)client.cancel();worker.shutdownNow();super.onDestroy();}
}
