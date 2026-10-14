#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/ru/reader/rsvp')
settings=(p/'SettingsActivity.java').read_text()
player=(p/'AudioPlayerActivity.java').read_text()
service=(p/'AudioPlaybackService.java').read_text()
edge=(p/'EdgeOnlineActivity.java').read_text()
queue=(p/'EdgeQueueActivity.java').read_text()
equalizer=(p/'AudioEqualizer.java').read_text()
gradle=Path('app/build.gradle').read_text()
assert "versionCode 11040; versionName '1.11.33-beta'" in gradle
assert settings.count('header("Версия и обновления")') == 1
assert 'header("Версия и обновления")' not in settings.split('void drawOnlineSettings()')[0]
assert 'Проверить версию в GitHub' not in settings.split('void drawOnlineSettings()')[0]
for label in ['Оформление','Скорочтение','Аудио','Онлайн']:
    assert f'"{label}"' in settings
assert 'void drawRsvpSettings()' in settings
assert 'void equalizerDialog()' in settings and 'void ttsEngineDialog()' in settings
assert 'preview.setText("Текст")' in settings
assert 'В дороге солнце' not in settings
assert 'getTtsBookUri()' in service and 'title=book.title' in service
assert 'showTtsCover(String uri)' in player and 'showTtsCover(u);' in player
assert 'updateTtsLayout(boolean inTts)' in player
assert 'String[] hints=isTts?new String[]{"Фрагмент","","Фрагмент"}' in player
assert 'overallLabel.setVisibility(inTts?View.GONE:View.VISIBLE)' in player
assert 'chooseTtsFragment(AudioPlaybackService s)' in player
assert '"Edge TTS"' not in edge
assert 'Голос можно менять с' not in edge
assert 'speedButton.setText(speedLabel(playback.playbackSpeed()))' in edge
assert 'Очистить список и файлы' in queue
assert 'void attach(Context context, int audioSessionId)' in equalizer
print('PASS: v1.11.33 UI, Edge speed, three TTS controls, settings, equalizer')
PY
