package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Verified SAF migration; deletes sources only after durable library references are changed. */
final class SafeDownloadTransfer {
    private SafeDownloadTransfer(){}
    static final class Result {int copied,existing,deleted;long bytes;String note="";}
    private static final class Source {
        final File file;final String uri,name;final int chapter;
        Source(File f,String n){this(f,n,-1);}
        Source(File f,String n,int ch){file=f;uri=null;name=n;chapter=ch;}
        Source(String u,String n){file=null;uri=u;name=n;chapter=-1;}
        InputStream open(Context ctx)throws IOException {
            if(file!=null)return new FileInputStream(file);
            InputStream in=ctx.getContentResolver().openInputStream(Uri.parse(uri));
            if(in==null)throw new IOException("Не удалось открыть "+name);
            return in;
        }
    }
    private static String safe(String s){
        String result=(s==null?"":s).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        if(result.length()>100)result=result.substring(0,100);
        return result.isEmpty()?"Загрузка":result;
    }
    private static void addFiles(ArrayList<Source> out,File folder,String prefix)throws IOException{
        File[] files=folder.listFiles();if(files==null)throw new IOException("Не удалось прочитать "+folder.getName());
        Arrays.sort(files,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        for(File f:files){
            if(f.isDirectory())addFiles(out,f,prefix+safe(f.getName())+"/");
            else if(f.isFile()&&!f.getName().endsWith(".part"))out.add(new Source(f,prefix+f.getName()));
        }
    }
    static Result copy(Context ctx,DownloadsRepository.Item item,Uri targetUri)throws Exception {
        if(item.running)throw new IOException("Сначала поставьте загрузку на паузу");
        DocumentFile root=DocumentFile.fromTreeUri(ctx,targetUri);
        if(root==null||!root.isDirectory()||!root.canWrite())throw new IOException("Папка недоступна для записи");
        ArrayList<Source> sources=new ArrayList<>();
        if(DownloadsRepository.AUDIO.equals(item.type)){
            if(item.job==null)throw new IOException("Аудиокнига не найдена");
            for(int i=0;i<item.job.chapters.size();i++){
                File file=BookImportManager.trackFile(ctx,item.job,i);
                if(file.isFile()&&file.length()>0)sources.add(new Source(file,file.getName(),i));
            }
        }else if(DownloadsRepository.MANGA.equals(item.type)){
            for(MangaLibStorage.Job job:MangaLibStorage.jobs(ctx)){
                if(!job.slug.equalsIgnoreCase(item.source))continue;
                File file=new File(job.file);
                if(file.isFile()&&file.getName().toLowerCase(Locale.ROOT).endsWith(".cbz"))
                    sources.add(new Source(file,"Том "+safe(job.volume)+"/"+file.getName()));
            }
        }else if(DownloadsRepository.LINK.equals(item.type)){
            LinkDownloadStore.Batch batch=LinkDownloadStore.get(ctx,item.link.id);
            if(batch==null)throw new IOException("Задание не найдено");
            for(LinkDownloadStore.Entry entry:batch.files){
                if("completed".equals(entry.status)&&!entry.savedUri.isEmpty())
                    sources.add(new Source(entry.savedUri,safe(entry.name)));
            }
        }else if(DownloadsRepository.EDGE.equals(item.type)){
            if(item.folder!=null&&item.folder.isDirectory())addFiles(sources,item.folder,"");
        }
        if(sources.isEmpty())throw new IOException("Нет завершённых файлов для переноса");
        String prefix=DownloadsRepository.MANGA.equals(item.type)?"Манга":DownloadsRepository.EDGE.equals(item.type)?"Edge TTS":DownloadsRepository.LINK.equals(item.type)?"По ссылке":"Аудиокниги";
        DocumentFile dir=directory(root,safe(prefix+" - "+item.title));
        Result result=new Result();
        java.util.ArrayList<Migration> ready=new java.util.ArrayList<>();
        for(Source source:sources){
            String[] segments=source.name.split("/");
            DocumentFile targetDir=dir;
            for(int i=0;i<segments.length-1;i++)targetDir=directory(targetDir,safe(segments[i]));
            DocumentFile destination=copyOne(ctx,source,targetDir,safe(segments[segments.length-1]),result);
            ready.add(new Migration(source,destination));
        }
        if(DownloadsRepository.AUDIO.equals(item.type))migrateAudio(ctx,item,ready,result);
        else if(DownloadsRepository.LINK.equals(item.type))migrateLinks(ctx,item,ready,result);
        else result.note="Манга и Edge TTS: копии проверены, исходники нужны движку чтения и пока оставлены.";
        return result;
    }
    private static final class Migration {
        final Source source;final DocumentFile destination;
        Migration(Source s,DocumentFile d){source=s;destination=d;}
    }
    private static void migrateAudio(Context ctx,DownloadsRepository.Item item,java.util.ArrayList<Migration> moves,Result result)throws Exception{
        BookImportManager.Job job=null;
        for(BookImportManager.Job candidate:BookImportManager.load(ctx))if(candidate.source.equals(item.source)){job=candidate;break;}
        if(job==null)throw new IOException("Задание аудиокниги изменилось; исходники сохранены");
        for(Migration m:moves){
            if(m.source.chapter<0||m.source.chapter>=job.chapters.size()||!m.source.file.isFile())
                throw new IOException("Состав глав изменился; исходники сохранены");
            while(job.relocatedUris.size()<=m.source.chapter)job.relocatedUris.add("");
            job.relocatedUris.set(m.source.chapter,m.destination.getUri().toString());
        }
        // Persist source-of-truth first, then refresh the audio library URI index.
        BookImportManager.put(ctx,job);
        BookImportManager.updateLibrary(ctx,job);
        for(Migration m:moves){
            String old=Uri.fromFile(m.source.file).toString();
            if(!referenced(ctx,old)&&m.source.file.delete())result.deleted++;
        }
    }
    private static void migrateLinks(Context ctx,DownloadsRepository.Item item,java.util.ArrayList<Migration> moves,Result result)throws Exception{
        LinkDownloadStore.Batch batch=LinkDownloadStore.get(ctx,item.link.id);
        if(batch==null)throw new IOException("Запись загрузки изменена; исходники сохранены");
        java.util.HashMap<String,String> changed=new java.util.HashMap<>();
        for(Migration m:moves)changed.put(m.source.uri,m.destination.getUri().toString());
        int matched=0;
        for(LinkDownloadStore.Entry entry:batch.files){
            String newer=changed.get(entry.savedUri);
            if(newer!=null){entry.savedUri=newer;matched++;}
        }
        if(matched!=moves.size())throw new IOException("Состав загрузки изменился; исходники сохранены");
        // Update bookmarks without resetting reading progress or replacing book identities.
        updateReaderUris(ctx,changed);
        updateAudioUris(ctx,changed);
        LinkDownloadStore.put(ctx,batch);
        for(Migration m:moves){
            if(m.source.uri.equals(m.destination.getUri().toString()))continue;
            try{DocumentFile old=DocumentFile.fromSingleUri(ctx,Uri.parse(m.source.uri));
                if(!referenced(ctx,m.source.uri)&&old!=null&&old.isFile()&&old.delete())result.deleted++;
            }catch(Exception ignored){}
        }
    }
    private static boolean referenced(Context ctx,String uri){
        for(AudioLibrary.Book b:AudioLibrary.load(ctx))if(b.uris.contains(uri))return true;
        try{
            org.json.JSONArray books=new org.json.JSONArray(ctx.getSharedPreferences("reader",0).getString("library","[]"));
            for(int i=0;i<books.length();i++){
                org.json.JSONObject b=books.optJSONObject(i);
                if(b!=null&&uri.equals(b.optString("uri")))return true;
            }
        }catch(Exception ex){return true;}
        for(LinkDownloadStore.Batch b:LinkDownloadStore.load(ctx))for(LinkDownloadStore.Entry e:b.files)
            if(uri.equals(e.savedUri))return true;
        return false;
    }
    private static void updateReaderUris(Context ctx,java.util.Map<String,String> changed)throws Exception{
        android.content.SharedPreferences prefs=ctx.getSharedPreferences("reader",0);
        org.json.JSONArray books=new org.json.JSONArray(prefs.getString("library","[]"));
        for(int i=0;i<books.length();i++){
            org.json.JSONObject book=books.optJSONObject(i);if(book==null)continue;
            String newer=changed.get(book.optString("uri"));if(newer!=null)book.put("uri",newer);
        }
        android.content.SharedPreferences.Editor editor=prefs.edit().putString("library",books.toString());
        for(java.util.Map.Entry<String,String> change:changed.entrySet()){
            String old=change.getKey(),newer=change.getValue();
            if(old.equals(prefs.getString("uri","")))editor.putString("uri",newer);
            for(String key:new String[]{"book_shared_word:","position:","tts_word:","viewport:","book_words:"}){
                String before=key+old,after=key+newer;
                if(prefs.contains(before))editor.putInt(after,prefs.getInt(before,0));
            }
            for(String key:new String[]{"book_shared_chapter:","book_title:","book_author:"}){
                String before=key+old,after=key+newer;
                if(prefs.contains(before))editor.putString(after,prefs.getString(before,""));
            }
        }
        if(!editor.commit())throw new IOException("Не сохранена библиотека книг");
    }
    private static void updateAudioUris(Context ctx,java.util.Map<String,String> changed)throws IOException{
        java.util.ArrayList<AudioLibrary.Book> books=AudioLibrary.load(ctx);
        for(AudioLibrary.Book book:books)for(int i=0;i<book.uris.size();i++){
            String newer=changed.get(book.uris.get(i));if(newer!=null)book.uris.set(i,newer);
        }
        org.json.JSONArray data=new org.json.JSONArray();for(AudioLibrary.Book book:books)data.put(book.json());
        if(!ctx.getSharedPreferences("reader",0).edit().putString(AudioLibrary.STORE,data.toString()).commit())
            throw new IOException("Не сохранена аудиобиблиотека");
    }
    private static DocumentFile directory(DocumentFile parent,String name)throws IOException{
        DocumentFile d=parent.findFile(name);
        if(d==null)d=parent.createDirectory(name);
        if(d==null||!d.isDirectory())throw new IOException("Не удалось создать каталог "+name);
        return d;
    }
    private static byte[] digest(InputStream in)throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;
        while((n=in.read(buffer))!=-1)md.update(buffer,0,n);
        return md.digest();
    }
    private static DocumentFile copyOne(Context ctx,Source source,DocumentFile dir,String name,Result result)throws Exception{
        byte[] expected;
        try(InputStream in=source.open(ctx)){expected=digest(in);}
        DocumentFile existing=dir.findFile(name);
        if(existing!=null){
            if(!existing.isFile())throw new IOException(name+" уже существует как папка");
            try(InputStream in=ctx.getContentResolver().openInputStream(existing.getUri())){
                if(in!=null&&MessageDigest.isEqual(expected,digest(in))){result.existing++;return existing;}
            }
            throw new IOException("Конфликт имени: "+name+" (существующий файл не изменён)");
        }
        String mime=name.toLowerCase(Locale.ROOT).endsWith(".cbz")?"application/vnd.comicbook+zip":
            name.toLowerCase(Locale.ROOT).endsWith(".mp3")?"audio/mpeg":"application/octet-stream";
        DocumentFile temp=dir.createFile(mime,name+".transfer_tmp");
        if(temp==null)throw new IOException("Невозможно создать "+name);
        try{
            try(InputStream in=source.open(ctx);OutputStream out=ctx.getContentResolver().openOutputStream(temp.getUri(),"wt")){
                if(out==null)throw new IOException("Невозможно записать "+name);
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);result.bytes+=n;}
                out.flush();
            }
            try(InputStream in=ctx.getContentResolver().openInputStream(temp.getUri())){
                if(in==null||!MessageDigest.isEqual(expected,digest(in)))throw new IOException("Проверка файла не прошла: "+name);
            }
            if(!temp.renameTo(name))throw new IOException("Невозможно завершить запись: "+name);
            DocumentFile finished=dir.findFile(name);
            if(finished==null||!finished.isFile())throw new IOException("Не удалось подтвердить конечный URI: "+name);
            result.copied++;return finished;
        }catch(Exception ex){temp.delete();throw ex;}
    }
}
