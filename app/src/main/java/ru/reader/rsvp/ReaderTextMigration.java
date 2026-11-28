package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** One-time migration when RBI4 is available for the same unchanged source. */
final class ReaderTextMigration {
    private ReaderTextMigration(){}
    static boolean migrate(Context c,String uri,ReaderBookCache.CachedBook previous,BookLoader.Book book,ReadingWordMap current)throws Exception{
        if(previous==null||previous.text.equals(book.text))return false;
        SharedPreferences p=c.getSharedPreferences("reader",0);
        String marker="reader_text_rbi5:"+uri;
        if(p.getBoolean(marker,false))return false;
        ReaderTextReanchor map=new ReaderTextReanchor(previous.map,current,previous.chapters,book.chapters);
        String identity=BookUriMigration.legacy(c,uri);
        try(ReaderAnnotationsStore store=new ReaderAnnotationsStore(c)){
            store.reanchorRbi5(identity,map,current);
        }
        SharedPreferences.Editor e=p.edit();
        for(String prefix:new String[]{"book_shared_word:","position:","tts_word:","viewport:"}){
            String key=prefix+uri;
            if(p.contains(key)){int old=p.getInt(key,-1);if(old>=0)e.putInt(key,map.word(old));}
        }
        if(uri.equals(p.getString("uri",""))&&p.contains("index"))e.putInt("index",map.word(p.getInt("index",0)));
        int oldLibraryIndex=0;
        try{JSONArray saved=new JSONArray(p.getString("library","[]"));for(int i=0;i<saved.length();i++){JSONObject row=saved.optJSONObject(i);if(row!=null&&uri.equals(row.optString("uri"))){oldLibraryIndex=row.optInt("index",0);break;}}}catch(Exception ignored){}
        int position=map.word(UnifiedReadingProgress.read(p,uri,oldLibraryIndex));
        e.putInt("book_shared_word:"+uri,position).putInt("position:"+uri,position).putInt("tts_word:"+uri,position);
        if(p.contains("tts_pos:"+uri)){
            java.util.ArrayList<SentenceChunks.Chunk> oldParts=SentenceChunks.split(previous.text),newParts=SentenceChunks.split(book.text);
            if(!oldParts.isEmpty()&&!newParts.isEmpty()){
                int chunk=Math.max(0,Math.min(oldParts.size()-1,p.getInt("tts_pos:"+uri,0)));
                int target=map.word(oldParts.get(chunk).fromWord),next=0;
                for(int i=0;i<newParts.size();i++){if(newParts.get(i).fromWord>target)break;next=i;}
                e.putInt("tts_pos:"+uri,next);
            }
        }
        e.putInt("book_words:"+uri,current.size());
        BookChapterIndex.Chapter chapter=book.chapters.atChar(current.starts[current.clamp(position)]);
        e.putString("book_shared_chapter:"+uri,chapter==null?"":chapter.title);
        String key="bookmarks_v2:"+uri;
        JSONArray marks=null;try{marks=new JSONArray(p.getString(key,"[]"));}catch(Exception ignored){}
        // Retain every original JSON record, including user text and IDs.
        for(int i=0;marks!=null&&i<marks.length();i++){
            JSONObject row=marks.optJSONObject(i);if(row==null)continue;
            int old=row.optInt("word",0),end=Math.max(old+1,row.optInt("endWord",old+1));
            int start=map.word(old),last=map.word(end-1);
            row.put("word",start).put("endWord",Math.max(start+1,last+1));
            BookChapterIndex.Chapter markChapter=book.chapters.atChar(current.starts[current.clamp(start)]);
            if(markChapter!=null)row.put("chapter",markChapter.title);
        }
        if(marks!=null&&p.contains(key))e.putString(key,marks.toString());
        String legacyKey="bookmark:"+uri;
        Set<String> oldMarks=p.getStringSet(legacyKey,java.util.Collections.emptySet());
        if(p.contains(legacyKey)){
            Set<String> migrated=new HashSet<>();
            for(String old:oldMarks)try{migrated.add(Integer.toString(map.word(Integer.parseInt(old))));}catch(NumberFormatException ex){migrated.add(old);}
            e.putStringSet(legacyKey,migrated);
        }
        JSONArray library=null;try{library=new JSONArray(p.getString("library","[]"));}catch(Exception ignored){}
        for(int i=0;library!=null&&i<library.length();i++){
            JSONObject row=library.optJSONObject(i);
            if(row!=null&&uri.equals(row.optString("uri"))){
                row.put("index",position).put("wordCount",current.size())
                    .put("progress",Math.min(100,(position+1L)*100/current.size()));
                if(chapter!=null)row.put("chapter",chapter.title);
            }
        }
        if(library!=null&&p.contains("library"))e.putString("library",library.toString());
        e.remove("reader_chapter_labels_v162:"+identity).remove("reader_chapter_labels_v162:"+identity+":revision");
        if(!e.putBoolean(marker,true).commit())throw new IOException("Не удалось сохранить перенос позиций книги");
        return true;
    }
}
