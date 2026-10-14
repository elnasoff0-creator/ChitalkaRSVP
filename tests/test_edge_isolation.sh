#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
ROOT=app/src/main/java/ru/reader/rsvp
[ -f "$ROOT/EdgeOnlineActivity.java" ]
[ -f "$ROOT/EdgeDownloadService.java" ]
[ -f "$ROOT/EdgePlaybackService.java" ]
[ -f "$ROOT/EdgeTtsClient.java" ]
grep -q 'EdgeOnlineActivity' app/src/main/AndroidManifest.xml
grep -q 'Слушать онлайн (MRT)' "$ROOT/ReaderActivity.java"
grep -q 'Edge TTS · онлайн' "$ROOT/ReaderActivity.java"
! grep -E '(android.webkit|WebView|evaluateJavascript|\.play\(|\.pause\()' "$ROOT/EdgeTtsClient.java" | grep -v '^ *\*'
! grep -E '(android.webkit|WebView|evaluateJavascript)' "$ROOT/EdgePlaybackService.java" | grep -v '^ *\*'
grep -q 'edge-direct-v1' "$ROOT/EdgeDownloadService.java"
[ -f "$ROOT/MrtDownloadService.java" ]
[ -f "$ROOT/MrtOnlineActivity.java" ]
echo 'Edge source isolation and MRT preserved: PASS'
