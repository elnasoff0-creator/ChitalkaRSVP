#!/usr/bin/env python3
"""v153 archive/menu/vector requirements; source-level checks only."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1];p=root/'app/src/main/java/ru/reader/rsvp'
def source(name):return (p/name).read_text()
assert 'versionCode 15300' in (root/'app/build.gradle').read_text()
assert "versionName '1.11.153-beta'" in (root/'app/build.gradle').read_text()
assert '.DownloadArchiveActivity' in (root/'app/src/main/AndroidManifest.xml').read_text()
main=source('DownloadActivity.java')
archive=source('DownloadArchiveActivity.java')
store=source('DownloadArchiveStore.java')
delete=source('DownloadArchiveDelete.java')
assert 'ItemTouchHelper.RIGHT' in main and 'getSwipeThreshold' in main
assert 'getBindingAdapterPosition()' in main
assert 'DownloadArchiveStore.archive(' in main and 'DownloadArchiveStore.hiddenIds(' in main
assert 'if(hidden.contains(item.id))continue;' in main
assert 'R.drawable.ic_download_archive' in main and 'case 81:archive(item);' in main
assert 'onTextChanged(CharSequence s' in archive and 'filter();' in archive
assert 'Дата: сначала новые' in archive and 'Имя: Я–А' in archive
assert 'DownloadArchiveStore.restore' in archive and 'DownloadArchiveStore.clear' in archive
assert 'DownloadArchiveDelete.delete' in archive and 'setNegativeButton("Отмена"' in archive
assert 'DownloadArchiveStore.removedFiles' in archive
assert 'if(item==null||!item.ready' in store
assert 'Set<String> discarded' in store and 'discarded.add(e.id)' in store
assert 'EdgeChapterArchive.deleteSavedBook' in delete
assert 'BookImportManager.remove(c,job,false)' in delete
assert 'LinkDownloadStore.remove' in delete
assert 'MangaLibStorage.put' in delete
for name in ('ic_download_archive','ic_download_restore','ic_download_archive_clear','ic_download_archive_delete','ic_edge_merge_chapter'):
    f=root/'app/src/main/res/drawable'/f'{name}.xml'
    e=ET.parse(f).getroot();a='{http://schemas.android.com/apk/res/android}'
    assert e.get(a+'width')=='24dp' and e.get(a+'height')=='24dp',name
    assert e.get(a+'viewportWidth')=='24' and e.get(a+'viewportHeight')=='24',name
import hashlib
assert hashlib.sha256((root/'app/src/main/res/drawable/ic_edge_merge_chapter.xml').read_bytes()).hexdigest()=='1a47fd76ec2aea160453640d18f0a8cea2c59684d3df161a5c25cbb5b3a257b2'
print('PASS: v153 archive UI/actions, swipe, search, sorting, SAF deletion guard, updated vector')
