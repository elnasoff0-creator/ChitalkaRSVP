#!/usr/bin/env python3
"""v1.11.147 fishing specifications: sources, resources and migration contracts.

Static checks only; Android Gradle compilation / actual device playtesting remain necessary.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
base=Path(__file__).resolve().parents[1]
java=base/'app/src/main/java/ru/reader/rsvp'
res=base/'app/src/main/res'
game=(java/'ChteicoFishingView.java').read_text()
ui=(java/'ChteicoActivity.java').read_text()
store=(java/'ChteicoStore.java').read_text()
help=(java/'ActivityInfo.java').read_text()
version=(base/'app/build.gradle').read_text()
assert 'versionCode 14800' in version and '1.11.148-beta' in version
# 1: rod renamed but persisted rod tier four remains untouched
assert '"Удочка прочитателя"' in game and '"Спиннинг"' not in game
assert 'удочка прочитателя за 8000' in help
assert 'fishing_rod_owned_'+'' in store and 'fishing_rod_equipped' in store
assert 'FISHING_ROD_PRICES={0,180,800,3000,8000}' in store
# 2: store icon, no text plus button
layout=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
ns='{http://schemas.android.com/apk/res/android}'
ident=lambda name:'@+id/'+name
idmap={v.get(ns+'id'):v for v in layout.iter() if v.get(ns+'id')}
shop=idmap[ident('fishing_shop')]
assert shop.tag=='ImageView' and shop.get(ns+'src')=='@drawable/ic_fishing_shop'
assert 'shopIcon).setColorFilter(ink)' in game
assert (res/'drawable/ic_fishing_shop.xml').is_file()
# 3: surface graphics are water at the top and reeds at the bottom
pond=(res/'drawable/ic_fishing_pond.xml').read_text()
assert 'M20,21 L125,21' in pond and 'M36,297' in pond
assert 'fishing_pond' in game and 'pond).setColorFilter(ink)' in game
# 4: v148 revises the former fullscreen layer into an opaque right-side panel
panel=idmap[ident('fishing_catch_panel')]
assert panel.get(ns+'layout_height')=='match_parent' and panel.get(ns+'layout_width')=='120dp'
assert panel.get(ns+'background')=='@drawable/bg_fishing_stage'
assert 'catchPanel.setBackground(panelBackground(themeBg,accent,dp(14)))' in game
assert {e.get(ns+'id') for e in panel.iter()} >= {ident('fishing_track'),ident('fishing_zone'),ident('fishing_fish'),ident('fishing_catch_progress')}
# 5: all major game cards/surfaces theme-based, no static purple primary
assert 'themeBg=petCardColor;' in game and 'ReaderButtonColors.accent' in game
for name in ('resultPanel','resultContinue','shopIcon','status','catchPanel'):
 assert name+'.setBackground(' in game, name
assert 'resultContinue.setTextColor(accent)' in game
assert 'line.setBackground(panelBackground(surface,i==equipped?accent:ink,dp(12)))' in game
# 6: hints restricted to training rod, centrally mounted and opaque
status=idmap[ident('fishing_status')]
assert status.get(ns+'layout_gravity')=='center'
assert status.get(ns+'background')=='@drawable/bg_fishing_stage'
assert 'trainingRod=ChteicoStore.fishingRodEquipped(getContext())==0' in game
assert 'showTutorial=trainingRod' in game
# 7: reuse legacy count, but new visible name/illustration; old find has same catching silhouette as a fish
assert 'recordFishingOldCover(getContext())' in game
assert 'fishing_old_boots' in store and 'recordFishingOldCover' in store
assert 'fish.setImageResource(R.drawable.ic_fishing_catch_silhouette)' in game
assert 'ic_fishing_boot_silhouette' not in game
assert 'ic_fishing_old_cover' in game and 'Старые обложки' in ui
assert not (res/'drawable/ic_fishing_old_boot.xml').exists()
assert not (res/'drawable/ic_fishing_boot_silhouette.xml').exists()
assert '#8B5A2B' in (res/'drawable/ic_fishing_old_cover.xml').read_text()
# 8: no version banner within catch list
assert 'Масса учитывается с v1.' not in ui
# 9: every fish is shimmering in dev mode; never the old cover
assert 'shimmering=!hookedBoot&&(ChteicoStore.developerUnlocked(getContext())||random.nextFloat()<.01f)' in game
assert 'if(shimmering)' in store
# 10: badges derived from persisted counts and rod ownership, with individual vectors
expected={'ic_reward_fishing':'"Рыбак"','ic_reward_fishing_shimmer':'"Охотник за мерцанием"',
 'ic_reward_fishing_legendary':'"Легендарный улов"','ic_reward_reader_rod':'"Удочка прочитателя"'}
for name,label in expected.items():
 assert 'R.drawable.'+name in ui and label in ui
 icon=ET.parse(res/f'drawable/{name}.xml').getroot()
 assert icon.tag=='vector' and icon.findall('path')
assert 'p.getInt("fishing_caught",0)>0' in ui
assert 'p.getInt("fishing_shimmer_count",0)>0' in ui
assert 'p.getInt("fishing_rarity_3",0)>0' in ui
assert 'ChteicoStore.hasFishingRod(this,4)' in ui
# check XML all parse; no rasters
for path in res.rglob('*.xml'):ET.parse(path)
assert not [p for p in res.rglob('*') if p.suffix.lower() in ('.png','.jpg','.jpeg','.webp','.gif')]
print('PASS: v1.11.147 all 10 fishing update contracts, XML only')
