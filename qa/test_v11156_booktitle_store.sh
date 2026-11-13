#!/bin/sh
set -eu
SRC=$(cd "$(dirname "$0")/.." && pwd)/app/src/main/java/ru/reader/rsvp
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/android/content" "$TMP/android/net" "$TMP/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {
 String getString(String key, String def);
 boolean contains(String key);
 Editor edit();
 interface Editor {
   Editor putString(String key,String value);
   boolean commit();
 }
}
JAVA
cat > "$TMP/android/content/Context.java" <<'JAVA'
package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final Map<String,SharedPreferences> groups=new HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode) {return groups.computeIfAbsent(name,k->new Store());}
 static class Store implements SharedPreferences {
  final Map<String,String> data=new HashMap<>();
  public String getString(String key,String def){return data.getOrDefault(key,def);}
  public boolean contains(String key){return data.containsKey(key);}
  public Editor edit(){return new E();}
  final class E implements Editor {
   final Map<String,String> delta=new HashMap<>();
   public Editor putString(String key,String value){delta.put(key,value);return this;}
   public boolean commit(){data.putAll(delta);return true;}
  }
 }
}
JAVA
cat > "$TMP/android/net/Uri.java" <<'JAVA'
package android.net;
public class Uri {
 private final java.net.URI uri;
 private Uri(String u){uri=java.net.URI.create(u);}
 public static Uri parse(String value){return new Uri(value);}
 public String getScheme(){return uri.getScheme();}
 public String getPath(){return uri.getPath();}
}
JAVA
cat > "$TMP/ru/reader/rsvp/DownloadsRepository.java" <<'JAVA'
package ru.reader.rsvp;
final class DownloadsRepository {
 static final String RANOBE="ranobelib",EDGE="edge",MANGA="mangalib",AUDIO="audiobook",LINK="direct_link";
 static final class Item {String id="",type="",source="",bookUri="",title="";}
}
JAVA
cat > "$TMP/ru/reader/rsvp/RanobeLibStorage.java" <<'JAVA'
package ru.reader.rsvp;
final class RanobeLibStorage {
 static String slugForBookUri(android.content.Context c,String uri){return "";}
}
JAVA
cp "$SRC/BookTitleStore.java" "$TMP/ru/reader/rsvp/"
cat > "$TMP/ru/reader/rsvp/TestBookTitles.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
final class TestBookTitles {
 static void equal(String expected,String actual){if(!expected.equals(actual))throw new AssertionError(expected+" != "+actual);}
 static void check(boolean condition){if(!condition)throw new AssertionError("failed");}
 public static void main(String[] a){
  Context c=new Context();
  String volume="file:///data/user/0/test/Books/RanobeLIB/136924--novel/book.html";
  check(BookTitleStore.renameText(c,volume,"My new volume name"));
  equal("My new volume name",BookTitleStore.title(c,BookTitleStore.DOWNLOAD,"ranobe:136924--novel","Wrong"));
  equal("My new volume name",BookTitleStore.title(c,BookTitleStore.DOWNLOAD,"edge:"+volume,"Wrong"));
  check(BookTitleStore.renameDownload(c,"ranobe:136924--novel",DownloadsRepository.RANOBE,volume,"136924--novel","Another name"));
  equal("Another name",BookTitleStore.title(c,BookTitleStore.TEXT,volume,"Wrong"));
  check(BookTitleStore.renameComic(c,"mangalib:88--manga","Comic custom"));
  equal("Comic custom",BookTitleStore.title(c,BookTitleStore.DOWNLOAD,"manga:88--manga","Wrong"));
  check(BookTitleStore.renameDownload(c,"manga:88--manga",DownloadsRepository.MANGA,"mangalib:88--manga","","Comic rename"));
  equal("Comic rename",BookTitleStore.title(c,BookTitleStore.COMIC,"mangalib:88--manga","Wrong"));
  check(BookTitleStore.renameAudio(c,"url:https://service/id","Audio custom"));
  equal("Audio custom",BookTitleStore.title(c,BookTitleStore.DOWNLOAD,"audio:https://service/id","Wrong"));
  check(BookTitleStore.renameDownload(c,"link:abc",DownloadsRepository.LINK,"","","Link custom"));
  equal("Link custom",BookTitleStore.title(c,BookTitleStore.AUDIO,"direct:abc","Wrong"));
  check(BookTitleStore.renameAudio(c,"direct:abc","Link from library"));
  equal("Link from library",BookTitleStore.title(c,BookTitleStore.DOWNLOAD,"link:abc","Wrong"));
  check(!BookTitleStore.renameAudio(c,"direct:abc","  "));
  equal("trim one",BookTitleStore.clean(" \n trim   one \t "));
  DownloadsRepository.Item job=new DownloadsRepository.Item();job.id="manga:88--manga";job.type=DownloadsRepository.MANGA;job.bookUri="mangalib:88--manga";job.title="Raw title";
  equal("Comic rename",BookTitleStore.downloadTitle(c,job));
  System.out.println("PASS: alias storage + bidirectional library/download sync + validation");
 }
}
JAVA
find "$TMP" -name '*.java' -print > "$TMP/sources.txt"
javac -encoding UTF-8 -d "$TMP/classes" @"$TMP/sources.txt"
java -cp "$TMP/classes" ru.reader.rsvp.TestBookTitles
