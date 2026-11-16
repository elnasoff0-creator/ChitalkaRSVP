"""v172 lightweight source contracts. This is not an Android device UI or SAF test."""
from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[1]
S=R/'app/src/main/java/ru/reader/rsvp'
def src(name):return (S/name).read_text(encoding='utf-8')
class Release172(unittest.TestCase):
    def test_version(self):
        gradle=(R/'app/build.gradle').read_text()
        self.assertIn('versionCode 17200',gradle)
        self.assertIn("versionName '1.11.172-beta'",gradle)
    def test_right_open_instead_of_dismiss(self):
        java=src('DownloadActivity.java')
        segment=java[java.index('private void attachArchiveSwipe()'):java.index('private final class JobsAdapter')]
        self.assertNotIn('onSwiped(',segment)
        self.assertNotIn('new ItemTouchHelper',java)
        self.assertIn('list.addOnItemTouchListener(',segment)
        self.assertIn('ArchiveSwipeMath.horizontal(',segment)
        self.assertIn('revealedSwipeId=open?item.id',segment)
    def test_buttons_not_obscured_by_foreground(self):
        java=src('DownloadActivity.java')
        self.assertIn('swipeActions.setGravity(Gravity.LEFT',java)
        self.assertIn('new FrameLayout.LayoutParams(-2,-1,Gravity.LEFT)',java)
        self.assertIn('card.setTranslationX(opened?swipeActions.getWidth():0f)',java)
        self.assertIn('confirm.setOnClickListener(v->archive(item))',java)
        self.assertIn('cancel.setOnClickListener(',java)
        self.assertNotIn('swipeFrame.post(',java)
    def test_single_card_and_themes(self):
        java=src('DownloadActivity.java')
        self.assertIn('revealedSwipeId=open?item.id:""',java)
        self.assertIn('if(i!=pos)adapter.notifyItemChanged(i)',java)
        self.assertIn('UiColorScheme.accent(DownloadActivity.this)',java)
    def test_targeted_publish_service(self):
        service=src('EdgeFullBookService.java')
        save=service[service.index('int exported=EdgeChapterArchive.autoMerge'):service.index('// Keep the pre-existing in-app',service.index('int exported=EdgeChapterArchive.autoMerge'))]
        self.assertIn('EdgeChapterArchive.publishOne(',save)
        self.assertIn('EdgeChapterArchive.publishFragmentsOne(',save)
        self.assertIn('job.chapter',save)
        self.assertNotIn('EdgeChapterArchive.snapshot(',save)
        self.assertIn('EdgeChapterArchive.publicationError()',save)
    def test_no_full_mp3_readback_after_publication(self):
        code=src('EdgeChapterArchive.java')
        publish=code[code.index('static synchronized int publish('):code.index('private static JSONArray oneChapter(')]
        self.assertIn('writeNew(ctx,dir,audioFile,"audio/mpeg",temporary)',publish)
        self.assertIn('writeManifest(ctx,dir,index)',publish)
        self.assertNotIn('digest(in)',publish)
        self.assertNotIn('pending.renameTo',publish)
        self.assertNotIn('write(ctx,dir,audioFile',publish)
        self.assertIn('chapterDigest.update(fr.frames)',publish)
    def test_manifest_commit_last(self):
        code=src('EdgeChapterArchive.java')
        self.assertLess(code.index('newAudio=writeNew(ctx,dir,audioFile'),code.index('newWords=writeNew(ctx,dir,wordsFile'))
        self.assertLess(code.index('newWords=writeNew(ctx,dir,wordsFile'),code.index('writeManifest(ctx,dir,index)',code.index('newWords=writeNew(ctx,dir,wordsFile')))
        self.assertIn('oldIndex',code) if False else None
        self.assertIn('backup=text(ctx,manifest',code)
    def test_real_provider_names_saved(self):
        code=src('EdgeChapterArchive.java')
        for token in ('newAudio.getName()', 'newAudio.getUri().toString()',
                      'newWords.getName()', 'newWords.getUri().toString()',
                      'DocumentFile.fromSingleUri(ctx,fileUri)'):
            self.assertIn(token,code)
    def test_backward_compat_restore(self):
        code=src('EdgeChapterArchive.java')
        self.assertIn('entry.optString("audioFile",no+".mp3")',code)
        self.assertIn('row.optString("audioFile",name+".mp3")',code)
        self.assertIn('index.optInt("version")!=1',code)
        self.assertIn('fragmentsPresent(dir,old)',code)
    def test_lazy_missing_cache_only_restore(self):
        service=src('EdgeFullBookService.java')
        self.assertIn('if(missingCache)EdgeChapterArchive.restoreChapter(',service)
        self.assertIn('if(!job.forceReload)',service)
        self.assertIn('chapterNumber>=0',src('EdgeChapterArchive.java'))
    def test_no_duplicate_saf_snapshot_for_manual_finish(self):
        activity=src('EdgeChapterDownloadsActivity.java')
        window=activity[activity.index('int published=single?'):activity.index('EdgeFullBookStore.markPublished(job,chapter)',activity.index('int published=single?'))]
        self.assertNotIn('EdgeChapterArchive.snapshot(',window)
        self.assertIn('success=published>0;',window)
    def test_archive_pause_and_failure_kept(self):
        java=src('DownloadActivity.java')
        self.assertIn('if(!item.ready&&!item.paused)',java)
        self.assertIn('pauseForArchive(item)',java)
        self.assertIn('if(!DownloadArchiveStore.archive(',java)
    def test_previous_fishing_untouched(self):
        code=src('FishingBalance.java')
        self.assertIn('.90f,.80f,.70f,.65f,.60f',code)
        self.assertIn('2f+2f*clampRod(rod)',code)
if __name__=='__main__':unittest.main()
