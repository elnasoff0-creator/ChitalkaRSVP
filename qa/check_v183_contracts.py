#!/usr/bin/env python3
"""Static integration assertions, complementing the real Android CI compilation."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
j=root/'app/src/main/java/ru/reader/rsvp'
get=lambda name:(j/name).read_text(encoding='utf-8')
gradle=(root/'app/build.gradle').read_text()
assert "versionCode 18300; versionName '1.11.183-beta'" in gradle
reader=get('ReaderActivity.java')
menu=get('ReaderSheets.java')
settings=get('SettingsActivity.java')
player=get('EdgePlaybackService.java')
edge=get('EdgeStreamingService.java')
live=get('EdgeLiveBuffer.java')
assert 'toolbarEngineBadge.setBackground(null);' in reader
assert 'toolbarEngineBadge.setPadding(0,0,0,0);' in reader
assert 'Путь к файлам Edge TTS' not in reader+menu
assert 'EdgeFolderPicker.show(this)' not in reader
assert 'mode==TTS?3:2' in reader and 'mode==TTS?4:3' in reader and 'mode==TTS?5:4' in reader
assert menu.count('"Загрузка озвучки"')==2
assert menu.count('"Словарь TTS"')==2
# These are the two label/icon sets of the TTS/non-TTS overflow, not UI paraphrases.
arrays=re.findall(r'new String\[\]\{([^}]+)\}',menu)
icons=re.findall(r'new int\[\]\{([^}]+)\}',menu)
assert len(arrays)==2 and len(icons)==2
for a,b in zip(arrays,icons):
    assert a.count('"')//2==len([x for x in b.split(',') if x.strip()]),'menu icon count mismatch'
assert 'list.addView(help,lp)' not in settings
assert 'addHelpIcon(' not in settings
assert 'row.addView(help,new LinearLayout.LayoutParams(dp(48),dp(48)))' in settings
for title in ('Редактирование меню','Подсветка текста (Highlight)',
              'Выделения и заметки в книге','Задержка перед стартом',
              'Выбор слова в обычном режиме','Выделение режима «Курсор»',
              'Размер порции','Точка фокусировки','Линии фокусировки'):
    assert ('headerWithHelp("'+title+'"') in settings,title
assert 'default void onTrackPlaybackError' in player
assert 'resumeEndedAt(int expectedStart)' in player
assert 'NEXT_CLIP_STARTED' in player
assert 'recoverEndedTransition();' in edge
assert 'advanceAfterTrack(start,end,key,"media3")' in edge
assert 'advanceAfterTrack(start,end,player.currentKey(),"watchdog")' in edge
assert 'player.hasTrack(current.key+":"+current.start)' in edge
assert 'preloaded.buffer.available()>0' in edge
assert 'TRANSITION_STALLED' in edge and 'TRANSITION_RECOVERED' in edge
assert 'SAF_RESTORE_WAIT_MS=9000L' in edge
assert 'if(!liveJobs.remove(job.fileId,job))return;' in edge
assert 'DEFAULT_STALL_TIMEOUT_MS = 45000L' in live
assert 'wait(Math.min(1000L,remainingMs))' in live
for xml in (root/'app/src/main').rglob('*.xml'):ET.parse(xml)
md=list(root.glob('*.md'))
assert md and all('1.11.183' in p.name for p in md),[p.name for p in md]
print('PASS: v183 UI, menu indexing, Edge transition/lazy SAF, cache, XML, CLEAN docs')
