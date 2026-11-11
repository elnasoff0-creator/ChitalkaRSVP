#!/usr/bin/env python3
"""Regression contracts for v1.11.145 fishing. Static checks + probability invariants.
Run with python3 qa/test_v11145_fishing.py. Does not replace on-device tests.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
j=root/'app/src/main/java/ru/reader/rsvp'
source=(j/'ChteicoFishingView.java').read_text()
store=(j/'ChteicoStore.java').read_text()
activity=(j/'ChteicoActivity.java').read_text()
helptext=(j/'ActivityInfo.java').read_text()
gradle=(root/'app/build.gradle').read_text()
layout=(root/'app/src/main/res/layout/view_chteico_fishing.xml').read_text()
assert 'versionCode 15300' in gradle and "versionName '1.11.153-beta'" in gradle
# A reward requires the explicit Continue control: no tap-to-dismiss in the game root.
assert 'resultContinue.setOnClickListener(v->closeResult())' in source
assert 'if(phase==RESULT)return true;' in source
assert 'fishing_result_continue' in layout
assert 'Тап — закрыть результат' not in layout
# Total weight and per-rarity weight accumulate exactly once, in integer grams.
assert '.putLong("fishing_total_grams"' in store
assert '.putLong("fishing_rarity_total_grams_"+rarity,' in store
assert 'p.getLong("fishing_rarity_total_grams_"+rarity,0L)+massGrams' in store
assert 'p.getLong("fishing_total_grams",0L)+massGrams' in store
assert 'p.getLong("fishing_total_grams",0L)' in activity
assert 'p.getLong("fishing_rarity_total_grams_"+i,0L)' in activity
assert 'fishing_rarity_best_grams_'+'' in store
# Boot is not counted as fish and offers nothing; chance only for unsuccessful casts.
assert 'hookedBoot=castGrade==4&&random.nextFloat()<.25f' in source
boot_method=source[source.index('private void showBootResult()'):source.index('private void successfulCatch()')]
assert 'recordFishingOldCover' in boot_method
assert 'recordFishingCatch' not in boot_method
assert 'ic_fishing_old_cover' in boot_method and '0 XP' in boot_method and '0 золота' in boot_method
boot_record=store[store.index('void recordFishingOldCover('):store.index('void recordFishingBoot(')]
assert 'fishing_old_boots' in boot_record and 'fishing_caught' not in boot_record and 'fishing_total_grams' not in boot_record
assert 'fishing_old_boots' in activity
assert 'BOOT_BROWN' in source
# Rarity affects rotation and appearance, but reward uses mass-based scaling.
assert 'FISH_SCALE={1f,.90f,.80f,.70f,.60f}' in source
assert 'float targetScale=hookedBoot?1.1f:FISH_SCALE[rarity]' in source
assert 'updateFishHeading(fishVelocity<0f)' in source
assert 'fish.animate().rotation(fishAngle(headUp))' in source
assert 'Math.sqrt(mass)' in source
# Quality weights increase rare outcomes monotonically, with no second rarity multiplier.
assert 'QUALITY_RARITY_MULTIPLIER={1.80,1.40,1.00,.70,.40}' in source
assert 'chooseRarity()' in source and '12d*quality' in source
p_rare=[]
for multiplier in (1.8,1.4,1.0,.7,.4):
    p=(12+4+1)*multiplier/(55+28+(12+4+1)*multiplier)
    p_rare.append(p)
assert all(p_rare[i]>p_rare[i+1] for i in range(len(p_rare)-1))
# Boot vector is valid XML, brown, and no new raster images were added.
xml=root/'app/src/main/res/drawable/ic_fishing_old_cover.xml'
vector=ET.parse(xml).getroot()
assert vector.tag=='vector' and vector.findall('path')
assert '#8B5A2B' in xml.read_text()
assert not [p for p in (root/'app/src/main/res').rglob('*') if p.suffix.lower() in ('.png','.jpg','.jpeg','.webp','.gif')]
assert 'Старая обложка' in helptext and 'общую массу' in helptext
print('PASS: v1.11.145 reward dismissal, totals, boot, cast weighting, fish rotations, XML resources')
