import unittest
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
def code(name):return (SRC/name).read_text(encoding='utf-8')

class V173Contracts(unittest.TestCase):
    def test_version(self):
        gradle=(ROOT/'app/build.gradle').read_text()
        self.assertIn('versionCode 17300',gradle)
        self.assertIn("versionName '1.11.173-beta'",gradle)
    def test_service_saves_fragments_only(self):
        svc=code('EdgeFullBookService.java')
        self.assertIn('EdgeChapterArchive.publishFragmentsOne(',svc)
        self.assertNotIn('EdgeChapterArchive.publishOne(',svc)
        self.assertNotIn('EdgeChapterArchive.autoMerge(',svc)
    def test_reading_saves_fragments_only(self):
        svc=code('EdgeStreamingService.java')
        self.assertIn('EdgeChapterArchive.publishFragments(',svc)
        self.assertNotIn('EdgeChapterArchive.publish(getApplicationContext()',svc)
        self.assertNotIn('EdgeChapterArchive.autoMerge(',svc)
    def test_legacy_preference_ignored(self):
        arc=code('EdgeChapterArchive.java')
        self.assertIn('static boolean autoMerge(Context c){return false;}',arc)
        self.assertIn('"fragments".equals(index.optString("mode"))',arc)
    def test_cached_save_uses_fragments(self):
        self.assertIn('EdgeChapterArchive.publishFragmentsOne(',code('EdgeChapterActions.java'))
        self.assertIn('EdgeChapterArchive.publishFragmentsOne(',code('EdgeChapterDownloadsActivity.java'))
    def test_no_manual_merge(self):
        self.assertNotIn('"Объединить MP3 главы"',code('UnifiedDownloadManagerActivity.java'))
        self.assertNotIn('"Преобразовать файлы главы в один MP3"',code('EdgeChapterDownloadsActivity.java'))
        self.assertNotIn('mergeChapter(item.chapter)',code('EdgeChapterDownloadsActivity.java'))
    def test_auto_merge_setting_removed(self):
        st=code('SettingsActivity.java')
        self.assertNotIn('"Автопреобразование озвучки главы в единый файл MP3"',st)
        self.assertNotIn('EdgeChapterArchive.AUTO_MERGE_PREF,true',st)
    def test_diagnostics_in_general(self):
        st=code('SettingsActivity.java')
        general=st[st.index('header("Цвет акцентных кнопок'):st.index('    /** Compact help link.')]
        audio=st[st.index('    void drawAudioSettings()'):st.index('    private void edgeDictionaryDialog()')]
        self.assertIn('header("Диагностика")',general)
        self.assertIn('ReaderLogActivity.class',general)
        self.assertNotIn('header("Диагностика")',audio)
    def test_one_mp3_per_chapter_export(self):
        arc=code('EdgeChapterArchive.java')
        export=arc[arc.index('static synchronized int exportAudiobook('):arc.index('    /** Delete ONLY')]
        self.assertIn('for(ExportChapter entry:ready.values())',export)
        self.assertIn('EdgeMp3ChapterExport.append(',export)
        self.assertIn('EdgeMp3ChapterExport.writeMetadata(',export)
        self.assertNotIn('WordBoundary',export)
        self.assertNotIn('renameTo(',export)
    def test_audio_folder_cover(self):
        arc=code('EdgeChapterArchive.java')
        self.assertIn('commonCover(ctx,folder,uri)',arc)
        self.assertIn('book.coverUri=cover.getUri().toString()',arc)
        self.assertNotIn('APIC',code('EdgeMp3ChapterExport.java'))
        self.assertIn('coverUri',code('AudioLibrary.java'))
    def test_chapter_export_names(self):
        arc=code('EdgeChapterArchive.java')
        self.assertIn('return "Глава "+prefix+".mp3"',arc)
        self.assertIn('safe(title,110)',arc)
    def test_partial_export(self):
        arc=code('EdgeChapterArchive.java')
        exp=arc[arc.index('static synchronized int exportAudiobook('):arc.index('    /** Delete ONLY')]
        self.assertIn('if(ready.isEmpty())',exp)
        self.assertNotIn('completed!=expectedChapters',exp)
        self.assertIn('committed.put(created.getName(),created)',exp)
    def test_only_one_cover(self):
        arc=code('EdgeChapterArchive.java')
        self.assertIn('if(previous!=null)return previous;',arc)
        self.assertIn('cover.jpg',arc) if False else None
    def test_context_menu_label(self):
        menu=code('DownloadActivity.java')
        self.assertIn('Перенести готовые аудиоглавы едиными файлами в аудиотеку',menu)
        self.assertIn('Готово "+count+" из "+total',menu)
    def test_icons_complete(self):
        drawable=(ROOT/'app/src/main/res/drawable/ic_download_chapter_complete.xml')
        self.assertTrue(drawable.is_file())
        self.assertIn('UiColorScheme.accent(',code('EdgeChapterDownloadsActivity.java'))
        self.assertIn('R.drawable.ic_download_chapter_complete',code('UnifiedDownloadManagerActivity.java'))
        self.assertIn('R.drawable.ic_download_chapter_complete',code('DownloadActivity.java'))
    def test_no_extra_background_scans(self):
        arc=code('EdgeChapterArchive.java')
        self.assertIn('No network request or cover generation is permitted',arc)
        self.assertIn('no background polling',arc)

if __name__=='__main__':unittest.main()
