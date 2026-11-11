#!/usr/bin/env python3
"""Regression contracts from v1.11.140-beta checked against v1.11.142-beta. Not an Android build/test."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/ru/reader/rsvp'
s=lambda name:(p/name).read_text()
assert 'sourceKey("kvuhe")' in s('KvuheSearchActivity.java') and 'sourceKey("mangalib")' in s('MangaLibSearchActivity.java')
assert 'Открыть папку' in s('DownloadActivity.java') and 'chooseMangaFilesToDelete' in s('DownloadActivity.java')
assert 'MangaLibStorage.seriesJobs(a,slug)' in s('UniversalDrawer.java')
assert 'Изменить обложку' in s('MainActivity.java') and 'ComicCovers.coverFromChapter' in s('MainActivity.java')
assert 'continueCover' in s('ComicCovers.java') and 'comicCover(recent,88,88,true)' in s('MainActivity.java')
assert 'toolbarStack.addView(titleChip,titleLp)' in s('ComicReaderActivity.java')
assert 'new LinearLayout.LayoutParams(-1,-2)' in s('ReaderActivity.java')
assert 'ImageView more=icon(R.drawable.ic_more_vertical' in s('DownloadActivity.java')
assert not list((r/'app/src/main/res').rglob('ic_import_link.png'))
assert not list((r/'app/src/main/res').rglob('ic_search_audiobook.png'))
node=ET.parse(r/'app/src/main/res/drawable/ic_import_link.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
assert node.get(a+'width')=='24dp' and node.get(a+'height')=='24dp'
assert node.get(a+'viewportWidth')=='24' and node.get(a+'viewportHeight')=='24'
assert node.find('group') is not None and node.find('group').get(a+'scaleX')=='0.08'
assert 'audioCoverCache' in s('AudioPlayerActivity.java') and 'coverExecutor' in s('AudioPlayerActivity.java')
assert 'header("Цвет выделения")' in s('SettingsActivity.java')
assert 'styleRgbSlider' in s('SettingsActivity.java') and 'bar.setThumbTintList' in s('SettingsActivity.java')
assert 'list.setPadding(dp(2),dp(6),dp(2),dp(12))' in s('SettingsActivity.java')
assert '.putExtra("reading_mode_override",0)' not in s('UniversalDrawer.java')
assert 'void openReadingFromDrawer(){' in s('ReaderActivity.java') and 'setMode(NORMAL);' not in s('ReaderActivity.java').split('void openReadingFromDrawer(){',1)[1].split('}',1)[0]
assert 'MangaDownloadCover.ensure(ctx,job)' in s('MangaLibDownloadWorker.java')
assert 'MangaDownloadCover.file(ctx,item.source)' in s('DownloadsRepository.java')
assert "versionCode 15100" in (r/'app/build.gradle').read_text()
print('PASS: 14 release source contracts (not runtime verification)')
