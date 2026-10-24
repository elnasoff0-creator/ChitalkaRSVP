#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT
javac -encoding UTF-8 -d "$TMP_DIR" qa/JavaSourceSyntaxCheck.java
java -cp "$TMP_DIR" JavaSourceSyntaxCheck
