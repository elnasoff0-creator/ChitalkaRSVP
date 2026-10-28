#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/app/src/main/java/ru/reader/rsvp"
# The only automatic warmup must follow entry into TTS and never use manual seek.
python3 - "$SRC" <<'PY'
import sys
from pathlib import Path
src=Path(sys.argv[1]); reader=(src/'ReaderActivity.java').read_text(); service=(src/'EdgeStreamingService.java').read_text(); queue=(src/'EdgeSpeechQueue.java').read_text()
assert 'ttsStreamer.setAnchor(index,false);ttsStreamer.setActive(true);' in reader
assert reader.count('ttsStreamer.setAnchor(index,true);')==1, 'only manual taps may select an anchor'
assert '.setAction(EdgeStreamingService.ACTION_PREFETCH)' not in reader, 'ordinary reader must not issue idle Edge requests'
assert 'restoreStartPosition();' in service
assert 'if(initial!=null&&initial.fromWord<anchorWord)setAnchor(anchorWord,true)' not in service
assert 'target.awaitingUserRetry' in service[service.index('private void warmCurrent()'):service.index('public void refreshSettings()')]
assert 'waiting.request.promoteToPlay();' in service
assert 'void promote(Ticket ticket)' in queue
print('PASS: entry into TTS does not issue a manual priority request or redundant warmups')
PY
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/android/os" "$TMP/android/util" "$TMP/okhttp3" "$TMP/ru/reader/rsvp"
cat > "$TMP/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1000000L;}}
JAVA
cat > "$TMP/android/util/Log.java" <<'JAVA'
package android.util;
public class Log {public static int w(String a,String b){return 0;}public static int e(String a,String b,Throwable e){return 0;}}
JAVA
cat > "$TMP/okhttp3/Response.java" <<'JAVA'
package okhttp3;
public class Response {public String header(String n){return null;}}
JAVA
cat > "$TMP/ru/reader/rsvp/EdgeStartupSingleFlightTest.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.concurrent.*;
class EdgeStartupSingleFlightTest {
 static void yes(boolean ok,String info){if(!ok)throw new AssertionError(info);}
 public static void main(String[] args)throws Exception {
   CountDownLatch firstCompleted=new CountDownLatch(1);
   EdgeSpeechQueue.Ticket normal=EdgeSpeechQueue.submit(EdgeSpeechQueue.PLAY,firstCompleted::countDown);
   yes(firstCompleted.await(1,TimeUnit.SECONDS),"initial request never started");
   EdgeSpeechQueue.release(normal);
   CountDownLatch warmed=new CountDownLatch(1);
   EdgeSpeechQueue.Ticket prefetch=EdgeSpeechQueue.submit(EdgeSpeechQueue.PREFETCH,warmed::countDown);
   long when=System.nanoTime();
   EdgeSpeechQueue.promote(prefetch);
   yes(warmed.await(700,TimeUnit.MILLISECONDS),"Play still waited for the prefetch 850ms gap");
   yes((System.nanoTime()-when)<700_000_000L,"Promoted request too slow");
   EdgeSpeechQueue.release(prefetch);
   System.out.println("PASS: Play promotes the exact warmup ticket without a new request");
 }
}
JAVA
# App-local diagnostics are asynchronous on Android; network unit tests use a no-op logger.
cat > "$TMP/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
final class ReaderDiagnosticLog { static void event(String component,String detail){} }
JAVA
javac -encoding UTF-8 -d "$TMP" \
 "$TMP/android/os/SystemClock.java" "$TMP/android/util/Log.java" "$TMP/okhttp3/Response.java" \
 "$TMP/ru/reader/rsvp/ReaderDiagnosticLog.java" \
 "$SRC/EdgeSpeechQueue.java" "$TMP/ru/reader/rsvp/EdgeStartupSingleFlightTest.java"
java -cp "$TMP" ru.reader.rsvp.EdgeStartupSingleFlightTest
