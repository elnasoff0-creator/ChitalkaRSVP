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
    static String safe(String s){
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
        if((DownloadsRepository.AUDIO.equals(item.type)||DownloadsRepository.LINK.equals(item.type))&&!item.ready)
            throw new IOException("Аудиозагрузку или загрузку по ссылке нужно завершить до переноса, чтобы новые файлы не попадали в старую папку");
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
                else if(MangaLibStorage.isUri(job.file)){
                    DocumentFile existing=DocumentFile.fromSingleUri(ctx,Uri.parse(job.file));
                    if(existing!=null&&existing.isFile())sources.add(new Source(job.file,"Том "+safe(job.volume)+"/"+safe(existing.getName())));
                }
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
        }else if(DownloadsRepository.RANOBE.equals(item.type)){
            File folder=RanobeLibStorage.folder(ctx,item.source);
            if(RanobeLibStorage.externalDir(ctx,item.source)!=null)
                throw new IOException("Ранобэ уже перенесено во внешнюю папку");
            if(folder.isDirectory())addFiles(sources,folder,"");
        }
        if(sources.isEmpty())throw new IOException("Нет завершённых файлов для переноса");
        String prefix=DownloadsRepository.MANGA.equals(item.type)?"Манга":DownloadsRepository.RANOBE.equals(item.type)?"Ранобэ":DownloadsRepository.EDGE.equals(item.type)?"Edge TTS":DownloadsRepository.LINK.equals(item.type)?"По ссылке":"Аудиокниги";
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
        else if(DownloadsRepository.MANGA.equals(item.type))migrateManga(ctx,item,ready,dir,targetUri,result);
        else if(DownloadsRepository.RANOBE.equals(item.type))migrateRanobe(ctx,item,ready,dir,targetUri,result);
        else result.note="Edge TTS: проверенные копии сохранены, рабочая озвучка остаётся у движка.";
        return result;
    }
    private static void migrateManga(Context ctx,DownloadsRepository.Item item,ArrayList<Migration> moves,DocumentFile dir,Uri grantUri,Result result)throws Exception{
        java.util.HashMap<String,String> changed=new java.util.HashMap<>();
        for(Migration m:moves)changed.put(m.source.file!=null?m.source.file.getAbsolutePath():m.source.uri,
            m.destination.getUri().toString());
        int migrated=0;
        for(MangaLibStorage.Job job:MangaLibStorage.jobs(ctx)){
            if(!job.slug.equalsIgnoreCase(item.source))continue;
            String newUri=changed.get(job.file);
            if(newUri==null)continue;
            job.file=newUri;
            MangaLibStorage.put(ctx,job);
            migrated++;
        }
        if(migrated!=moves.size())throw new IOException("Не удалось подтвердить все ссылки MangaLib. Оригиналы оставлены");
        // New chapters downloaded later are stored alongside relocated chapters.
        MangaLibStorage.setExternalDir(ctx,item.source,grantUri.toString(),dir.getName(),dir.getUri().toString());
        for(Migration m:moves){
            if(m.source.file!=null&&appOwned(ctx,m.source.file)&&m.source.file.delete())result.deleted++;
            else if(m.source.uri!=null)result.note="Некоторые исходные SAF-файлы оставлены пользователю";
        }
        result.note="MangaLib: пути CBZ обновлены, позиции чтения и обложка сохранены";
    }
    private static void migrateRanobe(Context ctx,DownloadsRepository.Item item,ArrayList<Migration> moves,DocumentFile dir,Uri grantUri,Result result)throws Exception{
        String slug=item.source;
        DocumentFile book=dir.findFile("book.html");
        if(book==null||!book.isFile()||book.length()<100)throw new IOException("Не удалось проверить HTML-книгу");
        String old=RanobeLibStorage.bookUri(ctx,slug),newUri=book.getUri().toString();
        java.util.HashMap<String,String> changed=new java.util.HashMap<>();
        for(Migration m:moves)if(m.source.file!=null)changed.put(m.source.file.getAbsolutePath(),m.destination.getUri().toString());
        // Update library identity before switching storage backend (new bookUri).
        BookUriMigration.text(ctx,old,newUri);
        RanobeLibStorage.setExternalDir(ctx,slug,grantUri.toString(),dir.getName(),dir.getUri().toString());
        for(RanobeLibStorage.Job job:RanobeLibStorage.series(ctx,slug)){
            String newer=changed.get(job.file);
            if(newer!=null){job.file=newer;RanobeLibStorage.put(ctx,job);}
        }
        for(Migration m:moves){
            if(m.source.file!=null&&appOwned(ctx,m.source.file)&&m.source.file.delete())result.deleted++;
        }
        File source=RanobeLibStorage.folder(ctx,slug);
        removeEmptyDirs(source);
        result.note="RanobeLIB: HTML, главы, изображения и обложка перенесены. Можно докачивать главы.";
    }
    private static void removeEmptyDirs(File folder){
        if(folder==null||!folder.isDirectory())return;
        File[] children=folder.listFiles();if(children!=null)for(File child:children)if(child.isDirectory())removeEmptyDirs(child);
        folder.delete();
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
            if(appOwned(ctx,m.source.file)&&!referenced(ctx,old)&&m.source.file.delete())result.deleted++;
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
        // Preserve reader positions and Edge chapter identity for any text files in this batch.
        for(java.util.Map.Entry<String,String> change:changed.entrySet()){
            boolean belongsToLibrary=false;
            try{
                org.json.JSONArray rows=new org.json.JSONArray(ctx.getSharedPreferences("reader",0).getString("library","[]"));
                for(int i=0;i<rows.length();i++){
                    org.json.JSONObject row=rows.optJSONObject(i);
                    if(row!=null&&change.getKey().equals(row.optString("uri"))){belongsToLibrary=true;break;}
                }
            }catch(Exception ex){throw new IOException("Невозможно прочитать Библиотеку",ex);}
            if(belongsToLibrary)BookUriMigration.text(ctx,change.getKey(),change.getValue());
        }
        updateAudioUris(ctx,changed);
        LinkDownloadStore.put(ctx,batch);
        // Link downloads originally reside in user-selected SAF directories; they are
        // never treated as app-owned, even if the copied documents are now primary.
        result.note="Загрузки по ссылкам: пути обновлены; исходные пользовательские файлы оставлены";
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
    static DocumentFile directory(DocumentFile parent,String name)throws IOException{
        DocumentFile d=parent.findFile(name);
        if(d==null)d=parent.createDirectory(name);
        if(d==null||!d.isDirectory())throw new IOException("Не удалось создать каталог "+name);
        return d;
    }

    static DocumentFile checkRoot(Context ctx,Uri uri)throws IOException {
        DocumentFile root=DocumentFile.fromTreeUri(ctx,uri);
        if(root==null||!root.isDirectory()||!root.canWrite())throw new IOException("Папка не поддерживает запись");
        return root;
    }
    static boolean appOwned(Context ctx,File file){
        try{
            String path=file.getCanonicalPath();
            File[] roots={ctx.getFilesDir(),ctx.getCacheDir(),ctx.getExternalFilesDir(null),
                ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS),
                ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)};
            for(File root:roots)if(root!=null&&path.startsWith(root.getCanonicalPath()+File.separator))return true;
        }catch(Exception ignored){}
        return false;
    }
    static DocumentFile copyFile(Context ctx,File file,DocumentFile directory,String name,Result result)throws Exception {
        if(!file.isFile())throw new IOException("Файл отсутствует: "+file.getName());
        return copyOne(ctx,new Source(file,name),directory,safe(name),result);
    }
    static DocumentFile copyUri(Context ctx,String uri,DocumentFile directory,String name,Result result)throws Exception {
        return copyOne(ctx,new Source(uri,name),directory,safe(name),result);
    }
    static void copyFolder(Context ctx,File folder,DocumentFile destination,Result result)throws Exception {
        File[] contents=folder.listFiles();
        if(contents==null)throw new IOException("Нельзя прочитать папку "+folder.getName());
        Arrays.sort(contents,(a,b)->a.getName().compareToIgnoreCase(b.getName()));
        for(File f:contents){
            if(f.getName().endsWith(".transfer_tmp")||f.getName().endsWith(".part"))continue;
            if(f.isDirectory())copyFolder(ctx,f,directory(destination,safe(f.getName())),result);
            else if(f.isFile())copyFile(ctx,f,destination,f.getName(),result);
        }
    }
    static void copyDocumentTree(Context ctx,DocumentFile source,DocumentFile dest,Result result,int level)throws Exception {
        if(level>15)throw new IOException("Слишком глубокая вложенность папок");
        DocumentFile[] items=source.listFiles();
        for(DocumentFile entry:items){
            String name=entry.getName();if(name==null||name.isEmpty())continue;
            if(entry.isDirectory())copyDocumentTree(ctx,entry,directory(dest,safe(name)),result,level+1);
            else if(entry.isFile())copyUri(ctx,entry.getUri().toString(),dest,name,result);
        }
    }
    static void removeCopiedFiles(File source){
        // Delete only migrated data, never unknown temporary files. Not intended for
        // untrusted user folders or content trees.
        if(source==null||!source.exists())return;
        if(source.isDirectory()){
            File[] files=source.listFiles();if(files!=null)for(File f:files)removeCopiedFiles(f);
            source.delete();
        }else if(!source.getName().endsWith(".part")&&!source.getName().endsWith(".transfer_tmp"))source.delete();
    }
    /** SAF has no reliable atomic rename-replace. Keep a recoverable .previous until verified. */
    static DocumentFile replaceFile(Context ctx,File source,DocumentFile directory,String name)throws Exception {
        Result result=new Result();
        String temporary=name+".new_v158";
        DocumentFile existingTemp=directory.findFile(temporary);
        if(existingTemp!=null&&!existingTemp.delete())throw new IOException("Не удалить незавершённое обновление");
        DocumentFile next=copyFile(ctx,source,directory,temporary,result);
        DocumentFile current=directory.findFile(name);
        DocumentFile backup=directory.findFile(name+".previous_v158");
        if(backup!=null&&!backup.delete())throw new IOException("Не удалить предыдущую резервную копию");
        if(current!=null&&!current.renameTo(name+".previous_v158"))throw new IOException("Не сохранить предыдущую версию");
        if(!next.renameTo(name)){
            if(current!=null){DocumentFile old=directory.findFile(name+".previous_v158");if(old!=null)old.renameTo(name);}
            throw new IOException("Не удалось завершить обновление: "+name);
        }
        DocumentFile finished=directory.findFile(name);
        if(finished==null||!finished.isFile())throw new IOException("Обновлённый файл не найден");
        // compare file SHA-256 with the completed destination once more
        try(InputStream a=new FileInputStream(source);InputStream b=ctx.getContentResolver().openInputStream(finished.getUri())){
            if(b==null||!MessageDigest.isEqual(digest(a),digest(b)))throw new IOException("Новая копия не прошла проверку");
        }
        DocumentFile old=directory.findFile(name+".previous_v158");if(old!=null)old.delete();
        return finished;
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
