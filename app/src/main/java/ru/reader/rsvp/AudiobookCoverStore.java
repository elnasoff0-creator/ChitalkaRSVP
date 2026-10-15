package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Saves downloaded audiobook artwork beside MP3s. Never downloads or changes audio files. */
final class AudiobookCoverStore {
    private static final int MAX_BYTES=5*1024*1024;
    private static final ExecutorService EXECUTOR=Executors.newSingleThreadExecutor();
    private static final Set<String> IN_PROGRESS=new HashSet<>();
    private static final Map<String,Long> FAILED_AT=new HashMap<>();
    private static final long RETRY_AFTER_MS=10L*60L*1000L;
    private AudiobookCoverStore(){}

    static File coverFile(Context ctx,BookImportManager.Job job){
        return new File(BookImportManager.directory(ctx,job),"cover.jpg");
    }
    static String localUri(Context ctx,BookImportManager.Job job){
        File cover=coverFile(ctx,job);
        return cover.isFile()&&cover.length()>100?Uri.fromFile(cover).toString():"";
    }
    static String localUri(Context ctx,String bookId){
        if(bookId==null||!bookId.startsWith("url:"))return "";
        String source=bookId.substring(4);
        for(BookImportManager.Job job:BookImportManager.load(ctx))
            if(source.equals(job.source))return localUri(ctx,job);
        return "";
    }
    /** Manual cover chosen by the user must always win over automatically saved artwork. */
    static void registerDefault(Context ctx,BookImportManager.Job job){
        String local=localUri(ctx,job);if(local.isEmpty())return;
        SharedPreferences prefs=ctx.getSharedPreferences("reader",0);
        String key="audio_cover:url:"+job.source;
        String previous=prefs.getString(key,"");
        if(previous.isEmpty()){
            prefs.edit().putString(key,local).apply();
        }else if(previous.startsWith("file:")){
            String path=Uri.parse(previous).getPath();
            if(path==null||!new File(path).isFile())prefs.edit().putString(key,local).apply();
        }
    }
    /** Executes on a worker thread. Failed artwork must never prevent MP3 downloading. */
    static synchronized boolean ensure(Context ctx,BookImportManager.Job job){
        File destination=coverFile(ctx,job);
        if(destination.isFile()&&destination.length()>100){
            registerDefault(ctx,job);return true;
        }
        if(job.cover==null||job.cover.trim().isEmpty())return false;
        File parent=destination.getParentFile();
        if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))return false;
        File tmp=new File(parent,"cover.jpg.part");
        try{
            byte[] encoded=fetch(job.cover.trim());
            BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(encoded,0,encoded.length,dimensions);
            if(dimensions.outWidth<=0||dimensions.outHeight<=0||dimensions.outWidth>16000||dimensions.outHeight>16000)
                throw new IllegalArgumentException("Некорректная обложка");
            BitmapFactory.Options options=new BitmapFactory.Options();
            int sample=1;while(dimensions.outWidth/sample>1400||dimensions.outHeight/sample>2000)sample*=2;
            options.inSampleSize=sample;
            Bitmap bitmap=BitmapFactory.decodeByteArray(encoded,0,encoded.length,options);
            if(bitmap==null)throw new IllegalArgumentException("Не удалось декодировать обложку");
            try(FileOutputStream out=new FileOutputStream(tmp,false)){
                if(!bitmap.compress(Bitmap.CompressFormat.JPEG,88,out))throw new IllegalArgumentException("Не удалось сохранить JPEG");
                out.flush();out.getFD().sync();
            }finally{bitmap.recycle();}
            if(tmp.length()<100)throw new IllegalArgumentException("Пустая обложка");
            if(destination.exists()&&!destination.delete())throw new IllegalArgumentException("Невозможно заменить обложку");
            if(!tmp.renameTo(destination))throw new IllegalArgumentException("Невозможно завершить сохранение обложки");
            registerDefault(ctx,job);return true;
        }catch(Exception ex){
            Log.w("AudiobookCover","No cover for: "+job.title,ex);
            return false;
        }finally{if(tmp.exists())tmp.delete();}
    }
    private static boolean safeUri(URI uri){
        if(uri==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null)return false;
        String host=uri.getHost();if(host==null||host.isEmpty())return false;
        String h=host.toLowerCase(java.util.Locale.ROOT);
        if(h.equals("localhost")||h.endsWith(".localhost")||h.endsWith(".local")||h.endsWith(".internal"))return false;
        try{
            for(InetAddress address:InetAddress.getAllByName(host)){
                if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress()
                    ||address.isSiteLocalAddress()||address.isMulticastAddress())return false;
            }
        }catch(Exception e){return false;}
        return true;
    }
    private static byte[] fetch(String address)throws Exception{
        URI current=new URI(address);int redirects=0;
        while(redirects<=3){
            if(!safeUri(current))throw new IllegalArgumentException("Недопустимый адрес обложки");
            HttpURLConnection connection=(HttpURLConnection)current.toURL().openConnection();
            connection.setConnectTimeout(8500);connection.setReadTimeout(11000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Mozilla/5.0 (ChitalkaRSVP)");
            connection.setRequestProperty("Accept","image/jpeg,image/png,image/webp,image/*;q=0.8");
            try{
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String to=connection.getHeaderField("Location");
                    if(to==null||to.isEmpty())throw new IllegalArgumentException("Редирект без адреса");
                    current=current.resolve(to);redirects++;continue;
                }
                if(status!=200)throw new IllegalArgumentException("HTTP "+status);
                String contentType=connection.getContentType();
                if(contentType==null||!contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/"))
                    throw new IllegalArgumentException("Ответ сервера не является изображением");
                if(connection.getContentLengthLong()>MAX_BYTES)throw new IllegalArgumentException("Слишком большой размер обложки");
                try(InputStream in=connection.getInputStream();ByteArrayOutputStream data=new ByteArrayOutputStream()){
                    byte[] block=new byte[8192];int n;
                    while((n=in.read(block))!=-1){
                        if(data.size()+n>MAX_BYTES)throw new IllegalArgumentException("Обложка превышает 5 МБ");
                        data.write(block,0,n);
                    }
                    return data.toByteArray();
                }
            }finally{connection.disconnect();}
        }
        throw new IllegalArgumentException("Слишком много перенаправлений");
    }
    /** Backfills existing downloads without touching MP3s or starting their download service. */
    static void backfill(Context context,Runnable finished){
        Context app=context.getApplicationContext();
        for(BookImportManager.Job job:BookImportManager.load(app))scheduleBackfill(app,job,finished);
    }
    static void backfillOne(Context context,String libraryId,Runnable finished){
        if(libraryId==null||!libraryId.startsWith("url:"))return;
        Context app=context.getApplicationContext();
        for(BookImportManager.Job job:BookImportManager.load(app)){
            if(job.source.equals(libraryId.substring(4))){scheduleBackfill(app,job,finished);break;}
        }
    }
    private static void scheduleBackfill(Context app,BookImportManager.Job job,Runnable finished){
        if(job.cover==null||job.cover.isEmpty()||!localUri(app,job).isEmpty())return;
        boolean hasAudio=false;
        for(int i=0;i<job.chapters.size();i++){
            if(BookImportManager.trackFile(app,job,i).isFile()){hasAudio=true;break;}
        }
        if(!hasAudio)return;
        synchronized(IN_PROGRESS){
            Long failed=FAILED_AT.get(job.source);
            if(failed!=null&&System.currentTimeMillis()-failed<RETRY_AFTER_MS)return;
            if(!IN_PROGRESS.add(job.source))return;
        }
        EXECUTOR.execute(()->{
            try{
                if(ensure(app,job)){
                    synchronized(IN_PROGRESS){FAILED_AT.remove(job.source);}
                    BookImportManager.updateLibrary(app,job);
                    if(finished!=null)new android.os.Handler(android.os.Looper.getMainLooper()).post(finished);
                }else synchronized(IN_PROGRESS){FAILED_AT.put(job.source,System.currentTimeMillis());}
            }finally{synchronized(IN_PROGRESS){IN_PROGRESS.remove(job.source);}}
        });
    }
}
