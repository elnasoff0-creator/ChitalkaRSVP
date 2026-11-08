#!/usr/bin/env python3
"""Checks source contracts for 1.11.118 same-version TTS performance/sleep fix.
Not an Android device or integration test.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
j=root/'app/src/main/java/ru/reader/rsvp'
def rd(x): return (j/x).read_text()
reader=rd('ReaderActivity.java'); audio=rd('AudioPlaybackService.java')
player=rd('AudioPlayerActivity.java'); dictact=rd('EdgeDictionaryActivity.java')
local=rd('LocalTtsWordTimings.java'); edge=rd('EdgePlaybackService.java')
checks={
    'dictionary selection removed': 'dictionarySelectedWord' not in reader and 'drawDictionarySelection' not in reader,
    'dialog picks word from current sentence': 'EXTRA_SENTENCE' in reader and 'activeSentence' in dictact and 'chooseWordFromSentence' in dictact,
    'playback position still selects sentence': 'selectTtsWord(tapped)' in reader and 'sentence.fromWord' in reader,
    'play-only word polling': 'ttsCurrentlyPlaying()?180L' in reader and 'ttsTickQueued' in reader and 'cancelTtsUi()' in reader,
    'local media events wake word tracking': 'AudioPlaybackService.ACTION_STATE' in reader and 'localStateReceiver' in reader,
    'word marks cached by chunk and sidecar metadata': 'cachedLocalTimingPosition' in audio and 'cachedLocalTimingModified' in audio and 'localTimingMarks(ttsPosition)' in audio,
    'timing binary search': 'int mid=(left+right)>>>1' in local,
    'reader sleep minutes badge': 'toolbarSleepValue' in reader and 'sleepChapterRemaining()' in reader and 'sleepDeadlineRemaining()' in reader,
    'chapter sleep from current playback': 'chapterEndAfter(currentPlaybackWord(),count)' in reader,
    'audio sleep chapter remaining': 'getChapterSleepRemainingMillis(service.sleepChaptersRemaining())' in player,
    'audio playback speed considered': 'remaining/Math.max(.1f,service.getSpeed())' in player,
    'edge chapter end observed': 'sleepChapterEndWord()' in edge,
    'startup stages measured': all(x in reader for x in ('READER_STARTUP','book_prepare_ms=','first_ui_ms=','loaded_ui_ms=')),
}
for name,ok in checks.items():print(('PASS' if ok else 'FAIL'),name)
assert all(checks.values())
print('PASS: 13 source-level checks; Android/runtime tests pending')
