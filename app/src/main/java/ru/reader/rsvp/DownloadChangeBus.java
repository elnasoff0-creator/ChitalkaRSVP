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
}
