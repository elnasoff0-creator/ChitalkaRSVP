ПРОЧиталка v1.11.94-beta CLEAN — Android-проект

Основа проекта: v1.11.93-beta CLEAN. Содержит законченный набор исходных изменений UI, новую настройку прозрачности/цвета подсветки и переработанные настройки аудио. Изменения Edge TTS v1.11.92 сохранены.

Сборка: откройте папку в Android Studio, установите Android SDK Platform 35 и используйте Gradle (compileSdk 35, minSdk 24, Java 17). В данном окружении сборка APK не выполнялась.

Проверки без SDK:
  bash qa/check_java_syntax.sh
  python qa/check_ui_v1194.py
  bash qa/check_edge_speech_groups.sh
  bash qa/test_edge_live_buffer.sh

Ключи настройки подсветки (SharedPreferences reader):
  sentence_highlight_color = 0xffedca43
  sentence_highlight_opacity = 50 [0..100]

Важно: для Edge TTS опция «Конец главы» использует границу MP3-фрагмента, а не точную обрезку аудио внутри MP3. Не удаляйте ранее загруженные исходные книги при очистке кеша. Все удаляемые файлы перечислены в CHANGELOG.

В корне CLEAN-архива оставлены только документы этой версии: README_v1.11.94.txt, CHANGELOG_v1.11.94.txt и COMMIT_MESSAGE_v1.11.94.txt.
