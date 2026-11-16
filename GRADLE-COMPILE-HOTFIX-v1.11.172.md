# PROChitalka v1.11.172-beta CLEAN — Gradle compile hotfix

## Причина и исправление

В `app/src/main/java/ru/reader/rsvp/EdgeChapterArchive.java`, в методе `publishFragments()`, переменная `String name` (название главы) уже объявлена во внешнем блоке. В цикле очистки устаревших фрагментов ещё раз объявлялась `for(String name : ...)`, что запрещено правилами локальных переменных Java. Компилятор сообщает `variable name is already defined in method publishFragments(...)`. Исправлено: переменная внутреннего цикла теперь `staleName`, а `clips.findFile(staleName)` использует её.

Сохранение MP3, правила очистки, SAF, свайп вправо и остальная логика не менялись. Версия и versionCode остаются `1.11.172-beta`, `17200`.

## Почему предыдущая проверка пропустила ошибку

Ранее QA проверял синтаксический разбор Java, но не семантику объявления переменных во всём EdgeChapterArchive. Добавлен офлайн-тест `qa/test_v172_archive_typecheck.py`, который компилирует настоящий `EdgeChapterArchive.java` с `javac --release 17` против небольших заглушек API Android, AndroidX и JSON. Он проверяет семантику Java непосредственно в затронутом файле, а не только парсинг.

## Проверки

* `sh qa/check_v172.sh` — пройдено: 53 регрессионных теста, 12 проверок свайпа, 297 проверок рыбалки, 197 Java-файлов синтаксически, 173 XML-файла.
* Новый компиляционный тест `EdgeChapterArchive.java` — пройден, без ошибок javac.
* Полная команда `gradle --no-daemon :app:assembleRelease --stacktrace` в этой среде **не выполнялась**, поскольку здесь отсутствуют Android SDK и Gradle. Проверка с заглушками не гарантирует успешность полной сборки.

## Если Gradle всё ещё сообщает об ошибке

Нужны строки **выше стека** начиная с `> Task :app:... FAILED`, первая `error:` или `A failure occurred while executing`, и блок `* What went wrong:`. В присланном фрагменте эти строки отсутствуют; возможно, компилятор сообщал и о других ошибках.

Команда для получения полного лога:

```sh
gradle --no-daemon :app:assembleRelease --stacktrace --console=plain > build-release.log 2>&1
```

Затем приложить `build-release.log`.
