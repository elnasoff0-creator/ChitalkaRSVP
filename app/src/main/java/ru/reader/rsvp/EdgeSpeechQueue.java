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

/** Edge network single-flight arbiter. User navigation has no artificial wait.
 * Speculative/background work stays lower priority. A real server Retry-After is
 * still honored; neither 15 minute prefetch block nor 4.5s PLAY pacing exists.
 */
final class EdgeSpeechQueue {
    static final int PLAY=0,PREFETCH=1,BULK=2;
    static final class Ticket {
        int priority;final long order;final Runnable run,cancel;
        boolean canceled;
        Ticket(int p,long o,Runnable r,Runnable c){priority=p;order=o;run=r;cancel=c;}
    }
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeSpeechQueue");t.setDaemon(true);return t;
    });
    private static final ArrayList<Ticket> pending=new ArrayList<>();
    private static Ticket current;
    private static ScheduledFuture<?> wake;
    private static long order,blockedUntil,lastCompletedAt,lastLimitedAt;
    // While interactive Edge audio is requested, defer explicit full-book jobs.
    // They resume on pause or after the last spoken group completes.
    private static boolean interactivePlayback;
    static synchronized void setInteractivePlayback(boolean playing){
        if(interactivePlayback==playing)return;
        interactivePlayback=playing;
        schedule();
    }
    // Local 429 fallback is one second. Explicit server Retry-After is authoritative.
    // Only background jobs are gently paced. A new tap/Play is never paced.
    private static final long BACKGROUND_GAP_MS=850L;
    private static final long DEFAULT_429_DEBOUNCE_MS=1000L;
    private EdgeSpeechQueue(){}
    static Ticket submit(int priority,Runnable run){return submit(priority,run,null);}
    static Ticket submit(int priority,Runnable run,Runnable onPreempt){
        Runnable preempt=null;Ticket t;
        synchronized(EdgeSpeechQueue.class){
            t=new Ticket(priority,++order,run,onPreempt);
            pending.add(t);
            if(priority==PLAY&&current!=null&&current.priority>PLAY&&current.cancel!=null)
                preempt=current.cancel;
            schedule();
        }
        if(preempt!=null)preempt.run();
        return t;
    }
    /** Promote an already queued warmup to immediate Play without a second
     * WebSocket handshake or discarding the buffered audio. */
    static void promote(Ticket ticket){
        Runnable preempt=null;
        synchronized(EdgeSpeechQueue.class){
            if(ticket==null||ticket.canceled||ticket.priority==PLAY)return;
            ticket.priority=PLAY;
            if(current!=null&&current!=ticket&&current.priority>PLAY&&current.cancel!=null)
                preempt=current.cancel;
            schedule();
        }
        if(preempt!=null)preempt.run();
    }
    static synchronized void release(Ticket ticket){
        if(ticket==null)return;
        ticket.canceled=true;pending.remove(ticket);
        if(current==ticket){current=null;lastCompletedAt=SystemClock.elapsedRealtime();}
        schedule();
    }
    static synchronized void restoreCooldown(long wallClockDeadlineMs){
        long remaining=wallClockDeadlineMs-System.currentTimeMillis();
        if(remaining>0){blockedUntil=Math.max(blockedUntil,SystemClock.elapsedRealtime()+remaining);schedule();}
    }
    static synchronized long remainingMs(){return Math.max(0,blockedUntil-SystemClock.elapsedRealtime());}
    static synchronized boolean allowPrefetch(){
        // Keep local speculative-traffic suppression at most one second,
        // but the shared queue must still honor a longer server Retry-After.
        return lastLimitedAt==0 || SystemClock.elapsedRealtime()-lastLimitedAt>=1000L;
    }
    static synchronized void success(){lastLimitedAt=0;}
    static synchronized void limited(Response response){
        long delay=DEFAULT_429_DEBOUNCE_MS;
        String after=response==null?null:response.header("Retry-After");
        if(after!=null)try{
            delay=Math.max(delay,Long.parseLong(after.trim())*1000L);
        }catch(Exception ignored){try{
            SimpleDateFormat fmt=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
            Date date=fmt.parse(after.trim());
            if(date!=null)delay=Math.max(delay,date.getTime()-System.currentTimeMillis());
        }catch(Exception ignoredAgain){}}
        lastLimitedAt=SystemClock.elapsedRealtime();
        blockedUntil=Math.max(blockedUntil,lastLimitedAt+Math.max(0,delay));
        Log.w("EdgeSpeechQueue","429 Retry-After/debounce="+delay+"ms");
        schedule();
    }
    private static void schedule(){
        if(wake!=null){wake.cancel(false);wake=null;}
        pending.removeIf(t->t.canceled);
        if(current!=null||pending.isEmpty())return;
        Ticket next=pending.stream().filter(t->!interactivePlayback||t.priority==PLAY)
            .min(Comparator.comparingInt((Ticket t)->t.priority)
            .thenComparingLong(t->t.order)).orElse(null);
        if(next==null)return;
        long now=SystemClock.elapsedRealtime();
        long due=blockedUntil;
        if(next.priority!=PLAY)due=Math.max(due,lastCompletedAt+BACKGROUND_GAP_MS);
        wake=TIMER.schedule(EdgeSpeechQueue::dispatch,Math.max(0,due-now),TimeUnit.MILLISECONDS);
    }
    private static void dispatch(){
        Ticket next;
        synchronized(EdgeSpeechQueue.class){
            wake=null;
            pending.removeIf(t->t.canceled);
            if(current!=null||pending.isEmpty())return;
            next=pending.stream().filter(t->!interactivePlayback||t.priority==PLAY)
                .min(Comparator.comparingInt((Ticket t)->t.priority)
                .thenComparingLong(t->t.order)).orElse(null);
            if(next==null)return;
            long now=SystemClock.elapsedRealtime();
            if(now<blockedUntil || (next.priority!=PLAY&&now<lastCompletedAt+BACKGROUND_GAP_MS)){
                schedule();return;
            }
            pending.remove(next);current=next;
        }
        try{if(!next.canceled)next.run.run();else release(next);}
        catch(Throwable ex){Log.e("EdgeSpeechQueue","Cannot start speech",ex);release(next);}
    }
}
