#!/usr/bin/env python3
"""v1.11.148 fishing regression contracts (source checks; Android build still required)."""
from pathlib import Path
import xml.etree.ElementTree as ET
import re

root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
res=root/'app/src/main/res'
activity=(src/'ChteicoActivity.java').read_text()
game=(src/'ChteicoFishingView.java').read_text()
build=(root/'app/build.gradle').read_text()
a='{http://schemas.android.com/apk/res/android}'
assert 'versionCode 15200' in build and "versionName '1.11.152-beta'" in build
# 1: Pet hero card, lake and game root share ONE source-of-truth opaque surface.
assert 'l.setBackground(shape(petCardColor(),18))' in activity
assert 'new ChteicoFishingView(this,()->closeMiniGame(),petCardColor())' in activity
assert 'themeBg=petCardColor;' in game
assert 'setBackgroundColor(themeBg);' in game
assert 'pond.setBackgroundColor(themeBg);' in game
# 2: Right-only opaque panel, visible lake left of panel; catch elements remain inside.
layout=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
ids={el.get(a+'id'):el for el in layout.iter() if el.get(a+'id')}
get=lambda key:ids['@+id/'+key]
panel=get('fishing_catch_panel')
assert panel.get(a+'layout_width')=='match_parent' and get('fishing_catch_viewport').get(a+'layout_width')=='112dp'
assert panel.get(a+'layout_height')=='match_parent'
assert 'right' in get('fishing_catch_viewport').get(a+'layout_gravity')
assert panel.get(a+'background')=='@drawable/bg_fishing_stage'
assert 'catchPanel.setBackground(panelBackground(themeBg,accent,dp(14)))' in game
assert {'fishing_track','fishing_fish','fishing_zone','fishing_catch_progress'} <= {
    el.get(a+'id').split('/')[-1] for el in panel.iter() if el.get(a+'id')}
assert list(layout).index(get('fishing_pond')) < list(layout).index(get('fishing_catch_viewport'))
# 3: exactly x3 GOLD only if shimmering, without accidentally multiplying experience.
assert re.search(r'int gold=Math\.max\(1,Math\.round\(mass\*5\*mul2\[rarity\]/2f\*\(shimmering\?3f:1f\)\)\)',game)
assert 'int xp=Math.round(mass*20*mul2[rarity]/2f);' in game
assert 'recordFishingCatch(getContext(),Math.round(mass*1000),xp,gold,rarity,shimmering)' in game
# 4: Visible sparkle overlays above fish on both screens; 2 out-of-phase loops.
catch_children=[el.get(a+'id','').split('/')[-1] for el in get('fishing_track')]
assert catch_children.index('fishing_fish') < catch_children.index('fishing_catch_shimmer') < catch_children.index('fishing_catch_shimmer_trail')
result_container=next(el for el in get('fishing_result_panel') if el.tag=='FrameLayout')
result_children=[el.get(a+'id','').split('/')[-1] for el in result_container]
assert result_children.index('fishing_result_fish') < result_children.index('fishing_result_shimmer') < result_children.index('fishing_result_shimmer_trail')
assert 'float trailDirection=fishHeadingUp?1f:-1f;' in game
assert 'direction*dp(30)*t' in game and 'star.setRotation(0f)' in game
assert '-dp(32)*t' in game
assert 'Math.sin(Math.PI*t)' in game and 'now+offset)%1100L' in game
assert '550L' in game and 'renderCatchSparkles(SystemClock.uptimeMillis())' in game
# 5: Fishing rod tier 2+ has exact fatigue factor equal to the fill slowdown.
assert 'float endgameSlow=catchFillFactor(catchAmount)' in game
assert 'progress>=.90f?.70f:progress>=.70f?.85f:1f' in game
assert 'ChteicoStore.fishingRodEquipped(getContext())>=2' in game
assert 'float movementFactor=!hookedBoot' in game
assert 'fishY+=fishVelocity*dt*movementFactor' in game
assert 'float slow=ChteicoStore.fishingRodEquipped(getContext())==0?endgameSlow:1f;' in game
# 6: Bobber has a visibly SOLID hull and separate nontransparent top stripe.
bobber=ET.parse(res/'drawable/ic_fishing_bobber.xml').getroot()
paths=bobber.findall('path')
assert len(paths)>=3
opaque=[p for p in paths if p.get(a+'fillColor') not in (None,'@android:color/transparent','#00000000')]
assert len(opaque)>=2
assert 'M14,15 C10,18' in opaque[0].get(a+'pathData','')
assert all(p.suffix.lower() not in ('.png','.jpg','.jpeg','.webp','.gif') for p in res.rglob('*') if p.is_file())
for xml in res.rglob('*.xml'): ET.parse(xml)
print('PASS: v1.11.148 all 5 requirements + opaque XML float, compatible resource layouts')
