#!/bin/sh
# Compile the REAL EdgeFullBookStore against Android API stand-ins and exercise
# per-chapter FIFO queue, deduplication, retry, pause and auto-planning.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
S="$T/src"; mkdir -p "$S/android/content" "$S/org/json" "$S/ru/reader/rsvp"
cat > "$S/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 private final java.io.File root;
 public Context(java.io.File f){root=f;}
 public java.io.File getFilesDir(){return root;}
}
JAVA
cat > "$S/org/json/JSONObject.java" <<'JAVA'
package org.json;
import java.util.*;
/** Test-only in-memory surrogate for JSONObject snapshots across file writes. */
public class JSONObject{
 private static final Map<String,Map<String,Object>> snapshots=new HashMap<>();
 private static long sequence;
 private final Map<String,Object> map=new LinkedHashMap<>();
 public JSONObject(){}
 public JSONObject(String text){Map<String,Object> saved=snapshots.get(text);
  if(saved==null)throw new IllegalArgumentException("Invalid JSON test snapshot "+text);
  for(Map.Entry<String,Object> e:saved.entrySet())map.put(e.getKey(),copy(e.getValue()));
 }
 static Object copy(Object o){if(o instanceof JSONObject){JSONObject n=new JSONObject();
   for(Map.Entry<String,Object> e:((JSONObject)o).map.entrySet())n.map.put(e.getKey(),copy(e.getValue()));return n;}
  if(o instanceof JSONArray)return ((JSONArray)o).duplicate();return o;}
 public JSONObject put(String k,Object o){map.put(k,o);return this;}
 public String optString(String k){return optString(k,"");}
 public String optString(String k,String def){Object v=map.get(k);return v==null?def:String.valueOf(v);}
 public int optInt(String k){return optInt(k,0);}
 public int optInt(String k,int d){Object v=map.get(k);return v instanceof Number?((Number)v).intValue():d;}
 public long optLong(String k){return optLong(k,0);}
 public long optLong(String k,long d){Object v=map.get(k);return v instanceof Number?((Number)v).longValue():d;}
 public boolean optBoolean(String k,boolean d){Object v=map.get(k);return v instanceof Boolean?(boolean)v:d;}
 public JSONArray optJSONArray(String k){Object v=map.get(k);return v instanceof JSONArray?(JSONArray)v:null;}
 public String getString(String k){return optString(k);}
 @Override public String toString(){String id="__TEST_JSON_"+(++sequence);
  Map<String,Object> save=new LinkedHashMap<>();for(Map.Entry<String,Object> e:map.entrySet())save.put(e.getKey(),copy(e.getValue()));
  snapshots.put(id,save);return id;}
}
JAVA
cat > "$S/org/json/JSONArray.java" <<'JAVA'
package org.json;
import java.util.*;
public class JSONArray {
 private final List<Object> list=new ArrayList<>();
 public JSONArray(){}
 public JSONArray(String ignored){}
 public JSONArray put(Object o){list.add(o);return this;}
 public int length(){return list.size();}
 public JSONObject optJSONObject(int i){return i>=0&&i<list.size()&&list.get(i) instanceof JSONObject?(JSONObject)list.get(i):null;}
 public JSONObject getJSONObject(int i){return optJSONObject(i);}
 public JSONArray duplicate(){JSONArray a=new JSONArray();for(Object o:list)a.put(JSONObject.copy(o));return a;}
}
JAVA
cat > "$S/ru/reader/rsvp/EdgeFullBookService.java" <<'JAVA'
package ru.reader.rsvp;
public class EdgeFullBookService { static boolean isServiceAlive(){return true;} }
JAVA
cat > "$S/ru/reader/rsvp/EdgeBookStorage.java" <<'JAVA'
package ru.reader.rsvp;
public class EdgeBookStorage { static java.io.File bookFolder(android.content.Context c,String uri,String title){
 return new java.io.File(c.getFilesDir(),"working/"+Integer.toHexString(uri.hashCode()));} }
