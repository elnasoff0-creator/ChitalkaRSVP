#!/usr/bin/env python3
"""v153 HOTFIX: move downloader help, chapter filenames and MP3-only AudioLibrary export."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/ru/reader/rsvp'
get=lambda n:(p/n).read_text()
assert "versionName '1.11.153-beta'" in (root/'app/build.gradle').read_text()
assert 'versionCode 15300' in (root/'app/build.gradle').read_text()
chapters=get('EdgeChapterDownloadsActivity.java')
help=get('ActivityInfo.java')
archive=get('EdgeChapterArchive.java')
download=get('DownloadActivity.java')
assert 'TextView hint=text("Нажмите главу для подготовки.' not in chapters
assert 'hint.setText("Нажмите на главу для подготовки' not in chapters
assert 'В «Загрузка озвучки» нажмите на главу для подготовки' in help
assert 'chapterAudioName(int chapter,BookChapterIndex toc)' in archive
assert 'return number+".mp3"' in archive
assert 'return number+" - "+title+".mp3"' in archive
assert 'index.put("audioFile",audioFile)' in archive
assert 'index.put("wordsFile",wordsFile)' in archive
assert 'write(ctx,dir,audioFile,"audio/mpeg",temporary)' in archive
assert 'write(ctx,dir,wordsFile,"application/json"' in archive
assert 'DocumentFile mp3=chapter.findFile(audioName(index))' in archive
assert 'DocumentFile stored=dir.findFile(audioName(index))' in archive
assert 'audioName(index),wordsName(index),INDEX_NAME' in archive
assert 'if(mergedPresent(chapter,index)' in archive
assert 'Перенести в аудиокниги' in download
assert 'if(item.ready)popup.getMenu().add(0,42' in download
assert 'case 42:transferEdgeAudiobook(item);break;' in download
assert 'EDGE_AUDIOBOOK_REQUEST=12522' in download
assert 'EdgeChapterArchive.exportAudiobook' in download
assert 'AudioLibrary.merge(ctx,imported)' in archive
assert '"fragments".equals(index.optString("mode"))' in archive
assert 'expectedChapters' in archive
assert 'destinationTree.toString()+"#"' in archive
assert 'new Thread(()->' in download
# Copy source bytes are streamed into audio/mpeg; JSON is used only to verify them.
assert 'folder.createFile("audio/mpeg",stagedName)' in archive
assert 'out.write(buf,0,n)' in archive
assert not re.search(r'folder\.createFile\([^)]*application/json',archive)
assert 'JSON не копируются' in download
assert 'Исходные файлы Edge TTS сохраняются' in download
print('PASS: v153 hotfix UI/help, indexed MP3 names, SAF MP3-only audiobook export, version unchanged')
