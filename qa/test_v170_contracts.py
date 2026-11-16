"""Offline source contracts for v1.11.170; not an Android build or live Ranobes request."""
from pathlib import Path
from html.parser import HTMLParser
import re
import unittest
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/ru/reader/rsvp'
FILE=ROOT/'qa/fixtures/ranobes_filter_all_the_skills.html'
def src(name):return (JAVA/name).read_text(encoding='utf-8')
class FixtureParser(HTMLParser):
    def __init__(self):
        super().__init__();self.depth=0;self.results=[];self.text=[];self.result=None;self.in_title=False;self.in_desc=False;self.cover=False
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='article' and 'shortstory' in a.get('class','').split():
            self.result={'id':'','title':'','cover':'','description':''}
            self.results.append(self.result)
        if self.result is None:return
        if tag=='h2' and 'title' in a.get('class','').split():self.in_title=True
        if self.in_title and tag=='a':
            m=re.search(r'/novels/(\d+)-',a.get('href',''))
            if m:self.result['id']=m.group(1)
        if tag=='figure' and 'cover' in a.get('class','').split():
            m=re.search(r'url\(([^)]+)\)',a.get('style',''))
            if m:self.result['cover']=m.group(1).strip('"\' ')
        if tag=='div' and 'short-desc' in a.get('class','').split():self.in_desc=True
    def handle_endtag(self,tag):
        if tag=='h2':self.in_title=False
        if tag=='div' and self.in_desc:self.in_desc=False
        if tag=='article':self.result=None
    def handle_data(self,data):
        if self.result is None:return
        if self.in_title:self.result['title']+=data
        if self.in_desc:self.result['description']+=data
class Release170(unittest.TestCase):
    def test_version(self):
        gradle=(ROOT/'app/build.gradle').read_text(encoding='utf-8')
        self.assertIn('versionCode 17200',gradle)
        self.assertIn("versionName '1.11.172-beta'",gradle)
    def test_cached_row_menu_never_requires_source(self):
        java=src('UnifiedDownloadManagerActivity.java')
        segment=java[java.index('private void fileMenu('):java.index('private final class FileAdapter')]
        self.assertIn('new PalettePopupMenu(this,anchor)',segment)
        self.assertIn('menu.show()',segment)
        self.assertNotIn('if(row.source==null)',segment)
        self.assertNotIn('reload();',segment)
        self.assertNotIn('Проверяем выбранную главу',java)
    def test_file_actions_do_not_trust_cache(self):
        java=src('UnifiedDownloadManagerActivity.java')
        self.assertIn('DownloadsRepository.loadOne(getApplicationContext(),id)',java)
        self.assertIn('MangaLibStorage.find(this,row.key.substring',java)
        self.assertIn('RanobeLibStorage.find(this,row.key.substring',java)
        self.assertIn('RanobesStorage.find(this,row.key.substring',java)
        self.assertNotIn('(RanobesStorage.Job)row.source',java)
        self.assertNotIn('(RanobeLibStorage.Job)row.source',java)
        self.assertNotIn('row.source==null',java)
        self.assertIn('row.key.equals("edge:"+chapter)',java)
        self.assertIn('withEdgeItem(row.ordinal,true)',java)
    def test_fixture_contains_exactly_two_search_cards(self):
        p=FixtureParser();p.feed(FILE.read_text(encoding='utf-8'))
        self.assertEqual(len(p.results),2)
        titles={i['id']:i for i in p.results}
        self.assertEqual(titles['1205570']['title'].strip(),'All The Skills')
        self.assertEqual(titles['1205975']['title'].strip(),'All The Skills I Have Are Boss Skills')
        for i in p.results:
            self.assertTrue(i['cover'].startswith('https://ranobes.net/uploads/'))
            self.assertTrue(len(i['description'])>40)
        self.assertNotIn('1205249',titles)
    def test_parser_scoped_and_css_and_pagination(self):
        api=src('RanobesApi.java')
        self.assertIn('#dle-content article.shortstory',api)
        self.assertIn('figure.cover[style]',api)
        self.assertIn('.short-desc',api)
        self.assertIn('filterWasApplied(result,value)',api)
        self.assertIn('l.title=',api)
        self.assertIn('get(next.toString())',api)
        self.assertIn('result.nextUrl',src('RanobesSearchActivity.java'))
        self.assertIn('if(!result.nextUrl.isEmpty())pages.addView',src('RanobesSearchActivity.java'))
    def test_all_themes_and_clean_docs(self):
        ui=src('RanobesSearchActivity.java')
        self.assertIn('UiColorScheme.background(this)',ui)
        self.assertIn('UiColorScheme.foreground(this)',ui)
        self.assertNotIn('0xfff3e7d5',ui)
        self.assertNotIn('0xff30221e',ui)
        for path in ROOT.rglob('*'):
            if path.is_file() and path.name.startswith(('CHANGELOG-v','README-v','QA-REPORT-v')):
                self.assertIn('v1.11.172-beta',path.name)
if __name__=='__main__':unittest.main(verbosity=2)
