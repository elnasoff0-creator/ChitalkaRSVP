#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMPDIR_TEST=$(mktemp -d)
trap 'rm -rf "$TMPDIR_TEST"' EXIT
javac -encoding UTF-8 -d "$TMPDIR_TEST" app/src/main/java/ru/reader/rsvp/SentenceChunks.java app/src/main/java/ru/reader/rsvp/EdgeSpeechSegments.java app/src/main/java/ru/reader/rsvp/EdgeTextSegments.java qa/TestSentenceChunks.java
java -cp "$TMPDIR_TEST" ru.reader.rsvp.TestSentenceChunks
