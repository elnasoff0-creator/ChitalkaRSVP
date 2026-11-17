#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh qa/check_v175.sh
python3 -m unittest qa.test_v176_contracts -v
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" app/src/main/java/ru/reader/rsvp/DownloadProgressTracker.java qa/TestDownloadProgress176.java
java -cp "$TMP" ru.reader.rsvp.TestDownloadProgress176
python3 qa/test_v176_android_types.py
