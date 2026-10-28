package ru.reader.rsvp;

import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Response;

/** Bounded, interactive-first scheduler. Four leased transport slots overall,
 * at most two speculative turns. No exponential rate-limit backoff.
 */
final class EdgeSpeechQueue {
    static final int PLAY=0,PREFETCH=1,BULK=2;
    static final int MAX_ACTIVE=4,MAX_PREFETCH=2;
    static final class Ticket {
        int priority;final long order;final Runnable run,cancel;
        boolean canceled;
        Ticket(int p,long o,Runnable r,Runnable c){priority=p;order=o;run=r;cancel=c;}
    }
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeSpeechQueue");t.setDaemon(true);return t;
    });
    private static final ArrayList<Ticket> pending=new ArrayList<>(),active=new ArrayList<>();
    private static ScheduledFuture<?> wake;
    private static long order,blockedUntil,lastBackgroundAt,lastLimitedAt;
    private static boolean interactivePlayback;
    private static final long MAX_LOCAL_DELAY_MS=500L;
    private static final long BACKGROUND_GAP_MS=500L;
    private EdgeSpeechQueue(){}
    static synchronized void setInteractivePlayback(boolean playing){
        if(interactivePlayback!=playing){interactivePlayback=playing;schedule();}
    }
    static Ticket submit(int priority,Runnable run){return submit(priority,run,null);}
    static Ticket submit(int priority,Runnable run,Runnable onPreempt){
        Runnable preempt=null;Ticket t;
        synchronized(EdgeSpeechQueue.class){
            t=new Ticket(priority,++order,run,onPreempt);pending.add(t);
            ReaderDiagnosticLog.event("QUEUE","enqueue priority="+priority+" pending="+pending.size()+" active="+active.size());
            if(priority==PLAY&&active.size()>=MAX_ACTIVE){
                Ticket victim=active.stream().filter(a->a.priority>PLAY&&a.cancel!=null)
                    .max(Comparator.comparingInt(a->a.priority)).orElse(null);
                if(victim!=null)preempt=victim.cancel;
            }
            schedule();
        }
        if(preempt!=null)preempt.run();return t;
    }
    static synchronized void promote(Ticket ticket){
        if(ticket==null||ticket.canceled)return;
        ticket.priority=PLAY;schedule();
    }
    static synchronized void release(Ticket ticket){
        if(ticket==null||ticket.canceled)return;
        ticket.canceled=true;pending.remove(ticket);active.remove(ticket);
        ReaderDiagnosticLog.event("QUEUE","release priority="+ticket.priority+" active="+active.size());
        schedule();
    }
    static synchronized void restoreCooldown(long wallClockDeadlineMs){
        long remaining=wallClockDeadlineMs-System.currentTimeMillis();
        if(remaining>0){blockedUntil=Math.max(blockedUntil,SystemClock.elapsedRealtime()+Math.min(remaining,MAX_LOCAL_DELAY_MS));schedule();}
    }
    static synchronized long remainingMs(){return Math.max(0,blockedUntil-SystemClock.elapsedRealtime());}
    static synchronized boolean allowPrefetch(){
        return (lastLimitedAt==0||SystemClock.elapsedRealtime()-lastLimitedAt>=MAX_LOCAL_DELAY_MS)
            &&SystemClock.elapsedRealtime()>=blockedUntil;
    }
    static synchronized void success(){lastLimitedAt=0;}
    static synchronized void limited(Response response){
        lastLimitedAt=SystemClock.elapsedRealtime();
        blockedUntil=Math.max(blockedUntil,lastLimitedAt+MAX_LOCAL_DELAY_MS);
        ReaderDiagnosticLog.event("QUEUE","HTTP 429 local_cooldown_ms=500 server_retry_after="+
            (response!=null&&response.header("Retry-After")!=null));
        Log.w("EdgeSpeechQueue","429 localDelay=500ms; serverRetryAfterPresent="+
            (response!=null&&response.header("Retry-After")!=null));
        schedule();
    }
    private static int runningBackground(){int n=0;for(Ticket t:active)if(t.priority!=PLAY)n++;return n;}
    /** A live foreground listen must not trigger a second, speculative WebSocket handshake.
     * Cached audio is unaffected. The next explicit Play can still create a connection. */
    private static boolean canRunBackground(Ticket t){
        if(t.priority!=PREFETCH || !interactivePlayback)return true;
        return EdgeWebSocketPool.hasIdleTransport();
    }
    private static Ticket nextEligible(){
        return pending.stream().filter(t->!t.canceled)
            .filter(t->t.priority!=BULK||!interactivePlayback)
            .filter(EdgeSpeechQueue::canRunBackground)
            .filter(t->t.priority==PLAY||runningBackground()<MAX_PREFETCH)
            .min(Comparator.comparingInt((Ticket t)->t.priority).thenComparingLong(t->t.order)).orElse(null);
    }
    private static void schedule(){
        if(wake!=null){wake.cancel(false);wake=null;}
        pending.removeIf(t->t.canceled);
        if(active.size()>=MAX_ACTIVE||pending.isEmpty())return;
        Ticket next=nextEligible();if(next==null)return;
        long now=SystemClock.elapsedRealtime(),due=blockedUntil;
        if(next.priority!=PLAY)due=Math.max(due,lastBackgroundAt+BACKGROUND_GAP_MS);
        wake=TIMER.schedule(EdgeSpeechQueue::dispatch,Math.max(0,due-now),TimeUnit.MILLISECONDS);
    }
    private static void dispatch(){
        Ticket next;
        synchronized(EdgeSpeechQueue.class){
            wake=null;
            if(active.size()>=MAX_ACTIVE){return;}
            next=nextEligible();if(next==null)return;
            long now=SystemClock.elapsedRealtime();
            if(now<blockedUntil||(next.priority!=PLAY&&now<lastBackgroundAt+BACKGROUND_GAP_MS)){
                schedule();return;
            }
            pending.remove(next);active.add(next);
            ReaderDiagnosticLog.event("QUEUE","dispatch priority="+next.priority+" active="+active.size()+" pending="+pending.size());
            if(next.priority!=PLAY)lastBackgroundAt=now;
            schedule(); // Fill other free slots while reserving Play priority.
        }
        try{if(!next.canceled)next.run.run();else release(next);}
        catch(Throwable e){Log.e("EdgeSpeechQueue","Cannot start speech",e);release(next);}
    }
    static synchronized int activeCountForTest(){return active.size();}
}
