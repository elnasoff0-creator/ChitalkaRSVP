package ru.reader.rsvp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;
import java.io.*;

/** SAF save folder for each download source, with persistent access grants. */
final class DownloadFolderPicker {
    static final int AUDIO_REQUEST=3811, MANGA_REQUEST=3812;
    static final String AUDIO="audiobook_search_tree", MANGA="mangalib_search_tree";
    private DownloadFolderPicker(){}
    static void choose(Activity activity,int request,String key){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        String saved=activity.getSharedPreferences("reader",0).getString(key,"");
        if(Build.VERSION.SDK_INT>=26&&!saved.isEmpty())intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,Uri.parse(saved));
        activity.startActivityForResult(intent,request);
    }
    static boolean receive(Activity activity,int request,int result,Intent data,int expected,String key){
        if(request!=expected)return false;
        if(result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{
                activity.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentFile folder=DocumentFile.fromTreeUri(activity,uri);
                if(folder==null||!folder.canWrite())throw new IOException("Нет доступа для записи");
                activity.getSharedPreferences("reader",0).edit().putString(key,uri.toString()).apply();
                Toast.makeText(activity,"Папка сохранения: "+folder.getName(),Toast.LENGTH_SHORT).show();
            }catch(Exception ex){Toast.makeText(activity,"Не удалось выбрать папку: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        }
        return true;
    }
    static void export(Context context,String key,File source,String... folders)throws IOException{
        String saved=context.getSharedPreferences("reader",0).getString(key,"");
        if(saved.isEmpty()||!source.isFile())return;
        DocumentFile dir=DocumentFile.fromTreeUri(context,Uri.parse(saved));
        if(dir==null||!dir.canWrite())throw new IOException("Выбранная папка недоступна для записи");
        for(String name:folders){
            String safe=name.replaceAll("[\\/:*?\"<>|]","_").trim();
            if(safe.isEmpty())continue;
            DocumentFile next=dir.findFile(safe);
            if(next==null)next=dir.createDirectory(safe);
            if(next==null||!next.isDirectory())throw new IOException("Не удалось создать папку: "+safe);
            dir=next;
        }
        DocumentFile target=dir.findFile(source.getName());
        if(target!=null&&target.isFile()&&target.length()==source.length())return;
        if(target==null)target=dir.createFile(source.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".cbz")?"application/vnd.comicbook+zip":"audio/mpeg",source.getName());
        if(target==null)throw new IOException("Не удалось создать файл: "+source.getName());
        try(InputStream in=new FileInputStream(source);OutputStream out=context.getContentResolver().openOutputStream(target.getUri(),"wt")){
            if(out==null)throw new IOException("Папка не позволяет запись");
            byte[] buffer=new byte[65536];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
        }
    }
}
