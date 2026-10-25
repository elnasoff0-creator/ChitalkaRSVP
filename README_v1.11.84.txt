ПРОЧиталка v1.11.84-beta CLEAN

Полный Android-проект, исправляющий ошибки cannot find symbol в ReaderActivity.java версии v1.11.83.
Сборка в GitHub Actions: gradle --no-daemon :app:assembleRelease --stacktrace

Локальные проверки (без Android SDK):
  sh qa/check_reader_methods.sh
  sh qa/check_java_syntax.sh
  sh qa/check_sentence_chunks.sh
  sh qa/test_shared_book_session.sh

Статические проверки не подтверждают компиляцию Android-приложения.
