package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

/** Replace a book URI without changing its progress, bookmarks or user-facing title. */
final class BookUriMigration {
    private BookUriMigration() { }

    static synchronized void text(Context ctx,String oldUri,String newUri)throws IOException {
        if(oldUri==null||newUri==null||oldUri.isEmpty()||newUri.isEmpty())
            throw new IOException("Неверный адрес книги");
        if(oldUri.equals(newUri))return;
        SharedPreferences prefs=ctx.getSharedPreferences("reader",0);
        JSONArray books;
        try{books=new JSONArray(prefs.getString("library","[]"));}catch(Exception e){throw new IOException("Библиотека повреждена",e);}
        boolean matched=false;
        try{
            for(int i=0;i<books.length();i++){
                JSONObject book=books.optJSONObject(i);
                if(book!=null&&oldUri.equals(book.optString("uri"))){book.put("uri",newUri);matched=true;}
            }
        }catch(Exception ex){throw new IOException("Не удалось обновить запись книги",ex);}
        if(!matched)throw new IOException("Книга не найдена в Библиотеке; исходник сохранён");
        String canonical=legacy(ctx,oldUri);
        if(!ctx.getSharedPreferences("book_uri_aliases_v158",0).edit()
            .putString(newUri,canonical).commit())throw new IOException("Не сохранена привязка Edge TTS к перенесённой книге");
        SharedPreferences.Editor editor=prefs.edit().putString("library",books.toString());
        if(oldUri.equals(prefs.getString("uri","")))editor.putString("uri",newUri);
        // Reader, RSVP, Edge TTS and bookmark state share a suffix-based book identity.
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
            String key=entry.getKey();
            if(!key.endsWith(oldUri)||!rekeyable(key))continue;
            String after=key.substring(0,key.length()-oldUri.length())+newUri;
            Object value=entry.getValue();
            if(value instanceof String)editor.putString(after,(String)value);
            else if(value instanceof Integer)editor.putInt(after,(Integer)value);
            else if(value instanceof Long)editor.putLong(after,(Long)value);
            else if(value instanceof Boolean)editor.putBoolean(after,(Boolean)value);
            else if(value instanceof Float)editor.putFloat(after,(Float)value);
            else if(value instanceof Set){
                @SuppressWarnings("unchecked") Set<String> s=(Set<String>)value;
                editor.putStringSet(after,new java.util.HashSet<>(s));
            }
        }
        if(!editor.commit())throw new IOException("Не удалось сохранить Библиотеку");
        BookTitleStore.moveTextId(ctx,oldUri,newUri);
        EdgeChapterArchive.invalidate();
    }
    /** Physical Edge TTS jobs, saved chapter manifests and cached MP3 are keyed by original URI. */
    static String legacy(Context ctx,String uri) {
        if(uri==null||uri.isEmpty())return uri;
        return ctx.getSharedPreferences("book_uri_aliases_v158",0).getString(uri,uri);
    }
    /** Return a currently available library URI for old Edge TTS metadata. */
    static String current(Context ctx,String oldUri) {
        try {
            JSONArray books=new JSONArray(ctx.getSharedPreferences("reader",0).getString("library","[]"));
            for(int i=0;i<books.length();i++) {
                JSONObject row=books.optJSONObject(i);
                if(row==null)continue;
                String candidate=row.optString("uri","");
                if(oldUri.equals(legacy(ctx,candidate)))return candidate;
            }
        }catch(Exception ignored){}
        return oldUri;
    }
    private static boolean rekeyable(String key){
        String[] prefixes={"position:","tts_word:","viewport:","book_words:",
            "book_shared_word:","book_shared_chapter:","book_title:","book_author:",
            "bookmarks_v2:","bookmark:","book_usage:","reading_position:",
            "reader_scroll:","reader_chapter:","edge:"};
        for(String prefix:prefixes)if(key.startsWith(prefix))return true;
        return false;
    }
}
