#!/usr/bin/env python3
"""Static regression guards for TOC browsing while TTS is already playing.
Android visual/transport behavior still needs a device test.
"""
from pathlib import Path
src=Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp/ReaderActivity.java'
s=src.read_text(encoding='utf-8')
start=s.index('private void previewTocDuringPlayback(int word)')
end=s.index('private void setToolbarExpanded(', start)
part=s[start:end]
preview=part[:part.index('private void showToc()')]
toc=part[part.index('private void showToc()'):]
checks={
    'active TTS branch': 'if(mode==TTS&&ttsCurrentlyPlaying())' in toc,
    'preview does not change playback word': 'index=' not in preview and 'index++' not in preview,
    'preview does not pause speech': 'stop()' not in preview and 'pause()' not in preview,
    'preview does not seek TTS': 'setAnchor(' not in preview and 'localSelect(' not in preview,
    'suspend follow after navigating': 'followSuspended=true;' in preview,
    'jump to selected chapter': 'layoutManager.scrollToPositionWithOffset(chunk+1' in preview,
    'scroll within containing chunk to chapter line': 'map.starts[target]-chunks.get(chunk).startChar' in preview and 'textList.scrollBy(0,delta)' in preview,
    'caption updated after new layout': 'generation==tocPreviewGeneration)updateFloatingCaption();' in preview,
    'active branch returns without running pause-navigation': 'previewTocDuringPlayback(word);\n                return;' in toc,
    'paused navigation still works': 'int previous=index;stop();index=word;' in toc,
    'normal TTS progress respects suspended follow': 'if(!followSuspended)ensureWordVisible(false)' in s,
    'caption checks spoken sentence visibility': 'isActiveSentenceVisible()' in s[s.index('private void updateFloatingCaption()'):s.index('private void returnToActiveWord()')],
    'version unchanged': '11800' in (src.parents[3]/'build.gradle').read_text() if (src.parents[3]/'build.gradle').exists() else True,
}
for name,ok in checks.items():
    print(('PASS' if ok else 'FAIL'),name)
if not all(checks.values()):raise SystemExit(1)
