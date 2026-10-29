#!/bin/sh
set -eu
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
javac -d "$WORK" \
  "$ROOT/qa/sleep_clock_jvm/android/content/SharedPreferences.java" \
  "$ROOT/qa/sleep_clock_jvm/android/content/Context.java" \
  "$ROOT/qa/sleep_clock_jvm/android/os/SystemClock.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/PlaybackSleepCountdown.java" \
  "$ROOT/qa/sleep_clock_jvm/ru/reader/rsvp/SleepClockTest.java"
java -cp "$WORK" ru.reader.rsvp.SleepClockTest
