#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/android/content" "$T/src/org/json" "$T/src/ru/reader/rsvp"
cat > "$T/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
import java.util.Set;
public interface SharedPreferences{
 String getString(String k,String d); Set<String> getStringSet(String k,Set<String> d);
 Editor edit();interface Editor{
  Editor putString(String k,String value);Editor putStringSet(String k,Set<String> values);boolean commit();
 }
}
JAVA
cat > "$T/src/android/content/Context.java" <<'JAVA'
package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final Map<String,Object> data=new HashMap<>();
 private final SharedPreferences pref=new SharedPreferences(){
  public String getString(String k,String d){Object v=data.get(k);return v instanceof String?(String)v:d;}
  public Set<String> getStringSet(String k,Set<String> d){Object v=data.get(k);return v instanceof Set?(Set<String>)v:d;}
  public Editor edit(){return new Editor(){Map<String,Object> updates=new HashMap<>();
   public Editor putString(String k,String v){updates.put(k,v);return this;}
   public Editor putStringSet(String k,Set<String> v){updates.put(k,new HashSet<>(v));return this;}
   public boolean commit(){data.putAll(updates);return true;}
  };}
 };
 public SharedPreferences getSharedPreferences(String k,int mode){return pref;}
}
JAVA
cat > "$T/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
import java.util.*;
public class JSONObject{
 private static final Map<String,Map<String,Object>> saved=new HashMap<>();
 private static long id;
 private final Map<String,Object> data=new HashMap<>();
 public JSONObject(){}
 public JSONObject(String text){Map<String,Object> row=saved.get(text);if(row==null)throw new IllegalArgumentException(text);data.putAll(row);}
 public JSONObject put(String k,Object v){data.put(k,v);return this;}
 public String optString(String k,String d){Object v=data.get(k);return v==null?d:v.toString();}
 public long optLong(String k,long d){Object v=data.get(k);return v instanceof Number?((Number)v).longValue():d;}
 public String toString(){String key="object#"+(++id);saved.put(key,new HashMap<>(data));return key;}
}
JAVA
cat > "$T/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
import java.util.*;
public class JSONArray {
 private static final Map<String,List<String>> saved=new HashMap<>();
 private static int id;
 private final List<String> items=new ArrayList<>();
 public JSONArray(){}
 public JSONArray(String key){List<String> rows=saved.get(key);if(rows==null)throw new IllegalArgumentException(key);items.addAll(rows);}
 public JSONArray put(JSONObject object){items.add(object.toString());return this;}
 public int length(){return items.size();}
 public JSONObject optJSONObject(int i){return i<0||i>=items.size()?null:new JSONObject(items.get(i));}
 public String toString(){String key="array#"+(++id);saved.put(key,new ArrayList<>(items));return key;}
}
JAVA
cat > "$T/src/ru/reader/rsvp/DownloadsRepository.java" <<'JAVA'
package ru.reader.rsvp;
final class DownloadsRepository{static final class Item{
 String id="",title="",type="",cover="",description="",source="",bookUri="";boolean ready;
}}
JAVA
cat > "$T/src/ru/reader/rsvp/ArchiveProbe.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
public class ArchiveProbe {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  Context ctx=new Context();
  DownloadsRepository.Item one=new DownloadsRepository.Item();one.id="edge:bookA";one.title="Глава A";one.type="edge";one.bookUri="A";one.ready=true;
  DownloadsRepository.Item two=new DownloadsRepository.Item();two.id="audio:bookB";two.title="Книга B";two.ready=true;
  check(DownloadArchiveStore.archive(ctx,one),"archive A failed");
  check(DownloadArchiveStore.archive(ctx,two),"archive B failed");
  check(DownloadArchiveStore.archive(ctx,one),"duplicate archive failed");
  check(DownloadArchiveStore.entries(ctx).size()==2,"deduplication");
  check(DownloadArchiveStore.hiddenIds(ctx).contains(one.id),"A must be suppressed");
  check(DownloadArchiveStore.restore(ctx,one.id),"restore failed");
  check(!DownloadArchiveStore.hiddenIds(ctx).contains(one.id),"restoration leaked tombstone");
  check(DownloadArchiveStore.entries(ctx).size()==1,"wrong entry retained");
  check(DownloadArchiveStore.clear(ctx),"clear failed");
  check(DownloadArchiveStore.entries(ctx).isEmpty(),"archive not empty after clear");
  check(DownloadArchiveStore.hiddenIds(ctx).contains(two.id),"clear made file reappear");
  check(!DownloadArchiveStore.restore(ctx,two.id),"cleared record should not be restorable");
  check(DownloadArchiveStore.archive(ctx,two),"reimport should rearchive");
  check(DownloadArchiveStore.entries(ctx).size()==1,"rearchive state");
  check(DownloadArchiveStore.removedFiles(ctx,two.id),"record removal after file delete");
  check(DownloadArchiveStore.entries(ctx).isEmpty(),"deleted item remains visible");
  DownloadsRepository.Item active=new DownloadsRepository.Item();active.id="audio:active";active.ready=false;
  check(!DownloadArchiveStore.archive(ctx,active),"active download must not archive");
  System.out.println("PASS: v153 logical archive, restore, dedup, clear tombstones, deletion and incomplete guard");
 }
}
JAVA
mkdir -p "$T/bin"
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$T/src" -name '*.java') app/src/main/java/ru/reader/rsvp/DownloadArchiveStore.java
java -cp "$T/bin" ru.reader.rsvp.ArchiveProbe
