#!/bin/sh
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;
final class EdgeWordTimings {
    static final class Word {
        final long startMs; final int from,to;
        Word(long start,int first,int end) {startMs=start;from=first;to=end;}
    }
}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestSeek.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.List;
public final class TestSeek {
    private static void eq(long expected,long actual) {
        if(expected!=actual)throw new AssertionError("Expected "+expected+" got "+actual);
    }
    public static void main(String[] args) {
        List<EdgeWordTimings.Word> words=List.of(
            new EdgeWordTimings.Word(0,0,6),
            new EdgeWordTimings.Word(430,7,15),
            new EdgeWordTimings.Word(2800,37,41),
            new EdgeWordTimings.Word(3200,42,49),
            new EdgeWordTimings.Word(4100,61,69));
        eq(0,EdgeSentenceSeek.at(0,words));
        eq(2800,EdgeSentenceSeek.at(37,words)); // another sentence in the same MP3
        eq(2800,EdgeSentenceSeek.at(35,words)); // whitespace before selected word
        eq(3200,EdgeSentenceSeek.at(44,words)); // tap inside a word
        eq(-1,EdgeSentenceSeek.at(20,words)); // no boundary near: exact suffix is required
        eq(-1,EdgeSentenceSeek.at(93,words)); // no boundary: never silently replay clip
        eq(-1,EdgeSentenceSeek.at(50,List.of()));
        System.out.println("PASS: exact sentence seek / safe fallback for absent timings");
    }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
    "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" \
    "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSentenceSeek.java" \
    "$TMP/src/ru/reader/rsvp/TestSeek.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestSeek
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
base=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
s=(base/'EdgeStreamingService.java').read_text()
p=(base/'EdgePlaybackService.java').read_text()
a=(base/'ReaderActivity.java').read_text()
assert s.index('player.armSelectedPosition(current.key') < s.index('addTrack(current);')
assert p.index('applyArmedPosition();\n        if (oldCount==0)') < p.index('player.prepare();',p.index('player.addMediaItem(insertion'))
assert 'if(ahead!=null)addTrack(ahead);' in s[s.index('private void pump()'):s.index('private void addTrack(')]
assert 'selectedOverride=target' in s
assert 'pendingTtsWord' in a and 'EdgeOnlineActivity' not in a
assert 'clip.awaitingUserRetry=true' in s
assert 'job.buffer=new EdgeLiveBuffer()' in s
assert 'player.appendLive(' in s
assert 'void appendLive(' in p
assert 'new ProgressiveMediaSource.Factory(' in p
assert 'EdgeSpeechQueue.allowPrefetch() && nearEndOfCurrentAudio(current)' in s
assert 'EdgeSpeechQueue.restoreCooldown' in s
print('PASS: selected MP3 is armed before prepare/play, no future-track substitution, 429 guarded')
PY
