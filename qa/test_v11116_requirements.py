#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=root/'app/src/main/java/ru/reader/rsvp'
def rd(name):return (s/name).read_text()
main=rd('MainActivity.java'); pet=rd('ChteicoPetView.java'); full=rd('ChteicoActivity.java')
reader=rd('ReaderActivity.java'); dictact=rd('EdgeDictionaryActivity.java'); trans=rd('EdgeSpeechTextTransform.java')
nav=rd('UniversalDrawer.java'); sheets=rd('ReaderSheets.java'); settings=rd('SettingsActivity.java')
checks={
'17. mini full-size sprite with overlay emotions':'new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER)' in main and 'miniEvolutionSizing' in pet,
'18. egg/chick/reader/adult scales 70/80/90/100':'?.70f' in pet and '?.80f' in pet and '?.90f' in pet,
'19. awake mini into Chteico':'putExtra("wake_pet",wake)' in main and 'getBooleanExtra("wake_pet",false)' in full,
'20. no Edge overlay ellipsis':'ttsFloatingCaption.setEllipsize(null)' in reader and 'ttsFloatingCaption.setMaxHeight(dp(188))' in reader and 'ttsFloatingCaption.scrollTo(0,hidden)' in reader,
'21. overlay overlaps book title':'captionLp.setMargins(dp(8),dp(44)' in reader,
'22. 0-2 chapters -> text blocks':'parsedChapters.size()>=3' in reader and 'chapters.size()>=3' in rd('UiNavigationSheet.java'),
'23. earned rewards up to 3 per row':'rewardCount%3==0' in full and 'rewardLine.addView(tile,lp)' in full,
'24. accent insertion':'"\\u0301"+clean.substring(letter+1)' in dictact,
'25. preview individual word':'EdgeTtsClient.synthesize(word,voice,rate' in dictact and 'MediaPlayer' in dictact,
'26. context-sensitive rules':'local.contains(context.toLowerCase(Locale.ROOT))' in trans and 'contains("|")' in trans,
'27. original WordBoundary mapping':'sourceFrom=from[word.from]' in trans and 'sourceTo=to[word.to-1]' in trans,
'28. standalone dictionary activity':'class EdgeDictionaryActivity' in dictact and 'EdgeDictionaryActivity.class' in settings,
'29. word tap selects independently':'dictionarySelectedWord=tapped' in reader and 'selectTtsWord(tapped)' in reader and 'dictionarySelectedWord<c.startWord' in reader,
'30. reader overflow pass selected word':'EXTRA_WORD,words[dictionarySelectedWord]' in reader and 'Словарь MS Edge' in sheets,
'31. whole-book search':'haystack.indexOf(needle,offset)' in reader and 'Поиск по книге' in sheets,
'32. shared left drawer':'class UniversalDrawer' in nav and 'UniversalDrawer.show(this)' in reader and 'UniversalDrawer.show(this)' in rd('AudioPlayerActivity.java'),
'33. drawer home, no Library or AudioLibrary': '"Главный экран"' in nav and 'UniversalDrawer.show(this)' in main and '"Библиотека"' not in nav and '"Аудиотека"' not in nav,
'34. library/audio toggles and icons':'iconToggle(bookToggle,R.drawable.ic_audio_chapters)' in main and 'iconToggle(toggle,R.drawable.ic_audiotheque)' in main,
}
for k,v in checks.items():print(('PASS' if v else 'FAIL')+' '+k)
assert all(checks.values()),'Missing 1.11.116 feature static evidence'
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert '.EdgeDictionaryActivity' in manifest
assert "versionName '1.11.117-beta'" in (root/'app/build.gradle').read_text()
print('PASS: 18/18 feature-source assertions + manifest/version')
