#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
player=app/src/main/java/ru/reader/rsvp/MrtPlaybackService.java
screen=app/src/main/java/ru/reader/rsvp/MrtOnlineActivity.java
queue=app/src/main/java/ru/reader/rsvp/MrtDownloadService.java
queue_ui=app/src/main/java/ru/reader/rsvp/MrtDownloadActivity.java
grep -q 'class MrtPlaybackService extends Service' "$player"
grep -q 'new ExoPlayer.Builder' "$player"
grep -q 'void appendReady' "$player"
grep -q 'void seekWithin' "$player"
grep -q 'void rewind10' "$player"
grep -q 'void forward10' "$player"
grep -q 'addListener' "$player"
grep -q 'class MrtDownloadService extends Service' "$queue"
grep -q 'MrtQueuePlanner.window' "$queue"
grep -q 'class MrtDownloadActivity extends Activity' "$queue_ui"
grep -q 'class MrtOnlineActivity extends Activity' "$screen"
grep -q 'new SeekBar' "$screen"
grep -q 'fileLabel.setText("Фрагмент №"' "$screen"
! grep -q 'bookSeek' "$screen"
! grep -q 'WebView' "$screen"
! grep -q 'stopButton' "$screen"
if grep -Eq 'import .*WebView|\.evaluateJavascript\(|captureBegin|MrtAudioCache' "$player"; then
  echo 'FAIL: player contains WebUI or download logic' >&2;exit 1
fi
if grep -Eq 'player\.(play|pause|stopAudio|seekWithin|next|previous)\(' "$queue"; then
  echo 'FAIL: downloader may not control audio transport' >&2;exit 1
fi
grep -q 'android:name=".MrtDownloadService"' app/src/main/AndroidManifest.xml
grep -q 'android:name=".MrtDownloadActivity"' app/src/main/AndroidManifest.xml
! grep -R -q 'KEYCODE_HEADSETOOK' app/src/main/java
printf '%s\n' 'PASS: ExoPlayer transport separate from background queue and screen, one file seekbar'
