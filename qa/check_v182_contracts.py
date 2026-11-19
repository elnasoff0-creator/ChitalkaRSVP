#!/usr/bin/env python3
"""Static release contracts; not a replacement for Android Gradle compilation."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
r=root/'app/src/main/java/ru/reader/rsvp'
read=lambda f:(r/f).read_text(encoding='utf-8')
assert "versionCode 18200; versionName '1.11.182-beta'" in (root/'app/build.gradle').read_text()
settings=read('SettingsActivity.java')
assert 'drawDownloadFoldersSection();' in settings
assert settings.index('drawDownloadFoldersSection();')<settings.index('private void headerWithHelp(')
assert settings.index('private void drawDownloadFoldersSection()')<settings.index('void drawOnlineSettings(){')
assert 'headerWithHelp("Редактирование меню");' in settings
assert 'addHelpIcon("Редактирование меню")' not in settings
assert 'audioLink(R.drawable.ic_downloads,"Папка Book' not in settings
assert 'Выберите каждую существующую папку отдельно через Android 16' not in settings
for title in ('Книги', 'Комиксы', 'Аудиокниги', 'Папки загрузок',
              'Повторить сохранение готовых файлов', 'Проверить и очистить дубликаты'):
    assert title in settings,title
for s in ('BOOK_REQUEST','MANGA_REQUEST','AUDIO_REQUEST','CategorySafRecovery.restore',
          'AudioSafDuplicates.inspect','EdgeChapterArchive.invalidate'):
    assert s in settings,s
icons=read('TtsEngineIcons.java')
assert 'R.drawable.ic_edge_official' in icons
assert 'getApplicationIcon("com.microsoft.emmx")' not in icons
icon=root/'app/src/main/res/drawable-nodpi/ic_edge_official.webp'
raw=icon.read_bytes()
assert raw[:4]==b'RIFF' and raw[8:12]==b'WEBP' and len(raw)>1000
reader=read('ReaderActivity.java')
assert 'TtsEngineIcons.edge(this)' in reader and 'toolbarEngineBadge' in reader
ranobe=read('RanobesApi.java')
assert 'resolveBookLink(String raw)' in ranobe and 'looksLikeUrl(String value)' in ranobe
assert 'if(value.length()>70)' in ranobe  # search term only, not a URL
assert 'resolveBookLink(entered)' in read('RanobesSearchActivity.java')
assert 'EdgeSentenceBoundary.split(raw)' in read('SentenceChunks.java')
assert 'EdgeSentenceBoundary.split(text)' in read('SentenceIndex.java')
seg=read('EdgeSpeechSegments.java')
assert 'priorityPieces(String raw,SentenceIndex index' in seg
assert 'first=next' in seg and 'groups.add(join(raw,first,last))' in seg
assert 'EdgeSpeechSize.fits' in read('UnifiedBookSession.java')
assert 'session.edgeGroups(speech,' in read('EdgeFullBookService.java')
assert 'EdgeSpeechLayout.forFolder' in read('EdgeStreamingService.java')
assert 'EdgeSpeechSize.fits(text)' in read('EdgeSpeechEngine.java')
assert 'boolean hardLimitExceeded=problem.contains("Предложение превышает допустимый размер")' in read('EdgeStreamingService.java')
assert 'startBackupTtsForFailedClip(clip,hardLimitExceeded)' in read('EdgeStreamingService.java')
assert 'ssml.getBytes(StandardCharsets.UTF_8).length' in read('EdgeSpeechEngine.java')
for f in (root/'app/src/main').rglob('*.xml'): ET.parse(f)
md=[p.name for p in root.glob('*.md')]
assert all('1.11.182' in name for name in md),md
print('PASS: v182 version, UI, SAF, Edge image, Ranobes URL, sentence-safe wiring and XML')
