#!/usr/bin/env python3
"""Current 1.11.135 release contracts, no claims about Android runtime behavior."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
java=lambda n:(src/(n+'.java')).read_text(encoding='utf-8')
assert "versionCode 13500; versionName '1.11.135-beta'" in (root/'app/build.gradle').read_text()
settings=java('SettingsActivity')
block=settings[settings.index('private void ttsResumeChoiceDialog()'):settings.index('void drawAudioSettings()')]
assert 'UiNavigationSheet.showTopChoices' in block
assert 'new AlertDialog.Builder(this)' not in block
assert 'TtsResumeChoice.set(this' in block
assert 'С начала предыдущего предложения' in block and 'С начала текущего предложения' in block
ui=java('UiNavigationSheet')
assert 'UiColorScheme.selected(a)' in ui and 'UiColorScheme.accent(a)' in ui
assert 'R.style.ChitalkaSheetTop' in ui
repo=java('DownloadsRepository')
assert 'LinkedHashMap<String,MangaLibDownloadGroup> mangaGroups' in repo
assert 'String key=slug.toLowerCase(Locale.ROOT)' in repo
assert 'for(String key:mangaRows.keySet())' in repo
assert 'all.add(item);' in repo
assert 'item.id="manga:"+key' in repo
assert 'item.id="manga:"+j.id()' not in repo
assert 'item.unknownTotal=group.unknownChapters>0' in repo
assert 'item.coveragePercent=group.chapters' in repo
activity=java('DownloadActivity')
assert 'openMangaChapters(item)' in activity
assert 'exportMangaChapter(item.source)' in activity
assert 'Отменить очередь этой манги' in activity
assert 'cancelUniqueWork("mangalib_serial_downloads")' not in activity
assert 'MangaLibQueue.cancelSeries(this,item.source)' in activity
assert 'progress.setProgress(item.coveragePercent)' in activity
queue=java('MangaLibQueue')
assert 'static int cancelSeries(Context ctx,String slug)' in queue
assert 'if(!slug.equals(job.slug))continue;' in queue
chapter=java('MangaLibChapterDownloadsActivity')
assert 'Глав готово: ' in chapter
assert 'String lastVolume=null;' in chapter
for name in ['ic_audio_chapters.xml','ic_manga_search.xml']:
    ET.parse(root/'app/src/main/res/drawable'/name)
for f in (root/'app/src/main/res').rglob('*.xml'):ET.parse(f)
ET.parse(root/'app/src/main/AndroidManifest.xml')
print('PASS: v1.11.135 shared dialog, manga aggregation, scoped actions, assets and XML')
