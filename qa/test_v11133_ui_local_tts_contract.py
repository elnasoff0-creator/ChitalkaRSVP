#!/usr/bin/env python3
"""Source-level contract checks for v1.11.133 settings, title layout and local TTS fallback."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
src=r/'app/src/main/java/ru/reader/rsvp'
settings=(src/'SettingsActivity.java').read_text()
reader=(src/'ReaderActivity.java').read_text()
local=(src/'AudioPlaybackService.java').read_text()
edge=(src/'EdgeStreamingService.java').read_text()
playback=(src/'EdgePlaybackService.java').read_text()
assert 'new LinearLayout.LayoutParams(-1,dp(32))' in reader
assert 'titleLp.topMargin=dp(4)' in reader
assert 'toolbarBookName.setOnClickListener(v->showBookmarks())' in reader
assert 'toolbarBookName.setSingleLine(true)' in reader
assert 'this::ttsResumeChoiceDialog' in settings
assert 'setSingleChoiceItems(options,selected' in settings
assert 'TtsResumeChoice.set(this,which==0?' in settings
assert 'getBoolean("tts_resume_previous_sentence",true)' not in local
assert 'TtsResumeChoice.previous(this)' in local
assert 'TtsResumeChoice.previous(this)' in edge
assert 'resumeSelection.selectResumeSentence()' in playback
assert 'private void selectSentenceOnResume()' in edge
assert 'sentenceHistory.currentOnResume(spoken)' in edge
assert 'sentenceHistory.currentOnResume(spoken)' in local
assert 'requestedLocalChar=sentence==null?-1:sentence.fromChar' in local
assert 'fallbackLocalSpeech(index);return;' in local
assert 'remaining=remaining.substring(skip)' in local
assert 'mp.seekTo((int)Math.min' in local
assert 'if(requestedLocalChar>=base&&' in local
print('PASS reader toolbar compact, audio settings picker, both TTS engines and precise local fallback')
