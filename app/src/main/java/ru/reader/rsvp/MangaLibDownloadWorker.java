package ru.reader.rsvp;

import android.content.Context;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.pm.ServiceInfo;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Durable, sequential chapter-to-CBZ task; one source (MangaLib), public pages only. */
public final class MangaLibDownloadWorker extends Worker {
    static final String JOB_ID="mangalib_job_id";
    public MangaLibDownloadWorker(@NonNull Context c,@NonNull WorkerParameters params){super(c,params);}
    private void update(MangaLibStorage.Job j,String status,String error){j.status=status;j.error=error;MangaLibStorage.put(getApplicationContext(),j);}
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();String id=getInputData().getString(JOB_ID);
        MangaLibStorage.Job job=id==null?null:MangaLibStorage.find(ctx,id);if(job==null)return Result.failure();
        File output=MangaLibStorage.chapterFile(ctx,job),part=new File(output.getAbsolutePath()+".part");
        if(output.isFile()&&output.length()>2048){job.file=output.getAbsolutePath();update(job,"complete","");MangaLibStorage.registerComic(ctx,job);return Result.success();}
        if("cancelled".equals(job.status))return Result.failure();
        update(job,"running","");
        try{setForegroundAsync(foreground(job));}catch(Exception ignored){}
        try{
            MangaLibApi api=new MangaLibApi();
            MangaLibApi.Chapter chapter=new MangaLibApi.Chapter(job.volume,job.number,job.chapter,job.branch);
            List<MangaLibApi.Page> pages=api.pages(job.slug,chapter);
            job.total=pages.size();job.done=0;MangaLibStorage.put(ctx,job);
            if(part.exists())part.delete();
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(part))){
                for(int i=0;i<pages.size();i++){
                    if(isStopped())throw new IOException("Загрузка остановлена");
                    byte[] data=api.image(pages.get(i).url);
                    String name=String.format(Locale.US,"%04d.%s",i+1,MangaLibApi.extension(data));
                    zip.putNextEntry(new ZipEntry(name));zip.write(data);zip.closeEntry();
                    job.done=i+1;MangaLibStorage.put(ctx,job);
                    if((i%5)==0)try{setForegroundAsync(foreground(job));}catch(Exception ignored){}
                    setProgressAsync(new Data.Builder().putInt("done",job.done).putInt("total",job.total).build());
                    try{Thread.sleep(300);}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IOException("Загрузка прервана");}
                }
                zip.putNextEntry(new ZipEntry("ComicInfo.xml"));
                String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><ComicInfo><Series>"+esc(job.series)+"</Series><Number>"+esc(job.number)+"</Number><Volume>"+esc(job.volume)+"</Volume><Title>"+esc(job.chapter)+"</Title></ComicInfo>";
                zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            try(ZipFile z=new ZipFile(part)){if(z.size()<pages.size())throw new IOException("Неполный CBZ");}
            if(output.exists()&&!output.delete())throw new IOException("Невозможно заменить CBZ");
            if(!part.renameTo(output))throw new IOException("Невозможно сохранить CBZ");
            job.file=output.getAbsolutePath();update(job,"complete","");MangaLibStorage.registerComic(ctx,job);
            return Result.success();
        }catch(MangaLibApi.AccessException denied){part.delete();update(job,"blocked",denied.getMessage());return Result.failure();
        }catch(Exception e){part.delete();
            if(isStopped()){update(job,"cancelled","Остановлено");return Result.failure();}
            boolean retry=(e.getMessage()!=null&&(e.getMessage().contains("429")||e.getMessage().contains("HTTP 5")))&&getRunAttemptCount()<3;
            update(job,retry?"queued":"failed",e.getMessage()==null?"Ошибка загрузки":e.getMessage());
            return retry?Result.retry():Result.failure();
        }
    }
    private ForegroundInfo foreground(MangaLibStorage.Job j){
        Context ctx=getApplicationContext();String channel="mangalib_downloads";
        NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26&&nm!=null)nm.createNotificationChannel(new NotificationChannel(channel,"Загрузка манги MangaLib",NotificationManager.IMPORTANCE_LOW));
        android.app.Notification note=new NotificationCompat.Builder(ctx,channel)
            .setSmallIcon(R.drawable.ic_downloads).setContentTitle(j.series)
            .setContentText("CBZ: "+j.chapter+" · "+j.done+"/"+j.total)
            .setProgress(Math.max(1,j.total),j.done,j.total==0).setOngoing(true).build();
        int id=12000+Math.abs(j.id().hashCode()%10000);
        if(Build.VERSION.SDK_INT>=29)return new ForegroundInfo(id,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        return new ForegroundInfo(id,note);
    }
    private static String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
}
