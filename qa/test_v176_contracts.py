"""Contract checks for download progress, bounded queues and version handling."""
import unittest
from pathlib import Path
R=Path(__file__).resolve().parent.parent
J=R/'app/src/main/java/ru/reader/rsvp'
def source(name):return (J/name).read_text()
class V176Contract(unittest.TestCase):
 def test_version(self):
  gradle=(R/'app/build.gradle').read_text();self.assertIn('versionCode 17600',gradle);self.assertIn("versionName '1.11.176-beta'",gradle)
 def test_release_workflow(self):
  y=(R/'.github/workflows/release.yml').read_text();self.assertIn('aapt" dump badging',y);self.assertIn('GITHUB_REF_NAME',y);self.assertIn('apksigner" verify',y);self.assertIn('sha256sum',y);self.assertNotIn('v1.11.58-beta.apk',y)
 def test_legacy_bus(self):
  b=source('DownloadChangeBus.java');self.assertIn('static void publish(String id)',b);self.assertIn('static void progress(',b);self.assertIn('static void command(',b)
 def test_idempotent_progress(self):
  t=source('DownloadProgressTracker.java');self.assertIn('sequence<=ch.sequence',t);self.assertIn('b.done+=next-ch.permille',t);self.assertIn('ch.committed&&!committed',t)
 def test_atomic_batched_checkpoint(self):
  t=source('DownloadProgressStore.java');self.assertIn('AtomicFile',t);self.assertIn('ScheduledExecutorService',t);self.assertIn('important?0:4',t);self.assertNotIn('JSONArray',t)
 def test_separate_command_lane(self):
  t=source('DownloadCommandController.java');self.assertIn('newSingleThreadExecutor',t);self.assertIn('generation',t)
 def test_manager_no_full_reload_on_progress(self):
  t=source('UnifiedDownloadManagerActivity.java');self.assertIn('notifyItemChanged(at,PAYLOAD_PROGRESS)',t);self.assertIn('DownloadProgressTracker.plan',t);self.assertIn('DownloadCommandController.submit',t);self.assertNotIn('main.postDelayed(this::reload,450)',t)
 def test_all_durable_engines(self):
  for name in ('DownloadWorker.java','MangaLibStorage.java','EdgeFullBookService.java','DirectLinkDownloadService.java','RanobeLibStorage.java','RanobesStorage.java'):
   self.assertIn('DownloadChangeBus.progress(',source(name),name)
 def test_manga_batch_resume(self):self.assertIn('MangaLibStorage.putAll(ctx,changed)',source('MangaLibQueue.java'))
 def test_audio_job_queue(self):
  t=source('DownloadWorker.java');self.assertIn('waiting.addLast(requested)',t);self.assertIn('commandLane.execute',t);self.assertIn('requested.equals(source)',t)
 def test_no_java_forward_references_in_manager(self):
  t=source('UnifiedDownloadManagerActivity.java')
  self.assertLess(t.index('private final Runnable reloadDebounced'),t.index('private final DownloadChangeBus.ProgressListener chapterProgressListener'))
 def test_master_list_partial_updates(self):
  t=source('DownloadActivity.java');self.assertIn('"fast-progress"',t);self.assertIn('DownloadChangeBus.addProgress(progressListener)',t)
if __name__=='__main__':unittest.main()
