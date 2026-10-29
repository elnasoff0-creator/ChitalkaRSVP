#!/usr/bin/env python3
"""Source-level checks for same-version 1.11.117 UI corrections.
These do not replace Android instrumentation/device checks.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
j=root/'app/src/main/java/ru/reader/rsvp'
nav=(j/'NavigationActivity.java').read_text()
drawer=(j/'UniversalDrawer.java').read_text()
reader=(j/'ReaderActivity.java').read_text()
main=(j/'MainActivity.java').read_text()
pet=(j/'ChteicoPetView.java').read_text()
dictionary=(j/'EdgeDictionaryActivity.java').read_text()
assert 'dispatchTouchEvent(' not in nav and 'navDownX' not in nav
assert 'closeIfOpen(this)' in nav
assert 'dx<-dp(getActivity(),65)' in drawer
assert 'isActiveSentenceVisible()' in reader
assert '||isActiveSentenceVisible()' in reader
assert 'SentenceChunks.forWord(ttsParts,map.clamp(index))' in reader
assert 'if(followSuspended&&mode==TTS)updateFloatingCaption();' in reader
assert 'topLimit=dp(88),bottomLimit=textList.getHeight()-dp(96)' in reader
assert 'bookDetails.setVisibility(opening?View.VISIBLE:View.GONE)' in main
assert 'audioDetails.setVisibility(opening?View.VISIBLE:View.GONE)' in main
assert 'emptyAudio.setVisibility(opening?View.VISIBLE:View.GONE)' in main
for prefix in ['"book_library_expanded"','"audio_library_expanded"']:
    assert 'apply();draw();' not in main[main.find(prefix)-130:main.find(prefix)+170]
assert 'FRAME_MS=630L' in pet and 'setDuration(FRAME_MS-62L)' in pet
assert 'ValueAnimator.ofFloat(0f,1f)' not in pet
assert 'spring();' in pet and 'frameDown=!frameDown;' in pet
assert 'dictionarySelectedWord' not in reader and 'selectTtsWord(tapped)' in reader
assert 'EdgeDictionaryActivity.EXTRA_SENTENCE' in reader and 'chooseWordFromSentence(original)' in dictionary
assert 'activeSentence=getIntent().getStringExtra(EXTRA_SENTENCE)' in dictionary
ET.parse(root/'app/src/main/res/drawable/ic_audiotheque.xml')
xml=(root/'app/src/main/res/drawable/ic_audiotheque.xml').read_text()
assert 'M4.5,12 L4.5,9.5' in xml and 'M6.5,10.8' in xml
assert "versionCode 11800; versionName '1.11.118-beta'" in (root/'app/build.gradle').read_text()
print('PASS: no swipe-open, sentence-aware overlay, stable lists, restored pet animation, dictionary sentence picker, audiotheque vector, unchanged version')
