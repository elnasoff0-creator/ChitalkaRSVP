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
assert "versionCode 11047; versionName '1.11.40-beta'" in gradle
assert settings.count('header("Версия и обновления")') == 1
assert 'header("Версия и обновления")' not in settings.split('void drawOnlineSettings()')[0]
assert 'Проверить версию в GitHub' not in settings.split('void drawOnlineSettings()')[0]
for label in ['Оформление','Скорочтение','Аудио','Онлайн']:
    assert f'"{label}"' in settings
assert 'void drawRsvpSettings()' in settings
assert 'void equalizerDialog()' in settings and 'void ttsEngineDialog()' in settings
assert 'preview.setText("Предпросмотр текста")' in settings
assert 'В дороге солнце' not in settings
assert 'getTtsBookUri()' in service and 'title=book.title' in service
assert 'showTtsCover(String uri)' in player and 'showTtsCover(u);' in player
assert 'updateTtsLayout(boolean inTts)' in player
assert 'String[] hints=isTts?new String[]{"Фрагмент","","Фрагмент"}' in player
assert 'overallLabel.setVisibility(inTts?View.GONE:View.VISIBLE)' in player
assert 'chooseTtsFragment(AudioPlaybackService s)' in player
assert '"Edge TTS"' not in edge
assert 'Голос можно менять с' not in edge
assert 'speedValue.setText(speedLabel(playback.playbackSpeed()))' in edge
assert 'Очистить список и файлы' in queue
assert 'void attach(Context context,int audioSessionId)' in equalizer
assert 'Цвет фона — палитра' not in settings
assert 'Автоозвучка следующих фрагментов' not in settings
assert 'Предзагрузка аудио в кэш' not in settings
assert 'customEqualizerDialog()' in settings and 'ttsEngineDialog()' in settings
assert 'new int[]{R.drawable.ic_prev_chapter,R.drawable.ic_play,R.drawable.ic_next_chapter}' in player
assert 'R.drawable.ic_list_audio' in player
assert 'R.drawable.ic_web_update' in settings
main=(p/'MainActivity.java').read_text()
assert 'showAddMenu()' in main and 'chooseAudioFolder()' in main
assert 'UrlImportActivity.class' not in main
assert 'BackNavigation.back(this)' in player
print('PASS: v1.11.35 UI, dynamic Edge queue, TTS, settings, presets, import')
PY
