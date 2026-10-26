from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
files={p.name:p.read_text(encoding='utf-8') for p in src.glob('*.java')}
for resource in (root/'app/src/main').rglob('*.xml'):ET.parse(resource)
def has(file,*checks):
    for token in checks:assert token in files[file],f'{file}: missing {token}'
has('MainActivity.java','ChteicoSpeechBubble','petArea.setGravity(Gravity.CENTER_HORIZONTAL)','bubble.setVisibility(View.GONE)','pet.setOnLongClickListener')
pet=files['MainActivity.java'].split('if(ChteicoStore.enabled(this) && prefs.getBoolean("mini_pet_on_home",true)){',1)[1].split('// Navigation moved',1)[0]
for old in ('sectionHeader("Интерактивный питомец"','Нажмите на питомца','petRow.setBackground'):assert old not in pet
has('ChteicoSpeechBubble.java','path.quadTo','path.lineTo(tail-dp(3),h-dp(1))','0xfffffdf8')
has('ReaderActivity.java','toolbarStack.setBackground(shape(translucent,15))','toolbarStack.addView(toolbarBookName','setToolbarExpanded(dy<0)','toolbarSpacer=new View(this)','"Голос озвучки"')
has('EdgeOnlineActivity.java','topToolbarPanel.setBackground(bg((background&0x00ffffff)|0x80000000,15))','topToolbarPanel.addView(topBookName','else if(n==2)chooseVoice()','secondary.setMaxLines(4)')
has('LocalVoiceCatalog.java','public static void prefetch','ready(activity.getApplicationContext()','new Thread(()->','if(activity.isFinishing()||activity.isDestroyed())return;')
assert 'AlertDialog loading=' not in files['LocalVoiceCatalog.java']
has('SettingsActivity.java','TextView chevron=text("▾"','Справка об озвучке')
audio=files['SettingsActivity.java'].split('    void drawAudioSettings(){',1)[1].split('    private void audioSpeedDialog()',1)[0]
assert 'Метод озвучки' not in audio and 'tts_last_local' not in audio
assert '"Настройки озвучивания"' not in files['SettingsActivity.java']
assert "versionName '1.11.98-beta'" in (root/'app/build.gradle').read_text()
print('PASS: retained v1.11.96 UI behaviors and settings')

# New v1.11.97 regressions
has('SwipeDialogs.java','params.width=ViewGroup.LayoutParams.MATCH_PARENT','params.height=ViewGroup.LayoutParams.WRAP_CONTENT','dialog.setOnShowListener(ignored->installGestures')
assert 'w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT' not in files['SwipeDialogs.java'], 'late AlertDialog resize is back'
has('UiNavigationSheet.java','dialog.show(); // Do not recenter','p.gravity=fromTop?Gravity.TOP:Gravity.BOTTOM','p.width=ViewGroup.LayoutParams.MATCH_PARENT')
has('SheetDragLayout.java','scrollableUnderFinger','getLocationOnScreen','fromTop?1:-1')
has('AudioPlayerActivity.java','UiNavigationSheet.showTopChoices(this,"Скорость аудиокниги"','UiNavigationSheet.showTopChoices(this,"Таймер сна"','UiNavigationSheet.showAudiobookChapters','int holderSize=dp(44)','setCornerRadius(dp(12))','new LinearLayout.LayoutParams(0,-1,1f)')
has('ReaderActivity.java','rsvpPlayFrame.setBackground(shape(accent(),12))','new LinearLayout.LayoutParams(dp(44),dp(44))')
has('EdgeOnlineActivity.java','playArea.setBackground(bg(playBackground,12))','new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.CENTER)')
has('PlaybackAudioFocus.java','AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && keepPlayingOnDuck','setWillPauseWhenDucked(!keepPlayingOnDuck)','if (!registered) return;')
has('AudioPlaybackService.java','},true); // Ignore duck-only notification focus changes')
assert "versionName '1.11.98-beta'" in (root/'app/build.gradle').read_text()
print('PASS: v1.11.97 audiobook sheets, gestures, standard buttons, safe audio focus and XML')
