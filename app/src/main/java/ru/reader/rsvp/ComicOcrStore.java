package ru.reader.rsvp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;

/** Per-comic, per-chapter cached OCR boxes in native-image normalized coordinates. */
final class ComicOcrStore {
    private final File directory;
    ComicOcrStore(Context context,String comicUri){
        String hash=hash(comicUri);
        directory=new File(context.getFilesDir(),"comic_ocr/"+hash);
        directory.mkdirs();
    }
    private static String hash(String s){try{
        byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder out=new StringBuilder();for(int i=0;i<16;i++)out.append(String.format(java.util.Locale.ROOT,"%02x",bytes[i]&255));return out.toString();
    }catch(Exception e){return Integer.toHexString(s.hashCode());}}
    private File file(int chapter,int page){return new File(directory,"chapter_"+chapter+"_page_"+page+".json");}
    synchronized JSONObject load(int chapter,int page){try{File f=file(chapter,page);if(!f.isFile())return null;
        return new JSONObject(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));
    }catch(Exception e){return null;}}
    synchronized void save(int chapter,int page,JSONObject value)throws Exception{
        File target=file(chapter,page),tmp=new File(target.getPath()+".tmp");
        Files.write(tmp.toPath(),value.toString().getBytes(StandardCharsets.UTF_8));
        if(!tmp.renameTo(target)){
            Files.copy(tmp.toPath(),target.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);tmp.delete();
        }
    }
    static JSONObject block(String text,float left,float top,float right,float bottom)throws Exception{
        JSONObject b=new JSONObject();b.put("text",text);JSONArray a=new JSONArray();
        a.put(left);a.put(top);a.put(right);a.put(bottom);b.put("bbox",a);return b;
    }
}
