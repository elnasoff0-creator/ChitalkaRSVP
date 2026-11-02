package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

/** Persisted reading font selection; imported fonts are privately copied and validated. */
final class ReaderFontLibrary {
    static final String KEY="reading_font_name";
    static final String[] NAMES={"Без засечек","С засечками","Моноширинный","Roboto","Noto Serif","Узкий Sans","Лёгкий Sans","Средний Sans","Рукописный"};
    private ReaderFontLibrary(){}
    static File folder(Context c){return new File(c.getFilesDir(),"fonts");}
    static ArrayList<String> names(Context c){ArrayList<String> out=new ArrayList<>(Arrays.asList(NAMES));File[] files=folder(c).listFiles();if(files!=null)for(File f:files){if(f.isFile()&&validExtension(f.getName()))out.add(f.getName());}return out;}
    static boolean validExtension(String name){String x=name.toLowerCase(java.util.Locale.ROOT);return x.endsWith(".ttf")||x.endsWith(".otf");}
    static String choice(Context c,SharedPreferences prefs){return prefs.getString(KEY,legacy(prefs.getInt("font",1)));}
    static String legacy(int n){return n==0?NAMES[0]:n==2?NAMES[2]:NAMES[1];}
    static Typeface selected(Context c,SharedPreferences p){String name=choice(c,p);
        try{
            if(name.equals(NAMES[0])||name.equals(NAMES[3]))return Typeface.SANS_SERIF;
            if(name.equals(NAMES[2]))return Typeface.MONOSPACE;
            if(name.equals(NAMES[5]))return Typeface.create("sans-serif-condensed",Typeface.NORMAL);
            if(name.equals(NAMES[6]))return Typeface.create("sans-serif-light",Typeface.NORMAL);
            if(name.equals(NAMES[7]))return Typeface.create("sans-serif-medium",Typeface.NORMAL);
            if(name.equals(NAMES[8]))return Typeface.create("casual",Typeface.NORMAL);
            if(name.equals(NAMES[1])||name.equals(NAMES[4]))return Typeface.SERIF;
            File f=new File(folder(c),name);if(f.isFile()&&f.getCanonicalFile().getParentFile().equals(folder(c).getCanonicalFile()))return Typeface.createFromFile(f);
        }catch(Exception ignored){}return Typeface.SERIF;
    }
    static Typeface preview(Context c,String name){
        SharedPreferences p=c.getSharedPreferences("reader",0);
        return selected(c,new PreviewPreferences(p,name));
    }
    private static final class PreviewPreferences implements SharedPreferences {
        private final SharedPreferences wrapped;private final String name;
        PreviewPreferences(SharedPreferences p,String n){wrapped=p;name=n;}
        @Override public String getString(String key,String def){return KEY.equals(key)?name:wrapped.getString(key,def);}
        @Override public int getInt(String key,int def){return wrapped.getInt(key,def);}
        @Override public long getLong(String key,long def){return wrapped.getLong(key,def);}
        @Override public float getFloat(String key,float def){return wrapped.getFloat(key,def);}
        @Override public boolean getBoolean(String key,boolean def){return wrapped.getBoolean(key,def);}
        @Override public boolean contains(String key){return wrapped.contains(key);}
        @Override public java.util.Map<String,?> getAll(){return wrapped.getAll();}
        @Override public java.util.Set<String> getStringSet(String key,java.util.Set<String> def){return wrapped.getStringSet(key,def);}
        @Override public Editor edit(){return wrapped.edit();}
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){wrapped.registerOnSharedPreferenceChangeListener(l);}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){wrapped.unregisterOnSharedPreferenceChangeListener(l);}
    }
    static String importFont(Context c,Uri uri) throws Exception{
        String original="";try(android.database.Cursor cursor=c.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst())original=cursor.getString(0);
        }
        if(!validExtension(original))throw new Exception("Выберите TTF или OTF");
        String safe=original.replaceAll("[^\\p{L}\\p{N}._-]","_");if(safe.length()>85)safe=safe.substring(safe.length()-85);
        folder(c).mkdirs();File target=new File(folder(c),safe);File temp=new File(folder(c),safe+".partial");long bytes=0;
        try(InputStream in=c.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temp)){
            if(in==null)throw new Exception("Не удалось прочитать шрифт");byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>15*1024*1024)throw new Exception("Шрифт слишком большой");out.write(buffer,0,n);}
        }catch(Exception ex){temp.delete();throw ex;}
        if(bytes<100)throw new Exception("Файл шрифта пуст");
        try{Typeface.createFromFile(temp);}catch(Exception ex){temp.delete();throw new Exception("Неверный файл шрифта");}
        if(target.exists()&&!target.delete())throw new Exception("Не удаётся заменить шрифт");
        if(!temp.renameTo(target))throw new Exception("Не удалось сохранить шрифт");
        return safe;
    }
}
