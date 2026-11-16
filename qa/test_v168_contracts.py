"""v1.11.168 release-contract tests; source checks, not Android SDK/on-device verification."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re, unittest
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
def source(n):return (SRC/n).read_text(encoding='utf-8')
class Release168(unittest.TestCase):
 def test_identity(self):
  gradle=(ROOT/'app/build.gradle').read_text()
  self.assertIn('versionCode 17100',gradle)
  self.assertIn("versionName '1.11.171-beta'",gradle)
 def test_xml(self):
  for f in (ROOT/'app/src/main').rglob('*.xml'):ET.parse(f)
 def test_no_periodic_full_scanning(self):
  d=source('DownloadActivity.java');m=source('UnifiedDownloadManagerActivity.java')
  self.assertNotIn('postDelayed(this,2600)',d)
  self.assertNotIn('now-diskScannedAt<7000L',d)
  self.assertNotIn('postDelayed(this,4200)',m)
  self.assertIn('DownloadChangeBus.add(downloadListener)',d)
  self.assertIn('DownloadChangeBus.add(downloadListener)',m)
  self.assertIn('DownloadChangeBus.remove(downloadListener)',d)
  self.assertIn('DownloadChangeBus.remove(downloadListener)',m)
 def test_selected_lookup_without_global_scan(self):
  r=source('DownloadsRepository.java')
  self.assertIn('static Item loadOne(Context ctx,String id)',r)
  d=source('DownloadActivity.java');m=source('UnifiedDownloadManagerActivity.java')
  x=d[d.index('private void resolvePreview('):d.index('private void openNovelIfAvailable(')]
  self.assertIn('DownloadsRepository.loadOne',x)
  self.assertNotIn('DownloadsRepository.load(getApplicationContext(),null)',x)
  x=m[m.index('private void reload()'):m.index('private void readRows(')]
  self.assertIn('DownloadsRepository.loadOne',x)
  self.assertNotIn('DownloadsRepository.load(getApplicationContext(),null)',x)
 def test_overflow_open_without_prior_verification(self):
  d=source('DownloadActivity.java')
  s=d[d.index('private void menu(View anchor,'):d.index('private void transferEdgeAudiobook(')]
  self.assertNotIn('if(item.previewOnly){resolvePreview',s)
  self.assertNotIn('RanobesStorage.hasBook(this',s)
  self.assertNotIn('RanobeLibStorage.stored(this',s)
 def test_events_produced_by_all_job_stores(self):
  for name,identity in [('BookImportManager.java','"audio:"+job.source'),
      ('LinkDownloadStore.java','"link:"+b.id'),('MangaLibStorage.java','"manga:"+job.slug'),
      ('RanobeLibStorage.java','"ranobe:"+job.slug'),('RanobesStorage.java','"ranobes:"+j.sourceKey()'),
      ('EdgeFullBookStore.java','"edge:"+j.uri')]:
   self.assertIn('DownloadChangeBus.publish(',source(name),name)
   self.assertIn(identity,source(name),name)
 def test_cached_manager_and_reactive_rows(self):
  m=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('DownloadRowsSnapshot.load',m)
  self.assertIn('DownloadRowsSnapshot.save',m)
  self.assertIn('notifyItemChanged(i)',m)
  self.assertIn('edgeTocCache',m)
  self.assertIn('previous.bytes',m)
  self.assertNotIn('if(row.source==null)',m)
 def test_colors_and_two_line_path(self):
  m=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('UiColorScheme.background(this)',m)
  self.assertIn('UiColorScheme.foreground(this)',m)
  self.assertIn('UiColorScheme.tintProgress',m)
  self.assertIn('TextView caption=text("Файлы",12,false)',m)
  self.assertIn('caption.setTextColor(muted)',m)
  self.assertIn('path.setMaxLines(2)',m)
  self.assertIn('fitPathToTwoLines',m)
  self.assertIn('R.drawable.ic_autoload',m)
  self.assertIn('actionIcon(R.drawable.ic_folder_audio',m)
 def test_popup_honors_runtime_palette(self):
  t=source('PalettePopupMenu.java')
  self.assertIn('UiColorScheme.neutral(context)',t)
  self.assertIn('UiColorScheme.foreground(context)',t)
  for file in ['DownloadActivity.java','UnifiedDownloadManagerActivity.java','DownloadArchiveActivity.java']:
   self.assertIn('new PalettePopupMenu(',source(file),file)
   self.assertNotIn('new PopupMenu(',source(file),file)
 def test_explicit_manual_reconciliation(self):
  d=source('DownloadActivity.java')
  self.assertIn('"Проверить загрузки"',d)
  self.assertIn('"Полностью сверить задания',d)
 def test_gradle_compile_hotfix_forward_refs_and_palette(self):
  manager=source('UnifiedDownloadManagerActivity.java')
  edge=source('EdgeChapterDownloadsActivity.java')
  self.assertLess(manager.index('private final Runnable reloadDebounced'),
                  manager.index('private final DownloadChangeBus.Listener downloadListener'))
  self.assertIn('private int stateColor(Entry item)',edge)
  self.assertNotIn('private static int stateColor(Entry item)',edge)
 def test_source_version_notes(self):
  for path in ['README-v1.11.171-beta.md','CHANGELOG-v1.11.171-beta.md']:
   self.assertTrue((ROOT/path).exists(),path)
if __name__=='__main__':unittest.main(verbosity=2)
