#!/usr/bin/env python3
"""v1.11.150 regression contracts: source/XML only; Android Gradle/device QA still required."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/ru/reader/rsvp'
res=r/'app/src/main/res'
read=lambda name:(j/name).read_text()
game=read('ChteicoFishingView.java')
root=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
ids={v.get(a+'id','').split('/')[-1]:v for v in root.iter() if v.get(a+'id')}
assert "versionCode 15100" in (r/'app/build.gradle').read_text()
assert "versionName '1.11.151-beta'" in (r/'app/build.gradle').read_text()
# 1. Heading removed and all inner frame margins aligned. No fullscreen panel.
panel=ids['fishing_catch_panel'];progress=ids['fishing_catch_progress'];track=ids['fishing_track']
assert 'fishing_catch_header' not in ids and 'catchHeader' not in game
assert panel.get(a+'layout_width')=='match_parent' and ids['fishing_catch_viewport'].get(a+'layout_width')=='112dp' 
assert progress.get(a+'layout_marginTop')==progress.get(a+'layout_marginBottom')=='8dp'
assert track.get(a+'layout_marginTop')==track.get(a+'layout_marginBottom')=='8dp'
assert 'catchPanel.setBackground(panelBackground(themeBg,accent,dp(14)))' in game
assert list(root).index(ids['fishing_pond'])<list(root).index(ids['fishing_catch_viewport'])
# 2. Only training rod has late-stage progress reduction; advanced rods keep fish fatigue.
assert 'float slow=ChteicoStore.fishingRodEquipped(getContext())==0?endgameSlow:1f;' in game
assert 'ChteicoStore.fishingRodEquipped(getContext())>=2' in game
assert 'float movementFactor=!hookedBoot' in game
# 3. Last rod has 6% shimmer, other rods 1%; developer overrides.
assert 'ChteicoStore.fishingRodEquipped(getContext())==4?.06f:.01f' in game
assert 'ChteicoStore.developerUnlocked(getContext())' in game
# 4. The SAF tree is required and persisted; all three entry points present.
folder=read('EdgeFolderPicker.java')
assert 'ACTION_OPEN_DOCUMENT_TREE' in folder and 'takePersistableUriPermission' in folder
assert 'getPersistedUriPermissions' in folder and 'getSharedPreferences' in folder
assert 'EdgeFolderPicker.show(this)' in read('SettingsActivity.java')
assert 'EdgeFolderPicker.show(this)' in read('ReaderActivity.java')
assert 'EdgeFolderPicker.show(this)' in read('DownloadActivity.java')
assert 'resumeEdgeAfterFolderPick' in read('ReaderActivity.java')
assert 'EdgeFolderPicker.available(this)' in read('EdgeFullBookActivity.java')
assert 'EdgeFolderPicker.available(this)' in read('EdgeChapterDownloadsActivity.java')
# 5. Real chapter MP3 + WordBoundary JSON, with rehydration for ReaderActivity.
storage=read('EdgeBookStorage.java');archive=read('EdgeChapterArchive.java')
assert 'context.getCacheDir()' in storage and 'context.getExternalFilesDir' not in storage
assert 'new File(context.getCacheDir(),"PROChitalka/EdgeWork/Books")' in storage
assert 'new File(localMp3,key+".mp3")' in archive
assert 'Глава.mp3' in archive and 'Глава.words.json' in archive
assert 'words.put(normalized)' in archive and 'timeMs+w.startMs' in archive
assert 'clipChar-chapterChar+w.from' in archive
assert 'meta.put("offset",combinedBytes)' in archive
assert 'EdgeWordTimings.storeAligned' in archive and 'static synchronized int restore(' in archive
assert 'EdgeChapterArchive.restore(this,requested,name,dir)' in read('EdgeStreamingService.java')
assert 'EdgeChapterArchive.publish(this,job.uri,job.title' in read('EdgeFullBookService.java')
assert 'EdgeChapterArchive.publish(getApplicationContext(),saveUri' in read('EdgeStreamingService.java')
assert 'EdgeChapterArchive.savedBooks(ctx)' in read('DownloadsRepository.java')
assert 'EdgeChapterArchive.deleteSavedBook(context,uri)' in storage
assert not [p for p in res.rglob('*') if p.suffix.lower() in ('.png','.jpg','.jpeg','.webp','.gif')]
for f in res.rglob('*.xml'):ET.parse(f)
print('PASS: v1.11.150 fishing, persisted SAF folder, completed chapter MP3/WordBoundary, cache restoration and source resources')
