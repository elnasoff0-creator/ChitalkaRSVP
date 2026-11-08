#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ru/reader/rsvp"
cat > "$TMP/ru/reader/rsvp/TestPrioritySegments.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
public final class TestPrioritySegments {
  static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
  public static void main(String[] ignored){
    check(EdgeSpeechSegments.MAX==800,"MAX must be 800");
    check(EdgeSpeechSegments.FIRST_TARGET==800,"FIRST_TARGET must be 800");
    check(EdgeSpeechSegments.FIRST_TARGET_PRIORITY==500,"priority must be 500");
    check(SentenceChunks.LIMIT==800,"sentence LIMIT must be 800");
    String raw="Тестовая фраза содержит несколько слов и знаков. ".repeat(120);
    ArrayList<Integer> positions=SentenceChunks.wordPositions(raw);
    ArrayList<SentenceChunks.Chunk> normal=EdgeSpeechSegments.split(raw);
    check(normal.size()>3,"expected long normal text");
    for(SentenceChunks.Chunk c:normal)check(c.text.length()<=800,"normal exceeds 800");
    // A jump to a later sentence / word inside a 800-char normal group.
    SentenceChunks.Chunk group=normal.get(1);
    int selected=group.fromWord+3;
    ArrayList<SentenceChunks.Chunk> pieces=EdgeSpeechSegments.priorityPieces(
        raw,positions,selected,group.toWord,group.toChar);
    check(!pieces.isEmpty(),"no priority pieces");
    check(pieces.get(0).fromWord==selected,"did not start at tap position");
    int previous=selected;
    for(SentenceChunks.Chunk c:pieces){
      check(c.fromWord==previous,"gap or replay across priority pieces");
      check(c.toWord>c.fromWord,"empty priority piece");
      check(c.text.length()<=500,"priority >500 characters: "+c.text.length());
      check(c.text.equals(raw.substring(c.fromChar,c.toChar)),"text/offset mismatch");
      previous=c.toWord;
    }
    check(previous==group.toWord,"lost remainder of selected 800-char group");
    // Following the selected group's remainder must resume at the next normal group.
    SentenceChunks.Chunk next=normal.get(2);
    check(next.fromWord==group.toWord,"normal continuation mismatch");
    // Manual jump at start of a normal group also uses first priority 500.
    ArrayList<SentenceChunks.Chunk> fromStart=EdgeSpeechSegments.priorityPieces(
       raw,positions,group.fromWord,group.toWord,group.toChar);
    check(fromStart.size()>=2,"expected 800 group to split after priority 500");
    System.out.println("PASS: normal 800, priority 500, no missing/duplicated words");
  }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/SentenceChunks.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/BookChapterIndex.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechSegments.java" \
 "$TMP/ru/reader/rsvp/TestPrioritySegments.java"
java -cp "$TMP" ru.reader.rsvp.TestPrioritySegments
