#!/usr/bin/env python3
"""v1.11.154: explicitly revised TЗ; static contracts, not an Android APK test."""
from pathlib import Path
from xml.etree import ElementTree as ET
import hashlib,re
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/ru/reader/rsvp'
res=root/'app/src/main/res'
def src(n):return (p/n).read_text()
build=(root/'app/build.gradle').read_text()
assert "versionCode 15400; versionName '1.11.154-beta'" in build
# User-facing guide rather than development documentation or formulas.
manual=src('ActivityInfo.java')
for wrong in ('Java Regex','ReaderActivity','WordBoundary','JSON с','×20','в режиме разработчика','Аудиофрагменты','0,2–2'):
    assert wrong not in manual,wrong
for section in ('Загрузка озвучки','Чтейцо — Рыбалка','Скачать по ссылке','Офлайн-прослушивание','Чтейцо и награды'):
    assert f'section(entries,"{section}"' in manual,section
# Link icon is one consistent stroke vector, with no solid fill.
link=ET.parse(res/'drawable/ic_import_link.xml').getroot();a='{http://schemas.android.com/apk/res/android}'
assert link.get(a+'width')=='24dp' and link.get(a+'height')=='24dp'
for child in link:
    assert child.tag=='path' and child.get(a+'strokeWidth')=='1.5'
    assert child.get(a+'fillColor') in ('@android:color/transparent','#00000000')
# Fast UI and book completion.
ui=src('DownloadActivity.java');cache=src('DownloadListSnapshot.java')
assert 'DownloadListSnapshot.load(this)' in ui and 'DownloadListSnapshot.save(getApplicationContext(),all)' in ui
assert 'summary=text("Загрузка данных' not in ui
assert 'new Thread(()->AudiobookCoverStore.backfill(' in ui
oncreate=ui.split('private void build(){')[0]
assert 'DownloadsRepository.load(this,null)' not in oncreate
assert 'previewOnly' in cache and 'if(item.previewOnly)' in ui
chapters=src('EdgeChapterDownloadsActivity.java')
assert 'final boolean allPublished=!result.isEmpty()&&published==result.size()' in chapters
assert 'final int bookPercent=allPublished?100' in chapters
assert 'summary.setText(allPublished?"Завершено' in chapters
assert 'fragmentsPresent' in src('EdgeChapterArchive.java')
# Link cards use filenames or EPUB/FB2 book titles, never generic title if files known.
repo=src('DownloadsRepository.java')
assert 'first.displayTitle.isEmpty()?first.name:first.displayTitle' in repo
assert 'hasFinishedBook' in repo and 'status="Завершено"' in repo
linkstore=src('LinkDownloadStore.java')
assert 'displayTitle' in linkstore and 'book.put("title",entry.displayTitle.isEmpty()' in linkstore
assert 'EbookTitleExtractor.extract' in src('DirectLinkDownloadService.java')
assert '"epub".equals(format)' in src('EbookTitleExtractor.java')
# Settings accessibility and spacing.
settings=src('SettingsActivity.java');colors=src('UiColorScheme.java')
assert 'gap(9);\n        list.addView(action("＋ Добавить шрифт' in settings
assert 'lp.gravity=Gravity.END' in settings
assert 'selectedOutline.setStroke(dp(4)' in settings
assert '(4*density+.5f)' in colors
assert 'lp.topMargin=dp(5);lp.bottomMargin=dp(6);list.addView(row,lp)' in settings
# Fishing limits, rarity-specific probabilities, a gentle mass penalty and reversal boost.
fish=src('ChteicoFishingView.java');stat=src('ChteicoActivity.java')
assert 'FISH_MASS_MAX_KG={1f,2f,5f,10f,20f}' in fish
assert 'SHIMMER_CHANCES={.01f,.025f,.05f,.10f,.15f}' in fish
assert 'SHIMMER_CHANCES[rarity]+' in fish
assert 'hookedMassKg=hookedBoot?0f:' in fish
assert '(1f+.12f*massRatio)' in fish
assert 'turnBoostLeft=.30f' in fish and 'float turnBoost=1f+.22f' in fish
assert 'Достижение открыто: Мифический улов!' in fish
assert 'R.drawable.ic_reward_fishing_mythical,"Мифический улов"' in stat
assert 'button("Игры",this::showFishingFromStats)' in stat
assert 'private void showFishingFromStats()' in stat
assert 'scroll.smoothScrollTo(0,holder.getTop())' in stat
assert 'Чтейцо сейчас отключено.' not in stat
layout=ET.parse(res/'layout/view_chteico_fishing.xml').getroot()
def node(id):return next(x for x in layout.iter() if x.get(a+'id')=='@+id/'+id)
assert node('fishing_catch_viewport').get(a+'layout_gravity')=='top|center_horizontal'
assert node('fishing_result_continue').get(a+'layout_width')=='156dp'
assert node('fishing_result_continue').get(a+'layout_height')=='40dp'
assert (res/'drawable/ic_reward_fishing_mythical.xml').is_file()
for f in (res/'drawable').glob('*fishing*.xml'):ET.parse(f)
assert not list(res.rglob('*fishing*.png'))
# User-uploaded vector resources remain byte-identical to v153 BUILD-HOTFIX.
EXPECTED={'ic_search_audiobook.xml':'f3e55b461a0de4c3f0c6d6920ef1dad558865995bc79036d90e8c2cb23c9e86e',
'ic_chteico_reader_audio_down.xml':'485c0f6b5d6de49051640e490618c35e761ea2b3255354d4758b8ae8d385e3ee',
'ic_edge_merge_chapter.xml':'1a47fd76ec2aea160453640d18f0a8cea2c59684d3df161a5c25cbb5b3a257b2'}
for n,h in EXPECTED.items():assert hashlib.sha256((res/'drawable'/n).read_bytes()).hexdigest()==h,n
print('PASS: all v154 TЗ contract checks, vector assets and legacy icon hashes')
