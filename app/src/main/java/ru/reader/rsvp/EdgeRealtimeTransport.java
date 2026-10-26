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
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** Streaming-first Edge protocol transport, compatible with rany2/edge-tts 7.3.x.
 * One request = one WebSocket turn. MP3 and WordBoundary are dispatched immediately
 * from callbacks. A shared OkHttp pool reuses TLS/TCP when the remote peer allows it.
 * A 429 is an explicit server rejection and is never bypassed by endpoint switching.
 */
final class EdgeRealtimeTransport {
    private static final String PRIMARY="wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1";
    private static final String LEGACY="wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String ORIGIN="chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        +"(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.4283.45";
    private static final long MAX_MP3_BYTES=12L*1024*1024;
    private static final AtomicLong SERVER_CLOCK_OFFSET_MS=new AtomicLong();
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectionPool(new ConnectionPool(6,5,TimeUnit.MINUTES))
        .connectTimeout(10,TimeUnit.SECONDS)
        .readTimeout(0,TimeUnit.MILLISECONDS)
        .pingInterval(20,TimeUnit.SECONDS)
        .build();
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeRealtimeWatchdog");t.setDaemon(true);return t;
    });
    private final String text,voice,rate;
    private final int priority;
    private final EdgeTtsClient.Callback callback;
    private final ArrayList<EdgeWordTimings.Boundary> metadata=new ArrayList<>();
    private final ByteArrayOutputStream recording=new ByteArrayOutputStream();
    private EdgeRequestGate.Ticket ticket;
    private ScheduledFuture<?> watcher;
    private WebSocket connection;
    private boolean done;
    private int attempts,audioFrames;
    private long bytes,sessionStart,connectionOpened,firstAudio,latestAudio;
    private boolean deliveredAudio;
    private String endpoint=PRIMARY;

    EdgeRealtimeTransport(String text,String voice,String rate,int priority,EdgeTtsClient.Callback callback){
        this.text=text==null?"":text;
        this.voice=voice==null?"ru-RU-SvetlanaNeural":voice;
        this.rate=rate!=null&&rate.matches("[+-]\\d{1,3}%")?rate:"+0%";
        this.priority=priority;this.callback=callback;
    }
    static String requestRate(String rate){return rate!=null&&rate.matches("[+-]\\d{1,3}%")?rate:"+0%";}
    void begin(){synchronized(this){if(done)return;ticket=EdgeRequestGate.enqueue(priority,this::connect);}}
    void cancel(){
        WebSocket toCancel;
        synchronized(this){done=true;clearWatchdog();EdgeRequestGate.release(ticket);ticket=null;toCancel=connection;}
        if(toCancel!=null)toCancel.cancel();
    }
    private static String uid(){return UUID.randomUUID().toString().replace("-","");}
    private static String date(){
        SimpleDateFormat f=new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(System.currentTimeMillis()+SERVER_CLOCK_OFFSET_MS.get()))+
            " GMT+0000 (Coordinated Universal Time)";
    }
    private static String escaped(String value){return value.replaceAll("[\\x00-\\x1F]"," ")
        .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        .replace("\"","&quot;").replace("'","&apos;");}
    private void clearWatchdog(){if(watcher!=null){watcher.cancel(false);watcher=null;}}
    private void complete(String error){
        WebSocket old;byte[] mp3;int chunks;List<EdgeWordTimings.Boundary> words;
        synchronized(this){
            if(done)return;done=true;clearWatchdog();
            old=connection;EdgeRequestGate.release(ticket);ticket=null;
            if(error==null&&(bytes<1500||audioFrames==0))error="Edge не прислал аудиоданные";
            if(error==null)EdgeRequestGate.success();
            chunks=audioFrames;words=new ArrayList<>(metadata);
            mp3=error==null&&!callback.streamOnly()?recording.toByteArray():null;
            Log.i("EdgeTtsLatency","connect="+(connectionOpened>0?connectionOpened-sessionStart:-1)+
                "ms; firstAudio="+(firstAudio>0?firstAudio-sessionStart:-1)+
                "ms; done="+(SystemClock.elapsedRealtime()-sessionStart)+"ms; bytes="+bytes+
                (error==null?"":"; error="+error));
        }
        if(old!=null)old.close(1000,"done");
        try{callback.finished(mp3,chunks,error,words);}
        catch(Exception ex){Log.e("EdgeRealtimeTransport","Completion callback failed",ex);}
    }
    private void connect(){
        final int generation;
        final String wsEndpoint;
        synchronized(this){
            if(done)return;
            attempts++;generation=attempts;
            wsEndpoint=endpoint;
            sessionStart=SystemClock.elapsedRealtime();
            latestAudio=sessionStart;connectionOpened=0;
            clearWatchdog();
            watcher=WATCHDOG.scheduleAtFixedRate(()->{
                String problem=null;
                WebSocket late=null;
                synchronized(EdgeRealtimeTransport.this){
                    if(done||generation!=attempts)return;
                    long now=SystemClock.elapsedRealtime();
                    if(firstAudio==0&&now-sessionStart>18000){
                        problem="Тайм-аут ожидания первого аудиопакета (18 с)";
                    }else if(firstAudio>0&&now-latestAudio>25000){
                        problem="Сервер перестал передавать звук (25 с)";
                    }
                    if(problem!=null)late=connection;
                }
                if(problem!=null){complete(problem);if(late!=null)late.cancel();}
            },2,2,TimeUnit.SECONDS);
        }
        try{
            String parameter=PRIMARY.equals(wsEndpoint)?"Ocp-Apim-Subscription-Key":"TrustedClientToken";
            String url=wsEndpoint+"?"+parameter+"="+EdgeToken.TOKEN+"&ConnectionId="+uid()+
                "&Sec-MS-GEC="+EdgeToken.generate((System.currentTimeMillis()+SERVER_CLOCK_OFFSET_MS.get())/1000L)+
                "&Sec-MS-GEC-Version="+EdgeToken.VERSION;
            Request request=new Request.Builder().url(url).header("Origin",ORIGIN)
                .header("User-Agent",UA).header("Pragma","no-cache")
                .header("Cache-Control","no-cache").header("Accept-Language","en-US,en;q=0.9")
                .header("Cookie","muid="+uid().toUpperCase(Locale.ROOT)+";").build();
            connection=CLIENT.newWebSocket(request,new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){
                    synchronized(EdgeRealtimeTransport.this){if(done||generation!=attempts){ws.cancel();return;}}
                    connectionOpened=SystemClock.elapsedRealtime();latestAudio=connectionOpened;
                    String now=date();
                    String config="X-Timestamp:"+now+"\r\nContent-Type:application/json; charset=utf-8\r\n"
                        +"Path:speech.config\r\n\r\n"
                        +"{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                        +"\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},"
                        +"\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
                    String ssml="<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>"
                        +"<voice name='"+escaped(voice)+"'><prosody pitch='+0Hz' rate='"+rate+"' volume='+0%'>"
                        +escaped(text)+"</prosody></voice></speak>";
                    if(!ws.send(config)||!ws.send("X-RequestId:"+uid()+"\r\nContent-Type:application/ssml+xml\r\n"
                        +"X-Timestamp:"+date()+"Z\r\nPath:ssml\r\n\r\n"+ssml)){
                        complete("Не удалось отправить запрос синтеза");
                    }
                }
                @Override public void onMessage(WebSocket ws,String message){
                    synchronized(EdgeRealtimeTransport.this){if(done||generation!=attempts)return;}
                    int split=message.indexOf("\r\n\r\n");
                    String header=split>=0?message.substring(0,split):message;
                    if(header.contains("Path:audio.metadata")&&split>=0){
                        List<EdgeWordTimings.Boundary> snapshot;
                        synchronized(EdgeRealtimeTransport.this){
                            if(done)return;
                            EdgeWordTimings.parseMetadata(message.substring(split+4),metadata);
                            snapshot=new ArrayList<>(metadata);
                        }
                        try{callback.words(snapshot);}
                        catch(Exception ex){complete("Не удалось обработать WordBoundary: "+ex.getMessage());}
                    }else if(header.contains("Path:turn.end"))complete(null);
                }
                @Override public void onMessage(WebSocket ws,ByteString data){
                    byte[] packet=data.toByteArray();
                    if(packet.length<2)return;
                    int size=((packet[0]&255)<<8)|(packet[1]&255);
                    if(size<1||size+2>packet.length){complete("Некорректная рамка аудиопотока");return;}
                    String header=new String(packet,2,size,StandardCharsets.US_ASCII);
                    if(!header.contains("Path:audio"))return;
                    int start=size+2,count=packet.length-start;
                    if(count<=0)return;
                    if(!header.contains("Content-Type:audio/mpeg")){
                        complete("Сервер вернул неизвестный аудиоформат");return;
                    }
                    int frames;long total;
                    synchronized(EdgeRealtimeTransport.this){
                        if(done||generation!=attempts)return;
                        if(bytes+count>MAX_MP3_BYTES){complete("Лимит MP3-фрагмента: 12 МБ");return;}
                        if(!callback.streamOnly())recording.write(packet,start,count);
                        bytes+=count;audioFrames++;frames=audioFrames;total=bytes;
                        latestAudio=SystemClock.elapsedRealtime();
                        if(firstAudio==0)firstAudio=latestAudio;
                    }
                    // Never perform disk I/O or UI work while holding the transport monitor.
                    try{
                        callback.audio(packet,start,count);
                        if(frames==1 || (frames%8)==0)callback.progress(frames,total);
                    }catch(Exception ex){complete("Ошибка обработки потока аудио: "+ex.getMessage());}
                }
                @Override public void onFailure(WebSocket ws,Throwable e,Response response){
                    int status=response==null?0:response.code();
                    synchronized(EdgeRealtimeTransport.this){if(done||generation!=attempts)return;}
                    if(status==429){
                        EdgeRequestGate.limited(response);
                        complete("HTTP 429: сервер ограничил запросы. Повтор позже.");return;
                    }
                    // Match edge-tts behavior on clock skew; one reconnect only and
                    // NEVER for an explicit rate limit. Do not race the previous socket.
                    if(status==403&&attempts==1&&response!=null){
                        long offset=clockOffset(response);
                        if(Math.abs(offset-SERVER_CLOCK_OFFSET_MS.get())>60000){
                            SERVER_CLOCK_OFFSET_MS.set(offset);
                            retryOnNewConnection(ws,PRIMARY);return;
                        }
                    }
                    if(status==0&&attempts==1&&firstAudio==0){
                        retryOnNewConnection(ws,LEGACY);return;
                    }
                    complete("Edge WebSocket "+(status>0?"HTTP "+status+": ":"ошибка сети: ")+
                        (e==null?"нет ответа":e.getMessage()));
                }
                @Override public void onClosed(WebSocket ws,int code,String reason){
                    synchronized(EdgeRealtimeTransport.this){if(done||generation!=attempts)return;}
                    complete("Сервер завершил соединение до конца синтеза: "+code);
                }
            });
        }catch(Exception ex){complete("Ошибка подключения к Edge: "+ex.getMessage());}
    }
    private long clockOffset(Response response){
        try{String date=response.header("Date");
            if(date==null)return SERVER_CLOCK_OFFSET_MS.get();
            SimpleDateFormat f=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("GMT"));Date parsed=f.parse(date);
            if(parsed!=null)return parsed.getTime()-System.currentTimeMillis();
        }catch(Exception ignored){}
        return SERVER_CLOCK_OFFSET_MS.get();
    }
    private void retryOnNewConnection(WebSocket old,String newEndpoint){
        synchronized(this){
            if(done||attempts>=2)return;
            clearWatchdog();
            endpoint=newEndpoint;
            if(old!=null)old.cancel();
            EdgeRequestGate.release(ticket);ticket=null;
        }
        // A fresh ticket preserves priority. Gate prevents retry during a cooldown.
        WATCHDOG.schedule(this::begin,400,TimeUnit.MILLISECONDS);
    }
}
