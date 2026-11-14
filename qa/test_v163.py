from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/reader/rsvp'

def text(filename):return (JAVA/filename).read_text(encoding='utf-8')

class Version163Tests(unittest.TestCase):
    def test_java_xml_sources(self):
        for p in (ROOT/'app/src/main').rglob('*.xml'):ET.parse(p)
        self.assertGreaterEqual(len(list(JAVA.glob('*.java'))),175)
    def test_version(self):
        s=(ROOT/'app/build.gradle').read_text()
        self.assertRegex(s,r'versionCode\s+16[34]00')
        self.assertRegex(s,r'versionName\s+[\'\"]1\.11\.16[34]-beta')
    def test_one_button_provider_dialog(self):
        s=text('DownloadSourcePicker.java')
        for source in ('RanobeLIB','Ranobes.net','MangaLib','Книга в ухе'):self.assertIn(source,s)
        screen=text('DownloadActivity.java')
        self.assertIn('DownloadSourcePicker.show(this,DownloadSourcePicker.RANOBE)',screen)
        self.assertIn('DownloadSourcePicker.show(this,DownloadSourcePicker.MANGA)',screen)
        self.assertIn('DownloadSourcePicker.show(this,DownloadSourcePicker.AUDIO)',screen)
    def test_shared_category_folder(self):
        s=text('DownloadFolderPicker.java')
        self.assertIn('if(p.contains("ranobe"))return RANOBE;',s)
        self.assertIn('RANOBE="ranobelib_search_tree"',s)
        for module in ('RanobesBookActivity.java','RanobesDownloadWorker.java','RanobesStorage.java'):
            self.assertIn('DownloadFolderPicker.RANOBE',text(module))
    def test_new_pages_manifest(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        for page in ('RanobesSearchActivity','RanobesBookActivity','UnifiedDownloadManagerActivity'):
            self.assertIn('.'+page,manifest)
    def test_audio_opens_manager_not_player_from_download_row(self):
        s=text('DownloadActivity.java');p=s.index('private void open(DownloadsRepository.Item item)');e=s.index('private void openMangaChapters',p)
        self.assertIn('UnifiedDownloadManagerActivity.class',s[p:e])
        self.assertNotIn('AudioPlayerActivity.class',s[p:e])
        n=text('DownloadWorker.java');self.assertIn('UnifiedDownloadManagerActivity.class',n)
    def test_unified_manager_has_metadata_files_cover(self):
        s=text('UnifiedDownloadManagerActivity.java')
        for label in ('"Мета"','"Файлы"','"Обложка"','showFiles()','showMeta(item)','showCover(item)','author','narrator','format'):
            self.assertIn(label,s)
        self.assertIn('AudioPlayerActivity.class',s)
        self.assertIn('"Слушать"',s)
    def test_ranobes_search_download_translation(self):
        s=text('RanobesBookActivity.java')
        for label in ('Скачать всю доступную книгу','Скачать том','Выбрать главы','Скачать выбранные','Машин','ML Kit'):
            self.assertTrue(label.lower() in s.lower(),label)
        self.assertIn('com.google.mlkit:translate:17.0.3',(ROOT/'app/build.gradle').read_text())
        api=text('RanobesApi.java')
        self.assertIn('parseSearch',api);self.assertIn('parseChapters',api);self.assertIn('extractChapterHtml',api)
        self.assertIn('HTTP 429',api) if 'HTTP 429' in api else self.assertIn('status==429',api)
        self.assertIn('Не сохраняем меню сайта',api)
    def test_local_cover_and_book_export(self):
        storage=text('RanobesStorage.java');worker=text('RanobesDownloadWorker.java')
        self.assertIn('cover.jpg',storage);self.assertIn('book.json',storage);self.assertIn('book.html',storage)
        self.assertIn('DownloadFolderPicker.export',storage);self.assertIn('DownloadFolderPicker.export',worker)
        self.assertIn('RanobesStorage.localCover',text('CoverLoader.java'))
        self.assertIn('"content".equalsIgnoreCase',text('CoverImageLoader.java'))
    def test_books_and_old_features_still_present(self):
        for module in ('RanobeLibSearchActivity.java','MangaLibSearchActivity.java','KvuheSearchActivity.java','ReaderAnnotationsStore.java','EdgeDictionaryRuleDialog.java','HtmlFolderLibrary.java','ReaderBookCache.java'):
            self.assertTrue((JAVA/module).exists(),module)
        self.assertIn('filter==6&&!DownloadsRepository.RANOBE.equals(item.type)&&!DownloadsRepository.RANOBES.equals(item.type)',text('DownloadActivity.java'))
    def test_downloader_rows_work_with_audio_manga_ranobe(self):
        s=text('DownloadsRepository.java')
        for module in ('RANOBES="ranobes"','fromAudio(ctx','RanobesStorage.jobs(ctx)','RanobeLibStorage.jobs(ctx)','MangaLibStorage.jobs(ctx)'):
            self.assertIn(module,s)
        self.assertIn('item.author',text('DownloadActivity.java'))

if __name__=='__main__':unittest.main(verbosity=2)
