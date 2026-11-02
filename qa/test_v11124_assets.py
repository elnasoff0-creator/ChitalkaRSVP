#!/usr/bin/env python3
"""Verifies release 1.11.124 icons, transparent-aware sprites, alignment and collisions.
No Android runtime or device execution.
"""
from pathlib import Path
from PIL import Image
import xml.etree.ElementTree as ET
import json,subprocess,tempfile
r=Path(__file__).resolve().parents[1]
java=r/'app/src/main/java/ru/reader/rsvp'
assets=r/'app/src/main/res/drawable-nodpi'
drawable=r/'app/src/main/res/drawable'
manifest=(r/'app/build.gradle').read_text()
assert 'versionCode 12400' in manifest and "versionName '1.11.124-beta'" in manifest
for name in ('ic_reward_audio10','ic_tts_pause_trim','ic_comic_library',
             'ic_edge_dictionary','ic_chteico_game','ic_drawer_comic','ic_tab_music'):
    path=drawable/f'{name}.xml'
    assert path.exists(),name
    root=ET.parse(path).getroot()
    andr='{http://schemas.android.com/apk/res/android}'
    assert root.attrib[andr+'width']=='24dp' and root.attrib[andr+'height']=='24dp'
assert not list(r.rglob('*.bak')), 'Backup files must not be packaged'
menu=(java/'DrawerMenuStore.java').read_text()
assert 'case "audio":return R.drawable.ic_tab_music;' in menu
assert 'case "comic":return R.drawable.ic_drawer_comic;' in menu
assert 'R.drawable.ic_comic_library' in (java/'MainActivity.java').read_text()

geometry=json.loads((r/'qa/game_assets_bounds_v11124.json').read_text())
for stage in ('chick','reader','adult'):
    variants=[]
    for pose in ('run1','run2','jump'):
        key=f'chteico_game_{stage}_{pose}'
        info=geometry[key]
        img=Image.open(assets/f'{key}.png').convert('RGBA')
        assert img.size==tuple(info['normalized_canvas'])
        assert img.size[0]<64 and img.size[1]<64
        assert img.getchannel('A').getbbox() is not None
        variants.append(img)
    assert len(set(x.size for x in variants))==1,'frames must use same canvas'
    baselines=[]
    for img in variants:
        bb=img.getchannel('A').getbbox()
        baselines.append(bb[3])
    assert len(set(baselines))==1,'feet/baselines must match across frames'
for i in range(1,6):
    key=f'chteico_book_{i}'
    img=Image.open(assets/f'{key}.png').convert('RGBA')
    assert img.size==tuple(geometry[key]['normalized_canvas'])
    assert img.size[0]<64 and img.size[1]<64
    assert img.getchannel('A').getbbox() is not None
runner=(java/'ChteicoRunnerActivity.java').read_text()
for token in ('visibleBounds(', 'frameVisible', 'bookVisible', 'RunnerHitboxes.pet(', 'RunnerHitboxes.book(',
              'body.intersects(obstacleHitbox(b))','bookHeights','getWidth()/bmp.getHeight()'):
    assert token in runner,token
assert 'px+playerSize*.73f' not in runner
with tempfile.TemporaryDirectory() as t:
    sources=[str(java/'RunnerHitboxes.java'),str(r/'qa/TestRunnerHitboxes.java')]
    subprocess.run(['javac','-encoding','UTF-8','-d',t]+sources,check=True)
    check=subprocess.run(['java','-cp',t,'ru.reader.rsvp.TestRunnerHitboxes'],check=True,capture_output=True,text=True)
    assert 'PASS' in check.stdout
print('PASS v1.11.124: 14 new sprites, aligned feet, true alpha bounds, hitboxes, 6 icons, drawer icons, version')
