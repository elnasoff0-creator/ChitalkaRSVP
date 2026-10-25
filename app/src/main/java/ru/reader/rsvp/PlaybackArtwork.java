package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded, asynchronous cover extraction for system MediaSession metadata. */
final class PlaybackArtwork {
    interface Callback { void onReady(Bitmap cover); }
    private static final ExecutorService EXEC=Executors.newSingleThreadExecutor();
    private PlaybackArtwork() {}
    static void load(Context ctx,String id,boolean textBook,String firstTrack,Callback callback){
        final Context app=ctx.getApplicationContext();
        EXEC.execute(()->{
            Bitmap bitmap=null;
            try {
                byte[] data=null;
                if(textBook){
                    if(id!=null&&!id.isEmpty())data=CoverLoader.get(app,id);
                }else{
                    String uri=app.getSharedPreferences("reader",0).getString("audio_cover:"+id,"");
                    if(uri.isEmpty())uri=AudiobookCoverStore.localUri(app,id);
                    if(!uri.isEmpty()){
                        try(InputStream input=app.getContentResolver().openInputStream(Uri.parse(uri))){
                            if(input!=null)data=readLimited(input,3*1024*1024);
                        }
                    }
                    if(data==null&&firstTrack!=null&&!firstTrack.isEmpty()){
                        MediaMetadataRetriever retriever=new MediaMetadataRetriever();
                        try{
                            retriever.setDataSource(app,Uri.parse(firstTrack));
                            data=retriever.getEmbeddedPicture();
                        }finally{try{retriever.release();}catch(Exception ignored){}}
                    }
                }
                if(data!=null&&data.length>0&&data.length<=4*1024*1024){
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                    if(bounds.outWidth>0&&bounds.outHeight>0){
                        BitmapFactory.Options opts=new BitmapFactory.Options();
                        int sample=1;while(bounds.outWidth/sample>640||bounds.outHeight/sample>640)sample*=2;
                        opts.inSampleSize=sample;
                        bitmap=BitmapFactory.decodeByteArray(data,0,data.length,opts);
                    }
                }
            }catch(Exception ignored){}
            Bitmap ready=bitmap;
            new Handler(Looper.getMainLooper()).post(()->callback.onReady(ready));
        });
    }
    private static byte[] readLimited(InputStream input,int max)throws java.io.IOException {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] b=new byte[8192];int n;
        while((n=input.read(b))!=-1){if(out.size()+n>max)return null;out.write(b,0,n);}
        return out.toByteArray();
    }
}
