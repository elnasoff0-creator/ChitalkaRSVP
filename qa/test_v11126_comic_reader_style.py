#!/usr/bin/env python3
"""Source contracts: ComicReader chrome copies ReaderActivity palette/layout.
These checks are static and do not replace an Android build or visual device QA.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
reader=(root/'app/src/main/java/ru/reader/rsvp/ReaderActivity.java').read_text()
comic=(root/'app/src/main/java/ru/reader/rsvp/ComicReaderActivity.java').read_text()
expect={
    'top gradient deliberately removed':'GradientDrawable.Orientation.TOP_BOTTOM' in reader and 'toolbarFade' not in comic and 'toolbarStack.setBackgroundColor(Color.TRANSPARENT)' in comic,
    'toolbar neutral chip and dimensions':'toolbarStack.setPadding(dp(5),dp(3),dp(5),dp(3))' in comic and 'titleChip.setBackground(rounded(neutral,13))' in comic,
    'same reader theme and action colors':'ReaderButtonColors.neutral(this,' in comic and 'ReaderButtonColors.accent(this,0xff915439)' in comic and 'ReaderButtonColors.contrastingText' in comic,
    'two metadata lines inside toolbar':'titleChip.addView(comicHeading' in comic and 'titleChip.addView(chapterInfo' in comic,
    'same bottom bar geometry':'new FrameLayout.LayoutParams(-1,dp(74),Gravity.BOTTOM)' in comic and 'bottomParams.setMargins(dp(8),0,dp(8),dp(8))' in comic,
    'same bottom chip dimensions':'Math.max(dp(105),(getResources().getDisplayMetrics().widthPixels/2)-dp(74))' in comic and 'dp(54),Gravity.LEFT|Gravity.CENTER_VERTICAL' in comic,
    'same accent contents icon':'contentsIcon.setBackground(rounded(accent,12))' in comic and 'R.drawable.ic_list_audio' in comic,
    'reader toolbar hide animation':'ReaderToolbarController.reveal(toolbarOverlay,show,64)' in comic,
    'bottom hide animation':'animateChrome(footerOverlay,show,dp(82))' in comic,
    'full WebView remains overlaid':'main.addView(web,new FrameLayout.LayoutParams(-1,-1))' in comic,
    'matching system bars':'SafeSystemBars.install(this)' in comic and 'SafeSystemBars.matchBackground(this,bg)' in comic,
    'no fixed purple colors from mockup':'0xffded0f0' not in comic,
    'comic navigation still available':'UiNavigationSheet.showComicModes' in comic and 'UiNavigationSheet.showComicChapters' in comic and 'UniversalDrawer.show(this)' in comic,
}
for label,result in expect.items():
    if not result:raise AssertionError(label)
print(f'PASS {len(expect)} comic-reader style contracts')
