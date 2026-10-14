package ru.reader.rsvp;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        void finished(byte[] mp3,int chunks,String error);
    }
    private static final int LIMIT_BYTES=12*1024*1024;
    private static final String ENDPOINT="wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        +"(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";
    private static final OkHttpClient HTTP=new OkHttpClient.Builder()
        .connectTimeout(18,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS)
        .pingInterval(15,TimeUnit.SECONDS).build();
    private WebSocket socket;
    private boolean completed;
    private final Callback callback;
    private final ByteArrayOutputStream received=new ByteArrayOutputStream();
    private int chunks;

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
        byte[] bytes=received.toByteArray();
        if(error==null && (bytes.length<1500 || chunks==0))error="Сервис не вернул аудиоданные";
        callback.finished(error==null?bytes:null,chunks,error);
        if(socket!=null)socket.close(1000,"completed");
    }
    void cancel(){synchronized(this){completed=true;}if(socket!=null)socket.cancel();}
    private void connect(String text,String voice,String rate){
        try{
            String uid=uuid();
            String url=ENDPOINT+"?TrustedClientToken="+EdgeToken.TOKEN
                +"&ConnectionId="+uid+"&Sec-MS-GEC="+EdgeToken.gec()+"&Sec-MS-GEC-Version="+EdgeToken.VERSION;
            Request req=new Request.Builder().url(url).header("Origin","chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("Pragma","no-cache").header("Cache-Control","no-cache")
                .header("User-Agent",UA).header("Accept-Language","en-US,en;q=0.9")
                .header("Cookie","muid="+uuid().toUpperCase(Locale.ROOT)+";").build();
            socket=HTTP.newWebSocket(req,new WebSocketListener(){
                @Override public void onOpen(WebSocket ws,Response response){
                    String config="X-Timestamp:"+date()+"\r\nContent-Type:application/json; charset=utf-8\r\n"
                        +"Path:speech.config\r\n\r\n"
                        +"{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                        +"\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
                    ws.send(config);
                    String ssml="<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ru-RU'>"
                        +"<voice name='"+xml(voice)+"'><prosody rate='"+xml(rate)+"' pitch='+0Hz' volume='+0%'>"
                        +xml(text)+"</prosody></voice></speak>";
                    ws.send("X-RequestId:"+uuid()+"\r\nContent-Type:application/ssml+xml\r\nX-Timestamp:"+date()
                        +"Z\r\nPath:ssml\r\n\r\n"+ssml);
                }
                @Override public void onMessage(WebSocket ws,String body){
                    String header=body.split("\\r\\n\\r\\n",2)[0];
                    if(header.contains("Path:turn.end"))finish(null);
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
                        if(received.size()+count>LIMIT_BYTES){finish("Аудиофрагмент превышает 12 МБ");return;}
                        received.write(packet,head+2,count);chunks++;
                        callback.progress(chunks,received.size());
                    }
                }
                @Override public void onFailure(WebSocket ws,Throwable error,Response response){
                    String suffix=response==null?"":" HTTP "+response.code();
                    finish("Соединение Edge TTS"+suffix+": "+(error==null?"неизвестная ошибка":error.getMessage()));
                }
                @Override public void onClosed(WebSocket ws,int code,String reason){if(!completed)finish("Edge закрыл соединение до завершения: "+code);}
            });
        }catch(Exception ex){finish("Не удалось подключиться к Edge: "+ex.getMessage());}
    }
}
