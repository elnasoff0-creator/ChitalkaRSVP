package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/** Durable full-book jobs, stored beside the legacy Edge MP3 cache, not in its queue.json. */
final class EdgeFullBookStore {
    static final String QUEUED="queued",RUNNING="running",PAUSED="paused",DONE="completed",FAILED="error";
    static final class Job {
        String uri="",title="",voice="",rate="+0%",status=QUEUED,error="";
        int chars=EdgeSpeechSegments.MAX,done,total,chapter=-1;
        long modified;
        File directory;
        File mp3Folder(){return new File(directory,"EdgeTTS");}
    }
    private EdgeFullBookStore(){}
    static Job create(Context ctx,String uri,String title,String voice,String rate,int chars){
        Job j=new Job();j.uri=uri;j.title=title;j.voice=voice;j.rate=rate;j.chars=chars;
        j.directory=EdgeBookStorage.bookFolder(ctx,uri,title);
        j.directory.mkdirs();j.mp3Folder().mkdirs();
        DownloadsRepository.storeEdgeMeta(j.directory,uri,title,0);
        save(j);DownloadsRepository.showEdge(ctx,uri);return j;
    }
    static synchronized void save(Job j){
        if(j==null||j.directory==null)return;
        try{
            JSONObject o=new JSONObject();o.put("uri",j.uri).put("title",j.title)
                .put("voice",j.voice).put("rate",j.rate).put("chars",j.chars)
                .put("status",j.status).put("chapter",j.chapter).put("done",j.done).put("total",j.total)
                .put("error",j.error).put("modified",System.currentTimeMillis());
            atomic(new File(j.directory,"full_job.json"),o.toString());
        }catch(Exception ignored){}
    }
    static synchronized Job read(File bookFolder){
        try{
            File f=new File(bookFolder,"full_job.json");
            JSONObject o=new JSONObject(readText(f,8192));
            Job j=new Job();j.directory=bookFolder;j.uri=o.optString("uri","");
            if(j.uri.isEmpty())return null;
            j.title=o.optString("title","Книга");j.voice=o.optString("voice","ru-RU-SvetlanaNeural");
            j.rate=o.optString("rate","+0%");j.chars=o.optInt("chars",EdgeSpeechSegments.MAX);
            j.chapter=o.optInt("chapter",-1);j.status=o.optString("status",PAUSED);j.done=o.optInt("done",0);j.total=o.optInt("total",0);
            j.error=o.optString("error","");j.modified=o.optLong("modified",0);return j;
        }catch(Exception ignored){return null;}
    }
    static synchronized Job find(Context ctx,String uri,String title){return read(EdgeBookStorage.bookFolder(ctx,uri,title));}
    static ArrayList<Job> list(Context ctx){
        ArrayList<Job> result=new ArrayList<>();
        File[] dirs=EdgeBookStorage.booksRoot(ctx).listFiles(File::isDirectory);
        if(dirs!=null)for(File f:dirs){Job j=read(f);if(j!=null)result.add(j);}
        result.sort((a,b)->Long.compare(b.modified,a.modified));return result;
    }
    static synchronized void chooseChapter(Job j,int chapter){
        Job current=read(j.directory);if(current==null)return;
        current.chapter=chapter;current.status=QUEUED;current.error="";
        save(current);
    }
    static synchronized void status(Job j,String status){
        Job old=read(j.directory);if(old==null)return;
        old.status=status;if(QUEUED.equals(status)||RUNNING.equals(status))old.error="";save(old);
    }
    static boolean running(Job j){Job current=read(j.directory);return current!=null&&RUNNING.equals(current.status);}
    static synchronized void progress(Job j,int done,int total,String error){
        Job old=read(j.directory);if(old==null)return;
        old.done=done;old.total=total;old.error=error==null?"":error;
        save(old);
    }
    static boolean ready(File file){return file.isFile()&&file.length()>1500;}
    static int readyCount(Job job){
        int result=0;
        try{
            JSONArray parts=queue(job);
            for(int i=0;i<parts.length();i++){
                JSONObject row=parts.optJSONObject(i);if(row==null)continue;
                String key=row.optString("key","");
                if(key.matches("[a-f0-9]{64}")&&ready(new File(job.mp3Folder(),key+".mp3")))result++;
            }
        }catch(Exception ignored){}
        return result;
    }
    static JSONArray queue(Job job){
        try{return new JSONArray(readText(new File(job.mp3Folder(),"full_queue.json"),24*1024*1024));}
        catch(Exception ignored){return new JSONArray();}
    }
    static synchronized void writeQueue(Job job,JSONArray data) throws Exception {
        job.mp3Folder().mkdirs();atomic(new File(job.mp3Folder(),"full_queue.json"),data.toString());
    }
    private static String readText(File file,int limit) throws Exception {
        if(!file.isFile()||file.length()<2||file.length()>limit)throw new Exception("No valid index");
        byte[] out=new byte[(int)file.length()];int n=0;
        try(FileInputStream in=new FileInputStream(file)){int v;while(n<out.length&&(v=in.read(out,n,out.length-n))>0)n+=v;}
        return new String(out,0,n,StandardCharsets.UTF_8);
    }
    private static void atomic(File dest,String contents) throws Exception {
        File part=new File(dest.getAbsolutePath()+".writing");
        try(FileOutputStream out=new FileOutputStream(part,false)){
            out.write(contents.getBytes(StandardCharsets.UTF_8));out.getFD().sync();
        }
        if(!part.renameTo(dest)){
            if(dest.isFile()&&!dest.delete())throw new Exception("Cannot replace index");
            if(!part.renameTo(dest))throw new Exception("Cannot save index");
        }
    }
}
