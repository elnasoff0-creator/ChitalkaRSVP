#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 -d "$tmp" app/src/main/java/ru/reader/rsvp/WebTransferPolicy.java qa/TestWebTransferPolicy192.java
java -cp "$tmp" ru.reader.rsvp.TestWebTransferPolicy192
python3 qa/check_v192_contracts.py
