package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Read-only projection of two INDEPENDENT downloader stores. Never copies downloaded MP3. */
final class DownloadsRepository {
    static final String AUDIO="audiobook",EDGE="edge",LINK="direct_link";
    static final String HIDDEN_EDGE="hidden_edge_downloads_v1";
    static final class Item {
        String type="",id="",title="",cover="",source="",bookUri="",status="",description="";
        String voice="";
        int done,total;
        long downloadedBytes,totalBytes;
        boolean ready,running,paused,failed,unknownTotal;
        File folder;
        BookImportManager.Job job;
        LinkDownloadStore.Batch link;
        EdgeFullBookStore.Job fullJob;
    }
    private DownloadsRepository(){}
    static void storeEdgeMeta(File bookDir,String bookUri,String title,int words){
        try{
            if(bookDir==null||!bookDir.isDirectory())return;
            JSONObject data=new JSONObject();data.put("bookUri",bookUri);data.put("title",title);
            data.put("words",words);data.put("savedAt",System.currentTimeMillis());
            File tmp=new File(bookDir,"book.json.part");
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(data.toString().getBytes(StandardCharsets.UTF_8));out.flush();}
            File file=new File(bookDir,"book.json");if(file.exists())file.delete();tmp.renameTo(file);
        }catch(Exception ignored){}
    }
    private static JSONObject readJson(File f){try{
        if(!f.isFile()||f.length()>4*1024*1024L||f.length()<2)return null;
        byte[] bytes=new byte[(int)f.length()];int offset=0;
        try(FileInputStream in=new FileInputStream(f)){int n;while(offset<bytes.length&&(n=in.read(bytes,offset,bytes.length-offset))>0)offset+=n;}
        return new JSONObject(new String(bytes,0,offset,StandardCharsets.UTF_8));
    }catch(Exception ignored){return null;}}
    private static JSONArray readArray(File f){try{
        if(!f.isFile()||f.length()>4*1024*1024L)return new JSONArray();
        byte[] data=new byte[(int)f.length()];int count=0;
        try(FileInputStream in=new FileInputStream(f)){int n;while(count<data.length&&(n=in.read(data,count,data.length-count))>0)count+=n;}
        return new JSONArray(new String(data,0,count,StandardCharsets.UTF_8));
    }catch(Exception ignored){return new JSONArray();}}
    private static Item fromAudio(Context context,BookImportManager.Job job){
        Item item=new Item();item.type=AUDIO;item.job=job;item.id="audio:"+job.source;
        item.source=job.source;item.title=job.title.isEmpty()?"Аудиокнига":job.title;
        String localCover=AudiobookCoverStore.localUri(context,job);
        item.cover=localCover.isEmpty()?job.cover:localCover;
        item.folder=BookImportManager.directory(context,job);
        item.paused=job.paused||"paused".equals(job.status);
        item.failed="error".equals(job.status);
        item.running="running".equals(job.status)&&!job.paused;
        for(int i=0;i<job.states.size();i++){
            int state=job.states.get(i);if(state<0)continue;
            item.total++;
            File file=BookImportManager.trackFile(context,job,i);
            if(state==100&&file.isFile()&&file.length()>1024){item.done++;item.downloadedBytes+=file.length();}
            else if(file.isFile())item.downloadedBytes+=file.length();
        }
        item.ready=item.total>0&&item.done==item.total;
        if(item.ready){item.running=false;item.status="Скачано";}
        else if(item.failed)item.status="Ошибка";
        else if(item.running)item.status="Скачивается";
        else if(item.paused)item.status="Приостановлено";
        else if(job.cancelled)item.status="Отменено";
        else item.status="Ожидает";
        item.description="Глав: "+item.done+" из "+item.total;
        if(item.downloadedBytes>0)item.description+=" · "+String.format(Locale.ROOT,"%.1f МБ",item.downloadedBytes/1048576.0);
        return item;
    }
    private static Item fromEdge(File bookDir,String uri,String title,int totalWords,EdgeDownloadService live){
        File audio=new File(bookDir,"EdgeTTS");
        JSONArray queue=readArray(new File(audio,"queue.json"));
        EdgeFullBookStore.Job full=EdgeFullBookStore.read(bookDir);
        if(queue.length()==0 && full==null && (live==null||!uri.equals(live.bookUri())||live.snapshot().isEmpty()))return null;
        Item item=new Item();item.type=EDGE;item.bookUri=uri;item.id="edge:"+uri;
        item.title=title.isEmpty()?"Текстовая книга":title;item.cover=uri;item.folder=audio;
        Set<String> keys=new HashSet<>();boolean allReady=true;int contiguous=0;
        for(int i=0;i<queue.length();i++){
            JSONObject clip=queue.optJSONObject(i);if(clip==null)continue;
            String key=clip.optString("key");int start=clip.optInt("start",-1),end=clip.optInt("end",-1);
            if(start<0||end<=start||!key.matches("[a-fA-F0-9]{64}")||!keys.add(key+":"+start))continue;
            item.total++;
            File file=new File(audio,key+".mp3");
            boolean good=file.isFile()&&file.length()>1500;
            if(good){item.done++;item.downloadedBytes+=file.length();}else allReady=false;
            if(start==contiguous&&good)contiguous=end;
        }
        // If the new file is not yet persisted, display the in-memory queue as well.
        if(live!=null&&uri.equals(live.bookUri())){
            int ready=live.countReady();
            if(ready>item.done)item.done=ready;
            item.total=Math.max(item.total,live.snapshot().size());
            item.running=live.isActive();item.voice=live.voice();
            if(item.done>0&&item.downloadedBytes==0)for(EdgeDownloadService.Clip c:live.snapshot())if(c.ready())item.downloadedBytes+=c.bytes;
        }
        item.ready=totalWords>0&&contiguous>=totalWords&&allReady&&item.total>0;
        item.unknownTotal=!item.ready;
        if(full!=null){
            item.fullJob=full;item.voice=full.voice;
            // The service updates durable counts after each completed fragment.
            // Avoid hundreds of file stats and parsing a large manifest on every UI refresh.
            item.total=full.total;
            item.done=Math.min(item.total,Math.max(0,full.done));
            item.ready=item.total>0&&item.done==item.total;
            item.running=EdgeFullBookService.isServiceAlive()&&(
                EdgeFullBookStore.RUNNING.equals(full.status)||EdgeFullBookStore.QUEUED.equals(full.status));
            item.paused=EdgeFullBookStore.PAUSED.equals(full.status)
                ||(!EdgeFullBookService.isServiceAlive()&&EdgeFullBookStore.RUNNING.equals(full.status));
            item.failed=EdgeFullBookStore.FAILED.equals(full.status);
            item.unknownTotal=item.total==0;
            if(item.ready)item.status="Подготовлено полностью";
            else if(item.failed)item.status="Ошибка подготовки";
            else if(item.paused)item.status="Приостановлено";
            else if(item.running)item.status="Готовится вся книга";
            else item.status="Частично подготовлено";
            item.description="Подготовлено "+item.done+" из "+item.total+" фрагментов";
            if(item.failed&&!full.error.isEmpty())item.description+=" · "+full.error;
            return item;
        }
        item.status=item.ready?"Подготовлено":item.running?"Скачивается":item.done>0?"Частично подготовлено":"Ожидает";
        item.description="Подготовлено "+item.done+" фрагментов";
        if(item.total>item.done)item.description+=" · в очереди "+item.total;
        if(item.voice.isEmpty())item.voice="";
        return item;
    }
    private static Item fromLink(LinkDownloadStore.Batch b){
        Item item=new Item();item.type=LINK;item.id="link:"+b.id;item.link=b;
        item.title=b.title;item.status="Скачивание по ссылке";item.total=b.files.size();item.done=b.completed();
        item.ready=item.total>0&&item.done==item.total;
        item.running="running".equals(b.status);item.paused="paused".equals(b.status);
        item.failed="error".equals(b.status);item.unknownTotal=true;
        if(item.ready)item.status="Скачано";else if(item.failed)item.status="Ошибка";
        else if(item.running)item.status="Скачивается";else if(item.paused)item.status="Приостановлено";
        else if("cancelled".equals(b.status))item.status="Отменено";else item.status="Ожидает";
        item.description="Файлов: "+item.done+" из "+item.total+" · "+b.treeLabel+(b.subfolder.isEmpty()?"":" / "+b.subfolder);
        for(LinkDownloadStore.Entry e:b.files)item.downloadedBytes+=e.bytes;
        return item;
    }
    static ArrayList<Item> load(Context ctx,EdgeDownloadService live){
        ArrayList<Item> all=new ArrayList<>();
        for(BookImportManager.Job j:BookImportManager.load(ctx))all.add(fromAudio(ctx,j));
        for(LinkDownloadStore.Batch b:LinkDownloadStore.load(ctx))all.add(fromLink(b));
        HashMap<String,JSONObject> index=new HashMap<>();
        try{
            JSONArray books=new JSONArray(ctx.getSharedPreferences("reader",0).getString("library","[]"));
            for(int i=0;i<books.length();i++){
                JSONObject book=books.optJSONObject(i);if(book!=null&&!book.optString("uri").isEmpty())index.put(book.optString("uri"),book);
            }
        }catch(Exception ignored){}
        SharedPreferences pref=ctx.getSharedPreferences("reader",0);
        Set<String> hidden=pref.getStringSet(HIDDEN_EDGE,java.util.Collections.emptySet());
        File root=EdgeBookStorage.booksRoot(ctx);
        File[] dirs=root.listFiles(f->f.isDirectory());Set<String> included=new HashSet<>();
        if(dirs!=null)for(File dir:dirs){
            JSONObject meta=readJson(new File(dir,"book.json"));
            if(meta==null)continue;
            String uri=meta.optString("bookUri","");if(uri.isEmpty()||hidden.contains(uri)||!included.add(uri))continue;
            Item item=fromEdge(dir,uri,meta.optString("title",""),meta.optInt("words",0),live);
            if(item!=null)all.add(item);
        }
        // Migration: older versions only stored Edge queue.json and a MainActivity library entry.
        for(JSONObject book:index.values()){
            String uri=book.optString("uri","");if(uri.isEmpty()||included.contains(uri)||hidden.contains(uri))continue;
            String title=book.optString("title","Книга");
            File dir=EdgeBookStorage.bookFolder(ctx,uri,title);
            Item item=fromEdge(dir,uri,title,0,live);
            if(item!=null){included.add(uri);all.add(item);}
        }
        all.sort((a,b)->{
            if(a.ready!=b.ready)return a.ready?1:-1;
            long left=a.folder==null?0:a.folder.lastModified();
            long right=b.folder==null?0:b.folder.lastModified();
            return Long.compare(right,left);
        });
        return all;
    }
    static void hideEdge(Context ctx,String uri){
        SharedPreferences prefs=ctx.getSharedPreferences("reader",0);
        Set<String> values=new HashSet<>(prefs.getStringSet(HIDDEN_EDGE,java.util.Collections.emptySet()));
        values.add(uri);prefs.edit().putStringSet(HIDDEN_EDGE,values).apply();
    }
    static void showEdge(Context ctx,String uri){
        SharedPreferences prefs=ctx.getSharedPreferences("reader",0);
        Set<String> values=new HashSet<>(prefs.getStringSet(HIDDEN_EDGE,java.util.Collections.emptySet()));
        if(values.remove(uri))prefs.edit().putStringSet(HIDDEN_EDGE,values).apply();
    }
}
