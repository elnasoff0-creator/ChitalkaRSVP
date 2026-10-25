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

/** Streaming capture is separate from the Media3 reader. All disk I/O is serialized
 * on a dedicated thread, never on OkHttp's incoming packet thread or Android UI.
 * Interrupted streams are NOT offline downloads; completed streams survive process death.
 */
final class EdgeStreamCache {
    private static final String TAG="EdgeStreamCache";
    private static final ExecutorService DISK=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EdgeStreamCacheDisk");t.setPriority(Thread.MIN_PRIORITY);return t;
    });
    static final class Entry {
        final File partial,ready,metadata;
        final String key,text;
        final long nonce;
        private FileOutputStream output;
        private boolean stopped;
        private long expected;
        Entry(File dir,String key,String text){
            this.key=key;this.text=text;
            nonce=System.nanoTime();
            File cache=new File(dir,"stream-cache");cache.mkdirs();
            String base=key+"-"+Long.toHexString(nonce);
            partial=new File(cache,base+".part");
            ready=new File(cache,base+".ready");
            metadata=new File(cache,base+".json");
        }
        /** Schedules an append without synchronously touching storage. */
        void append(byte[] source,int from,int length){
            if(length<=0)return;
            byte[] data=new byte[length];System.arraycopy(source,from,data,0,length);
            synchronized(this){if(stopped)return;expected+=length;}
            DISK.execute(()->{
                try{
                    if(output==null)output=new FileOutputStream(partial,true);
                    output.write(data);
                }catch(Exception e){Log.e(TAG,"Caching packet failed",e);stopped=true;}
            });
        }
        void cancel(){
            synchronized(this){stopped=true;}
            DISK.execute(()->{close();partial.delete();ready.delete();metadata.delete();});
        }
        void complete(List<EdgeWordTimings.Boundary> words,OnReady callback){
            final long required;
            synchronized(this){required=expected;stopped=true;}
            DISK.execute(()->{
                try{
                    close();
                    if(!partial.isFile()||partial.length()!=required||required<1500||!mp3(partial))
                        throw new Exception("Truncated or invalid MP3 stream");
                    JSONObject record=new JSONObject();record.put("key",key);record.put("text",text);
                    JSONArray ticks=new JSONArray();
                    for(EdgeWordTimings.Boundary w:words){
                        JSONArray row=new JSONArray();row.put(w.offsetTicks);row.put(w.durationTicks);row.put(w.spoken);ticks.put(row);
                    }
                    record.put("boundaries",ticks);
                    try(FileOutputStream out=new FileOutputStream(metadata,false)){
                        out.write(record.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
                    }
                    if(!partial.renameTo(ready))throw new Exception("Cannot seal complete stream");
                    callback.onComplete(ready,metadata,null);
                }catch(Exception e){partial.delete();ready.delete();metadata.delete();callback.onComplete(null,null,e.getMessage());}
            });
        }
        private void close(){try{if(output!=null){output.flush();output.getFD().sync();output.close();}}catch(Exception e){Log.w(TAG,"Cache close",e);}output=null;}
    }
    interface OnReady { void onComplete(File complete,File metadata,String error); }
    static Entry create(File directory,String key,String text){return new Entry(directory,key,text);}
    private static boolean mp3(File file){
        try(FileInputStream in=new FileInputStream(file)){
            int a=in.read(),b=in.read(),c=in.read();
            return (a=='I'&&b=='D'&&c=='3')||(a==255&&(b&224)==224&&((c>>1)&3)!=0);
        }catch(Exception e){return false;}
    }
    /** Completed recordings left by an interrupted commit are picked up next time. */
    static List<File> recover(File folder){
        ArrayList<File> result=new ArrayList<>();
        File cache=new File(folder,"stream-cache");File[] files=cache.listFiles();
        if(files!=null)for(File file:files){
            if(file.getName().endsWith(".ready") && file.length()>1500){
                File meta=new File(cache,file.getName().replace(".ready",".json"));
                if(meta.isFile())result.add(file);
            }else if(file.getName().endsWith(".part"))file.delete();
        }
        return result;
    }
}
