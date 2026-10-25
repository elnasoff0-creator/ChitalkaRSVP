ПРОЧиталка v1.11.82-beta CLEAN

Это полный исходный Android-проект. Изменение относительно v1.11.81-beta: исправлена ошибка компилятора Java «illegal forward reference» в ReaderActivity.java. Никакие функции чтения или TTS целенаправленно не изменялись.

Сборка в GitHub Actions:
  gradle --no-daemon :app:assembleRelease --stacktrace

Это исправление на уровне Java-исходников; в текущем окружении отсутствуют Gradle и Android SDK, поэтому готовность APK не подтверждена.

Текущая версия — 1.11.82-beta, versionCode 11182.
