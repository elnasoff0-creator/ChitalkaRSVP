ПРОЧиталка v1.11.90-beta CLEAN — исходный Android-проект

Android Java-проект с текущей архитектурой Edge TTS, унаследованной от v1.11.89-beta.
В этом выпуске заменены 9 XML VectorDrawable-иконок из присланного набора.

Структура:
  app/                 исходный код Android и ресурсы
  qa/                  проверки движка Edge TTS и вспомогательные скрипты
  CHANGELOG_v1.11.90.txt  перечень изменений текущей версии
  COMMIT_MESSAGE_v1.11.90.txt  описание коммита

В корневом каталоге намеренно отсутствуют README, CHANGELOG и COMMIT_MESSAGE прошлых выпусков.

Сборка:
  Требуются JDK 17, Android SDK Platform 35 и Gradle с Android Gradle Plugin.
  gradle assembleDebug

Проверка без Android SDK:
  bash qa/test_edge_websocket_frames.sh
  bash qa/test_edge_speech_engine.sh
  bash qa/test_edge_speech_files.sh
  bash qa/check_edge_speech_groups.sh
  bash qa/check_java_syntax.sh

Замечание: реальный запуск Android APK и онлайн Edge TTS в среде создания архива не проверялись.
