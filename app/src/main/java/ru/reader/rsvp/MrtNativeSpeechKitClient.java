package ru.reader.rsvp;

import android.util.Base64;

import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Native Android network-only TTS transport. No WebView, Javascript or WebAudio.
 * This uses the *official* SpeechKit v1 API (requires the user's own API key);
 * it is not a clone of the undocumented MRT/UniProxy transport.
 * Encoded OGG/MP3 bytes are streamed directly into the existing validated MRT cache.
 */
final class MrtNativeSpeechKitClient {
    interface Listener {
        void onAudioBlock(String base64, int bytes);
        void onComplete();
        void onError(String reason);
    }
    private static final String URL="https://tts.api.cloud.yandex.net/speech/v1/tts:synthesize";
    private static final int LIMIT=24*1024*1024;
    private static final OkHttpClient HTTP=new OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS).readTimeout(110,TimeUnit.SECONDS)
        .callTimeout(120,TimeUnit.SECONDS).build();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private Call call;

    void cancel(){cancelled.set(true);Call current=call;if(current!=null)current.cancel();}

    void synthesize(String apiKey,String text,String voice,String speed,String emotion,Listener callback){
        if(apiKey==null||apiKey.trim().isEmpty()){
            callback.onError("Нужен личный API-ключ Yandex SpeechKit в настройках MRT");return;
        }
        if(text==null||text.trim().isEmpty()||text.length()>5000){
            callback.onError("SpeechKit v1 принимает не более 5000 символов за запрос");return;
        }
        String speaker=voice==null||voice.isEmpty()?"jane":voice;
        String tempo=speed==null||speed.isEmpty()?"1.0":speed;
        String tone=emotion==null||emotion.isEmpty()?"neutral":emotion;
        FormBody.Builder form=new FormBody.Builder().add("text",text).add("lang","ru-RU")
            .add("voice",speaker).add("speed",tempo).add("format","oggopus");
        if(!"neutral".equals(tone))form.add("emotion",tone);
        FormBody body=form.build();
        Request request=new Request.Builder().url(URL).header("Authorization","Api-Key "+apiKey.trim())
            .header("Accept","audio/ogg").post(body).build();
        call=HTTP.newCall(request);
        call.enqueue(new Callback(){
            @Override public void onFailure(Call call,IOException e){
                if(!cancelled.get())callback.onError("Сеть / SpeechKit: "+e.getClass().getSimpleName()+": "+e.getMessage());
            }
            @Override public void onResponse(Call call,Response response){
                try(Response r=response){
                    if(cancelled.get())return;
                    if(!r.isSuccessful()){
                        callback.onError("SpeechKit HTTP "+r.code()+" (проверьте ключ, квоту и доступ к сервису)");return;
                    }
                    ResponseBody result=r.body();
                    if(result==null){callback.onError("SpeechKit прислал пустой ответ");return;}
                    String type=r.header("Content-Type","");
                    if(type.contains("json")||type.contains("html")||type.contains("text/")){
                        callback.onError("SpeechKit не прислал аудиоданные ("+type+")");return;
                    }
                    try(InputStream input=result.byteStream()){
                        byte[] buf=new byte[16384];int len;int total=0;
                        while(!cancelled.get()&&(len=input.read(buf))!=-1){
                            total+=len;
                            if(total>LIMIT){callback.onError("Аудио превышает 24 МБ");return;}
                            callback.onAudioBlock(Base64.encodeToString(buf,0,len,Base64.NO_WRAP),len);
                        }
                        if(!cancelled.get()){
                            if(total<2048)callback.onError("Недостаточно аудиоданных SpeechKit");
                            else callback.onComplete();
                        }
                    }
                }catch(IOException e){if(!cancelled.get())callback.onError("Ошибка получения аудио: "+e.getMessage());}
            }
        });
    }
}
