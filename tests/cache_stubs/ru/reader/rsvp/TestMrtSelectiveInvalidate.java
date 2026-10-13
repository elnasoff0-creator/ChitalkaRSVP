package ru.reader.rsvp;
import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public class TestMrtSelectiveInvalidate {
    static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    static void save(MrtAudioCache cache, int session, String key, byte[] mp3) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        cache.begin(session,key);
        cache.append(session,Base64.getEncoder().encodeToString(mp3));
        cache.finish(session,(file,bytes,chunks,error)->{
            check(error==null && file!=null && file.isFile(),"saved first: "+error);
            done.countDown();
        });
        check(done.await(4,TimeUnit.SECONDS),"save callback");
    }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("mrt-selective-invalidate").toFile();
        Context context=new Context(root);
        MrtAudioCache cache=new MrtAudioCache(context,"content://books/selected","Книга",50);
        String first="1111111111111111111111111111111111111111111111111111111111111111";
        String second="2222222222222222222222222222222222222222222222222222222222222222";
        byte[] mp3=new byte[4096];
        Arrays.fill(mp3,(byte)0x54);
        mp3[0]=(byte)0xff;mp3[1]=(byte)0xfb;mp3[2]=(byte)0x90;mp3[3]=(byte)0x64;
        save(cache,10,first,mp3);
        save(cache,11,second,mp3);
        cache.saveIndex("[{\"start\":0,\"played\":true},{\"start\":20,\"played\":false}]");
        File legacy=new File(context.getCacheDir(),"mrt_encoded_audio");legacy.mkdirs();
        Files.write(new File(legacy,first+".mp3").toPath(),mp3);
        CountDownLatch invalidated=new CountDownLatch(1);
        cache.invalidate(first, ok->{check(ok,"invalidate success");invalidated.countDown();});
        check(invalidated.await(4,TimeUnit.SECONDS),"invalidate callback");
        check(cache.cached(first)==null,"selected MP3 removed without migrating legacy copy");
        check(cache.cached(second)!=null,"other fragment preserved");
        check(cache.readIndex().contains("played"),"queue index preserved");
        save(cache,12,first,mp3);
        check(cache.cached(first)!=null,"same cache key can be downloaded again");
        cache.close();
        System.out.println("PASS: selective fragment invalidation, other MP3 unaffected, queue.json retained, re-download possible");
    }
}
