#!/usr/bin/env python3
"""Static integration contracts; Android device behaviour still needs runtime QA."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/name).read_text(encoding='utf-8')
reader=read('ReaderActivity.java')
controller=read('ReaderTtsScrollController.java')
policy=read('ReaderTtsScrollPolicy.java')
pet=read('ChteicoActivity.java')
size=read('PetSpeechBubbleSizing.java')
assert "versionCode 18900; versionName '1.11.189-beta'" in (root/'app/build.gradle').read_text()
assert 'new ReaderTtsScrollController(textList,layoutManager' in reader
assert 'ttsWordLocation(int word)' in reader
assert 'textList.offsetDescendantRectToMyCoords(value,bounds)' in reader
assert 'ttsScrollController.follow(index,explicitReturn)' in reader
assert 'ttsScrollController.cancel("MANUAL_SCROLL")' in reader
assert 'ttsScrollController.cancel("SELECTION")' in reader
assert 'ttsScrollController.cancel("TOC_BROWSE")' in reader
assert 'ttsScrollController.cancel("ACTIVITY_PAUSED")' in reader
assert 'epoch==viewportNavigationEpoch&&!followSuspended' in reader
assert 'ttsScrollController.cancel("EXPLICIT_RETURN")' in reader
assert '||words.length==0||isActiveWordVisible())' in reader
assert 'if(mode==TTS){\n            if(ttsScrollController' in reader
assert 'FOLLOW_START' in controller and 'FOLLOW_FINISHED' in controller
assert 'FOLLOW_CANCELLED' in controller and 'FOLLOW_SKIPPED' in controller
assert 'FOLLOW_TARGET' in controller and 'RETURN_TO_SPEECH' in controller
assert 'STALE_NAVIGATION' in controller
assert 'postOnAnimation(frame)' in controller
assert 'scrollBy(0,step)' in controller
assert 'Math.round(usable*.69f)' in policy
assert 'setEllipsize(TextUtils.TruncateAt.END)' in pet
assert 'FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)' in pet
assert 'PetSpeechBubbleSizing.maxWidth' in pet and 'PetSpeechBubbleSizing.quoteLines' in pet
assert 'SpeechQuoteScroll' not in pet and 'speechScroll' not in pet
assert 'quote?425' not in pet and 'quote?dp(275)' not in pet
assert 'FrameLayout petFrame=new FrameLayout(this);petSpeechFrame=petFrame;' in pet
assert 'hero.addView(petFrame,new LinearLayout.LayoutParams(-1,dp(260)))' in pet
assert 'setMaxHeight' in pet and 'setMaxWidth' in pet
assert 'maxHeight' in size
assert 'PetQuoteChoice.chooseQuote(phraseRandom,store.hasPetQuotes())' in pet
assert 'ttsScrollController.dispose()' in reader
for path in (root/'app/src/main').rglob('*.xml'):
    ET.parse(path)
md=list(root.glob('*.md'))
assert len(md)==4 and all('1.11.189' in f.name for f in md),md
print('PASS: v189 independent word scrolling, safe cancellation, fixed pet frame, ellipsis, CLEAN docs')
