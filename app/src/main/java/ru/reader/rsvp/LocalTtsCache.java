package ru.reader.rsvp;
import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** System TTS waveforms live ONLY in OS-managed, disposable app cache. */
final class LocalTtsCache {
    private LocalTtsCache(){}
    static File file(Context ctx,String book,String engine,String voice,String text){
        String key=book+"\n"+engine+"\n"+voice+"\n"+text;
        StringBuilder b=new StringBuilder();
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            for(int i=0;i<24;i++)b.append(String.format(Locale.ROOT,"%02x",digest[i]&255));
        }catch(Exception ex){b.append(Integer.toHexString(key.hashCode()));}
        File dir=new File(ctx.getCacheDir(),"system-tts-audio");if(!dir.exists())dir.mkdirs();
        return new File(dir,b+".wav");
    }
}
