#!/usr/bin/env python3
"""Static architecture and gesture checks for the one-drawer v1.11.118-beta refactor.
Not a substitute for on-device tests of touch animation and insets.
"""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/ru/reader/rsvp'
nav=(p/'UniversalDrawer.java').read_text()
base=(p/'NavigationActivity.java').read_text()
main=(p/'MainActivity.java').read_text()
back=(p/'BackNavigation.java').read_text()
entries=re.findall(r'item\(a,list,fg,R\.drawable\.\w+,"([^"]+)",',nav)
expected=['Главный экран','Чтение','Аудиокнига','Загрузки','Чтейцо','Настройки','Обновление','Справка']
assert entries==expected,(entries,expected)
for gone in ['Скорочтение','Озвучивание','Статистика','О приложении','Библиотека','Аудиотека']:
    assert f'"{gone}"' not in nav,gone
assert 'import android.app.Dialog;' not in nav and 'WindowManager.LayoutParams' not in nav
assert 'findViewById(android.R.id.content)' in nav
assert 'host.addView(shade' in nav and 'scroll.animate().translationX(0f)' in nav
assert 'setDuration(SLIDE_MS)' in nav and 'translationX(-s.panel.getWidth())' in nav
assert 'onInterceptTouchEvent(MotionEvent e)' in nav and 'dx<-dp(getActivity(),65)' in nav
assert 'shade.setOnClickListener(v->close(a))' in nav
assert 'onBackPressed()' in base and 'UniversalDrawer.closeIfOpen(this)' in base
assert 'UniversalDrawer.closeIfOpen(activity)' in back
assert 'dispatchTouchEvent(MotionEvent e)' not in base and 'UniversalDrawer.show(this)' not in base
assert 'UniversalDrawer.closeIfOpen(this)' in base
assert 'UniversalDrawer.show(this)' in main and 'drawerItem(' not in main
assert 'drawerShade' not in main and 'drawerPanel' not in main
assert 'R.drawable.ic_home' in nav and 'R.drawable.ic_microphone' in nav
assert 'a.startActivity(new Intent(a,AudioPlayerActivity.class))' in nav
# All activities except intentionally inherited children share NavigationActivity.
classes={}
for path in p.glob('*.java'):
    contents=path.read_text()
    m=re.search(r'\bclass\s+(\w+)\s+extends\s+(\w+)',contents)
    if m:classes[m.group(1)]=m.group(2)
for activity in ['MainActivity','ReaderActivity','AudioPlayerActivity','DownloadActivity','ChteicoActivity','SettingsActivity','ActivityInfo','EdgeDictionaryActivity','ReaderLogActivity','TtsVoiceActivity','LinkDownloadActivity','UrlImportActivity','EdgeFullBookActivity','EdgeFileExplorerActivity']:
    assert classes.get(activity)=='NavigationActivity',(activity,classes.get(activity))
assert classes.get('KvuheBookActivity')=='UrlImportActivity'
for xml_name in ['ic_home.xml','ic_book.xml','ic_microphone.xml','ic_downloads.xml','ic_chteico_nav.xml','ic_settings.xml','ic_web_update.xml','ic_info_help.xml']:
    assert (root/'app/src/main/res/drawable'/xml_name).exists()
build=(root/'app/build.gradle').read_text()
assert "versionCode 12000; versionName '1.11.120-beta'" in build
print('PASS v1.11.117: single identical 8-item overlay, icons, swipe-to-close only, navigation, 14 Activity destinations, version')
