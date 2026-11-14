"""Release 1.11.165 regression contracts. Syntax/manifest checks run independently."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
CODE=ROOT/'app/src/main/java/ru/reader/rsvp'
def source(name):return (CODE/name).read_text(encoding='utf-8')

class Release165(unittest.TestCase):
    def test_release_identity(self):
        gradle=(ROOT/'app/build.gradle').read_text()
        self.assertIn('versionCode 16500',gradle)
        self.assertIn("versionName '1.11.165-beta'",gradle)
    def test_one_release_docs_only(self):
        docs=sorted(p.name for p in ROOT.iterdir() if p.is_file() and ('CHANGELOG' in p.name or 'README' in p.name))
        self.assertEqual(docs,['CHANGELOG-v1.11.165-beta.md','README-v1.11.165-beta.md'])
    def test_xml_valid(self):
        xs=list((ROOT/'app/src/main').rglob('*.xml'))
        self.assertGreater(len(xs),160)
        for file in xs:ET.parse(file)
    def test_three_independent_persistent_trees(self):
        picker=source('DownloadFolderPicker.java')
        self.assertIn('ACTION_OPEN_DOCUMENT_TREE',picker)
        self.assertIn('FLAG_GRANT_PERSISTABLE_URI_PERMISSION',picker)
        self.assertIn('takePersistableUriPermission',picker)
        self.assertIn('verifyWritable',picker)
        self.assertIn('openOutputStream',picker)
        self.assertIn('openInputStream',picker)
        self.assertIn('BOOK="book_download_tree_v165"',picker)
        self.assertIn('RANOBE=BOOK',picker)
        self.assertIn('MANGA="mangalib_search_tree"',picker)
        self.assertIn('AUDIO="audiobook_search_tree"',picker)
        self.assertNotIn('root_download_tree',picker)
    def test_three_sources_in_settings_and_managers(self):
        settings=source('SettingsActivity.java')
        self.assertIn('drawOnlineSettings',settings)
        for key in ('DownloadFolderPicker.BOOK_REQUEST','DownloadFolderPicker.MANGA_REQUEST','DownloadFolderPicker.AUDIO_REQUEST'):
            self.assertIn(key,settings)
            self.assertIn(key,source('UnifiedDownloadManagerActivity.java'))
        self.assertIn('CategorySafRecovery.restore',settings)
        self.assertIn('Папка SAF:',source('UnifiedDownloadManagerActivity.java'))
    def test_edge_uses_book_folder(self):
        edge=source('EdgeFolderPicker.java')
        self.assertIn('DownloadFolderPicker.BOOK',edge)
        self.assertIn('EdgeChapterArchive.invalidate()',edge)
        self.assertIn('edge_audio_folder_uri_v150',source('DownloadFolderPicker.java'))
    def test_left_margin_marks_not_clipped(self):
        reader=source('ReaderActivity.java')
        icons=source('ReaderMarginMarksTextView.java')
        self.assertIn('ReaderMarginMarksTextView item=new ReaderMarginMarksTextView',reader)
        self.assertIn('setIndicators(notes,marks,UiColorScheme.accent(this))',reader)
        self.assertIn('item.setPadding(dp(24),dp(7),dp(25),dp(7))',reader)
        self.assertIn('canvas.clipRect(0,0,getTotalPaddingLeft(),getHeight())',icons)
        self.assertIn('getTotalPaddingTop()+layout.getLineTop(line)',icons)
        self.assertIn('markSize=Math.round(18*density)',icons)
        self.assertIn('ic_reader_selection_note',icons)
        self.assertIn('ic_reader_bookmark_side',icons)
        self.assertNotIn('holder.span(new ReaderNoteIndicatorSpan(',reader)
    def test_existing_note_never_activates_handles(self):
        reader=source('ReaderActivity.java')
        select=reader.split('private void openAnnotationById(long id)',1)[1].split('private void editExistingNote(',1)[0]
        self.assertIn('clearSelection();',select)
        self.assertIn('editExistingNote(annotation);',select)
        self.assertNotIn('selectionFrom=',select)
        self.assertNotIn('selectionTo=',select)
        self.assertIn('setOnDismissListener',reader)
    def test_bookmark_range_persists(self):
        text=source('ReaderBookmarks.java')
        self.assertIn('put("endWord",endWord)',text)
        self.assertIn('Math.max(b.word+1,o.optInt("endWord",b.word+1))',text)
    def test_workers_export_canonical_uris(self):
        self.assertIn('job.relocatedUris.set(index,saved.toString())',source('DownloadWorker.java'))
        self.assertIn('DownloadFolderPicker.AUDIO',source('DownloadWorker.java'))
        self.assertIn('DownloadFolderPicker.MANGA',source('MangaLibDownloadWorker.java'))
        self.assertIn('exportToSelectedFolder',source('MangaLibDownloadWorker.java'))
        self.assertIn('DownloadFolderPicker.BOOK',source('RanobesStorage.java'))
        self.assertIn('DownloadFolderPicker.BOOK',source('RanobeLibStorage.java'))
    def test_imported_files_use_book(self):
        picker=source('DownloadFolderPicker.java')
        main=source('MainActivity.java')
        self.assertIn('importDocument(Context context,String key,Uri original)',picker)
        self.assertIn('pendingBookImport',main)
        self.assertIn('pending_saf_book_import',main)
        self.assertIn('saveImportedBook(selected)',main)
        self.assertIn('publishUnderBook',source('HtmlFolderLibrary.java'))
    def test_recovery_does_not_delete_originals(self):
        recovery=source('CategorySafRecovery.java')
        for key in ('private static void audio','private static void manga','private static void ranobes','private static void ranobeLib','private static void edge'):
            self.assertIn(key,recovery)
        self.assertIn('copyDocumentTree',recovery)
        self.assertIn('CBZ неполный',recovery)
        self.assertNotIn('removeCopiedFiles',recovery)
        self.assertIn('Файл не появился в выбранной папке Book',recovery)
    def test_ranobes_last_published_survives_folder_switch(self):
        text=source('RanobesStorage.java')
        self.assertIn('ranobes_published_v165',text)
        self.assertIn('previousPublished',text)
        self.assertIn('String previous=bookUri(c,key);',text)
        self.assertIn('putString(key,published).commit()',text)
    def test_ranobes_pagination_and_search_preserved(self):
        src=source('RanobesApi.java')
        for token in ('window.__DATA__','tocPage(','loadToc(','new FormBody.Builder()', 'status==307'):
            self.assertIn(token,src)
    def test_selection_handles_unchanged(self):
        src=source('ReaderSelectionController.java')
        self.assertIn('autoScrollForHandle',src)
        self.assertIn('findPointerIndex',src)
        self.assertIn('scrollFrame',src)

if __name__=='__main__':unittest.main(verbosity=2)
