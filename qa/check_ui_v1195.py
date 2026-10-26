from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
resources=list((root/'app/src/main').rglob('*.xml'))
for p in resources:ET.parse(p)
def expect(name,*tokens):
 data=(src/name).read_text()
 for token in tokens:
  assert token in data, f'{name}: missing {token}'
expect('SwipeDialogs.java','fromTop()','ChitalkaSheetTop','ChitalkaSheetBottom','SwipeDismiss','setView(View view)')
expect('UiNavigationSheet.java','sh.fromTop=true','ChitalkaSheetTop')
expect('SettingsActivity.java','drawHighlightSettings()','sentence_highlight_opacity','sentence_highlight_color','audioLink(R.drawable.ic_equalizer')
settings=(src/'SettingsActivity.java').read_text()
block=settings.split('    void drawAudioSettings(){',1)[1].split('    private void audioSpeedDialog()',1)[0]
for phrase in ('До 600 символов на фрагмент','Edge MP3 офлайн','Кэш системного TTS','Скорость WPM','Скорость воспроизведения','Таймер сна'):
 assert phrase not in block, f'Audio settings contains {phrase}'
expect('ReaderActivity.java','sentenceHighlightColor','sentenceHighlightOpacity','chapterEndWord','"Конец главы"','toolbarWpm.setVisibility','contentsAction.setVisibility')
expect('EdgeOnlineActivity.java','sentence_highlight_color','sentence_highlight_opacity','setSleepChapterEnd','saveBookmark','tocControl','"Конец главы"')
expect('EdgePlaybackService.java','setSleepChapterEnd','edge_sleep_chapter_end')
expect('AudioPlaybackService.java','ttsSleepEndWord','sleepAtChapterEnd')
expect('DownloadActivity.java','confirmDeleteAllDownloads()','deleteAllDownloads()','Удалить скачанные файлы')
assert "versionName '1.11.95-beta'" in (root/'app/build.gradle').read_text()
print('PASS: UI v1.11.95 inherited feature hooks, Settings cleanup, chapter timers, bulk cleanup; XML:',len(resources))

# UI v1.11.95 regression checks: stable gravity, proper edges, consistent palettes and icons.
reader=(src/'ReaderActivity.java').read_text()
edge=(src/'EdgeOnlineActivity.java').read_text()
swipes=(src/'SwipeDialogs.java').read_text()
sheets=(src/'UiNavigationSheet.java').read_text()
assert 'toolbarBookName.setVisibility(View.INVISIBLE)' in reader
assert 'toolbarExpanded&&index>0?View.VISIBLE:View.INVISIBLE' in reader
assert 'contentsAction.setBackground(shape(buttonBg(),13))' in reader
assert 'progressView.setTextColor(contrast(buttonBg()))' in reader
assert 'speedView.setTextColor(contrast(buttonBg()))' in reader
assert 'new int[]{R.drawable.ic_bookmark,R.drawable.ic_settings}' in reader
assert 'new int[]{R.drawable.ic_bookmark,R.drawable.ic_settings}' in edge
assert 'LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(width,width);' in edge
assert 'toolbar.addView(new View(this),new LinearLayout.LayoutParams(0,dp(1),1));' in edge
assert 'configure(dialog,context,openFromTop);' in swipes
assert 'dialog.setOnShowListener(ignored->configure(' not in swipes
assert 'installGestures(dialog,openFromTop)' in swipes
assert 'SheetDragLayout' in swipes and 'SheetDragLayout' in sheets
assert 'UiNavigationSheet.showTopCustom' in reader and 'UiNavigationSheet.showTopCustom' in edge
assert 'UiNavigationSheet.showTopChoices' in (src/'LocalVoiceCatalog.java').read_text()
assert 'UiNavigationSheet.showTopChoices' in reader and 'UiNavigationSheet.showTopChoices' in edge
assert 'UiNavigationSheet.showBottomForm' in (src/'TtsWpm.java').read_text()
assert 'UiNavigationSheet.showBottomForm' in (src/'AudioPlayerActivity.java').read_text()
assert 'setGravity(top?Gravity.TOP:Gravity.BOTTOM);' in swipes
assert 'getRawY()' in (src/'SheetDragLayout.java').read_text()
print('PASS: v1.11.95 UI layout, palette, sheets, swipe gestures, actions')
