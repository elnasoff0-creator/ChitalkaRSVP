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
import java.util.concurrent.atomic.*;
public class WebSocket {
 public static final AtomicInteger active=new AtomicInteger(),maxActive=new AtomicInteger();
 public static final AtomicInteger sentTurns=new AtomicInteger();
 final WebSocketListener listener;final AtomicBoolean stopped=new AtomicBoolean();
 public WebSocket(WebSocketListener l){listener=l;}
 public void markOpened(){int v=active.incrementAndGet();maxActive.accumulateAndGet(v,Math::max);}
 public boolean send(String s){
  if(stopped.get())return false;
  if(s.contains("Path:ssml")){
   sentTurns.incrementAndGet();
   new Thread(()->{try{
    Thread.sleep(120);
    if(stopped.get())return;
    byte[] head="Path: audio\r\nContent-Type: audio/mpeg".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    byte[] p=new byte[head.length+2702];p[0]=(byte)(head.length>>8);p[1]=(byte)head.length;
    System.arraycopy(head,0,p,2,head.length);
    p[head.length+2]='I';p[head.length+3]='D';p[head.length+4]='3';
    listener.onMessage(this,new okio.ByteString(p));
    if(stopped.get())return;
    listener.onMessage(this,"Path: turn.end\r\n\r\n");
   }catch(Exception e){listener.onFailure(this,e,null);}}).start();
  }
  return true;
 }
 public boolean close(int c,String s){cancel();return true;}
 public void cancel(){if(stopped.compareAndSet(false,true)){
  new Thread(()->{try{Thread.sleep(80);}catch(Exception ignore){}active.decrementAndGet();
   listener.onFailure(this,new java.io.IOException("canceled"),null);}).start();}}
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
import java.util.concurrent.TimeUnit;
public class OkHttpClient {
 public static volatile int calls;public static volatile boolean tooMany;
 public static class Builder {
  public Builder connectionPool(ConnectionPool p){return this;}
  public Builder connectTimeout(long n,TimeUnit t){return this;}
  public Builder readTimeout(long n,TimeUnit t){return this;}
  public Builder pingInterval(long n,TimeUnit t){return this;}
  public OkHttpClient build(){return new OkHttpClient();}
 }
 public WebSocket newWebSocket(Request request,WebSocketListener listener){
  calls++;WebSocket ws=new WebSocket(listener);
  new Thread(()->{
   if(tooMany){listener.onFailure(ws,new java.io.IOException("HTTP 429"),new Response());return;}
   ws.markOpened();listener.onOpen(ws,new Response());
  }).start();return ws;
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;import java.util.List;
class EdgeWordTimings {static class Boundary{}static void parseMetadata(String s,List<Boundary> out){}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestPool.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.*;
import okhttp3.*;
public class TestPool {
 static EdgeTtsClient.Callback cb(CountDownLatch done,String[] errors,int index){
  return new EdgeTtsClient.Callback(){
   public void progress(int n,long b){}
   public void finished(byte[] mp3,int chunks,String error){
    errors[index]=error;
    if(error==null&&(mp3==null||mp3.length!=2700))errors[index]="bad MP3";
    done.countDown();
   }
  };
 }
 static void check(boolean v,String description){if(!v)throw new AssertionError(description);}
 public static void main(String[] args)throws Exception {
  CountDownLatch one=new CountDownLatch(1);String[] errors=new String[6];
  EdgeTtsClient first=EdgeTtsClient.synthesize("Один текст", "ru-RU-SvetlanaNeural", "+0%",cb(one,errors,0));
  check(one.await(3,TimeUnit.SECONDS),"first synthetic turn timed out");
  check(errors[0]==null,"first MP3: "+errors[0]);
  check(OkHttpClient.calls==1,"first handshake expected");
  check(EdgeWebSocketPool.idleForTest()==1,"finished socket not returned to idle pool");
  CountDownLatch again=new CountDownLatch(1);
  EdgeTtsClient second=EdgeTtsClient.synthesize("Другой текст", "ru-RU-SvetlanaNeural", "+0%",cb(again,errors,1));
  check(again.await(3,TimeUnit.SECONDS),"reused turn timed out");
  check(errors[1]==null,"second MP3: "+errors[1]);
  check(OkHttpClient.calls==1,"reused turn performed another handshake");
  check(WebSocket.sentTurns.get()==2,"reused WebSocket must send second SSML");
  System.out.println("PASS: two turns, one handshake, cached open websocket reused");
  CountDownLatch parallel=new CountDownLatch(4);
  for(int i=2;i<6;i++)EdgeTtsClient.synthesize("Параллельный номер "+i,
    "ru-RU-SvetlanaNeural","+0%",cb(parallel,errors,i));
  check(parallel.await(5,TimeUnit.SECONDS),"four concurrent sessions did not complete");
  for(int i=2;i<6;i++)check(errors[i]==null,"concurrent MP3 "+i+": "+errors[i]);
  check(WebSocket.maxActive.get()<=4,"pool exceeded 4 open sockets");
  check(EdgeWebSocketPool.allocatedForTest()<=4,"pool exceeded 4 resources");
  check(EdgeSpeechQueue.activeCountForTest()==0,"network tickets leaked");
  System.out.println("PASS: bounded resource pool and four parallel turns");
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/Test429.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.*;
import okhttp3.*;
public class Test429 {
 public static void main(String[] args)throws Exception {
  OkHttpClient.tooMany=true;Response.status=429;CountDownLatch done=new CountDownLatch(1);String[] error={null};
  EdgeTtsClient.synthesize("Лимит","ru-RU-SvetlanaNeural","+0%",new EdgeTtsClient.Callback(){
   public void progress(int n,long b){}public void finished(byte[] b,int n,String e){error[0]=e;done.countDown();}
  });
  if(!done.await(2,TimeUnit.SECONDS)||error[0]==null||!error[0].contains("429"))
   throw new AssertionError("Rate limit not reported");
  if(EdgeSpeechQueue.remainingMs()>500)throw new AssertionError("Local cooldown exceeds 500ms");
  Thread.sleep(250);if(OkHttpClient.calls!=1)throw new AssertionError("Unwanted automatic retry on 429");
  System.out.println("PASS: HTTP429, no automatic retry, <=500ms local cooldown");
 }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestCancel.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.*;
import okhttp3.*;
public class TestCancel {
 public static void main(String[] args)throws Exception {
  EdgeTtsClient first=EdgeTtsClient.synthesize("Отменённый запрос", "ru-RU-SvetlanaNeural", "+0%",
    new EdgeTtsClient.Callback(){public void progress(int n,long b){}
    public void finished(byte[] b,int n,String e){}});
  long deadline=System.currentTimeMillis()+1000;
  while(WebSocket.active.get()==0&&System.currentTimeMillis()<deadline)Thread.sleep(5);
  if(WebSocket.active.get()==0)throw new AssertionError("initial socket never opened");
  first.cancel();
  CountDownLatch done=new CountDownLatch(1);
  String[] error={null};
  EdgeTtsClient.synthesize("Следующий запрос", "ru-RU-SvetlanaNeural", "+0%",
    new EdgeTtsClient.Callback(){public void progress(int n,long b){}
    public void finished(byte[] b,int n,String e){error[0]=e;done.countDown();}});
  if(!done.await(3,TimeUnit.SECONDS)||error[0]!=null)
    throw new AssertionError("follow-up after cancel: "+error[0]);
  if(OkHttpClient.calls!=2)throw new AssertionError("cancelled socket reused");
  if(WebSocket.maxActive.get()>4)throw new AssertionError("exceeded pool capacity");
  System.out.println("PASS: cancelled resource destroyed; next request uses fresh socket");
 }
}
JAVA
# App-local diagnostics are asynchronous on Android; network unit tests use a no-op logger.
cat > "$TMP/src/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
final class ReaderDiagnosticLog { static void event(String component,String detail){} }
JAVA
javac -encoding UTF-8 -d "$TMP/classes" $(find "$TMP/src" -name '*.java') \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechQueue.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWebSocketPool.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechEngine.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeServerClock.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeToken.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWsFrames.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeAudioFormat.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeTtsClient.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestPool
java -cp "$TMP/classes" ru.reader.rsvp.Test429
java -cp "$TMP/classes" ru.reader.rsvp.TestCancel
