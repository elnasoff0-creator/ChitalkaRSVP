#!/bin/sh
# Offline release checks (no Android SDK, no APK build).
set -eu
cd "$(dirname "$0")/.."
TEST_TMP=$(mktemp -d)
trap 'rm -rf "$TEST_TMP"' EXIT
python3 -m unittest qa.test_v168_contracts qa.test_v169_contracts qa.test_v170_contracts qa.test_v171_contracts -q
sh qa/typecheck_v171_fishing.sh
javac -encoding UTF-8 -d "$TEST_TMP" app/src/main/java/ru/reader/rsvp/FishingBalance.java app/src/main/java/ru/reader/rsvp/FishingFatigue.java qa/TestFishing171.java qa/JavaParseVerifier.java
java -cp "$TEST_TMP" ru.reader.rsvp.TestFishing171
java -cp "$TEST_TMP" JavaParseVerifier app/src/main/java
python3 - <<'PY'
import xml.etree.ElementTree as ET
from pathlib import Path
sources = list(Path('app/src/main').rglob('*.xml'))
for item in sources:
    ET.parse(item)
print('PASS', len(sources), 'resource/manifest XML files parsed')
PY
