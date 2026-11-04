#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/android/content" "$TMP/src/org/json" "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {String getString(String key,String fallback);}
JAVA
cat > "$TMP/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
public class JSONArray {public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}public JSONArray optJSONArray(int i){return null;}public long optLong(int i,long d){return d;}public int optInt(int i,int d){return d;}public JSONArray put(Object v){return this;}}
JAVA
cat > "$TMP/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
public class JSONObject {public JSONObject(){}public JSONObject(String text){}public JSONObject optJSONObject(String k){return null;}public JSONArray optJSONArray(String k){return null;}public String optString(String k){return "";}public String optString(String k,String d){return d;}public long optLong(String k,long d){return d;}public int optInt(String k){return 0;}public JSONObject put(String k,Object v){return this;}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/EdgeAudioManager.java" <<'JAVA'
package ru.reader.rsvp;
class EdgeAudioManager {static String key(String s,String v,String r){return s+v+r;}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/Test132.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
import java.util.regex.Pattern;
class Test132 {
 static void yes(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 static EdgeSpeechTextTransform.Entry rule(int kind,String p,String ctx,String replacement,boolean regex){
   return new EdgeSpeechTextTransform.Entry(kind,p,ctx,replacement,regex);
 }
 static EdgeSpeechTextTransform.Result transform(String original,EdgeSpeechTextTransform.Entry... entries){
   return new EdgeSpeechTextTransform.Options("off",",",Arrays.asList(entries),12).apply(original);
 }
 public static void main(String[] args){
   var forms=rule(0,"(?iu)\\bполк[ауи]\\b","","полка",true);
   var first=transform("На полку положили книгу. С полки упала книга. Полка.",forms);
   yes(first.spoken.equals("На полка положили книгу. С полка упала книга. полка."),"three inflected forms");
   yes(first.original.equals("На полку положили книгу. С полки упала книга. Полка."),"book cannot change");
   var shorten=rule(0,"(?iu)\\bгл\\.\\s*(\\d+)","","глава $1",true);
   var numbered=transform("гл. 12 и гл. 24",shorten);
   yes(numbered.spoken.equals("глава 12 и глава 24"),"group replacement");
   yes(numbered.from[numbered.spoken.indexOf("12")]>=4,"group source mapping");
   var blank=rule(1,"(?m)^[ \\t]*\\r?\\n","","",true);
   var cleared=transform("Один.\n\n  \nДва.",blank);
   yes(cleared.spoken.equals("Один.\nДва."),"regex ignore blank paragraphs");
   yes(cleared.original.equals("Один.\n\n  \nДва."),"blank lines source preserved");
   var stars=rule(1,"\\*","","",true);
   var mixed=transform("Привет*** мир",stars);
   yes(mixed.spoken.equals("Привет мир"),"ignored symbols must not merge words");
   yes(transform("***",stars).skipSynthesis(),"ignored-only clip must not start websocket");
   var remove=rule(0,"(?iu)\\bи\\b","","",true);
   yes(transform("Раз и два",remove).spoken.equals("Раз  два"),"empty replacement supported");
   var literals=rule(0,"Ёлка","","йолка",false);
   yes(transform("Ёлка и Ёлка",literals).spoken.equals("йолка и йолка"),"ordinary word replacements");
   var mapping=transform("Ёлка и гл. 12",literals,shorten);
   yes(mapping.from.length==mapping.spoken.length(),"all characters must have source mapping");
   var boundaries=List.of(new EdgeWordTimings.Boundary(1000000,1000000,"йолка"),
       new EdgeWordTimings.Boundary(2000000,1000000,"глава"),
       new EdgeWordTimings.Boundary(3000000,1000000,"12"));
   var aligned=mapping.aligned(boundaries);
   yes(aligned.size()>=2,"WordBoundary mapping");
   for(var word:aligned)yes(word.from>=0&&word.to<=mapping.original.length(),"WordBoundary source range");
   yes(!mapping.cacheKey("v","r").equals(transform(mapping.original).cacheKey("v","r")),"changed speech must invalidate audio cache");
   var many=new ArrayList<EdgeSpeechTextTransform.Entry>();
   for(int i=0;i<300;i++)many.add(rule(0,"несуществующаяфраза"+i,"","x",false));
   many.add(rule(0,"корова","","кот",false));
   yes(new EdgeSpeechTextTransform.Options("off",",",many,16).apply("корова").spoken.equals("кот"),
       "must process beyond old 100-rule limit");
   boolean invalid=false;
   try{EdgeSpeechTextTransform.validateReplacement(Pattern.compile("(книга)"),"$2");}
   catch(IllegalArgumentException ex){invalid=true;}
   yes(invalid,"invalid group must be rejected");
   var named=rule(0,"гл\\. (?<num>\\d+)","","глава ${num}",true);
   yes(transform("гл. 10",named).spoken.equals("глава 10"),"named groups");
   System.out.println("PASS: forms, Regex, $1, ${name}, 300+ literal rules, ignored blank lines, original offsets, WordBoundary, cache");
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" "$TMP/src/android/content/SharedPreferences.java" \
 "$TMP/src/org/json/JSONArray.java" "$TMP/src/org/json/JSONObject.java" \
 "$TMP/src/ru/reader/rsvp/EdgeAudioManager.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWordTimings.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechTextTransform.java" \
 "$TMP/src/ru/reader/rsvp/Test132.java"
java -cp "$TMP/classes" ru.reader.rsvp.Test132
