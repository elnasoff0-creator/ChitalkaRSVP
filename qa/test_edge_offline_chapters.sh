#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/org/json" "$TMP/ru/reader/rsvp" "$TMP/android/os" "$TMP/data"
cat > "$TMP/android/os/SystemClock.java" <<'JAVA'
package android.os;
public final class SystemClock {
    public static long elapsedRealtime(){return java.lang.System.currentTimeMillis();}
}
JAVA
cat > "$TMP/ru/reader/rsvp/EdgeBookStorage.java" <<'JAVA'
package ru.reader.rsvp;
final class EdgeBookStorage {
    static String key(String text,String voice,String rate,String backend){
        return text+":"+voice+":"+rate+":"+backend;
    }
}
JAVA
cat > "$TMP/org/json/JSONObject.java" <<'JAVA'
package org.json;
import java.util.regex.*;
public final class JSONObject {
  private final String source;
  public JSONObject(String text){source=text;}
  public int optInt(String key,int fallback){
    Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*([0-9]+)").matcher(source);
    return m.find()?Integer.parseInt(m.group(1)):fallback;
  }
  public String optString(String key,String fallback){
    Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(source);
    return m.find()?m.group(1):fallback;
  }
}
JAVA
cat > "$TMP/org/json/JSONArray.java" <<'JAVA'
package org.json;
import java.util.*;
import java.util.regex.*;
public final class JSONArray {
  private final ArrayList<JSONObject> entries=new ArrayList<>();
  public JSONArray(){}
  public JSONArray(String raw){Matcher m=Pattern.compile("\\{[^{}]*\\}").matcher(raw);
    while(m.find())entries.add(new JSONObject(m.group()));}
  public int length(){return entries.size();}
  public JSONObject optJSONObject(int i){return i>=0&&i<entries.size()?entries.get(i):null;}
}
JAVA
cat > "$TMP/ru/reader/rsvp/CheckOfflineChapters.java" <<'JAVA'
package ru.reader.rsvp;
import java.io.*;
import java.util.*;
public final class CheckOfflineChapters {
  static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
  static void write(File f,String text)throws Exception{try(FileOutputStream out=new FileOutputStream(f)){out.write(text.getBytes("UTF-8"));}}
  public static void main(String[] args)throws Exception{
    File root=new File(args[0]);String a="a".repeat(64),b="b".repeat(64),c="c".repeat(64);
    write(new File(root,"sentences.json"),"[{\"start\":0,\"end\":30,\"key\":\""+a+"\"},{\"start\":30,\"end\":60,\"key\":\""+b+"\"}]");
    write(new File(root,"full_queue.json"),"[{\"start\":0,\"end\":30,\"key\":\""+a+"\"},{\"start\":50,\"end\":90,\"key\":\""+c+"\"}]");
    write(new File(root,"selected_clips.json"),"[{\"start\":25,\"end\":45,\"key\":\""+b+"\"}]");
    write(new File(root,a+".mp3"),"x".repeat(2200));
    write(new File(root,b+".mp3"),"x".repeat(1900));
    write(new File(root,c+".mp3"),"tiny"); // invalid cache must never count
    ArrayList<EdgeOfflineIndex.Segment> rows=EdgeOfflineIndex.readySegments(root);
    check(rows.size()==3,"union of manifests, physical MP3 only");
    EdgeOfflineIndex.Stats all=EdgeOfflineIndex.stats(rows,0,100);
    check(all.covered==60,"overlapping words counted once: "+all.covered);
    EdgeOfflineIndex.Stats first=EdgeOfflineIndex.stats(rows,0,50);
    check(first.covered==50&&first.complete(),"chapter 1 fully offline");
    EdgeOfflineIndex.Stats second=EdgeOfflineIndex.stats(rows,50,100);
    check(second.covered==10&&!second.complete(),"chapter 2 incomplete");
    check(all.percent()==60,"percent based on words, not number of files");
    check(all.bytes==4100,"unique MP3 bytes counted once, not per overlapping index row");
    check(EdgeAudioManager.segments(root).size()==3,"shared manager reads all legacy indexes");
    check(EdgeAudioManager.coverage(root,0,100).covered==60,"cached word coverage");
    check(EdgeAudioManager.key("text","voice","+0%").endsWith("edge-direct-v1"),"stable cache namespace");
    write(new File(root,c+".mp3"),"x".repeat(2400));
    EdgeAudioManager.invalidate(root);
    check(EdgeAudioManager.segments(root).size()==4,"explicit invalidate reveals completed MP3");
    check(EdgeAudioManager.coverage(root,0,100).covered==90,"newly saved MP3 contributes to word coverage");
    System.out.println("PASS: 4 manifest formats, no phantom MP3s, merged word coverage and chapters");
  }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
  "$TMP/org/json/JSONArray.java" "$TMP/org/json/JSONObject.java" \
  "$TMP/android/os/SystemClock.java" "$TMP/ru/reader/rsvp/EdgeBookStorage.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeOfflineIndex.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeAudioManager.java" \
  "$TMP/ru/reader/rsvp/CheckOfflineChapters.java"
java -cp "$TMP" ru.reader.rsvp.CheckOfflineChapters "$TMP/data"
