#!/usr/bin/env python3
"""Source contracts for ComicReaderActivity gesture/chrome update (Android device QA still required)."""
from pathlib import Path
s=(Path(__file__).resolve().parents[1]/'app/src/main/java/ru/reader/rsvp/ComicReaderActivity.java').read_text()
checks={
    'overlay root preserves full WebView viewport':'FrameLayout main=new FrameLayout(this)' in s and 'main.addView(web,new FrameLayout.LayoutParams(-1,-1))' in s,
    'both bars overlay and animate':'main.addView(toolbarOverlay,topParams)' in s and 'main.addView(footerOverlay,bottomParams)' in s and 'animateChrome(toolbarOverlay,show' in s and 'animateChrome(footerOverlay,show' in s,
    'short tap toggles chrome only in page mode':'comic.mode==0&&!gestureMoved&&!touchPinching' in s and 'setChromeVisible(!chromeVisible)' in s,
    'swipe still routed to WebView':'return false;' in s and 'scroll-snap-type:x mandatory' in s,
    'old page click navigation removed':'document.body.onclick' not in s,
    'vertical scroll controls chrome':'setOnScrollChangeListener' in s and 'setChromeVisible(feedScrollAccum<0)' in s,
    'feed pinch and scroll restore excluded':'||touchPinching' in s and 'uptimeMillis()-lastReaderTouchAt>900L' in s,
    'native pinch zoom enabled':'setSupportZoom(true)' in s and 'setBuiltInZoomControls(true)' in s and 'setDisplayZoomControls(false)' in s,
    'viewport allows zoom':'maximum-scale=5, user-scalable=yes' in s,
    'two fingers never act as tap':'ACTION_POINTER_DOWN' in s and 'event.getPointerCount()>1' in s,
    'close sheet and mode picker preserved':'UiNavigationSheet.showComicModes' in s and 'UiNavigationSheet.showComicChapters' in s,
}
for name,ok in checks.items():
    if not ok:raise AssertionError(name)
print('PASS 126 gesture/zoom: '+', '.join(checks))
