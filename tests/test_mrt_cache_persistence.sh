#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" $(find tests/cache_stubs -name '*.java') app/src/main/java/ru/reader/rsvp/MrtAudioFormat.java app/src/main/java/ru/reader/rsvp/MrtAudioCache.java
java -cp "$OUT" ru.reader.rsvp.TestMrtCachePersistence
