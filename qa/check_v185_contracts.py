#!/usr/bin/env python3
"""Static source-level checks; cannot prove gesture quality on an Android device."""
from pathlib import Path
from xml.etree import ElementTree
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/name).read_text(encoding='utf-8')
reader=read('ReaderActivity.java')
streamer=read('EdgeStreamingService.java')
version=(root/'app/build.gradle').read_text()
assert "versionCode 18500; versionName '1.11.185-beta'" in version
assert 'setTextIsSelectable(true)' in reader
assert 'setCustomSelectionActionModeCallback(nativeTextActions(holder))' in reader
assert 'new GestureDetector(' not in reader
assert 'item.setOnTouchListener(new View.OnTouchListener()' in reader
assert 'view.post(()->{' in reader
assert 'event.getEventTime()-downAt<ViewConfiguration.getLongPressTimeout()' in reader
assert 'if(nativeActionModeActive)return;' in reader
assert 'holder.boundChunk!=chunkIndex' in reader
assert 'applySelectionChars(' in reader
assert 'ReaderSelectionRange.Move moved=current.drag(' in reader
assert 'ReaderSpeechPositionGuard speechPosition' in reader
assert 'speechPosition.withoutTimings(ttsPlayer.currentKey(),clip.start)' in reader
assert 'int checked=speechPosition.accept(' in reader
assert 'syncExternalTtsSelection()' in reader
assert 'selectionSerial()' in reader
assert 'pendingTtsSelectedAt>8000L' not in reader
assert 'if(!explicitReturn&&mode==TTS&&isActiveSentenceVisible())return;' in reader
assert 'if(nativeActionModeActive||selectionDragging||selectionFrom>=0)return;' in reader
assert 'viewportNavigationEpoch++' in reader
assert 'completedNativeActionMode==mode' in reader
assert 'chapterForWord(index)' in reader
assert 'docUri.equals(s.getTtsBookUri())' in reader
assert 'public int chunkForCharacter(int position)' in read('BookChunkIndex.java')
assert 'selectionSerial++;lastSelectedWord=' in streamer
assert 'if(!key.equals(player.currentKey()))return;' in streamer
assert 'getLongPressTimeout' in reader and 'new GestureDetector(' not in reader
assert (root/'qa/TestReaderSelectionNavigation185.java').exists()
for f in (root/'app/src/main').rglob('*.xml'): ElementTree.parse(f)
md=list(root.glob('*.md'))
assert len(md)==4 and all('1.11.185' in f.name for f in md),md
print('PASS: v185 Android native selection isolation, book-wide range, TTS playback focus, external navigation, XML and CLEAN docs')
