#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
SRC="$ROOT/app/src/main/java/ru/reader/rsvp"
mkdir -p "$TMP/android/os" "$TMP/ru/reader/rsvp"
cat > "$TMP/android/os/SystemClock.java" <<'JAVA'
package android.os;
public final class SystemClock {
    public static long elapsedRealtime(){return System.nanoTime()/1000000L;}
}
JAVA
cat > "$TMP/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.ArrayList;
final class ReaderDiagnosticLog {
    private static final ArrayList<String> values=new ArrayList<>();
    static void event(String component,String message){values.add(component+" "+message);}
    static long count(String target){return values.stream().filter(s->s.contains(target)).count();}
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
    "$SRC/TtsSwitchPolicy.java" "$SRC/EdgeFirstAudioPolicy.java" \
    "$ROOT/qa/TestEdgeFirstAudio188.java"
java -cp "$TMP" ru.reader.rsvp.TestEdgeFirstAudio188
javac -encoding UTF-8 -cp "$TMP" -d "$TMP" \
    "$TMP/android/os/SystemClock.java" "$TMP/ru/reader/rsvp/ReaderDiagnosticLog.java" \
    "$SRC/EdgeStartupTrace.java" "$ROOT/qa/TestEdgeStartupTrace188.java"
java -cp "$TMP" ru.reader.rsvp.TestEdgeStartupTrace188
