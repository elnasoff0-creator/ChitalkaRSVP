#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
p=Path('app/src/main/java/ru/reader/rsvp')
gradle=Path('app/build.gradle').read_text()
assert "versionCode 11043; versionName '1.11.36-beta'" in gradle
assert "androidx.recyclerview:recyclerview:1.3.2" in gradle
search=(p/'KvuheSearchParser.java').read_text()
assert 'div.bookkitem' in search and 'search/?q=' in search
assert 'URLEncoder.encode(q,"UTF-8")' in search
assert 'data-src' in search and 'data-original' in search
assert 'trusted(requested,"/search/")' in search
assert 'return output' in search and 'nextUrl' in search
screen=(p/'KvuheSearchActivity.java').read_text()
assert 'RecyclerView' in screen and 'CoverImageLoader.show' in screen
assert 'new Intent(this,UrlImportActivity.class)' in screen and 'auto_fetch' in screen
ui=(p/'DownloadActivity.java').read_text()
assert 'Поиск аудиокниги' in ui and 'Импорт по ссылке' in ui
for tab in ['Все','В процессе','Аудиокниги','Edge TTS']:
 assert f'"{tab}"' in ui
assert 'R.drawable.ic_search_audiobook' in ui and 'R.drawable.ic_import_link' in ui
assert 'BookImportManager.remove' in ui and 'EdgeBookStorage.removeBook' in ui
assert 'edge.clearAudio' in ui and 'queue(item)' in ui
assert 'DownloadWorker.PAUSE' in ui and 'DownloadWorker.START' in ui
repo=(p/'DownloadsRepository.java').read_text()
assert 'queue.json' in repo and 'book.json' in repo
assert 'AudioLibrary' not in repo  # rendering reads original job/cache stores, no duplication
assert 'item.ready=totalWords>0' in repo
assert 'EdgeBookStorage.bookFolder' in repo
assert 'storeEdgeMeta' in (p/'EdgeDownloadService.java').read_text()
assert 'putNew' in (p/'BookImportManager.java').read_text()
assert 'auto_fetch' in (p/'UrlImportActivity.java').read_text()
manifest=ET.parse('app/src/main/AndroidManifest.xml')
ns='{http://schemas.android.com/apk/res/android}'
assert any(e.get(ns+'name')=='.KvuheSearchActivity' for e in manifest.findall('.//activity'))
assert Path('app/src/main/res/drawable-nodpi/ic_search_audiobook.png').is_file()
assert Path('app/src/main/res/drawable-nodpi/ic_import_link.png').is_file()
print('PASS: v1.11.35 unified downloads source, search, Edge migration, icons, controls')
PY
