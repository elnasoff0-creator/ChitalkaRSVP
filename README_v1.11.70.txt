# ПРОЧиталка v1.11.70-beta CLEAN

Полный исходный Android-проект. Версия исправляет ошибку компиляции Java `variable end might not have been initialized` в `EdgeOnlineActivity.refresh()`.

Обновление GitHub: сохранить `.git`/`.github` рабочего репозитория, заменить `app` из этого архива, скопировать при необходимости файлы сборки из корня. Использовать Git patch только при обновлении с v1.11.69, не одновременно с полной заменой `app`.

Сборка: `gradle --no-daemon :app:assembleRelease --stacktrace` (Android SDK, Java 17, Gradle 8.7).
