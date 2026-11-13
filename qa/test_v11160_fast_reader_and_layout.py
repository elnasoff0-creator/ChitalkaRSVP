#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET
p=Path(__file__).resolve().parents[1]
s=p/'app/src/main/java/ru/reader/rsvp'
read=lambda n:(s/n).read_text(encoding='utf-8')
assert "versionCode 16000; versionName '1.11.160-beta'" in (p/'app/build.gradle').read_text()
session=read('UnifiedBookSession.java')
assert session.index('if(s!=null)return s;')<session.index('ReaderBookCache.revision(context,source)')
assert 'syncIfChanged' not in session and 'syncManualEdits' not in session
cache=read('ReaderBookCache.java')
revision=cache[cache.index('static String revision'):cache.index('public static void invalidate')]
assert 'sourceSize(c,uri)' in revision and 'MessageDigest' not in revision
assert 'getContentResolver().openInputStream' not in cache
assert 'legacyHtmlIndex' in cache
assert 'sentenceIndex=SentenceIndex.fromText(b.text)' in session
assert 'ttsSentenceIndex=session.sentenceIndex' in read('ReaderActivity.java')
assert 'sentenceIndex=session.sentenceIndex' in read('EdgeStreamingService.java')
assert 'sentenceIndex=session.sentenceIndex' in read('AudioPlaybackService.java')
folder=read('HtmlFolderLibrary.java')
assert 'static Result importFolder' in folder and 'static boolean syncIfChanged' not in folder
assert 'static boolean checkChanges' not in folder and 'HtmlReadingAnchor' not in folder
assert '"sourceTree"' in folder and 'BookLoader.ranobeHtml' in folder
assert 'HtmlReadingAnchor.java' not in [v.name for v in s.glob('*.java')]
main=read('MainActivity.java')
assert '"Импортировать папку с HTML"' in main
assert 'Проверить изменения HTML' not in main
assert 'checkHtmlUpdates' not in main
ranobe=read('RanobeLibStorage.java')
assert 'syncManualEdits(' not in ranobe
assert 'ReaderBookCache.invalidate(c,Uri.parse(published));' in ranobe
assert 'UnifiedBookSession.invalidate(published);' not in ranobe
edge=read('EdgeSpeechSegments.java')
assert 'EdgeSentenceBoundary.split(raw)' in edge
assert 'SentenceChunks.split(raw)' not in edge
assert 'groupEndChar' in edge and 'FIRST_TARGET_PRIORITY' not in edge
assert 'wordEnd>limit' not in edge
assert 'EdgeSpeechSegments.priorityPieces(' in read('EdgeStreamingService.java')
assert 'EdgeTextSegments.all(raw,book.chapters,job.chars)' in read('EdgeFullBookService.java')
download=read('DownloadActivity.java')
assert 'LinearLayout topBar=' in download
assert 'topBar.addView(back' in download
assert 'toolbar.setGravity(Gravity.END|Gravity.CENTER_VERTICAL)' in download
assert 'toolbarScroll.setFillViewport(true)' in download
assert 'headingRow.addView(archive' in download
assert download.index('ImageView archive=')>download.index('TextView heading=text("Загрузки"')
assert 'toolbarAction(toolbar,R.drawable.ic_download_archive' not in download
for xml in (p/'app/src/main').rglob('*.xml'):ET.parse(xml)
print('PASS: fast reader, no SHA scanning or HTML refresh; Edge sentence boundaries, aligned toolbar, archive row, XML')
