#!/bin/sh
# Test on the host JVM without Android SDK. Tests production EdgeWordTimings,
# EdgeSentenceSeek and ReaderSpeechPositionGuard; no mocked seek algorithm.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/org/json" "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/org/json/JSONArray.java" <<'JAVA'
package org.json;
public final class JSONArray {
    public int length(){return 0;}
    public JSONObject optJSONObject(int i){return null;}
    public JSONArray optJSONArray(int i){return null;}
    public long optLong(int i,long fallback){return fallback;}
    public int optInt(int i,int fallback){return fallback;}
    public JSONArray put(Object value){return this;}
}
JAVA
cat > "$TMP/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
public final class JSONObject {
    public JSONObject(){}
    public JSONObject(String text){}
    public JSONArray optJSONArray(String key){return null;}
    public JSONObject optJSONObject(String key){return null;}
    public String optString(String key){return "";}
    public String optString(String key,String fallback){return fallback;}
    public long optLong(String key,long fallback){return fallback;}
    public int optInt(String key){return 0;}
    public JSONObject put(String key,Object value){return this;}
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestEdgeSentenceReplay187.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.List;
import java.util.Collections;
public final class TestEdgeSentenceReplay187 {
    static void eq(long expected,long actual,String message){
        if(expected!=actual)throw new AssertionError(message+": expected="+expected+" actual="+actual);
    }
    static void yes(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        String clip="Сначала был ветер. Потом начало грозы. Дальше лес.";
        int nextSentence=clip.indexOf("Потом");
        int secondWord=clip.indexOf("начало");
        int finalSentence=clip.indexOf("Дальше");
        List<EdgeWordTimings.Word> all=List.of(
            new EdgeWordTimings.Word(0,400,0,7),
            new EdgeWordTimings.Word(500,180,clip.indexOf("был"),clip.indexOf("был")+3),
            new EdgeWordTimings.Word(900,300,clip.indexOf("ветер"),clip.indexOf("ветер")+5),
            new EdgeWordTimings.Word(2800,250,nextSentence,nextSentence+5),
            new EdgeWordTimings.Word(3200,250,secondWord,secondWord+6),
            new EdgeWordTimings.Word(4700,320,finalSentence,finalSentence+6));
        eq(0,EdgeSentenceSeek.at(0,clip,Collections.emptyList()),
            "Beginning of MP3 never needs guessed WordBoundary");
        eq(2695,EdgeSentenceSeek.at(nextSentence,clip,all),
            "Sentence replay starts 105ms before the real first word");
        eq(2695,EdgeSentenceSeek.at(nextSentence+2,clip,all),
            "Tap within the first word seeks to its safe onset");
        eq(4595,EdgeSentenceSeek.at(finalSentence,clip,all),
            "Seek to third sentence does not replay whole group");
        List<EdgeWordTimings.Word> firstMissing=List.of(
            all.get(0),all.get(1),all.get(2),all.get(4),all.get(5));
        eq(-1,EdgeSentenceSeek.at(nextSentence,clip,firstMissing),
            "Missing FIRST marker must never use SECOND word within 12 chars");
        eq(-1,EdgeSentenceSeek.at(nextSentence+2,clip,firstMissing),
            "Inside missing first word must select full-sentence fallback");
        eq(-1,EdgeSentenceSeek.at(secondWord-3,clip,firstMissing),
            "Unmatched source letters must never be treated as silence");
        eq(-1,EdgeSentenceSeek.at(nextSentence,clip,Collections.emptyList()),
            "Missing all metadata in the middle requires an intact sentence");
        eq(-1,EdgeSentenceSeek.at(clip.length()+1,clip,all),
            "Outside MP3 text has no safe offset");
        String quoted="Книга закончилась. «Начало рассказа».";
        int quote=quoted.indexOf('«');
        int first=quoted.indexOf("Начало");
        eq(3395,EdgeSentenceSeek.at(quote,quoted,
            List.of(new EdgeWordTimings.Word(3500,200,first,first+6))),
            "Leading quotation punctuation is a safe non-spoken separator");
        String emoji="Закончено. 😀 Начало";
        int emojiAt=emoji.indexOf("😀");
        int afterEmoji=emoji.indexOf("Начало");
        eq(-1,EdgeSentenceSeek.at(emojiAt,emoji,
            List.of(new EdgeWordTimings.Word(500,300,afterEmoji,afterEmoji+6))),
            "Potentially spoken emoji cannot be silently skipped");
        // WordBoundary emitted by the service can use e instead of ё.
        List<EdgeWordTimings.Word> matched=EdgeWordTimings.align("Ёлка красивая",List.of(
            new EdgeWordTimings.Boundary(0,1300000,"елка"),
            new EdgeWordTimings.Boundary(2500000,1800000,"красивая")));
        yes(matched.size()==2&&matched.get(0).from==0,
            "Safe Russian orthographic normalization retains first boundary");
        List<EdgeWordTimings.Word> legacy=EdgeWordTimings.align("Первое второе",List.of(
            new EdgeWordTimings.Boundary(2100000,1300000,"второе")));
        yes(legacy.size()==1&&legacy.get(0).from==7,
            "Do not invent first WordBoundary in legacy MP3 metadata");
        eq(0,EdgeSentenceSeek.at(0,"Первое второе",legacy),
            "First sentence in file always starts from actual MP3 beginning");
        eq(-1,EdgeSentenceSeek.at(1,"Первое второе",legacy),
            "No jump into second word when first has no metadata");
        ReaderSpeechPositionGuard guard=new ReaderSpeechPositionGuard();
        guard.seek(40,45);
        eq(-1,guard.accept("old-track",0,2500,35,true),
            "Late previous track cannot overwrite selected first word");
        eq(-1,guard.accept("new-track",20,2695,39,true),
            "Pre-roll previous word must not replace selected highlight");
        eq(40,guard.accept("new-track",20,2800,40,true),
            "Selected first word becomes confirmed at its real boundary");
        eq(41,guard.accept("new-track",20,3200,41,true),
            "Subsequent WordBoundary advances normally");
        System.out.println("PASS: v187 full sentence replay, pre-roll, first-word metadata and highlight guard");
    }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
  "$TMP/src/org/json/JSONArray.java" "$TMP/src/org/json/JSONObject.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeWordTimings.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSentenceSeek.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/ReaderSpeechPositionGuard.java" \
  "$TMP/src/ru/reader/rsvp/TestEdgeSentenceReplay187.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestEdgeSentenceReplay187
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
a=(root/'ReaderActivity.java').read_text()
s=(root/'EdgeStreamingService.java').read_text()
p=(root/'EdgePlaybackService.java').read_text()
selection=a[a.index('private void selectTtsWord(int at)'):a.index('private void syncExternalTtsSelection()')]
assert selection.index('refreshHighlight(previousWord);') < selection.index('ttsStreamer.setAnchor(index,true)')
assert 'index=ttsSentenceIndex.beginning(index);' in selection
assert 'word=sentenceIndex.beginning(Math.max(0,word));' in s
assert s.count('EdgeSentenceSeek.at(relative,') == 3
assert 'current.start!=selectedSeekWord' in s and 'SEEK_REFUSED missing_first_word' in s
assert 'replayStartWord=selectedSeekWord;' in s and 'anchorWord=replayStartWord;' in s
assert 'current=Math.max(current,replayStartWord);' in s
assert s.index('player.armSelectedPosition(replayTrackKey,ms)')<s.index('addTrack(current);')
assert p.index('applyArmedPosition();\n        if (oldCount==0)')<p.index('player.prepare();',p.index('player.addMediaItem(insertion'))
assert 'EdgeSpeechSegments.priorityPieces(' in s
print('PASS: v187 selection is painted before audio, cached/isolated replay and Media3 arm contracts')
PY
