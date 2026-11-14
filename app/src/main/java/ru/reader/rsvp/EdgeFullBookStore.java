package ru.reader.rsvp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Durable per-chapter queue for background Edge TTS. Only metadata lives in
 * filesDir/EdgeJobs; synthesized audio remains in the disposable audio cache
 * until each finished chapter is published to the user's SAF folder.
 */
final class EdgeFullBookStore {
    static final String QUEUED="queued", RUNNING="running", PAUSED="paused",
        DONE="completed", FAILED="error", WAITING="waiting", SAVING="saving";
    static final int PLAN=-2; // plan all missing chapters, never synthesize as a chapter

    static final class Task {
        int chapter,done,total;
        String status=QUEUED,error="";
        boolean force;
        long order;
    }
    static final class Job {
        String uri="",title="",voice="",rate="+0%",status=PAUSED,error="";
        int chars=EdgeSpeechSegments.MAX,done,total,chapter=PLAN;
        boolean forceReload,autoContinue;
        long modified;
        File directory,mp3Dir;
        final ArrayList<Task> tasks=new ArrayList<>();
        File mp3Folder(){return mp3Dir!=null?mp3Dir:new File(directory,"EdgeTTS");}
    }
    private EdgeFullBookStore(){}
    private static File jobFolder(Context ctx,String uri){
        String identity=BookUriMigration.legacy(ctx,uri);
        String stable=Integer.toHexString(identity.hashCode())+"-"+Integer.toHexString((identity+"job").hashCode());
        return new File(new File(ctx.getFilesDir(),"EdgeJobs"),stable);
    }
    static synchronized Job create(Context ctx,String uri,String title,String voice,String rate,int chars){
        Job j=read(jobFolder(ctx,uri));
        if(j==null){j=new Job();j.uri=uri;j.directory=jobFolder(ctx,uri);}
        j.title=title;j.voice=voice;j.rate=rate;j.chars=chars;
        j.mp3Dir=new File(EdgeBookStorage.bookFolder(ctx,uri,title),"EdgeTTS");
        j.directory.mkdirs();j.mp3Folder().mkdirs();
        DownloadsRepository.storeEdgeMeta(EdgeBookStorage.bookFolder(ctx,uri,title),uri,title,0);
        save(j);DownloadsRepository.showEdge(ctx,uri);return j;
    }
    static synchronized Job read(File folder){
        try{
            JSONObject o=new JSONObject(readText(new File(folder,"full_job.json"),1024*1024));
            Job j=new Job();j.directory=folder;j.uri=o.optString("uri","");
            if(j.uri.isEmpty())return null;
            j.title=o.optString("title","Книга");j.voice=o.optString("voice","ru-RU-SvetlanaNeural");
            j.rate=o.optString("rate","+0%");j.chars=o.optInt("chars",EdgeSpeechSegments.MAX);
            String work=o.optString("mp3Dir","");if(!work.isEmpty())j.mp3Dir=new File(work);
            j.autoContinue=o.optBoolean("autoContinue",false);
            j.modified=o.optLong("modified",0);
            JSONArray list=o.optJSONArray("tasks");
            if(list!=null)for(int i=0;i<list.length();i++){
                JSONObject t=list.optJSONObject(i);if(t==null)continue;
                Task item=new Task();item.chapter=t.optInt("chapter",PLAN);
                item.status=t.optString("status",PAUSED);item.error=t.optString("error","");
                item.done=t.optInt("done",0);item.total=t.optInt("total",0);
                item.force=t.optBoolean("force",false);item.order=t.optLong("order",i+1);
                // A dead process may leave RUNNING, WAITING or SAVING behind.
                // These are resumable, not forgotten or accidentally retried twice.
                if((RUNNING.equals(item.status)||WAITING.equals(item.status)||SAVING.equals(item.status))
                    &&!EdgeFullBookService.isServiceAlive())item.status=PAUSED;
                j.tasks.add(item);
            }
            else {
                // Migrate v151 single-chapter job, preserving its intent.
                Task old=new Task();old.chapter=o.optInt("chapter",-1);
                old.status=o.optString("status",PAUSED);
                if(RUNNING.equals(old.status))old.status=PAUSED;
                old.force=o.optBoolean("forceReload",false);old.done=o.optInt("done",0);
                old.total=o.optInt("total",0);old.error=o.optString("error","");
                old.order=o.optLong("modified",System.currentTimeMillis());
                if(old.chapter==-1){old.chapter=PLAN;j.autoContinue=true;}
                j.tasks.add(old);
            }
            summarize(j);return j;
        }catch(Exception ignored){return null;}
    }
    private static Task findTask(Job j,int chapter){
        if(j==null)return null;
        for(Task t:j.tasks)if(t.chapter==chapter)return t;
        return null;
    }
    static Task task(Job j,int chapter){return findTask(j,chapter);}
    static synchronized Job find(Context ctx,String uri,String title){return read(jobFolder(ctx,uri));}
    static synchronized ArrayList<Job> list(Context ctx){
        ArrayList<Job> result=new ArrayList<>();
        File[] dirs=new File(ctx.getFilesDir(),"EdgeJobs").listFiles(File::isDirectory);
        if(dirs!=null)for(File f:dirs){Job j=read(f);if(j!=null)result.add(j);}
        result.sort((a,b)->Long.compare(b.modified,a.modified));return result;
    }
    private static void summarize(Job j){
        j.done=0;j.total=0;j.error="";j.status=DONE;j.chapter=PLAN;
        boolean queued=false,run=false,paused=false,failed=false,waiting=false;
        Task current=null;
        for(Task t:j.tasks){
            if(t.chapter!=PLAN){j.done+=Math.max(0,t.done);j.total+=Math.max(0,t.total);}
            if(RUNNING.equals(t.status)||SAVING.equals(t.status)){run=true;current=t;}
            else if(WAITING.equals(t.status)){waiting=true;if(current==null)current=t;}
            else if(QUEUED.equals(t.status)){queued=true;if(current==null)current=t;}
            else if(PAUSED.equals(t.status))paused=true;
            else if(FAILED.equals(t.status)){failed=true;if(j.error.isEmpty())j.error=t.error;}
        }
        if(run)j.status=RUNNING;
        else if(waiting)j.status=WAITING;
        else if(queued)j.status=QUEUED;
        else if(paused)j.status=PAUSED;
        else if(failed)j.status=FAILED;
        j.chapter=current==null?PLAN:current.chapter;
        if(current!=null)j.forceReload=current.force;
    }
    static synchronized void save(Job j){
        if(j==null||j.directory==null)return;
        try{
            JSONObject o=new JSONObject();o.put("uri",j.uri).put("title",j.title)
                .put("voice",j.voice).put("rate",j.rate).put("chars",j.chars)
                .put("mp3Dir",j.mp3Folder().getAbsolutePath()).put("autoContinue",j.autoContinue)
                .put("modified",System.currentTimeMillis());
            JSONArray a=new JSONArray();for(Task t:j.tasks){
                JSONObject row=new JSONObject();row.put("chapter",t.chapter).put("status",t.status)
                    .put("error",t.error).put("done",t.done).put("total",t.total)
                    .put("force",t.force).put("order",t.order);a.put(row);
            }o.put("tasks",a);
            j.directory.mkdirs();atomic(new File(j.directory,"full_job.json"),o.toString());
        }catch(Exception ignored){}
    }
    static synchronized void recoverInterrupted(Context ctx){
        // Read the RAW persisted statuses here, not read(), which renders a
        // stale running state as paused when no service is active.
        File[] dirs=new File(ctx.getFilesDir(),"EdgeJobs").listFiles(File::isDirectory);
        if(dirs==null)return;
        for(File dir:dirs)try{
            File index=new File(dir,"full_job.json");
            JSONObject data=new JSONObject(readText(index,1024*1024));
            JSONArray tasks=data.optJSONArray("tasks");if(tasks==null)continue;
            boolean modified=false;
            for(int i=0;i<tasks.length();i++){
                JSONObject t=tasks.optJSONObject(i);if(t==null)continue;
                String state=t.optString("status",PAUSED);
                if(RUNNING.equals(state)||WAITING.equals(state)||SAVING.equals(state)){
                    t.put("status",QUEUED);modified=true;
                }
            }
            if(modified)atomic(index,data.toString());
        }catch(Exception ignored){}
    }
    static synchronized void chooseChapter(Job j,int chapter){chooseChapter(j,chapter,false);}
    static synchronized void chooseChapter(Job j,int chapter,boolean force){
        Job current=read(j.directory);if(current==null)return;
        if(chapter==-1){current.autoContinue=true;chapter=PLAN;}
        Task task=findTask(current,chapter);
        if(task==null){task=new Task();task.chapter=chapter;task.order=System.currentTimeMillis();current.tasks.add(task);}
        else if(!force&&(QUEUED.equals(task.status)||RUNNING.equals(task.status)||WAITING.equals(task.status)||SAVING.equals(task.status))){
            save(current);return; // identical chapter is already pending
        }
        task.status=QUEUED;task.error="";task.force=force;
        task.done=0;task.total=0;
        save(current);
    }
    /** Continue is not just retry of the last task: it enables the all-chapter planner. */
    static synchronized void resumeAll(Job j){
        Job current=read(j.directory);if(current==null)return;
        current.autoContinue=true;
        for(Task t:current.tasks){
            if(PAUSED.equals(t.status)||WAITING.equals(t.status)||FAILED.equals(t.status)){
                t.status=QUEUED;t.error="";
            }
        }
        Task planner=findTask(current,PLAN);
        if(planner==null){planner=new Task();planner.chapter=PLAN;planner.order=System.currentTimeMillis();current.tasks.add(planner);}
        planner.status=QUEUED;planner.error="";save(current);
    }
    static synchronized void pauseAll(Job j){
        Job current=read(j.directory);if(current==null)return;
        for(Task t:current.tasks)if(QUEUED.equals(t.status)||RUNNING.equals(t.status)||WAITING.equals(t.status)||SAVING.equals(t.status))t.status=PAUSED;
        save(current);
    }
    static synchronized void pauseRunning(Job j){
        Job current=read(j.directory);if(current==null)return;
        for(Task t:current.tasks)if(RUNNING.equals(t.status)||WAITING.equals(t.status)||SAVING.equals(t.status))t.status=PAUSED;
        save(current);
    }
    static synchronized void forgetChapter(Context ctx,String uri,String title,int chapter){
        Job current=find(ctx,uri,title);if(current==null)return;
        current.tasks.removeIf(t->t.chapter==chapter);save(current);
    }
    static synchronized void stopChapter(Context ctx,String uri,String title,int chapter){
        Job current=find(ctx,uri,title);if(current==null)return;
        Task t=findTask(current,chapter);if(t!=null){t.status=PAUSED;save(current);}
    }
    static synchronized void status(Job job,String state){
        Job current=read(job.directory);if(current==null)return;
        Task t=findTask(current,job.chapter);if(t==null)return;
        t.status=state;if(QUEUED.equals(state)||RUNNING.equals(state))t.error="";
        save(current);
    }
    static synchronized boolean running(Job job){
        Job current=read(job.directory);Task t=findTask(current,job.chapter);
        return t!=null&&(RUNNING.equals(t.status)||WAITING.equals(t.status)||SAVING.equals(t.status));
    }
    static synchronized void progress(Job job,int done,int total,String error){
        Job current=read(job.directory);Task t=findTask(current,job.chapter);if(t==null)return;
        t.done=Math.max(0,done);t.total=Math.max(0,total);t.error=error==null?"":error;
        save(current);
    }
    /** A finished SAF publication marks only the chosen chapter as complete. */
    static synchronized void markPublished(Job job,int chapter){
        if(job==null)return;
        Job current=read(job.directory);Task task=findTask(current,chapter);
        if(task!=null){task.done=task.total;task.error="";task.status=DONE;save(current);}
    }
    static synchronized void clearForce(Job job){
        Job current=read(job.directory);Task t=findTask(current,job.chapter);
        if(t!=null){t.force=false;save(current);}
    }
    static synchronized Job next(Context ctx){
        Job result=null;Task first=null;
        for(Job j:list(ctx))for(Task t:j.tasks)if(QUEUED.equals(t.status)){
            // A planner is processed before queued chapters in the same book,
            // but only to add missing work (never to replace user selections).
            if(first==null||(t.chapter==PLAN&&first.chapter!=PLAN)||
                (t.chapter==first.chapter&&t.order<first.order)||
                (t.chapter!=PLAN&&first.chapter!=PLAN&&t.order<first.order)){
                first=t;result=j;
            }
        }
        if(result!=null&&first!=null){result.chapter=first.chapter;result.forceReload=first.force;}
        return result;
    }
    static synchronized void planMissing(Job job,BookChapterIndex toc,EdgeChapterArchive.ChapterSnapshot saved){
        Job current=read(job.directory);if(current==null)return;
        Task plan=findTask(current,PLAN);if(plan!=null)plan.status=DONE;
        if(!current.autoContinue){save(current);return;}
        int size=toc.size();
        for(int chapter=0;chapter<Math.max(1,size);chapter++){
            int number=size==0?-1:chapter;
            if(number>=0&&saved.state(number)>0){
                Task ready=findTask(current,number);
                if(ready!=null&&!ready.force&&(QUEUED.equals(ready.status)||PAUSED.equals(ready.status))){
                    ready.status=DONE;ready.error="";
                }
                continue;
            }
            Task existing=findTask(current,number);
            if(existing!=null){
                if(FAILED.equals(existing.status)||DONE.equals(existing.status)||
                    PAUSED.equals(existing.status)){
                    // A user explicitly requested 'Continue' or 'Prepare all'.
                    // Retry unsaved chapters once, not in a background error loop.
                    existing.status=QUEUED;existing.error="";
                    existing.done=0;existing.total=0;
                }
                continue;
            }
            Task t=new Task();t.chapter=number;t.status=QUEUED;
            t.order=System.currentTimeMillis()+chapter+1;current.tasks.add(t);
        }
        save(current);
    }
    static boolean ready(File f){return f.isFile()&&f.length()>1500;}
    static int readyCount(Job job){
        int result=0;try{JSONArray arr=queue(job);
            for(int i=0;i<arr.length();i++){
                JSONObject row=arr.optJSONObject(i);if(row==null)continue;
                String k=row.optString("key","");
                if(k.matches("[a-f0-9]{64}")&&ready(new File(job.mp3Folder(),k+".mp3")))result++;
            }
        }catch(Exception ignored){}return result;
    }
    static JSONArray queue(Job job){try{
        return new JSONArray(readText(new File(job.mp3Folder(),"full_queue.json"),24*1024*1024));
    }catch(Exception ignored){return new JSONArray();}}
    static synchronized void writeQueue(Job job,JSONArray data)throws Exception{
        job.mp3Folder().mkdirs();atomic(new File(job.mp3Folder(),"full_queue.json"),data.toString());
    }
    private static String readText(File file,int limit)throws Exception{
        if(!file.isFile()||file.length()<2||file.length()>limit)throw new Exception("No valid index");
        byte[] out=new byte[(int)file.length()];int n=0;
        try(FileInputStream in=new FileInputStream(file)){
            int v;while(n<out.length&&(v=in.read(out,n,out.length-n))>0)n+=v;
        }return new String(out,0,n,StandardCharsets.UTF_8);
    }
    private static void atomic(File dest,String contents)throws Exception{
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
