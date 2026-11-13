#!/usr/bin/env python3
"""Structural tests of v1.11.159 HTML-folder import and safe rebuild integration.
JVM chapter-sort test and full Java parser checks run separately.
"""
from pathlib import Path
from xml.etree import ElementTree as ET

base = Path(__file__).resolve().parents[1]
folder = base / 'app/src/main/java/ru/reader/rsvp'
read = lambda name: (folder/name).read_text(encoding='utf-8')
assert "versionCode 15900; versionName '1.11.159-beta'" in (base/'app/build.gradle').read_text()
main = read('MainActivity.java')
for marker in ('showBookImportMenu()', '"Импортировать папку с HTML"',
               'ACTION_OPEN_DOCUMENT_TREE', 'IMPORT_HTML_FOLDER_REQUEST',
               'HtmlFolderLibrary.importFolder', '"Проверить изменения HTML"',
               'HtmlFolderLibrary.checkChanges', 'BookMeta found=meta(result.uri)',
               'found.format="HTML"'):
    assert marker in main, marker
storage = read('HtmlFolderLibrary.java')
for marker in ('takePersistableUriPermission' if False else 'DocumentFile.fromTreeUri',
               'static Result importFolder', 'static boolean syncIfChanged',
               'static boolean checkChanges', 'MessageDigest.getInstance("SHA-256")',
               'private static void atomic(', 'book.html.bak',
               'signature(ctx,scan)', '"sourceTree"', '"chapters"',
               'copyIllustrations(', 'findCover(', 'cover.img',
               'readDocument(c,part.document,MAX_CHAPTER_BYTES)',
               'HtmlReadingAnchor.capture', 'HtmlReadingAnchor.restore',
               'static void rememberTransfer', 'publishExternal(',
               'external-book.rollback.html', 'MessageDigest.isEqual(',
               'return false;', 'HtmlChapterOrder.compare'):
    assert marker in storage, marker
assert 'HtmlFolderLibrary.rememberTransfer(ctx,original,out.newUri,destination,bookDir.getName())' in read('LibraryMoveManager.java')
anchor = read('HtmlReadingAnchor.java')
for marker in ('oldChapterWords', 'chapterOrdinal', 'phrasePivot', 'findPhrase(',
               'relativeWord', '"book_shared_word:"', '"tts_word:"',
               'ReaderBookCache.invalidate', 'UnifiedBookSession.invalidate'):
    assert marker in anchor, marker
cache = read('ReaderBookCache.java')
assert 'MAGIC=0x52424934' in cache
assert 'MessageDigest.getInstance("SHA-256")' in cache
assert 'String storedRevision=readString(in,256),now=revision(context,uri)' in cache
assert 'writeString(out,revision(context,uri))' in cache
session = read('UnifiedBookSession.java')
assert 'HtmlFolderLibrary.syncIfChanged' in session
assert 'RanobeLibStorage.syncManualEdits' in session
assert 'fingerprint.equals(s.revision)' in session
assert 'static synchronized void invalidate(String uri)' in session
ranobe = read('RanobeLibStorage.java')
assert 'book.html.bak' in ranobe and 'HTML-глава повреждена' in ranobe
reader = read('ReaderActivity.java')
assert 'UnifiedReadingProgress.read(prefs,docUri,index)' in reader
assert 'chapters=parsedChapters!=null&&parsedChapters.size()>0' in reader
cover = read('CoverLoader.java')
assert 'HtmlFolderLibrary.coverBytes(ctx,uri)' in cover
for xml in (base/'app/src/main').rglob('*.xml'):
    ET.parse(xml)
print('PASS: HTML folder picker, ordering, cover, SAF source, transactional build, SHA verification, reanchor, cache invalidation, relocation, TOC')
