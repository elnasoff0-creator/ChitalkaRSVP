#!/bin/sh
# Type-check actual v154 cache and metadata code; exercise FB2 and EPUB title reading.
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/android/content" "$T/src/android/net" "$T/src/android/text" "$T/src/org/json" "$T/src/ru/reader/rsvp" "$T/bin"
cat > "$T/src/android/net/Uri.java" <<'EOF'
package android.net; public class Uri { private final String val; private Uri(String s){val=s;} public static Uri parse(String s){return new Uri(s);} public String toString(){return val;} }
EOF
cat > "$T/src/android/content/SharedPreferences.java" <<'EOF'
package android.content; public interface SharedPreferences {String getString(String key,String d); Editor edit();interface Editor{Editor putString(String k,String v);void apply();}}
EOF
cat > "$T/src/android/content/ContentResolver.java" <<'EOF'
package android.content; public class ContentResolver {public java.io.InputStream openInputStream(android.net.Uri uri){return null;}}
EOF
cat > "$T/src/android/content/Context.java" <<'EOF'
package android.content; public class Context {public ContentResolver getContentResolver(){return null;} public SharedPreferences getSharedPreferences(String name,int mode){return null;}}
EOF
cat > "$T/src/android/text/Html.java" <<'EOF'
package android.text;public class Html {public static final int FROM_HTML_MODE_LEGACY=0;public static CharSequence fromHtml(String s,int f){return s.replace("&amp;","&").replace("&lt;","<");}}
EOF
cat > "$T/src/ru/reader/rsvp/BookLoader.java" <<'EOF'
package ru.reader.rsvp; public class BookLoader {public static String decode(byte[] b){return new String(b,java.nio.charset.StandardCharsets.UTF_8);}}
EOF
cat > "$T/src/ru/reader/rsvp/DownloadsRepository.java" <<'EOF'
package ru.reader.rsvp; final class DownloadsRepository {static class Item {String id="",type="",title="",cover="",source="",bookUri="",status="",description="";int done,total,coveragePercent;boolean ready,running,paused,failed,previewOnly;}}
EOF
cat > "$T/src/org/json/JSONObject.java" <<'EOF'
package org.json;public class JSONObject {public JSONObject(){}public JSONObject(String json){}public JSONObject put(String k,Object v){return this;}public String optString(String k){return "";}public int optInt(String k){return 0;}public boolean optBoolean(String k){return false;}public String toString(){return "{}";}}
EOF
cat > "$T/src/org/json/JSONArray.java" <<'EOF'
package org.json;public class JSONArray {public JSONArray(){}public JSONArray(String json){}public JSONArray put(Object v){return this;}public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}public String toString(){return "[]";}}
EOF
cat > "$T/src/ru/reader/rsvp/TestMetadata.java" <<'EOF'
package ru.reader.rsvp;
import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
public class TestMetadata {
  static final class FakeContext extends Context {
    final byte[] bytes;FakeContext(byte[] b){bytes=b;}
    @Override public ContentResolver getContentResolver(){return new ContentResolver(){
      @Override public InputStream openInputStream(Uri u){return new ByteArrayInputStream(bytes);}
    };}
  }
  static void check(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
  public static void main(String[] args)throws Exception {
    byte[] fb2="<FictionBook><description><title-info><book-title>Книга &amp; книга</book-title></title-info></description></FictionBook>".getBytes(StandardCharsets.UTF_8);
    check(EbookTitleExtractor.extract(new FakeContext(fb2),Uri.parse("content://fb2"),"fb2"),"Книга & книга");
    ByteArrayOutputStream buffer=new ByteArrayOutputStream();
    try(ZipOutputStream zip=new ZipOutputStream(buffer)){
      zip.putNextEntry(new ZipEntry("META-INF/container.xml"));zip.write("<container/>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
      zip.putNextEntry(new ZipEntry("OEBPS/content.opf"));zip.write("<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title id='book-title'>Заголовок EPUB</dc:title></metadata></package>".getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    check(EbookTitleExtractor.extract(new FakeContext(buffer.toByteArray()),Uri.parse("content://epub"),"epub"),"Заголовок EPUB");
    check(EbookTitleExtractor.fromXml("<book-title>Моя книга</book-title>","book-title"),"Моя книга");
    System.out.println("PASS: real EbookTitleExtractor FB2/EPUB metadata and v154 cache class typecheck");
  }
}
EOF
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$T/src" -name '*.java') \
  app/src/main/java/ru/reader/rsvp/EbookTitleExtractor.java \
  app/src/main/java/ru/reader/rsvp/DownloadListSnapshot.java
java -cp "$T/bin" ru.reader.rsvp.TestMetadata
