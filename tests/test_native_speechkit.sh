#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
src=app/src/main/java/ru/reader/rsvp/MrtNativeSpeechKitClient.java
service=app/src/main/java/ru/reader/rsvp/MrtDownloadService.java
ui=app/src/main/java/ru/reader/rsvp/MrtOnlineActivity.java
for name in 'OkHttpClient' 'onAudioBlock' 'onComplete' 'onError' 'cancelled.get' 'tts:synthesize' 'format","oggopus' 'Base64.NO_WRAP'; do
  grep -Fq "$name" "$src"
done
grep -Fq 'MrtNativeSpeechKitClient' "$service"
grep -Fq 'setBackend' "$service"
grep -Fq 'Настроить нативный TTS' "$ui"
echo 'PASS: optional native online engine isolated from existing MRT/Edge'
