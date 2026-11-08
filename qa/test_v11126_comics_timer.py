#!/usr/bin/env python3
"""v1.11.126 static contracts: UI routing, archive folders, page state, sleep timer layout.
These checks cannot substitute for real Android compilation or device tests.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/ru/reader/rsvp'
read=lambda file:(p/file).read_text(encoding='utf-8')
reader=read('ComicReaderActivity.java')
lib=read('ComicLibrary.java')
main=read('MainActivity.java')
covers=read('ComicCovers.java')
sheet=read('UiNavigationSheet.java')
audio=read('AudioPlayerActivity.java')
textreader=read('ReaderActivity.java')
timer=read('SleepTimerBadge.java')
assert 'UniversalDrawer.show(this)' in reader
assert 'UiNavigationSheet.showComicModes' in reader
assert 'UiNavigationSheet.showComicChapters' in reader
assert 'Spinner' not in reader
assert '"Страницы","Лента"' in sheet
assert 'positionInfo()' in reader and 'comic.page=' in reader
assert 'archive_tree' in lib and 'archive_folder' in lib and 'pagesForChapter' in lib
assert 'archives.sort' in lib and 'ensureArchiveExtracted' in lib
assert 'source.getUri()' in lib and 'zip.getInputStream(e)' in lib
assert '"Папка с архивами CBZ / CBR"' in main
assert 'private void importComicArchive()' not in main
assert 'ComicCovers.cover' in main and 'active.title' in main
assert 'ComicLibrary.chapters(c,comic)' in covers and 'ComicLibrary.pagesForChapter' in covers
assert 'SleepTimerBadge.layout' in textreader and 'SleepTimerBadge.layout' in audio
assert 'Gravity.CENTER' in timer and 'View.GONE' in timer
assert 'versionCode 12600' in (r/'app/build.gradle').read_text()
for name in ('ic_comic_mode_pages.xml','ic_comic_mode_feed.xml'):
    ET.parse(r/'app/src/main/res/drawable'/name)
assert 'ComicReaderActivity' in (r/'app/src/main/AndroidManifest.xml').read_text()
print('PASS 126: chapter folders, comic bottom sheets, page persistence, resume, centered sleep icons')
