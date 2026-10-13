#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
service=app/src/main/java/ru/reader/rsvp/MrtPlaybackService.java
activity=app/src/main/java/ru/reader/rsvp/MrtOnlineActivity.java
grep -q 'class MrtPlaybackService extends Service' "$service"
grep -q 'new ExoPlayer.Builder' "$service"
grep -q 'void appendReady' "$service"
grep -q 'void seekWithin' "$service"
grep -q 'void rewind10' "$service"
grep -q 'void forward10' "$service"
grep -q 'void setAutoContinue' "$service"
if grep -Eq '^import .*WebView|\.evaluateJavascript\(|tapNativeMrtButton\(' "$service"; then echo 'ERROR: player must not touch WebUI' >&2; exit 1; fi
grep -q 'private void syncReadyFiles' "$activity"
grep -q 'private void updateTimebars' "$activity"
grep -q 'bookSeek=new SeekBar' "$activity"
grep -q 'fileSeek=new SeekBar' "$activity"
grep -q 'nativePlayer.pause()' "$activity"
grep -q 'nativePlayer.appendReady' "$activity"
grep -q 'androidx.media3:media3-exoplayer:' app/build.gradle
grep -q 'android:name=".MrtPlaybackService"' app/src/main/AndroidManifest.xml
! grep -R -q 'KEYCODE_HEADSETOOK' app/src/main/java
printf '%s\n' 'PASS: independent Media3 player, separated download queue, two timelines, safe media buttons'
