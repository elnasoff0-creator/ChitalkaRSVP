#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
D="$(mktemp -d)"
trap 'rm -rf "$D"' EXIT
mkdir -p "$D/app/src/main/java/ru/reader/rsvp" "$D/app/src/main/assets"
printf 'package ru.reader.rsvp; class MrtDownloadService {}\n' > "$D/app/src/main/java/ru/reader/rsvp/MrtDownloadService.java"
printf 'package ru.reader.rsvp; class MrtPlaybackService {}\n' > "$D/app/src/main/java/ru/reader/rsvp/MrtPlaybackService.java"
printf 'package ru.reader.rsvp; class EdgeTtsClient {}\n' > "$D/app/src/main/java/ru/reader/rsvp/EdgeTtsClient.java"
printf 'legacy' > "$D/app/src/main/assets/mrt_online_capture.js"
printf '<manifest />\n' > "$D/app/src/main/AndroidManifest.xml"
bash "$ROOT/scripts/remove-legacy-mrt-sources.sh" "$D"
test ! -f "$D/app/src/main/java/ru/reader/rsvp/MrtDownloadService.java"
test ! -f "$D/app/src/main/java/ru/reader/rsvp/MrtPlaybackService.java"
test ! -f "$D/app/src/main/assets/mrt_online_capture.js"
test -f "$D/app/src/main/java/ru/reader/rsvp/EdgeTtsClient.java"
echo 'Overlay-upgrade MRT cleanup: PASS'
