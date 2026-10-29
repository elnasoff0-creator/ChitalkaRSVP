from pathlib import Path
import re
r=Path(__file__).resolve().parents[1]
s=r/'app/src/main/java/ru/reader/rsvp'
main=(s/'MainActivity.java').read_text();full=(s/'ChteicoActivity.java').read_text();em=(s/'ChteicoEmotions.java').read_text()
pet=(s/'ChteicoPetView.java').read_text();bubble=(s/'ChteicoSpeechBubble.java').read_text()
q=(s/'EdgeSpeechQueue.java').read_text();pool=(s/'EdgeWebSocketPool.java').read_text();play=(s/'EdgePlaybackService.java').read_text()
assert '.replace("( ◡‿◡ ) ","")' not in main
assert 'ChteicoEmotions.sleep(phase++)' in main and 'ChteicoEmotions.sleep(0)' in main
assert 'miniSleepAppearance.accept(true)' in main and 'miniSleepAppearance.accept(false)' in main
assert 'sleeping?0xff1d2531:panelColor()' in main
assert 'bubble.setNightAppearance(sleeping)' in main
assert 'pet.setForceNightWhenSleeping(true)' in main
assert 'forceNightWhenSleeping && sleeping' in pet
assert 'nightAppearance?0xff27313f' in bubble
assert 'nightAppearance?0xffffffff:foreground' in bubble
assert 'emotion.setTextSize(39)' in main
assert 'speechBubble.setTextSize(face?41:15)' in full
assert 'FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)' in main
assert 'setPadding(dp(5),dp(4),dp(5),dp(16))' in bubble
assert full.count('petTapTarget.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)')==1
assert 'view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)' in em
assert 'canRunBackground' in q and 'EdgeWebSocketPool.hasIdleTransport()' in q and 'interactivePlayback' in q
assert 'static synchronized boolean hasIdleTransport()' in pool
assert 'auto_transition_resume_callback_ms' in play and 'auto_transition_to_playing_ms' not in play
assert 'autoTransitionIndex==player.getCurrentMediaItemIndex()' in play
assert 'sample_gap_ms=unavailable' in play
assert 'play_to_is_playing_callback_ms' in play and 'first_sound_after_play_ms' not in play
assert 'track_callback_gap_ms' in (s/'EdgeTtsDiagnostics.java').read_text()
assert 'versionCode 11600' in (r/'app/build.gradle').read_text()
assert "versionName '1.11.116-beta'" in (r/'app/build.gradle').read_text()
# Approved vector colors and transparent backgrounds must survive the release.
import xml.etree.ElementTree as ET
xml=r/'app/src/main/res/drawable';ns='{http://schemas.android.com/apk/res/android}'
for theme in ('day','night','soft','sepia','custom'):
    for suffix,edge in (('', '#FF151515'),('_dark','#FFFFFFFF')):
        icon=ET.parse(xml/f'ic_reading_theme_{theme}{suffix}.xml').getroot()
        assert icon.get(ns+'width')=='24dp' and icon.get(ns+'height')=='24dp'
        paths=icon.findall('path')
        assert paths and any(x.get(ns+'fillColor')!='#00000000' for x in paths)
        assert any(x.get(ns+'strokeColor')==edge for x in paths)
print('PASS regression to v1.11.115: sleep face, isolated night mini pet, 39sp emotions, tap and emotion full-UI haptics, compact bubble, bounded prefetch, callback-only diagnostics, XML colors, version')
