package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/** Shared navigation layout: IDs remain stable when labels or order change. */
final class DrawerMenuStore {
    static final String[] DEFAULT={"home","reading","audio","comic","downloads","pet","settings","update","help"};
    private static final String ORDER="drawer_order_v123",HIDDEN="drawer_hidden_v123";
    private DrawerMenuStore(){}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("reader",0);}
    static ArrayList<String> order(Context c){
        ArrayList<String> out=new ArrayList<>();
        for(String id:prefs(c).getString(ORDER,android.text.TextUtils.join(",",DEFAULT)).split(","))
            if(isKnown(id)&&!out.contains(id))out.add(id);
        for(String id:DEFAULT)if(!out.contains(id))out.add(id);
        return out;
    }
    static void saveOrder(Context c,ArrayList<String> order){prefs(c).edit().putString(ORDER,android.text.TextUtils.join(",",order)).apply();}
    static boolean isKnown(String id){return Arrays.asList(DEFAULT).contains(id);}
    static boolean isProtected(String id){return "home".equals(id)||"settings".equals(id);}
    static HashSet<String> hidden(Context c){return new HashSet<>(prefs(c).getStringSet(HIDDEN,new HashSet<>()));}
    static boolean visible(Context c,String id){return isProtected(id)||!hidden(c).contains(id);}
    static void visible(Context c,String id,boolean visible){if(isProtected(id))return;HashSet<String> items=hidden(c);if(visible)items.remove(id);else items.add(id);prefs(c).edit().putStringSet(HIDDEN,items).apply();}
    static String defaultName(Context c,String id){
        switch(id){case "home":return "Главный экран";case "reading":return "Чтение";
            case "audio":return "Аудиокнига";case "comic":return "Комикс";
            case "downloads":return "Загрузки";case "pet":return ChteicoStore.enabled(c)?"Чтейцо":"Статистика";
            case "settings":return "Настройки";case "update":return "Обновление";
            case "help":return "Справка";default:return id;}
    }
    static String name(Context c,String id){return prefs(c).getString("drawer_name_v123_"+id,defaultName(c,id));}
    static void rename(Context c,String id,String title){if(isKnown(id)&&title!=null&&!title.trim().isEmpty())
        prefs(c).edit().putString("drawer_name_v123_"+id,title.trim()).apply();}
    static void reset(Context c){SharedPreferences.Editor edit=prefs(c).edit().remove(ORDER).remove(HIDDEN);
        for(String id:DEFAULT)edit.remove("drawer_name_v123_"+id);edit.apply();}
    static int icon(String id,Context c){switch(id){
        case "home":return R.drawable.ic_home;case "reading":return R.drawable.ic_book;
        case "audio":return R.drawable.ic_microphone;case "comic":return R.drawable.ic_comic_library;
        case "downloads":return R.drawable.ic_downloads;
        case "pet":return ChteicoStore.enabled(c)?R.drawable.ic_chteico_nav:R.drawable.ic_stats_nav;
        case "settings":return R.drawable.ic_settings;case "update":return R.drawable.ic_web_update;
        default:return R.drawable.ic_info_help;
    }}
}
