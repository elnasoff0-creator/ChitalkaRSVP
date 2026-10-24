ПРОЧиталка v1.11.75-beta CLEAN — исходный Android-проект

Состав: app/, build.gradle, settings.gradle, gradle.properties,
qa/, README_v1.11.75.txt, CHANGELOG_v1.11.75.txt,
COMMIT_MESSAGE_v1.11.75.txt.

Обновление GitHub: сохраните свою .git и .github; замените исходный
проект файлами из ZIP. Запустите:
  gradle --no-daemon :app:assembleRelease --stacktrace

Проверки без Android SDK:
  bash qa/check_java_syntax.sh
  bash qa/check_sentence_chunks.sh

Диагностика сетевых задержек на телефоне (через adb logcat):
  adb logcat -s EdgeTtsLatency:I
В логах: handshake WebSocket, время до первого аудиопакета, полное
получение MP3 и локальная запись до готовности к запуску.

Проверить на устройстве:
1. Сепия/ночная/пользовательская тема: фон под верхними и нижними
   системными значками без светлых полос, контраст пиктограмм.
2. Режимы Курсор/Скорочтение, TTS, главный экран: безопасные отступы;
   полноэкранный режим и возврат.
3. Edge TTS: произвольное далеко расположенное предложение с Play,
   скачивание только выбранного, приоритет над предзагрузкой.
4. Edge TTS: фрагмент скачан — Play без ожидания анализа конечной тишины,
   автоматическое продолжение через следующие два предложения.
5. Edge WordBoundary не дрейфует, готовые MP3 не удаляются, sentences.json
   остаётся пригодным после перезапуска.

Версия: versionCode 11175, versionName 1.11.75-beta.
Полная Android-сборка не выполнялась: не установлен Android SDK/Gradle.
