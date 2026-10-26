#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ru/reader/rsvp"
cat > "$TMP/ru/reader/rsvp/CheckEdgeGroups.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.ArrayList;
public class CheckEdgeGroups {
  public static void main(String[] args){
    StringBuilder b=new StringBuilder();
    for(int i=0;i<30;i++)b.append("Это предложение с небольшой длиной, проверяем новый режим. ");
    String raw=b.toString();
    ArrayList<SentenceChunks.Chunk> old=SentenceChunks.split(raw), grouped=EdgeSpeechSegments.split(raw);
    if(grouped.size()>=old.size()/2)throw new AssertionError("Not enough handshake reduction");
    int last=0;
    for(SentenceChunks.Chunk c:grouped){
      if(c.text.length()>600||!c.text.equals(raw.substring(c.fromChar,c.toChar))||c.fromWord!=last)
        throw new AssertionError("Broken groups / offsets: "+c.text);
      last=c.toWord;
    }
    if(last!=SentenceChunks.wordPositions(raw).size())throw new AssertionError("Missing words");
    int chapterBoundary=raw.indexOf("Это предложение",600);
    BookChapterIndex toc=BookChapterIndex.build(java.util.Arrays.asList(
        new BookChapterIndex.Chapter("Начало",0,0,1),
        new BookChapterIndex.Chapter("Продолжение",chapterBoundary,0,1)),raw.length());
    for(SentenceChunks.Chunk c:EdgeSpeechSegments.split(raw,toc)){
        if(c.fromChar<chapterBoundary&&c.toChar>chapterBoundary)
          throw new AssertionError("One MP3 crosses two chapters");
    }
    if(grouped.get(0).text.length()>320)throw new AssertionError("Slow first group");
    System.out.println("PASS: "+old.size()+" sentences -> "+grouped.size()+" requests, <=600 characters, stable word offsets");
  }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/SentenceChunks.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/BookChapterIndex.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechSegments.java" \
 "$TMP/ru/reader/rsvp/CheckEdgeGroups.java"
java -cp "$TMP" ru.reader.rsvp.CheckEdgeGroups
