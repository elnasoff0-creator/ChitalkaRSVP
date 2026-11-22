#!/usr/bin/env python3
"""Static wiring checks: only real Android runtime/Gradle can validate user audio."""
from pathlib import Path
import re
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
get=lambda f:(src/f).read_text(encoding='utf-8')
reader=get('ReaderActivity.java')
controller=get('ReaderTtsController.java')
stream=get('EdgeStreamingService.java')
archive=get('EdgeChapterArchive.java')
audio=get('AudioPlaybackService.java')
play=get('EdgePlaybackService.java')
log=get('ReaderDiagnosticLog.java')
trace=get('EdgeStartupTrace.java')
version=(root/'app/build.gradle').read_text()
match=re.search(r"versionCode\s+(\d+);\s*versionName\s+'1\.11\.(\d+)-beta'",version)
assert match and int(match.group(2))>=188 and int(match.group(1))==int(match.group(2))*100
assert 'DownloadFolderPicker.granted(this,DownloadFolderPicker.BOOK)' in reader
assert '!EdgeFolderPicker.available(this)' not in reader
assert 'TtsSwitchPolicy.shouldSwitch(ttsLocal,localChoice,backupActive)' in reader
assert 'ReaderTtsController.stopLocal(this)' in reader
assert 'startTtsSession(shouldResume,!localChoice)' in reader
assert 'TTS_BADGE_UPDATED' in reader
assert 'if(mode==TTS)scheduleTtsUi();' in reader
assert 'TTS_BACKUP_STOP_REQUESTED' in controller
assert '.setAction(AudioPlaybackService.STOP)' in controller
assert 'EXTRA_FORCE_EDGE' in controller
assert 'TTS_BACKUP_STOPPED' in audio
assert 'ttsBookLoadGeneration' in audio
assert 'FALLBACK_INIT_MS=' in audio
assert 'TTS_FALLBACK_AUDIO_STARTED' in audio
assert 'fallbackAudioStarted();' in audio
assert 'EDGE_RECOVERY_INTERVAL_MS' in audio
assert 'TTS_EDGE_ACTIVATED forced=true' in stream
assert 'setAnchor(selected,true);' in stream
assert 'cancelLiveExcept(null);liveJobs.clear();' in stream
assert 'EdgeFirstAudioPolicy.restoreWaitMs(foreground)' in stream
assert 'EdgeFirstAudioPolicy.bypassPendingRestore(' in stream
assert 'lazyCommitPermits' in stream and 'commitAllowed::get' in stream
assert 'mayPublish.getAsBoolean()' in archive
assert 'if(!mayPublish.getAsBoolean()){part.delete();return false;}' in archive
assert 'if(!mayPublish.getAsBoolean()){temp.delete();return false;}' in archive
assert 'EdgeStartupTrace.firstAudio("media3_is_playing")' in play
for stage in ('PLAY','SAF_PERMISSION','BOOK_READY','GROUP_INDEXED','SAF_RESTORE_CHECK','NETWORK_REQUEST','WS_CONNECTED','FIRST_MP3_PACKET','FIRST_AUDIO'):
    assert stage in (reader+stream+trace+play),stage
assert 'private static final int MAX_BYTES=512*1024' in log
assert 'Без исходного текста книг' in log
assert 'ReaderDiagnosticLog.prepareExport(' in get('ReaderLogActivity.java')
for f in (root/'app/src/main').rglob('*.xml'):
    ET.parse(f)
md=list(root.glob('*.md'))
current_version=re.search(r"versionName\s+'([^']+)'",version).group(1)
assert len(md)==4 and all(current_version in p.name for p in md),md
print('PASS: v188 forced switch, first-audio trace, bounded SAF and CLEAN documentation')
