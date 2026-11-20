#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
DIR=$(mktemp -d)
trap 'rm -rf "$DIR"' EXIT
javac -encoding UTF-8 -d "$DIR" \
 app/src/main/java/ru/reader/rsvp/PetQuoteChoice.java qa/TestPetQuoteChoice184.java
java -cp "$DIR" ru.reader.rsvp.TestPetQuoteChoice184
