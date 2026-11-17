# ПРОЧиталка v1.11.176-beta CLEAN — полный исходный проект

Это исходники Android Java, **не APK**.

**Изменено:** быстрый Play/Pause с последовательной очередью команд; адресное обновление отдельных глав; накопительный общий прогресс, AtomicFile JSON cache, пакетные контрольные точки; миграция очередей MangaLib/Audio/Edge/DirectLink/RanobeLIB/Ranobes; автоматическая версия APK и проверка Git-тега в `.github/workflows/release.yml`.

**Версия:** `versionCode 17600`, `versionName 1.11.176-beta`.

**Полная расшифровка** — `CHANGELOG-v1.11.176-beta.md`, архитектура — `ARCHITECTURE-DOWNLOAD-PROGRESS-v1.11.176.md`, отчёт — `QA-REPORT-v1.11.176-beta.md`, полное накопительное ТЗ — `TZ-PROChitalka-v1.11.176-beta.md`.

Запуск проверок: `sh qa/check_v176.sh`. Для настоящей release-сборки нужна Android SDK 35, Java 17, Gradle 8.7 и зависимости проекта. В GitHub Actions: `gradle --no-daemon :app:assembleRelease --stacktrace`. Публикация подписанного APK требует действительных secrets репозитория.

Локальная статическая/Java проверка **не равнозначна подтверждённой Gradle-сборке**. После загрузки исходников в GitHub обязательно просмотреть полный результат CI, затем проверить на Android устройстве.
