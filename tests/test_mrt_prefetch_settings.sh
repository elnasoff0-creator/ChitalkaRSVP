#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -encoding UTF-8 -d "$OUT" $(find tests/prefetch_settings_stubs -name '*.java') app/src/main/java/ru/reader/rsvp/MrtPrefetchSettings.java
java -cp "$OUT" ru.reader.rsvp.TestMrtPrefetchSettings
# All prefetch settings live in the Online tab; MRT reads the same saved preference.
grep -q 'MrtPrefetchSettings.show' app/src/main/java/ru/reader/rsvp/SettingsActivity.java
grep -q 'MrtPrefetchSettings.get' app/src/main/java/ru/reader/rsvp/MrtOnlineActivity.java
grep -q 'audioQueue.size() < prefetchAhead' app/src/main/java/ru/reader/rsvp/MrtOnlineActivity.java
