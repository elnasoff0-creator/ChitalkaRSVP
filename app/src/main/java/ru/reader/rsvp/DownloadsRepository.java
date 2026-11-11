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
    static final String AUDIO="audiobook",EDGE="edge",LINK="direct_link",MANGA="mangalib";
    static final String HIDDEN_EDGE="hidden_edge_downloads_v1";
    static final class Item {
        String type="",id="",title="",cover="",source="",bookUri="",status="",description="";
        String voice="";
        int done,total,coveragePercent;
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
            JSONObject existing=readJson(new File(bookDir,"book.json"));
            if(words<=0 && existing!=null && bookUri.equals(existing.optString("bookUri")))
                words=existing.optInt("words",0);
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
            if(state==100&&!BookImportManager.relocated(job,i).isEmpty()){item.done++;}
            else if(state==100&&file.isFile()&&file.length()>1024){item.done++;item.downloadedBytes+=file.length();}
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
    private static Item fromEdge(Context ctx,File bookDir,String uri,String title,int totalWords,EdgeStreamingService live){
        File audio=new File(bookDir,"EdgeTTS");
        EdgeFullBookStore.Job full=EdgeFullBookStore.find(ctx,uri,title);
        ArrayList<EdgeOfflineIndex.Segment> saved=EdgeAudioManager.segments(audio);
        if(saved.isEmpty()&&full==null&&(live==null||!uri.equals(live.bookUri())||live.snapshot().isEmpty()))return null;
        Item item=new Item();item.type=EDGE;item.bookUri=uri;item.id="edge:"+uri;
        item.title=title.isEmpty()?"Текстовая книга":title;item.cover=uri;item.folder=audio;
        EdgeOfflineIndex.Stats coverage=EdgeOfflineIndex.stats(saved,0,Math.max(0,totalWords));
        item.done=coverage.files;
        item.total=saved.size();
        item.coveragePercent=coverage.percent();
        item.downloadedBytes=coverage.bytes;
        item.ready=coverage.complete();item.unknownTotal=totalWords<=0;
        if(live!=null&&uri.equals(live.bookUri())){
            item.running=live.isActive();item.voice=live.voice();
            if(totalWords<=0)item.total=Math.max(item.total,live.snapshot().size());
        }
        if(full!=null){
            item.fullJob=full;item.voice=full.voice;
            // The service updates durable counts after each completed fragment.
            // Avoid hundreds of file stats and parsing a large manifest on every UI refresh.
            item.total=full.total;
            item.done=Math.min(item.total,Math.max(0,full.done));
            item.ready=coverage.complete();
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
            item.description="Сохранено "+coverage.percent()+"% текста · "+coverage.files+" MP3 · "+String.format(Locale.ROOT,"%.1f МБ",coverage.bytes/1048576.0)+" · подготовка "+item.done+"/"+item.total;
            if(item.failed&&!full.error.isEmpty())item.description+=" · "+full.error;
            return item;
        }
        item.status=item.ready?"Подготовлено":item.running?"Скачивается":item.done>0?"Частично подготовлено":"Ожидает";
        item.description="Сохранено "+coverage.percent()+"% текста · "+coverage.files+" MP3 · "+String.format(Locale.ROOT,"%.1f МБ",coverage.bytes/1048576.0);
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
    static ArrayList<Item> load(Context ctx,EdgeStreamingService live){
        ArrayList<Item> all=new ArrayList<>();
        for(BookImportManager.Job j:BookImportManager.load(ctx))all.add(fromAudio(ctx,j));
        for(LinkDownloadStore.Batch b:LinkDownloadStore.load(ctx))all.add(fromLink(b));
        // One download card per manga, NOT per chapter.  Existing chapter records,
        // CBZ files and retry state are unchanged; only the dashboard projection is grouped.
        // A stable MangaLib slug is the identity; the human title is the displayed label.
        java.util.LinkedHashMap<String,MangaLibDownloadGroup> mangaGroups=new java.util.LinkedHashMap<>();
        java.util.LinkedHashMap<String,Item> mangaRows=new java.util.LinkedHashMap<>();
        for(MangaLibStorage.Job j:MangaLibStorage.jobs(ctx)){
            String slug=j.slug==null?"":j.slug.trim();
            if(slug.isEmpty())continue; // malformed legacy jobs cannot be routed to a series
            String key=slug.toLowerCase(Locale.ROOT);
            MangaLibDownloadGroup group=mangaGroups.get(key);
            Item item=mangaRows.get(key);
            if(group==null){
                group=new MangaLibDownloadGroup();mangaGroups.put(key,group);
                item=new Item();item.type=MANGA;item.id="manga:"+key;
                item.title=j.series==null||j.series.trim().isEmpty()?slug:j.series;
                item.bookUri=MangaLibStorage.comicUri(slug);item.source=slug;
                mangaRows.put(key,item);
            }
            if((item.title==null||item.title.isEmpty()||item.title.equals(slug))
                &&j.series!=null&&!j.series.trim().isEmpty())item.title=j.series;
            group.add(j.volume,j.done,j.total,j.bytes,j.status);
        }
        for(String key:mangaRows.keySet()){
            Item item=mangaRows.get(key);MangaLibDownloadGroup group=mangaGroups.get(key);
            File coverFile=MangaDownloadCover.file(ctx,item.source);
            if(coverFile.isFile()&&coverFile.length()>100)item.cover=coverFile.toURI().toString();
            item.done=group.pagesDone;item.total=group.pagesKnown;
            item.unknownTotal=group.unknownChapters>0;
            // When total page count is unknown, show completion by chapters instead
            // of a misleading pages-based percentage.
            item.coveragePercent=group.chapters==0?0:100*group.finished/group.chapters;
            item.downloadedBytes=group.downloadedBytes;
            item.ready=group.complete();item.running=group.active()&&!item.ready;
            item.paused=group.paused&&!item.running;
            item.failed=group.failed&&!item.running;
            item.status=group.status();item.description=group.description();
            all.add(item);
        }
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
            Item item=fromEdge(ctx,dir,uri,meta.optString("title",""),meta.optInt("words",0),live);
            if(item!=null)all.add(item);
        }
        // Persisted jobs must remain visible after Android evicts the MP3 work cache.
        for(EdgeFullBookStore.Job persisted:EdgeFullBookStore.list(ctx)){
            if(persisted.uri.isEmpty()||included.contains(persisted.uri)||hidden.contains(persisted.uri))continue;
            File work=EdgeBookStorage.bookFolder(ctx,persisted.uri,persisted.title);
            Item restored=fromEdge(ctx,work,persisted.uri,persisted.title,0,live);
            if(restored!=null){included.add(persisted.uri);all.add(restored);}
        }
        // A completed SAF chapter must remain discoverable even after Android
        // evicts temporary audio files from our internal cache.
        for(EdgeChapterArchive.SavedBook stored:EdgeChapterArchive.savedBooks(ctx)){
            if(stored.uri.isEmpty()||hidden.contains(stored.uri))continue;
            if(included.contains(stored.uri)){
                for(Item existing:all)if(EDGE.equals(existing.type)&&stored.uri.equals(existing.bookUri)){
                    if(existing.fullJob==null||!existing.running){
                        existing.ready=stored.chapters>0;
                        if(existing.ready){existing.status="Сохранены главы Edge TTS";
                            existing.description="Готовые главы в выбранной папке: "+stored.chapters;}
                    }
                    break;
                }
                continue;
            }
            Item saved=new Item();saved.type=EDGE;saved.id="edge:"+stored.uri;
            saved.bookUri=stored.uri;saved.title=stored.title;
            saved.ready=true;saved.status="Сохранено";
            saved.done=stored.chapters;saved.total=stored.chapters;saved.coveragePercent=100;
            saved.description="Готовые MP3-главы во внешней папке: "+stored.chapters;
            all.add(saved);included.add(stored.uri);
        }
        // Migration: older versions only stored Edge queue.json and a MainActivity library entry.
        for(JSONObject book:index.values()){
            String uri=book.optString("uri","");if(uri.isEmpty()||included.contains(uri)||hidden.contains(uri))continue;
            String title=book.optString("title","Книга");
            File dir=EdgeBookStorage.bookFolder(ctx,uri,title);
            Item item=fromEdge(ctx,dir,uri,title,0,live);
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
