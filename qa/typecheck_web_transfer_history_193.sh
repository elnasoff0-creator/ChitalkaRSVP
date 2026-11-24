#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/src/android/content" "$tmp/src/android/os" "$tmp/src/org/json" "$tmp/src/ru/reader/rsvp"
cat > "$tmp/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback);
 Editor edit();
 interface Editor {Editor putString(String key,String value);boolean commit();}
}
JAVA
cat > "$tmp/src/android/content/Context.java" <<'JAVA'
package android.content;
import java.util.HashMap;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final HashMap<String,SharedPreferences> preferences=new HashMap<>();
 public SharedPreferences getSharedPreferences(String key,int mode){
  return preferences.computeIfAbsent(key,k->new Memory());
 }
 static final class Memory implements SharedPreferences {
  final HashMap<String,String> items=new HashMap<>();
  public String getString(String k,String fallback){return items.getOrDefault(k,fallback);}
  public Editor edit(){return new Editor(){
   public Editor putString(String key,String value){items.put(key,value);return this;}
   public boolean commit(){return true;}
  };}
 }
}
JAVA
cat > "$tmp/src/android/os/SystemClock.java" <<'JAVA'
package android.os;public final class SystemClock {
 public static long elapsedRealtime(){return System.currentTimeMillis();}
}
JAVA
cat > "$tmp/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
import java.util.HashMap;
public class JSONObject {
 final HashMap<String,Object> items=new HashMap<>();
 public JSONObject put(String k,Object v){items.put(k,v);return this;}
 public String optString(String k,String fallback){Object x=items.get(k);return x==null?fallback:x.toString();}
 public long optLong(String k,long fallback){Object x=items.get(k);return x instanceof Number?((Number)x).longValue():fallback;}
}
JAVA
cat > "$tmp/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
import java.util.*;
public class JSONArray {
 static final HashMap<String,ArrayList<JSONObject>> records=new HashMap<>();
 final ArrayList<JSONObject> items=new ArrayList<>();
 public JSONArray(){}
 public JSONArray(String wire){ArrayList<JSONObject> a=records.get(wire);if(a!=null)items.addAll(a);}
 public JSONArray put(JSONObject o){items.add(o);return this;}
 public int length(){return items.size();}
 public JSONObject optJSONObject(int index){return index>=0&&index<items.size()?items.get(index):null;}
 public String toString(){String id="wire:"+java.util.UUID.randomUUID();records.put(id,new ArrayList<>(items));return id;}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/DownloadChangeBus.java" <<'JAVA'
package ru.reader.rsvp;
final class DownloadChangeBus {
 static int changes;
 static void publish(String id){if(id!=null&&!id.isEmpty())changes++;}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/TestWebTransferHistory193.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
public class TestWebTransferHistory193 {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
  Context c=new Context();String id=WebTransferDownloadStore.newId();
  check(WebTransferDownloadStore.begin(c,id,"Моя ё-книга.epub","book",2048),"begin");
  check(!WebTransferDownloadStore.begin(c,id,"copy.epub","book",2048),"duplicate id");
  WebTransferDownloadStore.progress(c,id,1024,2048);
  check(WebTransferDownloadStore.one(c,id).received==1024,"byte progress");
  WebTransferDownloadStore.importing(c,id);
  check(WebTransferDownloadStore.IMPORTING.equals(WebTransferDownloadStore.one(c,id).state),"import status");
  check(!WebTransferDownloadStore.cancel(c,id),"cannot cancel after import starts");
  WebTransferDownloadStore.finish(c,id,WebTransferDownloadStore.DONE,"","content://book/1",2048);
  check(WebTransferDownloadStore.DONE.equals(WebTransferDownloadStore.one(c,id).state),"completed");
  check(WebTransferDownloadStore.one(c,id).uri.equals("content://book/1"),"saved URI");
  String cancel=WebTransferDownloadStore.newId();
  check(WebTransferDownloadStore.begin(c,cancel,"Манга.cbz","manga",100),"begin second");
  check(WebTransferDownloadStore.cancel(c,cancel),"cancel");
  WebTransferDownloadStore.finish(c,cancel,WebTransferDownloadStore.DONE,"late callback","",100);
  check(WebTransferDownloadStore.CANCELLED.equals(WebTransferDownloadStore.one(c,cancel).state),"cancel beats late success");
  String lost=WebTransferDownloadStore.newId();
  check(WebTransferDownloadStore.begin(c,lost,"audio.m4b","audio",100),"begin third");
  WebTransferDownloadStore.recover(c,false);
  check(WebTransferDownloadStore.ERROR.equals(WebTransferDownloadStore.one(c,lost).state),"stale job recovered");
  check(!WebTransferDownloadStore.remove(c,id+"bad"),"must not delete unknown job");
  check(WebTransferDownloadStore.remove(c,lost),"remove completed error history");
  check(WebTransferDownloadStore.one(c,lost)==null,"history removal");
  check(WebTransferDownloadStore.all(c).size()==2,"no duplicate rows");
  check(DownloadChangeBus.changes>=7,"dashboard invalidation");
  System.out.println("PASS: Wi-Fi transfer durable journal, byte progress, import, cancellation, idempotency and recovery");
 }
}
JAVA
javac -encoding UTF-8 -d "$tmp/bin" $(find "$tmp/src" -name '*.java') \
 "$root/app/src/main/java/ru/reader/rsvp/WebTransferDownloadStore.java"
java -cp "$tmp/bin" ru.reader.rsvp.TestWebTransferHistory193
