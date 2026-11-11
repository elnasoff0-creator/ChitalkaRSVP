#!/usr/bin/env python3
"""Static release contracts for XML-only Chteico fishing. Not Android instrumentation."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
java=r/'app/src/main/java/ru/reader/rsvp'
res=r/'app/src/main/res'
get=lambda name:(java/name).read_text(encoding='utf-8')
game=get('ChteicoFishingView.java')
store=get('ChteicoStore.java')
activity=get('ChteicoActivity.java')
help=get('ActivityInfo.java')
reader=get('ReaderActivity.java')
comic=get('ComicReaderActivity.java')
audio=get('AudioPlaybackService.java')
assert "versionCode 15000" in (r/'app/build.gradle').read_text()
assert not (java/'ChteicoMiniGameView.java').exists()
assert 'new ChteicoFishingView(this,()->closeMiniGame(),petCardColor())' in activity
assert 'ChteicoFishingView activeMiniGame' in activity
assert 'Бегун' not in activity and 'Летун' not in activity
assert 'ChteicoStore.fishingBait' in game
assert 'useFishingBait' in game and 'phase==CASTING' in game
assert 'Math.abs(cast-.80f)' in game and 'castDirection' in game
assert 'phase==REELING&&held' in game
assert 'phase==FLOATING' in game and 'phase==CATCHING' in game
assert 'Math.exp(-p)' in game and 'held?-.53f:.34f' in game
assert 'catchAmount+dt*(inside?.26f*slow*shimmerFactor/fillTime:-.12f)' in game
assert 'recordFishingCatch' in game
assert 'Math.round(mass*20*mul2' in game
assert 'Math.round(mass*5*mul2' in game
assert 'buyFishingBait' in store and 'amount==10?270' in store
assert 'amount==50?1200' in store and 'amount==1?30' in store
assert 'fishing_comic_unique_pages' in store and 'total%10==0' in store
assert 'creditOnce(prefs(c),"audio"' in store and 'creditOnce(prefs(c),"read"' in store
assert 'ChteicoStore.awardAudioChapter(this,bookId,segment)' in audio
assert 'ChteicoStore.recordComicPage' in comic
assert 'ChteicoStore.awardReadChapter' in reader
assert 'ChteicoStore.awardAudioChapter' in reader
assert 'Чтейцо — Рыбалка' in help and 'Чтейцо — Рыбалка: редкость и награды' in help
assert 'Чтейцо — Бегун и Летун' not in help
fish_paths=list((res/'drawable').glob('ic_fishing_fish_*.xml'))
assert len(fish_paths)==5
for f in fish_paths:
 root=ET.parse(f).getroot()
 assert root.tag=='vector'
 assert root.findall('path')
assert not list(res.rglob('*fishing*.png'))
for f in (res/'drawable').glob('*fishing*.xml'):ET.parse(f)
root=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
all_ids={el.get(ns+'id') for el in root.iter()}
for id_ in ('fishing_bobber','fishing_cast_meter','fishing_cast_optimum','fishing_cast_indicator',
            'fishing_fish','fishing_zone','fishing_catch_progress','fishing_catch_panel','fishing_status'):
 assert '@+id/'+id_ in all_ids,id_
print('PASS: v1.11.143 fishing backward-compatible economy and reading milestones')
