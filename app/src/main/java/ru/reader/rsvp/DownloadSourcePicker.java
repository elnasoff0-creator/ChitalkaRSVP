package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;

/** Category-level launcher. Adding a site adds a provider record, not a toolbar button. */
final class DownloadSourcePicker {
    private DownloadSourcePicker() {}

    static final class Provider {
        final String id, title, description;
        final Class<? extends Activity> screen;
        Provider(String id, String title, String description, Class<? extends Activity> screen) {
            this.id=id;this.title=title;this.description=description;this.screen=screen;
        }
    }
    static final String RANOBE="ranobe", MANGA="manga", AUDIO="audio";
    private static final Provider[] RANOBE_SOURCES={
        new Provider("ranobelib","RanobeLIB","Каталог, тома и HTML-главы",RanobeLibSearchActivity.class),
        new Provider("ranobes","Ranobes.net","HTML-ранобэ, оригинал или машинный перевод",RanobesSearchActivity.class)
    };
    private static final Provider[] MANGA_SOURCES={
        new Provider("mangalib","MangaLib","Манга и CBZ-главы",MangaLibSearchActivity.class)
    };
    private static final Provider[] AUDIO_SOURCES={
        new Provider("kvuhe","Книга в ухе","Аудиокниги и MP3-главы",KvuheSearchActivity.class)
    };
    static void show(Activity activity,String category){
        Provider[] sources=RANOBE.equals(category)?RANOBE_SOURCES:MANGA.equals(category)?MANGA_SOURCES:AUDIO_SOURCES;
        String heading=RANOBE.equals(category)?"Поиск ранобэ":MANGA.equals(category)?"Поиск манги":"Поиск аудиокниг";
        String[] labels=new String[sources.length];
        for(int i=0;i<sources.length;i++)labels[i]=sources[i].title+"\n"+sources[i].description;
        new SwipeDialogs.Builder(activity).setTitle(heading)
            .setItems(labels,(dialog,which)->{
                if(which<0||which>=sources.length)return;
                activity.startActivity(new Intent(activity,sources[which].screen).putExtra("content_source_id",sources[which].id));
            })
            .setNegativeButton("Отмена",null).show();
    }
}
