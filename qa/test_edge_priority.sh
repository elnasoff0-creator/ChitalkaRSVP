#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/android/os" "$TMP/android/util" "$TMP/okhttp3" "$TMP/ru/reader/rsvp"
cat > "$TMP/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}
JAVA
cat > "$TMP/android/util/Log.java" <<'JAVA'
package android.util;
public class Log {public static int w(String a,String b){return 0;}
public static int e(String a,String b,Throwable e){return 0;}}
JAVA
cat > "$TMP/okhttp3/Response.java" <<'JAVA'
package okhttp3;
public class Response {public String header(String n){return null;}}
JAVA
cat > "$TMP/ru/reader/rsvp/TestEdgePriority.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.*;
public class TestEdgePriority {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        CountDownLatch bulkStarted=new CountDownLatch(1);
        CountDownLatch userStarted=new CountDownLatch(1);
        EdgeSpeechQueue.Ticket[] background=new EdgeSpeechQueue.Ticket[1];
        background[0]=EdgeSpeechQueue.submit(EdgeSpeechQueue.BULK,
            bulkStarted::countDown,()->EdgeSpeechQueue.release(background[0]));
        check(bulkStarted.await(2,TimeUnit.SECONDS),"bulk never started");
        long start=System.nanoTime();
        EdgeSpeechQueue.Ticket play=EdgeSpeechQueue.submit(EdgeSpeechQueue.PLAY,userStarted::countDown);
        check(userStarted.await(750,TimeUnit.MILLISECONDS),"Play was stuck behind background work");
        long latencyMs=(System.nanoTime()-start)/1000000;
        check(latencyMs<750,"tap blocked for "+latencyMs+"ms");
        EdgeSpeechQueue.release(play);
        // While interactive reading is active, explicit full-book synthesis must
        // wait for the user to pause or finish the current reading session.
        EdgeSpeechQueue.setInteractivePlayback(true);
        CountDownLatch deferred=new CountDownLatch(1);
        EdgeSpeechQueue.Ticket queued=EdgeSpeechQueue.submit(EdgeSpeechQueue.BULK,deferred::countDown);
        check(!deferred.await(250,TimeUnit.MILLISECONDS),"background WebSocket ran during Edge playback");
        EdgeSpeechQueue.setInteractivePlayback(false);
        check(deferred.await(2,TimeUnit.SECONDS),"background WebSocket did not resume after playback");
        EdgeSpeechQueue.release(queued);
        EdgeSpeechQueue.limited(null);
        long cooldown=EdgeSpeechQueue.remainingMs();
        check(cooldown>=700&&cooldown<=1500,"missing Retry-After should use about 1s cooldown: "+cooldown);
        System.out.println("PASS: user Play preempts full-book job; no 4.5s tap delay; no 15min default 429");
    }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$TMP/android/os/SystemClock.java" "$TMP/android/util/Log.java" "$TMP/okhttp3/Response.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechQueue.java" \
 "$TMP/ru/reader/rsvp/TestEdgePriority.java"
java -cp "$TMP" ru.reader.rsvp.TestEdgePriority
