from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/ru/reader/rsvp'
r=(p/'ReaderActivity.java').read_text()
a=(p/'AudioPlaybackService.java').read_text()
e=(p/'EdgePlaybackService.java').read_text()
s=(p/'SleepTimerSheet.java').read_text()
c=(p/'PlaybackSleepCountdown.java').read_text()
checks={
 'correct version':"versionCode 12100; versionName '1.11.121-beta'" in (root/'app/build.gradle').read_text(),
 'urgent UI callback preempts 15 second timer':'handler.removeCallbacks(ttsUiTick);' in r and 'if(ttsTickDueAt<=due)return' in r,
 'track and media callbacks wake UI':'ttsPlayerListener' in r and 'scheduleTtsUi()' in r and 'localStateReceiver' in r,
 'TOC retries on actual animation frames':'postOnAnimation(()->alignTocPreview(' in r and 'if(attempt<12)' in r,
 'TOC keeps playing position':'index=' not in r[r.index('private void browseToWord(int word)'):r.index('private void showToc()')],
 'async word-boundary reads':'timingReader.execute(' in r and 'EdgeWordTimings.read(file,spoken)' in r,
 'sidecar when newly available':'cachedTimingSidecarModified' in r and 'sideLength!=cachedTimingSidecarLength' in r,
 'partial metadata keeps live words':'ttsStreamer.liveWords(clip)' in r,
 'first MP3 caption fallback':'missingWordTiming' in r and 'part.toChar' in r,
 'playback verified before pending word release':'activeClip.start>=target.fromWord' in r,
 'two distinct clocks': 'TTS_KEY="tts_sleep_remaining_ms"' in c and 'AUDIOBOOK_KEY="audiobook_sleep_remaining_ms"' in c,
 'audio service domain switches':'ttsMode?ttsSleepClock:audiobookSleepClock' in a,
 'edge TTS only clock':'new PlaybackSleepCountdown(this,PlaybackSleepCountdown.TTS_KEY)' in e,
 'no wall clock deadline timer':'audio_sleep_deadline' not in a+e+r,
 'chapter mode not shared with audiobook':'ttsDomain' in s and 'chapterActive=ttsDomain' in s,
 'TTS chapter end canonical':'PlaybackSleepCountdown.TTS_CHAPTER_KEY' in a+e+r,
 'pause freezes countdown':'clock.freeze()' in a and 'ttsSleepClock.freeze()' in e,
 'no periodic media notification rebuild':'notifyState();handler.postDelayed(this,1000)' not in a,
}
for k,v in checks.items():print(('PASS' if v else 'FAIL'),k)
assert all(checks.values())
