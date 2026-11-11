package ru.reader.rsvp;

import android.app.Activity;

/** Single navigation-sheet facade for every ReaderActivity mode.
 * Uses one animation/gesture implementation, never an AlertDialog fallback.
 */
final class ReaderSheets {
    private ReaderSheets(){}
    static void modes(Activity owner,int mode,UiNavigationSheet.IndexChoice selected){
        UiNavigationSheet.showModes(owner,mode,selected);
    }
    static void overflow(Activity owner,boolean tts,UiNavigationSheet.IndexChoice selected){
        String[] labels=tts
            ?new String[]{"Добавить закладку","Все закладки","Голос озвучки","Загрузка озвучки","Словарь MS Edge","Поиск по книге","Путь к файлам Edge TTS"}
            :new String[]{"Добавить закладку","Все закладки","Загрузка озвучки","Словарь MS Edge","Поиск по книге","Путь к файлам Edge TTS"};
        int[] icons=tts
            ?new int[]{R.drawable.ic_bookmark,R.drawable.ic_bookmark,R.drawable.ic_tts_engine,R.drawable.ic_downloads,R.drawable.ic_edge_dictionary,R.drawable.ic_search,R.drawable.ic_downloads}
            :new int[]{R.drawable.ic_bookmark,R.drawable.ic_bookmark,R.drawable.ic_downloads,R.drawable.ic_edge_dictionary,R.drawable.ic_search,R.drawable.ic_downloads};
        UiNavigationSheet.showActions(owner,"Дополнительно",labels,icons,selected);
    }
}
