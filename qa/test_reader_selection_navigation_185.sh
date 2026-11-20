#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
src="$root/app/src/main/java/ru/reader/rsvp"
javac -d "$work" "$src/ReaderSelectionRange.java" "$src/ReaderSpeechPositionGuard.java" "$src/ReadingWordMap.java" "$src/BookChunkIndex.java" "$root/qa/TestReaderSelectionNavigation185.java"
java -cp "$work" ru.reader.rsvp.TestReaderSelectionNavigation185
