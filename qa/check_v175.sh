#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 -m unittest qa.test_v175_contracts -v
python3 qa/test_v175_coverage.py
python3 qa/test_v172_archive_typecheck.py
sh qa/check_java_syntax.sh
python3 - <<'PYS'
from pathlib import Path
from xml.etree import ElementTree
files=list(Path('app/src/main').rglob('*.xml'))
for p in files:ElementTree.parse(p)
print('PASS:',len(files),'Android XML')
PYS
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -encoding UTF-8 -d "$TMP" \
 app/src/main/java/ru/reader/rsvp/ArchiveSwipeMath.java qa/TestArchiveSwipe172.java \
 app/src/main/java/ru/reader/rsvp/FishingBalance.java app/src/main/java/ru/reader/rsvp/FishingFatigue.java qa/TestFishing171.java \
 app/src/main/java/ru/reader/rsvp/EdgeMp3ChapterExport.java qa/TestEdgeMp3ChapterExport173.java
java -cp "$TMP" ru.reader.rsvp.TestArchiveSwipe172
java -cp "$TMP" ru.reader.rsvp.TestFishing171
if command -v ffmpeg >/dev/null 2>&1 && command -v ffprobe >/dev/null 2>&1; then
 ffmpeg -hide_banner -loglevel error -f lavfi -i 'sine=frequency=440:duration=0.4' -ar 24000 -ac 1 -b:a 64k "$TMP/a.mp3" -y
 ffmpeg -hide_banner -loglevel error -f lavfi -i 'sine=frequency=660:duration=0.4' -ar 24000 -ac 1 -b:a 64k "$TMP/b.mp3" -y
 java -cp "$TMP" ru.reader.rsvp.TestEdgeMp3ChapterExport173 "$TMP/a.mp3" "$TMP/b.mp3" "$TMP/out.mp3"
 ffmpeg -hide_banner -loglevel error -i "$TMP/out.mp3" -f null -
fi
