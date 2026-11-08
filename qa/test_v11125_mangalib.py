#!/usr/bin/env python3
"""Static release contracts for MangaLib-only integration; not a live API test."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/ru/reader/rsvp'
def read(name):return (p/name).read_text(encoding='utf-8')
api=read('MangaLibApi.java')
search=read('MangaLibSearchActivity.java')
worker=read('MangaLibDownloadWorker.java')
storage=read('MangaLibStorage.java')
repository=read('DownloadsRepository.java')
download=read('DownloadActivity.java')
comics=read('ComicLibrary.java')
assert 'https://api.cdnlibs.org/api' in api
assert 'site_id[]' in api and 'chapters' in api and 'branch_id' in api and 'pages' in api
assert 'imageServers' in api and 'allowedImage' in api and 'https' in api
assert 'AccessException' in api and ('401' in api and '403' in api)
assert 'Страницы' in read('ComicReaderActivity.java') and 'Лента' in read('ComicReaderActivity.java')
assert 'MangaLibSearchActivity.class' in download
assert 'ic_manga_search' in download
assert '"Манга"' in download
assert 'MANGA="mangalib"' in repository
assert 'MangaLibStorage.jobs' in repository
assert 'MangaLibStorage.comicChapters' in comics
assert 'mangalib:' in storage and 'ZipFile' in storage
assert 'OneTimeWorkRequest' in search and 'WorkContinuation' in search and 'NetworkType.CONNECTED' in search
assert 'ZipOutputStream' in worker and '.part' in worker and 'ZipFile' in worker
assert 'ComicInfo.xml' in worker and 'MangaLibStorage.registerComic' in worker
assert 'ForegroundInfo' in worker and 'setForegroundAsync' in worker
assert 'com.github.junrar:junrar' in (r/'app/build.gradle').read_text()
assert 'androidx.work:work-runtime' in (r/'app/build.gradle').read_text()
assert 'versionCode 12600' in (r/'app/build.gradle').read_text()
manifest=(r/'app/src/main/AndroidManifest.xml').read_text()
assert '.MangaLibSearchActivity' in manifest
assert 'SystemForegroundService' in manifest
ET.parse(r/'app/src/main/AndroidManifest.xml')
ET.parse(r/'app/src/main/res/drawable/ic_manga_search.xml')
assert not list((r/'app/src/main').rglob('*.bak'))
print('PASS MangaLib 125: API contract, chapters, CBZ, WorkManager, UI, registry, XML, auth gating')
