#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ROOT=app/src/main/java/ru/reader/rsvp
for unit in EdgeOnlineActivity EdgeDownloadService EdgePlaybackService EdgeQueueActivity EdgeTtsClient EdgeBookStorage EdgeAudioFormat EdgeQueuePlanner EdgeFileExplorerActivity EdgePrefetchSettings EdgeSegmentSettings; do
  test -f "$ROOT/$unit.java"
done
! find "$ROOT" -maxdepth 1 -name 'Mrt*.java' | grep .
! grep -q 'Mrt' app/src/main/AndroidManifest.xml
! grep -q 'Mrt' "$ROOT/ReaderActivity.java"
! grep -q 'androidx.webkit' app/build.gradle
! grep -RIl 'android.webkit' "$ROOT" --include='*.java' | grep .
grep -q 'EdgeFileExplorerActivity' app/src/main/AndroidManifest.xml
grep -q 'Edge TTS · онлайн' "$ROOT/ReaderActivity.java"
grep -q 'EdgeQueuePlanner.window' "$ROOT/EdgeDownloadService.java"
grep -q 'EdgeTtsClient.synthesize' "$ROOT/EdgeDownloadService.java"
grep -q 'EdgeDownloadService' "$ROOT/EdgeQueueActivity.java"
grep -q 'EdgeSettings.AUTO_CONTINUE' "$ROOT/EdgeDownloadService.java"
echo 'Edge-only native source isolation: PASS'
