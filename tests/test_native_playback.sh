#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" tests/native_stubs/android/media/*.java tests/native_stubs/android/util/*.java \
  app/src/main/java/ru/reader/rsvp/MrtPlaybackEngine.java tests/native_stubs/ru/reader/rsvp/TestMrtPlaybackEngine.java
java -cp "$OUT" ru.reader.rsvp.TestMrtPlaybackEngine
