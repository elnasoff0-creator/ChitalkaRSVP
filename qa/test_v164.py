from pathlib import Path
import unittest, re, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/reader/rsvp'

def read(filename):return (JAVA/filename).read_text(encoding='utf-8')

class Release164(unittest.TestCase):
    def test_version(self):
        content=(ROOT/'app/build.gradle').read_text()
        self.assertRegex(content,r'versionCode\s+16400')
        self.assertRegex(content,r"versionName\s+['\"]1\.11\.164-beta")
    def test_no_previous_readmes(self):
        self.assertFalse(list(ROOT.rglob('README-v15*.md')))
        self.assertFalse(list(ROOT.rglob('README-v16[0-3]*.md')))
        self.assertFalse(list(ROOT.rglob('CHANGELOG-v15*.md')))
        self.assertFalse(list(ROOT.rglob('CHANGELOG-v16[0-3]*.md')))
    def test_android_xml(self):
        for xml in (ROOT/'app/src/main').rglob('*.xml'):
            ET.parse(xml)
    def test_ranobes_form_redirect(self):
        src=read('RanobesApi.java')
        for phrase in ('new FormBody.Builder()','add("catlist[]","1")','add("story",query.trim())',
            'add("do","search")','add("subaction","search")','status==307','status==308',
            'response.request().url().resolve(loc)','allowed(destination)'):
            self.assertIn(phrase,src)
        self.assertNotIn('addQueryParameter("story",query.trim())',src)
    def test_ranobes_pagination(self):
        src=read('RanobesApi.java')
        for phrase in ('window.__DATA__','pages_count','count_all','chapters','tocPage(','loadToc(',
                       'Collections.reverse(ordered)','/chapters/','/page/'):
            self.assertIn(phrase,src)
        ui=read('RanobesBookActivity.java')
        for phrase in ('Больше глав','renderPagedToc','chooseTocPage','Стр. ','Выбрать главы этой страницы'):
            self.assertIn(phrase,ui)
    def test_top_cards_picker(self):
        src=read('DownloadSourcePicker.java')
        self.assertIn('.fromTop()',src)
        self.assertIn('params.gravity=Gravity.TOP',src)
        self.assertIn('setCornerRadius',src)
        for source in ('RanobeLIB','Ranobes.net','MangaLib','Книга в ухе'):self.assertIn(source,src)
    def test_bookmark_sentence_markers(self):
        src=read('ReaderActivity.java')
        self.assertIn('ReaderBookmarkSentences.choose',src)
        self.assertIn('ReaderBookmarks.list',src)
        self.assertIn('item.setPadding(dp(24),dp(7),dp(25),dp(7))',src)
        self.assertIn('UiColorScheme.accent(this),notes,marks',src)
        self.assertIn('bookmarksAtLine(',src)
        span=read('ReaderNoteIndicatorSpan.java')
        for phrase in ('ic_reader_bookmark_side','ic_reader_selection_note','PorterDuff.Mode.SRC_IN','left-Math.round(22*density)'):
            self.assertIn(phrase,span)
        vector=(ROOT/'app/src/main/res/drawable/ic_reader_bookmark_side.xml').read_text()
        self.assertIn('fillColor',vector)
        self.assertIn('viewportWidth="24"',vector)
    def test_handles_scroll_and_swap(self):
        src=read('ReaderSelectionController.java')
        for phrase in ('pointerId','findPointerIndex','scrollFrame','requestDisallowInterceptTouchEvent','autoScrollForHandle'):
            self.assertIn(phrase,src)
        main=read('ReaderActivity.java')
        for phrase in ('selectionHandles.invalidate();','autoScrollSelection(','selectionFrom=selectionTo;selectionTo=word;start=false',
            'selectionTo=selectionFrom;selectionFrom=word;start=true','selectionMenuAfterScroll'):
            self.assertIn(phrase,main)

if __name__=='__main__':unittest.main(verbosity=2)
