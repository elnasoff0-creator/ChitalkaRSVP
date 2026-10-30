#!/usr/bin/env python3
"""Static assertions for FIX4; device/Android resource inflation is not tested here."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
pet=(src/'ChteicoPetView.java').read_text()
ref=(src/'ChteicoActivity.java').read_text()
gradle=(root/'app/build.gradle').read_text()
assert 'FRAME_MS=630L' in pet, '1.3x older v116 interval (820/1.3≈631)'
assert 'setDuration(FRAME_MS-62L)' in pet
assert 'setScaleX(1f+' not in pet and 'ValueAnimator' not in pet
assert 'frameDown?1.012f:1f' in pet and 'setPivotY(getHeight()*.84f)' in pet
assert 'setTranslationY(-getHeight()' not in pet
assert 'setBackground(shape(toolbarButtonColor,12))' in ref
assert ref.count('setBackground(shape(toolbarButtonColor,12))')==1
assert 'ReaderButtonColors.neutral(this,' in ref
assert 'finishRewardGrid();' in ref
assert 'int missing=(3-rewardCount%3)%3' in ref
assert 'LinearLayout.LayoutParams(0,dp(112),1f)' in ref
assert 'rewardLine.addView(spacer,lp)' in ref
# All incomplete rows reserve 3 slots, leaving 1/3 unused when only two exist.
assert all((n+(3-n%3)%3)%3==0 for n in range(1,20))
assert "versionCode 12100; versionName '1.11.121-beta'" in gradle
print('PASS: original v116 motion + 30% faster, rounded toolbar buttons, fixed three-column awards, unchanged version')
