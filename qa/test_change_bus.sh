#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TEMP=$(mktemp -d)
trap 'rm -rf "$TEMP"' EXIT
mkdir -p "$TEMP/android/os" "$TEMP/ru/reader/rsvp"
cat > "$TEMP/android/os/Looper.java" <<'JAVA'
package android.os;
public final class Looper {private static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;}}
JAVA
cat > "$TEMP/android/os/Handler.java" <<'JAVA'
package android.os;
public final class Handler {public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;}}
JAVA
cat > "$TEMP/ru/reader/rsvp/TestEvents.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.ArrayList;
public final class TestEvents {
  public static void main(String[] args){
    ArrayList<String> seen=new ArrayList<>();
    DownloadChangeBus.Listener listener=seen::add;
    DownloadChangeBus.add(listener);
    long before=DownloadChangeBus.revision();
    DownloadChangeBus.publish("manga:book-a");
    DownloadChangeBus.publish("audio:book-b");
    if(seen.size()!=2)throw new AssertionError("listeners missed events");
    if(!DownloadChangeBus.since(before).contains("audio:book-b"))throw new AssertionError("missing revisions");
    DownloadChangeBus.remove(listener);
    DownloadChangeBus.publish("edge:book-c");
    if(seen.size()!=2)throw new AssertionError("paused client was notified");
    if(!DownloadChangeBus.since(before).contains("edge:book-c"))throw new AssertionError("paused change lost");
    if(DownloadChangeBus.revision()-before!=3)throw new AssertionError("revision not monotonic");
    System.out.println("PASS: event journal, scoped invalidation, paused replay");
  }
}
JAVA
javac -encoding UTF-8 -d "$TEMP/classes" "$TEMP/android/os/Looper.java" "$TEMP/android/os/Handler.java" app/src/main/java/ru/reader/rsvp/DownloadChangeBus.java "$TEMP/ru/reader/rsvp/TestEvents.java"
java -cp "$TEMP/classes" ru.reader.rsvp.TestEvents
