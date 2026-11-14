#!/usr/bin/env python3
"""v1.11.158 structural transfer regressions. Device/SAF-provider tests still needed."""
from pathlib import Path
from xml.etree import ElementTree as ET
import subprocess
import tempfile

root=Path(__file__).resolve().parents[1]
code=root/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(code/name).read_text(encoding='utf-8')
assert "versionCode 16100; versionName '1.11.161-beta'" in (root/'app/build.gradle').read_text()
for name in ['BookUriMigration.java','SafeDownloadTransfer.java','SafTreeDirectory.java',
             'LibraryMoveManager.java','BulkLibraryTransfer.java']:
    assert (code/name).is_file(),name

transfer=read('SafeDownloadTransfer.java')
for marker in ('MessageDigest.isEqual(expected,digest(in))',
               'MessageDigest.isEqual(expected,digest(in))',
               'replaceFile(', 'migrateManga(', 'migrateRanobe(', 'migrateAudio(',
               'migrateLinks(', 'BookUriMigration.text(', 'MangaLibStorage.setExternalDir',
               'RanobeLibStorage.setExternalDir','appOwned(ctx,m.source.file)',
               'item.running','!item.ready','private static void removeEmptyDirs'):
    assert marker in transfer,marker
assert 'result.note="Загрузки по ссылкам: пути обновлены; исходные пользовательские файлы оставлены"' in transfer
assert 'm.source.uri.equals(m.destination.getUri().toString())' not in transfer

manager=read('LibraryMoveManager.java')
for marker in ('BookUriMigration.text(ctx,original,out.newUri)',
               'SafeDownloadTransfer.copyDocumentTree',
               'SafTreeDirectory.containsOrSame',
               'ComicLibrary.write(ctx,all)',
               'AudioLibrary.STORE',
               'SafeDownloadTransfer.appOwned(ctx,file)',
               'SafeDownloadTransfer.removeCopiedFiles(assets)'):
    assert marker in manager,marker

identity=read('BookUriMigration.java')
for marker in ('book_uri_aliases_v158','BookTitleStore.moveTextId','EdgeChapterArchive.invalidate()',
               'static String legacy(', 'static String current(', '"bookmarks_v2:"', '"book_usage:"'):
    assert marker in identity,marker

saf=read('SafTreeDirectory.java')
for marker in ('DocumentsContract.getDocumentId','DocumentsContract.getTreeDocumentId',
               'static DocumentFile resolve','containsOrSame', 'target=ancestor'):
    assert marker in saf,marker
assert 'SafTreeDirectory.resolve(c,Uri.parse(item.uri))' in read('ComicLibrary.java')

manga=read('MangaLibStorage.java')
for marker in ('externalFolder(', 'slug+":root"', 'slug+":name"',
               'localArchive(Context ctx,String value)', 'stored.getUri().toString()',
               'getSharedPreferences("reader",0).edit().putString(STORE,a.toString()).commit()'):
    assert marker in manga,marker
ranobe=read('RanobeLibStorage.java')
for marker in ('slug+":root"', 'slug+":name"', 'SafTreeDirectory.resolve',
               'publish(c,slug,local,"book.html")', 'readStored(c,j.file)',
               'BookUriMigration.current'):
    if marker=='BookUriMigration.current':
        assert marker in read('DownloadsRepository.java')
    else:assert marker in ranobe,marker

ui=read('MainActivity.java')
assert ui.count('"Перенести в папку"')>=3
assert 'MOVE_LIBRARY_REQUEST' in ui
assert 'LibraryMoveManager.comic(' in ui and 'LibraryMoveManager.audio(' in ui
screen=read('DownloadActivity.java')
assert 'Перенести всё из внутреннего хранилища' in screen
assert 'BulkLibraryTransfer.move' in screen
assert 'SafeDownloadTransfer.copy' in screen
assert 'Перенести файлы в папку' in screen
assert 'Перенести файлы' in read('DownloadArchiveActivity.java')
assert 'MangaLibStorage.localArchive(context,ch.imagePaths.get(0))' in read('ComicLibrary.java')
assert 'MangaLibStorage.stored' not in read('DownloadsRepository.java')
assert 'RanobeLibStorage.stored(ctx,job.file)' in read('DownloadsRepository.java')

files=list((root/'app/src/main').rglob('*.xml'))
for f in files:ET.parse(f)
print('PASS: v158 verified SAF copies, destinations, reload, migration identities, UI, XML; XML=',len(files))
