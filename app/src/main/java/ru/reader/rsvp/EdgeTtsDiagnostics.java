package ru.reader.rsvp;

import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;

/** Event-based latency diagnostics, one trace per active MP3. Logs no book text.
 * Times use elapsedRealtime so wall-clock adjustments never affect the result.
 */
final class EdgeTtsDiagnostics {
    private static final String TAG="EdgeTtsLatency";
    private static final ConcurrentHashMap<String,Trace> TRACES=new ConcurrentHashMap<>();
    private static volatile long lastTrackEndMs;
    static final class Trace {
        final long queued=SystemClock.elapsedRealtime();
        final int chars;
        final boolean warmup;
        volatile long opened=-1,firstMp3=-1,firstRead=-1;
        Trace(int chars,boolean warmup){this.chars=chars;this.warmup=warmup;}
    }
    private EdgeTtsDiagnostics(){}
    private static String shortKey(String key){
        return key==null?"unknown":key.substring(0,Math.min(12,key.length()));
    }
    static void requested(String key,int chars,boolean warmup){
        if(TRACES.size()>64)TRACES.clear(); // bound diagnostics memory
        TRACES.put(key,new Trace(chars,warmup));
        Log.i(TAG,"request="+shortKey(key)+" chars="+chars+" warmup="+warmup);
    }
    static void connected(String key){
        Trace t=TRACES.get(key);if(t==null||t.opened>=0)return;
        t.opened=SystemClock.elapsedRealtime();
        Log.i(TAG,"socket-open="+shortKey(key)+" queue+handshake="+(t.opened-t.queued)+"ms");
    }
    static void firstMp3(String key,int bytes){
        Trace t=TRACES.get(key);if(t==null||t.firstMp3>=0)return;
        t.firstMp3=SystemClock.elapsedRealtime();
        Log.i(TAG,"first-mp3="+shortKey(key)+" request-to-bytes="+(t.firstMp3-t.queued)+
            "ms connected-to-bytes="+(t.opened<0?-1:t.firstMp3-t.opened)+"ms packet="+bytes);
    }
    static void decoderRead(String key,int bytes){
        Trace t=TRACES.get(key);if(t==null||t.firstRead>=0)return;
        t.firstRead=SystemClock.elapsedRealtime();
        Log.i(TAG,"decoder-first-read="+shortKey(key)+" from-first-mp3="+
            (t.firstMp3<0?-1:t.firstRead-t.firstMp3)+"ms bytes="+bytes);
    }
    static void firstSound(String key){
        Trace t=TRACES.remove(key);if(t==null)return;
        long now=SystemClock.elapsedRealtime();
        long gap=lastTrackEndMs;
        lastTrackEndMs=0;
        Log.i(TAG,"player-start="+shortKey(key)+" request-to-player="+(now-t.queued)+
            "ms mp3-to-player="+(t.firstMp3<0?-1:now-t.firstMp3)+
            "ms read-to-player="+(t.firstRead<0?-1:now-t.firstRead)+
            "ms serial-gap="+(gap>0 && now-gap<30000?now-gap:-1)+"ms");
    }
    static void finished(String key,String error,int aligned){
        Trace t=TRACES.get(key);if(t==null)return;
        Log.i(TAG,"network-end="+shortKey(key)+" duration="+
            (SystemClock.elapsedRealtime()-t.queued)+"ms boundaries="+aligned+
            " status="+(error==null?"ok":"failed"));
        if(error!=null)TRACES.remove(key);
    }
    static void trackEnded(String key){
        lastTrackEndMs=SystemClock.elapsedRealtime();
        Log.i(TAG,"track-ended="+shortKey(key));
    }
    static void abandon(String key){TRACES.remove(key);}
}
