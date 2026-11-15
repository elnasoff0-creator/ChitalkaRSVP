"""v1.11.167 source-contract checks. Not Android SDK compilation or on-device UI tests."""
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/ru/reader/rsvp'
def source(name):return (SRC/name).read_text(encoding='utf8')
class V167(unittest.TestCase):
 def test_version(self):
  b=(ROOT/'app/build.gradle').read_text()
  self.assertIn('versionCode 16700',b)
  self.assertIn("versionName '1.11.167-beta'",b)
 def test_xml(self):
  files=list((ROOT/'app/src/main').rglob('*.xml'))
  for f in files:ET.parse(f)
  self.assertGreater(len(files),160)
 def test_docs_current_only(self):
  names=sorted(p.name for p in ROOT.glob('*.md') if p.name.startswith(('README','CHANGELOG')))
  self.assertEqual(names,['CHANGELOG-v1.11.167-beta.md','README-v1.11.167-beta.md'])
 def test_browser_tabs_available(self):
  t=source('BrowserTabs.java')
  for x in ('iconStrip(', 'strip(', 'panel(', 'equal?new LinearLayout.LayoutParams(0,dp(context,49),1f)', 'scroll.setFillViewport(true)', 'ViewGroup.LayoutParams.MATCH_PARENT'):
   self.assertIn(x,t)
 def test_file_info_visible_same_width(self):
  mgr=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('new String[]{"Файлы","Инфо"}',mgr)
  self.assertIn('tabContainer.addView(tabs,new LinearLayout.LayoutParams(-1,dp(52)))',mgr)
  self.assertIn('infoScroll.setVisibility(files?View.GONE:View.VISIBLE)',mgr)
  self.assertIn('tab="files"',mgr)
  self.assertIn('showInfo(selected)',mgr)
  self.assertNotIn('new String[]{"Мета","Файлы","Обложка"}',mgr)
 def test_settings_icon_tabs_and_dynamic_title(self):
  s=source('SettingsActivity.java')
  self.assertIn('BrowserTabs.iconStrip(this,names,icons,currentTab',s)
  self.assertIn('"Настройки · "+names[',s)
  self.assertIn('AppToolbar.add(this,root,',s)
  for r in ('ic_tab_appearance','ic_book','ic_tab_music','ic_tab_online','ic_chteico_nav'):self.assertIn(r,s)
  self.assertIn('settings_tab_positions',s)
  self.assertNotIn('TextView tabAction(',s)
 def test_unified_toolbar(self):
  m=source('UnifiedDownloadManagerActivity.java')
  self.assertIn('AppToolbar.add(this,root,"Менеджер")',m)
  t=source('AppToolbar.java')
  self.assertIn('HEIGHT_DP=52',t)
  self.assertIn('UiColorScheme.neutral(a)',t)
  self.assertIn('setSingleLine(true)',t)
 def test_other_real_tab_screens_reuse_shared_component(self):
  self.assertIn('BrowserTabs.strip(this,new String[]{"Замены","Игнорировать"}',source('EdgeDictionaryActivity.java'))
  self.assertIn('BrowserTabs.strip(this,new String[]{"Книги"',source('KvuheSearchActivity.java'))
  self.assertIn('contentPanel=BrowserTabs.panel(this)',source('KvuheSearchActivity.java'))
 def test_archive_swipe_theme(self):
  s=source('DownloadActivity.java')
  self.assertIn('UiColorScheme.accent(DownloadActivity.this)',s)
  self.assertIn('UiColorScheme.neutral(DownloadActivity.this)',s)
  self.assertIn('confirm.setBackground(bg(themedAccent,14))',s)
  self.assertIn('cancel.setBackground(bg(ordinary,14))',s)
  self.assertIn('ReaderButtonColors.contrastingText(themedAccent)',s)
  self.assertIn('ReaderButtonColors.contrastingText(ordinary)',s)
  self.assertIn('В архив',s)
  self.assertIn('Отмена',s)
  self.assertNotIn('0xff7260a5',s)
 def test_unframed_overflow_everywhere(self):
  self.assertIn('more.setBackground(null)',source('DownloadActivity.java'))
  self.assertIn('rename.setBackground(null)',source('DownloadArchiveActivity.java'))
  self.assertIn('overflowMenu.setBackground(null)',source('ReaderActivity.java'))
  self.assertIn('private ImageView bareMenuIcon',source('UnifiedDownloadManagerActivity.java'))
  self.assertIn('ImageView more=bareMenuIcon',source('UnifiedDownloadManagerActivity.java'))
  self.assertIn('button.setClickable(true)',source('MainActivity.java'))
 def test_rounded_dialogs(self):
  self.assertIn('setCornerRadius(22f*',source('RoundedDialogs.java'))
  self.assertIn('UiColorScheme.neutral(context)',source('RoundedDialogs.java'))
  self.assertIn('setCornerRadius(22f*density)',source('SwipeDialogs.java'))
  self.assertIn('ChitalkaAppTheme',(ROOT/'app/src/main/AndroidManifest.xml').read_text())
  styles=(ROOT/'app/src/main/res/values/sheets.xml').read_text()
  self.assertIn('android:popupMenuStyle',styles)
  self.assertIn('chitalka_popup_surface',styles)
  for f in SRC.glob('*.java'):
   if f.name in ('RoundedDialogs.java','SwipeDialogs.java'):continue
   self.assertNotRegex(f.read_text(encoding='utf8'),r'new\s+(?:android\.app\.)?AlertDialog\.Builder\(')
 def test_manager_previous_features(self):
  mgr=source('UnifiedDownloadManagerActivity.java')
  for expr in ('R.drawable.ic_autoload','TextView progressTitle=text("Общий прогресс"','control(selected)','downloadAll(selected)','fileMenu(','folderRequest(key)','DownloadFolderPicker.directoryPath(this,key)','R.drawable.ic_folder_audio','selectedRows("delete")'):
   self.assertIn(expr,mgr)
 def test_saf_categories_stable(self):
  s=source('DownloadFolderPicker.java')
  for x in ('BOOK="book_download_tree_v165"','MANGA="mangalib_search_tree"','AUDIO="audiobook_search_tree"','takePersistableUriPermission'):
   self.assertIn(x,s)
 def test_reader_was_not_broken(self):
  r=source('ReaderActivity.java')
  self.assertIn('browseToWord(at)',r)
  self.assertIn('selectionHandles',r)
  self.assertIn('showSelectionMenu()',r)
 def test_no_new_downloader_engine(self):
  for file in ('DownloadWorker.java','EdgeStreamingService.java','MangaLibDownloadWorker.java'):
   self.assertTrue((SRC/file).exists())
if __name__=='__main__':unittest.main(verbosity=2)
