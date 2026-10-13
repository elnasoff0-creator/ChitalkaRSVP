#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" tests/focus_stubs/android/content/*.java tests/focus_stubs/android/media/*.java tests/focus_stubs/android/os/*.java app/src/main/java/ru/reader/rsvp/PlaybackAudioFocus.java tests/focus_stubs/ru/reader/rsvp/TestPlaybackAudioFocus.java
java -cp "$OUT" ru.reader.rsvp.TestPlaybackAudioFocus
