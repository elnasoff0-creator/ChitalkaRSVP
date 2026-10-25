ПРОЧиталка v1.11.79-beta CLEAN — исходный Android-проект
=========================================================

Обновление с v1.11.78: распакуйте содержимое архива в репозиторий, сохранив собственные .git и .github; выполните Gradle-сборку в Android SDK.

Сборка:
  gradle --no-daemon :app:assembleRelease --stacktrace

Локальные проверки без SDK:
  sh qa/check_java_syntax.sh
  sh qa/check_sentence_chunks.sh
  javac -d /tmp/chapter-check app/src/main/java/ru/reader/rsvp/BookChapterIndex.java qa/TestBookChapterIndex.java
  java -cp /tmp/chapter-check ru.reader.rsvp.TestBookChapterIndex

Проверка Edge на телефоне:
  1. Открыть EPUB или FB2, выбрать Microsoft Edge и фразу без готового MP3.
  2. Нажать Play немедленно после входа. Иконка должна перейти в Pause, затем должен появиться звук.
  3. Если воспроизведение не началось, на экране должна появиться диагностическая ошибка, а повторный Play должен позволить повторить запрос.
  4. Нажать на далекое предложение: только выбранный поток воспроизводится, старые незавершенные отменены.
  5. Проверить WordBoundary и локальный MP3 после полной загрузки.
  6. Открыть «Менеджер текстовых блоков» → «Главы» и выбрать главу из EPUB/FB2.

Диагностика Logcat:
  adb logcat -v time -s EdgeTtsLatency:I Edge-Player:W AndroidRuntime:E ExoPlayerImplInternal:E '*:S'

Важно: исходный ZIP не является APK, полная сборка и реальная сеть Edge здесь не проверялись. Неофициальный протокол Microsoft может измениться. Документация прошлых версий не включена.
