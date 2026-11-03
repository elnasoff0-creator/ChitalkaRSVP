#!/usr/bin/env python3
"""Version 1.11.127 release contracts. Source assertions plus real JVM math tests.
Not a replacement for Gradle/Javac Android build or on-device testing.
"""
from pathlib import Path
import subprocess, tempfile, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/(name+'.java')).read_text(encoding='utf-8')
main=read('MainActivity');home=read('HomeSectionOrder');comic=read('ComicLibrary');viewer=read('ComicReaderActivity')
local=read('AudioPlaybackService');edge=read('EdgePlaybackService');stream=read('EdgeStreamingService');reader=read('ReaderActivity')
checks={
    'versionName / versionCode':"versionCode 12700; versionName '1.11.127-beta'" in (root/'app/build.gradle').read_text(),
    'seven home sections': '"comicContinue","comic"' in home and 'if(bounds.length!=8)' in home,
    'migration preserves order': 'int location=result.indexOf("comic")' in home,
    'comics resume separated': 'addComicContinue(contentWrap)' in main and main.index('addComicContinue(contentWrap)')<main.index('addComicSection(contentWrap)'),
    'seven section boundaries': 'comicContinueStart,comicStart,audioStart,audioLibraryStart[0]' in main,
    'section rename': 'home_section_title:' in home and 'static void rename(' in home,
    'rename dialog': 'setTitle("Название блока")' in home and 'rename.setOnClickListener' in home,
    'reset titles': 'e.remove("home_section_title:"+id)' in home,
    'hide and reorder preserved': 'setOnDragListener' in home and 'putStringSet(HIDDEN' in home,
    'no audio list play button': 'TextView play=buttonish(finished' not in main,
    'audio list overflow menu': 'v->audioMenu(ab)' in main,
    'comic overflow menu': 'v->comicMenu(item)' in main,
    'book overflow menu': 'v->bookMenu(m)' in main,
    'uniform action menu': main.count('"Сделать текущей","Убрать из библиотеки"')==2 and '"Сделать текущей", "Убрать из библиотеки"' in main,
    'no file deletion on remove': 'EdgeBookStorage.removeBook(this, m.uri)' not in main and 'ComicLibrary.remove(this,item.uri)' in main,
    'confirm forget': 'setTitle("Убрать из библиотеки?")' in main and 'Файлы на устройстве сохранятся' in main,
    'no long-press menus on book': 'row.setOnLongClickListener(v->true)' in main,
    'no long-press menus on audio': 'r.setOnLongClickListener(v->true)' in main,
    'no long-press menus on comic': 'line.setOnLongClickListener(v->true)' in main,
    'comic cover': 'ComicCovers.cover(this,comic)' in main,
    'compact shared progress': main.count('compactProgress()')>=2 and 'int progressPercent()' in comic,
    'serialize current page': '.put("page",page)' in comic and '.put("pageTotal",pageTotal)' in comic,
    'serialize chapter count': '.put("chapterTotal",chapterTotal)' in comic,
    'serialize chapter size index': 'o.put("pageCounts",counts)' in comic,
    'no guess when missing counts': 'ComicProgressMath.percent(pageCounts,chapter,page,chapterTotal)' in comic,
    'count archives in background': 'new Thread(()->' in main and 'ComicLibrary.indexCounts(' in main,
    'MangaLib count': '"mangalib_cbz"' in comic and 'countLocalCbz(' in comic,
    'CBR metadata count': 'com.github.junrar.Archive' in comic and 'countArchive(' in comic,
    'ComicReader update counts': 'comic.pageCounts.set(comic.chapter,count)' in viewer,
    'chapter transition resets stale total': 'comic.pageTotal=comic.chapter<comic.pageCounts.size()' in viewer,
    'local TTS service rewind': 'ttsRewindOnNextPlay' in local and 'ttsPosition=Math.max(0,ttsPosition-1)' in local,
    'local Play uniform': 'private void resume()' in local and 'case PLAY:' in local,
    'Edge TTS player rewind': 'rewindAfterUserPause' in edge and 'resumeSelection.rewindToPreviousSentence()' in edge,
    'Edge streamer sentence selection': 'private void rewindOnResume()' in stream and 'setAnchor(previous,true)' in stream,
    'Edge live WordBoundary': 'liveWords(clip)' in stream and 'EdgeWordTimings.at(timing,ms)' in stream,
    'Reader removed UI-only rewind': 'resumePreviousSentence' not in reader and 'ReaderTtsController.localPauseOrResume(this)' in reader,
    'comic reader gestures unchanged': 'setChromeVisible(!chromeVisible)' in viewer and 'setBuiltInZoomControls(true)' in viewer,
    'comic reader style unchanged': 'ReaderToolbarController.reveal(toolbarOverlay,show,98)' in viewer,
}
for name,passed in checks.items():
    if not passed:raise AssertionError('FAIL '+name)
with tempfile.TemporaryDirectory() as temp:
    subprocess.run(['javac','-encoding','UTF-8','-d',temp,str(src/'ComicProgressMath.java'),str(root/'qa/TestComicProgressMath.java')],check=True)
    subprocess.run(['java','-cp',temp,'ru.reader.rsvp.TestComicProgressMath'],check=True)
for file in (root/'app/src/main/res').rglob('*.xml'):
    ET.parse(file)
print(f'PASS: {len(checks)} source contracts; 6 actual JVM percent tests; XML resources parse')
