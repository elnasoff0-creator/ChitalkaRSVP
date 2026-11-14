#!/usr/bin/env python3
"""v1.11.162 source contracts; no Android SDK or internet required."""
import pathlib, re, subprocess, tempfile, xml.etree.ElementTree as ET
root=pathlib.Path(__file__).resolve().parents[1]
java=root/'app/src/main/java/ru/reader/rsvp'
def src(name):return (java/name).read_text(encoding='utf-8')
assert "versionCode 16200; versionName '1.11.162-beta'" in (root/'app/build.gradle').read_text()
assert 'FIRST_TARGET_PRIORITY=500' in src('EdgeSpeechSegments.java')
assert 'SentenceChunks.split(raw)' in src('EdgeSpeechSegments.java')
assert 'MAX_LOCAL_DELAY_MS=500L' in src('EdgeSpeechQueue.java')
assert 'sentenceIndex=session.sentenceIndex' in src('EdgeStreamingService.java')
assert 'public void onSwiped' in src('DownloadActivity.java')
assert 'private void resolvePreview(' in src('DownloadActivity.java')
assert 'item.previewOnly&&DownloadsRepository.EDGE.equals(item.type)' in src('DownloadActivity.java')
assert 'revealedSwipeId' in src('DownloadActivity.java')
assert 'showNoteChoices' in src('ReaderActivity.java')
assert 'bookAnnotationId()' in src('ReaderActivity.java')
assert 'bookmarkSelection' in src('ReaderActivity.java')
assert 'private void bookmark()' not in src('ReaderActivity.java')
assert '"Все заметки","Все закладки"' in src('ReaderSheets.java')
for icon in ('ic_reader_selection_color.xml','ic_reader_selection_copy.xml','ic_reader_selection_note.xml','ic_reader_selection_dictionary.xml'):
    assert (root/'app/src/main/res/drawable'/icon).exists()
assert 'ReaderChapterLabels.atWord' in src('BookmarksActivity.java')
assert 'ReaderAnnotationsStore' in src('BookmarksActivity.java')
assert '"Удалить"' in src('BookmarksActivity.java') and '"Отмена"' in src('BookmarksActivity.java')
assert 'String chapterBody=RanobeLibHtmlCleaner.chapterBody(document' in src('RanobeLibStorage.java')
assert 'Пересобрать HTML без служебного текста' in src('RanobeLibBookActivity.java')
assert 'static String chapterBody(' in src('RanobeLibHtmlCleaner.java')
assert 'HtmlFolderLibrary.syncIfChanged(' not in src('UnifiedBookSession.java')
# Parse ALL Java source; JavacTask.parse() does not need Android dependencies.
compiler=pathlib.Path(__file__).with_name('ParseSources162.java')
with tempfile.TemporaryDirectory() as temp:
    subprocess.run(['javac','-d',temp,str(compiler)],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    output=subprocess.run(['java','-cp',temp,'ParseSources162',str(root/'app/src/main/java')],
        capture_output=True,text=True,check=True)
    print(output.stdout.strip())
    test=[java/'SentenceChunks.java',java/'BookChapterIndex.java',java/'EdgeSpeechSegments.java',root/'qa/TestEdgeSegments162.java']
    subprocess.run(['javac','-d',temp]+list(map(str,test)),check=True,capture_output=True,text=True)
    result=subprocess.run(['java','-cp',temp,'ru.reader.rsvp.TestEdgeSegments162'],check=True,capture_output=True,text=True)
    print(result.stdout.strip())
count=0
for xml in (root/'app/src/main').rglob('*.xml'):
    ET.parse(xml);count+=1
print(f'PASS: v1.11.162 UI/data contracts; {count} Android XML files valid')
