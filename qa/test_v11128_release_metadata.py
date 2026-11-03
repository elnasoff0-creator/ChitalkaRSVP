#!/usr/bin/env python3
"""Verify v1.11.128-beta metadata without changing app functionality."""
from pathlib import Path
import re, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
build=(root/'app/build.gradle').read_text(encoding='utf-8')
assert re.search(r"\bversionCode\s+12800\s*;\s*versionName\s+'1\.11\.128-beta'",build), 'Gradle release version mismatch'
for file in [root/'app/src/main/AndroidManifest.xml']:
    assert file.is_file()
    ET.parse(file)
for xml in (root/'app/src/main/res').rglob('*.xml'):
    ET.parse(xml)
java=list((root/'app/src/main/java').rglob('*.java'))
assert len(java)>100, 'Missing Java source files'
assert (root/'app/src/main/java/ru/reader/rsvp/ComicReaderActivity.java').is_file()
assert (root/'app/src/main/java/ru/reader/rsvp/MangaLibApi.java').is_file()
assert (root/'app/src/main/java/ru/reader/rsvp/EdgeStreamingService.java').is_file()
print(f'PASS v1.11.128-beta / 12800, {len(java)} Java files, XML valid')
