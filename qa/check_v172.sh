#!/bin/sh
# Offline QA: sources and pure-JVM logic. Does not build or launch an Android APK.
set -eu
cd "$(dirname "$0")/.."
TEST_TMP=$(mktemp -d)
trap 'rm -rf "$TEST_TMP"' EXIT
python3 -m unittest qa.test_v168_contracts qa.test_v169_contracts qa.test_v170_contracts qa.test_v171_contracts qa.test_v172_contracts -q
python3 qa/test_v11153_swipe_callback_build.py
javac -encoding UTF-8 -d "$TEST_TMP" app/src/main/java/ru/reader/rsvp/ArchiveSwipeMath.java qa/TestArchiveSwipe172.java
java -cp "$TEST_TMP" ru.reader.rsvp.TestArchiveSwipe172
sh qa/typecheck_v171_fishing.sh
javac -encoding UTF-8 -d "$TEST_TMP" app/src/main/java/ru/reader/rsvp/FishingBalance.java app/src/main/java/ru/reader/rsvp/FishingFatigue.java qa/TestFishing171.java qa/JavaParseVerifier.java qa/JavaSourceSyntaxCheck.java
java -cp "$TEST_TMP" ru.reader.rsvp.TestFishing171
java -cp "$TEST_TMP" JavaSourceSyntaxCheck
python3 - <<'PYCHECK'
import xml.etree.ElementTree as ET
from pathlib import Path
files=list(Path('app/src/main').rglob('*.xml'))
for p in files:ET.parse(p)
print('PASS:', len(files), 'Android XML files parsed')
PYCHECK
