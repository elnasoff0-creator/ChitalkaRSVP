# ПРОЧиталка v1.11.175-beta CLEAN — исходный проект

Полный Android Java проект для импорта в Android Studio и сборки Gradle. **versionCode 17500**. Эта поставка содержит исходники, не подписанный APK.

## Сборка

```sh
gradle --no-daemon :app:assembleRelease --stacktrace --console=plain
```

Среда подготовки не содержит Android SDK/Gradle; release assemble не проверен. Задача QA с автономными проверками доступна:

```sh
sh qa/check_v175.sh
```

Описание всех изменений: `CHANGELOG-v1.11.175-beta.md`, результат QA: `QA-REPORT-v1.11.175-beta.md`, накопительное ТЗ: `TZ-PROChitalka-v1.11.175-beta.md`.

**Совместимость:** исходная база v1.11.174 с правками оптимизации; пользовательские файлы MP3/WordBoundary не мигрируют и не удаляются при установке исходников или при смене папки.
