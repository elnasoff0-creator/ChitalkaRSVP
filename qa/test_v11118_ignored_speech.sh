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
cat > "$TMP/src/ru/reader/rsvp/TestIgnore.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
class TestIgnore {
 static void yes(boolean value,String s){if(!value)throw new AssertionError(s);}
 static EdgeSpeechTextTransform.Result run(String s,String ignore){return new EdgeSpeechTextTransform.Options("off",",","",ignore).apply(s);}
 public static void main(String[] args){
   var a=run("***","***");yes(a.skipSynthesis(),"skip standalone token");yes(a.spoken.isEmpty(),"remove all tokens");
   yes(run("***...", "***").skipSynthesis(),"skip remaining punctuation without speech");
   var b=run("Привет *** мир.","***");yes(!b.skipSynthesis(),"don't skip valid spoken sentence");
   yes(b.spoken.equals("Привет  мир."),"remove only ignored literal");
   var c=run("Слово***слово", "***");yes(c.spoken.equals("Слово слово"),"don't combine adjacent words");
   var d=run("Первое. Убрать эту строку. Третье.","Убрать эту строку.");
   yes(d.spoken.equals("Первое.  Третье."),"remove the ignored phrase but preserve neighbors");
   var e=run("Убрать эту строку.","Убрать эту строку.");yes(e.skipSynthesis(),"skip complete sentence");
   var f=run("Замок. Мука.","замок.");yes(f.spoken.equals(" Мука."),"case-insensitive rule");
   yes(!run("замоковый","замок").skipSynthesis(),"word token shouldn't eat part of word");
   var mapped=run("Ёлка *** растёт", "***");
   var marks=List.of(new EdgeWordTimings.Boundary(1000000,1000000,"Ёлка"),new EdgeWordTimings.Boundary(2000000,1000000,"растёт"));
   var timing=mapped.aligned(marks);yes(timing.size()==2,"WordBoundary count");
   yes(mapped.original.substring(timing.get(1).from,timing.get(1).to).equals("растёт"),"source mapping");
   yes(!mapped.cacheKey("v","r").equals(new EdgeSpeechTextTransform.Options("off",",","").apply(mapped.original).cacheKey("v","r")),"key reflects ignored text");
   var untouched=run("Тест текст","***");yes(!untouched.skipSynthesis()&&untouched.spoken.equals("Тест текст"),"unmatched rule changes nothing");
   var unconfigured=run("***", "");yes(!unconfigured.skipSynthesis(),"no unwanted default filtering");
   yes(run("******", "***").skipSynthesis(),"consecutive skipped tokens");
   android.content.SharedPreferences old=(key,fallback)->
       key.equals(EdgeSpeechTextTransform.DICTIONARY)?"***=.\nЁлка=йолка":fallback;
   var converted=EdgeSpeechTextTransform.Options.read(old);
   yes(converted.apply("***").skipSynthesis(),"migrate old sample symbol rule to skip");
   yes(converted.apply("Ёлка").spoken.equals("йолка"),"retain unrelated dictionary rules");
   System.out.println("PASS: ignored literal/sentence skipping, no WebSocket candidate, mixed speech, UTF-16 offsets/cache");
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" "$TMP/src/android/content/SharedPreferences.java" "$TMP/src/org/json/JSONArray.java" "$TMP/src/org/json/JSONObject.java" "$TMP/src/ru/reader/rsvp/EdgeAudioManager.java" "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWordTimings.java" "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechTextTransform.java" "$TMP/src/ru/reader/rsvp/TestIgnore.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestIgnore
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1]);p=root/'app/src/main/java/ru/reader/rsvp'
svc=(p/'EdgeStreamingService.java').read_text();full=(p/'EdgeFullBookService.java').read_text();ui=(p/'EdgeDictionaryActivity.java').read_text();t=(p/'EdgeSpeechTextTransform.java').read_text()
assert 'Clip current=skipIgnored(clipAt(anchorWord));' in svc
assert '||shouldIgnore(clip)' in svc
assert 'if(!shouldIgnore(candidate))' in svc
assert 'if(speech.skipSynthesis())continue' in full
assert 'if(result.transformation.skipSynthesis())' in full
assert 'setText("Игнорировать")' in ui
assert 'Символьная замена перед Edge TTS. Например: ! → -.' in ui
assert '*** → .' not in ui
assert 'static final String IGNORED=' in t
assert 'versionCode 12100' in (root/'app/build.gradle').read_text()
print('PASS: live/bulk WebSocket bypass, UI actions/example, version 118')
PY
