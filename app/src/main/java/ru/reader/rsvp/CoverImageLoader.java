package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared asynchronous cover loader for website audiobook covers and local EPUB/FB2. */
final class CoverImageLoader {
    private static final ExecutorService EXEC=Executors.newFixedThreadPool(3);
    private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(18*1024){
        @Override protected int sizeOf(String k,Bitmap b){return Math.max(1,b.getByteCount()/1024);}
    };
    private CoverImageLoader(){}
    private static Bitmap scaled(byte[] data){
        BitmapFactory.Options check=new BitmapFactory.Options();check.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(data,0,data.length,check);
        if(check.outWidth<=0||check.outHeight<=0)return null;
        BitmapFactory.Options opt=new BitmapFactory.Options();
        int sample=1;while(check.outWidth/sample>500||check.outHeight/sample>700)sample*=2;
        opt.inSampleSize=sample;return BitmapFactory.decodeByteArray(data,0,data.length,opt);
    }
    private static String key(String value){try{
        byte[] sum=MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder b=new StringBuilder();for(int i=0;i<12;i++)b.append(String.format(Locale.ROOT,"%02x",sum[i]&255));return b.toString();
    }catch(Exception ignored){return Integer.toHexString(value.hashCode());}}
    static void show(ImageView target,String url,boolean textBook){
        target.setTag(null);target.setImageDrawable(null);
        target.setVisibility(android.view.View.INVISIBLE);
        if(url==null||url.isEmpty())return;
        String identity=(textBook?"book:":"web:")+url;
        target.setTag(identity);
        Bitmap cached=MEMORY.get(identity);
        if(cached!=null){target.setImageBitmap(cached);target.setVisibility(android.view.View.VISIBLE);return;}
        Context app=target.getContext().getApplicationContext();
        EXEC.execute(()->{
            try{
                File folder=new File(app.getCacheDir(),"download_covers");folder.mkdirs();
                File file=new File(folder,key(identity)+".img");byte[] bytes=null;
                if(file.isFile()&&file.length()>30&&file.length()<4*1024*1024){try(InputStream in=new FileInputStream(file)){bytes=BookLoader.readAll(in);}}
                if(bytes==null){
                    if(textBook){bytes=CoverLoader.get(app,url);}else{
                        URI u=new URI(url);
                        if("file".equalsIgnoreCase(u.getScheme())){
                            File local=new File(u);
                            if(!local.isFile()||local.length()>4*1024*1024L||local.length()<30)return;
                            try(InputStream in=new FileInputStream(local)){bytes=BookLoader.readAll(in);}
                        }else{
                        if(!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||u.getHost()==null)return;
                        HttpURLConnection c=(HttpURLConnection)u.toURL().openConnection();
                        c.setConnectTimeout(9000);c.setReadTimeout(11000);c.setInstanceFollowRedirects(false);
                        c.setRequestProperty("User-Agent","Mozilla/5.0");
                        try{if(c.getResponseCode()!=200||c.getContentLengthLong()>4*1024*1024)return;
                            String mime=c.getContentType();if(mime==null||!mime.startsWith("image/"))return;
                            try(InputStream in=c.getInputStream()){
                                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                                while((n=in.read(buf))>=0){if(out.size()+n>4*1024*1024)return;out.write(buf,0,n);}bytes=out.toByteArray();
                            }
                        }finally{c.disconnect();}
                        }
                    }
                    // Local cover.jpg remains the source of truth: don't duplicate it into transient cache.
                    if(bytes!=null&&!url.startsWith("file:")&&bytes.length>50&&bytes.length<4*1024*1024){try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}catch(Exception ignored){}}
                }
                if(bytes==null)return;Bitmap image=scaled(bytes);if(image==null)return;
                MEMORY.put(identity,image);
                target.post(()->{if(identity.equals(target.getTag())){target.setImageBitmap(image);target.setVisibility(android.view.View.VISIBLE);}});
            }catch(Exception ignored){}
        });
    }
}
