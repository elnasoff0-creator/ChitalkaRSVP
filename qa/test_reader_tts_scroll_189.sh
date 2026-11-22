#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" \
  "$root/app/src/main/java/ru/reader/rsvp/ReaderTtsScrollPolicy.java" \
  "$root/app/src/main/java/ru/reader/rsvp/PetSpeechBubbleSizing.java" \
  "$root/qa/TestReaderTtsScrollPolicy189.java" \
  "$root/qa/TestPetSpeechBubble189.java"
java -cp "$tmp" ru.reader.rsvp.TestReaderTtsScrollPolicy189
java -cp "$tmp" ru.reader.rsvp.TestPetSpeechBubble189
python3 "$root/qa/check_v189_contracts.py"
