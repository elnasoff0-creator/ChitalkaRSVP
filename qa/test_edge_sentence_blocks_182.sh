#!/usr/bin/env bash
set -euo pipefail
project="$(cd "$(dirname "$0")/.." && pwd)"
java_dir="$project/app/src/main/java/ru/reader/rsvp"
output="$(mktemp -d)"
trap 'rm -rf "$output"' EXIT
mkdir -p "$output/stubs/ru/reader/rsvp" "$output/classes"
cat > "$output/stubs/ru/reader/rsvp/EdgeSpeechQueue.java" <<'JAVA'
package ru.reader.rsvp;
final class EdgeSpeechQueue { static final int PLAY=0; }
JAVA
javac -encoding UTF-8 -d "$output/classes" \
 "$output/stubs/ru/reader/rsvp/EdgeSpeechQueue.java" \
 "$java_dir/BookChapterIndex.java" "$java_dir/SentenceChunks.java" \
 "$java_dir/SentenceIndex.java" "$java_dir/EdgeSentenceBoundary.java" \
 "$java_dir/EdgeSpeechSize.java" "$java_dir/EdgeAdaptiveBlocks.java" \
 "$java_dir/EdgeSpeechLayout.java" \
 "$java_dir/EdgeSpeechSegments.java" \
 "$project/qa/TestEdgeSentenceBlocks182.java"
java -cp "$output/classes" ru.reader.rsvp.TestEdgeSentenceBlocks182
