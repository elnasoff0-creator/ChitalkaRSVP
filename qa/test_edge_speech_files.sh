#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/ru/reader/rsvp" "$TMP/classes" "$TMP/cache"
cat > "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;import java.io.File;import java.util.List;
class EdgeWordTimings {static class Boundary{}static void store(File file,String t,List<Boundary> b){}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestMp3Cache.java" <<'JAVA'
package ru.reader.rsvp;
import java.io.File;import java.nio.file.Files;import java.util.Collections;
class TestMp3Cache {
 public static void main(String[] args)throws Exception{
  File folder=new File(args[0]);String key="abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdef";
  byte[] mp3=new byte[2500];mp3[0]='I';mp3[1]='D';mp3[2]='3';
  File saved=EdgeSpeechFiles.save(folder,key,"Проверка",mp3,Collections.emptyList());
  if(!saved.isFile()||!Files.readAllBytes(saved.toPath()).equals(mp3)){
     if(saved.length()!=mp3.length||!java.util.Arrays.equals(mp3,Files.readAllBytes(saved.toPath())))
       throw new AssertionError("MP3 cache mismatch");
  }
  if(new File(folder,key+".speech.part").exists())throw new AssertionError("Partial MP3 leaked");
  byte[] bad=new byte[2500];bad[0]=1;
  try{EdgeSpeechFiles.save(folder,key+"abcdef", "Bad",bad,Collections.emptyList());
    throw new AssertionError("Invalid MP3 was accepted");
  }catch(java.io.IOException expected){}
  System.out.println("PASS: complete MP3 accepted, partial not published, invalid MP3 rejected");
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
 "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" \
 "$TMP/src/ru/reader/rsvp/TestMp3Cache.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeAudioFormat.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechFiles.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestMp3Cache "$TMP/cache"
