package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Small cached chapter-to-word lookup shared by bookmark/note lists. No HTML parsing on list open. */
final class ReaderChapterLabels {
    private static final String PREFIX="reader_chapter_labels_v162:";
    private ReaderChapterLabels(){}
    static void saveIfMissing(Context c,String uri,ReadingWordMap words,BookChapterIndex chapters){
        if(uri==null||uri.isEmpty()||chapters==null||chapters.size()==0||words==null||words.size()==0)return;
        String key=PREFIX+BookUriMigration.legacy(c,uri);
        SharedPreferences p=c.getSharedPreferences("reader",0);
        BookChapterIndex.Chapter first=chapters.get(0),last=chapters.get(chapters.size()-1);
        // Constant-time revision check avoids re-indexing large books on every return,
        // but a deliberate manual HTML rebuild refreshes chapter labels on next load.
        String revision=words.size()+":"+chapters.size()+":"+first.startChar+":"+first.title+
            ":"+last.startChar+":"+last.title;
        if(revision.equals(p.getString(key+":revision",""))&&p.contains(key))return;
        JSONArray arr=new JSONArray();
        for(int i=0;i<chapters.size();i++)try{
            BookChapterIndex.Chapter chapter=chapters.get(i);
            JSONObject row=new JSONObject();row.put("word",words.atCharacter(chapter.startChar));row.put("name",chapter.title);
            arr.put(row);
        }catch(Exception ignored){}
        p.edit().putString(key,arr.toString()).putString(key+":revision",revision).apply();
    }
    static String atWord(Context c,String uri,int word){
        if(uri==null||uri.isEmpty())return "";
        String raw=c.getSharedPreferences("reader",0).getString(PREFIX+BookUriMigration.legacy(c,uri),"");
        if(raw.isEmpty())return "";
        try{
            JSONArray rows=new JSONArray(raw);String found="";
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                if(row.optInt("word",0)>word)break;
                found=row.optString("name","");
            }
            return found;
        }catch(Exception ignored){return "";}
    }
}
