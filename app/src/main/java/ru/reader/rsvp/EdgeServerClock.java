package ru.reader.rsvp;

import android.os.SystemClock;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-blocking, passive clock calibration from Microsoft WebSocket HTTP Date headers.
 * No extra MS_TIME_SYNC request. Only GEC timestamps are adjusted; device clock is untouched.
 * Successful 101 and failed HTTP responses can supply samples. An HTTP 429 never stops
 * playback merely because time calibration failed, and is NOT retried here.
 */
final class EdgeServerClock {
    // A stale clock correction can be worse than an ordinary device clock.
    private static final long MAX_SAMPLE_AGE_MS=24L*60*60*1000;
    private static final long MAX_ABS_SKEW_MS=48L*60*60*1000;
    private static final long MAX_HANDSHAKE_MS=15_000L;
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EdgeServerClock");t.setDaemon(true);return t;
    });
    private static volatile long skewMillis;
    /** Tiny persistence contract; the Android adapter is a separate class so the
     * existing JVM network tests can exercise this clock without an Android SDK. */
    interface OffsetStore {
        long savedSkew();
        long savedAt();
        void save(long skew,long sampleWall);
    }
    private static volatile OffsetStore persistence;
    private static long lastSampleElapsed=-1;
    private EdgeServerClock() {}

    /** Called once on application startup; never creates a network connection. */
    static synchronized void initialize(OffsetStore store) {
        if(store==null||persistence!=null)return;
        long now=System.currentTimeMillis();
        long savedAt=store.savedAt(),savedSkew=store.savedSkew();
        if(savedAt>0 && now>=savedAt && now-savedAt<=MAX_SAMPLE_AGE_MS &&
                Math.abs(savedSkew)<=MAX_ABS_SKEW_MS) {
            skewMillis=savedSkew;
            ReaderDiagnosticLog.event("CLOCK","saved offset loaded skew_ms="+savedSkew);
        }else skewMillis=0;
        persistence=store;
    }

    static long adjustedEpochSeconds() {
        return (System.currentTimeMillis()+skewMillis)/1000L;
    }

    /** Record an already occurring WebSocket handshake's HTTP Date without waiting.
     * Its header contains no SSML, query token or user information. The RTT only
     * bounds the sample's uncertainty; long handshakes are ignored.
     */
    static void observeHttpDate(String httpDate,int httpStatus,
            long requestStartedWall,long requestStartedElapsed,long receivedWall,long receivedElapsed) {
        if(httpDate==null||httpDate.isEmpty())return;
        if(httpStatus!=101&&httpStatus!=403&&httpStatus!=429&&httpStatus!=400)return;
        long roundTrip=receivedElapsed-requestStartedElapsed;
        if(roundTrip<0||roundTrip>MAX_HANDSHAKE_MS)return;
        IO.execute(()->{
            Long serverDate=parseHttpDate(httpDate);
            if(serverDate==null)return;
            // Date has one-second precision; comparison near the response time
            // avoids unnecessary requests and is sufficiently precise for GEC's 300s bucket.
            long measuredSkew=serverDate-receivedWall;
            adopt(measuredSkew,receivedWall,receivedElapsed,"ws"+httpStatus);
        });
    }

    /** HTTP 403 may require a *single* immediate fresh token handshake.
     * Keep this path synchronous on OkHttp's callback thread (not UI or Play).
     * Return true only when the corrected GEC differs from the failed one.
     */
    static boolean correctFor403(String httpDate,String failedGec,long receivedAtMillis) {
        Long server=parseHttpDate(httpDate);
        if(server==null||failedGec==null)return false;
        if(EdgeToken.generate(server/1000L).equals(failedGec))return false;
        if(!adopt(server-receivedAtMillis,receivedAtMillis,SystemClock.elapsedRealtime(),"ws403"))return false;
        return true;
    }

    private static Long parseHttpDate(String header) {
        if(header==null)return null;
        String value=header.trim();
        SimpleDateFormat f=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("GMT"));f.setLenient(false);
        ParsePosition pos=new ParsePosition(0);
        Date date=f.parse(value,pos);
        if(date==null||pos.getIndex()!=value.length())return null;
        return date.getTime();
    }

    private static synchronized boolean adopt(long candidate,long sampleWall,
            long sampleElapsed,String source) {
        if(Math.abs(candidate)>MAX_ABS_SKEW_MS)return false;
        if(lastSampleElapsed>sampleElapsed)return false; // discard older asynchronous events
        lastSampleElapsed=sampleElapsed;
        skewMillis=candidate;
        OffsetStore store=persistence;
        if(store!=null){
            // The adapter uses SharedPreferences.apply(), never synchronous commit().
            store.save(candidate,sampleWall);
        }
        ReaderDiagnosticLog.event("CLOCK","server date synchronized via="+source+
            " offset_ms="+candidate);
        return true;
    }
}
