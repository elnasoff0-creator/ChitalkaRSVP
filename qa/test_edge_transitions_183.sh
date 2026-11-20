#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ru/reader/rsvp"
cat > "$TMP/ru/reader/rsvp/TestEdgeTransition183.java" <<'JAVA'
package ru.reader.rsvp;
import java.io.IOException;
import java.util.concurrent.*;
/** Simulates WebSocket delivery, lost producer, and error propagation to Media3. */
public final class TestEdgeTransition183 {
  static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception {
    ExecutorService pool=Executors.newFixedThreadPool(3);
    try {
      EdgeLiveBuffer first=new EdgeLiveBuffer(3000L);
      EdgeLiveBuffer next=new EdgeLiveBuffer(3000L);
      Future<Integer> f1=pool.submit(()->first.read(0,new byte[8],0,8));
      Future<Integer> f2=pool.submit(()->next.read(0,new byte[8],0,8));
      Thread.sleep(65);
      check(!f1.isDone()&&!f2.isDone(),"both readers must await their own source");
      first.append(new byte[]{73,68,51,1},0,4);
      check(f1.get(1,TimeUnit.SECONDS)==4,"current MP3 must become available first");
      check(!f2.isDone(),"prefetch must not spuriously finish next MP3");
      next.append(new byte[]{73,68,51,2},0,4);
      check(f2.get(1,TimeUnit.SECONDS)==4,"next MP3 must become available while current plays");
      first.complete();next.complete();
      check(first.read(4,new byte[8],0,8)==-1,"first EOF missing");
      check(next.read(4,new byte[8],0,8)==-1,"next EOF missing");

      // A stranded producer must not freeze Media3's loader indefinitely.
      EdgeLiveBuffer stuck=new EdgeLiveBuffer(130L);
      long started=System.nanoTime();
      Future<Boolean> timeout=pool.submit(()->{
        try{stuck.read(0,new byte[8],0,8);return false;}
        catch(IOException failure){return failure.getMessage().contains("stalled");}
      });
      check(timeout.get(2,TimeUnit.SECONDS),"stalled stream must terminate with IOException");
      check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<1500,
          "stalled stream exceeded bounded wait");
      EdgeLiveBuffer stopped=new EdgeLiveBuffer(3000L);
      Future<Boolean> canceled=pool.submit(()->{
        try{stopped.read(0,new byte[8],0,8);return false;}
        catch(IOException error){return error.getMessage().contains("cancel");}
      });
      Thread.sleep(50);stopped.fail(new IOException("cancel current source"));
      check(canceled.get(1,TimeUnit.SECONDS),"canceled buffer reader did not unblock");
      System.out.println("PASS: parallel live MP3, immediate next audio, bounded stall, cancellation");
    } finally {pool.shutdownNow();}
  }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeLiveBuffer.java" \
 "$TMP/ru/reader/rsvp/TestEdgeTransition183.java"
java -cp "$TMP" ru.reader.rsvp.TestEdgeTransition183
