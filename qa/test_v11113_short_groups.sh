#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ru/reader/rsvp"
cat > "$TMP/ru/reader/rsvp/TestShortGroups.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
class TestShortGroups {
  static void require(boolean test,String msg){if(!test)throw new AssertionError(msg);}
  public static void main(String[] argv){
    String a="Короткая реплика.\n\nЕщё одна.\n\nСледующая мысль.\n\n";
    String b="Это более длинный абзац с достаточным количеством слов, чтобы проверить работу обычной группировки. ".repeat(6);
    String text=a+b;
    ArrayList<SentenceChunks.Chunk> before=SentenceChunks.split(text),after=EdgeSpeechSegments.split(text);
    require(after.size()<before.size(),"Short paragraphs should merge");
    require(after.get(0).text.length()>a.trim().length(),"Tiny paragraph isolated");
    require(after.stream().noneMatch(c->c.text.length()<100),"Orphan generated for 100 chars");
    int prev=0;
    for(SentenceChunks.Chunk c:after){
       require(c.fromWord==prev,"Broken word continuity");
       require(c.toChar-c.fromChar<=800,"Max exceeded");
       require(c.text.equals(text.substring(c.fromChar,c.toChar)),"Word offset mapping broken");
       prev=c.toWord;
    }
    require(prev==SentenceChunks.wordPositions(text).size(),"Words lost");
    int boundary=text.indexOf("Следующая мысль");
    BookChapterIndex toc=BookChapterIndex.build(Arrays.asList(
      new BookChapterIndex.Chapter("Глава 1",0,0,1),
      new BookChapterIndex.Chapter("Глава 2",boundary,0,1)),text.length());
    for(SentenceChunks.Chunk c:EdgeSpeechSegments.split(text,toc))
        require(!(c.fromChar<boundary && c.toChar>boundary),"Crossed chapter");
    System.out.println("PASS: paragraph coalescing, <=800 chars, stable indexes, chapter boundaries");
  }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/SentenceChunks.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/BookChapterIndex.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechSegments.java" \
 "$TMP/ru/reader/rsvp/TestShortGroups.java"
java -cp "$TMP" ru.reader.rsvp.TestShortGroups
