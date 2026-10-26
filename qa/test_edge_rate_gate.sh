#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/stubs/android/os" "$TMP/stubs/android/util" "$TMP/stubs/okhttp3" "$TMP/stubs/okio" "$TMP/stubs/ru/reader/rsvp"
cat > "$TMP/stubs/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}
JAVA
cat > "$TMP/stubs/android/util/Log.java" <<'JAVA'
package android.util;
public class Log {public static int w(String t,String m){return 0;}public static int i(String t,String m){return 0;}
public static int e(String t,String m,Throwable x){return 0;}}
JAVA
cat > "$TMP/stubs/okhttp3/Response.java" <<'JAVA'
package okhttp3;
public class Response {public int code(){return 429;}public String header(String name){return "120";}}
JAVA
cat > "$TMP/stubs/okhttp3/Request.java" <<'JAVA'
package okhttp3;
public class Request {public static class Builder {public Builder url(String s){return this;}
public Builder header(String k,String v){return this;}public Request build(){return new Request();}}}
JAVA
cat > "$TMP/stubs/okhttp3/WebSocket.java" <<'JAVA'
package okhttp3;
public class WebSocket {public boolean send(String s){return true;}
public boolean close(int c,String s){return true;}public void cancel(){}}
JAVA
cat > "$TMP/stubs/okhttp3/WebSocketListener.java" <<'JAVA'
package okhttp3;
public class WebSocketListener {public void onOpen(WebSocket s,Response r){}
public void onMessage(WebSocket s,String str){}
public void onMessage(WebSocket s,okio.ByteString b){}
public void onFailure(WebSocket s,Throwable error,Response response){}
public void onClosed(WebSocket s,int c,String reason){}}
JAVA
cat > "$TMP/stubs/okio/ByteString.java" <<'JAVA'
package okio;public class ByteString {public byte[] toByteArray(){return new byte[0];}}
JAVA
cat > "$TMP/stubs/okhttp3/OkHttpClient.java" <<'JAVA'
package okhttp3;
import java.util.concurrent.TimeUnit;
public class OkHttpClient {
public static volatile int calls;
public static class Builder {public Builder connectTimeout(long l,TimeUnit u){return this;}
public Builder readTimeout(long l,TimeUnit u){return this;}public OkHttpClient build(){return new OkHttpClient();}}
public WebSocket newWebSocket(Request r,WebSocketListener listener){calls++;
WebSocket socket=new WebSocket();new Thread(()->{try{Thread.sleep(70);}catch(Exception ignored){}
listener.onFailure(socket,new java.io.IOException("Expected HTTP 101"),new Response());}).start();
return socket;}}
JAVA
cat > "$TMP/stubs/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.List;
class EdgeWordTimings {static class Boundary{}static void parseMetadata(String data,List<Boundary> out){}}
JAVA
cat > "$TMP/stubs/ru/reader/rsvp/EdgeToken.java" <<'JAVA'
package ru.reader.rsvp;
class EdgeToken {static String TOKEN="x",VERSION="v";static String gec(){return "token";}}
JAVA
cat > "$TMP/stubs/ru/reader/rsvp/EdgeHandshakeSmoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
class EdgeHandshakeSmoke {
public static void main(String[] args)throws Exception{
 final CountDownLatch latch=new CountDownLatch(1);
 final String[] msg={null};
 EdgeTtsClient client=EdgeTtsClient.synthesize("Пример.","ru-RU-DmitryNeural","+0%",new EdgeTtsClient.Callback(){
 public void progress(int n,long bytes){}
 public void finished(byte[] data,int n,String error){msg[0]=error;latch.countDown();}
 });
 if(!latch.await(4,TimeUnit.SECONDS))throw new RuntimeException("Handshake result missing");
 if(msg[0]==null||!msg[0].contains("HTTP 429"))throw new RuntimeException("Wrong error: "+msg[0]);
 if(EdgeRequestGate.remainingMs()<60000)throw new RuntimeException("HTTP 429 cooldown ignored");
 Thread.sleep(250);
 if(OkHttpClient.calls!=1)throw new RuntimeException("429 triggered a second request: "+OkHttpClient.calls);
 client.cancel();
 System.out.println("PASS: HTTP 429 -> cooldown, no retry, error visible");
}
}
JAVA
cat > "$TMP/stubs/ru/reader/rsvp/EdgeGatePrioritySmoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
class EdgeGatePrioritySmoke {
  public static void main(String[] args)throws Exception{
    CountDownLatch first=new CountDownLatch(1),second=new CountDownLatch(1);
    AtomicInteger step=new AtomicInteger();
    EdgeRequestGate.Ticket held=EdgeRequestGate.enqueue(EdgeRequestGate.PLAY,()->first.countDown());
    if(!first.await(2,TimeUnit.SECONDS))throw new AssertionError("First request not started");
    EdgeRequestGate.Ticket low=EdgeRequestGate.enqueue(EdgeRequestGate.BULK,()->{step.compareAndSet(0,99);second.countDown();});
    EdgeRequestGate.Ticket high=EdgeRequestGate.enqueue(EdgeRequestGate.PLAY,()->{step.compareAndSet(0,1);second.countDown();});
    EdgeRequestGate.release(held);
    if(!second.await(4,TimeUnit.SECONDS)||step.get()!=1)throw new AssertionError("Current playback lost priority: "+step);
    EdgeRequestGate.release(low);EdgeRequestGate.release(high);
    System.out.println("PASS: active playback has priority over full-book downloads");
  }
}
JAVA
mkdir -p "$TMP/classes"
javac -d "$TMP/classes" $(find "$TMP/stubs" -name '*.java') \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeRequestGate.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeTtsClient.java"
java -cp "$TMP/classes" ru.reader.rsvp.EdgeHandshakeSmoke
java -cp "$TMP/classes" ru.reader.rsvp.EdgeGatePrioritySmoke
