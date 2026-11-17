# ПРОЧиталка v1.11.175-beta CLEAN — Gradle Compile Hotfix

**Основание:** полный журнал GitHub Actions, `:app:compileReleaseJavaWithJavac FAILED`, 17 ноября 2026 года. **Статус:** исправлены все три диагностированные ошибки Java из переданного журнала. **Версия:** `versionCode 17500`, `versionName 1.11.175-beta` (без повышения версии и изменения функций).

## Причина и исправление

1. `app/src/main/java/ru/reader/rsvp/ReaderActivity.java:1621`: `variable count is already defined in method onTextChanged(CharSequence,int,int,int)`. Параметр `count` уже существует в `TextWatcher.onTextChanged`. Локальный счётчик совпадений `count` переименован в `matchCount` во всех трёх применениях. Поисковая логика не изменена.
2. `app/src/main/java/ru/reader/rsvp/LinkDownloadDetailsActivity.java:46`: два сообщения `illegal forward reference`. Поле `downloadListener` при инициализации ссылается на `refreshRow`, который объявлен ниже. Поле `refreshRow` перемещено перед `downloadListener`, обработка событий и задержка обновления 160 мс остались прежними.

Предупреждение `Unable to strip ... libtranslate_jni.so` не является ошибкой Java-компиляции и не требует изменений.

## Регрессионная защита

Добавлен `qa/test_v175_javac_hotfix.py`: он извлекает два реально проблемных участка из исходников, создаёт минимальные Java-обёртки и компилирует их посредством Java 17 `javac`. Такой тест обнаруживает обе ошибки, которые пропустил прежний синтаксический парсер.

Скрипт подключён к `qa/check_v175.sh`.

## Проверки выполнены

- `qa/test_v175_javac_hotfix.py`: PASS, компиляция двух фрагментов Java 17.
- `qa/check_v175.sh`: PASS — 27 контрактных тестов, 10 000 проверок Edge Coverage, проверка типов EdgeChapterArchive с заглушками, синтаксис 198 Java и 174 Android XML, 12 проверок свайпа, 297 проверок рыбалки и MP3-декодирование.
- Архив ZIP: проверка целостности и присутствия исправленных исходников.

**Ограничение:** полная Android Gradle-сборка (`:app:assembleRelease`) здесь не выполнялась, поскольку Android SDK/Gradle недоступны. В переданном CI-журнале были ровно три ошибки, все устранены; новый прогон CI необходим для подтверждения отсутствия других ошибок, которые компилятор мог не показать до исправления первых.

## Команда повторной сборки

```sh
gradle --no-daemon :app:assembleRelease --stacktrace --console=plain
```

Другие функции приложения и исходное ТЗ v1.11.175 не изменялись.
