#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" "$root/app/src/main/java/ru/reader/rsvp/ReaderTextTouchPolicy.java" "$root/qa/TestReaderTextTouchPolicy186.java"
java -cp "$tmp" ru.reader.rsvp.TestReaderTextTouchPolicy186
