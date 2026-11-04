#!/usr/bin/env python3
"""Source and Android XML contract tests for v1.11.131. Does not execute Android UI."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
src=root/'app/src/main/java/ru/reader/rsvp'
res=root/'app/src/main/res'
source=lambda n:(src/n).read_text(encoding='utf8')
main=source('MainActivity.java');reader=source('ReaderActivity.java')
activity=source('ChteicoActivity.java');game=source('ChteicoMiniGameView.java')
store=source('ChteicoStore.java');pet=source('ChteicoPetView.java')
progress=source('UnifiedReadingProgress.java');local=source('AudioPlaybackService.java')
edge=source('EdgeStreamingService.java')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
checks={
  'v1.11.131 / 13100':"versionCode 13100; versionName '1.11.131-beta'" in (root/'app/build.gradle').read_text(),
  'Accent plus in MainActivity':'importBtn.setBackground(shape(addAccent,16))' in main and 'ReaderButtonColors.contrastingText(addAccent)' in main,
  'Shared position persisted':'book_shared_word:' in progress and 'position:' in progress and 'tts_word:' in progress,
  'Book progress independent of time':'getInt("book_words:' in main and 'current*100/total' in main,
  'Shared library updated':'book.put("index",current)' in progress and 'book.put("chapter",chapter)' in progress,
  'Reader loads shared position':'UnifiedReadingProgress.read(prefs,docUri' in reader,
  'Mode switching keeps shared position':'index=map.clamp(UnifiedReadingProgress.read' in reader,
  'Reader persists shared position':'UnifiedReadingProgress.save(this,docUri,index,words.length' in reader,
  'TTS saves accurate spoken word':'currentSpokenTtsWord(),ttsWordCount' in local,
  'Local TTS updates from background tick':'UnifiedReadingProgress.save(AudioPlaybackService.this,currentBook' in local,
  'Edge TTS background current word':'UnifiedReadingProgress.save(EdgeStreamingService.this,bookUri' in edge and 'currentSpeechWord()' in edge,
  'Pet game selection':'showGameChoices(petFrame)' in activity and 'Бегун' in activity and 'Летун' in activity,
  'Egg barred from both games':'stage<ChteicoStore.CHICK' in activity and 'stage<ChteicoStore.READER' in activity,
  'Game within pet frame':'frame.addView(mini,new FrameLayout.LayoutParams(-1,-1))' in activity,
  'Animated frame height':'ValueAnimator.ofInt(from,to)' in activity,
  'No standalone runner Activity':'ChteicoRunnerActivity' not in manifest and not (src/'ChteicoRunnerActivity.java').exists(),
  'Game Canvas on View':'class ChteicoMiniGameView extends View' in game and 'void onDraw(Canvas c)' in game,
  'Two vector book obstacles':'R.drawable.ic_game_book_open' in game and 'R.drawable.ic_game_book_upright' in game,
  'Standing obstacle stretches above/below':'b.gapTop+dp(15)' in game and 'h-b.gapBottom+dp(10)' in game,
  'Existing pet vectors reused':'ChteicoPetView.gameFrameResource' in game and 'FRAMES[s][b][up?0:1]' in pet,
  'Jump uses up sprite':'(airborne?1:frame)' in game,
  'Flyer flap impulse':'velocity=-dp(230)' in game,
  'Runner stats kept':'runner_best' in store and 'addGameExperience' in store,
  'Flyer statistics separate':'flyer_best' in store and 'flyer_games' in store,
  'UI restores on exit':'closeMiniGame()' in activity and 'show();' in activity,
  'Pause on activity background':'activeMiniGame.pauseClock()' in activity,
  'No unused game png':not list(res.rglob('chteico_game_*.png')) and not list(res.rglob('chteico_book_*.png')),
  'No game PNG decode':'BitmapFactory.decodeResource' not in game,
  'Manifest no obsolete Activity':'ChteicoRunnerActivity' not in manifest,
  'New XML resources exist':(res/'drawable/ic_game_book_open.xml').exists() and (res/'drawable/ic_game_book_upright.xml').exists(),
}
failed=[]
for name,ok in checks.items():
 print(('PASS' if ok else 'FAIL')+': '+name)
 if not ok:failed.append(name)
xml=list(res.rglob('*.xml'))
for item in xml:
 try:ET.parse(item)
 except Exception as e:failed.append('XML '+str(item)+': '+str(e))
print(f'XML parsed: {len(xml)}')
resources={f.stem for folder in res.iterdir() if folder.is_dir() and folder.name.startswith('drawable') for f in folder.iterdir() if f.is_file()}
java=list((root/'app/src/main/java').rglob('*.java'))
refs=set()
for item in java:refs.update(re.findall(r'(?<![\w.])R\.drawable\.([A-Za-z]\w*)',item.read_text(errors='replace')))
missing=refs-resources
if missing:failed.append('Missing drawable references '+str(missing))
print(f'Java: {len(java)}; Drawable refs: {len(refs)}; missing: {len(missing)}')
print(f'VERSION 131 GATE: {len(checks)-len(failed)}/{len(checks)} contract checks PASS' if not failed else 'FAIL: '+repr(failed))
if failed:raise SystemExit(1)
