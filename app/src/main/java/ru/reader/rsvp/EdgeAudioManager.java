package ru.reader.rsvp;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Single Edge MP3 store view for playback, warmup, book preparation and Downloads.
 * Keeps the historic hash namespace: previously downloaded audio is never moved.
 * A change to any manifest, folder listing or explicit save/delete invalidates its
 * snapshot. All index I/O must be called on an executor, not the Android UI thread.
 */
final class EdgeAudioManager {
    private static final String BACKEND="edge-direct-v1";
    private static final long MAX_SNAPSHOT_AGE_MS=4000L;
    private static final ConcurrentHashMap<String, Snapshot> snapshots=new ConcurrentHashMap<>();
    private static final String[] MANIFESTS={"sentences.json","queue.json","full_queue.json","selected_clips.json"};
    private static final class Snapshot {
        final long stamp, created;
        final ArrayList<EdgeOfflineIndex.Segment> segments;
        Snapshot(long stamp,ArrayList<EdgeOfflineIndex.Segment> segments){
            this.stamp=stamp;this.segments=segments;this.created=android.os.SystemClock.elapsedRealtime();
        }
    }
    private EdgeAudioManager(){}
    static String key(String text,String voice,String rate){return EdgeBookStorage.key(text,voice,rate,BACKEND);}
    static File mp3(File folder,String key){return new File(folder,key+".mp3");}
    static boolean ready(File mp3){return mp3.isFile()&&mp3.length()>1500;}
    static void invalidate(File folder){if(folder!=null)snapshots.remove(folder.getAbsolutePath());}
    private static long stamp(File folder){
        long h=31L*folder.lastModified()+folder.length();
        for(String name:MANIFESTS){File f=new File(folder,name);h=h*31L+f.lastModified();h=h*31L+f.length();}
        return h;
    }
    static ArrayList<EdgeOfflineIndex.Segment> segments(File folder){
        if(folder==null||!folder.isDirectory())return new ArrayList<>();
        String id=folder.getAbsolutePath();long hash=stamp(folder);
        Snapshot old=snapshots.get(id);long now=android.os.SystemClock.elapsedRealtime();
        if(old!=null&&old.stamp==hash&&now-old.created<MAX_SNAPSHOT_AGE_MS)
            return new ArrayList<>(old.segments);
        ArrayList<EdgeOfflineIndex.Segment> loaded=EdgeOfflineIndex.readySegments(folder);
        // Do not retain every book's manifest forever on large libraries.
        if(snapshots.size()>96)snapshots.clear();
        snapshots.put(id,new Snapshot(hash,loaded));
        return new ArrayList<>(loaded);
    }
    static EdgeOfflineIndex.Stats coverage(File folder,int from,int to){
        return EdgeOfflineIndex.stats(segments(folder),from,to);
    }
}
