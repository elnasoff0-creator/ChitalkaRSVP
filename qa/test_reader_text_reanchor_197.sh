#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
src=app/src/main/java/ru/reader/rsvp
javac -encoding UTF-8 -d "$work" "$src/ReadingWordMap.java" "$src/BookChapterIndex.java" "$src/ReaderTextReanchor.java" qa/TestReaderTextReanchor197.java
java -cp "$work" ru.reader.rsvp.TestReaderTextReanchor197
