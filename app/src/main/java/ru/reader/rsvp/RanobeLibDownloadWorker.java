package ru.reader.rsvp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
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

/** Downloads one publicly accessible text chapter and atomically refreshes HTML. */
public final class RanobeLibDownloadWorker extends Worker {
    static final String JOB_ID="ranobelib_job_id";
    public RanobeLibDownloadWorker(@NonNull Context c,@NonNull WorkerParameters args){super(c,args);}
    private void save(RanobeLibStorage.Job j,String status,String error){
        RanobeLibStorage.Job now=RanobeLibStorage.find(getApplicationContext(),j.id());
        if(now!=null&&( "cancelled".equals(now.status)||"paused".equals(now.status))){
            // A user-initiated pause must never be overwritten by a worker callback.
            if(!"paused".equals(status)&&!"cancelled".equals(status))return;
        }
        j.status=status;j.error=error;RanobeLibStorage.put(getApplicationContext(),j);
    }
    private boolean halted(String id){RanobeLibStorage.Job j=RanobeLibStorage.find(getApplicationContext(),id);
        return isStopped()||(j!=null&&( "paused".equals(j.status)||"cancelled".equals(j.status)));
    }
    private static boolean verified(File f){return f.isFile()&&f.length()>100&&f.length()<40_000_000;}
    // Permanent failure must not cancel the dependent WorkManager chain:
    // other public chapters should still download after an unavailable one.
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();String id=getInputData().getString(JOB_ID);
        RanobeLibStorage.Job job=id==null?null:RanobeLibStorage.find(ctx,id);
        if(job==null)return Result.failure();
        if("paused".equals(job.status)||"cancelled".equals(job.status))return Result.success();
        if("complete".equals(job.status)&&RanobeLibStorage.stored(ctx,job.file))return Result.success();
        if(verified(RanobeLibStorage.chapterFile(ctx,job))){
            job.file=RanobeLibStorage.chapterFile(ctx,job).getAbsolutePath();job.bytes=new File(job.file).length();save(job,"complete","");
            try{RanobeLibStorage.rebuildBook(ctx,job.slug);}catch(IOException error){android.util.Log.w("RanobeLibWorker","Recover HTML",error);}
            return Result.success();
        }
        save(job,"running","");
        try{setForegroundAsync(notification(job));}catch(Exception ignored){}
        try{
            if(halted(id))return stopResult(job);
            RanobeLibApi api=new RanobeLibApi();
            RanobeLibApi.Title book=RanobeLibStorage.savedTitle(ctx,job.slug);
            if(book.title.isEmpty())book.title=job.title;
            if(book.author.isEmpty())book.author=job.author;
            RanobeLibStorage.rememberTitle(ctx,book);
            RanobeLibStorage.ensureCover(ctx,book,api);
            String markup=api.chapterHtml(job.slug,job.apiChapter());
            if(halted(id))return stopResult(job);
            markup=RanobeLibStorage.localizeImages(ctx,job,markup,api);
            if(halted(id))return stopResult(job);
            RanobeLibStorage.saveChapter(ctx,job,markup);
            if(halted(id))return stopResult(job);
            save(job,"complete","");
            // Preserve a readable partial HTML after the first chapter and refresh it
            // periodically on large downloads to avoid quadratic rewriting.
            int done=0,pending=0;for(RanobeLibStorage.Job j:RanobeLibStorage.series(ctx,job.slug)){
                if("complete".equals(j.status))done++;
                if("running".equals(j.status)||"queued".equals(j.status))pending++;
            }
            if(done==1||done%20==0||pending==0){
                RanobeLibStorage.rebuildBook(ctx,job.slug);
                if(pending==0&&RanobeLibStorage.externalDir(ctx,job.slug)==null){try{
                    String display=book.title.length()>70?book.title.substring(0,70):book.title;
                    String target=display+"__"+job.slug.substring(0,job.slug.indexOf("--"));
                    String tree=DownloadFolderPicker.sourceKey("ranobelib");
                    DownloadFolderPicker.export(ctx,tree,RanobeLibStorage.book(ctx,job.slug),target);
                    DownloadFolderPicker.export(ctx,tree,RanobeLibStorage.cover(ctx,job.slug),target);
                    File images=new File(RanobeLibStorage.folder(ctx,job.slug),"images");
                    File[] pictures=images.listFiles();if(pictures!=null)for(File picture:pictures)
                        if(picture.isFile())DownloadFolderPicker.export(ctx,tree,picture,target,"images");
                }catch(IOException err){android.util.Log.w("RanobeLibWorker","SAF export unavailable",err);}}
            }
            setProgressAsync(new Data.Builder().putInt("complete",done).build());
            try{Thread.sleep(400);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            return Result.success();
        }catch(RanobeLibApi.AccessException denied){save(job,"blocked",denied.getMessage());refreshAfterTerminal(ctx,job.slug);return Result.success();}
        catch(RanobeLibApi.RateException rate){if(halted(id))return stopResult(job);
            boolean retry=getRunAttemptCount()<4;save(job,retry?"queued":"failed",rate.getMessage());if(!retry)refreshAfterTerminal(ctx,job.slug);return retry?Result.retry():Result.success();}
        catch(Exception error){if(halted(id))return stopResult(job);
            String message=error.getMessage()==null?"Ошибка скачивания":error.getMessage();
            boolean retry=(message.contains("HTTP 5")||message.contains("timeout")||message.contains("Socket"))&&getRunAttemptCount()<3;
            save(job,retry?"queued":"failed",message);if(!retry)refreshAfterTerminal(ctx,job.slug);return retry?Result.retry():Result.success();}
    }
    private void refreshAfterTerminal(Context context,String slug){
        int remaining=0,complete=0;
        for(RanobeLibStorage.Job job:RanobeLibStorage.series(context,slug)){
            if("running".equals(job.status)||"queued".equals(job.status))remaining++;
            if("complete".equals(job.status))complete++;
        }
        if(remaining==0&&complete>0)try{
            RanobeLibStorage.rebuildBook(context,slug);
            if(RanobeLibStorage.externalDir(context,slug)!=null)return;
            String tree=DownloadFolderPicker.sourceKey("ranobelib");
            RanobeLibApi.Title title=RanobeLibStorage.savedTitle(context,slug);
            String display=title.title.length()>70?title.title.substring(0,70):title.title;
            String directory=display+"__"+slug.substring(0,slug.indexOf("--"));
            DownloadFolderPicker.export(context,tree,RanobeLibStorage.book(context,slug),directory);
            DownloadFolderPicker.export(context,tree,RanobeLibStorage.cover(context,slug),directory);
        }catch(Exception error){android.util.Log.w("RanobeLibWorker","Rebuild partial HTML",error);}
    }
    private Result stopResult(RanobeLibStorage.Job j){
        RanobeLibStorage.Job now=RanobeLibStorage.find(getApplicationContext(),j.id());
        if(now!=null&&( "paused".equals(now.status)||"cancelled".equals(now.status)))return Result.success();
        save(j,"queued","");return Result.retry();
    }
    private ForegroundInfo notification(RanobeLibStorage.Job job){
        Context context=getApplicationContext();String channel="ranobelib_books";
        NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26&&manager!=null)manager.createNotificationChannel(new NotificationChannel(channel,"Скачивание RanobeLIB",NotificationManager.IMPORTANCE_LOW));
        android.app.Notification message=new NotificationCompat.Builder(context,channel)
            .setSmallIcon(R.drawable.ic_downloads).setContentTitle(job.title)
            .setContentText(job.apiChapter().fullTitle()).setOngoing(true).build();
        int notificationId=16000+Math.abs(job.id().hashCode()%10000);
        if(Build.VERSION.SDK_INT>=29)return new ForegroundInfo(notificationId,message,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        return new ForegroundInfo(notificationId,message);
    }
}
