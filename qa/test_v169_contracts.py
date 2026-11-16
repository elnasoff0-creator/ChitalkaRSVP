"""v1.11.169 lightweight regression checks; not Android SDK/Gradle compilation."""
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
def source(file):return (SRC/file).read_text(encoding='utf-8')
class Release169(unittest.TestCase):
    def test_version_identity(self):
        gradle=(ROOT/'app/build.gradle').read_text()
        self.assertIn('versionCode 17200',gradle)
        self.assertIn("versionName '1.11.172-beta'",gradle)
    def test_manual_scan_only_inside_all_downloads_dropdown(self):
        downloads=source('DownloadActivity.java')
        self.assertIn('menu.getMenu().add(1,103,97,"Проверить загрузки")',downloads)
        self.assertIn('confirmFullScan()',downloads)
        self.assertNotIn('toolbarAction(toolbar,R.drawable.ic_refresh_download',downloads)
        self.assertIn('toolbar.addOnLayoutChangeListener',downloads)
        self.assertIn('available/slot-1',downloads)
        self.assertIn('R.drawable.ic_more_vertical,"Дополнительные действия"',downloads)
    def test_button_palette_no_accent_outline(self):
        manager=source('UnifiedDownloadManagerActivity.java')
        icon=manager[manager.index('private ImageView actionIcon('):manager.index('private ImageView bareMenuIcon(')]
        self.assertIn('ReaderButtonColors.neutral(this,panel)',icon)
        self.assertIn('ReaderButtonColors.contrastingText(fill)',icon)
        self.assertIn('rounded(fill,12)',icon)
        self.assertNotIn('outline()',manager)
        self.assertNotIn('setColorFilter(accent)',icon)
        reader=source('ReaderActivity.java')
        start=reader.index('ImageButton overflowMenu=icon(R.drawable.ic_more_vertical')
        self.assertNotIn('overflowMenu.setBackground(null)',reader[start:start+1000])
        self.assertNotIn('overflowMenu.setColorFilter(fg)',reader[start:start+1000])
    def test_persisted_info_and_edge_toc(self):
        manager=source('UnifiedDownloadManagerActivity.java')
        info=source('DownloadInfoSnapshot.java')
        toc=source('EdgeTocSnapshot.java')
        self.assertIn('currentInfo=DownloadInfoSnapshot.load(this,id)',manager)
        self.assertIn('snapshot.info=buildInfo(snapshot.item)',manager)
        self.assertIn('DownloadInfoSnapshot.save(getApplicationContext(),snapshot.info)',manager)
        self.assertIn('if(!files&&currentInfo!=null)showInfo(currentInfo)',manager)
        render=manager[manager.index('private void showInfo(DownloadInfoSnapshot.Entry'):manager.index('@Override protected void onActivityResult')]
        self.assertNotIn('destinationPath(',render)
        self.assertNotIn('EdgeChapterArchive.snapshot(',render)
        self.assertIn('EdgeTocSnapshot.load(getApplicationContext(),key)',manager)
        self.assertIn('EdgeTocSnapshot.save(getApplicationContext(),key,result)',manager)
        self.assertIn('SharedPreferences',info)
        self.assertIn('SHA-256',toc)
    def test_docs_only_for_current_release(self):
        self.assertTrue((ROOT/'CHANGELOG-v1.11.172-beta.md').is_file())
        self.assertTrue((ROOT/'README-v1.11.172-beta.md').is_file())
        self.assertTrue((ROOT/'QA-REPORT-v1.11.172-beta.md').is_file())
        for path in ROOT.rglob('*'):
            if path.is_file() and path.name.startswith(('CHANGELOG-v','README-v','QA-REPORT-v')):
                self.assertIn('v1.11.172-beta',path.name)
if __name__=='__main__':unittest.main(verbosity=2)
