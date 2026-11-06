package ru.reader.rsvp;

import android.content.Context;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Serial MangaLib queue; pausing is cooperative, so validated pages remain intact. */
final class MangaLibQueue {
    private MangaLibQueue(){}
    static int enqueue(Context ctx,List<MangaLibStorage.Job> selected){
        WorkManager manager=WorkManager.getInstance(ctx);
        WorkContinuation chain=null;int count=0;
        for(MangaLibStorage.Job job:selected){
            if("complete".equals(job.status)||"running".equals(job.status))continue;
            job.status="queued";job.error="";MangaLibStorage.put(ctx,job);
            Constraints connected=new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build();
            OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(MangaLibDownloadWorker.class)
                .setInputData(new Data.Builder().putString(MangaLibDownloadWorker.JOB_ID,job.id()).build())
                .setConstraints(connected)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
                .addTag("mangalib").addTag("mangalib:"+job.slug)
                .addTag("mangalib-job:"+job.id()).build();
            chain=chain==null?manager.beginUniqueWork("mangalib_serial_downloads",
                ExistingWorkPolicy.APPEND_OR_REPLACE,request):chain.then(request);
            count++;
        }
        if(chain!=null)chain.enqueue();
        return count;
    }
    static int pause(Context ctx,String slug){
        int count=0;
        for(MangaLibStorage.Job job:MangaLibStorage.jobs(ctx)){
            if(!slug.equals(job.slug))continue;
            if("running".equals(job.status)||"queued".equals(job.status)){
                job.status="paused";MangaLibStorage.put(ctx,job);count++;
            }
        }
        return count;
    }
    static int resume(Context ctx,String slug){
        ArrayList<MangaLibStorage.Job> resume=new ArrayList<>();
        for(MangaLibStorage.Job job:MangaLibStorage.jobs(ctx)){
            if(slug.equals(job.slug)&&("paused".equals(job.status)||"failed".equals(job.status)
                ||"cancelled".equals(job.status)))resume.add(job);
        }
        return enqueue(ctx,resume);
    }
}
