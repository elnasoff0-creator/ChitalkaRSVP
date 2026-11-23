#!/usr/bin/env python3
"""Source integration contracts: version, SAF UI, CSV, SQLite migration and scroll."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda fn:(src/fn).read_text(encoding='utf-8')
assert "versionCode 19100; versionName '1.11.191-beta'" in (root/'app/build.gradle').read_text()
policy=read('ReaderTtsScrollPolicy.java')
controller=read('ReaderTtsScrollController.java')
assert 'movingShift(int wordCenterY' in policy
assert 'anchorPercent(int wordCenterY' in policy
assert 'animating\n                ?ReaderTtsScrollPolicy.movingShift' in controller
assert 'explicitReturn||keepExplicit' in controller
assert 'postOnAnimation(frame)' in controller
assert 'MANUAL_OR_SELECTION' in controller and 'STALE_NAVIGATION' in controller
store=read('EdgeDictionaryStore.java')
assert 'super(context.getApplicationContext(),DB,null,2)' in store
assert 'ALTER TABLE rules ADD COLUMN enabled' in store
assert 'ALTER TABLE rules ADD COLUMN note' in store
assert 'SELECT id,kind,pattern,context,replacement,is_regex,enabled,note' in store
assert 'TtsDictionaryImportPlan importCsv' in store
assert 'db.beginTransaction()' in store and 'db.setTransactionSuccessful()' in store
assert 'backupBeforeReplace' in store and 'latestCsvBackup' in store
assert 'update.remove("ordinal")' in store
assert 'changed(true)' in store
assert 'WHERE enabled=1 ORDER BY ordinal,id' in store
activity=read('EdgeDictionaryActivity.java')
assert 'Intent.ACTION_CREATE_DOCUMENT' in activity and 'Intent.ACTION_OPEN_DOCUMENT' in activity
assert 'text/csv' in activity and 'Импорт словаря (.csv)' in activity
assert 'Экспорт словаря (.csv)' in activity and 'Восстановить последнюю копию' in activity
assert 'TtsDictionaryCsv.read' in activity and 'TtsDictionaryCsv.write' in activity
assert 'new Thread' in activity and 'showCsvPreview' in activity and 'commitCsvImport' in activity
assert 'enum Mode {MERGE_KEEP,MERGE_OVERWRITE,REPLACE_ALL}' in read('TtsDictionaryImportPlan.java')
assert 'TtsDictionaryCsv.java' in [p.name for p in src.glob('*.java')]
assert 'setChecked(previous==null||previous.enabled)' in read('EdgeDictionaryRuleDialog.java')
assert 'existing rules' not in activity # no misleading text English
assert 'setEllipsize(TextUtils.TruncateAt.END)' in read('ChteicoActivity.java')
assert 'PetQuoteChoice.chooseQuote(phraseRandom,store.hasPetQuotes())' in read('ChteicoActivity.java')
for x in (root/'app/src/main').rglob('*.xml'):ET.parse(x)
md=list(root.glob('*.md'))
assert len(md)==4 and all('1.11.191' in m.name for m in md),md
assert not list(root.glob('*.json')), 'no standalone JSON exporter'
print('PASS: version 191, scrolling lifecycle, Android CSV SAF UI, SQLite migration, atomic import, CLEAN docs')
