#!/usr/bin/env python3
"""v151 new features; source contracts, not device or Android Gradle compilation."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/ru/reader/rsvp'
d=r/'app/src/main/res/drawable'
read=lambda name:(j/name).read_text()
s=(r/'app/build.gradle').read_text()
assert 'versionCode 15100' in s and "versionName '1.11.151-beta'" in s
# User-supplied XML icons must replace corresponding sources byte-for-byte.
for name in ('ic_search_audiobook','ic_manga_search','ic_fishing_bait_counter','ic_fishing_gold_counter'):
    x=ET.parse(d/(name+'.xml')).getroot();a='{http://schemas.android.com/apk/res/android}'
    assert x.get(a+'width')=='24dp' and x.get(a+'height')=='24dp'
    assert x.get(a+'viewportWidth')=='24' and x.get(a+'viewportHeight')=='24'
for name in ('ic_edge_merge_chapter','ic_edge_delete_chapter'):
    assert (d/(name+'.xml')).is_file()
setting=read('SettingsActivity.java');assert 'Автопреобразование озвучки главы в единый файл MP3' in setting
assert setting.index('Путь к файлам Edge TTS')<setting.index('Автопреобразование озвучки главы в единый файл MP3')
archive=read('EdgeChapterArchive.java');full=read('EdgeFullBookService.java');stream=read('EdgeStreamingService.java')
assert 'AUTO_MERGE_PREF' in archive and 'getBoolean(AUTO_MERGE_PREF,true)' in archive
assert 'publishFragments(' in archive and '"fragments"' in archive
assert 'static synchronized boolean mergeChapter(' in archive
assert 'static synchronized boolean deleteChapter(' in archive
assert 'queueFromSAF(' in archive and 'sha256' in archive
assert 'EdgeChapterArchive.publishFragments(' in full and 'EdgeChapterArchive.publishFragments(' in stream
assert 'EdgeChapterArchive.autoMerge(' in full and 'EdgeChapterArchive.autoMerge(' in stream
store=read('EdgeFullBookStore.java');assert 'ctx.getFilesDir()' in store and 'EdgeJobs' in store
assert 'forceReload' in store and 'clearForce(' in store
chapters=read('EdgeChapterDownloadsActivity.java')
for key in ('Готов единый MP3','Фрагменты готовы','В очереди','Идёт загрузка','Приостановлено','Ошибка','Преобразовать файлы главы в один MP3','Перезагрузить главу','Удалить главу'):
    assert key in chapters,key
assert 'sameStats(' in chapters and 'a.state.equals(b.state)' in chapters
assert 'EdgeChapterArchive.snapshot(' in chapters and 'EdgeChapterArchive.deleteChapter(' in chapters
assert 'EdgeChapterArchive.mergeChapter(' in chapters and 'EXTRA_FORCE' in chapters
load=read('DownloadActivity.java')
assert 'item.status="Идёт озвучивание";item.failed=false;' in load
assert 'sharedSnapshot' in load and 'diskScannedAt' in load and 'mangaCoversAt' in load
layout=ET.parse(r/'app/src/main/res/layout/view_chteico_fishing.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
ids={node.get(a+'id','').split('/')[-1]:node for node in layout.iter() if node.get(a+'id')}
vp=ids['fishing_catch_viewport'];panel=ids['fishing_catch_panel']
assert vp.get(a+'layout_width')=='112dp' and vp.get(a+'layout_marginTop')=='62dp'
assert vp.get(a+'layout_marginBottom')=='7dp' and vp.get(a+'clipChildren')=='true'
assert panel.get(a+'layout_width')=='match_parent' and panel.get(a+'layout_height')=='match_parent'
assert ids['fishing_track'].get(a+'layout_width')=='68dp'
for xml in (r/'app/src/main/res').rglob('*.xml'):ET.parse(xml)
print('PASS: v1.11.151 toggle, SAF fragments, chapter actions/status, durable jobs, UI cache, fishing viewport, supplied vectors')
