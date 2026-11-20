package ru.reader.rsvp;

import android.os.SystemClock;
import java.util.HashSet;

/** One monotonic, privacy-safe first-audio timeline across Activity and both Edge services.
 * Contains only stage labels, durations and numeric counters, never book content/URLs.
 */
final class EdgeStartupTrace {
    private static long serial, startMs;
    private static boolean firstAudioReported;
    private static final HashSet<String> seen=new HashSet<>();
    private EdgeStartupTrace(){}
    static synchronized void begin(String reason){
        serial++;
        startMs=SystemClock.elapsedRealtime();
        firstAudioReported=false;
        seen.clear();
        ReaderDiagnosticLog.event("EDGE_STARTUP","id="+serial+" t=0 stage=PLAY reason="+reason);
    }
    static synchronized void stage(String name,String metrics){
        if(startMs<=0||firstAudioReported||!seen.add(name))return;
        ReaderDiagnosticLog.event("EDGE_STARTUP","id="+serial+" t="+
            Math.max(0,SystemClock.elapsedRealtime()-startMs)+" stage="+name+
            (metrics==null||metrics.isEmpty()?"":" "+metrics));
    }
    static synchronized void firstAudio(String source){
        if(startMs<=0||firstAudioReported)return;
        firstAudioReported=true;
        ReaderDiagnosticLog.event("EDGE_STARTUP","id="+serial+" t="+
            Math.max(0,SystemClock.elapsedRealtime()-startMs)+
            " stage=FIRST_AUDIO source="+source);
    }
    static synchronized void cancelled(String reason){
        if(startMs<=0||firstAudioReported)return;
        ReaderDiagnosticLog.event("EDGE_STARTUP","id="+serial+" t="+
            Math.max(0,SystemClock.elapsedRealtime()-startMs)+" stage=CANCEL reason="+reason);
        startMs=0;
    }
}
