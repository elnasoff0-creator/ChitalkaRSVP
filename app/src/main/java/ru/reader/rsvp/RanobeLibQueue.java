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
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** One serial WorkManager chain keeps request rate moderate and jobs durable. */
final class RanobeLibQueue {
    private static final String CHAIN="ranobelib_serial_chapters";
    private RanobeLibQueue(){}
    static int enqueue(Context c,List<RanobeLibStorage.Job> requested){
        ArrayList<RanobeLibStorage.Job> accepted=new ArrayList<>();HashSet<String> seen=new HashSet<>();
        java.util.HashMap<String,RanobeLibStorage.Job> saved=new java.util.HashMap<>();
        for(RanobeLibStorage.Job job:RanobeLibStorage.jobs(c))saved.put(job.id(),job);
        for(RanobeLibStorage.Job candidate:requested){
            if(!seen.add(candidate.id()))continue;
            RanobeLibStorage.Job previous=saved.get(candidate.id());
            if(previous!=null){if("complete".equals(previous.status)&&new java.io.File(previous.file).isFile())continue;
                if("running".equals(previous.status)||"queued".equals(previous.status))continue;
                previous.status="queued";previous.error="";candidate=previous;
            }else{candidate.status="queued";candidate.error="";}
            accepted.add(candidate);
        }
        if(accepted.isEmpty())return 0;
        RanobeLibStorage.putAll(c,accepted);
        WorkContinuation chain=null;WorkManager manager=WorkManager.getInstance(c);
        for(RanobeLibStorage.Job j:accepted){
            OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(RanobeLibDownloadWorker.class)
                .setInputData(new Data.Builder().putString(RanobeLibDownloadWorker.JOB_ID,j.id()).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,35,TimeUnit.SECONDS)
                .addTag("ranobelib").addTag("ranobelib:"+j.slug).build();
            chain=chain==null?manager.beginUniqueWork(CHAIN,ExistingWorkPolicy.APPEND_OR_REPLACE,request):chain.then(request);
        }
        chain.enqueue();return accepted.size();
    }
    static void pause(Context c,String slug){set(c,slug,"paused");}
    static void cancel(Context c,String slug){set(c,slug,"cancelled");}
    private static void set(Context c,String slug,String status){
        ArrayList<RanobeLibStorage.Job> changed=new ArrayList<>();for(RanobeLibStorage.Job j:RanobeLibStorage.series(c,slug)){
            if("queued".equals(j.status)||"running".equals(j.status)){j.status=status;changed.add(j);}}
        if(!changed.isEmpty())RanobeLibStorage.putAll(c,changed);
    }
    static int resume(Context c,String slug){ArrayList<RanobeLibStorage.Job> retry=new ArrayList<>();
        for(RanobeLibStorage.Job j:RanobeLibStorage.series(c,slug))if("paused".equals(j.status)||"failed".equals(j.status)||"cancelled".equals(j.status)||"blocked".equals(j.status))retry.add(j);
        return enqueue(c,retry);
    }
}
