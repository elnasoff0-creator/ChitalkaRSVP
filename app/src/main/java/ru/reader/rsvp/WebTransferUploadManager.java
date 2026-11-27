package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/** SAF-backed import; no arbitrary filesystem paths supplied by HTTP clients are used. */
final class WebTransferUploadManager {
    static final long MAX_BYTES=8L*1024L*1024L*1024L; // Bounded streaming, eight GiB per file.
    static final class Outcome {
        final String name,type,uri; final long bytes;
        Outcome(String n,String t,String u,long b){name=n;type=t;uri=u;bytes=b;}
    }
    private WebTransferUploadManager(){}
    static String sanitize(String raw)throws IOException {return WebTransferPolicy.filename(raw);}
    static String category(String name)throws IOException {
        String kind=WebTransferPolicy.kind(name);
        return "book".equals(kind)?DownloadFolderPicker.BOOK:"manga".equals(kind)?DownloadFolderPicker.MANGA:DownloadFolderPicker.AUDIO;
    }
    static String safeTitle(String name){int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name;}
    static synchronized Outcome commit(Context context,File temp,String requestedName,String onCollision)throws Exception {
        String name=sanitize(requestedName),kind=category(name);
        if(!temp.isFile()||temp.length()<=0||temp.length()>MAX_BYTES)throw new IOException("Недопустимый размер файла");
        if(!DownloadFolderPicker.available(context,kind))throw new IOException("Выберите доступную папку «"+DownloadFolderPicker.label(kind)+"» в Настройки → Общие → Папки загрузок");
        DocumentFile root=DocumentFile.fromTreeUri(context,DownloadFolderPicker.selected(context,kind));
        if(root==null||!root.canWrite())throw new IOException("Нет доступа к SAF-папке");
        DocumentFile folder=SafeDownloadTransfer.directory(root,DownloadFolderPicker.BOOK.equals(kind)?"ImportedBooks":"WebTransfer");
        DocumentFile existing=folder.findFile(name);
        if(existing!=null){
            String strategy="rename".equals(onCollision)?"rename":WebTransferPrefs.duplicate(context);
            if(!"rename".equals(strategy))throw new IOException(("skip".equals(strategy)?"DUPLICATE_SKIP:":"DUPLICATE:")+"Файл с таким именем уже существует");
            int pos=name.lastIndexOf('.');String base=pos>0?name.substring(0,pos):name,ext=pos>0?name.substring(pos):"";
            boolean found=false;
            for(int n=2;n<10000;n++){String candidate=base+" ("+n+")"+ext;if(folder.findFile(candidate)==null){name=candidate;found=true;break;}}
            if(!found)throw new IOException("Слишком много совпадающих имён");
        }
        // A temporary SAF document remains hidden from the reader library until fully copied.
        String staging="upload_"+Long.toHexString(System.nanoTime())+".part";
        DocumentFile partial=folder.createFile("application/octet-stream",staging);
        if(partial==null)throw new IOException("Не удалось создать файл назначения");
        DocumentFile target=null;
        try{
            try(FileInputStream in=new FileInputStream(temp);OutputStream out=context.getContentResolver().openOutputStream(partial.getUri(),"wt")){
                if(out==null)throw new IOException("Невозможно записать файл через SAF");
                byte[] buffer=new byte[65536];int n;long copied=0;
                while((n=in.read(buffer))!=-1){out.write(buffer,0,n);copied+=n;}
                out.flush();if(copied!=temp.length())throw new IOException("Размер файла изменился при копировании");
            }
            if(partial.length()!=temp.length())throw new IOException("Проверка размера через SAF не пройдена");
            if(!partial.renameTo(name))throw new IOException("Невозможно завершить сохранение файла");
            target=partial;
            // Keep an unchanged file if a library-index operation fails; user can import it manually.
            Uri uri=target.getUri();
            if(DownloadFolderPicker.BOOK.equals(kind))registerBook(context,name,uri);
            else if(DownloadFolderPicker.MANGA.equals(kind))registerComic(context,name,uri);
            else registerAudio(context,name,uri);
            return new Outcome(name,kind,uri.toString(),temp.length());
        }catch(Exception ex){
            if(target==null)try{partial.delete();}catch(Exception ignored){}
            throw ex;
        }
    }
    static void registerBook(Context c,String name,Uri uri)throws Exception {
        android.content.SharedPreferences p=c.getSharedPreferences("reader",0);
        synchronized(WebTransferUploadManager.class){
            JSONArray books=new JSONArray(p.getString("library","[]"));
            for(int i=0;i<books.length();i++)if(uri.toString().equals(books.optJSONObject(i).optString("uri")))return;
            JSONObject meta=new JSONObject();meta.put("uri",uri.toString());meta.put("title",safeTitle(name));
            meta.put("format",name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT));meta.put("openedAt",System.currentTimeMillis());
            JSONArray changed=new JSONArray();changed.put(meta);for(int i=0;i<books.length();i++)changed.put(books.opt(i));
            if(!p.edit().putString("library",changed.toString()).commit())throw new IOException("Не удалось обновить библиотеку книг");
        }
    }
    private static void registerComic(Context c,String name,Uri uri) {
        ComicLibrary.Comic comic=new ComicLibrary.Comic();comic.uri=uri.toString();comic.title=safeTitle(name);
        comic.kind=name.toLowerCase(Locale.ROOT).endsWith(".cbr")?"cbr":"cbz";comic.opened=System.currentTimeMillis();
        ComicLibrary.save(c,comic);
    }
    private static void registerAudio(Context c,String name,Uri uri) {
        AudioLibrary.Book book=new AudioLibrary.Book();book.id="webtransfer:"+uri;
        book.name=safeTitle(name);book.updated=System.currentTimeMillis();book.uris.add(uri.toString());book.chapters.add(safeTitle(name));
        java.util.ArrayList<AudioLibrary.Book> all=AudioLibrary.load(c);all.add(0,book);AudioLibrary.save(c,all);
    }
}
