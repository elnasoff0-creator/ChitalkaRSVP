#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ru/reader/rsvp"
cat > "$TMP/ru/reader/rsvp/TestLiveBuffer.java" <<'JAVA'
package ru.reader.rsvp;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
public class TestLiveBuffer {
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args)throws Exception{
        ExecutorService threads=Executors.newSingleThreadExecutor();
        try{
            EdgeLiveBuffer buf=new EdgeLiveBuffer();
            byte[] dest=new byte[20];
            Future<Integer> reading=threads.submit(()->buf.read(0,dest,0,20));
            Thread.sleep(70);check(!reading.isDone(),"reader must wait for network");
            byte[] incoming="ID3abcdef".getBytes(StandardCharsets.US_ASCII);
            buf.append(incoming,0,3);
            check(reading.get(2,TimeUnit.SECONDS)==3,"first MP3 prefix not immediately readable");
            check(dest[0]=='I'&&dest[1]=='D'&&dest[2]=='3',"invalid first bytes");
            buf.append(incoming,3,incoming.length-3);
            check(buf.read(3,dest,0,20)==incoming.length-3,"subsequent bytes unavailable");
            Future<Integer> eof=threads.submit(()->buf.read(incoming.length,dest,0,20));
            Thread.sleep(60);check(!eof.isDone(),"must wait for stream completion");
            buf.complete();check(eof.get(2,TimeUnit.SECONDS)==-1,"missing clean EOS");
            check(buf.read(0,dest,0,20)==incoming.length,"repeated read lost data");
            EdgeLiveBuffer canceled=new EdgeLiveBuffer();
            Future<Boolean> stopped=threads.submit(()->{
                try{canceled.read(0,new byte[32],0,32);return false;}
                catch(IOException expected){return true;}
            });
            Thread.sleep(60);canceled.fail(new IOException("user tapped another sentence"));
            check(stopped.get(2,TimeUnit.SECONDS),"cancellation failed to interrupt loader");
            System.out.println("PASS: first-frame progressive reading, EOF, replay, cancellation");
        }finally{threads.shutdownNow();}
    }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeLiveBuffer.java" \
 "$TMP/ru/reader/rsvp/TestLiveBuffer.java"
java -cp "$TMP" ru.reader.rsvp.TestLiveBuffer
