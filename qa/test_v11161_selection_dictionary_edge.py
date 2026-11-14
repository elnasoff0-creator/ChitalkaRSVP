#!/usr/bin/env python3
"""Structural release contract for v161; runtime gestures/SAF still need device testing."""
from pathlib import Path
from xml.etree import ElementTree as ET
from subprocess import run
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda file:(src/file).read_text(encoding='utf-8')
assert "versionCode 16100; versionName '1.11.161-beta'" in (root/'app/build.gradle').read_text()
reader=read('ReaderActivity.java')
for key in ('private void beginTextSelection(', 'private void showSelectionMenu(',
            'private void moveSelectionHandle(', 'private void pickSelectionColor(',
            'private void copySelectedText(', 'private void editSelectedNote(',
            'private void dictionarySelected(', 'new ReaderSelectionController(',
            'renderAnnotations(this,c)', 'ReaderAnnotationsStore',
            'BookUriMigration.legacy(this,docUri)', 'getLongPressTimeout()',
            'EdgeDictionaryRuleDialog.show(this,null,EdgeDictionaryStore.REPLACE'):
    assert key in reader,key
assert 'HtmlFolderLibrary.syncIfChanged' not in reader
assert 'RanobeLibStorage.syncManualEdits' not in reader
assert 'ReaderBookCache.invalidate' not in reader
assert 'selectionFrom=selectionTo=map.clamp(word)' in reader
assert 'bookAnnotations=store.load' in reader
assert 'selectionHandles=new ReaderSelectionController' in reader
assert 'private static int' not in read('ReaderAnnotationsStore.java')
store=read('ReaderAnnotationsStore.java')
assert 'CREATE TABLE annotations' in store and 'CREATE INDEX idx_annotations_book' in store
assert 'from_word' in store and 'to_word' in store and 'context' in store and 'note' in store
assert 'getWritableDatabase().update' in store and 'getWritableDatabase().delete' in store
assert 'source' not in store.lower() or 'book' in store
forms=read('EdgeDictionaryRuleDialog.java')
for key in ('EdgeDictionaryStore.get(activity)', 'store.upsert(',
            'EdgeSpeechTextTransform.compileUserRegex(pattern)',
            'EdgeTtsClient.synthesize(', 'Ударение', 'Ограничить правилом',
            'Произносить как', 'Игнорировать при озвучивании'):
    assert key in forms,key
assert 'Из предложения' not in read('EdgeDictionaryActivity.java')
assert 'EdgeDictionaryRuleDialog.show(this,current' in read('EdgeDictionaryActivity.java')
assert 'Регулярные выражения словаря Edge TTS",RegexHelp.content()' in read('ActivityInfo.java')
assert '(?=abc)' in read('RegexHelp.java') and '(?<!abc)' in read('RegexHelp.java')
settings=read('SettingsActivity.java')
for i in range(4):assert f'annotation_color_{i}' in settings
assert 'annotation_opacity' in settings
queue=read('EdgeSpeechQueue.java')
assert 'INITIAL_429_DELAY_MS=60_000L' in queue
assert 'MAX_LOCAL_DELAY_MS=15*60_000L' in queue
assert 'retryAfterMillis(response)' in queue
archive=read('EdgeChapterArchive.java')
assert 'publicationError()' in archive and 'publishFailure(ex)' in archive
assert 'Проверка записанного MP3' in archive and 'SHA-256 итогового MP3' in archive
assert 'publicationError()' in read('EdgeFullBookService.java')
downloads=read('EdgeChapterDownloadsActivity.java')
assert 'finishCachedChapter(item.chapter)' in downloads
assert 'private void finishCachedChapter(' in downloads
assert 'Не хватает ' in downloads and 'EdgeFullBookStore.markPublished(job,chapter)' in downloads
assert 'EdgeChapterArchive.publishFragments' in downloads
ns='{http://schemas.android.com/apk/res/android}'
for n in ('color','copy','note','dictionary'):
    f=root/f'app/src/main/res/drawable/ic_reader_selection_{n}.xml'
    v=ET.parse(f).getroot()
    assert v.attrib[ns+'width']=='24dp' and v.attrib[ns+'viewportWidth']=='24'
    assert v.find('path') is not None and v.find('path').attrib[ns+'fillType']=='evenOdd'
for p in (root/'app/src/main').rglob('*.xml'):ET.parse(p)
print('PASS: v161 selection & handles, 4 traced icons, annotations, dictionary overlay, regex help, 429 and SAF finalization')
