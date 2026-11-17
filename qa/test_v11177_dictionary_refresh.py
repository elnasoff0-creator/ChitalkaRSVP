"""Non-Android structural regression contracts for the dictionary refresh release.
Full Android runtime integration still requires a device/CI build.
"""
from pathlib import Path
base=Path(__file__).resolve().parent.parent
src=base/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/name).read_text(encoding='utf-8')
service=read('EdgeStreamingService.java')
store=read('EdgeDictionaryStore.java')
reader=read('ReaderActivity.java')
manager=read('UnifiedDownloadManagerActivity.java')
archive=read('EdgeChapterArchive.java')
full=read('EdgeFullBookService.java')
assert 'versionCode 17700' in (base/'app/build.gradle').read_text()
assert 'new Intent(this,UnifiedDownloadManagerActivity.class)' in reader
assert 'new Intent(this,EdgeChapterDownloadsActivity.class)' not in reader
assert '"Загрузка озвучки"' in read('ReaderSheets.java')
assert 'ACTION_CHANGED' in store and 'app.sendBroadcast(event)' in store
assert 'dictionaryReceiver' in service and 'refreshSettings()' in service
assert 'scheduleDictionaryScan(false)' in service
assert 'dictionaryScan.execute(' in service
assert 'for(int n=0;n<5' in service
assert 'previousOnly&&interactiveListening' in service
assert 'dictionaryGeneration.incrementAndGet()' in service
assert 'player.clearTracksForSelection()' in service
assert 'if(resume&&player!=null){player.play()' in service
assert 'job.speech.cacheKey(voiceNameAtRequest,rateAtRequest)' in service
assert 'liveJobs.remove(job.fileId,job)' in service
assert 'dictionaryRevision' in archive and 'dictionaryRevision(chapter)' in manager
assert 'EdgeFullBookStore.QUEUED' in full and 'revisionNumber()' in full
assert 'if(!allTypes)all.removeIf' in read('DownloadsRepository.java')
print('PASS: v1.11.177 UI routing, revision events, priority refresh, stale-job guards and chapter metadata')
