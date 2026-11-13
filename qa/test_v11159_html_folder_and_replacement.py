#!/usr/bin/env python3
"""Compatibility test: v159 folder import survives v160 removal of automatic rescanning."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1]
source=root/'app/src/main/java/ru/reader/rsvp'
get=lambda f:(source/f).read_text(encoding='utf-8')
main=get('MainActivity.java');folder=get('HtmlFolderLibrary.java')
assert '"Импортировать папку с HTML"' in main
assert 'HtmlFolderLibrary.importFolder' in main
assert 'BookMeta found=meta(result.uri)' in main
assert 'DocumentFile.fromTreeUri' in folder
assert 'static Result importFolder' in folder
assert 'HtmlChapterOrder.compare' in folder
assert 'copyIllustrations(' in folder and 'cover.img' in folder
assert 'static void rememberTransfer' in folder
assert 'publishExternal(' in folder and 'MessageDigest.isEqual(' in folder
assert 'HtmlFolderLibrary.rememberTransfer(' in get('LibraryMoveManager.java')
assert 'Проверить изменения HTML' not in main
assert 'syncIfChanged(' not in folder and 'checkChanges(' not in folder
assert not (source/'HtmlReadingAnchor.java').exists()
for xml in (root/'app/src/main').rglob('*.xml'):ET.parse(xml)
print('PASS: single-shot HTML folder import, cover, TOC, SAF transfer; no automatic rescans')
