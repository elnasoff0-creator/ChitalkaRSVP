package ru.reader.rsvp;

import android.content.Context;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.content.pm.ServiceInfo;
import android.graphics.BitmapFactory;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Each verified page survives pause, process death and transient network failures. */
public final class MangaLibDownloadWorker extends Worker {
    static final String JOB_ID="mangalib_job_id";
    public MangaLibDownloadWorker(@NonNull Context c,@NonNull WorkerParameters params){super(c,params);}
    private void update(MangaLibStorage.Job j,String status,String error){
        j.status=status;j.error=error;MangaLibStorage.put(getApplicationContext(),j);
    }
    private boolean pauseRequested(String id){
        MangaLibStorage.Job persisted=MangaLibStorage.find(getApplicationContext(),id);
        return persisted!=null&&("paused".equals(persisted.status)||"cancelled".equals(persisted.status));
    }
    private boolean stoppedOrPaused(String id){return isStopped()||pauseRequested(id);}
    private Result halted(MangaLibStorage.Job job){
        MangaLibStorage.Job saved=MangaLibStorage.find(getApplicationContext(),job.id());
        boolean cancelled=saved!=null&&"cancelled".equals(saved.status);
        boolean paused=saved!=null&&"paused".equals(saved.status);
        if(cancelled||paused){
            update(job,cancelled?"cancelled":"paused",cancelled?"Отменено":"");
            return Result.success();
        }
        // WorkManager may stop work because of connectivity or process constraints:
        // this is not a user pause and must not strand the chapter permanently.
        update(job,"queued","");
        return Result.retry();
    }
    private static boolean validPage(File file){
        if(!file.isFile()||file.length()<16||file.length()>25_000_000L)return false;
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(file.getAbsolutePath(),options);
        return options.outWidth>0&&options.outHeight>0;
    }
    private static File cachedPage(File folder,int index){
        File[] matches=folder.listFiles((dir,name)->name.startsWith(String.format(Locale.US,"%04d.",index))
            &&!name.endsWith(".tmp"));
        if(matches!=null)for(File file:matches){if(validPage(file))return file;file.delete();}
        return null;
    }
    private File downloadPage(MangaLibApi api,MangaLibApi.Page page,File folder,int index) throws IOException{
        byte[] data=api.image(page);
        String extension=MangaLibApi.extension(data);
        File target=new File(folder,String.format(Locale.US,"%04d.%s",index,extension));
        File temporary=new File(folder,String.format(Locale.US,"%04d.tmp",index));
        try(FileOutputStream output=new FileOutputStream(temporary)){
            output.write(data);output.getFD().sync();
        }
        if(!validPage(temporary)){temporary.delete();throw new IOException("Изображение не декодируется: страница "+index);}
        if(!temporary.renameTo(target)){
            temporary.delete();throw new IOException("Не удалось сохранить страницу "+index);
        }
        return target;
    }
    private static void copyFile(ZipOutputStream zip,File source) throws IOException{
        byte[] buffer=new byte[8192];
        try(FileInputStream stream=new FileInputStream(source)){
            int n;while((n=stream.read(buffer))!=-1)zip.write(buffer,0,n);
        }
    }
    private static boolean completeArchive(File file){
        if(!file.isFile()||file.length()<2048)return false;
        try(ZipFile zip=new ZipFile(file)){
            if(zip.size()<2||zip.getEntry("ComicInfo.xml")==null)return false;
            java.util.Enumeration<? extends ZipEntry> list=zip.entries();
            int imageCount=0;byte[] buffer=new byte[8192];
            while(list.hasMoreElements()){
                ZipEntry entry=list.nextElement();
                if(entry.isDirectory()||entry.getSize()==0)return false;
                if(!"ComicInfo.xml".equals(entry.getName()))imageCount++;
                try(java.io.InputStream in=zip.getInputStream(entry)){
                    while(in.read(buffer)!=-1){}
                }
            }
            return imageCount>0;
        }catch(IOException error){return false;}
    }
    @NonNull @Override public Result doWork(){
        Context ctx=getApplicationContext();String id=getInputData().getString(JOB_ID);
        MangaLibStorage.Job job=id==null?null:MangaLibStorage.find(ctx,id);
        if(job==null)return Result.failure();
        if("paused".equals(job.status)||"cancelled".equals(job.status))return Result.success();
        File output=MangaLibStorage.chapterFile(ctx,job);
        File part=new File(output.getAbsolutePath()+".part");
        if(completeArchive(output)){
            job.file=output.getAbsolutePath();update(job,"complete","");
            MangaLibStorage.registerComic(ctx,job);return Result.success();
        }
        update(job,"running","");
        try{setForegroundAsync(foreground(job));}catch(Exception ignored){}
        File stage=MangaLibStorage.stageDir(ctx,job);
        try{
            MangaLibApi api=new MangaLibApi();
            MangaLibApi.Chapter chapter=new MangaLibApi.Chapter(job.volume,job.number,job.chapter,job.branch);
            List<MangaLibApi.Page> pages=api.pages(job.slug,chapter);
            job.total=pages.size();job.done=0;job.bytes=0;
            MangaLibStorage.put(ctx,job);
            for(int i=1;i<=pages.size();i++){
                if(stoppedOrPaused(id))return halted(job);
                File image=cachedPage(stage,i);
                if(image==null){
                    try{image=downloadPage(api,pages.get(i-1),stage,i);}
                    catch(MangaLibApi.AccessException denied){throw denied;}
                    catch(IOException problem){throw new IOException("Глава "+job.number+", страница "
                        +i+"/"+pages.size()+": "+problem.getMessage(),problem);}
                }
                job.done=i;job.bytes+=image.length();
                // A concurrent Pause must not be overwritten by a normal progress update.
                if(stoppedOrPaused(id))return halted(job);
                MangaLibStorage.put(ctx,job);
                if(i%5==1)try{setForegroundAsync(foreground(job));}catch(Exception ignored){}
                setProgressAsync(new Data.Builder().putInt("done",job.done)
                    .putInt("total",job.total).putLong("bytes",job.bytes).build());
                try{Thread.sleep(250);}catch(InterruptedException e){Thread.currentThread().interrupt();return halted(job);}
            }
            if(stoppedOrPaused(id))return halted(job);
            if(part.exists()&&!part.delete())throw new IOException("Не удалось заменить временный CBZ");
            try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(part))){
                for(int i=1;i<=pages.size();i++){
                    if(stoppedOrPaused(id))return halted(job);
                    File image=cachedPage(stage,i);
                    if(image==null)throw new IOException("Отсутствует проверенная страница "+i);
                    zip.putNextEntry(new ZipEntry(image.getName()));copyFile(zip,image);zip.closeEntry();
                }
                zip.putNextEntry(new ZipEntry("ComicInfo.xml"));
                String xml="<?xml version=\"1.0\" encoding=\"UTF-8\"?><ComicInfo><Series>"+esc(job.series)
                    +"</Series><Number>"+esc(job.number)+"</Number><Volume>"+esc(job.volume)
                    +"</Volume><Title>"+esc(job.chapter)+"</Title></ComicInfo>";
                zip.write(xml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            if(stoppedOrPaused(id))return halted(job);
            try(ZipFile zip=new ZipFile(part)){
                if(zip.size()!=pages.size()+1||zip.getEntry("ComicInfo.xml")==null)
                    throw new IOException("Неполный CBZ: неправильное число файлов");
                byte[] buffer=new byte[8192];
                for(java.util.Enumeration<? extends ZipEntry> iterator=zip.entries();iterator.hasMoreElements();){
                    ZipEntry entry=iterator.nextElement();if(entry.isDirectory()||entry.getSize()==0)
                        throw new IOException("Пустая страница CBZ");
                    try(java.io.InputStream stream=zip.getInputStream(entry)){
                        while(stream.read(buffer)!=-1){}
                    }
                }
            }
            if(output.exists()&&!output.delete())throw new IOException("Невозможно заменить CBZ");
            if(!part.renameTo(output))throw new IOException("Невозможно сохранить CBZ");
            job.file=output.getAbsolutePath();update(job,"complete","");
            MangaLibStorage.clearStage(stage);
            MangaLibStorage.registerComic(ctx,job);return Result.success();
        }catch(MangaLibApi.AccessException denied){
            part.delete();update(job,"blocked",denied.getMessage());return Result.failure();
        }catch(Exception error){
            part.delete();
            if(stoppedOrPaused(id))return halted(job);
            String message=error.getMessage()==null?"Ошибка загрузки":error.getMessage();
            boolean retry=(message.contains("429")||message.contains("HTTP 5"))&&getRunAttemptCount()<3;
            update(job,retry?"queued":"failed",message);
            return retry?Result.retry():Result.failure();
        }
    }
    private ForegroundInfo foreground(MangaLibStorage.Job job){
        Context ctx=getApplicationContext();String channel="mangalib_downloads";
        NotificationManager manager=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26&&manager!=null)manager.createNotificationChannel(
            new NotificationChannel(channel,"Загрузка манги MangaLib",NotificationManager.IMPORTANCE_LOW));
        android.app.Notification note=new NotificationCompat.Builder(ctx,channel)
            .setSmallIcon(R.drawable.ic_downloads).setContentTitle(job.series)
            .setContentText("CBZ: "+job.chapter+" · "+job.done+"/"+job.total)
            .setProgress(Math.max(1,job.total),job.done,job.total==0).setOngoing(true).build();
        int id=12000+Math.abs(job.id().hashCode()%10000);
        if(Build.VERSION.SDK_INT>=29)return new ForegroundInfo(id,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        return new ForegroundInfo(id,note);
    }
    private static String esc(String str){return str.replace("&","&amp;").replace("<","&lt;")
        .replace(">","&gt;").replace("\"","&quot;");}
}
