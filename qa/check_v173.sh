#!/bin/sh
# Offline checks. Android Gradle and device UI/SAF tests require Android SDK/device.
set -eu
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
python3 -m unittest qa.test_v173_contracts -v
python3 qa/test_v172_archive_typecheck.py
sh qa/check_java_syntax.sh
python3 - <<'PYXML'
import xml.etree.ElementTree as ET
from pathlib import Path
files=list(Path('app/src/main').rglob('*.xml'))
for file in files: ET.parse(file)
print('PASS: Android XML parsed:',len(files))
PYXML
javac -encoding UTF-8 -d "$TMP" \
  app/src/main/java/ru/reader/rsvp/ArchiveSwipeMath.java qa/TestArchiveSwipe172.java \
  app/src/main/java/ru/reader/rsvp/FishingBalance.java \
  app/src/main/java/ru/reader/rsvp/FishingFatigue.java qa/TestFishing171.java \
  app/src/main/java/ru/reader/rsvp/EdgeMp3ChapterExport.java qa/TestEdgeMp3ChapterExport173.java
java -cp "$TMP" ru.reader.rsvp.TestArchiveSwipe172
java -cp "$TMP" ru.reader.rsvp.TestFishing171
if command -v ffmpeg >/dev/null 2>&1 && command -v ffprobe >/dev/null 2>&1; then
  ffmpeg -hide_banner -loglevel error -f lavfi -i 'sine=frequency=440:duration=0.6' -ar 24000 -ac 1 -b:a 64k "$TMP/a.mp3" -y
  ffmpeg -hide_banner -loglevel error -f lavfi -i 'sine=frequency=660:duration=0.6' -ar 24000 -ac 1 -b:a 64k "$TMP/b.mp3" -y
  java -cp "$TMP" ru.reader.rsvp.TestEdgeMp3ChapterExport173 "$TMP/a.mp3" "$TMP/b.mp3" "$TMP/chapter.mp3"
  ffprobe -v error -show_entries format_tags=title,album,track -of default=noprint_wrappers=1 "$TMP/chapter.mp3" | grep -q 'Глава 7'
  ffmpeg -hide_banner -v error -i "$TMP/chapter.mp3" -f null -
else
  echo 'SKIP: real MP3 playback smoke test requires ffmpeg and ffprobe'
fi
