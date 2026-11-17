package ru.reader.rsvp;

import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-wide, revisioned invalidation of *one* download. No disk scans or UI work here.
 * Revisions survive an Activity being paused; persistent job stores remain authoritative.
 */
final class DownloadChangeBus {
    interface Listener { void onDownloadChanged(String id); }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Listener> listeners=new CopyOnWriteArraySet<>();
    private static final LinkedHashMap<String,Long> changes=new LinkedHashMap<>();
    private static long revision=0, oldest=0;
    private DownloadChangeBus(){}
    static void add(Listener l){listeners.add(l);}
    static void remove(Listener l){listeners.remove(l);}
    static synchronized long revision(){return revision;}
    static void publish(String id){
        if(id==null||id.isEmpty())return;
        synchronized(DownloadChangeBus.class){
            changes.remove(id);changes.put(id,++revision);
            if(changes.size()>512){
                Map.Entry<String,Long> first=changes.entrySet().iterator().next();
                oldest=first.getValue();changes.remove(first.getKey());
            }
        }
        MAIN.post(()->{for(Listener l:listeners)l.onDownloadChanged(id);});
    }
    /** Null means a long-inactive client missed more entries than the bounded journal. */
    static synchronized ArrayList<String> since(long from){
        if(from<oldest)return null;
        ArrayList<String> result=new ArrayList<>();
        for(Map.Entry<String,Long> e:changes.entrySet())if(e.getValue()>from)result.add(e.getKey());
        return result;
    }

    enum Kind {CHAPTER_PROGRESS,CHAPTER_COMMITTED,COMMAND_STATE,ERROR}
    interface ProgressListener {void onProgress(ProgressEvent event);}
    static final class ProgressEvent {
        final Kind kind; final String id,chapterKey,state,message;
        final int permille;final boolean committed,desiredRunning;
        final long sequence,generation;
        ProgressEvent(Kind type,String id,String key,int fraction,boolean done,boolean desired,
            String state,String message,long sequence,long generation){
            this.kind=type;this.id=id;chapterKey=key;permille=fraction;committed=done;
            desiredRunning=desired;this.state=state;this.message=message;this.sequence=sequence;this.generation=generation;
        }
    }
    private static final CopyOnWriteArraySet<ProgressListener> progressListeners=new CopyOnWriteArraySet<>();
    private static final LinkedHashMap<String,ProgressEvent> pendingProgress=new LinkedHashMap<>();
    private static boolean progressScheduled;
    private static long progressSequence;
    private static final LinkedHashMap<String,Long> recentProgress=new LinkedHashMap<>();
    static synchronized boolean hasLiveProgress(String id){
        Long at=recentProgress.get(id);
        return at!=null&&android.os.SystemClock.elapsedRealtime()-at<1400;
    }
    private static final Runnable deliverProgress=()->{
        ArrayList<ProgressEvent> events;
        synchronized(DownloadChangeBus.class){events=new ArrayList<>(pendingProgress.values());
            pendingProgress.clear();progressScheduled=false;}
        for(ProgressEvent event:events)for(ProgressListener l:progressListeners)l.onProgress(event);
    };
    static void addProgress(ProgressListener l){progressListeners.add(l);}
    static void removeProgress(ProgressListener l){progressListeners.remove(l);}
    static void progress(android.content.Context context,String id,String chapterKey,int done,int total,boolean committed){
        if(id==null||id.isEmpty()||chapterKey==null||chapterKey.isEmpty())return;
        int fraction=total>0?(int)Math.min(1000,Math.max(0,(1000L*Math.max(0,done))/total)):-1;
        long sequence; synchronized(DownloadChangeBus.class){
            sequence=++progressSequence;
            recentProgress.put(id,android.os.SystemClock.elapsedRealtime());
            while(recentProgress.size()>64)recentProgress.remove(recentProgress.keySet().iterator().next());
        }
        DownloadProgressTracker.Summary summary=DownloadProgressTracker.update(id,chapterKey,fraction,committed,sequence);
        DownloadProgressStore.changed(context,summary,committed);
        ProgressEvent event=new ProgressEvent(committed?Kind.CHAPTER_COMMITTED:Kind.CHAPTER_PROGRESS,
            id,chapterKey,committed?1000:fraction,committed,false,"","",sequence,0);
        if(committed){
            synchronized(DownloadChangeBus.class){pendingProgress.remove(id+"\n"+chapterKey);}
            MAIN.post(()->{for(ProgressListener l:progressListeners)l.onProgress(event);});
        }else synchronized(DownloadChangeBus.class){
            pendingProgress.put(id+"\n"+chapterKey,event);
            if(!progressScheduled){progressScheduled=true;MAIN.postDelayed(deliverProgress,225);}
        }
    }
    static void command(String id,long generation,boolean desired,String state,String message){
        ProgressEvent event=new ProgressEvent(Kind.COMMAND_STATE,id,"",-1,false,desired,
            state,message,0,generation);
        MAIN.post(()->{for(ProgressListener l:progressListeners)l.onProgress(event);});
    }
}
