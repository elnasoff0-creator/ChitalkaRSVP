package ru.reader.rsvp;

import android.content.Context;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/** A bounded manifest followed by the raw files, in manifest order. No archive or full-memory copy. */
final class WebTransferFolder {
    private static final int MAX_MANIFEST=4*1024*1024,MAX_FILES=10000;
    private static final class Entry {String path;long size;DocumentFile file;Entry(String p,long s){path=p;size=s;}}
    static boolean coverName(String name){return name.matches("(?i)(cover|folder|front|poster)\\.(jpg|jpeg|png|webp|gif|bmp|avif)");}
    static String leaf(String p){return p.substring(p.lastIndexOf('/')+1);}
    static WebTransferUploadManager.Outcome receive(Context c,InputStream input,Map<String,String> h,String id,Socket client,Map<String,Socket> transfers,BooleanSupplier running,Runnable tick)throws Exception{
        long length=WebTransferPolicy.length(h.getOrDefault("content-length","-1"),h.get("transfer-encoding"),Long.MAX_VALUE-1);
        DataInputStream in=new DataInputStream(input);int manifestSize=in.readInt();
        if(manifestSize<=0||manifestSize>MAX_MANIFEST||length<4L+manifestSize)throw new IOException("Неверный манифест папки");
        byte[] raw=new byte[manifestSize];in.readFully(raw);JSONObject manifest=new JSONObject(new String(raw,StandardCharsets.UTF_8));
        String name=WebTransferPolicy.filename(manifest.getString("name"));JSONArray files=manifest.getJSONArray("files");
        if(files.length()==0||files.length()>MAX_FILES)throw new IOException("Недопустимое количество файлов");
        ArrayList<Entry> entries=new ArrayList<>();HashSet<String> paths=new HashSet<>();long total=0;boolean audio=false,manga=false,html=false,book=false;
        for(int i=0;i<files.length();i++){
            JSONObject f=files.getJSONObject(i);String path=WebTransferPolicy.relative(f.getString("path"));String n=leaf(path);long size=f.getLong("size");
            if(size<=0||size>WebTransferUploadManager.MAX_BYTES||size>Long.MAX_VALUE-total||!paths.add(path.toLowerCase(Locale.ROOT)))throw new IOException("Неверный размер или повтор пути");
            total+=size;entries.add(new Entry(path,size));
            if(AudioLibrary.isAudio(n))audio=true;else if(coverName(n)){}else if(ComicLibrary.isImageName(n))manga=true;
            else if(n.matches("(?i).*\\.(html|htm)"))html=true;else if(n.matches("(?i).*\\.(epub|fb2|txt)"))book=true;
            else if(n.matches("(?i).*\\.(cbz|cbr|zip|rar)"))manga=true;else throw new IOException("Неподдерживаемый файл: "+n);
        }
        if(total!=length-4L-manifestSize)throw new IOException("Размер папки не совпадает с манифестом");
        for(String p:paths){int pos=p.indexOf('/');while(pos>=0){if(paths.contains(p.substring(0,pos)))throw new IOException("Файл совпадает с каталогом");pos=p.indexOf('/',pos+1);}}
        String selected=manifest.optString("kind", "auto");
        String kind=audio&&!manga&&!html&&!book?DownloadFolderPicker.AUDIO:!audio&&!book&&(manga||html)?DownloadFolderPicker.MANGA:!audio&&!manga&&book?DownloadFolderPicker.BOOK:"";
        if(!"auto".equals(selected)){
            if("audio".equals(selected)&&audio&&!manga&&!html&&!book)kind=DownloadFolderPicker.AUDIO;
            else if("manga".equals(selected)&&!audio&&!book&&(manga||html))kind=DownloadFolderPicker.MANGA;
            else if("book".equals(selected)&&!audio&&!manga&&(book||html))kind=DownloadFolderPicker.BOOK;
            else throw new IOException("Файлы не соответствуют выбранному типу; разделите смешанную папку");
        }
        if(kind.isEmpty())throw new IOException("Разделите смешанную папку по типам произведений");
        if(!DownloadFolderPicker.available(c,kind))throw new IOException("Выберите папку назначения в настройках");
        DocumentFile root=DocumentFile.fromTreeUri(c,DownloadFolderPicker.selected(c,kind));
        if(root==null||!root.canWrite())throw new IOException("Нет доступа к папке назначения");
        checkSpace(root,total);
        if(!WebTransferDownloadStore.begin(c,id,name,kind,total))throw new IOException("Повтор ID передачи");
        WebTransferDownloadStore.groupCount(c,id,entries.size());
        transfers.put(id,client);DocumentFile pending=null;boolean success=false,published=false;
        try{
            DocumentFile dest=SafeDownloadTransfer.directory(root,DownloadFolderPicker.BOOK.equals(kind)?"ImportedBooks":"WebTransfer");
            String base=name;
            synchronized(WebTransferFolder.class){
                if(dest.findFile(name)!=null){String strategy="rename".equals(h.get("x-wt-collision"))?"rename":WebTransferPrefs.duplicate(c);
                    if(!"rename".equals(strategy))throw new IOException(("skip".equals(strategy)?"DUPLICATE_SKIP:":"DUPLICATE:")+"Папка уже существует");
                    for(int i=2;dest.findFile(name)!=null;i++){if(i>9999)throw new IOException("Слишком много совпадений");name=base+" ("+i+")";}}
                pending=dest.createDirectory("upload_"+UUID.randomUUID()+".part");
            }
            if(pending==null)throw new IOException("Не удалось создать временную папку");
            long received=0;byte[] buffer=new byte[65536];
            for(Entry entry:entries){
                check(c,id,running);checkSpace(root,total-received);String[] parts=entry.path.split("/");DocumentFile dir=pending;
                for(int i=0;i<parts.length-1;i++)dir=SafeDownloadTransfer.directory(dir,parts[i]);
                String n=parts[parts.length-1];if(dir.findFile(n)!=null)throw new IOException("Повтор имени файла");
                entry.file=dir.createFile("application/octet-stream","upload_"+UUID.randomUUID()+".part");if(entry.file==null||!entry.file.renameTo(n)||!n.equals(entry.file.getName()))throw new IOException("Хранилище изменило имя файла: "+n);
                try(OutputStream out=c.getContentResolver().openOutputStream(entry.file.getUri(),"wt")){
                    if(out==null)throw new IOException("Не удалось открыть файл назначения");long left=entry.size;
                    while(left>0){check(c,id,running);int read=in.read(buffer,0,(int)Math.min(buffer.length,left));if(read<0)throw new EOFException("Передача прервана");out.write(buffer,0,read);left-=read;received+=read;tick.run();WebTransferDownloadStore.progress(c,id,received,total);}out.flush();
                }
                if(entry.file.length()!=entry.size)throw new IOException("Размер файла после записи не совпадает");
            }
            check(c,id,running);WebTransferDownloadStore.importing(c,id);
            // Re-resolve every child after directory rename: SAF document IDs can change.
            synchronized(WebTransferFolder.class){if(dest.findFile(name)!=null)throw new IOException("DUPLICATE:Папка появилась во время передачи");if(!pending.renameTo(name))throw new IOException("Не удалось завершить сохранение папки");published=true;}
            String uri=pending.getUri().toString(),cover="";
            entries.sort((a,b)->AudioLibrary.naturalCompare(a.path,b.path));
            for(Entry e:entries){DocumentFile dir=pending;for(String part:e.path.split("/")){dir=dir.findFile(part);if(dir==null)throw new IOException("Файл не найден после переименования");}e.file=dir;if(cover.isEmpty()&&coverName(leaf(e.path)))cover=dir.getUri().toString();}
            if(DownloadFolderPicker.AUDIO.equals(kind)){
                AudioLibrary.Book b=new AudioLibrary.Book();b.id="webtransfer:"+uri;b.name=name;b.updated=System.currentTimeMillis();b.coverUri=cover;
                for(Entry e:entries)if(AudioLibrary.isAudio(leaf(e.path))){b.uris.add(e.file.getUri().toString());b.chapters.add(AudioLibrary.clean(e.path));}
                synchronized(WebTransferFolder.class){AudioLibrary.merge(c,new ArrayList<>(Collections.singletonList(b)));}
                if(cover.isEmpty()&&!b.uris.isEmpty())cover="wifi-cover:mp3:"+b.uris.get(0);
            }else if(DownloadFolderPicker.MANGA.equals(kind)){
                ComicLibrary.Comic comic=new ComicLibrary.Comic();comic.uri=uri;comic.title=name;comic.kind="auto";comic.opened=System.currentTimeMillis();ComicLibrary.save(c,comic);if(!cover.isEmpty())c.getSharedPreferences("reader",0).edit().putString("wifi_comic_cover:"+uri,cover).apply();
                if(cover.isEmpty())cover="wifi-cover:folder:"+uri;
            }else{
                for(Entry e:entries)if(leaf(e.path).matches("(?i).*\\.(epub|fb2|txt|html|htm)")){WebTransferUploadManager.registerBook(c,leaf(e.path),e.file.getUri());if(cover.isEmpty()){String n=leaf(e.path);cover="wifi-cover:"+n.substring(n.lastIndexOf('.')+1).toLowerCase(Locale.ROOT)+":"+e.file.getUri();}}
            }
            if(!cover.isEmpty()&&!cover.startsWith("wifi-cover:"))cover="wifi-cover:image:"+cover;
            WebTransferDownloadStore.setCover(c,id,cover);WebTransferDownloadStore.finish(c,id,WebTransferDownloadStore.DONE,"",uri,total);success=true;
            return new WebTransferUploadManager.Outcome(name,kind,uri,total);
        }catch(Exception e){String msg=e.getMessage()==null?"Ошибка папки":e.getMessage();WebTransferDownloadStore.finish(c,id,WebTransferDownloadStore.cancelled(c,id)?WebTransferDownloadStore.CANCELLED:msg.startsWith("DUPLICATE")?WebTransferDownloadStore.DUPLICATE:WebTransferDownloadStore.ERROR,msg,"",-1);throw e;}
        finally{transfers.remove(id,client);if(!success&&!published&&pending!=null)try{pending.delete();}catch(Exception ignored){}}
    }
    private static void checkSpace(DocumentFile root,long needed)throws IOException{
        // A provider may expose a non-local volume. Only trust a known primary external storage path.
        try{String doc=android.provider.DocumentsContract.getTreeDocumentId(root.getUri());
            if("com.android.externalstorage.documents".equals(root.getUri().getAuthority())&&doc.startsWith("primary:")){
                long free=new android.os.StatFs(android.os.Environment.getExternalStorageDirectory().getAbsolutePath()).getAvailableBytes();
                if(free<needed)throw new IOException("Недостаточно свободного места для папки");
            }
        }catch(IllegalArgumentException|SecurityException ignored){}
    }
    private static void check(Context c,String id,BooleanSupplier running)throws IOException{if(!running.getAsBoolean()||WebTransferDownloadStore.cancelled(c,id))throw new IOException("Передача отменена");}
}
