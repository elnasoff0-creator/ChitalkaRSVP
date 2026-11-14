package ru.reader.rsvp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Android 16 SAF: three independently persisted destination trees, never one parent tree. */
final class DownloadFolderPicker {
    static final int AUDIO_REQUEST=3811,MANGA_REQUEST=3812,RANOBE_REQUEST=3813,BOOK_REQUEST=3814;
    static final String BOOK="book_download_tree_v165";
    static final String RANOBE=BOOK; // All ranobe providers share the Book category with Edge TTS.
    static final String MANGA="mangalib_search_tree",AUDIO="audiobook_search_tree";
    private static final String OLD_RANOBE="ranobelib_search_tree",OLD_EDGE="edge_audio_folder_uri_v150";
    private DownloadFolderPicker(){}
    static String sourceKey(String provider){
        if(provider==null)return BOOK;
        String p=provider.toLowerCase(Locale.ROOT);
        if(p.contains("manga")||p.contains("comic"))return MANGA;
        if(p.contains("audio")||p.contains("kvuhe")||p.contains("knigavuhe"))return AUDIO;
        if(p.contains("ranobe")||p.contains("edge")||p.contains("ebook")||p.contains("epub")||p.contains("fb2")||p.contains("html")||p.contains("book"))return BOOK;
        return "download_provider_tree:"+p;
    }
    static String label(String key){
        if(BOOK.equals(key))return "Book (книги, ранобэ, Edge TTS)";
        if(MANGA.equals(key))return "Manga (манга)";
        if(AUDIO.equals(key))return "Audiobook (аудиокниги)";
        return "Загрузки";
    }
    /** Older distinct settings are read, but new picks always use the category URI. */
    static Uri selected(Context context,String key){
        SharedPreferences p=context.getSharedPreferences("reader",0);
        String text=p.getString(key,"");
        if(text.isEmpty()&&BOOK.equals(key)){
            text=p.getString(OLD_RANOBE,"");
            if(text.isEmpty())text=p.getString(OLD_EDGE,"");
        }
        try{return text.isEmpty()?null:Uri.parse(text);}catch(Exception bad){return null;}
    }
    static boolean available(Context context,String key){
        Uri uri=selected(context,key);
        if(uri==null)return false;
        try{
            boolean grant=false;
            for(android.content.UriPermission permission:context.getContentResolver().getPersistedUriPermissions())
                if(uri.equals(permission.getUri())&&permission.isReadPermission()&&permission.isWritePermission()){
                    grant=true;break;
                }
            if(!grant)return false;
            DocumentFile tree=DocumentFile.fromTreeUri(context,uri);
            return tree!=null&&tree.exists()&&tree.isDirectory()&&tree.canWrite();
        }catch(Exception error){return false;}
    }
    static String summary(Context c,String key){
        Uri uri=selected(c,key);
        if(uri==null)return "Не выбрана";
        try{
            DocumentFile folder=DocumentFile.fromTreeUri(c,uri);
            String name=folder==null?null:folder.getName();
            return (name==null||name.isEmpty()?"Выбранная папка":name)+(available(c,key)?" · доступна":" · нет доступа");
        }catch(Exception error){return "Нет доступа к выбранной папке";}
    }
    static void choose(Activity activity,int request,String key){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Uri previous=selected(activity,key);
        if(Build.VERSION.SDK_INT>=26&&previous!=null)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,previous);
        activity.startActivityForResult(intent,request);
    }
    /** A real write/read/delete probe is required before retaining a user-selected folder. */
    private static void verifyWritable(Context context,DocumentFile folder)throws IOException{
        if(folder==null||!folder.exists()||!folder.isDirectory()||!folder.canWrite())
            throw new IOException("Выбранный каталог недоступен для записи");
        DocumentFile probe=null;
        byte[] expected="PROChitalka-SAF-v165".getBytes(StandardCharsets.UTF_8);
        try{
            probe=folder.createFile("application/octet-stream","prochitalka_saf_write_check_"+System.nanoTime()+".tmp");
            if(probe==null)throw new IOException("Android не позволил создать проверочный файл");
            try(OutputStream out=context.getContentResolver().openOutputStream(probe.getUri(),"wt")){
                if(out==null)throw new IOException("Android не предоставил поток записи");
                out.write(expected);out.flush();
            }
            byte[] actual=new byte[expected.length];
            try(InputStream in=context.getContentResolver().openInputStream(probe.getUri())){
                if(in==null)throw new IOException("Невозможно прочитать проверочный файл");
                int offset=0;
                while(offset<actual.length){int n=in.read(actual,offset,actual.length-offset);if(n<0)break;offset+=n;}
                if(offset!=actual.length||!java.util.Arrays.equals(actual,expected))
                    throw new IOException("Проверка целостности записи не прошла");
            }
            if(!probe.delete())throw new IOException("Нет разрешения удалить проверочный файл");
            probe=null;
        }catch(IOException exception){throw exception;}
        catch(Exception exception){throw new IOException("Ошибка доступа SAF: "+exception.getMessage(),exception);}
        finally{if(probe!=null)probe.delete();}
    }
    static boolean receive(Activity activity,int request,int result,Intent data,int expected,String key){
        if(request!=expected)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        Uri uri=data.getData();
        try{
            int flags=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if((flags&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)==0)
                throw new IOException("Android не выдал доступ на запись");
            activity.getContentResolver().takePersistableUriPermission(uri,flags);
            verifyWritable(activity,DocumentFile.fromTreeUri(activity,uri));
            if(!activity.getSharedPreferences("reader",0).edit().putString(key,uri.toString())
                    .remove("saf_last_error_"+key).commit())throw new IOException("Не сохранилось разрешение на папку");
            Toast.makeText(activity,"Путь "+label(key)+" сохранён",Toast.LENGTH_LONG).show();
        }catch(Exception ex){
            Toast.makeText(activity,"Папка не выбрана: "+ex.getMessage(),Toast.LENGTH_LONG).show();
        }
        return true;
    }
    static void recordIssue(Context context,String key,Exception error){
        String message=error==null?"Не удалось сохранить файл":error.getMessage();
        context.getSharedPreferences("reader",0).edit().putString("saf_last_error_"+key,
            message==null?"Ошибка файловой системы":message).apply();
        android.util.Log.w("PROChitalka-SAF",label(key)+": "+message,error);
    }
    static String lastIssue(Context context,String key){
        return context.getSharedPreferences("reader",0).getString("saf_last_error_"+key,"");
    }
    static Uri exportFile(Context context,String key,File source,String... directories)throws IOException{
        if(source==null||!source.isFile()||source.length()==0)
            throw new IOException("Исходный файл отсутствует или пуст");
        Uri rootUri=selected(context,key);
        if(rootUri==null)throw new IOException("Сначала выберите папку "+label(key)+" в настройках загрузок");
        if(!available(context,key))throw new IOException("Нет доступа к папке "+label(key)+". Выберите её повторно");
        try{
            DocumentFile dir=DocumentFile.fromTreeUri(context,rootUri);
            for(String raw:directories){
                String name=SafeDownloadTransfer.safe(raw);
                if(name.equals(".")||name.equals(".."))throw new IOException("Недопустимое имя каталога");
                dir=SafeDownloadTransfer.directory(dir,name);
            }
            DocumentFile target=dir.findFile(source.getName());
            DocumentFile exported=target!=null?
                SafeDownloadTransfer.replaceFile(context,source,dir,source.getName()):
                SafeDownloadTransfer.copyFile(context,source,dir,source.getName(),new SafeDownloadTransfer.Result());
            if(exported==null||!exported.isFile()||exported.length()!=source.length())
                throw new IOException("Сохранённый файл не прошёл проверку размера");
            context.getSharedPreferences("reader",0).edit().remove("saf_last_error_"+key).apply();
            return exported.getUri();
        }catch(Exception error){
            IOException failure=error instanceof IOException?(IOException)error:new IOException(error.getMessage(),error);
            recordIssue(context,key,failure);
            throw failure;
        }
    }
    static void export(Context context,String key,File source,String... folders)throws IOException{
        exportFile(context,key,source,folders);
    }
    /** Import an EPUB/FB2/HTML document into Book without rewriting its bytes. */
    static Uri importDocument(Context context,String key,Uri original)throws IOException{
        if(original==null)throw new IOException("Файл книги не выбран");
        if(!available(context,key))throw new IOException("Сначала выберите папку "+label(key));
        String name="book.html";
        try(android.database.Cursor cursor=context.getContentResolver().query(original,
            new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()&&!cursor.isNull(0))name=cursor.getString(0);
        }catch(Exception ignored){}
        name=SafeDownloadTransfer.safe(name);
        DocumentFile root=DocumentFile.fromTreeUri(context,selected(context,key));
        DocumentFile imported=SafeDownloadTransfer.directory(root,"ImportedBooks");
        DocumentFile existing=imported.findFile(name);
        if(existing!=null&&existing.getUri().equals(original))return original;
        if(existing!=null){
            int dot=name.lastIndexOf('.');String stem=dot>0?name.substring(0,dot):name;
            String ext=dot>0?name.substring(dot):"";
            for(int suffix=2;suffix<1000;suffix++){
                String candidate=stem+" ("+suffix+")"+ext;
                if(imported.findFile(candidate)==null){name=candidate;break;}
                if(suffix==999)throw new IOException("Слишком много книг с одинаковым именем");
            }
        }
        File staging=new File(context.getCacheDir(),"import_book_saf_165");
        if(!staging.isDirectory()&&!staging.mkdirs())throw new IOException("Не подготовлена временная папка");
        File temporary=new File(staging,name);
        try{
            try(InputStream in=context.getContentResolver().openInputStream(original);
                OutputStream out=new java.io.FileOutputStream(temporary)){
                if(in==null)throw new IOException("Исходная книга недоступна");
                byte[] bytes=new byte[32768];int n;long total=0;
                while((n=in.read(bytes))!=-1){
                    total+=n;if(total>250L*1024*1024)throw new IOException("Книга больше 250 МБ");
                    out.write(bytes,0,n);
                }
                if(total==0)throw new IOException("Книга пуста");
            }
            return exportFile(context,key,temporary,"ImportedBooks");
        }finally{temporary.delete();}
    }
}
