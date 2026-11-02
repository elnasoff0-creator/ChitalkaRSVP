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
# In 123 the menu is user-configurable; route IDs and icons live in DrawerMenuStore.
store=(p/'DrawerMenuStore.java').read_text()
assert 'String[] DEFAULT={"home","reading","audio","comic","downloads","pet","settings","update","help"}' in store
assert 'for(String id:DrawerMenuStore.order(a))' in nav
assert 'DrawerMenuStore.visible(a,id)' in nav
assert 'DrawerEditorActivity.class' in nav and '"Редактировать"' in nav
assert 'companionEnabled?"Чтейцо":"Статистика"' in store or 'ChteicoStore.enabled(c)?"Чтейцо":"Статистика"' in store
for gone in ['Скорочтение','Озвучивание','О приложении','Библиотека','Аудиотека']:
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
assert 'R.drawable.ic_home' in store and 'R.drawable.ic_tab_music' in store
assert 'a.startActivity(new Intent(a,AudioPlayerActivity.class).putExtra("audio_book_id",selected.id))' in nav
# All activities except intentionally inherited children share NavigationActivity.
classes={}
for path in p.glob('*.java'):
    contents=path.read_text()
    m=re.search(r'\bclass\s+(\w+)\s+extends\s+(\w+)',contents)
    if m:classes[m.group(1)]=m.group(2)
for activity in ['MainActivity','ReaderActivity','AudioPlayerActivity','DownloadActivity','ChteicoActivity','SettingsActivity','ActivityInfo','EdgeDictionaryActivity','ReaderLogActivity','TtsVoiceActivity','LinkDownloadActivity','UrlImportActivity','EdgeFullBookActivity','EdgeFileExplorerActivity']:
    assert classes.get(activity)=='NavigationActivity',(activity,classes.get(activity))
assert classes.get('KvuheBookActivity')=='UrlImportActivity'
for xml_name in ['ic_home.xml','ic_book.xml','ic_tab_music.xml','ic_downloads.xml','ic_chteico_nav.xml','ic_settings.xml','ic_web_update.xml','ic_info_help.xml']:
    assert (root/'app/src/main/res/drawable'/xml_name).exists()
build=(root/'app/build.gradle').read_text()
assert "versionCode 12400; versionName '1.11.124-beta'" in build
print('PASS v1.11.117: single identical 8-item overlay, icons, swipe-to-close only, navigation, 14 Activity destinations, version')
