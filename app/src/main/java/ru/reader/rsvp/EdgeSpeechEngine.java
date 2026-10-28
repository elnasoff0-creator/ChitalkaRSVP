package ru.reader.rsvp;

import android.os.SystemClock;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import okhttp3.Request;
import okhttp3.Response;
import okio.ByteString;

/** Streams each Edge MP3 frame into Media3, then commits complete audio at turn.end.
 * WebSocket lease persists across turns in EdgeWebSocketPool; only the currently
 * assigned engine may send SSML or receive metadata on its lease.
 */
final class EdgeSpeechEngine {
    private static final String URL="wss://api.msedgeservices.com/tts/cognitiveservices/websocket/v1";
    private static final String USER_AGENT="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0";
    private static final String ORIGIN="chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold";
    private static final int MAX_INPUT_UTF8=4096,MAX_AUDIO=12*1024*1024;
    private static final ScheduledExecutorService TIMER=Executors.newScheduledThreadPool(2,r->{
        Thread t=new Thread(r,"EdgeSpeechWatchdog");t.setDaemon(true);return t;
    });
    private final String text,voice,rate;
    private final int priority;
    private final EdgeTtsClient.Callback callback;
    private final List<EdgeWordTimings.Boundary> words=new ArrayList<>();
    private final ByteArrayOutputStream audio=new ByteArrayOutputStream();
    private EdgeSpeechQueue.Ticket ticket;
    private EdgeWebSocketPool.Lease lease,closingLease;
    private ScheduledFuture<?> watchdog,closeFallback;
    private boolean finished,canceled,retriedAfter403,reusedTurn,reusedRestarted;
    private long queuedAt,started,handshakeStarted,firstAudio,lastFrame,closeStartedAt;
    private int frames,connectionAttempt;
    private String lastGec="";
    EdgeSpeechEngine(String value,String voice,String rate,int priority,EdgeTtsClient.Callback callback){
        this.text=value==null?"":value;
        this.voice=voice==null?"ru-RU-SvetlanaNeural":voice;
        this.rate=rate!=null&&rate.matches("[+-]\\d{1,3}%")?rate:"+0%";
        this.priority=priority;this.callback=callback;
    }
    void start(){
        synchronized(this){if(finished||canceled)return;queuedAt=SystemClock.elapsedRealtime();
            ReaderDiagnosticLog.event("EDGE","request queued priority="+priority+" chars="+text.length());
            ticket=EdgeSpeechQueue.submit(priority,this::open,this::preempt);}
    }
    void promoteToPlay(){EdgeSpeechQueue.Ticket held;synchronized(this){held=ticket;}
        EdgeSpeechQueue.promote(held);}
    void cancel(){
        EdgeWebSocketPool.Lease held;
        synchronized(this){
            if(finished)return;
            finished=true;canceled=true;stopWatchdog();held=lease;lease=null;
        }
        ReaderDiagnosticLog.event("EDGE","request canceled");
        abort(held);
    }
    private void preempt(){finish("Edge TTS: запрос прерван ради активного чтения");}
    private static String id(){return UUID.randomUUID().toString().replace("-","");}
    private static String escaped(String s){return s.replace("&","&amp;").replace("<","&lt;")
        .replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;")
        .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"," ");}
    private static String timestamp(){
        SimpleDateFormat f=new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss",Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date())+" GMT+0000 (Coordinated Universal Time)";
    }
    private synchronized void stopWatchdog(){if(watchdog!=null){watchdog.cancel(false);watchdog=null;}}
    private void releaseTicketAfterClose(){
        EdgeSpeechQueue.Ticket held;
        synchronized(this){
            if(closeFallback!=null){closeFallback.cancel(false);closeFallback=null;}
            closingLease=null;held=ticket;ticket=null;
        }
        if(held!=null)EdgeSpeechQueue.release(held);
    }
    private void abort(EdgeWebSocketPool.Lease resource){
        if(resource==null){releaseTicketAfterClose();return;}
        synchronized(this){closingLease=resource;closeStartedAt=SystemClock.elapsedRealtime();}
        EdgeWebSocketPool.discard(resource,true);
        synchronized(this){
            if(closingLease!=resource)return;
            closeFallback=TIMER.schedule(()->{
                synchronized(EdgeSpeechEngine.this){if(closingLease!=resource)return;}
                Log.w("EdgeSpeech","cancel unacknowledged at 500ms; local ticket release");
                ReaderDiagnosticLog.event("WS","cancel close acknowledgement timeout_ms=500");
                socketTerminated(resource);
            },500,TimeUnit.MILLISECONDS);
        }
    }
    private void socketTerminated(EdgeWebSocketPool.Lease resource){
        boolean match;
        synchronized(this){match=resource!=null&&resource==closingLease;}
        if(match){Log.d("EdgeSpeech","socket-close-ack="+
            (SystemClock.elapsedRealtime()-closeStartedAt)+"ms");releaseTicketAfterClose();}
    }
    private void finish(String problem){
        EdgeWebSocketPool.Lease resource;
        byte[] bytes;List<EdgeWordTimings.Boundary> marks;int count;long elapsed;
        synchronized(this){
            if(finished)return;
            finished=true;stopWatchdog();resource=lease;lease=null;
            if(problem==null&&(frames==0||audio.size()<1500))problem="Edge не прислал MP3";
            if(problem==null)EdgeSpeechQueue.success();
            bytes=problem==null?audio.toByteArray():null;
            marks=new ArrayList<>(words);count=frames;
            elapsed=SystemClock.elapsedRealtime()-started;
        }
        ReaderDiagnosticLog.event("EDGE", "turn_complete result="+(problem==null?"ok":
            (problem.contains("429")?"http429":"failed"))+" queue_ms="+
            (started==0?-1:started-queuedAt)+" first_mp3_ms="+
            (firstAudio==0?-1:firstAudio-started)+" total_ms="+elapsed+
            " frames="+count+" words="+marks.size());
        if(problem==null){
            // Critical: turn.end releases the lease WITHOUT closing the OPEN WebSocket.
            EdgeWebSocketPool.recycle(resource);
            releaseTicketAfterClose();
        }else abort(resource);
        Log.i("EdgeSpeech","queueWait="+(started==0?-1:started-queuedAt)+
            "ms total="+elapsed+"ms firstAudio="+(firstAudio==0?-1:firstAudio-started)+
            "ms frames="+frames+(problem==null?"":" error="+problem));
        try{callback.finished(bytes,count,problem,marks);}
        catch(Exception e){Log.e("EdgeSpeech","TTS completion callback failed",e);}
    }
    private void open(){
        synchronized(this){
            if(finished)return;
            started=SystemClock.elapsedRealtime();handshakeStarted=started;lastFrame=started;
            ReaderDiagnosticLog.event("EDGE","request start priority="+priority+" queue_ms="+(started-queuedAt));
        }
        if(text.trim().isEmpty()){finish("Пустой фрагмент");return;}
        if(text.getBytes(StandardCharsets.UTF_8).length>MAX_INPUT_UTF8){
            finish("Текст превышает 4096 UTF-8 байт");return;
        }
        synchronized(this){
            watchdog=TIMER.scheduleAtFixedRate(()->{
                String issue=null;
                synchronized(EdgeSpeechEngine.this){
                    if(finished)return;
                    long now=SystemClock.elapsedRealtime();
                    if(firstAudio==0&&now-handshakeStarted>18000)issue="Тайм-аут первого аудиопакета (18 с)";
                    else if(firstAudio>0&&now-lastFrame>25000)issue="Тайм-аут передачи MP3 (25 с)";
                }
                if(issue!=null)finish(issue);
            },2,2,TimeUnit.SECONDS);
        }
        connect();
    }
    private void connect(){
        EdgeWebSocketPool.Lease resource;
        synchronized(this){if(finished||canceled)return;
            ++connectionAttempt;handshakeStarted=SystemClock.elapsedRealtime();}
        try{resource=EdgeWebSocketPool.acquire(this);}
        catch(Exception e){finish("Нет свободного Edge WebSocket: "+e);return;}
        synchronized(this){
            if(finished){EdgeWebSocketPool.discard(resource,true);return;}
            lease=resource;
        }
        if(resource.open){socketReady(resource,true);return;}
        ReaderDiagnosticLog.event("WS","handshake start resource="+resource.id+" attempt="+connectionAttempt);
        String gec=EdgeToken.generate(EdgeServerClock.adjustedEpochSeconds());
        lastGec=gec;
        String query="?Ocp-Apim-Subscription-Key="+EdgeToken.TOKEN+
            "&Sec-MS-GEC="+gec+
            "&Sec-MS-GEC-Version="+EdgeToken.VERSION+"&ConnectionId="+id();
        Request request=new Request.Builder().url(URL+query).header("Origin",ORIGIN)
            .header("User-Agent",USER_AGENT).header("Pragma","no-cache")
            .header("Cache-Control","no-cache").header("Accept-Language","en-US,en;q=0.9")
            .header("Cookie","MUID="+id().toUpperCase(Locale.ROOT)).build();
        try{EdgeWebSocketPool.connect(resource,request);}
        catch(Exception e){EdgeWebSocketPool.discard(resource,true);finish("Ошибка подключения Edge: "+e);}
    }
    void socketReady(EdgeWebSocketPool.Lease resource,boolean reused){
        synchronized(this){if(finished||resource!=lease)return;}
        reusedTurn=reused;
        Log.i("EdgeSpeech","resourceId="+resource.id+" socketReused="+reused);
        ReaderDiagnosticLog.event("WS","socket ready resource="+resource.id+" reused="+reused+" elapsed_ms="+(SystemClock.elapsedRealtime()-handshakeStarted));
        try{callback.connected();}catch(Exception e){Log.w("EdgeSpeech","Connected callback: "+e);}
        if(!resource.configured){
            String config="X-Timestamp:"+timestamp()+"\r\nContent-Type:application/json; charset=utf-8\r\n"
                +"Path:speech.config\r\n\r\n{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                +"\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"true\"},"
                +"\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}\r\n";
            if(!resource.send(config)){finish("Не удалось отправить настройки синтеза");return;}
            resource.configured=true;
        }
        String ssml="<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>"
            +"<voice name='"+escaped(voice)+"'><prosody pitch='+0Hz' rate='"+rate
            +"' volume='+0%'>"+escaped(text)+"</prosody></voice></speak>";
        resource.requestId=id();
        if(!resource.send("X-RequestId:"+resource.requestId+"\r\nContent-Type:application/ssml+xml\r\n"
                +"X-Timestamp:"+timestamp()+"Z\r\nPath:ssml\r\n\r\n"+ssml))
            finish("Не удалось отправить запрос синтеза");
    }
    void socketText(EdgeWebSocketPool.Lease resource,String message){
        synchronized(this){if(finished||resource!=lease)return;}
        String responseId=EdgeWsFrames.textHeader(message,"X-RequestId");
        if(!responseId.isEmpty()&&!responseId.equalsIgnoreCase(resource.requestId))return;
        String path=EdgeWsFrames.textPath(message);
        if("audio.metadata".equals(path)){
            String body=EdgeWsFrames.textBody(message);
            if(!body.isEmpty()){
                List<EdgeWordTimings.Boundary> snapshot;
                synchronized(this){if(finished||resource!=lease)return;
                    EdgeWordTimings.parseMetadata(body,words);snapshot=new ArrayList<>(words);}
                try{callback.metadata(snapshot);}catch(Exception e){Log.w("EdgeSpeech","Metadata callback: "+e);}
            }
        }else if("turn.end".equals(path)){
            ReaderDiagnosticLog.event("EDGE","turn.end resource="+resource.id);
            finish(null);
        }
    }
    void socketAudio(EdgeWebSocketPool.Lease resource,ByteString packet){
        synchronized(this){if(finished||resource!=lease)return;}
        byte[] data=packet.toByteArray();EdgeWsFrames.Frame frame;
        try{frame=EdgeWsFrames.audio(data);}
        catch(IOException e){finish("Повреждённый Edge MP3 кадр: "+e.getMessage());return;}
        if(!frame.requestId.isEmpty()&&!frame.requestId.equalsIgnoreCase(resource.requestId))return;
        if(frame.terminal)return;
        boolean tooLarge;int count;long bytes;boolean firstFrame;
        synchronized(this){
            if(finished||resource!=lease)return;
            tooLarge=audio.size()+frame.dataLength>MAX_AUDIO;
            firstFrame=firstAudio==0;
            if(!tooLarge){audio.write(data,frame.dataOffset,frame.dataLength);frames++;
                lastFrame=SystemClock.elapsedRealtime();if(firstAudio==0)firstAudio=lastFrame;}
            count=frames;bytes=audio.size();
        }
        if(tooLarge){finish("MP3 превысил 12 МБ");return;}
        if(firstFrame)ReaderDiagnosticLog.event("EDGE","first_mp3_frame elapsed_ms="+(lastFrame-started)+" bytes="+frame.dataLength);
        try{callback.audio(data,frame.dataOffset,frame.dataLength);}catch(Exception e){finish("Ошибка передачи MP3 в плеер: "+e);return;}
        if((count&7)==0)callback.progress(count,bytes);
    }
    void socketFailure(EdgeWebSocketPool.Lease resource,Throwable error,Response response){
        int status=response==null?0:response.code();
        ReaderDiagnosticLog.event("WS","failure http="+status+" resource="+resource.id+
            " error_type="+(error==null?"none":error.getClass().getSimpleName()));
        synchronized(this){
            if(finished||resource!=lease){socketTerminated(resource);return;}
        }
        if(status==403&&response!=null&&!retriedAfter403){
            String serverDate=response.header("Date");
            if(EdgeServerClock.correctFor403(serverDate,lastGec,System.currentTimeMillis())){
                retriedAfter403=true;
                synchronized(this){if(lease==resource)lease=null;}
                Log.i("EdgeSpeech","HTTP 403: clock corrected, retrying once");
                ReaderDiagnosticLog.event("EDGE","http403 server clock adjusted; retry once");
                connect();return;
            }
            // A valid Date can still refresh future tokens even when it does
            // not change the failed token's five-minute GEC bucket.
            EdgeServerClock.observeHttpDate(serverDate,403,
                resource.requestStartedWall,resource.requestStartedElapsed,
                System.currentTimeMillis(),SystemClock.elapsedRealtime());
        }
        if(status==429){ReaderDiagnosticLog.event("EDGE","HTTP 429 from synthesis WebSocket handshake");
            EdgeSpeechQueue.limited(response);
            finish("HTTP 429: Edge временно ограничил запросы");socketTerminated(resource);return;}
        boolean retry;
        synchronized(this){retry=!finished&&status==0&&firstAudio==0&&
            connectionAttempt<3&&error instanceof IOException;
            if(retry&&lease==resource)lease=null;
        }
        if(retry){
            long pause=connectionAttempt==1?100L:200L;
            Log.w("EdgeSpeech","Transient socket failure; retry="+connectionAttempt+" delay="+pause+"ms");
            ReaderDiagnosticLog.event("WS","transport retry attempt="+connectionAttempt+" delay_ms="+pause);
            TIMER.schedule(this::connect,pause,TimeUnit.MILLISECONDS);
            return;
        }
        finish(status>0?"Edge HTTP "+status+": отказ сервера":"Ошибка соединения Edge: "+error);
        socketTerminated(resource);
    }
    void socketClosed(EdgeWebSocketPool.Lease resource,int code,String reason){
        boolean retry;
        synchronized(this){
            if(finished||resource!=lease){socketTerminated(resource);return;}
            retry=reusedTurn&&!reusedRestarted&&firstAudio==0;
            if(retry){reusedRestarted=true;lease=null;}
        }
        if(retry){
            Log.w("EdgeSpeech","Reused socket rejected turn; fresh handshake fallback");
            connect();return;
        }
        finish("Edge закрыл соединение до завершения: "+code);
        socketTerminated(resource);
    }
}
