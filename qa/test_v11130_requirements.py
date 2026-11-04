#!/usr/bin/env python3
"""Offline structural release gate. Android runtime verification remains external."""
from pathlib import Path
import re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parent.parent
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda x:(src/x).read_text(encoding='utf-8')
main=read('MainActivity.java');reader=read('ReaderActivity.java')
local=read('AudioPlaybackService.java');edge=read('EdgeStreamingService.java');playback=read('EdgePlaybackService.java')
gradle=(root/'app/build.gradle').read_text()
checks={
    'Version 1.11.130 / versionCode 13000':"versionCode 13000; versionName '1.11.130-beta'" in gradle,
    'MainActivity no extra top padding': 'root.setPadding(dp(12), 0, dp(12), dp(8))' in main,
    'Main toolbar isolated from home scroll with gap': 'scrollLp.topMargin=dp(12)' in main and 'root.addView(top' in main,
    'Reader title placed inside toolbarStack below action row': 'toolbarStack.addView(toolbarBookName,titleLp)' in reader and 'toolbar.addView(titleSpacer,' in reader,
    'Reader title still clickable for bookmarks': 'toolbarBookName.setOnClickListener(v->showBookmarks())' in reader,
    'Reader title text one line with chapter': 'toolbarBookName.setText(title+(name.isEmpty()?"":" · "+name))' in reader,
    'Reading resume cover same 88dp': 'coverView(m.title,m.uri),new LinearLayout.LayoutParams(dp(88),dp(88))' in main,
    'Audio resume cover 88dp': 'audioCover(lastBook,88),new LinearLayout.LayoutParams(dp(88),dp(88))' in main,
    'Comic resume cover 88dp': 'comicCover(recent,88,88),new LinearLayout.LayoutParams(dp(88),dp(88))' in main,
    'Shared compact resume button': main.count('addResumeButton(')==4 and 'button.setTextSize(12)' in main and 'pad(button,8,6,8,6)' in main,
    'No Play glyph on resume cards':'▶  Продолжить' not in main and '▶ Продолжить' not in main,
    'Reading resume content now stats instead of excerpt': 'readingResumeStats(m)' in main and 'shorten(m.excerpt,145)' not in main,
    'Reading position persists word count': 'm.wordCount' in main and 'current.put("wordCount",words.length)' in reader,
    'Audio continue displays chapter, time position and percent': 'audioPosition+" · "+listenProgress+"%"' in main,
    'Comic continue uses file/page/percent': 'recent.compactProgress()' in main,
    'Library headers use circle triangle': 'ic_library_triangle_down' in main and 'arrow.setBackground(shape(panelColor(),50))' in main,
    'Library expanded state rotates triangle': 'setLibraryArrow(toggle,opening)' in main and 'setLibraryArrow(toggle,show)' in main,
    'Individual menus are unframed vertical dots':main.count('itemMenuButton(v->')==3 and 'R.drawable.ic_more_vertical' in main,
    'No text arrows in library headings': '"⌄"' not in main and '"⌃"' not in main,
    'Real sentence segmentation independent of 800-char clips':'SentenceIndex.fromText(loadedText)' in reader and 'SentenceIndex.fromText(session.book.text)' in local and 'SentenceIndex.fromText(raw)' in edge,
    'Reader highlighting shares sentence index': 'ttsSentenceIndex.atWordRange(' in reader and 'SentenceChunks.forWord(ttsParts,' not in reader,
    'Local TTS captures pause and resumes previous': 'sentenceHistory.capturePause(currentSpokenTtsWord())' in local and 'sentenceHistory.previousOnResume(' in local,
    'Edge TTS captures pause and resumes previous': 'setPauseSelection' in playback and 'capturePausedSentence' in edge and 'sentenceHistory.previousOnResume(' in edge,
    'TTS commands keep previous/next independent': 'sentenceIndex.previous(word)' in local and 'sentenceIndex.previous(position)' in edge,
    'Comic feed scroll remains tap only': 'setOnScrollChangeListener' not in read('ComicReaderActivity.java'),
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+': '+k)
resources=root/'app/src/main/res'
xml=list(resources.rglob('*.xml'))
for p in xml:ET.parse(p)
print(f'PASS: XML parsing ({len(xml)} resource files)')
java=list((root/'app/src/main/java').rglob('*.java'))
refs=set()
for p in java:refs.update(re.findall(r'(?<![\w.])R\.drawable\.([A-Za-z]\w*)',p.read_text(errors='replace')))
available={p.stem for d in resources.iterdir() if d.is_dir() and d.name.startswith('drawable') for p in d.iterdir() if p.is_file()}
missing=refs-available
if missing:failed.append('unresolved drawable references: '+','.join(sorted(missing)))
else:print(f'PASS: {len(refs)} drawable references exist')
if failed:raise AssertionError('; '.join(failed))
print('PASS: all',len(checks),'v1.11.130 source checks')