JAVA
cat > "$S/ru/reader/rsvp/DownloadsRepository.java" <<'JAVA'
package ru.reader.rsvp;
public class DownloadsRepository {static void storeEdgeMeta(java.io.File f,String u,String t,int n){}static void showEdge(android.content.Context c,String u){} }
JAVA
cat > "$S/ru/reader/rsvp/EdgeSpeechSegments.java" <<'JAVA'
package ru.reader.rsvp;
public class EdgeSpeechSegments {static final int MAX=900;}
JAVA
cat > "$S/ru/reader/rsvp/BookChapterIndex.java" <<'JAVA'
package ru.reader.rsvp;
public class BookChapterIndex {int n;BookChapterIndex(int count){n=count;}int size(){return n;}}
JAVA
cat > "$S/ru/reader/rsvp/EdgeChapterArchive.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
public class EdgeChapterArchive {static class ChapterSnapshot{
 final Map<Integer,Integer> states=new HashMap<>();int state(int n){return states.getOrDefault(n,0);}}
}
JAVA
cat > "$S/ru/reader/rsvp/QueueProbe.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
import java.io.File;
public class QueueProbe{
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  Context ctx=new Context(new File(args[0]));
  EdgeFullBookStore.Job j=EdgeFullBookStore.create(ctx,"book1","Книга","voice","+0%",900);
  EdgeFullBookStore.chooseChapter(j,0);
  EdgeFullBookStore.chooseChapter(j,1);
  EdgeFullBookStore.chooseChapter(j,2);
  EdgeFullBookStore.chooseChapter(j,1);
  j=EdgeFullBookStore.find(ctx,"book1","Книга");
  check(j.tasks.size()==3,"Chapter selection overwrote/doubled earlier chapter");
  check(EdgeFullBookStore.QUEUED.equals(EdgeFullBookStore.task(j,0).status),"First chapter lost queued state");
  EdgeFullBookStore.Job work=EdgeFullBookStore.next(ctx);
  check(work!=null&&work.chapter==0,"FIFO first chapter was not zero");
  EdgeFullBookStore.status(work,EdgeFullBookStore.RUNNING);
  EdgeFullBookStore.progress(work,3,10,null);
  j=EdgeFullBookStore.find(ctx,"book1","Книга");
  check(EdgeFullBookStore.task(j,0).done==3&&EdgeFullBookStore.task(j,1).done==0,"Chapter progress leaked");
  EdgeFullBookStore.status(work,EdgeFullBookStore.DONE);
  check(EdgeFullBookStore.next(ctx).chapter==1,"Second chapter should follow first");
  EdgeFullBookStore.stopChapter(ctx,"book1","Книга",1);
  check(EdgeFullBookStore.next(ctx).chapter==2,"Cancel chapter must not stop other tasks");
  EdgeFullBookStore.pauseAll(j);
  check(EdgeFullBookStore.next(ctx)==null,"Paused work resumed without command");
  EdgeFullBookStore.resumeAll(j);
  j=EdgeFullBookStore.find(ctx,"book1","Книга");
  check(j.autoContinue,"Resume did not enable auto-queue for remaining chapters");
  EdgeChapterArchive.ChapterSnapshot saved=new EdgeChapterArchive.ChapterSnapshot();
  saved.states.put(0,2);saved.states.put(2,1);
  EdgeFullBookStore.Job planner=EdgeFullBookStore.next(ctx);
  check(planner!=null&&planner.chapter==EdgeFullBookStore.PLAN,"Continue must plan all missing chapters");
  EdgeFullBookStore.planMissing(planner,new BookChapterIndex(5),saved);
  j=EdgeFullBookStore.find(ctx,"book1","Книга");
  check(EdgeFullBookStore.task(j,3)!=null&&EdgeFullBookStore.task(j,4)!=null,"Missing chapters not auto-enqueued");
  check(EdgeFullBookStore.task(j,0).status.equals(EdgeFullBookStore.DONE),"Finished chapter requeued");
  // Repeating the planner cannot duplicate a chapter.
  EdgeFullBookStore.chooseChapter(j,-1);
  planner=EdgeFullBookStore.next(ctx);EdgeFullBookStore.planMissing(planner,new BookChapterIndex(5),saved);
  j=EdgeFullBookStore.find(ctx,"book1","Книга");
  int count=0;for(EdgeFullBookStore.Task t:j.tasks)if(t.chapter==4)count++;
  check(count==1,"Planner duplicated a chapter");
  EdgeFullBookStore.Job other=EdgeFullBookStore.create(ctx,"book2","Другая книга","voice","+0%",900);
  EdgeFullBookStore.chooseChapter(other,0);
  check(EdgeFullBookStore.list(ctx).size()==2,"Distinct books share a task queue");
  check(EdgeFullBookStore.task(EdgeFullBookStore.find(ctx,"book1","Книга"),4)!=null,
      "Enqueuing another book erased the first book queue");
  System.out.println("PASS: real EdgeFullBookStore queue, FIFO, dedupe, pause, resume, planner, persisted progress");
 }
}
JAVA
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" \
 $(find "$S" -name '*.java') app/src/main/java/ru/reader/rsvp/EdgeFullBookStore.java
java -cp "$T/bin" ru.reader.rsvp.QueueProbe "$T/files"
