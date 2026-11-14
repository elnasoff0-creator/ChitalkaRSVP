package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.io.IOException;

/** Downloads only public chapters; interrupted and restricted requests are not written as HTML. */
public final class RanobesDownloadWorker extends Worker {
    static final String JOB_ID="ranobes_job_id";
    public RanobesDownloadWorker(@NonNull Context context,@NonNull WorkerParameters params){super(context,params);}
    private static boolean stopped(Context ctx,RanobesStorage.Job job){RanobesStorage.Job saved=RanobesStorage.find(ctx,job.id());
        return saved==null||"paused".equals(saved.status)||"cancelled".equals(saved.status);}
    private static void save(Context ctx,RanobesStorage.Job job,String state,String error){job.status=state;job.error=error==null?"":error;RanobesStorage.put(ctx,job);}
    @NonNull @Override public Result doWork(){Context c=getApplicationContext();String id=getInputData().getString(JOB_ID);
        RanobesStorage.Job job=id==null?null:RanobesStorage.find(c,id);if(job==null)return Result.failure();
        if(stopped(c,job))return Result.success();
        File target=RanobesStorage.chapter(c,job);
        if("complete".equals(job.status)&&target.isFile()&&target.length()>100)return Result.success();
        if(target.isFile()&&target.length()>100){save(c,job,"complete","");refresh(c,job.sourceKey());return Result.success();}
        save(c,job,"running","");
        try{setForegroundAsync(foreground(c,job));}catch(Exception ignored){}
        try{
            RanobesApi api=new RanobesApi();
            RanobesApi.Title book=new RanobesApi.Title(job.bookId,job.bookUrl,job.bookTitle);
            book.cover=job.cover;book.author=job.author;
            RanobesStorage.cover(c,book,api,job.language);
            RanobesApi.Chapter chapter=new RanobesApi.Chapter(job.chapterId,job.chapterUrl,job.title,job.volume,job.index);
            String html=api.chapterHtml(chapter);
            if(stopped(c,job)||isStopped())return Result.success();
            if("ru".equals(job.language))html=RanobesTranslate.toRussian(html);
            if(stopped(c,job)||isStopped())return Result.success();
            RanobesStorage.saveChapter(c,job,html);
            try{DownloadFolderPicker.export(c,DownloadFolderPicker.RANOBE,RanobesStorage.chapter(c,job),
                RanobesStorage.safeOutputDir(job.sourceKey()),"volumes","v"+job.volume);
            }catch(IOException writeError){android.util.Log.w("RanobesWorker","Chapter exported locally; SAF copy postponed",writeError);}
            save(c,job,"complete","");
            refresh(c,job.sourceKey());return Result.success();
        }catch(Exception e){if(stopped(c,job)||isStopped())return Result.success();
            String message=e.getMessage()==null?"Ошибка обработки главы":e.getMessage();
            // Access denial and HTTP 429 are not solved by automatically hammering the site.
            boolean repeat=(message.contains("timeout")||message.contains("Socket"))&&getRunAttemptCount()<2;
            save(c,job,repeat?"queued":"failed",message);
            if(!repeat)refresh(c,job.sourceKey());return repeat?Result.retry():Result.success();
        }
    }
    private void refresh(Context c,String key){int ready=0,waiting=0;
        for(RanobesStorage.Job j:RanobesStorage.jobs(c,key)){
            if("complete".equals(j.status))ready++;
            if("queued".equals(j.status)||"running".equals(j.status))waiting++;
        }
        if(ready>0&&(ready==1||ready%12==0||waiting==0))try{RanobesStorage.rebuild(c,key);}catch(IOException ex){android.util.Log.w("RanobesWorker","HTML assembly postponed",ex);}
    }
    private ForegroundInfo foreground(Context ctx,RanobesStorage.Job j){
        String channel="ranobes_books";
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26&&nm!=null)nm.createNotificationChannel(new NotificationChannel(channel,"Скачивание Ranobes",NotificationManager.IMPORTANCE_LOW));
        Intent intent=new Intent(ctx,UnifiedDownloadManagerActivity.class).putExtra(UnifiedDownloadManagerActivity.EXTRA_ID,"ranobes:"+j.sourceKey());
        android.app.PendingIntent click=android.app.PendingIntent.getActivity(ctx,Math.abs(j.sourceKey().hashCode()%12000)+20000,intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?android.app.PendingIntent.FLAG_IMMUTABLE:0));
        Notification message=new NotificationCompat.Builder(ctx,channel)
            .setSmallIcon(R.drawable.ic_downloads).setContentTitle(j.bookTitle)
            .setContentText("Том "+j.volume+" · "+j.title).setContentIntent(click).setOngoing(true).build();
        int num=26000+Math.abs(j.id().hashCode()%9000);
        if(Build.VERSION.SDK_INT>=29)return new ForegroundInfo(num,message,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        return new ForegroundInfo(num,message);
    }
}
