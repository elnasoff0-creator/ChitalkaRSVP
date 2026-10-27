#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/android/os" "$TMP/src/android/util" "$TMP/src/okhttp3" "$TMP/src/okio" "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}
JAVA
cat > "$TMP/src/android/util/Log.java" <<'JAVA'
package android.util;
public class Log {public static int w(String a,String b){return 0;}public static int i(String a,String b){return 0;}
public static int e(String a,String b,Throwable c){return 0;}public static int d(String a,String b){return 0;}}
JAVA
cat > "$TMP/src/okhttp3/ConnectionPool.java" <<'JAVA'
package okhttp3;import java.util.concurrent.TimeUnit;
public class ConnectionPool {public ConnectionPool(int a,long b,TimeUnit t){}}
JAVA
cat > "$TMP/src/okhttp3/Response.java" <<'JAVA'
package okhttp3;
public class Response {public static volatile int status=101;
public static volatile boolean badDate,shortSkew;
public int code(){return status;}
public String header(String s){
 if("Retry-After".equals(s))return "47";
 if(!"Date".equals(s)||badDate)return badDate?"bad-date":null;
 java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",java.util.Locale.US);
 fmt.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));
 return fmt.format(new java.util.Date(System.currentTimeMillis()+(shortSkew?0:600000L)));
}}
JAVA
cat > "$TMP/src/okhttp3/Request.java" <<'JAVA'
package okhttp3;
public class Request {public final String url;public Request(String s){url=s;}
public static class Builder {String u;public Builder url(String s){u=s;return this;}
public Builder header(String a,String b){return this;}public Request build(){return new Request(u);}}
}
JAVA
cat > "$TMP/src/okio/ByteString.java" <<'JAVA'
package okio;
public class ByteString {private final byte[] bytes;public ByteString(byte[] v){bytes=v;}
public byte[] toByteArray(){return bytes;}}
JAVA
cat > "$TMP/src/okhttp3/WebSocket.java" <<'JAVA'
package okhttp3;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
public class WebSocket {
 public static volatile String sent="";
 public static final AtomicInteger active=new AtomicInteger(),maxActive=new AtomicInteger();
 private final WebSocketListener listener;
 private final AtomicBoolean terminated=new AtomicBoolean(),opened=new AtomicBoolean();
 public WebSocket(WebSocketListener l){listener=l;}
 public void markOpened(){opened.set(true);int n=active.incrementAndGet();maxActive.accumulateAndGet(n,Math::max);}
 public boolean send(String s){sent+=s;return true;}
 private void shutdown(boolean clean){
  if(!terminated.compareAndSet(false,true))return;
  new Thread(()->{try{Thread.sleep(110);}catch(Exception ignored){}
   if(opened.get())active.decrementAndGet();
   if(clean)listener.onClosed(this,1000,"closed");
   else listener.onFailure(this,new java.io.IOException("canceled"),null);
  }).start();
 }
 public boolean close(int code,String reason){shutdown(true);return true;}
 public void cancel(){shutdown(false);}
}
JAVA
cat > "$TMP/src/okhttp3/WebSocketListener.java" <<'JAVA'
package okhttp3;
public class WebSocketListener {public void onOpen(WebSocket w,Response r){}
public void onMessage(WebSocket w,String m){}public void onMessage(WebSocket w,okio.ByteString b){}
public void onFailure(WebSocket w,Throwable t,Response r){}public void onClosed(WebSocket w,int c,String reason){}
}
JAVA
cat > "$TMP/src/okhttp3/OkHttpClient.java" <<'JAVA'
package okhttp3;
import java.util.concurrent.TimeUnit;import java.nio.charset.StandardCharsets;
public class OkHttpClient {
 public static volatile String target="",firstTarget="";
 public static volatile int calls; public static volatile boolean always403;
 public static class Builder {
  public Builder connectionPool(ConnectionPool p){return this;}
  public Builder connectTimeout(long n,TimeUnit t){return this;}
  public Builder readTimeout(long n,TimeUnit t){return this;}
  public Builder pingInterval(long n,TimeUnit t){return this;}
  public OkHttpClient build(){return new OkHttpClient();}}
 public WebSocket newWebSocket(Request request,WebSocketListener listener){
  target=request.url;calls++;if(calls==1)firstTarget=target;
  WebSocket ws=new WebSocket(listener);
  new Thread(()->{try{
    if(Response.status==429){listener.onFailure(ws,new java.io.IOException("limited"),new Response());return;}
    if(Response.status==403&&(always403||calls==1)){
        listener.onFailure(ws,new java.io.IOException("forbidden"),new Response());return;
    }
    ws.markOpened();listener.onOpen(ws,new Response());Thread.sleep(30);
    byte[] header="Path: audio\r\nContent-Type: audio/mpeg".getBytes(StandardCharsets.US_ASCII);
    byte[] bytes=new byte[2+header.length+2700];bytes[0]=(byte)(header.length>>8);bytes[1]=(byte)header.length;
    System.arraycopy(header,0,bytes,2,header.length);
    bytes[2+header.length]='I';bytes[3+header.length]='D';bytes[4+header.length]='3';
    listener.onMessage(ws,new okio.ByteString(bytes));Thread.sleep(80);
    byte[] terminator="Path:audio\r\n".getBytes(StandardCharsets.US_ASCII);
    byte[] end=new byte[2+terminator.length];end[0]=(byte)(terminator.length>>8);end[1]=(byte)terminator.length;
    System.arraycopy(terminator,0,end,2,terminator.length);
    listener.onMessage(ws,new okio.ByteString(end));
    listener.onMessage(ws,"Path: turn.end\r\n\r\n");
  }catch(Exception ex){throw new RuntimeException(ex);}}).start();
  return ws;
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;import java.util.List;
class EdgeWordTimings {static class Boundary{}static void parseMetadata(String s,List<Boundary> out){}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeSpeechEngineSmoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;import okhttp3.Response;import okhttp3.WebSocket;
class EdgeSpeechEngineSmoke {
 public static void main(String[] args)throws Exception{
  CountDownLatch finish=new CountDownLatch(1);
  final byte[][] saved={null};final String[] errors={null};
  final long[] firstPacket={0},ended={0};
  EdgeTtsClient c=EdgeTtsClient.synthesize("Проверка быстрого синтеза.","ru-RU-DmitryNeural","+28%",new EdgeTtsClient.Callback(){
   public void progress(int n,long b){}
   public void audio(byte[] bytes,int from,int length){
      if(firstPacket[0]==0)firstPacket[0]=System.nanoTime();
      if(length!=2700)throw new AssertionError("Missing live MP3 bytes");
   }
   public void finished(byte[] mp3,int chunks,String problem){
      ended[0]=System.nanoTime();saved[0]=mp3;errors[0]=problem;finish.countDown();
   }
  });
  if(!finish.await(3,TimeUnit.SECONDS))throw new AssertionError("Never finished");
  if(saved[0]==null||saved[0].length!=2700||errors[0]!=null||EdgeAudioFormat.identify(saved[0],saved[0].length)==null)
      throw new AssertionError("MP3 incomplete: "+errors[0]);
  if(firstPacket[0]==0 || firstPacket[0]>=ended[0])
      throw new AssertionError("First audio must be streamed before turn.end");
  if(!WebSocket.sent.contains("rate='+28%'"))throw new AssertionError("SSML rate not applied");
  if(!OkHttpClient.target.startsWith("wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1?Ocp-Apim-Subscription-Key="))
      throw new AssertionError("Outdated Edge endpoint: "+OkHttpClient.target);
  if(OkHttpClient.calls!=1)throw new AssertionError("Unexpected network retries");
  c.cancel();
  System.out.println("PASS: MP3 with spaced headers and terminal control packet, voice/rate, endpoint, one handshake");
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeSpeech429Smoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;import okhttp3.Response;
class EdgeSpeech429Smoke {
 public static void main(String[] args)throws Exception{
  Response.status=429;CountDownLatch finish=new CountDownLatch(1);
  final String[] error={null};
  EdgeTtsClient c=EdgeTtsClient.synthesize("Проверка ограничений.","ru-RU-SvetlanaNeural","+0%",new EdgeTtsClient.Callback(){
   public void progress(int n,long b){}
   public void finished(byte[] b,int n,String problem){error[0]=problem;finish.countDown();}
  });
  if(!finish.await(3,TimeUnit.SECONDS))throw new AssertionError("429 handshake hung");
  if(error[0]==null||!error[0].contains("HTTP 429"))throw new AssertionError(error[0]);
  if(EdgeSpeechQueue.remainingMs()<45000)throw new AssertionError("Ignored Retry-After");
  Thread.sleep(140);
  if(OkHttpClient.calls!=1)throw new AssertionError("Automatic retry after 429");
  c.cancel();
  System.out.println("PASS: 429 fail-fast, Retry-After cooldown, no retry storm");
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeSpeechSerialSmoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
class EdgeSpeechSerialSmoke {
 public static void main(String[] args)throws Exception{
  Response.status=101;
  CountDownLatch ended=new CountDownLatch(2);
  final String[] errors={null,null};
  EdgeTtsClient.Callback first=new EdgeTtsClient.Callback(){
   public void progress(int n,long b){}
   public void finished(byte[] mp3,int parts,String error){errors[0]=error;ended.countDown();}
  };
  EdgeTtsClient.Callback second=new EdgeTtsClient.Callback(){
   public void progress(int n,long b){}
   public void finished(byte[] mp3,int parts,String error){errors[1]=error;ended.countDown();}
  };
  EdgeTtsClient a=EdgeTtsClient.synthesize("Первый текст", "ru-RU-SvetlanaNeural", "+0%",first);
  EdgeTtsClient b=EdgeTtsClient.synthesize("Второй текст", "ru-RU-SvetlanaNeural", "+0%",second);
  if(!ended.await(6,TimeUnit.SECONDS))throw new AssertionError("Serial requests timed out");
  if(errors[0]!=null||errors[1]!=null)throw new AssertionError("Serial errors: "+errors[0]+", "+errors[1]);
  Thread.sleep(250);
  if(OkHttpClient.calls!=2 || WebSocket.maxActive.get()!=1 || WebSocket.active.get()!=0)
   throw new AssertionError("WebSockets overlapped: calls="+OkHttpClient.calls+
     " maxActive="+WebSocket.maxActive+" active="+WebSocket.active);
  a.cancel();b.cancel();
  System.out.println("PASS: second WebSocket waited for first socket close acknowledgement");
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeSpeechCancelSerialSmoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.WebSocket;
class EdgeSpeechCancelSerialSmoke {
 public static void main(String[] args)throws Exception{
  Response.status=101;
  EdgeTtsClient first=EdgeTtsClient.synthesize("Текст для отмены", "ru-RU-SvetlanaNeural", "+0%",
   new EdgeTtsClient.Callback(){public void progress(int n,long b){}public void finished(byte[] b,int n,String e){}});
  long until=System.currentTimeMillis()+1000;
  while(WebSocket.active.get()==0 && System.currentTimeMillis()<until)Thread.sleep(5);
  if(WebSocket.active.get()==0)throw new AssertionError("First WebSocket never opened");
  first.cancel();
  CountDownLatch done=new CountDownLatch(1);
  EdgeTtsClient second=EdgeTtsClient.synthesize("Следующий текст", "ru-RU-SvetlanaNeural", "+0%",
   new EdgeTtsClient.Callback(){public void progress(int n,long b){}public void finished(byte[] b,int n,String e){done.countDown();}});
  if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("Second socket blocked after cancel");
  Thread.sleep(200);
  if(OkHttpClient.calls!=2||WebSocket.maxActive.get()!=1)
   throw new AssertionError("Sockets overlapped during cancellation: "+WebSocket.maxActive);
  second.cancel();
  System.out.println("PASS: cancel completes before replacement WebSocket opens");
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeSpeech403Smoke.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.CountDownLatch;import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;import okhttp3.Response;
class EdgeSpeech403Smoke {
 public static void main(String[] args)throws Exception{
  String scenario=args[0];
  Response.status=403;
  Response.badDate="bad-date".equals(scenario);
  Response.shortSkew="same-window".equals(scenario);
  OkHttpClient.always403="repeat-403".equals(scenario);
  CountDownLatch complete=new CountDownLatch(1);
  final String[] error={null};final byte[][] audio={null};
  EdgeTtsClient synth=EdgeTtsClient.synthesize("Проверка коррекции времени", "ru-RU-SvetlanaNeural", "+0%",
   new EdgeTtsClient.Callback(){
    public void progress(int frames,long size){}
    public void finished(byte[] bytes,int frames,String problem){audio[0]=bytes;error[0]=problem;complete.countDown();}
   });
  if(!complete.await(4,TimeUnit.SECONDS))throw new AssertionError("403 handshake hung");
  int expectedCalls=("bad-date".equals(scenario)||"same-window".equals(scenario))?1:2;
  if(OkHttpClient.calls!=expectedCalls)throw new AssertionError("Expected "+expectedCalls+" handshakes got "+OkHttpClient.calls);
  if("recover".equals(scenario)){
   if(error[0]!=null||audio[0]==null||audio[0].length<2000)throw new AssertionError("403 recovery failed: "+error[0]);
   if(OkHttpClient.firstTarget.equals(OkHttpClient.target))throw new AssertionError("Retry did not regenerate Sec-MS-GEC");
  } else if(error[0]==null||!error[0].contains("403"))throw new AssertionError("Should fail once with 403: "+error[0]);
  synth.cancel();
  System.out.println("PASS: HTTP 403 scenario: "+scenario+", calls="+OkHttpClient.calls);
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" $(find "$TMP/src" -name '*.java') \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechQueue.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechEngine.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeServerClock.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeToken.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWsFrames.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeAudioFormat.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeTtsClient.java"
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeechEngineSmoke
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeech429Smoke
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeech403Smoke recover
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeech403Smoke repeat-403
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeech403Smoke bad-date
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeech403Smoke same-window

java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeechSerialSmoke
java -cp "$TMP/classes" ru.reader.rsvp.EdgeSpeechCancelSerialSmoke
