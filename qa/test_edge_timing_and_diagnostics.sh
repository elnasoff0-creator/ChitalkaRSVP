#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/org/json" "$TMP/src/android/os" "$TMP/src/android/util" "$TMP/src/ru/reader/rsvp"
cat > "$TMP/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
public class JSONArray {
  public int length(){return 0;} public JSONObject optJSONObject(int i){return null;}
  public JSONArray optJSONArray(int i){return null;}
  public long optLong(int i,long fallback){return fallback;}
  public int optInt(int i,int fallback){return fallback;}
  public JSONArray put(Object item){return this;}
}
JAVA
cat > "$TMP/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
public class JSONObject {
  public JSONObject(){} public JSONObject(String s){}
  public JSONArray optJSONArray(String key){return null;}
  public JSONObject optJSONObject(String key){return null;}
  public String optString(String key){return "";}
  public String optString(String key,String fallback){return fallback;}
  public long optLong(String key,long fallback){return fallback;}
  public int optInt(String key){return 0;}
  public JSONObject put(String key,Object value){return this;}
}
JAVA
cat > "$TMP/src/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}
JAVA
cat > "$TMP/src/android/util/Log.java" <<'JAVA'
package android.util;
public class Log {
  public static final java.util.List<String> messages=new java.util.concurrent.CopyOnWriteArrayList<>();
  public static int i(String tag,String message){messages.add(tag+" "+message);return 0;}
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestEdgeTimingDiagnostics.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
import android.util.Log;
public class TestEdgeTimingDiagnostics {
  static void yes(boolean ok,String message){if(!ok)throw new AssertionError(message);}
  public static void main(String[] args)throws Exception {
    List<EdgeWordTimings.Word> sample=List.of(
      new EdgeWordTimings.Word(100,120,0,4),
      new EdgeWordTimings.Word(400,0,5,9),
      new EdgeWordTimings.Word(2000,200,10,15));
    yes(EdgeWordTimings.at(sample,99)==-1,"No highlight before first word");
    yes(EdgeWordTimings.at(sample,120)==0,"Boundary highlight lost");
    yes(EdgeWordTimings.at(sample,280)==0,"Expected 90ms tail tolerance");
    yes(EdgeWordTimings.at(sample,320)==-1,"Stale highlight during silence");
    yes(EdgeWordTimings.at(sample,450)==1,"Zero-duration word not highlighted");
    yes(EdgeWordTimings.at(sample,991)==-1,"Zero-duration word stuck through silence");
    yes(EdgeWordTimings.at(sample,2070)==2,"Second sentence timing broken");
    yes(EdgeWordTimings.at(sample,2340)==-1,"Long silence highlighted");
    String raw="Один два три";
    List<EdgeWordTimings.Boundary> marks=List.of(
      new EdgeWordTimings.Boundary(0,1200000,"Один"),
      new EdgeWordTimings.Boundary(3000000,1000000,"два"),
      new EdgeWordTimings.Boundary(6000000,900000,"три"));
    List<EdgeWordTimings.Word> aligned=EdgeWordTimings.align(raw,marks);
    yes(aligned.size()==3,"Lost metadata alignment");
    yes(aligned.get(1).startMs==300 && aligned.get(1).from==5,"Wrong WordBoundary offset");
    String key="9e5a9c726500:12";
    EdgeTtsDiagnostics.requested(key,700,false);
    EdgeTtsDiagnostics.connected(key);
    EdgeTtsDiagnostics.firstMp3(key,1024);
    EdgeTtsDiagnostics.firstMp3(key,512);
    EdgeTtsDiagnostics.decoderRead(key,300);
    EdgeTtsDiagnostics.finished(key,null,3);
    EdgeTtsDiagnostics.trackEnded("prev-track");
    EdgeTtsDiagnostics.firstSound(key);
    EdgeTtsDiagnostics.firstSound(key);
    String logs=String.join("\n",Log.messages);
    yes(logs.contains("socket-open=") && logs.contains("first-mp3=") &&
        logs.contains("decoder-first-read=") && logs.contains("player-start="),"Missing pipeline diagnostics");
    yes(!logs.contains(raw),"Private book text appeared in diagnostics");
    yes(logs.contains("boundaries=3"),"Missing WordBoundary count");
    yes(logs.contains("callback-gap="),"Missing clearly labeled callback-gap diagnostic");
    System.out.println("PASS: WordBoundary timing, zero-duration silence, source offsets, MP3 latency logging");
  }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
final class ReaderDiagnosticLog { static void event(String component,String detail){} }
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
 "$TMP/src/ru/reader/rsvp/ReaderDiagnosticLog.java" "$TMP/src/org/json/JSONArray.java" "$TMP/src/org/json/JSONObject.java" \
 "$TMP/src/android/os/SystemClock.java" "$TMP/src/android/util/Log.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWordTimings.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeTtsDiagnostics.java" \
 "$TMP/src/ru/reader/rsvp/TestEdgeTimingDiagnostics.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestEdgeTimingDiagnostics
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
stream=(root/'EdgeStreamingService.java').read_text()
engine=(root/'EdgeSpeechEngine.java').read_text()
data=(root/'EdgeLiveDataSource.java').read_text()
player=(root/'EdgePlaybackService.java').read_text()
assert 'callback.connected()' in engine
assert 'EdgeTtsDiagnostics.connected(diagnosticKey)' in stream
assert 'EdgeTtsDiagnostics.firstMp3(diagnosticKey,length)' in stream
assert 'EdgeTtsDiagnostics.decoderRead(traceKey,n)' in data
assert 'EdgeTtsDiagnostics.firstSound(currentKey())' in player
assert '.setBufferDurationsMs(450,15000,160,350)' in player
assert 'startLive(ahead)' not in stream
print('PASS: diagnostics wired from WebSocket through Media3; sequential synthesis intact')
PY
