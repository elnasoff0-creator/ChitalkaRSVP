#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/android/os" "$TMP/src/android/util" "$TMP/src/okhttp3" "$TMP/src/okio" "$TMP/src/ru/reader/rsvp"
cat > "$TMP/src/android/os/SystemClock.java" <<'JAVA'
package android.os;public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}
JAVA
cat > "$TMP/src/android/util/Log.java" <<'JAVA'
package android.util;public class Log {public static int w(String t,String s){return 0;}
public static int i(String t,String s){return 0;}public static int e(String t,String s,Throwable e){return 0;}}
JAVA
cat > "$TMP/src/okhttp3/ConnectionPool.java" <<'JAVA'
package okhttp3;import java.util.concurrent.TimeUnit;
public class ConnectionPool {public ConnectionPool(int a,long b,TimeUnit t){}}
JAVA
cat > "$TMP/src/okhttp3/Response.java" <<'JAVA'
package okhttp3;public class Response {public int code(){return 101;}public String header(String h){return null;}}
JAVA
cat > "$TMP/src/okhttp3/Request.java" <<'JAVA'
package okhttp3;public class Request {public static class Builder {public Builder url(String s){return this;}
public Builder header(String a,String b){return this;}public Request build(){return new Request();}}}
JAVA
cat > "$TMP/src/okio/ByteString.java" <<'JAVA'
package okio;public class ByteString {private final byte[] value;
public ByteString(byte[] b){value=b;}public byte[] toByteArray(){return value;}}
JAVA
cat > "$TMP/src/okhttp3/WebSocket.java" <<'JAVA'
package okhttp3;public class WebSocket {
public static volatile String ssml="";
public boolean send(String value){if(value.contains("Path:ssml"))ssml=value;return true;}
public boolean close(int code,String reason){return true;}public void cancel(){}
}
JAVA
cat > "$TMP/src/okhttp3/WebSocketListener.java" <<'JAVA'
package okhttp3;public class WebSocketListener {
public void onOpen(WebSocket w,Response r){} public void onMessage(WebSocket w,String s){}
public void onMessage(WebSocket w,okio.ByteString s){}
public void onFailure(WebSocket w,Throwable t,Response r){}
public void onClosed(WebSocket w,int c,String reason){}
}
JAVA
cat > "$TMP/src/okhttp3/OkHttpClient.java" <<'JAVA'
package okhttp3;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
public class OkHttpClient {
public static class Builder {public Builder connectionPool(ConnectionPool p){return this;}
public Builder connectTimeout(long n,TimeUnit t){return this;}
public Builder readTimeout(long n,TimeUnit t){return this;}
public Builder pingInterval(long n,TimeUnit t){return this;}
public OkHttpClient build(){return new OkHttpClient();}}
public WebSocket newWebSocket(Request request,WebSocketListener listener){
 WebSocket ws=new WebSocket();
 new Thread(()->{try{
    listener.onOpen(ws,new Response());
    Thread.sleep(35);
    byte[] header="Path:audio\r\nContent-Type:audio/mpeg".getBytes(StandardCharsets.US_ASCII);
    byte[] bytes=new byte[2+header.length+2100];bytes[0]=(byte)(header.length>>8);bytes[1]=(byte)header.length;
    System.arraycopy(header,0,bytes,2,header.length);
    listener.onMessage(ws,new okio.ByteString(bytes));
    Thread.sleep(100);
    listener.onMessage(ws,"Path:turn.end\r\n\r\n");
 }catch(Exception ex){throw new RuntimeException(ex);}}).start();
 return ws;
}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;import java.util.List;
class EdgeWordTimings {static class Boundary{} static void parseMetadata(String s,List<Boundary> b){}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeToken.java" <<'JAVA'
package ru.reader.rsvp;class EdgeToken {static final String TOKEN="t",VERSION="v";
static String generate(long seconds){return "valid-test-token";}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeRealtimeStreamSmoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.WebSocket;
public class EdgeRealtimeStreamSmoke {
public static void main(String[] args)throws Exception {
 CountDownLatch audio=new CountDownLatch(1),finished=new CountDownLatch(1);
 AtomicInteger received=new AtomicInteger();
 EdgeTtsClient c=EdgeTtsClient.synthesize("Тест потокового воспроизведения", "ru-RU-DmitryNeural", "+28%",new EdgeTtsClient.Callback(){
   public boolean streamOnly(){return true;}
   public void progress(int chunks,long bytes){}
   public void audio(byte[] b,int from,int length){received.addAndGet(length);audio.countDown();}
   public void finished(byte[] bytes,int chunks,String error){
       if(error!=null||bytes!=null||received.get()!=2100)throw new AssertionError("Stream outcome: "+error);
       finished.countDown();
   }
 });
 if(!audio.await(2,TimeUnit.SECONDS))throw new AssertionError("No early MP3 frame");
 if(finished.getCount()==0)throw new AssertionError("Stream did not precede completion");
 if(!finished.await(2,TimeUnit.SECONDS))throw new AssertionError("Never received turn.end");
 if(!WebSocket.ssml.contains("rate='+28%'"))throw new AssertionError("Configured Edge rate was ignored: "+WebSocket.ssml);
 System.out.println("PASS: first audio before turn.end, streaming-only memory, rate in SSML");
 c.cancel();
}
}
JAVA
mkdir -p "$TMP/classes"
javac -encoding UTF-8 -d "$TMP/classes" $(find "$TMP/src" -name '*.java') \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeRequestGate.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeTtsClient.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeRealtimeTransport.java"
java -cp "$TMP/classes" ru.reader.rsvp.EdgeRealtimeStreamSmoke
