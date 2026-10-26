package ru.reader.rsvp;

import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;

/** One cooperative rate gate for all Edge synthesis consumers in this process.
 * A WebSocket request holds the permit until turn.end, failure or cancellation.
 * It prevents competing TTS prefetch and full-book jobs from hammering the
 * unofficial Microsoft endpoint, and honours HTTP 429 without bypass attempts.
 */
final class EdgeRequestGate {
    static final int PLAY=0, PREFETCH=1, BULK=2;
    private static final long MIN_START_INTERVAL_MS=2200L;
    private static final long DEFAULT_429_MS=60000L;
    private static final long MAX_429_MS=30L*60L*1000L;
    private static final ScheduledExecutorService EXEC=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeRequestGate");t.setDaemon(true);return t;
    });
    private static final ArrayList<Ticket> WAITING=new ArrayList<>();
    private static Ticket active;
    private static long nextStartAt,sequence;
    private static long cooldownUntil;
    private static int consecutive429;
    private static boolean scheduled;
    static final class Ticket {
        final int priority;
        final long sequence;
        final Runnable start;
        boolean canceled;
        Ticket(int priority,long sequence,Runnable start){
            this.priority=priority;this.sequence=sequence;this.start=start;
        }
    }
    private EdgeRequestGate(){}
    static synchronized Ticket enqueue(int priority,Runnable start){
        Ticket t=new Ticket(priority,++sequence,start);
        WAITING.add(t);schedule();return t;
    }
    static synchronized void release(Ticket t){
        if(t==null)return;
        t.canceled=true;
        WAITING.remove(t);
        if(active==t)active=null;
        schedule();
    }
    static synchronized long remainingMs(){
        return Math.max(0,cooldownUntil-SystemClock.elapsedRealtime());
    }
    static synchronized long nextRetryAt(){
        return Math.max(cooldownUntil,SystemClock.elapsedRealtime());
    }
    static synchronized void success(){consecutive429=0;}
    /** Apply server Retry-After, or exponential cooldown. Never circumvent an
     * explicit rate limit by immediately switching to a different endpoint. */
    static synchronized void limited(Response response){
        consecutive429=Math.min(6,consecutive429+1);
        long wait=DEFAULT_429_MS*Math.min(16,1L<<(consecutive429-1));
        String header=response==null?null:response.header("Retry-After");
        if(header!=null){
            try{wait=Math.max(wait,Math.multiplyExact(Long.parseLong(header.trim()),1000L));}
            catch(Exception ignored){
                try{
                    java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
                    fmt.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
                    Date date=fmt.parse(header.trim());
                    if(date!=null)wait=Math.max(wait,date.getTime()-System.currentTimeMillis());
                }catch(Exception ignoredDate){}
            }
        }
        wait=Math.max(DEFAULT_429_MS,Math.min(MAX_429_MS,wait));
        cooldownUntil=Math.max(cooldownUntil,SystemClock.elapsedRealtime()+wait);
        Log.w("EdgeRequestGate","HTTP 429, synthesis paused for "+(wait/1000)+"s");
        schedule();
    }
    private static void schedule(){
        if(scheduled||active!=null||WAITING.isEmpty())return;
        scheduled=true;
        long now=SystemClock.elapsedRealtime();
        long wait=Math.max(0,Math.max(nextStartAt,cooldownUntil)-now);
        EXEC.schedule(EdgeRequestGate::dispatch,wait,TimeUnit.MILLISECONDS);
    }
    private static void dispatch(){
        Ticket selected;
        synchronized(EdgeRequestGate.class){
            scheduled=false;
            if(active!=null)return;
            WAITING.removeIf(t->t.canceled);
            if(WAITING.isEmpty())return;
            long now=SystemClock.elapsedRealtime();
            if(now<nextStartAt||now<cooldownUntil){schedule();return;}
            selected=WAITING.stream().min(Comparator.comparingInt((Ticket t)->t.priority)
                .thenComparingLong(t->t.sequence)).orElse(null);
            if(selected==null)return;
            WAITING.remove(selected);
            active=selected;
            nextStartAt=now+MIN_START_INTERVAL_MS;
        }
        try{if(!selected.canceled)selected.start.run();
            else release(selected);
        }catch(Throwable ex){
            Log.e("EdgeRequestGate","Cannot open Edge socket",ex);
            release(selected);
        }
    }
}
