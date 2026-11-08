#!/usr/bin/env python3
"""v1.11.134 audio settings narrow viewport regression (static source contract)."""
from pathlib import Path
src=(Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp/SettingsActivity.java').read_text(encoding='utf-8')
block=src.split('private TextView settingValue(String value){',1)[1].split('private void audioToggle(',1)[0]
assert 'label.setSingleLine(true)' in block
assert 'label.setMaxWidth(dp(108))' in block
assert 'label.setEllipsize(android.text.TextUtils.TruncateAt.END)' in block
assert 'selection.length()>18' in block
assert 'String details=(info==null||info.isEmpty())?selection:info+"\\n"+selection;' in block
assert 'audioCard(icon,title,details,chevron,action)' in block
assert 'TextView chevron=settingValue("›")' in block
assert 'audioCard(icon,title,info,tail,action)' in block
assert 'audioLink(R.drawable.ic_audio_timer,"Продолжение TTS после паузы",\n            "",TtsResumeChoice.summary(this),this::ttsResumeChoiceDialog)' in src
assert 'caption.setMaxLines(5)' in src
assert 'setSingleChoiceItems(options,selected' in src
print('PASS: long TTS resume summary no longer gets measured as trailing accessory')
print('PASS: other audio setting trailing values remain bounded and ellipsized')
print('PASS: both pause/resume choices and existing dialog preserved')
