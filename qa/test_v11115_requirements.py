#!/usr/bin/env python3
"""Structural regression checks for v1.11.115 (runtime Android QA still required)."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=(src/'ReaderActivity.java').read_text()
mini=(src/'MainActivity.java').read_text()
full=(src/'ChteicoActivity.java').read_text()
stream=(src/'EdgeStreamingService.java').read_text()
waves=(src/'EmotionTapWaves.java').read_text()
checks={
    '1. Center current word/sentence': 'viewportCenter-relativeLineCenter' in read and 'centerActiveVisibleLine' in read,
    '2. Smooth near/far reposition': 'textList.smoothScrollBy(0,delta)' in read and 'layoutManager.scrollToPositionWithOffset(chunk+1,viewportCenter-relativeLineCenter)' in read,
    '3. No toolbar upper gap': 'toolbarOverlay.setMargins(dp(8),0,dp(8),0)' in read and 'toolbarStack.setPadding(dp(5),0,' in read,
    '4. Persist selection while book loads': 'deferredSelectedWord' in stream and 'setAnchor(selected,true)' in stream,
    '5. Mini +30%': 'emotion.setTextSize(39)' in mini and 'emotion.setTextSize(26)' in mini,
    '6. Full UI +50%': 'speechBubble.setTextSize(face?41:15)' in full and 'speechBubble.setTextSize(29)' in full,
    '7. Overlay in ReaderActivity': 'sceneRoot.addView(ttsFloatingCaption,captionLp)' in read,
    '8. WordBoundary progressive text': 'body.substring(first,Math.max(first,through))' in read,
    '9. Overlay tap returns': 'ttsFloatingCaption.setOnClickListener(v->returnToActiveWord())' in read,
    '10. Cursor pause when hidden': 'mode==CURSOR&&playing&&!isActiveWordVisible()' in read,
    '11. No timed automatic return': 'lastTtsVisualUpdate>900' not in read and 'followSuspended' in read,
    '12. Only explicit return': 'if(!explicitReturn&&followSuspended)return;' in read and 'if(!ttsCurrentlyPlaying())returnToActiveWord()' in read,
    '13. Fast far/smooth near': 'Math.abs(delta)>textList.getHeight()*2' in read and 'textList.smoothScrollBy(0,delta)' in read,
    '14. RSVP highlight stays': 'new BackgroundColorSpan(0x66ffc94e)' in read and 'refreshHighlight(index); // The current word' in read,
    '15. Recenter leaving RSVP': 'updateModeUI();refreshHighlight(old);returnToActiveWord();saveLight(true);' in read,
    '16. Emotion waves and haptics': 'emotionRays.burst()' in mini and 'petArea.performHapticFeedback' in mini and 'emotionTapWaves.burst()' in full and 'pet.performHapticFeedback' in full and 'ValueAnimator.ofFloat' in waves,
}
for title,ok in checks.items():
    print(('PASS' if ok else 'FAIL')+' '+title)
assert all(checks.values()),'Some v1.11.115 requirements are missing'
assert 'versionCode 11900' in (root/'app/build.gradle').read_text()
assert "versionName '1.11.119-beta'" in (root/'app/build.gradle').read_text()
print('PASS 16/16 static requirement checks, versionName/versionCode')
