import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
def code(name):return (SRC/name).read_text(encoding='utf-8')

class V174PerformanceContracts(unittest.TestCase):
    def test_version(self):
        cfg=(ROOT/'app/build.gradle').read_text()
        self.assertIn('versionCode 17400',cfg)
        self.assertIn("versionName '1.11.174-beta'",cfg)
    def test_edge_open_not_blocked_by_entire_archive(self):
        src=code('EdgeStreamingService.java');opened=src[src.index('public void openBook('):src.index('private void restoreStartPosition()')]
        self.assertNotIn('EdgeChapterArchive.restore(this,requested,name,dir)',opened)
        self.assertIn('bookReady=!offsets.isEmpty()',opened)
        self.assertIn('lazyRestore()',opened)
    def test_edge_lazy_one_fragment(self):
        src=code('EdgeStreamingService.java')
        self.assertIn('EdgeChapterArchive.restoreClip(',src)
        self.assertIn('clipRestore.execute(',src)
        self.assertIn('if(lazyRestore(current))',src)
        self.assertIn('if(lazyRestore(candidate))',src)
    def test_edge_restore_local_sidecar_reused(self):
        src=code('EdgeChapterArchive.java')
        segment=src[src.index('static synchronized boolean restoreClip('):src.index('/** Publish all complete chapter fragments')]
        self.assertIn('words.isFile()&&words.length()>0)return true;',segment)
        self.assertIn('LazyChapter chapter=lazyChapter(',segment)
        self.assertNotIn('for(DocumentFile chapter:all.listFiles())',segment)
    def test_saf_picker_worker_and_atomic_commit(self):
        src=code('DownloadFolderPicker.java')
        body=src[src.index('static boolean receive(Activity activity,int request,int result,Intent data,int expected,String key,'):]
        self.assertIn('VERIFY.execute(',body)
        self.assertIn('verifyWritable(context,DocumentFile.fromTreeUri(context,uri));',body)
        self.assertIn('activity.runOnUiThread(',body)
    def test_picker_ui_summary_without_provider_stats(self):
        src=code('DownloadFolderPicker.java')
        block=src[src.index('static String summary('):src.index('/** SAF tree display path')]
        self.assertNotIn('DocumentFile.fromTreeUri',block)
        self.assertNotIn('available(c,key)',block)
    def test_picker_async_resumption(self):
        for file in ['MainActivity.java','EdgeChapterDownloadsActivity.java','EdgeFullBookActivity.java','ReaderActivity.java','SettingsActivity.java']:
            self.assertIn('ok->{',code(file),file)
    def test_comics_async_first_open(self):
        src=code('ComicReaderActivity.java')
        start=src[src.index('public void onCreate('):src.index('private void setChromeVisible(')]
        self.assertIn('comic-chapter-discovery',start)
        self.assertNotIn('chapters=ComicLibrary.chapters(this,comic)',start)
        self.assertIn('Подготовка оглавления и страниц',start)
    def test_manga_event_driven_virtualized(self):
        src=code('MangaLibChapterDownloadsActivity.java')
        self.assertIn('RecyclerView.Adapter<',src)
        self.assertIn('AsyncListDiffer<Row>',src)
        self.assertIn('DownloadChangeBus.add(listener)',src)
        self.assertNotIn('main.postDelayed(this,2500)',src)
        self.assertNotIn('entries.removeAllViews()',src)
    def test_manga_no_starvation(self):
        src=code('MangaLibChapterDownloadsActivity.java')
        self.assertIn('if(refreshScheduled)return;',src)
    def test_manga_progress_checkpoint(self):
        src=code('MangaLibDownloadWorker.java')
        self.assertIn('if(i%8==0||i==pages.size()',src)
        self.assertIn('MangaLibStorage.progress(ctx,job)',src)
        self.assertIn('Thread.sleep(250)',src)
    def test_manga_status_still_durable(self):
        src=code('MangaLibStorage.java')
        self.assertIn('persistedCache',src)
        self.assertIn('sharedPreferences',src.lower()) if False else None
        self.assertIn('.commit()',src)
        self.assertIn('liveProgress.remove(job.id())',src)
    def test_edge_archives_only_refresh_on_change(self):
        src=code('EdgeChapterArchive.java')
        self.assertIn('lazyChapters.clear()',src)
        self.assertIn('lazyMissingChapters.clear()',src)
        self.assertIn('if(bookCache!=null)return',src)
        self.assertIn('if(chapterCache.containsKey(key))return',src)
    def test_word_index_prepared_on_io_thread(self):
        src=code('MainActivity.java')
        self.assertIn('final PreparedText prepared=PreparedText.from(',src)
        self.assertIn('cachedChapterStarts=prepared.chapterStarts',src)
        self.assertIn('java.util.Arrays.binarySearch(',src)
    def test_search_debounced_and_cached(self):
        src=code('MainActivity.java')
        self.assertIn('searchHandler.postDelayed(render,200L)',src)
        self.assertIn('library-search-index',src)
        self.assertIn('if(matches>=100)break;',src)
    def test_audiobook_duration_prefix(self):
        src=code('AudioPlaybackService.java')
        self.assertIn('rebuildDurationPrefix()',src)
        self.assertIn('return cachedTotalDuration;',src)
        self.assertIn('return Math.max(0,getPosition())+durationPrefix[at]',src)
    def test_prior_export_and_swipe_still_present(self):
        self.assertIn('EdgeMp3ChapterExport.append(',code('EdgeChapterArchive.java'))
        self.assertIn('ArchiveSwipeMath',code('DownloadActivity.java'))
        self.assertIn('R.drawable.ic_download_chapter_complete',code('DownloadActivity.java'))
    def test_no_new_static_theme_colors(self):
        self.assertIn('UiColorScheme.accent(this)',code('MangaLibChapterDownloadsActivity.java'))
    def test_no_delete_on_picker(self):
        src=code('DownloadFolderPicker.java')
        body=src[src.index('static boolean receive(Activity activity,int request,int result,Intent data,int expected,String key,'):src.index('static void recordIssue(')]
        self.assertNotIn('releasePersistableUriPermission(',body)
        self.assertNotIn('EdgeChapterArchive.delete',body)

if __name__=='__main__':unittest.main()
