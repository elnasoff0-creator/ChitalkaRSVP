from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
files={p.name:p.read_text(encoding='utf-8') for p in src.glob('*.java')}
for resource in (root/'app/src/main').rglob('*.xml'):ET.parse(resource)
def has(file,*checks):
    for token in checks:assert token in files[file],f'{file}: missing {token}'
has('MainActivity.java','ChteicoSpeechBubble','petArea=new FrameLayout(this)','bubble.setVisibility(View.GONE)','pet.setOnLongClickListener')
pet=files['MainActivity.java'].split('if(ChteicoStore.enabled(this) && prefs.getBoolean("mini_pet_on_home",true)){',1)[1].split('// Navigation moved',1)[0]
for old in ('sectionHeader("Интерактивный питомец"','Нажмите на питомца','petRow.setBackground'):assert old not in pet
has('ChteicoSpeechBubble.java','path.quadTo','path.lineTo(tail-dp(3),h-dp(1))','0xfffffdf8')
has('ReaderActivity.java','toolbarStack.setBackground(shape(translucent,15))','toolbarStack.addView(toolbarBookName','setToolbarExpanded(dy<0)','toolbarSpacer=new View(this)','ReaderSheets.overflow')
assert 'EdgeOnlineActivity.java' not in files
assert '.EdgeOnlineActivity' not in (root/'app/src/main/AndroidManifest.xml').read_text()
has('LocalVoiceCatalog.java','public static void prefetch','ready(activity.getApplicationContext()','new Thread(()->','if(activity.isFinishing()||activity.isDestroyed())return;')
assert 'AlertDialog loading=' not in files['LocalVoiceCatalog.java']
has('SettingsActivity.java','TextView chevron=text("▾"','Справка об озвучке')
audio=files['SettingsActivity.java'].split('    void drawAudioSettings(){',1)[1].split('    private void audioSpeedDialog()',1)[0]
assert 'Метод озвучки' not in audio and 'tts_last_local' not in audio
assert '"Настройки озвучивания"' not in files['SettingsActivity.java']
assert "versionName '1.11.99-beta'" in (root/'app/build.gradle').read_text()
print('PASS: retained UI features, deleted Edge screen, and shared controls')

# New v1.11.97 regressions
has('SwipeDialogs.java','params.width=ViewGroup.LayoutParams.MATCH_PARENT','params.height=ViewGroup.LayoutParams.WRAP_CONTENT','dialog.setOnShowListener(ignored->installGestures')
assert 'w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT' not in files['SwipeDialogs.java'], 'late AlertDialog resize is back'
has('UiNavigationSheet.java','dialog.show(); // Do not recenter','p.gravity=fromTop?Gravity.TOP:Gravity.BOTTOM','p.width=ViewGroup.LayoutParams.MATCH_PARENT')
has('SheetDragLayout.java','scrollableUnderFinger','getLocationOnScreen','fromTop?1:-1')
has('AudioPlayerActivity.java','UiNavigationSheet.showTopChoices(this,"Скорость аудиокниги"','UiNavigationSheet.showTopChoices(this,"Таймер сна"','UiNavigationSheet.showAudiobookChapters','int holderSize=dp(44)','setCornerRadius(dp(12))','new LinearLayout.LayoutParams(0,-1,1f)')
has('ReaderActivity.java','rsvpPlayFrame.setBackground(shape(accent(),12))','new LinearLayout.LayoutParams(dp(44),dp(44))')
has('ReaderToolbarController.java','static void measure','static void reveal','static void reset')
has('PlaybackAudioFocus.java','AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && keepPlayingOnDuck','setWillPauseWhenDucked(!keepPlayingOnDuck)','if (!registered) return;')
has('AudioPlaybackService.java','},true); // Ignore duck-only notification focus changes')
assert "versionName '1.11.99-beta'" in (root/'app/build.gradle').read_text()
print('PASS: audiobook sheets, gestures, standard buttons, safe audio focus and XML')

# v1.11.99 architectural and performance invariants
has('DownloadActivity.java','newSingleThreadExecutor','DiffUtil.calculateDiff','changes.dispatchUpdatesTo(adapter)','refreshInFlight','scan.shutdownNow()')
assert 'notifyDataSetChanged()' not in files['DownloadActivity.java']
assert 'DownloadsRepository.load(this,edge)' not in files['DownloadActivity.java']
has('EdgeChapterDownloadsActivity.java','sameChapters(result)','sameStats(displayed.get(i),entry)')
has('EdgeAudioManager.java','readySegments(folder)','snapshots.put','static String key','static void invalidate')
has('EdgeStreamingService.java','EdgeAudioManager.key','EdgeAudioManager.invalidate')
has('EdgeFullBookService.java','EdgeAudioManager.key','EdgeAudioManager.invalidate')
has('ReaderActivity.java','ReaderTtsController.begin','ReaderTtsController.localPauseOrResume','ReaderSheets.overflow','ReaderToolbarController.measure','ReaderToolbarController.reveal')
has('MainActivity.java','FrameLayout petArea','FrameLayout.LayoutParams bubbleLp','bubble.setElevation(dp(8))','areaLp')
print('PASS: v1.11.99 no UI scan, cached Edge index, four-mode Reader, stable mini-pet')
