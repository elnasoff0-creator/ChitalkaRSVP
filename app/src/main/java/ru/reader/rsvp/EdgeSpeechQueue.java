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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;

/** A single permit for all Edge traffic, including whole-book generation.
 * Requests are paced, never concurrently retried and never routed around 429.
 * A cached file requires no permit and plays immediately.
 */
final class EdgeSpeechQueue {
    static final int PLAY=0, PREFETCH=1, BULK=2;
    static final class Ticket {
        final int priority;final long order;final Runnable run;
        boolean canceled;
        Ticket(int p,long o,Runnable r){priority=p;order=o;run=r;}
    }
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeSpeechQueue");t.setDaemon(true);return t;
    });
    private static final ArrayList<Ticket> pending=new ArrayList<>();
    private static Ticket current;
    private static ScheduledFuture<?> wake;
    private static long order,nextRequestAt,blockedUntil;
    private static int consecutive429;
    private static final long PACING_MS=1400L;
    private static final long MAX_COOLDOWN_MS=30L*60*1000L;
    private EdgeSpeechQueue(){}
    static synchronized Ticket submit(int priority,Runnable run){
        Ticket t=new Ticket(priority,++order,run);pending.add(t);schedule();return t;
    }
    static synchronized void release(Ticket ticket){
        if(ticket==null)return;
        ticket.canceled=true;pending.remove(ticket);
        if(current==ticket){current=null;nextRequestAt=Math.max(nextRequestAt,SystemClock.elapsedRealtime()+PACING_MS);}
        schedule();
    }
    static synchronized long remainingMs(){return Math.max(0L,blockedUntil-SystemClock.elapsedRealtime());}
    static synchronized long nextRetryAt(){return Math.max(blockedUntil,SystemClock.elapsedRealtime());}
    static synchronized void success(){consecutive429=0;}
    static synchronized void limited(Response response){
        consecutive429=Math.min(6,consecutive429+1);
        long delay=Math.min(MAX_COOLDOWN_MS,30000L*(1L<<Math.min(4,consecutive429-1)));
        String after=response==null?null:response.header("Retry-After");
        if(after!=null)try{
            delay=Math.max(delay,Math.multiplyExact(Long.parseLong(after.trim()),1000L));
        }catch(Exception ignored){try{
            SimpleDateFormat fmt=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date=fmt.parse(after.trim());
            if(date!=null)delay=Math.max(delay,date.getTime()-System.currentTimeMillis());
        }catch(Exception ignoredAgain){}}
        delay=Math.min(MAX_COOLDOWN_MS,Math.max(30000L,delay));
        blockedUntil=Math.max(blockedUntil,SystemClock.elapsedRealtime()+delay);
        Log.w("EdgeSpeechQueue","Server 429: respecting cooldown "+delay+"ms");
        schedule();
    }
    private static void schedule(){
        if(wake!=null){wake.cancel(false);wake=null;}
        if(current!=null||pending.isEmpty())return;
        long delay=Math.max(0L,Math.max(nextRequestAt,blockedUntil)-SystemClock.elapsedRealtime());
        wake=TIMER.schedule(EdgeSpeechQueue::dispatch,delay,TimeUnit.MILLISECONDS);
    }
    private static void dispatch(){
        Ticket next;
        synchronized(EdgeSpeechQueue.class){
            wake=null;
            pending.removeIf(t->t.canceled);
            if(current!=null||pending.isEmpty())return;
            if(SystemClock.elapsedRealtime()<Math.max(nextRequestAt,blockedUntil)){schedule();return;}
            next=pending.stream().min(Comparator.comparingInt((Ticket t)->t.priority)
                .thenComparingLong(t->t.order)).orElse(null);
            if(next==null)return;
            pending.remove(next);current=next;
        }
        try{if(!next.canceled)next.run.run();else release(next);}
        catch(Throwable ex){Log.e("EdgeSpeechQueue","Cannot begin synthesis",ex);release(next);}
    }
}
