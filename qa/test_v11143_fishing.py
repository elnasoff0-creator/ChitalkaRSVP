#!/usr/bin/env python3
"""Static source/resource release checks (do not replace Gradle or device tests)."""
from pathlib import Path
from xml.etree import ElementTree as ET
r=Path(__file__).resolve().parents[1]
j=r/'app/src/main/java/ru/reader/rsvp'
a=r/'app/src/main/res'
g=(j/'ChteicoFishingView.java').read_text()
s=(j/'ChteicoStore.java').read_text()
c=(j/'ChteicoActivity.java').read_text()
h=(j/'ActivityInfo.java').read_text()
manifest=(r/'app/src/main/AndroidManifest.xml').read_text()
gradle=(r/'app/build.gradle').read_text()
assert 'versionCode 14300' in gradle and "versionName '1.11.143-beta'" in gradle
assert 'android.permission.VIBRATE' in manifest
assert 'QUICK_TAP_MS=220L' in g and 'castWaitingSecondTap' in g and 'finishCast();ignoreUp=true;' in g
assert 'Math.abs(cast-.80f)' in g and 'bobberX=' in g and 'getWidth()*bobberX' in g
assert 'phase==REELING&&held' in g and 'phase==BITE' in g and 'biteNeedsRelease' in g
assert 'BITE_TIMES={6000,5000,4000,3000,2000}' in g
assert 'SPEED={1f,1.15f,1.35f,1.60f,1.90f}' in g
assert 'FILL_TIME={1f,1.15f,1.35f,1.65f,2f}' in g
assert 'fishing_rarity_best_grams_' in s and 'fishing_rarity_"+rarity' in s
assert 'showFishingCatchCollection' in c and '"Улов"' in c
assert 'Math.sqrt(mass)' in g and 'setRotation(rarity==0?-90f:0f)' in g
assert 'startBite(now)' in g and 'beginCatch()' in g and 'playWaves()' in g and 'vibrate(75)' in g
assert 'Чтейцо — Рыбалка' in h and '6, 5, 4, 3 и 2 секунды' in h
root=ET.parse(a/'layout/view_chteico_fishing.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
ids={x.get(ns+'id') for x in root.iter()}
for x in ['fishing_cast_feedback','fishing_bite_ring','fishing_bite_message','fishing_result_panel',
          'fishing_result_fish','fishing_result_rarity','fishing_wave_1','fishing_wave_2']:
 assert '@+id/'+x in ids,x
for i,duration in [(2,2000),(3,3000),(4,4000),(5,5000),(6,6000)]:
 animator=ET.parse(a/f'animator/fishing_bite_{i}s.xml').getroot()
 assert animator.get(ns+'duration')==str(duration)
 avd=ET.parse(a/f'drawable/ic_fishing_bite_{i}.xml').getroot()
 assert avd.tag=='animated-vector'
 assert avd.find('target').get(ns+'animation')==f'@animator/fishing_bite_{i}s'
for f in (a/'drawable').glob('ic_fishing_fish_*.xml'):
 parsed=ET.parse(f).getroot()
 assert parsed.tag=='vector' and parsed.findall('path')
 assert 'Q16,18 19,15' not in f.read_text() # old smile removed
assert len(list((a/'drawable').glob('ic_fishing_fish_*.xml')))==5
assert len({p.read_text() for p in (a/'drawable').glob('ic_fishing_fish_*.xml')})==5
assert not [p for p in a.rglob('*') if p.suffix.lower() in ('.png','.jpg','.jpeg','.gif','.webp')]
for p in a.rglob('*.xml'):ET.parse(p)
print('PASS: 1.11.143 fishing rarity, XML graphics, catch stats, cast/bite mechanics, countdown, help')
