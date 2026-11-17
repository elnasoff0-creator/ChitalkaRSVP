package ru.reader.rsvp;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared bounded command lane: no queue/SAF IO on Activity's main thread. */
final class DownloadCommandController {
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"DownloadCommandLane");t.setDaemon(true);return t;
    });
    private static final Map<String,Long> generations=new HashMap<>();
    private DownloadCommandController(){}
    static long submit(Context context,String id,boolean start,Callable<Void> action){
        final long generation;
        synchronized(generations){generation=generations.getOrDefault(id,0L)+1;generations.put(id,generation);}
        DownloadChangeBus.command(id,generation,start,start?"STARTING":"PAUSING","");
        EXECUTOR.execute(()->{
            try{action.call();DownloadChangeBus.command(id,generation,start,start?"QUEUED":"PAUSED","");}
            catch(Exception error){DownloadChangeBus.command(id,generation,start,"ERROR",error.getMessage()==null?error.toString():error.getMessage());}
        });
        return generation;
    }
}
