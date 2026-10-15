#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
p=Path('app/src/main/java/ru/reader/rsvp')
types=(p/'LinkFileTypes.java').read_text()
for fragment in ('static final String MP3="mp3", EPUB="epub", FB2="fb2"','"application/epub+zip"','"application/x-fictionbook+xml"','ZipFile','META-INF/container.xml','FictionBook','safeFilename','mimeAllowed','checkSignature'):
 assert fragment in types,fragment
resolver=(p/'DirectLinkResolver.java').read_text()
assert 'LinkFileTypes.extension(raw)' in resolver
assert 'LinkFileTypes.supported(f.optString("name"))' in resolver
assert 'LinkFileTypes.checkSignature(format,sig,n)' in resolver
store=(p/'LinkDownloadStore.java').read_text()
assert 'o.put("format",format)' in store and 'o.optString("format"' in store
assert 'registerTextBook' in store and 'prefs.edit().putString("library",result.toString()).commit()' in store
assert '"mp3".equals(f.format)' in store
service=(p/'DirectLinkDownloadService.java').read_text()
for t in ('LinkFileTypes.mime(e.format)','LinkFileTypes.verify(temp,entry.format)','LinkDownloadStore.registerTextBook(this,e)','LinkFileTypes.safeFilename(e.name,e.format)'):
 assert t in service,t
assert 'DocumentFile.fromTreeUri' in service
form=(p/'LinkDownloadActivity.java').read_text()
assert 'Ссылка на MP3, FB2, EPUB или облачный файл' in form
assert 'LinkFileTypes.safeFilename(item.name,item.format)' in form
assert 'Выберите хотя бы один файл' in form
screen=(p/'LinkDownloadDetailsActivity.java').read_text()
assert 'new Intent(this,ReaderActivity.class)' in screen
assert '"book_uri",entry.savedUri' in screen
assert 'Читать ' in screen
assert 'mp3".equals(e.format)' in screen
assert "versionCode 11044; versionName '1.11.37-beta'" in Path('app/build.gradle').read_text()
manifest=ET.parse('app/src/main/AndroidManifest.xml')
assert manifest.find('.//application') is not None
print('PASS: v1.11.37 extra formats MP3/FB2/EPUB, signature checks, SAF, reader library, backwards compatibility')
PY
