#!/usr/bin/env python3
"""v1.11.149 layout and behavior contracts. Static checks; not device/Gradle tests."""
from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
res=r/'app/src/main/res'
java=(r/'app/src/main/java/ru/reader/rsvp/ChteicoFishingView.java').read_text()
gradle=(r/'app/build.gradle').read_text()
a='{http://schemas.android.com/apk/res/android}'
layout=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
byid={n.get(a+'id','').split('/')[-1]:n for n in layout.iter() if n.get(a+'id')}
assert "versionCode 15000" in gradle and "versionName '1.11.150-beta'" in gradle
# compact toolbar stands on its own and occupies space ABOVE the now-shortened lake
bar=byid['fishing_toolbar']; pond=byid['fishing_pond']
assert bar.tag=='LinearLayout' and bar.get(a+'layout_height')=='48dp'
assert pond.get(a+'layout_marginTop')=='62dp'
assert int(pond.get(a+'layout_marginTop').replace('dp',''))>int(bar.get(a+'layout_height').replace('dp',''))
assert list(layout).index(pond)<list(layout).index(bar)
assert 'Рыбалка' not in ET.tostring(bar,encoding='unicode')
assert all(key in byid for key in ('fishing_bait_icon','fishing_hud','fishing_gold_icon','fishing_gold_count','fishing_shop','fishing_close'))
assert 'hud.setText(String.valueOf(bait));goldCount.setText(String.valueOf(gold));' in java
assert 'if(y<=dp(57))' in java and 'return true;' in java
assert 'gameToolbar.setBackground(panelBackground(surface,ink,dp(13)))' in java
for kind in ('bait','gold'):
 icon=ET.parse(res/f'drawable/ic_fishing_{kind}_counter.xml').getroot()
 assert icon.tag=='vector' and icon.get(a+'viewportWidth')=='24' and icon.get(a+'viewportHeight')=='24'
# fish swims in right-hand opaque panel without a title/header
panel=byid['fishing_catch_panel']
assert panel.get(a+'layout_width')=='120dp' and panel.get(a+'layout_height')=='match_parent'
assert panel.get(a+'layout_marginTop')=='62dp'
assert 'fishing_catch_header' not in byid
track=byid['fishing_track']
assert track.get(a+'layout_marginTop')==track.get(a+'layout_marginBottom')=='8dp'
assert track.get(a+'layout_marginTop')==byid['fishing_catch_progress'].get(a+'layout_marginTop')
assert panel.get(a+'background')=='@drawable/bg_fishing_stage' and 'catchPanel.setBackground(panelBackground(themeBg,accent,dp(14)))' in java
# Real vertical bar: bottom-aligned fill, not a rotated horizontal ProgressBar.
progress=byid['fishing_catch_progress'];fill=byid['fishing_catch_progress_fill']
assert progress.tag=='FrameLayout' and progress.get(a+'layout_width')=='13dp'
assert progress.get(a+'layout_height')=='match_parent'
assert fill.tag=='View' and fill.get(a+'layout_height')=='0dp' and fill.get(a+'layout_gravity')=='bottom'
assert 'renderVerticalCatchProgress()' in java
assert 'available*Math.max(0f,Math.min(1f,catchAmount))' in java
assert 'catchProgressFill.setLayoutParams(lp)' in java
assert 'catchProgressFill.setBackground(panelBackground(accent,0,dp(5)))' in java
assert 'ProgressBar' not in java
for p in res.rglob('*.xml'):ET.parse(p)
assert not [p for p in res.rglob('*') if p.suffix.lower() in ('.png','.jpeg','.jpg','.gif','.webp')]
print('PASS: v1.11.149+ vertical progress, title-free uniform catch panel, compact toolbar, XML icons and themes')
