package ru.reader.rsvp;

import android.os.SystemClock;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** Complete-file Edge speech engine. No live audio decoder, growing Media3 source,
 * per-sentence streaming playlist or intermediate PCM buffer. Playback begins when
 * a verified MP3 has been atomically committed to the cache.
 * Edge's free Read Aloud service is unofficial: HTTP 429 can still be enforced
 * remotely and cannot be fixed by changing the client implementation.
 */
final class EdgeSpeechEngine {
    private static final String URL="wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1";
    private static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0";
    private static final String ORIGIN="chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    private static final int MAX_INPUT_UTF8=4096,MAX_AUDIO=12*1024*1024;
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(3,5,TimeUnit.MINUTES))
        .connectTimeout(10,TimeUnit.SECONDS).readTimeout(0,TimeUnit.MILLISECONDS)
        .pingInterval(20,TimeUnit.SECONDS).build();
    private static final ScheduledExecutorService TIMER=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeSpeechWatchdog");t.setDaemon(true);return t;
    });
    private final String text,voice,rate;
    private final int priority;
    private final EdgeTtsClient.Callback callback;
    private final List<EdgeWordTimings.Boundary> words=new ArrayList<>();
    private final ByteArrayOutputStream audio=new ByteArrayOutputStream();
    private EdgeSpeechQueue.Ticket ticket;
    private ScheduledFuture<?> watchdog;
    private WebSocket socket;
    private boolean finished,canceled;
    private long started,firstAudio,lastFrame;
    private int frames;
    EdgeSpeechEngine(String value,String voice,String rate,int priority,EdgeTtsClient.Callback callback){
        this.text=value==null?"":value;
        this.voice=voice==null?"ru-RU-SvetlanaNeural":voice;
        this.rate=rate!=null&&rate.matches("[+-]\\d{1,3}%")?rate:"+0%";
        this.priority=priority;this.callback=callback;
    }
    void start(){
        synchronized(this){if(finished||canceled)return;ticket=EdgeSpeechQueue.submit(priority,this::open);}
    }
    void cancel(){
        WebSocket old;
        synchronized(this){if(finished)return;finished=true;canceled=true;stopWatchdog();old=socket;
            EdgeSpeechQueue.release(ticket);ticket=null;}
        if(old!=null)old.cancel();
    }
    private static String id(){return UUID.randomUUID().toString().replace("-","");}
    private static String escaped(String s){return s.replace("&","&amp;").replace("<","&lt;")
        .replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"," ");}
    private static String timestamp(){
        SimpleDateFormat f=new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date())+" GMT+0000 (Coordinated Universal Time)";
    }
    private void stopWatchdog(){if(watchdog!=null){watchdog.cancel(false);watchdog=null;}}
    private void finish(String problem){
        WebSocket old;byte[] bytes;List<EdgeWordTimings.Boundary> marks;int count;long duration;
        synchronized(this){
            if(finished)return;
            finished=true;stopWatchdog();old=socket;socket=null;
            if(problem==null&&(frames==0||audio.size()<1500))problem="Edge не прислал MP3";
            if(problem==null)EdgeSpeechQueue.success();
            EdgeSpeechQueue.release(ticket);ticket=null;
            bytes=problem==null?audio.toByteArray():null;
            marks=new ArrayList<>(words);count=frames;
            duration=SystemClock.elapsedRealtime()-started;
        }
        if(old!=null)old.close(1000,"complete");
        Log.i("EdgeSpeech", "total="+duration+"ms firstAudio="+
            (firstAudio==0?-1:firstAudio-started)+"ms bytes="+(bytes==null?0:bytes.length)+
            (problem==null?"":" error="+problem));
        try{callback.finished(bytes,count,problem,marks);}
        catch(Exception e){Log.e("EdgeSpeech","TTS completion callback failed",e);}
    }
    private void open(){
        synchronized(this){
            if(finished)return;
            started=SystemClock.elapsedRealtime();lastFrame=started;
        }
        if(text.trim().isEmpty()){finish("Пустой фрагмент");return;}
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_INPUT_UTF8){
            finish("Текст превышает 4096 UTF-8 байт");return;
        }
        // One request per selected/prefetched 600-char paragraph; never retry a
        // rejected handshake with another endpoint or a different client token.
        String query="?Ocp-Apim-Subscription-Key="+EdgeToken.TOKEN+
            "&Sec-MS-GEC="+EdgeToken.generate(System.currentTimeMillis()/1000L)+
            "&Sec-MS-GEC-Version="+EdgeToken.VERSION+"&ConnectionId="+id();
        Request request=new Request.Builder().url(URL+query).header("Origin",ORIGIN)
            .header("User-Agent",USER_AGENT).header("Pragma","no-cache")
            .header("Cache-Control","no-cache").header("Accept-Language","en-US,en;q=0.9")
            .header("Cookie","MUID="+id().toUpperCase(Locale.ROOT)).build();
        synchronized(this){
            if(finished)return;
            watchdog=TIMER.scheduleAtFixedRate(()->{
                String issue=null;
                synchronized(EdgeSpeechEngine.this){
                    if(finished)return;
                    long now=SystemClock.elapsedRealtime();
                    if(firstAudio==0&&now-started>18000)issue="Тайм-аут первого аудиопакета (18 с)";
                    else if(firstAudio>0&&now-lastFrame>25000)issue="Тайм-аут передачи MP3 (25 с)";
                }
                if(issue!=null){WebSocket ws; synchronized(EdgeSpeechEngine.this){ws=socket;}
                    finish(issue);if(ws!=null)ws.cancel();}
            },2,2,TimeUnit.SECONDS);
        }
        try{
            WebSocket connected=CLIENT.newWebSocket(request,new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){
                    synchronized(EdgeSpeechEngine.this){if(finished){ws.cancel();return;}}
                    String config="X-Timestamp:"+timestamp()+"\r\nContent-Type:application/json; charset=utf-8\r\n"
                        +"Path:speech.config\r\n\r\n{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                        +"\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},"
                        +"\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
                    String ssml="<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>"
                        +"<voice name='"+escaped(voice)+"'><prosody pitch='+0Hz' rate='"+rate
                        +"' volume='+0%'>"+escaped(text)+"</prosody></voice></speak>";
                    if(!ws.send(config)||!ws.send("X-RequestId:"+id()+"\r\nContent-Type:application/ssml+xml\r\n"
                        +"X-Timestamp:"+timestamp()+"Z\r\nPath:ssml\r\n\r\n"+ssml))
                        finish("Не удалось отправить запрос синтеза");
                }
                @Override public void onMessage(WebSocket ws,String message){
                    synchronized(EdgeSpeechEngine.this){if(finished)return;}
                    int boundary=message.indexOf("\r\n\r\n");
                    String header=boundary<0?message:message.substring(0,boundary);
                    if(header.contains("Path:audio.metadata")&&boundary>=0){
                        synchronized(EdgeSpeechEngine.this){if(!finished)
                            EdgeWordTimings.parseMetadata(message.substring(boundary+4),words);}
                    }else if(header.contains("Path:turn.end"))finish(null);
                }
                @Override public void onMessage(WebSocket ws,ByteString packet){
                    byte[] data=packet.toByteArray();
                    if(data.length<2)return;
                    int h=((data[0]&255)<<8)|(data[1]&255);
                    if(h<=0||h+2>data.length){finish("Повреждённый пакет Edge");return;}
                    String header=new String(data,2,h,StandardCharsets.US_ASCII);
                    if(!header.contains("Path:audio"))return;
                    if(!header.contains("Content-Type:audio/mpeg")){finish("Неизвестный аудиоформат");return;}
                    int offset=h+2,length=data.length-offset;
                    if(length<=0)return;
                    synchronized(EdgeSpeechEngine.this){
                        if(finished)return;
                        if(audio.size()+length>MAX_AUDIO){finish("MP3 превысил 12 МБ");return;}
                        audio.write(data,offset,length);frames++;
                        lastFrame=SystemClock.elapsedRealtime();
                        if(firstAudio==0)firstAudio=lastFrame;
                    }
                    if((frames&7)==0)callback.progress(frames,audio.size());
                }
                @Override public void onFailure(WebSocket ws,Throwable e,Response response){
                    synchronized(EdgeSpeechEngine.this){if(finished)return;}
                    int status=response==null?0:response.code();
                    if(status==429){EdgeSpeechQueue.limited(response);finish("HTTP 429: Edge временно ограничил запросы");}
                    else finish(status>0?"Edge HTTP "+status+": отказ сервера":"Ошибка соединения Edge: "+e);
                }
                @Override public void onClosed(WebSocket ws,int code,String reason){
                    finish("Edge закрыл соединение до завершения: "+code);
                }
            });
            synchronized(this){if(finished)connected.cancel();else socket=connected;}
        }catch(Exception e){finish("Ошибка подключения Edge: "+e);}
    }
}
