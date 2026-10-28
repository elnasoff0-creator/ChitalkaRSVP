#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src/android/content" "$T/src/org/json" "$T/src/ru/reader/rsvp" "$T/classes"
cat > "$T/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {String getString(String key,String def);}
JAVA
cat > "$T/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
public class JSONArray { public int length(){return 0;} public JSONObject optJSONObject(int i){return null;} public JSONArray optJSONArray(int i){return null;} public long optLong(int i,long d){return d;} public int optInt(int i,int d){return d;} public JSONArray put(Object v){return this;} }
JAVA
cat > "$T/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
public class JSONObject {public JSONObject(){} public JSONObject(String s){} public JSONObject optJSONObject(String k){return null;} public JSONArray optJSONArray(String k){return null;} public String optString(String k){return "";} public String optString(String k,String def){return def;} public long optLong(String k,long d){return d;} public int optInt(String k){return 0;} public JSONObject put(String k,Object v){return this;} }
JAVA
cat > "$T/src/ru/reader/rsvp/EdgeAudioManager.java" <<'JAVA'
package ru.reader.rsvp;
class EdgeAudioManager {static String key(String s,String voice,String rate){return s+voice+rate;}}
JAVA
cat > "$T/src/ru/reader/rsvp/TestTransform.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
class TestTransform{
 static void yes(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
   String text="Ёлка. Потом тест. Число 3.14. Адрес site.ru. Точка в конце.\nНовая строка.";
   EdgeSpeechTextTransform.Result off=new EdgeSpeechTextTransform.Options("off",",","").apply(text);
   yes(text.equals(off.spoken),"off changed source");
   yes(off.cacheKey("a","b").equals(text+"ab"),"legacy cache identity changed");
   EdgeSpeechTextTransform.Result v1=new EdgeSpeechTextTransform.Options("v1",";","").apply(text);
   yes(v1.spoken.contains("Ёлка; Потом тест;"),"V1 regular punctuation");
   yes(v1.spoken.contains("3.14")&&v1.spoken.contains("site.ru"),"decimals/domains affected");
   yes(text.contains("Ёлка. Потом")&&text.endsWith("строка."),"source mutated");
   EdgeSpeechTextTransform.Result v2=new EdgeSpeechTextTransform.Options("v2",",","").apply("Первый.\nВторой. Третий.");
   yes(v2.spoken.equals("Первый.\nВторой, Третий."),"V2 line end");
   EdgeSpeechTextTransform.Result v3=new EdgeSpeechTextTransform.Options("v3",":","").apply("Один. Два.\nТри.\tЧетыре.");
   yes(v3.spoken.equals("Один: Два.\nТри:\tЧетыре."),"V3 whitespace");
   String original="Ёлка. Пётр сказал: привет.";
   EdgeSpeechTextTransform.Result mapped=new EdgeSpeechTextTransform.Options("v1","\n\n\n","Ёлка=йолочка\nПётр=Пётр").apply(original);
   yes(mapped.spoken.startsWith("йолочка\n\n\n "),"dictionary plus newline");
   List<EdgeWordTimings.Boundary> marks=List.of(
     new EdgeWordTimings.Boundary(1000000,1000000,"йолочка"),
     new EdgeWordTimings.Boundary(2000000,1000000,"Пётр"),
     new EdgeWordTimings.Boundary(3000000,1000000,"сказал"),
     new EdgeWordTimings.Boundary(4000000,1000000,"привет"));
   List<EdgeWordTimings.Word> aligned=mapped.aligned(marks);
   yes(aligned.size()==4,"WordBoundary lost after dictionary and punctuation");
   yes(original.substring(aligned.get(0).from,aligned.get(0).to).equals("Ёлка"),"dictionary mapping wrong");
   yes(original.substring(aligned.get(1).from,aligned.get(1).to).equals("Пётр"),"later mapping wrong");
   yes(original.substring(aligned.get(3).from,aligned.get(3).to).equals("привет"),"last mapping wrong");
   yes(!mapped.cacheKey("a","b").equals(off.cacheKey("a","b")),"speech cache identity unchanged");
   System.out.println("PASS: V1/V2/V3, decimals, URL, dictionary, newline mapping, cache separation, WordBoundary");
 }
}
JAVA
javac -encoding UTF-8 -d "$T/classes" "$T/src/android/content/SharedPreferences.java" \
 "$T/src/org/json/JSONArray.java" "$T/src/org/json/JSONObject.java" \
 "$T/src/ru/reader/rsvp/EdgeAudioManager.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWordTimings.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechTextTransform.java" \
 "$T/src/ru/reader/rsvp/TestTransform.java"
java -cp "$T/classes" ru.reader.rsvp.TestTransform
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
a=(p/'EdgeStreamingService.java').read_text()
b=(p/'EdgeSpeechQueue.java').read_text()
c=(p/'EdgeSpeechEngine.java').read_text()
assert 'prefetchWindow(current)' in a
assert 'candidate.ready()||liveFor(candidate)!=null' in a
assert 'EdgeSpeechFiles.saveMapped' in a
assert 'job.speech.spoken' in a
assert 'foreground?EdgeSpeechQueue.PLAY:EdgeSpeechQueue.PREFETCH' in a
assert 'job.speech.aligned' in a
assert 'if(current!=null&&!next.equals(current.key))choose(null,null)' in a
assert 'EdgeWebSocketPool.recycle(resource)' in c and 'socketTerminated(resource)' in c
assert 'active.size()>=MAX_ACTIVE' in b
print('PASS: cache-first candidate window, pooled WS, transformed text/timings')
PY
