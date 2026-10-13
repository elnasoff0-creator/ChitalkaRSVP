package ru.reader.rsvp;
import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public class TestMrtCachePersistence {
 static void ok(boolean yes,String message){if(!yes)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception {
  File root=Files.createTempDirectory("mrt-cache-test").toFile();
  Context context=new Context(root);
  String uri="content://books/book77";
  MrtAudioCache cache=new MrtAudioCache(context,uri,"Тестовая книга",50);
  ok(cache.directory().getAbsolutePath().contains("Тестовая книга"),"folder named by book");
  byte[] mp3=new byte[4000];Arrays.fill(mp3,(byte)0x54);
  mp3[0]=(byte)0xff;mp3[1]=(byte)0xfb;mp3[2]=(byte)0x90;mp3[3]=(byte)0x64;
  CountDownLatch finished=new CountDownLatch(1);
  cache.begin(101,"test-key");cache.append(101,Base64.getEncoder().encodeToString(mp3));
  cache.finish(101,(file,bytes,chunks,error)->{
    ok(error==null,"MP3 saved: "+error);ok(bytes==4000,"bytes tracked");ok(chunks==1,"chunks tracked");
    ok(file!=null&&file.isFile(),"ready MP3 exists");finished.countDown();
  });
  ok(finished.await(3,TimeUnit.SECONDS),"completed asynchronously");
  cache.saveIndex("[{\"start\":0,\"end\":30,\"played\":true}]");
  cache.close();
  MrtAudioCache reopen=new MrtAudioCache(context,uri,"Тестовая книга",50);
  ok(reopen.cached("test-key")!=null,"MP3 survived restart");
  // The writer is asynchronous; the previous close must finish index writes.
  for(int i=0;i<30&&!reopen.readIndex().contains("played");i++)Thread.sleep(30);
  ok(reopen.readIndex().contains("played"),"queue index survived restart");
  File legacyDir=new File(context.getCacheDir(),"mrt_encoded_audio");legacyDir.mkdirs();
  Files.write(new File(legacyDir,"legacy-key.mp3").toPath(),mp3);
  ok(reopen.cached("legacy-key")!=null,"old cache migrated into book directory");
  CountDownLatch cleared=new CountDownLatch(1);
  reopen.clear(cleared::countDown);
  ok(cleared.await(3,TimeUnit.SECONDS),"cleared asynchronously");
  ok(reopen.cached("test-key")==null,"MP3 was deleted");
  ok(reopen.cached("legacy-key")==null,"cleared audio cannot reappear from older cache");
  ok(reopen.readIndex().equals("[]"),"queue index was deleted");
  reopen.close();
  MrtAudioCache.removeBook(context,uri);
  for(int i=0;i<100 && MrtAudioCache.bookFolder(context,uri,"Тестовая книга").exists();i++)Thread.sleep(30);
  ok(!MrtAudioCache.bookFolder(context,uri,"Тестовая книга").exists(),"book removal cleans audio folder");
  System.out.println("PASS: per-book MP3 cache, chunks, persistent queue, clear and book removal");
 }
}
