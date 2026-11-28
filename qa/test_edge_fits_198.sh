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
cat > "$T/src/ru/reader/rsvp/TestFits198.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
class TestFits198 {
 static void yes(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 public static void main(String[] args){
   List<EdgeSpeechTextTransform.Options> options=Arrays.asList(
      new EdgeSpeechTextTransform.Options("off",",",""),
      new EdgeSpeechTextTransform.Options("off",",","слово=новое слово"),
      new EdgeSpeechTextTransform.Options("off",",","","***"),
      new EdgeSpeechTextTransform.Options("off",",",Arrays.asList(
          new EdgeSpeechTextTransform.Entry(0,"слово","","длинное слово",false),
          new EdgeSpeechTextTransform.Entry(1,"***","","",false),
          new EdgeSpeechTextTransform.Entry(0,"[0-9]+","","число",true)),5));
   for(EdgeSpeechTextTransform.Options o:options){
     for(String value:new String[]{"слово ".repeat(900),"Привет & < >", "", "слово *** 123", "Обычная строка", "число 12 & слово"}){
       yes(o.fitsSpeech(value)==EdgeSpeechSize.fits(o.apply(value).spoken),"fast byte check changed result");
     }
   }
   System.out.println("PASS: speech byte check matches actual transform for empty, legacy, ignore and SQLite rules");
 }
}
JAVA
javac -encoding UTF-8 -d "$T/classes" "$T/src/android/content/SharedPreferences.java" \
 "$T/src/org/json/JSONArray.java" "$T/src/org/json/JSONObject.java" \
 "$T/src/ru/reader/rsvp/EdgeAudioManager.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWordTimings.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechTextTransform.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechSize.java" \
 "$T/src/ru/reader/rsvp/TestFits198.java"
java -cp "$T/classes" ru.reader.rsvp.TestFits198
