package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** A single word cursor for reading, RSVP, cursor, local TTS and Edge TTS.
 * Durations and WPM are never used as a source for book completion.
 */
public final class UnifiedReadingProgress {
    private UnifiedReadingProgress() {}
    private static String key(String uri){ return "book_shared_word:"+uri; }
    public static int read(SharedPreferences p,String uri,int fallback){
        if(uri==null||uri.isEmpty())return Math.max(0,fallback);
        // Migrate from v1.11.130: the last active mode determines which old
        // cursor was authoritative; subsequent writes use only the shared key.
        int current=p.getInt(key(uri),-1);
        if(current>=0)return current;
        int reading=p.getInt("position:"+uri,fallback);
        return Math.max(0,p.getInt("reading_mode",0)==3
                ?p.getInt("tts_word:"+uri,reading):reading);
    }
    public static synchronized void save(Context context,String uri,int word,int wordCount,String chapter,boolean syncLibrary){
        if(uri==null||uri.isEmpty())return;
        SharedPreferences p=context.getSharedPreferences("reader",Context.MODE_PRIVATE);
        int current=Math.max(0,word),total=Math.max(0,wordCount);
        if(total>0)current=Math.min(current,total-1);
        SharedPreferences.Editor e=p.edit().putInt(key(uri),current)
                .putInt("position:"+uri,current).putInt("tts_word:"+uri,current);
        if(uri.equals(p.getString("uri","")))e.putInt("index",current);
        if(total>0)e.putInt("book_words:"+uri,total);
        if(chapter!=null&&!chapter.isEmpty())e.putString("book_shared_chapter:"+uri,chapter);
        if(syncLibrary){
            try {
                JSONArray books=new JSONArray(p.getString("library","[]"));
                for(int i=0;i<books.length();i++){
                    JSONObject book=books.optJSONObject(i);
                    if(book!=null&&uri.equals(book.optString("uri"))){
                        book.put("index",current);
                        if(total>0){book.put("wordCount",total);book.put("progress",(int)Math.min(100,(current+1L)*100/total));}
                        if(chapter!=null&&!chapter.isEmpty())book.put("chapter",chapter);
                        book.put("openedAt",System.currentTimeMillis());
                        e.putString("library",books.toString());
                        break;
                    }
                }
            }catch(Exception ignored){}
        }
        e.apply();
    }
}
