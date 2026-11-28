#!/usr/bin/env python3
"""Typecheck new migration code with Android/JSON API-shaped doubles (not integration)."""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
stubs={
'android/content/Context.java': 'package android.content; public class Context {public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String n,int m){return null;}}',
'android/content/SharedPreferences.java': '''package android.content; public interface SharedPreferences {boolean getBoolean(String k,boolean v);int getInt(String k,int d);String getString(String k,String d);java.util.Set<String> getStringSet(String k,java.util.Set<String> d);boolean contains(String k);Editor edit();interface Editor{Editor putBoolean(String k,boolean v);Editor putInt(String k,int v);Editor putString(String k,String v);Editor putStringSet(String k,java.util.Set<String> v);Editor remove(String k);boolean commit();}}''',
'android/content/ContentValues.java': 'package android.content; public class ContentValues {public void put(String k,String v){}public void put(String k,Integer v){}public void put(String k,Long v){}}',
'android/database/Cursor.java': 'package android.database; public interface Cursor extends AutoCloseable{boolean moveToNext();long getLong(int i);int getInt(int i);String getString(int i);void close();}',
'android/database/sqlite/SQLiteOpenHelper.java': 'package android.database.sqlite;public abstract class SQLiteOpenHelper implements AutoCloseable{public SQLiteOpenHelper(android.content.Context c,String n,Object f,int v){}public abstract void onCreate(SQLiteDatabase d);public abstract void onUpgrade(SQLiteDatabase d,int a,int b);public SQLiteDatabase getReadableDatabase(){return null;}public SQLiteDatabase getWritableDatabase(){return null;}public void close(){}}',
'android/database/sqlite/SQLiteDatabase.java': 'package android.database.sqlite;public class SQLiteDatabase{public void execSQL(String s){}public android.database.Cursor query(String t,String[] c,String s,String[] a,String g,String h,String o){return null;}public int update(String t,android.content.ContentValues v,String w,String[] a){return 1;}public long insertOrThrow(String t,String n,android.content.ContentValues v){return 1;}public int delete(String t,String w,String[] a){return 1;}public void beginTransaction(){}public void setTransactionSuccessful(){}public void endTransaction(){}}',
'org/json/JSONArray.java':'package org.json;public class JSONArray{public JSONArray(String s)throws Exception{}public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}}',
'org/json/JSONObject.java':'package org.json;public class JSONObject{public int optInt(String k,int d){return d;}public String optString(String k){return "";}public JSONObject put(String k,Object v)throws Exception{return this;}}',
'ru/reader/rsvp/Deps.java':'''package ru.reader.rsvp;import android.content.*;class BookLoader{static class Book{String text;BookChapterIndex chapters;}}class ReaderBookCache{static class CachedBook{String text;ReadingWordMap map;BookChapterIndex chapters;}}class BookUriMigration{static String legacy(Context c,String u){return u;}}class UnifiedReadingProgress{static int read(SharedPreferences p,String u,int i){return i;}}class SentenceChunks{static class Chunk{int fromWord;}static java.util.ArrayList<Chunk> split(String s){return new java.util.ArrayList<>();}}'''
}
with tempfile.TemporaryDirectory() as d:
 t=Path(d)
 for n,s in stubs.items():
  p=t/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
 subprocess.run(['javac','-encoding','UTF-8','-d',str(t/'classes'),*[str(t/n) for n in stubs],*[str(src/n) for n in ['ReaderTextMigration.java','ReaderAnnotationsStore.java','ReaderTextReanchor.java','ReadingWordMap.java','BookChapterIndex.java']]],check=True)
print('PASS: migration and annotation store Java types against Android/JSON API-shaped doubles')
