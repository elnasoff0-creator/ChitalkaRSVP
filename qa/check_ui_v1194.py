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
assert "versionName '1.11.94-beta'" in (root/'app/build.gradle').read_text()
print('PASS: UI v1.11.94 feature hooks, Settings cleanup, chapter timers, bulk cleanup; XML:',len(resources))
