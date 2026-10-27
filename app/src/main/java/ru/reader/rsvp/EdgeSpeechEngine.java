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

/** Live MP3 bytes are delivered to the Media3 buffer as WebSocket frames arrive.
 * A complete validated MP3 is also committed to disk after turn.end.
 * Edge Read Aloud is unofficial: HTTP 429 can still be enforced remotely.
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
    private boolean finished,canceled,retriedAfter403,connecting;
    // The queue ticket belongs to the socket until OkHttp confirms it has ended.
    // Otherwise cancellation can open a second WebSocket before the first closes.
    private WebSocket closingSocket;
    private ScheduledFuture<?> closeFallback;
    private long started,handshakeStarted,firstAudio,lastFrame;
    private int frames,connectionAttempt;
    EdgeSpeechEngine(String value,String voice,String rate,int priority,EdgeTtsClient.Callback callback){
        this.text=value==null?"":value;
        this.voice=voice==null?"ru-RU-SvetlanaNeural":voice;
        this.rate=rate!=null&&rate.matches("[+-]\\d{1,3}%")?rate:"+0%";
        this.priority=priority;this.callback=callback;
    }
    void start(){
        synchronized(this){if(finished||canceled)return;ticket=EdgeSpeechQueue.submit(priority,this::open,this::preempt);}
    }
    void promoteToPlay(){
        EdgeSpeechQueue.Ticket pending;
        synchronized(this){if(finished||canceled)return;pending=ticket;}
        EdgeSpeechQueue.promote(pending);
    }
    void cancel(){
        WebSocket old;boolean opening;
        synchronized(this){
            if(finished)return;
            finished=true;canceled=true;stopWatchdog();old=socket;socket=null;
            opening=connecting;
        }
        if(old!=null)endSocket(old,true);
        else if(!opening)releaseTicketAfterClose();
        // If newWebSocket() is still returning, connect() cancels and closes it.
    }
    private void releaseTicketAfterClose(){
        EdgeSpeechQueue.Ticket held;
        synchronized(this){
            if(closeFallback!=null){closeFallback.cancel(false);closeFallback=null;}
            closingSocket=null;
            held=ticket;ticket=null;
        }
        if(held!=null)EdgeSpeechQueue.release(held);
    }
    private void socketTerminated(WebSocket terminated){
        boolean match;
        synchronized(this){match=terminated!=null && terminated==closingSocket;}
        if(match)releaseTicketAfterClose();
    }
    /** A closed/canceled socket must acknowledge termination before the next
     * queue ticket can run. The fallback force-cancels a broken close handshake. */
    private void endSocket(WebSocket old,boolean forceCancel){
        if(old==null){releaseTicketAfterClose();return;}
        synchronized(this){closingSocket=old;}
        try{
            if(forceCancel || !old.close(1000,"complete"))old.cancel();
        }catch(Exception ex){old.cancel();}
        synchronized(this){
            if(closingSocket==old){
                closeFallback=TIMER.schedule(()->{
                    Log.w("EdgeSpeech","WebSocket close acknowledgement timed out; forcing cancel");
                    old.cancel();
                    socketTerminated(old);
                },4,TimeUnit.SECONDS);
            }
        }
    }
    /** A foreground tap can preempt speculative/bulk work. Unlike user cancel,
     * this completes the background callback so whole-book workers do not wait
     * for their long timeout holding a generation lock. */
    private void preempt(){
        WebSocket existing;
        synchronized(this){if(finished)return;existing=socket;}
        finish("Edge TTS: запрос прерван ради активного чтения");
        if(existing!=null)existing.cancel();
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
            bytes=problem==null?audio.toByteArray():null;
            marks=new ArrayList<>(words);count=frames;
            duration=SystemClock.elapsedRealtime()-started;
        }
        if(old!=null)endSocket(old,false);
        else if(!connecting)releaseTicketAfterClose();
        Log.i("EdgeSpeech", "total="+duration+"ms firstAudio="+
            (firstAudio==0?-1:firstAudio-started)+"ms bytes="+(bytes==null?0:bytes.length)+
            (problem==null?"":" error="+problem));
        try{callback.finished(bytes,count,problem,marks);}
        catch(Exception e){Log.e("EdgeSpeech","TTS completion callback failed",e);}
    }
    private void open(){
        synchronized(this){
            if(finished)return;
            started=SystemClock.elapsedRealtime();handshakeStarted=started;lastFrame=started;
        }
        if(text.trim().isEmpty()){finish("Пустой фрагмент");return;}
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_INPUT_UTF8){
            finish("Текст превышает 4096 UTF-8 байт");return;
        }
        synchronized(this){
            if(finished)return;
            watchdog=TIMER.scheduleAtFixedRate(()->{
                String issue=null;
                synchronized(EdgeSpeechEngine.this){
                    if(finished)return;
                    long now=SystemClock.elapsedRealtime();
                    if(firstAudio==0&&now-handshakeStarted>18000)issue="Тайм-аут первого аудиопакета (18 с)";
                    else if(firstAudio>0&&now-lastFrame>25000)issue="Тайм-аут передачи MP3 (25 с)";
                }
                if(issue!=null){WebSocket ws; synchronized(EdgeSpeechEngine.this){ws=socket;}
                    finish(issue);if(ws!=null)ws.cancel();}
            },2,2,TimeUnit.SECONDS);
        }
        connect();
    }
    /** Retry at most one failed 403 handshake, using a server-date-corrected GEC. */
    private void connect(){
        final int currentAttempt;
        synchronized(this){
            if(finished||canceled)return;
            currentAttempt=++connectionAttempt;
            connecting=true;
            handshakeStarted=SystemClock.elapsedRealtime();
        }
        final String attemptGec=EdgeToken.generate(EdgeServerClock.adjustedEpochSeconds());
        String query="?Ocp-Apim-Subscription-Key="+EdgeToken.TOKEN+
            "&Sec-MS-GEC="+attemptGec+
            "&Sec-MS-GEC-Version="+EdgeToken.VERSION+"&ConnectionId="+id();
        Request request=new Request.Builder().url(URL+query).header("Origin",ORIGIN)
            .header("User-Agent",USER_AGENT).header("Pragma","no-cache")
            .header("Cache-Control","no-cache").header("Accept-Language","en-US,en;q=0.9")
            .header("Cookie","MUID="+id().toUpperCase(Locale.ROOT)).build();
        try{
            WebSocket connected=CLIENT.newWebSocket(request,new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){
                    synchronized(EdgeSpeechEngine.this){
                        if(finished||currentAttempt!=connectionAttempt){ws.cancel();return;}
                    }
                    callback.connected();
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
                    String path=EdgeWsFrames.textPath(message);
                    if("audio.metadata".equals(path)){
                        String body=EdgeWsFrames.textBody(message);
                        if(!body.isEmpty()){
                            List<EdgeWordTimings.Boundary> snapshot;
                            synchronized(EdgeSpeechEngine.this){
                                if(finished)return;
                                EdgeWordTimings.parseMetadata(body,words);
                                snapshot=new ArrayList<>(words);
                            }
                            callback.metadata(snapshot);
                        }
                    }else if("turn.end".equals(path))finish(null);
                }
                @Override public void onMessage(WebSocket ws,ByteString packet){
                    byte[] data=packet.toByteArray();
                    EdgeWsFrames.Frame frame;
                    try{frame=EdgeWsFrames.audio(data);}
                    catch(java.io.IOException problem){
                        Log.w("EdgeSpeech","Invalid Edge binary envelope: "+problem.getMessage());
                        finish(problem.getMessage());return;
                    }
                    if(frame.terminal){
                        // Normal Edge end-of-audio marker. Wait for Path:turn.end before commit.
                        Log.d("EdgeSpeech","Received empty audio terminal frame");
                        return;
                    }
                    boolean tooLarge;
                    synchronized(EdgeSpeechEngine.this){
                        if(finished)return;
                        tooLarge=audio.size()+frame.dataLength>MAX_AUDIO;
                        if(!tooLarge){
                            audio.write(data,frame.dataOffset,frame.dataLength);
                            frames++;
                            lastFrame=SystemClock.elapsedRealtime();
                            if(firstAudio==0)firstAudio=lastFrame;
                        }
                    }
                    if(tooLarge){finish("MP3 превысил 12 МБ");return;}
                    try{callback.audio(data,frame.dataOffset,frame.dataLength);}catch(Exception e){finish("Ошибка передачи MP3 в плеер: "+e);return;}
                    if((frames&7)==0)callback.progress(frames,audio.size());
                }
                @Override public void onFailure(WebSocket ws,Throwable e,Response response){
                    synchronized(EdgeSpeechEngine.this){
                        if(finished||currentAttempt!=connectionAttempt){
                            socketTerminated(ws);return;
                        }
                    }
                    int status=response==null?0:response.code();
                    if(status==403&&response!=null){
                        boolean retry=false;
                        synchronized(EdgeSpeechEngine.this){
                            if(!finished&&!canceled&&!retriedAfter403&&currentAttempt==connectionAttempt){
                                String serverDate=response.header("Date");
                                if(EdgeServerClock.correctFor403(serverDate,attemptGec,System.currentTimeMillis())){
                                    retriedAfter403=true;
                                    socket=null;
                                    retry=true;
                                }
                            }
                        }
                        if(retry){
                            Log.i("EdgeSpeech","HTTP 403: clock corrected, retrying handshake once");
                            connect();
                            return;
                        }
                    }
                    if(status==429){EdgeSpeechQueue.limited(response);finish("HTTP 429: Edge временно ограничил запросы");}
                    else finish(status>0?"Edge HTTP "+status+": отказ сервера":"Ошибка соединения Edge: "+e);
                    socketTerminated(ws); // OkHttp confirms failed WebSocket is gone.
                }
                @Override public void onClosed(WebSocket ws,int code,String reason){
                    synchronized(EdgeSpeechEngine.this){
                        if(currentAttempt!=connectionAttempt)return;
                    }
                    finish("Edge закрыл соединение до завершения: "+code);
                    socketTerminated(ws);
                }
            });
            boolean terminate;
            synchronized(this){
                connecting=false;
                terminate=finished||currentAttempt!=connectionAttempt;
                if(!terminate)socket=connected;
            }
            if(terminate){
                if(finished)endSocket(connected,true);
                else connected.cancel(); // stale 403 attempt already failed
            }
        }catch(Exception e){
            synchronized(this){connecting=false;}
            finish("Ошибка подключения Edge: "+e);
            if(finished && closingSocket==null)releaseTicketAfterClose();
        }
    }
}
