#!/usr/bin/env python3
"""Regression guard for the checked-exception compile failure in ComicReaderActivity."""
from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
java = root / 'app/src/main/java/ru/reader/rsvp'
reader = (java / 'ComicReaderActivity.java').read_text(encoding='utf-8')
library = (java / 'ComicLibrary.java').read_text(encoding='utf-8')
assert re.search(r'pagesForChapter\(Context c,Comic comic,Chapter ch\)\s+throws Exception', library)
assert re.search(r'prepareImageAssets\(ComicLibrary\.Chapter chapter\)\s+throws Exception', reader)
assert re.search(r'buildChapterHtml\(ComicLibrary\.Chapter chapter, int mode\)\s+throws Exception', reader)
assert 'ComicLibrary.pagesForChapter(this,comic,chapter)' in reader
print('PASS: checked exceptions are propagated to the existing chapter-load error handler')
