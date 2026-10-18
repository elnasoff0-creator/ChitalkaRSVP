#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
p=Path('app/src/main/java/ru/reader/rsvp')
assert "versionCode 11046; versionName '1.11.39-beta'" in Path('app/build.gradle').read_text()
assert 'androidx.documentfile:documentfile:1.0.1' in Path('app/build.gradle').read_text()
ui=(p/'DownloadActivity.java').read_text()
assert 'Скачать по ссылке' in ui and 'Поиск аудиокниги' in ui
assert 'new Intent(this,LinkDownloadActivity.class)' in ui
assert '"По ссылке"' in ui and 'DownloadsRepository.LINK.equals(item.type)' in ui
form=(p/'LinkDownloadActivity.java').read_text()
for label in ('Путь / Папка для сохранения','Выбрать папку','Анализировать ссылки','Скачать выбранное','Переименовать','ACTION_OPEN_DOCUMENT_TREE','takePersistableUriPermission'):
 assert label in form,label
assert 'new Intent(this,DirectLinkDownloadService.class)' in form
resolver=(p/'DirectLinkResolver.java').read_text()
assert 'https' in resolver and 'checkUrl(current)' in resolver and 'getInstanceFollowRedirects' not in resolver
for key in ('dropbox.com','drive.google.com','disk.yandex.ru','cloud-api.yandex.net','audio/mpeg','Content-Range'):
 assert key in resolver,key
service=(p/'DirectLinkDownloadService.java').read_text()
for key in ('startForeground','DocumentFile.fromTreeUri','createDirectory','createFile','ContentResolver','savedUri','bytes=','FileOutputStream(temp,position>0)'):
 assert key in service,key
assert 'KvuheParser' not in service and 'EdgeDownloadService' not in service
store=(p/'LinkDownloadStore.java').read_text()
assert 'AudioLibrary.Book' in store and 'savedUri' in store and 'direct_link_batches_v1' in store
assert 'LinkDownloadStore.load(ctx)' in (p/'DownloadsRepository.java').read_text()
xml=ET.parse('app/src/main/AndroidManifest.xml')
ns='{http://schemas.android.com/apk/res/android}'
for name,element in [('DirectLinkDownloadService','service'),('LinkDownloadActivity','activity'),('LinkDownloadDetailsActivity','activity')]:
 assert any(x.get(ns+'name')=='.'+name for x in xml.findall('.//'+element)),name
print('PASS: v1.11.37 direct-link input, SAF storage, source separation, dashboard, foreground download')
PY
