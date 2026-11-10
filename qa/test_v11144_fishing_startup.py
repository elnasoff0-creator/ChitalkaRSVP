#!/usr/bin/env python3
"""Regression: ensure fishing view casts agree with the Android layout.
Static checks only, not a substitute for assembleRelease or on-device testing.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

root=Path(__file__).resolve().parents[1]
src=(root/'app/src/main/java/ru/reader/rsvp/ChteicoFishingView.java').read_text()
activity=(root/'app/src/main/java/ru/reader/rsvp/ChteicoActivity.java').read_text()
gradle=(root/'app/build.gradle').read_text()
xml=root/'app/src/main/res/layout/view_chteico_fishing.xml'
ns='{http://schemas.android.com/apk/res/android}'
elements={x.get(ns+'id', '').split('/')[-1]: x.tag.rsplit('}',1)[-1]
          for x in ET.parse(xml).getroot().iter() if x.get(ns+'id')}
assignments=dict(re.findall(r'\b(\w+)\s*=\s*findViewById\(R\.id\.(\w+)\)',src))
assert assignments, 'No findViewById bindings found'
for field, resource in assignments.items():
    assert resource in elements, f'{field}: missing ID {resource}'
# Verify expected hierarchy for typed container references.
expected={
    'meter': 'FrameLayout', 'catchPanel': 'FrameLayout',
    'track': 'FrameLayout', 'resultPanel': 'LinearLayout',
    'hud': 'TextView', 'status':'TextView', 'feedback':'TextView',
    'biteMessage':'TextView', 'resultName':'TextView', 'resultMass':'TextView',
    'resultRarity':'TextView','resultRewards':'TextView',
    'bobber':'ImageView','fish':'ImageView','zone':'ImageView',
    'ring':'ImageView','resultFish':'ImageView',
    'catchProgress':'ProgressBar'
}
for field, tag in expected.items():
    assert elements[assignments[field]]==tag, f'{field}: expected {tag}, got {elements[assignments[field]]}'
# The result panel is a LinearLayout in XML, so it must not be cast to FrameLayout.
assert 'private final View resultPanel;' in src
assert not re.search(r'FrameLayout[^;]*\bresultPanel\s*;',src)
assert 'Could not initialize fishing UI' in activity
assert 'versionCode 14700' in gradle and "versionName '1.11.147-beta'" in gradle
print('PASS: Fishing startup widget types, recovery path, release version')
