package ru.reader.rsvp;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Tiny, debounced UI snapshot, never the source of truth for completed downloads. */
final class DownloadProgressStore {
    private static final ScheduledExecutorService IO=Executors.newSingleThreadScheduledExecutor(r->{
        Thread thread=new Thread(r,"DownloadProgressCheckpoint");thread.setDaemon(true);return thread;
    });
    private static final Map<String,DownloadProgressTracker.Summary> pending=new HashMap<>();
    private static boolean scheduled;
    private DownloadProgressStore(){}
    static void changed(Context context,DownloadProgressTracker.Summary value,boolean important){
        if(value==null||!value.hasPlan)return;
        Context app=context.getApplicationContext();
        synchronized(pending){pending.put(value.downloadId,value);
            if(!scheduled){scheduled=true;IO.schedule(()->flush(app),important?0:4,TimeUnit.SECONDS);}
            else if(important){IO.execute(()->flush(app));}
        }
    }
    private static void flush(Context app){
        Map<String,DownloadProgressTracker.Summary> batch;
        synchronized(pending){batch=new HashMap<>(pending);pending.clear();scheduled=false;}
        for(DownloadProgressTracker.Summary s:batch.values()){
            try{
                JSONObject o=new JSONObject();o.put("schema",1);o.put("downloadId",s.downloadId);
                o.put("revision",s.revision);o.put("plannedChapters",s.chapters);
                o.put("completedChapters",s.finished);o.put("weightedDone",s.weightedDone);
                o.put("weightedTotal",s.weightedTotal);o.put("activeChapterId",s.activeChapter);
                o.put("overallPercent",s.percent);o.put("updatedAt",System.currentTimeMillis());
                AtomicFile target=new AtomicFile(file(app,s.downloadId));
                FileOutputStream out=null;
                try{out=target.startWrite();out.write(o.toString().getBytes(StandardCharsets.UTF_8));target.finishWrite(out);}
                catch(Exception error){if(out!=null)target.failWrite(out);throw error;}
            }catch(Exception problem){android.util.Log.w("DownloadProgress","Checkpoint unavailable",problem);}
        }
    }
    static JSONObject read(Context c,String id){
        try{
            AtomicFile target=new AtomicFile(file(c,id));
            if(!target.getBaseFile().isFile())return null;
            try(InputStream in=target.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[4096];int n;
                while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>16384)return null;}
                JSONObject data=new JSONObject(out.toString("UTF-8"));
                return data.optInt("schema")==1&&id.equals(data.optString("downloadId"))?data:null;
            }
        }catch(Exception ignored){return null;}
    }
    private static File file(Context context,String id)throws Exception{
        File dir=new File(context.getFilesDir(),"download_progress_v176");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new java.io.IOException("No progress cache directory");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] bytes=digest.digest(id.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex=new StringBuilder();for(byte v:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
        return new File(dir,hex.toString()+".json");
    }
}
