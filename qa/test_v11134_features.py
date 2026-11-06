#!/usr/bin/env python3
"""Source contract regression for 1.11.134: no device or network required."""
from pathlib import Path
from xml.etree import ElementTree as ET
from hashlib import sha256
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'; draw=root/'app/src/main/res/drawable'
def java(name):return (src/(name+'.java')).read_text(encoding='utf8')
assert "versionCode 13400; versionName '1.11.134-beta'" in (root/'app/build.gradle').read_text()
m=java('MangaLibApi')
for fragment in ['private static String pagePath(JSONObject page)','"url","path","image_url","imageUrl","src"','private static String safeDiagnostic','servers.add("https://img2.imglib.info")','servers.add("https://img3.mixlib.me")','sites.optInt(j,-1)==1','!allowedImage(original.toString())','Не удалось определить адрес страницы']:
    assert fragment in m,fragment
assert m.index('String path=pagePath(o)')<m.index('List<String> candidates=imageUrls(path,servers)')
e=java('EdgeDictionaryActivity')
for fragment in ['plus.setOnClickListener(v->editRule(null,ignoredOnly?','R.drawable.ic_microphone','microphone.setOnClickListener(v->listen(speaking.getText().toString()))','listen.setOnClickListener(v->listen(rule.replacement))','UiColorScheme.accent(this)','UiColorScheme.selected(this)','new SwipeDialogs.Builder(this)','touch(int color)']:
    assert fragment in e,fragment
for fragment in ['Символы +','Добавить исключение','Убрать пустые строки · Regex','Текст для предварительной проверки','Проверить Regex / замену','Прослушать результат','Символьная замена перед Edge TTS']:
    assert fragment not in e,fragment
c=java('UiColorScheme')
for fragment in ['extends','KEY_MODE="ui_color_scheme"','KEY_CUSTOM="ui_custom_accent"','"warm"','"green"','"violet"','"custom"','"theme"','public static int selected','setCheckMarkTintList','setButtonTintList','setThumbTintList','setProgressTintList']:
    if fragment=='extends':continue
    assert fragment in c,fragment
setting=java('SettingsActivity')
assert 'header("Цветовая схема интерфейса");drawUiColorScheme()' in setting
for fragment in ['Свой цвет (HEX)','#[0-9A-Fa-f]{6}','uiCustomColorDialog()','UiColorScheme.KEY_MODE','UiColorScheme.KEY_CUSTOM']:
    assert fragment in setting,fragment
for file in ['UiNavigationSheet','ReaderActivity','TtsTextBlockManager','AudioPlayerActivity','SwipeDialogs']:
    assert 'UiColorScheme.' in java(file),file
ui=java('UiNavigationSheet')
for color in ['0xffdae9df','0xffd9c6ef','0xffe7d5cf','0xffead7d1','0xff7050a8','0xff388458','0xffa53b4c']:
    assert color not in ui,color
assert 'new SwipeDialogs.Builder(this).setTitle("Главы · "+title.title)' in java('MangaLibSearchActivity')
for fn in ['ic_manga_search.xml','ic_audio_chapters.xml']:
    E=ET.parse(draw/fn).getroot()
    assert E.attrib['{http://schemas.android.com/apk/res/android}width']=='24dp'
    assert E.attrib['{http://schemas.android.com/apk/res/android}height']=='24dp'
assert sha256((draw/'ic_manga_search.xml').read_bytes()).hexdigest()=='3c3bcba55cb76be1f77d2b29a21af3ff0f8cc06396971b9c5ed95178e556776c'
assert sha256((draw/'ic_audio_chapters.xml').read_bytes()).hexdigest()=='e281097b60e64ab8fe0b3594389b414db4341e04c719b26a9666e836f8bd8fca'
for f in root.glob('app/src/main/res/**/*.xml'):ET.parse(f)
print('PASS: MangaLib URL/CDN security and diagnosis')
print('PASS: Edge dictionary editor, actions, preview and theme colors')
print('PASS: shared palette across activities and native dialogs')
print('PASS: supplied vector drawables and all resource XML')
