from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1];s=r/'app/src/main/java/ru/reader/rsvp';xml=r/'app/src/main/res/drawable'
ns='{http://schemas.android.com/apk/res/android}'
em=(s/'ChteicoEmotions.java').read_text();main=(s/'MainActivity.java').read_text();full=(s/'ChteicoActivity.java').read_text()
assert 'ViewConfiguration.getLongPressTimeout()' in em
assert 'view.postDelayed' not in em and 'v.postDelayed(triggerLong,longMs)' in em
assert 'view.performHapticFeedback' in em and 'drag.start(view)' in em
assert em.index('view.performHapticFeedback')<em.index('drag.start(view)')
assert 'if(moved){if(pet!=null)pet.run();}' in em
assert 'else {v.performClick();if(tap!=null)tap.run();}' in em
assert 'vibrate(v)' not in em
assert 'new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)' in main
assert 'emotion.setTextSize(39)' in main and 'emotion.setTextSize(26)' in main
assert 'speechBubble.setTextSize(face?41:15)' in full
assert 'lp.width=face?-2:dp(235)' in full
assert 'setPadding(dp(5),dp(4),dp(5),dp(16))' in full
assert 'setPadding(dp(5),dp(4),dp(5),dp(16))' in (s/'ChteicoSpeechBubble.java').read_text()
for name in ['day','night','soft','sepia','custom']:
    base=ET.parse(xml/f'ic_reading_theme_{name}.xml').getroot()
    dark=ET.parse(xml/f'ic_reading_theme_{name}_dark.xml').getroot()
    assert len(list(base))==len(list(dark)) and len(list(base))>=1
    assert any(x.get(ns+'fillColor')!='#00000000' for x in base)
    assert any(x.get(ns+'fillColor')!='#00000000' for x in dark)
    assert '#FF151515' in [x.get(ns+'strokeColor') for x in base]
    assert '#FFFFFFFF' in [x.get(ns+'strokeColor') for x in dark]
    assert all(x.get(ns+'strokeColor') in ('#FF151515','#FFFFFFFF') for x in list(base)+list(dark) if x.get(ns+'strokeColor'))
assert 'isDark(p.getInt("bg"' in (s/'ReadingThemeCycle.java').read_text()
service=(s/'EdgeStreamingService.java').read_text()
assert 'prefetchForStart!=current.start' in service
assert 'prefetchIssued>=EdgeSpeechQueue.MAX_PREFETCH' in service
assert 'pending++;prefetchIssued++' in service
assert 'activeLeases' in (s/'EdgeWebSocketPool.java').read_text()
assert 'buffering_duration_ms' in (s/'EdgePlaybackService.java').read_text()
assert 'versionName \'1.11.116-beta\'' in (r/'app/build.gradle').read_text()
print('PASS: new long-hold/tap/swipe separation; haptic only drag; wrap bubbles, white-black adaptive icon outlines, bounded prefetch and diagnostic instrumentation')
