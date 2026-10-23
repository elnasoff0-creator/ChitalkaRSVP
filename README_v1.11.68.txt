ПРОЧиталка 1.11.68-beta CLEAN (исходный Android-проект)

Исправление ошибки компиляции Java по логу GitHub Actions для v1.11.67.
Откройте каталог проекта в Android Studio либо замените папку app и
сопутствующие файлы проекта в репозитории GitHub.

Для проверки в GitHub Actions выполните:
  gradle --no-daemon :app:assembleRelease --stacktrace

Содержит полные исходники, CHANGELOG и COMMIT_MESSAGE.
В среде подготовки архива Gradle/Android SDK недоступны, сборка не запускалась.
