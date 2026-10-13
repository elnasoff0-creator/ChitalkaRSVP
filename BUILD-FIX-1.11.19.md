# ChitalkaRSVP v1.11.19-beta: исправление сборки

GitHub Actions `compileReleaseJavaWithJavac` не находил `KeyEvent.KEYCODE_HEADSETOOK` (такой константы в Android нет).

Исправлены два места:
- `MrtOnlineActivity.java`
- `AudioPlaybackService.java`

Используется `KeyEvent.KEYCODE_HEADSETHOOK`. Остальной функционал v1.11.19-beta сохранён.

Добавлена проверка на прежнюю опечатку в GitHub Actions.

APK необходимо собрать через GitHub Actions.
