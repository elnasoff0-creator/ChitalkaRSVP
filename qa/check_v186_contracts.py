#!/usr/bin/env python3
"""Static v186 integration contracts; gesture/animation smoothness still needs Android testing."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda filename:(src/filename).read_text(encoding='utf-8')
reader=read('ReaderActivity.java')
version=re.search(r"versionCode\s+(\d+);\s*versionName\s+'([^']+)'",(root/'app/build.gradle').read_text())
assert version and int(version.group(1))>=18600
assert 'setTextIsSelectable(false)' in reader
assert 'setCustomSelectionActionModeCallback' not in reader
assert 'nativeTextActions(' not in reader
assert 'activeNativeActionMode' not in reader
assert 'ReaderTextTouchPolicy touch=new ReaderTextTouchPolicy()' in reader
assert 'beginTextSelection(word)' in reader
assert 'touch.consumeLongPress()' in reader
assert 'if(tap)handleTextTap(' in reader
assert 'applySelectionChars(' in reader
assert 'ReaderSelectionRange.Move moved=current.drag(' in reader
assert 'selectedCharFrom' in reader and 'selectedCharTo' in reader
assert 'this::saveSelectionQuote' in reader
assert 'new Intent(this,QuotesActivity.class)' in reader
assert 'new PopupWindow(actionScroll,width,height,false)' in reader
for icon in ('ic_reader_selection_color','ic_reader_selection_copy','ic_reader_selection_note',
             'ic_reader_selection_dictionary','ic_bookmark','ic_reader_quote'):
    assert 'R.drawable.'+icon in reader,icon
assert 'annotationSpans' in reader and 'playbackSpans' in reader
assert 'renderedAnnotations!=annotationPaintVersion' in reader
assert 'wordBoundaryRefreshDelay()' in reader
assert 'WordBoundaryRefresh.nextDelayMs(' in reader
assert 'ReaderWordHighlightSpan' in reader
assert 'wordFade.setDuration(70L)' in reader
assert 'speechPosition.accept(' in reader
assert 'selectionDragging||selectionFrom>=0)return;' in reader
assert 'viewportNavigationEpoch++' in reader
handle=read('ReaderSelectionController.java')
assert 'teardrop.cubicTo' in handle and 'paint.setColor(host.handleColor())' in handle
assert 'touchRadius=32f' in handle
for icon in ('ic_reader_selection_dictionary.xml','ic_edge_dictionary.xml'):
    path=root/'app/src/main/res/drawable'/icon
    tree=ET.parse(path).getroot()
    ns='{http://schemas.android.com/apk/res/android}'
    paths=tree.findall('path')
    assert paths and all(p.attrib[ns+'strokeWidth']=='1.5' for p in paths),icon
    assert all(p.attrib[ns+'fillColor']=='@android:color/transparent' for p in paths),icon
uni=read('UnifiedDownloadManagerActivity.java')
edge=read('EdgeChapterDownloadsActivity.java')
download=read('DownloadActivity.java')
assert 'AnimatedDownloadProgressBar progress' in uni
assert 'progress.setWorking(showPause&&!i.ready)' in uni
assert 'DownloadButtonFeedback.apply(playPause' in uni
assert 'DownloadButtonFeedback.apply(allIcon' in uni
assert 'totalProgress.setWorking(running&&!allPublished)' in edge
assert 'DownloadButtonFeedback.apply(pauseButton' in edge
assert 'h.progressBar.setWorking(item.running&&!item.ready)' in download
animation=read('AnimatedDownloadProgressBar.java')
for entry in ('motion.setDuration(1100L)','stopMotion();super.onDetachedFromWindow()',
              'setWorking(boolean active)','if(!working||isIndeterminate()'):
    assert entry in animation,entry
button=read('DownloadButtonFeedback.java')
assert 'state_pressed' in button and 'state_focused' in button
assert 'shape(surface,accent,active' in button
menu=read('ReaderSheets.java')
assert 'Все цитаты' in menu
pet=read('PetQuoteChoice.java')
assert 'return hasEnabledQuotes && random.nextBoolean();' in pet
for xml in (root/'app/src/main').rglob('*.xml'):
    ET.parse(xml)
md=[f for f in root.glob('*.md') if f.name != 'README.md']
assert (root/'README.md').is_file(), 'README.md missing'
assert len(md)==3 and all(version.group(2) in f.name for f in md),md
print('PASS: v186 custom selection, themed teardrop handles, 1.5dp dictionary icons, smooth WordBoundary, live downloads, quotes and CLEAN docs')
