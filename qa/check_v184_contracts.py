#!/usr/bin/env python3
"""v184 source integration checks. UI gestures, Android SQLite and speech bubbles require device QA."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/name).read_text(encoding='utf-8')
assert "versionCode 18400; versionName '1.11.184-beta'" in (root/'app/build.gradle').read_text()
reader=read('ReaderActivity.java')
menu=read('ReaderSheets.java')
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'setTextIsSelectable(true)' in reader
assert 'setCustomSelectionActionModeCallback(nativeTextActions(holder))' in reader
assert 'new GestureDetector(' in reader
assert 'getLongPressTimeout()' not in reader
assert 'NATIVE_QUOTE' in reader and 'NATIVE_EXTEND' in reader
assert 'this::saveSelectionQuote' in reader
assert 'new Intent(this,QuotesActivity.class)' in reader
assert 'Все цитаты' in menu and menu.count('"Все цитаты"')==2
arrays=re.findall(r'new String\[\]\{([^}]+)\}',menu)
icons=re.findall(r'new int\[\]\{([^}]+)\}',menu)
assert len(arrays)==2 and len(icons)==2
for labels,drawables in zip(arrays,icons):
    assert labels.count('"')//2==len([item for item in drawables.split(',') if item.strip()])
for name in ('mode==TTS?4:3','mode==TTS?5:4','mode==TTS?6:5'):
    assert name in reader,name
assert 'ReaderQuotesStore' in reader
assert 'QuotesActivity' in manifest
assert 'reader_quotes_v184.db' in read('ReaderQuotesStore.java')
store=read('ReaderQuotesStore.java')
for item in ('UNIQUE(book,from_char,to_char)','pet_enabled','randomForPet','GROUP BY LOWER(TRIM(text))','created_ms'):
    assert item in store,item
notes=read('ReaderAnnotationsStore.java')
assert 'null,2' in notes and 'ALTER TABLE annotations ADD COLUMN from_char' in notes
assert 'ALTER TABLE annotations ADD COLUMN to_char' in notes
pet=read('ChteicoActivity.java')
assert 'PetQuoteChoice.chooseQuote(phraseRandom,store.hasPetQuotes())' in pet
assert 'randomForPet(phraseRandom' in pet
assert 'speechScroll' in pet and 'Integer.MAX_VALUE' in pet
assert 'showSpeech(reply,lastPetReplyWasQuote?' in pet
quote_ui=read('QuotesActivity.java')
for item in ('Все цитаты','Изменить','Удалить','Показывать питомцем','Не показывать питомцем','quote_from_char'):
    assert item in quote_ui,item
assert not re.search(r'Процитировать питомцем',quote_ui)
assert 'return hasEnabledQuotes && random.nextBoolean();' in read('PetQuoteChoice.java')
for xml in (root/'app/src/main').rglob('*.xml'): ET.parse(xml)
md=list(root.glob('*.md'))
assert len(md)==4 and all('1.11.184' in file.name for file in md),md
print('PASS: v184 native selection, quote archive, pet 50/50, Android XML, CLEAN docs')
