ПРОЧиталка / Чтейцо — v1.11.74-beta CLEAN

Полный исходный Android-проект. Пакет: ru.reader.rsvp
versionCode 11174; versionName 1.11.74-beta

Состав:
- app/: Java-код приложения и Android XML-ресурсы;
- qa/: тест разбиения текста на предложения и синтаксический parse-only Java;
- build.gradle, settings.gradle, gradle.properties, .gitignore;
- README_v1.11.74.txt, CHANGELOG_v1.11.74.txt,
  COMMIT_MESSAGE_v1.11.74.txt (только документация текущей версии).

Обновление v1.11.74:
1. При выборе любого, даже ранее не скачанного, предложения Edge TTS
   немедленно переключает приоритет загрузки на выбранную позицию.
   Старые запросы не теряются, а ожидают следующей свободной загрузки.
2. Полный индекс всех предложений с состояниями saved/downloading/pending/missing
   сохраняется в EdgeTTS/sentences.json рядом с постоянными MP3.
3. Системные области статус-бара и нижней навигации окрашиваются в фон книги,
   а не отдельную светлую заливку.
4. Таймер сна в TTS Toolbar показывает остаток времени (минуты или секунды),
   как таймер в обычном MP3-плеере.
5. Предыдущие MP3 и WordBoundary не удаляются и не переписываются.

Локальные проверки: sh qa/check_sentence_chunks.sh
                   sh qa/check_java_syntax.sh
GitHub Actions:     gradle --no-daemon :app:assembleRelease --stacktrace

Ограничения: локально нет Android SDK и Gradle, поэтому выполнены только
синтаксические проверки Java/XML, тесты SentenceChunks и целостности ZIP.
Полная компиляция и реальная работа Android требуют GitHub Actions и устройства.
