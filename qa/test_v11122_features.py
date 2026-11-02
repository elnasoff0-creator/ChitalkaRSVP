#!/usr/bin/env python3
"""Source contracts for the new release; does not simulate Android runtime."""
from pathlib import Path
from PIL import Image
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/ru/reader/rsvp'
icons=['ic_reading_theme_custom_dark','ic_reading_theme_custom','ic_reward_streak','ic_reward_audio','ic_next_chapter','ic_microphone','ic_audio_chapters','ic_audio_timer','ic_autoload','ic_back','ic_book','ic_bookmark','ic_downloads','ic_edge_tts','ic_forward_1m','ic_forward_10s','ic_mode_cursor','ic_list_audio','ic_audiotheque']
for name in icons:
 f=r/'app/src/main/res/drawable'/(name+'.xml');assert f.exists(),name
 ET.parse(f)
assert len(icons)==19
manifest=(r/'app/src/main/AndroidManifest.xml').read_text()
for name in ['ComicReaderActivity','ChteicoRunnerActivity','BookmarksActivity']:
 assert f'.{name}' in manifest,name
reader=(p/'ReaderActivity.java').read_text()
assert 'updateChapterTitle' in reader and 'toolbarChapterName' in reader
assert 'showBookmarks()' in reader and 'ReaderBookmarks.add' in reader
assert 'refreshSelectedClip(selected)' in reader
assert 'tts_resume_previous_sentence' in reader
comic_reader=(p/'ComicReaderActivity.java').read_text()
comic_library=(p/'ComicLibrary.java').read_text()
main=(p/'MainActivity.java').read_text()
assert 'ComicLibrary.chapters' in comic_reader
assert '"Постраничный","Вертикальный"' in comic_reader
assert 'Комиксотека' in main
assert 'ComicLibrary.save(this,comic)' in main
assert 'Архив CBZ / CBR' in main
assert 'importComicArchive' in main
assert 'isArchiveName' in comic_library
assert 'extractCbz' in comic_library and 'extractCbr' in comic_library
assert (r/'app/src/main/res/drawable/ic_comic_library.xml').exists()
ET.parse(r/'app/src/main/res/drawable/ic_comic_library.xml')
assert 'com.github.junrar:junrar' in (r/'app/build.gradle').read_text()
assert 'setOnLongClickListener' in (p/'BookmarksActivity.java').read_text()
assert 'ReaderFontLibrary.importFont' in (p/'SettingsActivity.java').read_text()
assert 'EdgeChapterLayout.publish' in (p/'EdgeFullBookService.java').read_text()
assert 'selectedFromSentence' in (p/'EdgeDictionaryActivity.java').read_text()
assert 'new Intent(this,ChteicoRunnerActivity.class)' in (p/'ChteicoActivity.java').read_text()
assert 'addGameExperience' in (p/'ChteicoStore.java').read_text()
folder=r/'app/src/main/res/drawable-nodpi'
for stage in ['chick','reader','adult']:
 for pose in ['run1','run2','jump']:
  f=folder/f'chteico_game_{stage}_{pose}.png';assert f.exists(),f
  im=Image.open(f);assert im.size==(64,64) and im.mode=='RGBA'
for i in range(1,6):
 f=folder/f'chteico_book_{i}.png';assert f.exists()
assert 'versionCode 12300' in (r/'app/build.gradle').read_text()
print('PASS 122: icons, manifest, reader, bookmarks, font picker, comics, dictionary revision, chapter layout, runner sprites')
