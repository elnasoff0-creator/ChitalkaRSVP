ПРОЧиталка / Чтейцо — v1.11.73-beta CLEAN

Полный исходный проект Android для Android Studio и GitHub Actions.
Приложение: ru.reader.rsvp
Версия: versionCode 11173 / versionName 1.11.73-beta

Содержимое:
- app/ — исходники, AndroidManifest, Java и XML VectorDrawable.
- build.gradle, settings.gradle, gradle.properties, .gitignore.
- qa/ — существующие вспомогательные тесты проекта, если доступны.
- CHANGELOG_v1.11.73.txt — подробное описание изменений.
- COMMIT_MESSAGE_v1.11.73.txt — готовое сообщение локального Git-коммита.

Изменения: единые кнопки Toolbar по стандарту TTS (44dp, зазоры 5dp),
анимированное боковое меню, закреплённая верхняя панель MainActivity,
исправленный жизненный цикл меню, скруглённые обложки и иконка,
унифицированные вертикальные интервалы секций.

Сборка на GitHub Actions:
    gradle --no-daemon :app:assembleRelease --stacktrace

ВАЖНО: в данном окружении Gradle/Android SDK отсутствуют, поэтому релизная
сборка и тестирование на реальном телефоне не выполнялись. XML и синтаксис
Java проверены локально; перед установкой требуется сборка GitHub Actions.
