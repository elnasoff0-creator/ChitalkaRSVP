#!/usr/bin/env python3
"""Cross-mode navigation and drawer-only settings contract for v1.11.121."""
from pathlib import Path
src=Path(__file__).resolve().parents[1]
p=src/'app/src/main/java/ru/reader/rsvp'
r=(p/'ReaderActivity.java').read_text();drawer=(p/'UniversalDrawer.java').read_text()
settings=(p/'SettingsActivity.java').read_text();pet=(p/'ChteicoActivity.java').read_text()
main=(p/'MainActivity.java').read_text();audio=(p/'AudioPlayerActivity.java').read_text()
download=(p/'DownloadActivity.java').read_text();sheets=(p/'ReaderSheets.java').read_text()
open_toc=r[r.index('private void showToc()'):r.index('private void setToolbarExpanded(')]
preview=r[r.index('private void browseToWord('):r.index('private void showToc()')]
mode=r[r.index('private void setMode('):r.index('private void chteicoTouch(')]
checks={
 'version 12100': "versionCode 12100; versionName '1.11.121-beta'" in (src/'app/build.gradle').read_text(),
 'TOC only navigates viewport': 'browseToWord(word);' in open_toc and 'stop()' not in open_toc and 'index=' not in open_toc,
 'all modes use same TOC path':'if(mode==TTS&&(ttsCurrentlyPlaying()' not in open_toc,
 'does not select first sentence': 'selectedWordActive=true' not in open_toc,
 'TOC records viewport': 'viewportWord=target;' in preview and 'followSuspended=true;' in preview,
 'layout aligns inside chunk': 'dp(108)-lineY' in preview and 'postOnAnimation(' in preview,
 'book scrolling never RSVP-scrubs':'stepRsvpByScroll(dy);' not in r,
 'RSVP context available during browsing':'if(mode==RSVP&&playing)stop();' in preview,
 'mode transition does not force view away':'if(!followSuspended)returnToActiveWord();' in mode,
 'TTS and other cursors separate':'if(mode==TTS)editor.putInt("tts_word:"+docUri,index)' in r and 'else editor.putInt("position:"+docUri,index)' in r,
 'restore TTS only when selected':'if(mode==TTS&&!getIntent().getBooleanExtra("reset",false))' in r,
 'TTS exit restores reading cursor':'prefs.getInt("position:"+docUri,index)' in mode,
 'tap explicitly commits':'abandonViewportPreview(); // Only an actual tap' in r,
 'normal select requires user tap':'mode==NORMAL&&selecting&&selectedWordActive' in r,
 'RSVP tap works with scroll-word option':'(mode==RSVP&&!playing)||(mode==TTS)' in r,
 'select toggle clears stale word':'selectedWordActive=false;updateSelectWordButton()' in r,
 'bookmark uses visible chapter':'viewportWord>=0?viewportWord:index' in r,
 'drawer disabled companion name':'companionEnabled?"Чтейцо":"Статистика"' in drawer,
 'drawer disabled companion icon':'companionEnabled?R.drawable.ic_chteico_nav:R.drawable.ic_stats_nav' in drawer,
 'settings only in drawer':'item(a,list,fg,R.drawable.ic_settings,"Настройки"' in drawer,
 'no settings in reader overflow':'"Настройки"' not in sheets,
 'no toolbar settings in audio': 'R.drawable.ic_settings' not in audio,
 'no toolbar settings in download':'R.drawable.ic_settings' not in download,
 'no toolbar settings in pet':'R.drawable.ic_settings' not in pet,
 'no toolbar settings in main reading':'View more = iconButton(R.drawable.ic_settings' not in main,
 'no secondary settings shortcut':'settingsAction.setOnClickListener(v -> settings())' not in main,
 'notifications grouped below rewards':settings.index('devBox.addView(rewardsPreview);')<settings.index('LinearLayout noticeBox=col()'),
 'notification switches inside card':'chteicoSwitch(noticeBox,"Ежедневное напоминание"' in settings and 'noticeBox.addView(daysRow);' in settings,
}
for k,v in checks.items():print(('PASS' if v else 'FAIL')+' '+k)
assert all(checks.values())
