#!/bin/sh
# Compile the ACTUAL v152 Edge background service and durable job store
# against narrowly scoped Android/third-party API signature stand-ins.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src"
python3 - "$T/src" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])
stubs={
'android/content/Context.java':'''package android.content; public class Context{
 public java.io.File getFilesDir(){return new java.io.File(".");}
 public SharedPreferences getSharedPreferences(String a,int b){return null;}
 public <T> T getSystemService(Class<T> cls){return null;}
 public Object getSystemService(String name){return null;}
}''',
'android/content/SharedPreferences.java':'''package android.content;public interface SharedPreferences{
 long getLong(String name,long def);
 interface Editor{Editor putLong(String name,long value);void apply();}
 Editor edit();}''',
'android/content/Intent.java':'''package android.content;public class Intent{
 public Intent(Context c,Class<?> a){}
 public String getStringExtra(String name){return null;}
 public String getAction(){return null;}
 public int getIntExtra(String key,int def){return def;}
 public boolean getBooleanExtra(String key,boolean def){return def;}
}''',
'android/net/Uri.java':'''package android.net;public class Uri{public static Uri parse(String u){return null;}}''',
'android/os/Build.java':'''package android.os;public class Build{public static class VERSION{public static int SDK_INT=35;}}''',
'android/os/IBinder.java':'''package android.os;public interface IBinder{}''',
'android/util/Log.java':'''package android.util;public class Log{
 public static int w(String a,String b){return 0;}
 public static int w(String a,String b,Throwable x){return 0;}
 public static int i(String a,String b){return 0;}
}''',
'android/app/Notification.java':'''package android.app;public class Notification{}''',
'android/app/NotificationChannel.java':'''package android.app;public class NotificationChannel{
 public NotificationChannel(String id,String name,int importance){}
}''',
'android/app/NotificationManager.java':'''package android.app;public class NotificationManager{
 public static final int IMPORTANCE_LOW=2;
 public void createNotificationChannel(NotificationChannel n){}
 public void notify(int id,Notification n){}
}''',
'android/app/PendingIntent.java':'''package android.app;public class PendingIntent{
 public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;
 public static PendingIntent getActivity(android.content.Context c,int req,android.content.Intent i,int flags){return null;}
}''',
'android/app/Service.java':'''package android.app;public class Service extends android.content.Context{
 public static final int START_REDELIVER_INTENT=1,STOP_FOREGROUND_REMOVE=1;
 public static final String NOTIFICATION_SERVICE="notification";
 public void onCreate(){}
 public android.os.IBinder onBind(android.content.Intent intent){return null;}
 public int onStartCommand(android.content.Intent intent,int flags,int id){return 0;}
 public void startForeground(int id,Notification notification){}
 public void stopForeground(int flags){}
 public void stopSelf(){}
 public void onTimeout(int id,int type){}
 public void onDestroy(){}
}''',
'androidx/core/app/NotificationCompat.java':'''package androidx.core.app;
public class NotificationCompat{
 public static class Builder{
 public Builder(android.content.Context c,String channel){}
 public Builder setSmallIcon(int id){return this;}
 public Builder setContentTitle(String text){return this;}
 public Builder setContentText(String text){return this;}
 public Builder setContentIntent(android.app.PendingIntent p){return this;}
 public Builder setOnlyAlertOnce(boolean b){return this;}
 public Builder setOngoing(boolean b){return this;}
 public Builder setProgress(int a,int b,boolean c){return this;}
 public android.app.Notification build(){return null;}
 }
}''',
'org/json/JSONObject.java':'''package org.json;public class JSONObject{
 public JSONObject(){}public JSONObject(String s){}
 public JSONObject put(String s,Object v){return this;}
 public String optString(String s,String def){return def;}
 public String optString(String s){return "";}
 public int optInt(String s,int def){return def;}
 public int optInt(String s){return 0;}
 public long optLong(String s,long def){return def;}
 public boolean optBoolean(String s,boolean def){return def;}
 public JSONArray optJSONArray(String s){return null;}
 public String getString(String s){return "";}
 public String toString(){return "{}";}
}''',
'org/json/JSONArray.java':'''package org.json;public class JSONArray{
 public JSONArray(){}public JSONArray(String s){}
 public int length(){return 0;}
 public JSONObject optJSONObject(int i){return null;}
 public JSONObject getJSONObject(int i){return null;}
 public JSONArray put(Object value){return this;}
 public String toString(){return "[]";}
}''',
'ru/reader/rsvp/R.java':'''package ru.reader.rsvp;public class R{public static class drawable{public static final int ic_reader=1;}}''',
'ru/reader/rsvp/DownloadActivity.java':'''package ru.reader.rsvp;public class DownloadActivity{}''',
'ru/reader/rsvp/EdgeSpeechSegments.java':'''package ru.reader.rsvp;public class EdgeSpeechSegments{public static final int MAX=900;}''',
'ru/reader/rsvp/EdgeBookStorage.java':'''package ru.reader.rsvp;public class EdgeBookStorage{
 public static java.io.File bookFolder(android.content.Context c,String u,String t){return new java.io.File(".");}
}''',
'ru/reader/rsvp/DownloadsRepository.java':'''package ru.reader.rsvp;public class DownloadsRepository{
 public static void showEdge(android.content.Context c,String u){}
 public static void storeEdgeMeta(java.io.File b,String u,String t,int words){}
}''',
'ru/reader/rsvp/EdgeSegmentSettings.java':'''package ru.reader.rsvp;public class EdgeSegmentSettings{
 public static final int DEFAULT=900;
 public static int clamp(int number){return number;}
}''',
'ru/reader/rsvp/BookChapterIndex.java':'''package ru.reader.rsvp;public class BookChapterIndex{
 public static class Chapter{public int startChar,endChar;public String title;}
 public int size(){return 0;}
 public Chapter get(int i){return null;}
}''',
'ru/reader/rsvp/BookLoader.java':'''package ru.reader.rsvp;public class BookLoader{
 public static class Book{public String text="";public BookChapterIndex chapters=new BookChapterIndex();}
 public static Book load(android.content.Context c,android.net.Uri uri){return null;}
}''',
'ru/reader/rsvp/SentenceChunks.java':'''package ru.reader.rsvp;public class SentenceChunks{
 public static int lowerBound(java.util.ArrayList<Integer> positions,int chars){return 0;}
}''',
'ru/reader/rsvp/EdgeTextSegments.java':'''package ru.reader.rsvp;public class EdgeTextSegments{
 public static class Part{public int start,end;public String text="";}
 public static java.util.ArrayList<Part> all(String raw,BookChapterIndex chapters,int max){return new java.util.ArrayList<>();}
 public static java.util.ArrayList<Integer> positions(String raw){return new java.util.ArrayList<>();}
}''',
'ru/reader/rsvp/EdgeSpeechTextTransform.java':'''package ru.reader.rsvp;public class EdgeSpeechTextTransform{
 public static class Options{public Result apply(String raw){return null;}}
 public static class Result{public String spoken="";
 public boolean skipSynthesis(){return false;}
 public String cacheKey(String voice,String rate){return "";}
 public java.util.List<EdgeWordTimings.Boundary> aligned(java.util.List<EdgeWordTimings.Boundary> input){return null;}}
}''',
'ru/reader/rsvp/EdgeDictionaryStore.java':'''package ru.reader.rsvp;public class EdgeDictionaryStore{
 public static EdgeDictionaryStore get(android.content.Context c){return null;}
 public EdgeSpeechTextTransform.Options options(){return null;}
}''',
'ru/reader/rsvp/EdgeWordTimings.java':'''package ru.reader.rsvp;public class EdgeWordTimings{
 public static class Boundary{}
 public static void storeAligned(java.io.File f,String raw,java.util.List<Boundary> words){}
}''',
'ru/reader/rsvp/EdgeSpeechQueue.java':'''package ru.reader.rsvp;public class EdgeSpeechQueue{
 public static final int BULK=2;
 public static void restoreCooldown(long time){}
 public static long remainingMs(){return 0;}
 public static boolean interactivePlaybackActive(){return false;}
}''',
'ru/reader/rsvp/EdgeTtsClient.java':'''package ru.reader.rsvp;public class EdgeTtsClient{
 public interface Callback{
 void progress(int count,long bytes);
 void finished(byte[] audio,int chunks,String error);
 void finished(byte[] audio,int chunks,String error,java.util.List<EdgeWordTimings.Boundary> words);
 }
 public static EdgeTtsClient synthesize(String text,String voice,String rate,int priority,Callback c){return null;}
 public void cancel(){}
}''',
'ru/reader/rsvp/EdgeFolderPicker.java':'''package ru.reader.rsvp;public class EdgeFolderPicker{
 public static boolean available(android.content.Context c){return true;}
}''',
'ru/reader/rsvp/EdgeAudioFormat.java':'''package ru.reader.rsvp;public class EdgeAudioFormat{
 public final String extension=".mp3";
 public static EdgeAudioFormat identify(byte[] mp3,int length){return null;}
}''',
'ru/reader/rsvp/EdgeGenerationGuard.java':'''package ru.reader.rsvp;public class EdgeGenerationGuard{
 public static boolean claim(String key){return true;}
 public static void release(String key){}
}''',
'ru/reader/rsvp/EdgeAudioManager.java':'''package ru.reader.rsvp;public class EdgeAudioManager{
 public static void invalidate(java.io.File f){}
}''',
'ru/reader/rsvp/EdgeChapterArchive.java':'''package ru.reader.rsvp;public class EdgeChapterArchive{
 public static class ChapterSnapshot{public int state(int chapter){return 0;}}
 public static ChapterSnapshot snapshot(android.content.Context c,String uri,String title){return null;}
 public static boolean autoMerge(android.content.Context c){return false;}
 public static int publish(android.content.Context c,String uri,String title,java.io.File folder,org.json.JSONArray queue,
 BookChapterIndex chapters,java.util.ArrayList<Integer> offsets){return 0;}
 public static int publishFragments(android.content.Context c,String uri,String title,java.io.File folder,org.json.JSONArray queue,
 BookChapterIndex chapters,java.util.ArrayList<Integer> offsets){return 0;}
 public static int restore(android.content.Context c,String uri,String title,java.io.File folder){return 0;}
 public static void invalidate(){}
}''',
'ru/reader/rsvp/EdgeChapterLayout.java':'''package ru.reader.rsvp;public class EdgeChapterLayout{
 public static void publish(EdgeFullBookStore.Job job,org.json.JSONArray queue,
 BookChapterIndex chapters,java.util.ArrayList<Integer> offsets){}
}''',
}
for name,body in stubs.items():
    f=root/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
PY
mkdir -p "$T/bin"
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$T/src" -name '*.java') \
 app/src/main/java/ru/reader/rsvp/EdgeFullBookStore.java \
 app/src/main/java/ru/reader/rsvp/EdgeFullBookService.java
printf '%s\n' 'PASS: actual EdgeFullBookService + EdgeFullBookStore compile against Android API signatures'
