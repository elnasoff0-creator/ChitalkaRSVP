#!/usr/bin/env python3
"""Static regression guards for TOC browsing while TTS is already playing.
Android visual/transport behavior still needs a device test.
"""
from pathlib import Path
src=Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp/ReaderActivity.java'
s=src.read_text(encoding='utf-8')
start=s.index('private void browseToWord(int word)')
end=s.index('private void setToolbarExpanded(',start)
part=s[start:end]
preview=part[:part.index('private void showToc()')]
toc=part[part.index('private void showToc()'):]
checks={
 'all modes browse with same method': 'browseToWord(word);' in toc,
 'browse does not change playing word': 'index=' not in preview,
 'browse does not pause speech': 'ttsPlayer.pause()' not in preview and 'ReaderTtsController.localSelect' not in preview,
 'browse never changes selected word': 'selectedWordActive=true' not in preview,
 'browse does not seek Edge': 'setAnchor(' not in preview,
 'suspend follow': 'followSuspended=true;' in preview,
 'viewport separate': 'viewportWord=target' in preview and 'viewport:' in preview,
 'jump to chapter': 'scrollToPositionWithOffset(chunk+1' in preview,
 'align inside block': 'map.starts[target]-chunks.get(chunk).startChar' in preview,
 'retry layout': 'postOnAnimation(()->alignTocPreview(' in preview,
 'TOC never stops player': 'stop();index=word;' not in toc,
 'caption respects viewport': 'updateFloatingCaption()' in preview,
 'manual selection commits': 'abandonViewportPreview()' in s,
 'version 121': "versionCode 12100" in (Path(__file__).resolve().parents[1]/'app/build.gradle').read_text(),
}
for name,ok in checks.items():print(('PASS' if ok else 'FAIL'),name)
assert all(checks.values())
