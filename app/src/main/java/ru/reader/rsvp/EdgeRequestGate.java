package ru.reader.rsvp;

import android.os.SystemClock;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;

/** Cooperative, playback-first single-flight transport scheduler.
 * Does not manufacture a 2.2-second wait before every spoken sentence.
 * A genuine server 429, however, is never retried or routed around automatically.
 */
final class EdgeRequestGate {
    static final int PLAY=0, PREFETCH=1, BULK=2;
    private static final long MAX_COOLDOWN_MS=30L*60*1000;
    private static final long BASE_COOLDOWN_MS=60000;
    private static final ScheduledExecutorService EXEC=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeSpeechScheduler");t.setDaemon(true);return t;
    });
    static final class Ticket {
        final int priority;final long order;final Runnable start;boolean canceled;
        Ticket(int p,long order,Runnable run){priority=p;this.order=order;start=run;}
    }
    private static final ArrayList<Ticket> queue=new ArrayList<>();
    private static Ticket active;
    private static long order,notBefore,blockedUntil;
    private static int consecutive429;
    private static boolean wakeScheduled;
    private EdgeRequestGate(){}

    static synchronized Ticket enqueue(int priority,Runnable runnable){
        Ticket ticket=new Ticket(priority,++order,runnable);queue.add(ticket);schedule();return ticket;
    }
    static synchronized void release(Ticket ticket){
        if(ticket==null)return;
        ticket.canceled=true;queue.remove(ticket);
        if(active==ticket)active=null;
        schedule();
    }
    static synchronized long remainingMs(){return Math.max(0,blockedUntil-SystemClock.elapsedRealtime());}
    static synchronized long nextRetryAt(){return Math.max(blockedUntil,SystemClock.elapsedRealtime());}
    static synchronized void success(){consecutive429=0;}
    /** Rate limit is a server decision. Prefer Retry-After and do not switch endpoints. */
    static synchronized void limited(Response response){
        consecutive429=Math.min(6,consecutive429+1);
        long delay=Math.min(MAX_COOLDOWN_MS,BASE_COOLDOWN_MS*(1L<<Math.min(4,consecutive429-1)));
        String retry=response==null?null:response.header("Retry-After");
        if(retry!=null){
            try{delay=Math.max(delay,Long.parseLong(retry.trim())*1000L);}
            catch(Exception ignored){try{
                SimpleDateFormat f=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
                f.setTimeZone(TimeZone.getTimeZone("GMT"));
                Date d=f.parse(retry.trim());if(d!=null)delay=Math.max(delay,d.getTime()-System.currentTimeMillis());
            }catch(Exception ignoredToo){}}
        }
        delay=Math.min(MAX_COOLDOWN_MS,Math.max(BASE_COOLDOWN_MS,delay));
        blockedUntil=Math.max(blockedUntil,SystemClock.elapsedRealtime()+delay);
        Log.w("EdgeRequestGate","Edge server returned 429; stop network synthesis for "+delay+" ms");
        schedule();
    }
    private static void schedule(){
        if(wakeScheduled||active!=null||queue.isEmpty())return;
        wakeScheduled=true;
        long wait=Math.max(0,Math.max(notBefore,blockedUntil)-SystemClock.elapsedRealtime());
        EXEC.schedule(EdgeRequestGate::dispatch,wait,TimeUnit.MILLISECONDS);
    }
    private static void dispatch(){
        Ticket chosen;
        synchronized(EdgeRequestGate.class){
            wakeScheduled=false;
            if(active!=null)return;
            queue.removeIf(t->t.canceled);
            if(queue.isEmpty())return;
            if(SystemClock.elapsedRealtime()<Math.max(blockedUntil,notBefore)){schedule();return;}
            chosen=queue.stream().min(Comparator.comparingInt((Ticket t)->t.priority)
                .thenComparingLong(t->t.order)).orElse(null);
            if(chosen==null)return;
            queue.remove(chosen);active=chosen;
            // Only low-priority background work has a small pacing delay.
            // Never delay Play artificially after a successful previous sentence.
            notBefore=SystemClock.elapsedRealtime()+(chosen.priority==PLAY?0:350);
        }
        try{if(chosen.canceled)release(chosen);else chosen.start.run();}
        catch(Throwable e){Log.e("EdgeRequestGate","Cannot dispatch Edge request",e);release(chosen);}
    }
}
