#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
src=Path('app/src/main/java/ru/reader/rsvp')
settings=(src/'SettingsActivity.java').read_text()
player=(src/'AudioPlayerActivity.java').read_text()
service=(src/'AudioPlaybackService.java').read_text()
edge=(src/'EdgeOnlineActivity.java').read_text()
queue=(src/'EdgeQueueActivity.java').read_text()
gradle=Path('app/build.gradle').read_text()
assert "versionCode 11039; versionName '1.11.32-beta'" in gradle
assert settings.count('header("Версия и обновления")') == 1
assert 'header("Версия и обновления")' not in settings.split('void drawOnlineSettings()')[0]
assert 'Проверить версию в GitHub' not in settings.split('void drawOnlineSettings()')[0]
assert 'preview.setText("Текст")' in settings
assert 'В дороге солнце' not in settings
assert 'getTtsBookUri()' in service
assert 'title=book.title' in service
assert 'showTtsCover(String uri)' in player
assert 'CoverLoader.get(getApplicationContext(),uri)' in player
assert 'showTtsCover(u);' in player
assert 'returnToAudioCover()' in player
assert '"Edge TTS"' not in edge
assert '"Голос Edge' not in edge
assert '"Слушать' not in edge
assert 'Очистить список и файлы' in queue
print('PASS UI scope, TTS cover/book title, Edge toolbar, queue label, version')
PY
