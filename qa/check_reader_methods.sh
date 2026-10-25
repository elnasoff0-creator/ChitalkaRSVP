#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
javac -d "$OUT" "$ROOT/qa/ReaderActivityMethodCheck.java"
java -cp "$OUT" ReaderActivityMethodCheck "$ROOT/app/src/main/java/ru/reader/rsvp/ReaderActivity.java"
