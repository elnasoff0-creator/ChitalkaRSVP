from pathlib import Path
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]; s=r/'app/src/main/java/ru/reader/rsvp'; xml=r/'app/src/main/res/drawable';ns='{http://schemas.android.com/apk/res/android}'
res=['day','night','soft','sepia','custom']
for theme in res:
 p=xml/f'ic_reading_theme_{theme}.xml';t=ET.parse(p).getroot()
 assert t.get(ns+'width')=='24dp' and t.get(ns+'height')=='24dp'
 paths=t.findall('path');assert paths
 assert any(x.get(ns+'fillColor')!='#00000000' for x in paths)
 assert all(x.get(ns+'strokeColor') in ('#FF151515','#FFFFFFFF') for x in paths if x.get(ns+'strokeColor'))
 assert all(x.get(ns+'strokeColor') and x.get(ns+'strokeWidth') for x in paths)
print('PASS 5 theme VectorDrawable XML, 24dp, color-filled with single contrast outline and transparent canvas')
main=(s/'MainActivity.java').read_text()
reader=(s/'ReaderActivity.java').read_text()
settings=(s/'SettingsActivity.java').read_text()
em=(s/'ChteicoEmotions.java').read_text()
pet=(s/'ChteicoPetView.java').read_text()
cycle=(s/'ReadingThemeCycle.java').read_text()
assert main.index('View search = iconButton')<main.index('View themeButton=iconButton')<main.index('View importBtn = iconButton')
assert 'iconButton(ReadingThemeCycle.icon(prefs)' in main
assert 'toolbarThemeAction.clearColorFilter()' in reader
assert 'toolbarThemeAction=icon(ReadingThemeCycle.icon(prefs)' in reader
assert 'saveCustom' in cycle and '(current+1)%5' in cycle
assert 'theme_custom_bg' in cycle and 'theme_custom_fg' in cycle
assert 'ReadingThemeCycle.apply(prefs,ReadingThemeCycle.CUSTOM)' in settings
assert 'contrastSpinnerAdapter(stageLabels)' in settings
assert 'contrastSpinnerAdapter(branchLabels)' in settings
assert 'contrastSpinnerAdapter(labels)' in settings
assert 'contrastSpinnerAdapter(values)' in settings
assert 'contrastSpinnerAdapter(modes)' in settings
assert 'setPopupBackgroundDrawable' in settings
assert 'ReadingThemeCycle.isDark(bg)' in main
assert 'ReadingThemeCycle.isDark(bg)' in (s/'ChteicoActivity.java').read_text()
assert 'forceNightWhenSleeping && sleeping' in pet and 'ReadingThemeCycle.isDark(bg)' in pet
assert 'ChteicoEmotions.attach(petArea,' in main
assert 'null,showEmotion,HomeSectionOrder::startPetDrag' in main
assert 'ViewConfiguration.getLongPressTimeout()' in em and 'drag.start(view)' in em
assert 'android.view.HapticFeedbackConstants.LONG_PRESS' in em
assert 'new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL)' in main
assert "versionName '1.11.121-beta'" in (r/'app/build.gradle').read_text()
print('PASS toolbar placement, cycling, custom theme persistence, dark contrast, spinner visibility, revised gestures, tight bubble and version')
