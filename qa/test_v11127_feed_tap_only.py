#!/usr/bin/env python3
"""v1.11.127 feed chrome must not react to scroll; Android device testing still required."""
from pathlib import Path
src=(Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp/ComicReaderActivity.java').read_text(encoding='utf-8')
assert 'setOnScrollChangeListener' not in src, 'scroll callback may hide/show panels'
assert 'feedScrollAccum' not in src, 'old scroll-direction state left behind'
assert 'lastReaderTouchAt' not in src, 'unused scroll restoration timestamp'
assert 'setChromeVisible(!chromeVisible)' in src, 'tap control missing'
assert 'if(comic!=null&&!gestureMoved&&!touchPinching&&' in src, 'tap must reject swipe/pinch'
assert 'event.getPointerCount()>1' in src and 'ACTION_POINTER_DOWN' in src
assert 'return false;' in src, 'WebView scroll and pinch must remain native'
assert 'scroll-snap-type:x mandatory' in src, 'page-mode swipes must remain'
assert 'UiNavigationSheet.showComicChapters' in src, 'bottom menu must remain'
print('PASS v1.11.127 feed: scroll never toggles chrome; taps/swipes/pinch preserved')
