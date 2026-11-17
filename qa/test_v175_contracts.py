import unittest
from pathlib import Path
R=Path(__file__).resolve().parents[1];J=R/'app/src/main/java/ru/reader/rsvp'
def src(n):return (J/n).read_text(encoding='utf-8')
class V175Contracts(unittest.TestCase):
 def test_version(self):
  x=(R/'app/build.gradle').read_text();self.assertIn('versionCode 17500',x);self.assertIn("versionName '1.11.175-beta'",x)
 def test_fishing_clock_preserves_cast_and_bite(self):
  x=src('ChteicoFishingView.java');self.assertIn('phase==READY||(phase==RESULT&&!shimmering)',x);self.assertIn('phase==BITE&&now-biteStartedMs',x)
 def test_sleep_badge_change_only(self):
  self.assertIn('if(',src('SleepTimerBadge.java'));self.assertIn('setLayoutParams',src('SleepTimerBadge.java'))
 def test_audio_player_dynamic_change_only(self):
  x=src('AudioPlayerActivity.java');self.assertIn('lastPlayImage',x);self.assertIn('setTextIfDifferent',x);self.assertIn('lastSpokenText',x)
 def test_audio_title_and_cover_layout(self):
  x=src('AudioPlayerActivity.java');self.assertIn('title.setMaxLines(2)',x);self.assertIn('lastPlayImage=0;',x)
 def test_comic_position_geometry(self):
  x=src('ComicReaderActivity.java');self.assertIn('getBoundingClientRect()',x);self.assertIn('positionInfo()',x);self.assertIn('aspect-ratio',x)
 def test_comic_no_alpha_flicker(self):
  x=src('ComicReaderActivity.java');self.assertNotIn('web.setAlpha(0f)',x)
 def test_comic_epoch_and_recovery(self):
  x=src('ComicReaderActivity.java');self.assertIn('generations.get(version)',x);self.assertIn('restoreWhenReady(',x);self.assertIn('comic_anchor_v175:',x)
 def test_reader_search_async(self):
  x=src('ReaderActivity.java');self.assertIn('searchWorker.execute',x);self.assertIn('250',x);self.assertIn('RecyclerView.Adapter',x)
 def test_legacy_reader_checkpoint(self):
  x=src('MainActivity.java');self.assertIn('lastTickerSavedIndex',x);self.assertNotIn('if (index % 8 == 0) saveAll();',x)
 def test_edge_prepared_book_cache(self):
  x=src('EdgeFullBookService.java');self.assertIn('private PreparedJob lastPrepared;',x);self.assertIn('PreparedJob prepared(',x)
 def test_edge_book_folder_cache(self):
  x=src('EdgeChapterArchive.java');self.assertIn('locatedBooks',x);self.assertIn('locatedBooks.clear();',x)
 def test_edge_snapshot_no_global_blocking_or_deadlock(self):
  x=src('EdgeChapterArchive.java');self.assertIn('static ChapterSnapshot snapshot(',x);self.assertIn('SNAPSHOT_LOCKS',x);self.assertIn('cacheEpoch',x);self.assertIn('static int exportableCount(',x)
 def test_edge_chapter_stats_and_virtualization(self):
  x=src('EdgeChapterDownloadsActivity.java');self.assertIn('RecyclerView.Adapter<ChapterHolder>',x);self.assertIn('EdgeOfflineIndex.CoverageIndex',x);self.assertNotIn('entries.removeAllViews()',x);self.assertNotIn('main.postDelayed(this,3000)',x)
 def test_edge_coverage_index(self):
  x=src('EdgeOfflineIndex.java');self.assertIn('prefixMaxEnd',x);self.assertIn('CoverageIndex(',x)
 def test_link_cards_stable(self):
  x=src('LinkDownloadDetailsActivity.java');self.assertIn('DownloadChangeBus.add',x);self.assertNotIn('postDelayed(ticker,1100)',x);self.assertEqual(x.count('public void onResume()'),1);self.assertEqual(x.count('public void onPause()'),1)
 def test_dashboard_async_diff(self):
  x=src('DownloadActivity.java');self.assertIn('DiffUtil.calculateDiff',x);self.assertIn('scan.execute(',x)
 def test_archive_hidden_cache(self):
  x=src('DownloadArchiveStore.java');self.assertIn('cachedHidden',x)
 def test_downloads_checkpoints(self):
  x=src('DownloadWorker.java');self.assertIn('lastPersist',x)
 def test_manager_file_diff(self):
  x=src('UnifiedDownloadManagerActivity.java');self.assertIn('DiffUtil.calculateDiff',x)
 def test_cover_size_lru(self):
  x=src('ComicCovers.java');self.assertIn('MAX_MEMORY_BYTES=12L*1024*1024',x);self.assertIn('Bitmap resident=fromMemory(key)',x)
 def test_audiolibrary_merge_and_natural(self):
  x=src('AudioLibrary.java');self.assertIn('NATURAL_PARTS',x);self.assertIn('java.util.Map<String,Integer> positions',x)
 def test_previous_edge_features(self):
  self.assertIn('publishFragmentsOne(',src('EdgeChapterArchive.java'));self.assertIn('restoreClip(',src('EdgeChapterArchive.java'))
 def test_previous_swipe(self):
  self.assertIn('ArchiveSwipeMath',src('DownloadActivity.java'))
 def test_previous_mp3_export(self):
  self.assertIn('EdgeMp3ChapterExport',src('EdgeChapterArchive.java'))
 def test_previous_comic_image_query(self):
  self.assertIn('comic.local',src('ComicReaderActivity.java'))
 def test_no_stripping_wordboundary(self):
  self.assertIn('words.json',src('EdgeChapterArchive.java'))
if __name__=='__main__':unittest.main()
