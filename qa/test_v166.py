"""Release v1.11.166 source contracts. This is not an Android-device test or Gradle compilation."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
def source(name):return (SRC/name).read_text(encoding='utf8')

class V166(unittest.TestCase):
 def test_release_identity(self):
  gradle=(ROOT/'app/build.gradle').read_text()
  self.assertIn('versionCode 16600',gradle)
  self.assertIn("versionName '1.11.166-beta'",gradle)
 def test_xml_well_formed(self):
  resources=list((ROOT/'app/src/main').rglob('*.xml'))
  self.assertGreater(len(resources),160)
  for resource in resources:ET.parse(resource)
 def test_one_tab_style_reused(self):
  self.assertIn('BrowserTabs.strip(this',source('SettingsActivity.java'))
  self.assertIn('BrowserTabs.strip(this',source('UnifiedDownloadManagerActivity.java'))
  self.assertIn('top(context,chosen?panel:passive,14)',source('BrowserTabs.java'))
  self.assertIn('BrowserTabs.panel(this)',source('UnifiedDownloadManagerActivity.java'))
 def test_tabs_are_files_info_only(self):
  text=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('new String[]{"Файлы","Инфо"}',text)
  self.assertIn('String id="",hintTitle="",hintCover="",hintType="",tab="files"',text)
  self.assertNotIn('new String[]{"Мета","Файлы","Обложка"}',text)
 def test_manager_title_navigation(self):
  text=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('TextView heading=text("Менеджер",',text)
  self.assertNotIn('button("Назад"',text)
  self.assertIn('UniversalDrawer.show(this)',text)
 def test_progress_controls(self):
  text=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('R.drawable.ic_play',text)
  self.assertIn('R.drawable.ic_pause',text)
  self.assertIn('R.drawable.ic_autoload',text)
  self.assertIn('TextView progressTitle=text("Общий прогресс"',text)
  self.assertLess(text.index('progressLine.addView(playPause'),text.index('progressTitle'))
  self.assertLess(text.index('percent=text('),text.index('allIcon=actionIcon'))
 def test_no_old_details_launcher(self):
  text=source('UnifiedDownloadManagerActivity.java')
  for legacy in ('EdgeChapterDownloadsActivity.class','MangaLibChapterDownloadsActivity.class','LinkDownloadDetailsActivity.class'):
   self.assertNotIn(legacy,text)
  self.assertIn('class FileAdapter',text)
  self.assertIn('fileMenu(',text)
  self.assertIn('selectedRows(',text)
 def test_edge_all_chapters_present(self):
  text=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('edgeChapters(item)',text)
  self.assertIn('BookLoader.load(this,Uri.parse(key))',text)
  self.assertIn('edgeTocCache.put(key,result)',text)
  self.assertIn('EdgeChapterActions.saveCached(',text)
  self.assertIn('EdgeChapterActions.merge(',text)
  self.assertIn('EdgeChapterArchive.snapshot(',text)
 def test_separate_download_all(self):
  text=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('private void control(',text)
  self.assertIn('private void downloadAll(',text)
  for expr in ('MangaLibQueue.enqueue(', 'RanobeLibQueue.enqueue(', 'RanobesQueue.enqueue(', 'DownloadWorker.START', 'EdgeFullBookService.ADD'):
   self.assertIn(expr,text)
 def test_folder_path_only_with_last_part_visible(self):
  text=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('DownloadFolderPicker.directoryPath(this,key)',text)
  self.assertIn('TextUtils.TruncateAt.START',text)
  self.assertIn('display.setSingleLine(true)',text)
  self.assertIn('R.drawable.ic_folder_audio',text)
  self.assertIn('folderRequest(key)',text)
  self.assertNotIn('Временный кэш:',text)
  self.assertNotIn('Папка SAF:',text)
 def test_correct_separate_saf_permissions(self):
  picker=source('DownloadFolderPicker.java')
  for s in ('BOOK="book_download_tree_v165"','RANOBE=BOOK','MANGA="mangalib_search_tree"', 'AUDIO="audiobook_search_tree"', 'takePersistableUriPermission','getTreeDocumentId','verifyWritable'):
   self.assertIn(s,picker)
  manager=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('DownloadsRepository.AUDIO.equals(item.type))return DownloadFolderPicker.AUDIO',manager)
  self.assertIn('DownloadsRepository.MANGA.equals(item.type))return DownloadFolderPicker.MANGA',manager)
  self.assertIn('DownloadsRepository.EDGE.equals(item.type)||DownloadsRepository.RANOBE.equals(item.type)',manager)
 def test_saf_path_not_full_filename(self):
  folder=source('DownloadFolderPicker.java')
  display=folder.split('static String directoryPath(',1)[1].split('static void choose(',1)[0]
  self.assertIn('getTreeDocumentId',display)
  self.assertIn('folders',display)
  self.assertNotIn('file.getName',display)
 def test_verified_duplicate_cleanup(self):
  cleanup=source('AudioSafDuplicates.java')
  for v in ('SHA-256','matches(Context ctx,File local,Uri saved)','libraryReferences(', 'inspect(Context ctx,boolean remove)', 'if(remove)', 'local.delete()'):
   self.assertIn(v,cleanup)
  worker=source('DownloadWorker.java')
  self.assertIn('AudioSafDuplicates.matches(',worker)
  self.assertIn('AudioSafDuplicates.libraryReferences(',worker)
  self.assertIn('track.delete()',worker)
 def test_manual_cleanup_requires_confirmation(self):
  settings=source('SettingsActivity.java')
  self.assertIn('Проверить и очистить дубликаты загрузок',settings)
  self.assertIn('AudioSafDuplicates.inspect(',settings)
  self.assertIn('setPositiveButton("Удалить',settings)
 def test_saf_audio_library_uri(self):
  worker=source('DownloadWorker.java')
  self.assertIn('job.relocatedUris.set(index,saved.toString())',worker)
  self.assertLess(worker.index('job.relocatedUris.set(index,saved.toString())'),worker.index('track.delete()'))
  self.assertIn('BookImportManager.updateLibrary(this,job)',worker)
 def test_swipe_actions_static_and_measured(self):
  download=source('DownloadActivity.java')
  self.assertIn('back.getWidth()',download)
  self.assertIn('front.setTranslationX(distance)',download)
  self.assertIn('adapter.notifyItemChanged(p)',download)
  self.assertIn('item.id.equals(revealedSwipeId)',download)
  self.assertIn('В архив',download)
  self.assertIn('Отмена',download)
 def test_reader_selection_icon_only(self):
  reader=source('ReaderActivity.java')
  self.assertIn('setContentDescription(names[i])',reader)
  self.assertIn('setTooltipText(names[i])',reader)
  self.assertIn('showSelectionMenu()',reader)
 def test_note_gutter_alignment(self):
  margin=source('ReaderMarginMarksTextView.java')
  self.assertIn('getFontMetrics()',margin)
  self.assertIn('layout.getLineBaseline(line)',margin)
  self.assertIn('18*density',margin)
  self.assertIn('getTotalPaddingLeft()',margin)
 def test_tts_note_navigation_does_not_seek(self):
  reader=source('ReaderActivity.java')
  handler=reader.split('onActivityResult(int req,int result,Intent data)',1)[1].split('private void ',1)[0]
  self.assertIn('browseToWord(at)',handler)
  self.assertNotIn('selectTtsWord(at)',handler)
 def test_only_one_source_skips_picker(self):
  picker=source('DownloadSourcePicker.java')
  self.assertIn('if(sources.length==1)',picker)
  self.assertIn('activity.startActivity(',picker)
 def test_verified_manga_archive_cleanup(self):
  worker=source('MangaLibDownloadWorker.java')
  self.assertIn('releaseVerifiedOutput(ctx,output,job)',worker)
  self.assertIn('AudioSafDuplicates.matches(ctx,output,android.net.Uri.parse(job.file))',worker)
  self.assertIn('MangaLibStorage.find(ctx,job.id())',worker)
 def test_inline_direct_link_functions(self):
  manager=source('UnifiedDownloadManagerActivity.java')
  for feature in ('LinkDownloadStore.addToLibrary(this,current)', 'Открыть папку файлов',
                  'Отменить оставшиеся загрузки', 'openFinishedFile(FileRow row)'):
   self.assertIn(feature,manager)
 def test_old_release_docs_cleanup(self):
  documents=sorted(p.name for p in ROOT.glob('*.md') if 'README' in p.name or 'CHANGELOG' in p.name)
  self.assertEqual(documents,['CHANGELOG-v1.11.166-beta.md','README-v1.11.166-beta.md'])

if __name__=='__main__': unittest.main(verbosity=2)
