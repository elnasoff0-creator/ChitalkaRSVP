#!/usr/bin/env python3
"""Regression contracts for the final 1.11.134 AUDIO-UI based revision."""
from pathlib import Path
from xml.etree import ElementTree as ET
from hashlib import sha256
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/(name+'.java')).read_text(encoding='utf8')
assert "versionCode 13400; versionName '1.11.134-beta'" in (root/'app/build.gradle').read_text()
api=read('MangaLibApi')
assert 'path.startsWith("//manga/")||path.startsWith("//uploads/")' in api
assert 'path=path.substring(1)' in api
assert 'else if(path.startsWith("//")||path.startsWith("http:"))' in api
worker=read('MangaLibDownloadWorker')
for fragment in ['MangaLibStorage.stageDir(ctx,job)','cachedPage(stage,i)',
                 'BitmapFactory.decodeFile(file.getAbsolutePath(),options)',
                 '"paused".equals(saved.status)','"cancelled".equals(saved.status)',
                 'return Result.retry();','MangaLibStorage.clearStage(stage)',
                 'completeArchive(output)','getRunAttemptCount()<3']:
    assert fragment in worker,fragment
storage=read('MangaLibStorage')
for fragment in ['long branch=0,bytes=0','static File stageDir(', 'static void clearStage(', '"bytes"']:
    assert fragment in storage,fragment
queue=read('MangaLibQueue')
for fragment in ['static int pause(', 'static int resume(', 'static int enqueue(', 'ExistingWorkPolicy.APPEND_OR_REPLACE']:
    assert fragment in queue,fragment
screen=read('MangaLibChapterDownloadsActivity')
for fragment in ['Загрузка манги','Скачать все','Приостановлено'.lower(), 'МБ','Страниц:', 'MangaLibQueue.pause(', 'MangaLibQueue.resume(']:
    assert fragment.lower() in screen.lower(),fragment
assert '.MangaLibChapterDownloadsActivity' in (root/'app/src/main/AndroidManifest.xml').read_text()
download=read('DownloadActivity')
assert 'TextView more=center("⋮",28)' in download
assert 'openMangaChapters(item)' in download
assert 'MangaLibQueue.pause(this,item.source)' in download
repository=read('DownloadsRepository')
assert 'item.downloadedBytes=j.bytes' in repository
assert 'item.paused="paused".equals(j.status)' in repository
edge=read('EdgeChapterDownloadsActivity')
assert 'pauseButton=button("Пауза",this::toggle)' in edge
assert 'pauseButton.setText(running?"Пауза":"Продолжить")' in edge
assert '"приостановлено"' in edge
settings=read('SettingsActivity')
assert 'audioLink(R.drawable.ic_edge_dictionary,"Словарь MS Edge"' in settings
for old in ['Словарь произношения · Edge TTS','Каждая строка: исходное слово=произношение',
            'Сокращение пауз между предложениями MS Edge','Замена точки · Edge TTS','edgePunctuationSpinners(']:
    assert old not in settings,old
assert 'nameLp.leftMargin=dp(14)' in settings
assert 'EdgeTrailingSilence' not in read('EdgePlaybackService')
assert 'EdgeTrailingSilence' not in read('EdgeCacheCommitter')
assert 'EdgeTrailingSilence' not in read('EdgeStreamingService')
assert not (src/'EdgeTrailingSilence.java').exists()
assert 'return result;' in read('EdgeSpeechTextTransform')
expected=sha256((root.parent/'ic_audio_chapters(4).xml').read_bytes()).hexdigest() if (root.parent/'ic_audio_chapters(4).xml').exists() else '6299c2cab8cc621614a135e3069dda67cff556befdcbd1d7de4e8cab80b3e622'
assert sha256((root/'app/src/main/res/drawable/ic_audio_chapters.xml').read_bytes()).hexdigest()==expected
count=0
for xml in (root/'app/src/main/res').rglob('*.xml'):
    ET.parse(xml);count+=1
ET.parse(root/'app/src/main/AndroidManifest.xml')
print(f'PASS: final MangaLib, audio, settings, icon contracts and {count} resource XML files')
