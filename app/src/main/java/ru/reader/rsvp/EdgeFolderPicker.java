package ru.reader.rsvp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

/** Edge TTS is saved alongside ebooks under the user-selected Book SAF tree. */
public final class EdgeFolderPicker {
    public static final int REQUEST=15150;
    private EdgeFolderPicker(){}
    public static Uri selected(Context context){return DownloadFolderPicker.selected(context,DownloadFolderPicker.BOOK);}
    public static boolean available(Context context){return DownloadFolderPicker.available(context,DownloadFolderPicker.BOOK);}
    public static void choose(Activity activity){DownloadFolderPicker.choose(activity,REQUEST,DownloadFolderPicker.BOOK);}
    public static void show(Activity activity){
        new SwipeDialogs.Builder(activity).setTitle("Папка Book — книги и Edge TTS")
            .setMessage("Все книги и готовые MP3 Edge TTS сохраняются в общей пользовательской папке Book. Текущий путь: "+
                DownloadFolderPicker.summary(activity,DownloadFolderPicker.BOOK))
            .setNegativeButton("Отмена",null).setPositiveButton("Выбрать папку Book",(d,w)->choose(activity)).show();
    }
    public static void browse(Activity activity){choose(activity);}
    public static boolean accept(Activity activity,int request,int result,Intent data){
        if(!DownloadFolderPicker.receive(activity,request,result,data,REQUEST,DownloadFolderPicker.BOOK))return false;
        if(result==Activity.RESULT_OK&&available(activity))EdgeChapterArchive.invalidate();
        return true;
    }
}
