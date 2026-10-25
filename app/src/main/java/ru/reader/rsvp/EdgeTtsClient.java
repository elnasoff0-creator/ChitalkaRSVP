package ru.reader.rsvp;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import android.os.SystemClock;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/** Experimental direct WebSocket client for unofficial Edge Read Aloud synthesis.
 * A new session is opened per text fragment. No server, WebView or API key required.
 * The service may rate limit/change its protocol without warning.
 */
final class EdgeTtsClient {
    interface Callback {
        void progress(int chunks,long bytes);
        default boolean streamOnly(){return false;}
        default void audio(byte[] data,int start,int count){}
        default void words(List<EdgeWordTimings.Boundary> boundaries){}
        void finished(byte[] mp3,int chunks,String error);
        default void finished(byte[] mp3,int chunks,String error,List<EdgeWordTimings.Boundary> boundaries){
            finished(mp3,chunks,error);
        }
    }
    private static final int LIMIT_BYTES=12*1024*1024;
    private static final String ENDPOINT="wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        +"(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.4283.45";
    private static final OkHttpClient HTTP=new OkHttpClient.Builder()
        .connectTimeout(18,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS)
        .build();
    private static final ScheduledExecutorService WATCHDOG=Executors.newSingleThreadScheduledExecutor(r->{
        Thread t=new Thread(r,"EdgeTtsWatchdog");t.setDaemon(true);return t;
    });
    private ScheduledFuture<?> timeout;
    private long lastPacketAt;
    private WebSocket socket;
    private boolean completed;
    private final Callback callback;
    private final ByteArrayOutputStream received=new ByteArrayOutputStream();
    private int chunks;
    private int connectAttempts;
    private volatile boolean retryScheduled;
    private long totalBytes;
    private long startedAt,openedAt,firstAudioAt;
    private final ArrayList<EdgeWordTimings.Boundary> boundaries=new ArrayList<>();

    private EdgeTtsClient(Callback cb){callback=cb;}
    static EdgeTtsClient synthesize(String text,String voice,String rate,Callback cb){
        EdgeTtsClient client=new EdgeTtsClient(cb);
        client.connect(text,voice,rate);
        return client;
    }
    private static String uuid(){return UUID.randomUUID().toString().replace("-","");}
    private static String date(){SimpleDateFormat df=new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss",Locale.US);
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        return df.format(new Date())+" GMT+0000 (Coordinated Universal Time)";}
    private static String xml(String value){return value.replaceAll("[\\x00-\\x1F]"," ")
        .replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
        .replace("\"","&quot;").replace("'","&apos;");}

    private synchronized void finish(String error){
        if(completed)return;completed=true;
        if(timeout!=null)timeout.cancel(false);
        byte[] bytes=callback.streamOnly()?null:received.toByteArray();
        if(error==null && (totalBytes<1500 || chunks==0))error="Сервис не вернул аудиоданные";
        long finishedAt=SystemClock.elapsedRealtime();
        Log.i("EdgeTtsLatency","websocket="+(openedAt>0?openedAt-startedAt:-1)+
            "ms firstAudio="+(firstAudioAt>0?firstAudioAt-startedAt:-1)+
            "ms finished="+(finishedAt-startedAt)+"ms; bytes="+totalBytes+
            (error==null?"":"; network error="+error));
        callback.finished(error==null?bytes:null,chunks,error,new ArrayList<>(boundaries));
        if(socket!=null)socket.close(1000,"completed");
    }
    void cancel(){synchronized(this){completed=true;if(timeout!=null)timeout.cancel(false);}
        if(socket!=null)socket.cancel();}
    private void connect(String text,String voice,String rate){
        connectAttempts++;
        retryScheduled=false;
        if(connectAttempts==1)startedAt=SystemClock.elapsedRealtime();
        lastPacketAt=SystemClock.elapsedRealtime();
        timeout=WATCHDOG.scheduleAtFixedRate(()->{
            synchronized(EdgeTtsClient.this){
                if(completed)return;
                long now=SystemClock.elapsedRealtime();
                if(firstAudioAt==0&&now-startedAt>20000){
                    finish("Edge TTS не прислал аудио за 20 секунд");
                    if(socket!=null)socket.cancel();
                }else if(firstAudioAt>0&&now-lastPacketAt>25000){
                    finish("Edge TTS остановил передачу аудио");
                    if(socket!=null)socket.cancel();
                }
            }
        },3,3,TimeUnit.SECONDS);
        try{
            String uid=uuid();
            boolean alternateEndpoint=connectAttempts>1;
            String base=alternateEndpoint
                ?"wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1"
                :ENDPOINT;
            String parameter=alternateEndpoint?"Ocp-Apim-Subscription-Key":"TrustedClientToken";
            String url=base+"?"+parameter+"="+EdgeToken.TOKEN
                +"&ConnectionId="+uid+"&Sec-MS-GEC="+EdgeToken.gec()+"&Sec-MS-GEC-Version="+EdgeToken.VERSION;
            Request req=new Request.Builder().url(url).header("Origin","chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Pragma","no-cache").header("Cache-Control","no-cache")
                .header("User-Agent",UA).header("Accept-Language","en-US,en;q=0.9")
                .header("Cookie","muid="+uuid().toUpperCase(Locale.ROOT)+";").build();
            socket=HTTP.newWebSocket(req,new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){
                    synchronized(EdgeTtsClient.this){if(completed){ws.cancel();return;}}
                    openedAt=SystemClock.elapsedRealtime();lastPacketAt=openedAt;
                    String config="X-Timestamp:"+date()+"\r\nContent-Type:application/json; charset=utf-8\r\n"
                        +"Path:speech.config\r\n\r\n"
                        +"{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},"
                        +"\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
                    ws.send(config);
                    String ssml="<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>"
                        +"<voice name='"+xml(voice)+"'><prosody rate='+0%' pitch='+0Hz' volume='+0%'>"
                        +xml(text)+"</prosody></voice></speak>";
                    ws.send("X-RequestId:"+uuid()+"\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:"+date()
                        +"Z\r\nPath:ssml\r\n\r\n"+ssml);
                }
                @Override public void onMessage(WebSocket ws,String body){
                    String[] parts=body.split("\\r\\n\\r\\n",2);
                    String header=parts[0];
                    if(header.contains("Path:audio.metadata") && parts.length==2){
                        synchronized(EdgeTtsClient.this){
                            if(!completed){
                                EdgeWordTimings.parseMetadata(parts[1],boundaries);
                                callback.words(new ArrayList<>(boundaries));
                            }
                        }
                    }else if(header.contains("Path:turn.end"))finish(null);
                }
                @Override public void onMessage(WebSocket ws,ByteString bytes){
                    byte[] packet=bytes.toByteArray();
                    if(packet.length<3){finish("Пустой бинарный пакет");return;}
                    int head=((packet[0]&255)<<8)|(packet[1]&255);
                    if(head<1 || head+2>packet.length){finish("Неверный заголовок Edge");return;}
                    String metadata=new String(packet,2,head,StandardCharsets.US_ASCII);
                    if(!metadata.contains("Path:audio"))return;
                    if(!metadata.contains("Content-Type:audio/mpeg") && packet.length>head+2){finish("Неожиданный формат ответа Edge");return;}
                    int count=packet.length-head-2;
                    if(count<=0)return;
                    synchronized(EdgeTtsClient.this){
                        if(completed)return;
                        if(totalBytes+count>LIMIT_BYTES){finish("Аудиофрагмент превышает 12 МБ");return;}
                        lastPacketAt=SystemClock.elapsedRealtime();
                        if(firstAudioAt==0)firstAudioAt=lastPacketAt;
                        if(!callback.streamOnly())received.write(packet,head+2,count);
                        totalBytes+=count;chunks++;
                        callback.audio(packet,head+2,count);
                        callback.progress(chunks,totalBytes);
                    }
                }
                @Override public void onFailure(WebSocket ws,Throwable error,Response response){
                    String suffix=response==null?"":" HTTP "+response.code();
                    // A transient reset before any audio must not poison the live Media3 source.
                    // Retry once with fresh ConnectionId; keep the same callback/live buffer.
                    synchronized(EdgeTtsClient.this){
                        // Ignore the old WebSocket's late failure after a retry
                        // has already replaced it. Otherwise it can abort a
                        // healthy replacement connection before first audio.
                        if(ws!=socket)return;
                        if(!completed && connectAttempts<2 && firstAudioAt==0 &&
                                (response==null || response.code()==403 || response.code()>=500)){
                            retryScheduled=true;
                            WATCHDOG.schedule(()->{
                                synchronized(EdgeTtsClient.this){
                                    if(completed)return;
                                    if(timeout!=null)timeout.cancel(false);
                                    connect(text,voice,rate);
                                }
                            },350,TimeUnit.MILLISECONDS);
                            return;
                        }
                    }
                    finish("Соединение Edge TTS"+suffix+": "+(error==null?"неизвестная ошибка":error.getMessage()));
                }
                @Override public void onClosed(WebSocket ws,int code,String reason){
                    synchronized(EdgeTtsClient.this){
                        if(ws!=socket)return;
                        if(!completed&&!retryScheduled)finish("Edge закрыл соединение до завершения: "+code);
                    }
                }
            });
        }catch(Exception ex){finish("Не удалось подключиться к Edge: "+ex.getMessage());}
    }
}
