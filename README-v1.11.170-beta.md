# ПРОЧиталка v1.11.170-beta CLEAN — Full Source

Полный исходный Android Java проект на базе **v1.11.169-beta CLEAN** с исправлениями меню глав в Менеджере и разбором поиска Ranobes.net. `versionCode 17000`, `versionName 1.11.170-beta`.

## Сборка
Используйте Android SDK 35, JDK 17, Gradle и Android Gradle Plugin, настроенные в проекте. Выполните:

```bash
gradle --no-daemon :app:assembleRelease --stacktrace
```

В среде подготовки этого архива нет Android SDK и Gradle, поэтому APK не собран и работоспособность на устройстве не проверена.

## Что изменено

Подробности в `CHANGELOG-v1.11.170-beta.md`.

- Меню «⋮» теперь открывается даже у закэшированной главы с `source=null` и у Edge-главы без активной задачи.
- Файловые действия сверяют выбранное задание с актуальным хранилищем **после выбора действия**.
- Парсер Ranobes теперь использует только настоящие результаты в `#dle-content`, поддерживает CSS-обложки и краткие описания, не показывает боковые рекомендации.
- Для поиска по `l.title` предусмотрена попытка каталожного DLE Filter с резервным поиском; точный маршрут сайта требует проверки сетевым HAR/на устройстве.
- Кнопка пагинации появляется только при наличии фактического URL следующей страницы.
- Экран поиска Ranobes теперь использует палитру приложения.

## QA

Запуск основных контрактных тестов:

```bash
python -m unittest discover -s qa -p 'test_v170_contracts.py' -v
python -m unittest discover -s qa -p 'test_v168_contracts.py' -v
python -m unittest discover -s qa -p 'test_v169_contracts.py' -v
javac -d /tmp/qa_parse qa/JavaSourceSyntaxCheck.java
java -cp /tmp/qa_parse JavaSourceSyntaxCheck
```

Результаты и ограничения представлены в `QA-REPORT-v1.11.170-beta.md`.

## Комплект

В проекте сохраняются все Java-классы, ресурсы, манифесты и сборочные файлы предыдущего релиза. Сторонние платформенные компоненты не заменены. Старые README, CHANGELOG и QA-отчёты не включаются.
