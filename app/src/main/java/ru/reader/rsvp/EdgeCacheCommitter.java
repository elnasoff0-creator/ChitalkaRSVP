package ru.reader.rsvp;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A fully sealed cache item is the only source of new offline MP3 files.
 * This worker NEVER controls playback or starts a network request.
 */
final class EdgeCacheCommitter {
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EdgeOfflineCommit");t.setPriority(Thread.MIN_PRIORITY);return t;
    });
    interface Listener {void onSaved(String key,File file,String error);}
    static void submit(Context context,File completed,File metadata,File destination,boolean trim,Listener listener){
        WORK.execute(()->{
            String key="";File file=null;String error=null;
            try{
                byte[] raw=new byte[(int)metadata.length()];
                try(FileInputStream in=new FileInputStream(metadata)){
                    int p=0,r;while(p<raw.length&&(r=in.read(raw,p,raw.length-p))>0)p+=r;
                    if(p!=raw.length)throw new Exception("Incomplete cache metadata");
                }
                JSONObject data=new JSONObject(new String(raw,StandardCharsets.UTF_8));
                key=data.getString("key");String text=data.getString("text");
                if(!key.matches("[0-9a-f]{20,80}"))throw new Exception("Invalid cache key");
                JSONArray tickRows=data.optJSONArray("boundaries");
                List<EdgeWordTimings.Boundary> ticks=new ArrayList<>();
                if(tickRows!=null)for(int i=0;i<tickRows.length();i++){
                    JSONArray row=tickRows.optJSONArray(i);
                    if(row!=null&&row.length()>=3)
                        ticks.add(new EdgeWordTimings.Boundary(row.optLong(0),row.optLong(1),row.optString(2)));
                }
                if(!destination.isDirectory()&&!destination.mkdirs())throw new Exception("Cannot create offline folder");
                file=new File(destination,key+".mp3");
                if(file.isFile()&&file.length()>1500){completed.delete();}
                else {
                    if(file.exists()&&!file.delete())throw new Exception("Cannot replace corrupted offline file");
                    if(!completed.renameTo(file)){
                        try(FileInputStream input=new FileInputStream(completed);
                            FileOutputStream output=new FileOutputStream(file)){
                            byte[] buf=new byte[32768];int n;
                            while((n=input.read(buf))>0)output.write(buf,0,n);
                            output.getFD().sync();
                        }
                        completed.delete();
                    }
                }
                if(file.length()<1500)throw new Exception("Offline file is not complete");
                EdgeWordTimings.store(file,text,ticks);
                // Timing files and MP3 are available before optional decoding work.
                metadata.delete();
                if(trim)try{EdgeTrailingSilence.detectAndStore(file,text);}
                    catch(Exception ex){Log.w("EdgeOfflineCommit","Tail analysis skipped",ex);}
            }catch(Exception ex){error=ex.getMessage();Log.w("EdgeOfflineCommit","Will recover cache on next open",ex);}
            if(listener!=null)listener.onSaved(key,file,error);
        });
    }
}
