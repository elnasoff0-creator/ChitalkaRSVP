#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" \
 "$root/app/src/main/java/ru/reader/rsvp/ComicRestorePolicy.java" \
 "$root/app/src/main/java/ru/reader/rsvp/ReaderTtsScrollPolicy.java" \
 "$root/qa/TestComicRestore191.java"
java -cp "$tmp" ru.reader.rsvp.TestComicRestore191
python3 "$root/qa/check_restore_settings_191.py"
