package ru.reader.rsvp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;

/** Explicit, persisted SAF tree: the app never silently falls back to Android/data. */
public final class EdgeFolderPicker {
    public static final int REQUEST=15150;
    private static final String PREF="edge_audio_folder_uri_v150";
    private EdgeFolderPicker(){}
    public static Uri selected(Context c){
        String value=c.getSharedPreferences("reader",0).getString(PREF,"");
        if(value.isEmpty())return null;
        try{return Uri.parse(value);}catch(Exception ex){return null;}
    }
    public static boolean available(Context c){
        Uri uri=selected(c);if(uri==null)return false;
        try{
            boolean granted=false;
            for(android.content.UriPermission p:c.getContentResolver().getPersistedUriPermissions())
                if(uri.equals(p.getUri())&&p.isReadPermission()&&p.isWritePermission()){granted=true;break;}
            if(!granted)return false;
            DocumentFile folder=DocumentFile.fromTreeUri(c,uri);
            return folder!=null&&folder.exists()&&folder.isDirectory()&&folder.canWrite();
        }catch(Exception ex){return false;}
    }
    public static void choose(Activity activity){
        Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(pick,REQUEST);
    }
    public static void show(Activity activity){
        String current=selected(activity)==null?"Не выбрана":selected(activity).toString();
        new SwipeDialogs.Builder(activity).setTitle("Путь к файлам Edge TTS")
            .setMessage("Выберите постоянную папку для аудиокниг. В ней будут созданы папки книг и глав.\n\nТекущий путь: "+current)
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Использовать эту папку",(d,w)->choose(activity)).show();
    }
    public static void browse(Activity activity){
        Uri root=selected(activity);
        if(root==null||!available(activity)){show(activity);return;}
        try{
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI,root);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            activity.startActivityForResult(intent,REQUEST);
        }catch(Exception ex){show(activity);}
    }
    public static boolean accept(Activity activity,int request,int result,Intent data){
        if(request!=REQUEST)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        Uri uri=data.getData();
        try{
            activity.getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            DocumentFile folder=DocumentFile.fromTreeUri(activity,uri);
            if(folder==null||!folder.isDirectory()||!folder.canWrite())
                throw new Exception("Выбранная папка недоступна для записи");
            activity.getSharedPreferences("reader",0).edit().putString(PREF,uri.toString()).apply();
            EdgeChapterArchive.invalidate();
            Toast.makeText(activity,"Папка Edge TTS сохранена",Toast.LENGTH_SHORT).show();
        }catch(Exception ex){Toast.makeText(activity,"Не удалось выбрать папку: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        return true;
    }
}
