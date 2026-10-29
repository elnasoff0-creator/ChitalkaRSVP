#!/usr/bin/env python3
"""Regression contract for v1.11.120: lazy Edge TTS and warm session reuse."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
s=(src/'EdgeStreamingService.java').read_text()
r=(src/'ReaderActivity.java').read_text()
u=(src/'UnifiedBookSession.java').read_text()
start=s[s.index('    private void restore(){'):s.index('    private void persist(){')]
checks={
 'version':"versionCode 12000; versionName '1.11.120-beta'" in (root/'app/build.gradle').read_text(),
 'restore uses structural keys only':'new Clip(part.fromWord,part.toWord,part.text,"")' in start,
 'restore never reads dictionary':'EdgeSpeechTextTransform' not in start and 'Options.read' not in start,
 'restore never stats every MP3':'.isFile()' not in start and 'getSharedPreferences' not in start,
 'restore has timings':'edge_restore_start' in start and 'edge_restore_end_ms' in start,
 'lazy per-fragment speech':'private void prepareClip(Clip clip)' in s and 'speechOptions.apply(clip.text)' in s,
 'same options reuse speech result':'if(revision.equals(clip.revision))' in s,
 'bounded alignment map cache':'return size()>20;' in s,
 'prefetch is bounded':'EdgePrefetchPlanner.select(' in s and 'if(!shouldIgnore(candidate))' in s,
 'spoken text matches cache identity':'job.speech=speechFor(clip);' in s and 'job.speech.spoken' in s,
 'legacy MP3s retained by background manifest merge':'Preserve previously downloaded MP3 index rows' in s and 'io.execute(()->{' in s,
 'coverage independent of dictionary':'EdgeAudioManager.coverage(folder,0,total)' in s and '.apply(part.text)' not in s[s.index('public int savedPercent()'):s.index('public int currentOrdinal()')],
 'idle service reused':'main.postDelayed(idleExpiry,IDLE_REUSE_MS)' in s and 'IDLE_REUSE_MS=120000L' in s,
 'active listener does not stop service':'changed();main.postDelayed(idleExpiry,IDLE_REUSE_MS);' in s,
 'same book idempotent':'edge_book_reuse ready=' in s,
 'book speech groups reused':'session.edgeGroups()' in s and 'if(edgeGroups==null)' in u,
 'mode selection measured':'tts_mode_selected' in r and 'tts_ui_ready_ms' in r,
 'first fragment measured':'first_track_ready source=' in s,
 'word boundary preserved':'job.speech.aligned(metadata)' in s,
 'skip ignored before sending':'skipIgnored(clipAt(anchorWord))' in s,
 'same prior sleep architecture':'PlaybackSleepCountdown.TTS_CHAPTER_KEY' in r and 'ttsTickQueued' in r,
}
for name,ok in checks.items():print(('PASS' if ok else 'FAIL')+' '+name)
assert all(checks.values())
