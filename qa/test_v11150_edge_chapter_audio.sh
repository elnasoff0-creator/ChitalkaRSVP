#!/bin/sh
# Statically typecheck the new SAF chapter packer with minimal Android signatures,
# then exercise its real MP3-frame concatenation on generated test audio.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/android/content" "$T/src/android/net" "$T/src/android/util" "$T/src/androidx/documentfile/provider" "$T/src/org/json" "$T/src/ru/reader/rsvp" "$T/bin"
cat > "$T/src/android/content/Context.java" <<'JAVA'
package android.content;
public class Context{public ContentResolver getContentResolver(){return new ContentResolver();}public java.io.File getCacheDir(){return new java.io.File(".");}public android.content.SharedPreferences getSharedPreferences(String s,int mode){return null;}}
JAVA
cat > "$T/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content; public interface SharedPreferences {boolean getBoolean(String key,boolean def);}
JAVA
mkdir -p "$T/src/android/os"
cat > "$T/src/android/os/SystemClock.java" <<'JAVA'
package android.os;public class SystemClock {public static long elapsedRealtime(){return 0L;}}
JAVA
cat > "$T/src/android/content/ContentResolver.java" <<'JAVA'
package android.content;
public class ContentResolver{
 public java.io.InputStream openInputStream(android.net.Uri u){return null;}
 public java.io.OutputStream openOutputStream(android.net.Uri u,String mode){return null;}
}
JAVA
cat > "$T/src/android/net/Uri.java" <<'JAVA'
package android.net; public class Uri{}
JAVA
cat > "$T/src/android/util/Log.java" <<'JAVA'
package android.util; public class Log{public static int w(String a,String b,Throwable e){return 0;}}
JAVA
cat > "$T/src/androidx/documentfile/provider/DocumentFile.java" <<'JAVA'
package androidx.documentfile.provider;
public class DocumentFile{
 public static DocumentFile fromTreeUri(android.content.Context c,android.net.Uri u){return null;}
 public boolean canWrite(){return true;}public boolean isDirectory(){return true;}public boolean isFile(){return true;}
 public boolean exists(){return true;}public long length(){return 0;}public String getName(){return "";}
 public android.net.Uri getUri(){return null;}public DocumentFile findFile(String n){return null;}
 public DocumentFile createDirectory(String n){return null;}public DocumentFile createFile(String mime,String n){return null;}
 public DocumentFile[] listFiles(){return new DocumentFile[0];}public boolean delete(){return false;}
 public boolean renameTo(String n){return true;}
}
JAVA
cat > "$T/src/org/json/JSONObject.java" <<'JAVA'
package org.json; public class JSONObject{
 public JSONObject(){}public JSONObject(String s){}public JSONObject put(String k,Object v){return this;}
 public String optString(String k){return "";}public String optString(String k,String fallback){return fallback;}
 public int optInt(String k){return 0;}public int optInt(String k,int def){return def;}
 public long optLong(String k){return 0L;}public long optLong(String k,long def){return def;}
 public String getString(String k){return "";}public JSONArray optJSONArray(String key){return null;}
 public String toString(){return "{}";}
}
JAVA
cat > "$T/src/org/json/JSONArray.java" <<'JAVA'
package org.json; public class JSONArray{
 public JSONArray(){}public JSONArray(String s){}public JSONArray put(Object v){return this;}
 public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}
 public JSONArray optJSONArray(int i){return null;}
 public String toString(){return "[]";}
 public int optInt(int i){return 0;}public long optLong(int i){return 0;}
}
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeFolderPicker.java" <<'JAVA'
package ru.reader.rsvp; public class EdgeFolderPicker{
 public static boolean available(android.content.Context c){return false;}
 public static android.net.Uri selected(android.content.Context c){return null;}
}
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;public class EdgeWordTimings{
 public static class Word{public long startMs,durationMs;public int from,to;
 public Word(long a,long b,int c,int d){startMs=a;durationMs=b;from=c;to=d;}}
 public static java.util.List<Word> read(java.io.File f,String s){return java.util.Collections.emptyList();}
 public static void storeAligned(java.io.File f,String s,java.util.List<Word> words){}
}
JAVA
mkdir -p "$T/src/android/provider"
cat > "$T/src/android/provider/DocumentsContract.java" <<'JAVA'
package android.provider;public class DocumentsContract{
 public static String getDocumentId(android.net.Uri uri){return "folder";}
}
JAVA
cat > "$T/src/ru/reader/rsvp/AudioLibrary.java" <<'JAVA'
package ru.reader.rsvp;public class AudioLibrary{
 public static class Book{public String id="",name="";public long updated;
 public java.util.ArrayList<String> uris=new java.util.ArrayList<>(),chapters=new java.util.ArrayList<>();}
 public static int naturalCompare(String a,String b){return a.compareTo(b);}
 public static String clean(String name){return name.replaceAll("(?i)\\.mp3$","");}
 public static void merge(android.content.Context c,java.util.ArrayList<Book> books){}
}
JAVA
cat > "$T/src/ru/reader/rsvp/FrameProbe.java" <<'JAVA'
package ru.reader.rsvp;
public final class FrameProbe{
 public static void main(String[] args)throws Exception{
  if(!"001.mp3".equals(EdgeChapterArchive.chapterAudioName(0,BookChapterIndex.empty())))
   throw new Exception("No TOC must yield only a chapter number");
  java.util.ArrayList<BookChapterIndex.Chapter> toc=new java.util.ArrayList<>();
  toc.add(new BookChapterIndex.Chapter("Начало",0,0,0));
  toc.add(new BookChapterIndex.Chapter("Финал",300,0,0));
  BookChapterIndex index=BookChapterIndex.build(toc,500);
  if(!"001 - Начало.mp3".equals(EdgeChapterArchive.chapterAudioName(0,index))
     ||!"002 - Финал.mp3".equals(EdgeChapterArchive.chapterAudioName(1,index)))
   throw new Exception("TOC chapter names must be numbered and titled");
  System.out.println("PASS: New MP3 naming with TOC and without TOC");
  Class<?> c=Class.forName("ru.reader.rsvp.EdgeChapterArchive");
  java.lang.reflect.Method m=c.getDeclaredMethod("audioFrames",java.io.File.class);m.setAccessible(true);
  java.io.ByteArrayOutputStream combined=new java.io.ByteArrayOutputStream();long duration=0;
  for(int i=1;i<args.length;i++){
   Object f=m.invoke(null,new java.io.File(args[i]));Class<?> type=f.getClass();
   java.lang.reflect.Field audio=type.getDeclaredField("frames");audio.setAccessible(true);
   java.lang.reflect.Field ms=type.getDeclaredField("durationMs");ms.setAccessible(true);
   combined.write((byte[])audio.get(f));duration+=(long)ms.get(f);
  }
  try(java.io.FileOutputStream o=new java.io.FileOutputStream(args[0])){o.write(combined.toByteArray());}
  if(duration<1200||duration>4000)throw new Exception("Invalid MPEG frame timing: "+duration);
  System.out.println("PASS: MPEG frames normalized; total "+duration+" ms, "+combined.size()+" bytes");
 }
}
JAVA
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" \
 $(find "$T/src" -name '*.java') app/src/main/java/ru/reader/rsvp/EdgeChapterArchive.java app/src/main/java/ru/reader/rsvp/BookChapterIndex.java
if command -v ffmpeg >/dev/null 2>&1 && command -v ffprobe >/dev/null 2>&1;then
 ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i sine=frequency=330:duration=1 -ar 24000 -ac 1 -b:a 48k -y "$T/one.mp3"
 ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i sine=frequency=660:duration=1 -ar 24000 -ac 1 -b:a 48k -y "$T/two.mp3"
 java -cp "$T/bin" ru.reader.rsvp.FrameProbe "$T/complete.mp3" "$T/one.mp3" "$T/two.mp3"
 ffmpeg -nostdin -hide_banner -loglevel error -i "$T/complete.mp3" -f null -
 DURATION=$(ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 "$T/complete.mp3")
 python3 - "$DURATION" <<'PY'
import sys
assert 1.7 < float(sys.argv[1]) < 2.3, sys.argv[1]
print('PASS: Joined chapter decodes in FFmpeg, duration '+sys.argv[1]+' s')
PY
else
 echo 'PASS: Java compile; FFmpeg audio roundtrip skipped (not installed)'
fi
