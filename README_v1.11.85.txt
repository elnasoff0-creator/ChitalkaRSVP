ПРОЧиталка v1.11.85-beta CLEAN

Содержимое: полный исходный Android-проект. Версия приложения 1.11.85-beta, versionCode 11185.

Сборка (GitHub Actions, JDK 17 и Android SDK):
  gradle --no-daemon :app:assembleRelease --stacktrace

Проверки, выполняемые без Android SDK:
  bash qa/check_java_syntax.sh
  bash qa/check_sentence_chunks.sh
  bash qa/test_shared_book_session.sh
  bash qa/check_reader_methods.sh
  bash qa/test_edge_rate_gate.sh

Сетевой слой Edge использует неофициальную службу Microsoft Edge Read Aloud; возможны HTTP 429/403 и другие ограничения.
В случае HTTP 429 прекратите частые попытки, дайте истечь сроку ограничения и повторите воспроизведение.
Подготовка всей книги после ограничения может потребовать ручного возобновления в «Загрузках».
Это не APK. Реальная работа и подключение требуют проверки на устройстве.
