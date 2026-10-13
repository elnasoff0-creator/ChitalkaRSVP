package ru.reader.rsvp;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class TestMrtAudioFormats {
    private static void ok(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static byte[] data(int length,byte[] signature){
        byte[] d=new byte[length];System.arraycopy(signature,0,d,0,signature.length);return d;
    }
    private static byte[] readAll(File file) throws Exception {
        try(java.io.FileInputStream in=new java.io.FileInputStream(file);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buf=new byte[8192];int count;while((count=in.read(buf))!=-1)out.write(buf,0,count);return out.toByteArray();
        }
    }
    private static void sample(Context ctx,String key,byte[] bytes,String extension) throws Exception {
        MrtAudioCache cache=new MrtAudioCache(ctx,"content://tests/book","Тест форматов",100);
        int session=key.hashCode();
        CountDownLatch saved=new CountDownLatch(1);
        cache.begin(session,key);
        for(int i=0;i<bytes.length;i+=12288){
            byte[] part=Arrays.copyOfRange(bytes,i,Math.min(bytes.length,i+12288));
            cache.append(session,Base64.getEncoder().encodeToString(part));
        }
        cache.finish(session,(file,size,chunks,error)->{
            ok(error==null,"Format "+key+": "+error);
            ok(file!=null&&file.getName().endsWith(extension),"extension: "+key);
            ok(size==bytes.length,"complete original bytes "+key);
            saved.countDown();
        });
        ok(saved.await(3,TimeUnit.SECONDS),"format saved: "+key);
        cache.close();
        MrtAudioCache again=new MrtAudioCache(ctx,"content://tests/book","Тест форматов",100);
        File restored=again.cached(key);
        ok(restored!=null&&restored.getName().endsWith(extension),"reopened "+key);
        ok(Arrays.equals(readAll(restored),bytes),"no transcoding of "+key);
        again.close();
    }
    public static void main(String[] args)throws Exception {
        Context ctx=new Context(Files.createTempDirectory("mrt-formats").toFile());
        sample(ctx,"one-mp3",data(4096,new byte[]{'I','D','3',4,0,0,0,0,0,0}),".mp3");
        byte[] wav=data(250000,new byte[]{'R','I','F','F',0,0,0,0,'W','A','V','E','f','m','t',' '});
        sample(ctx,"second-wav",wav,".wav");
        sample(ctx,"second-aac",data(250000,new byte[]{(byte)0xff,(byte)0xf1,0x50,(byte)0x80,0x00,0x1f,(byte)0xfc}),".aac");
        sample(ctx,"ogg",data(5000,new byte[]{'O','g','g','S',0,2}),".ogg");
        sample(ctx,"mp4",data(5000,new byte[]{0,0,0,24,'f','t','y','p','M','4','A',' '}),".m4a");
        sample(ctx,"webm",data(5000,new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3}),".webm");
        sample(ctx,"flac",data(5000,new byte[]{'f','L','a','C'}),".flac");
        ok(MrtAudioFormat.identify(data(20000,new byte[]{'H','T','M','L'}),4096)==null,"unknown data rejected");
        System.out.println("PASS: native encoded MP3/WAV/AAC/OGG/M4A/WebM/FLAC with persistent cache and unchanged bytes");
    }
}
