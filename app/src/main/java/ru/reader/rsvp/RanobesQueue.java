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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Serial, resumable queue shared across Ranobes books; independent of RanobeLIB. */
final class RanobesQueue {
    private RanobesQueue(){}
    static int enqueue(Context ctx,List<RanobesStorage.Job> requested){
        HashMap<String,RanobesStorage.Job> stored=new HashMap<>();for(RanobesStorage.Job job:RanobesStorage.jobs(ctx))stored.put(job.id(),job);
        ArrayList<RanobesStorage.Job> accepted=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        for(RanobesStorage.Job candidate:requested){
            if(!seen.add(candidate.id()))continue;
            RanobesStorage.Job old=stored.get(candidate.id());
            if(old!=null){if("complete".equals(old.status)&&RanobesStorage.chapter(ctx,old).isFile())continue;
                if("running".equals(old.status)||"queued".equals(old.status))continue;
                candidate=old;}
            candidate.status="queued";candidate.error="";accepted.add(candidate);
        }
        if(accepted.isEmpty())return 0;
        RanobesStorage.putAll(ctx,accepted);
        WorkContinuation chain=null;
        WorkManager manager=WorkManager.getInstance(ctx);
        for(RanobesStorage.Job j:accepted){
            OneTimeWorkRequest item=new OneTimeWorkRequest.Builder(RanobesDownloadWorker.class)
                .setInputData(new Data.Builder().putString(RanobesDownloadWorker.JOB_ID,j.id()).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,45,TimeUnit.SECONDS)
                .addTag("ranobes").addTag("ranobes:"+j.sourceKey()).build();
            chain=chain==null?manager.beginUniqueWork("ranobes_serial_chapters",ExistingWorkPolicy.APPEND_OR_REPLACE,item):chain.then(item);
        }
        chain.enqueue();return accepted.size();
    }
    private static void state(Context ctx,String source,String status){ArrayList<RanobesStorage.Job> modified=new ArrayList<>();
        for(RanobesStorage.Job j:RanobesStorage.jobs(ctx,source))if("queued".equals(j.status)||"running".equals(j.status)){
            j.status=status;modified.add(j);
        }
        RanobesStorage.putAll(ctx,modified);
    }
    static void pause(Context ctx,String source){state(ctx,source,"paused");}
    static void cancel(Context ctx,String source){state(ctx,source,"cancelled");}
    static int resume(Context ctx,String source){ArrayList<RanobesStorage.Job> jobs=new ArrayList<>();
        for(RanobesStorage.Job j:RanobesStorage.jobs(ctx,source))if("paused".equals(j.status)||"failed".equals(j.status)||"cancelled".equals(j.status))jobs.add(j);
        return enqueue(ctx,jobs);
    }
}
