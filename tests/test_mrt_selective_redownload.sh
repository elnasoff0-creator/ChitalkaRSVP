#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" $(find tests/cache_stubs -name '*.java') app/src/main/java/ru/reader/rsvp/MrtAudioFormat.java app/src/main/java/ru/reader/rsvp/MrtAudioCache.java
java -cp "$OUT" ru.reader.rsvp.TestMrtSelectiveInvalidate
SERVICE='app/src/main/java/ru/reader/rsvp/MrtDownloadService.java'
SCREEN='app/src/main/java/ru/reader/rsvp/MrtDownloadActivity.java'
PLAYER='app/src/main/java/ru/reader/rsvp/MrtPlaybackService.java'
grep -q 'boolean redownloadClip(int start)' "$SERVICE"
grep -q 'manualRedownloads' "$SERVICE"
grep -q 'boolean evictCachedTrack(String key)' "$PLAYER"
grep -q 'confirmRedownload(c,pos+1)' "$SCREEN"
grep -q 'ic_retry_fragment' "$SCREEN"
echo 'PASS: queue redownload control, forced regeneration, and player invalidation are wired'
