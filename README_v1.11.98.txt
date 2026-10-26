ПРОЧиталка v1.11.98-beta CLEAN

Это полный проект исходников Android, НЕ готовый APK.
Исходная версия: v1.11.97-beta. Обновлён app/build.gradle.

Сборка на компьютере с Android SDK 35 и Gradle с Android Gradle Plugin,
поддерживающим compileSdk 35, через Android Studio.
Минимальная версия Android: API 24.

Основные обновления:
- Общая статистика офлайн-кэша Edge TTS по главам с кнопками управления.
- Нажатие на Edge-книгу в «Загрузках» показывает разделы и проценты.
- Кнопка «Загрузка озвучки» доступна в меню ⋮ текущей книги.
- Один низкоприоритетный прогревающий запрос при чтении с выбранным Edge,
  если нужного MP3 ещё нет. При Play используется тот же поток.
- Подсветка при тапе не возвращается к старому фрагменту.
- Скрыты всплывающие ошибки сетевого синтеза в режиме Edge.
- Используются прежние сохранённые MP3; очистка кэша не требуется.

Важные файлы:
  EdgeOfflineIndex.java — единый read-only индекс и покрытие по словам
  EdgeChapterDownloadsActivity.java — экран загрузок по главам
  EdgeFullBookService.java / EdgeFullBookStore.java — управляемая подготовка
  EdgeStreamingService.java — онлайн и предварительная загрузка
  EdgeSpeechSegments.java / EdgeTextSegments.java — общие границы MP3
  DownloadsRepository.java — карточки и статистика в разделе «Загрузки»

QA:
  bash qa/check_java_syntax.sh
  bash qa/check_reader_methods.sh
  bash qa/test_edge_offline_chapters.sh
  bash qa/check_edge_speech_groups.sh
  bash qa/test_edge_priority.sh
  bash qa/test_edge_sentence_seek.sh
  python qa/check_ui_v1197.py

Проверить вручную на телефоне:
1. Открыть ранее озвученную Edge-книгу без сети — она должна быть в загрузках.
2. Открыть главы, сверить их проценты и фактические MP3.
3. Сохранить одну главу, поставить на паузу, продолжить; перезапустить приложение.
4. При открытии книги с Edge убедиться, что Play использует готовые данные.
5. Сделать переход с 100-го на 900-е предложение и обратно несколькими тапами.
6. Проверить поведение HTTP 429 без навязчивых всплывающих окон.
7. Проверить, что голос, скорость, WordBoundary и старые аудиокниги работают.

Сохранены только CHANGELOG, README и COMMIT_MESSAGE текущей версии.
