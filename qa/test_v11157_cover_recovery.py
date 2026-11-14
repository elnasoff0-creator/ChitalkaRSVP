#!/usr/bin/env python3
"""v157 RanobeLIB artwork regression coverage; API/device tests remain external."""
from pathlib import Path
import subprocess
import tempfile
from xml.etree import ElementTree as ET

base=Path(__file__).resolve().parents[1]
src=base/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(src/name).read_text(encoding='utf-8')

assert "versionCode 16100; versionName '1.11.161-beta'" in (base/'app/build.gradle').read_text()
urls=read('RanobeLibCoverUrls.java')
for marker in ('cover.lib.social','/uploads/cover/','cover.cdnlibs.org','fromCoverId','candidateUrls',
               'trustedHost','https','getPort()'):
    assert marker in urls,marker
api=read('RanobeLibApi.java')
for marker in ('"cover", "covers"','"coverImage"','"cover_image"','fromCoverId',
               'candidateUrls(result.cover)','coverFromHtml','img.cover__img[src]',
               'img.cover__img[data-src]','meta[property=og:image]','coverFromPage',
               'header("Referer","https://ranobelib.me/")',
               'imageClient.newCall(req)','response.request().url().toString()'):
    assert marker in api,marker
storage=read('RanobeLibStorage.java')
for marker in ('boolean ensureCover','ranobelib_cover_attempts_v157','15*60*1000L',
               'BitmapFactory.decodeByteArray','BitmapFactory.decodeFile',
               'rememberTitle(c,t)','rebuildBook(c,t.slug)','repairMissingCover',
               'coverFromPage(t.slug)','cover.jpg'):
    assert marker in storage,marker
assert 'RanobeLibStorage.repairMissingCover(ctx,uri)' in read('CoverLoader.java')
book=read('RanobeLibBookActivity.java')
assert 'Загрузить обложку повторно' in book
assert 'ensureCover(getApplicationContext(),latest,api,true)' in book
assert 'Metadata and artwork are useful' in book
preview=read('CoverImageLoader.java')
assert 'host.equals("cover.lib.social")' in preview
assert 'c.setInstanceFollowRedirects(true)' in preview

for file in (base/'app/src/main').rglob('*.xml'):
    ET.parse(file)
with tempfile.TemporaryDirectory() as d:
    subprocess.run(['javac','-encoding','UTF-8','-d',d,
                    str(src/'RanobeLibCoverUrls.java'),str(base/'qa/TestRanobeLibCoverUrls.java')],check=True)
    subprocess.run(['java','-cp',d,'ru.reader.rsvp.TestRanobeLibCoverUrls'],check=True)
print('PASS: RanobeLIB artwork extraction, old/new CDN URLs, HTML fallback, local book repair and XML')
