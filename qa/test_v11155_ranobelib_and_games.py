#!/usr/bin/env python3
"""v155: structural tests. Network/Android runtime still require on-device checks."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
res=root/'app/src/main/res/drawable'
java=lambda n: (src/n).read_text(encoding='utf-8')

assert "versionCode 15800; versionName '1.11.158-beta'" in (root/'app/build.gradle').read_text()
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
namespace='{http://schemas.android.com/apk/res/android}'
activities={a.attrib.get(namespace+'name') for a in manifest.iter('activity')}
assert {'.RanobeLibSearchActivity','.RanobeLibBookActivity'}.issubset(activities)
vector=ET.parse(res/'ic_ranobelib_search.xml').getroot()
assert vector.attrib[namespace+'width']=='24dp'
assert vector.attrib[namespace+'height']=='24dp'
assert all(p.attrib.get(namespace+'strokeWidth')=='1.6' for p in vector)
assert all(p.attrib.get(namespace+'fillColor')=='@android:color/transparent' for p in vector)

fish=java('ChteicoActivity.java')
assert 'panel.addView(button("Игры",this::showFishingFromStats))' not in fish
assert 'showFishingFromStats()' not in fish
assert 'holder=card();' not in fish
assert 'if(activeMiniGame!=null)return;' in fish
assert 'gameButton.setContentDescription("Открыть рыбалку в статистике и наградах")' in fish
assert 'gameFrame.getLayoutParams().height=dp(260)' in fish
assert 'openMiniGame(gameFrame)' in fish
assert 'activeMiniGame=game;' in fish
assert 'activeMiniGame.stopClock();activeMiniGame=null;activePetFrame=null;show();' in fish

api=java('RanobeLibApi.java')
for fragment in ('site_id[]","3"','"Site-Id","3"','"/manga/"+validSlug(slug)+"/chapters"',
                 'addQueryParameter("number",chapter.number)','addQueryParameter("branch_id"',
                 'AccessException','RateException','Safelist.relaxed()',
                 'if(visible.length()<35)'):
    assert fragment in api,fragment
assert 'http://' not in api
assert 'b.optLong("branch_id",0)' in api
assert 'renderDocument((JSONObject)raw,data.optJSONArray("attachments"))' in api
assert 'result.chapters=items.optInt("uploaded",result.chapters)' in api
assert 'plainDocument((JSONObject)summary)' in api
search=java('RanobeLibSearchActivity.java')
for txt in ('Найти книги','Название книги или URL RanobeLIB','search(page+1)','RanobeLibBookActivity.class'):
    assert txt in search,txt
book=java('RanobeLibBookActivity.java')
for txt in ('Скачать всю книгу','Скачать том','Выбрать главы','Скачать выбранные',
            'primaryTranslations','setMultiChoiceItems','getText().toString().trim()',
            'DownloadFolderPicker.RANOBE_REQUEST'):
    assert txt in book,txt
assert 'HashSet<String> seen' in book or 'HashSet<String> seen' in book
storage=java('RanobeLibStorage.java')
for txt in ('Books/RanobeLIB','book.html','book.json','cover.jpg','volumes/v',
            'chapter_','"library"','registerBook','rebuildBook','writeAtomic',
            'localizeImages','externalFile(c,slug,"cover.jpg")'):
    assert txt in storage,txt
loader=java('BookLoader.java')
for txt in ('if (html.contains("ranobe-chapter"))return ranobeHtml(html,filename)',
            'doc.select("section.ranobe-chapter")','BookChapterIndex.build(markers,plain.length())',
            'uri.getLastPathSegment()'):
    assert txt in loader,txt
cover=java('CoverLoader.java')
assert 'RanobeLibStorage.localCover(ctx,uri)' in cover
repo=java('DownloadsRepository.java')
assert 'RANOBE="ranobelib"' in repo
assert 'entry.id="ranobe:"+job.slug' in repo
ui=java('DownloadActivity.java')
assert 'R.drawable.ic_ranobelib_search' in ui
assert 'HorizontalScrollView toolbarScroll' in ui
assert '"Ранобэ"' in ui
assert 'RanobeLibQueue.pause' in ui and 'RanobeLibQueue.resume' in ui
assert 'RanobeLibBookActivity.class' in ui
assert 'DownloadsRepository.RANOBE.equals(item.type)' in ui
assert 'case DownloadsRepository.RANOBE:' in java('DownloadArchiveDelete.java')
queue=java('RanobeLibQueue.java')
assert 'ExistingWorkPolicy.APPEND_OR_REPLACE' in queue
assert 'NetworkType.CONNECTED' in queue
assert 'BackoffPolicy.EXPONENTIAL' in queue
assert 'for(RanobeLibStorage.Job job:RanobeLibStorage.jobs(c))saved.put(job.id(),job);' in queue
worker=java('RanobeLibDownloadWorker.java')
assert 'return Result.success()' in worker
assert 'catch(RanobeLibApi.AccessException denied){save(job,"blocked",denied.getMessage());refreshAfterTerminal(ctx,job.slug);return Result.success();}' in worker
assert 'DownloadFolderPicker.export(ctx,tree,RanobeLibStorage.cover' in worker
assert 'refreshAfterTerminal' in worker
assert 'source.length()*31L)^source.lastModified()' in java('ReaderBookCache.java')
assert 'if(actual>=0&&original!=actual)return null;' in java('ReaderBookCache.java')
assert 'c.setRequestProperty("Referer","https://ranobelib.me/")' in java('CoverImageLoader.java')
assert 'background.execute(()->{' in book
print('PASS: v155 RanobeLIB parser, download workflow, library/cover/TOC, and single embedded game invariants')
