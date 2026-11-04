#!/usr/bin/env python3
"""Source-level release contract checks. Does not replace Android compilation/runtime QA."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda s:(src/s).read_text(encoding='utf-8')
gradle=(root/'app/build.gradle').read_text()
main=read('MainActivity.java'); home=read('HomeSectionOrder.java'); drawer=read('DrawerEditorActivity.java')
reader=read('ReaderActivity.java'); comic=read('ComicReaderActivity.java')
focus=read('PlaybackAudioFocus.java'); local=read('AudioPlaybackService.java')
edge=read('EdgePlaybackService.java'); stream=read('EdgeStreamingService.java')
settings=read('SettingsActivity.java'); unidrawer=read('UniversalDrawer.java')
checks={
 'version 12900':bool(re.search(r"versionCode\s+12900\s*;\s*versionName\s+'1\.11\.129-beta'",gradle)),
 '7 independent main sections':len(re.search(r'IDS=\{([^}]+)\}',home).group(1).split(','))==7,
 'consistent home spacing':'editing?8:12' in home and 'lp.bottomMargin=dp(activity' in home,
 'editing hides actual contents':'group.getChildAt(child).setVisibility(View.GONE)' in home,
 'live home reordering':'ACTION_DRAG_ENTERED' in home and 'parent.addView(source' in home,
 'live drawer reordering':'ACTION_DRAG_ENTERED' in drawer and 'entries.addView(source' in drawer,
 'home edit pencil after search':main.find('R.drawable.ic_edit_name')>main.find('R.drawable.ic_search'),
 'drawer pencil': 'MenuEditPrefs.longPress' in unidrawer and 'ic_edit_name' in unidrawer,
 'shared editing preference': 'MenuEditPrefs.LONG_PRESS' in settings,
 'settings general rename': 'Общие' in settings,
 'autopause preference': 'PlaybackAudioFocus.AUTO_PAUSE_KEY' in settings,
 'background no pause on reader release': 'private void releaseTts()' in reader and 'unbindService' in reader,
 'external focus modes': all(token in focus for token in ['MODE_RINGTONE','MODE_IN_CALL','MODE_IN_COMMUNICATION','AUTO_PAUSE_KEY']),
 'local player deferred selection': 'preparedSwitch=new Intent(intent)' in local,
 'local only paused on other Play': 'AudioModeCoordinator.stopEdge(this)' in local,
 'edge only paused on local Play': 'AudioModeCoordinator.pauseLocal(this)' in edge,
 'Edge source prepared selection': 'pendingBookUri' in stream and 'activatePreparedBook' in stream,
 'local sentence navigation': 'SentenceIndex.fromChunks' in local and 'sentenceIndex.previous' in local,
 'edge sentence navigation': 'setSentenceNavigation' in stream and 'sentenceIndex.previous' in stream,
 'reader inline heading': 'toolbarBookName.setText(title+(name.isEmpty()?' in reader,
 'comic inline heading': 'comic.title+(name.isEmpty()?' in comic,
 'comic restore without 550ms delay': 'restoreWhenReady(' in comic and '550' not in comic,
 'comic position hidden during restore': 'web.setAlpha(0f)' in comic and 'onPositionRestored' in comic,
 'comic orientation': 'SCREEN_ORIENTATION_LANDSCAPE' in comic and 'showComicOrientation' in comic,
 'comic mode top sheet': 'sh.fromTop=true;' in read('UiNavigationSheet.java'),
 'TTS minutes short unit': '" м"' in reader,
 'comic portrait icon': (root/'app/src/main/res/drawable/ic_comic_portrait.xml').is_file(),
 'comic landscape icon': (root/'app/src/main/res/drawable/ic_comic_landscape.xml').is_file(),
}
failed=[]
for name,ok in checks.items():
 print(('PASS' if ok else 'FAIL')+': '+name)
 if not ok:failed.append(name)
java_files=list((root/'app/src/main/java').rglob('*.java'))
ref_names=set()
for java in java_files:
 ref_names.update(re.findall(r'(?<![\w.])R\.drawable\.([a-zA-Z]\w*)',java.read_text(errors='ignore')))
resources=root/'app/src/main/res'
resource_names={p.stem for d in resources.iterdir() if d.is_dir() and d.name.startswith('drawable') for p in d.iterdir() if p.is_file()}
missing=sorted(ref_names-resource_names)
if missing: raise AssertionError('Missing drawable resources: '+', '.join(missing))
print(f'PASS: {len(ref_names)} app drawable references resolve')
xml=list((root/'app/src/main/res').rglob('*.xml'))
for f in xml: ET.parse(f)
print(f'PASS: {len(xml)} Android resource XML files parse')
if failed:raise SystemExit('FAILED: '+', '.join(failed))
print('PASS: all',len(checks),'release source contracts')
