#!/usr/bin/env python3
"""v156 structural regression checks for display-name overrides and metadata safety."""
from pathlib import Path
r=Path(__file__).resolve().parents[1]
src=r/'app/src/main/java/ru/reader/rsvp'
read=lambda f:(src/f).read_text(encoding='utf-8')
gradle=(r/'app/build.gradle').read_text(encoding='utf-8')
assert "versionCode 16000; versionName '1.11.160-beta'" in gradle
main=read('MainActivity.java')
assert main.count('"\u041f\u0435\u0440\u0435\u0438\u043c\u0435\u043d\u043e\u0432\u0430\u0442\u044c"')>=3
assert 'BookRenameDialog.show(this,m.title' in main
assert 'BookRenameDialog.show(this,book.name' in main
assert 'BookRenameDialog.show(this,item.title' in main
assert 'BookTitleStore.renameText(this,m.uri' in main
assert 'BookTitleStore.renameAudio(this,book.id' in main
assert 'BookTitleStore.renameComic(this,item.uri' in main
assert 'm.title=BookTitleStore.title(this,BookTitleStore.TEXT,m.uri,m.title)' in main
assert 'BookTitleStore.title(this,BookTitleStore.TEXT,uriString,book.title' in main
assert 'BookTitleStore.title(this,BookTitleStore.TEXT,uri.toString(),book.title)' in read('ReaderActivity.java')
repo=read('DownloadsRepository.java')
assert 'item.sourceTitle=item.title;item.title=BookTitleStore.downloadTitle(ctx,item)' in repo
assert 'sourceTitle=""' in repo
ui=read('DownloadActivity.java')
assert 'private void renameDownload(' in ui
assert 'BookTitleStore.renameDownload(this,item.id,item.type,item.bookUri,item.source,newName)' in ui
assert ui.count('"\u041f\u0435\u0440\u0435\u0438\u043c\u0435\u043d\u043e\u0432\u0430\u0442\u044c"')>=4
assert 'case 77:renameDownload(item);break;' in ui
archive=read('DownloadArchiveStore.java')
assert 'e.sourceTitle=item.sourceTitle.isEmpty()?item.title:item.sourceTitle' in archive
assert 'displayTitle=BookTitleStore.title' in archive
assert 'EdgeBookStorage.bookFolder(c,uri,archived.sourceTitle)' in read('DownloadArchiveDelete.java')
assert 'BookRenameDialog.show(this,entry.displayTitle' in read('DownloadArchiveActivity.java')
assert 'menu.getMenu().add("\u041f\u0435\u0440\u0435\u0438\u043c\u0435\u043d\u043e\u0432\u0430\u0442\u044c")' in read('DownloadArchiveActivity.java')
titles=read('BookTitleStore.java')
for item in ('renameText','renameAudio','renameComic','renameDownload','downloadTitle','String clean','edit.commit()'):
    assert item in titles,item
assert 'name = clean(name)' in titles
assert 'item.file.renameTo(' not in titles
assert 'String safe' in read('RanobeLibStorage.java')
assert 'catch(Exception error){android.util.Log.w("RanobeLibStorage","Metadata",error);}' in read('RanobeLibStorage.java')
assert 'for(AudioLibrary.Book old:books)' in read('LinkDownloadStore.java')
assert 'book.name=all.get(i).name;book.customName=true' in read('BookImportManager.java')
print('PASS: v156 rename UI + title alias propagation + no filesystem rename + CI JSONException fix')
