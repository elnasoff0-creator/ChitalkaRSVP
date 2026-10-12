# Читалка RSVP — экспериментальная ветка MRT

## v1.11.14-beta — проверка установленной версии

- Общий блок **«Версия и обновления»** присутствует в настройках на вкладках **«Чтение»** и **«Аудио»**.
- Номер версии и код сборки берутся из реально установленного APK (`PackageManager`), а не из текстовой константы.
- Кнопка **«Проверить версию в GitHub»** получает опубликованные GitHub Releases, в том числе beta-релизы, и сравнивает их версии.
- При отсутствии опубликованных релизов или недоступности интернета выводится понятное сообщение; APK из GitHub Actions Artifacts не учитываются.
- Скачивание обновления предлагается только для опубликованного APK с контрольной суммой SHA-256. Режим «Слушать онлайн · MRT» сохранён.

## v1.11.12-beta — Слушать онлайн (MRT)

* Новое меню «Слушать онлайн (MRT)» из ReaderActivity, настроек «Аудио» и аудиоплеера.
* Оригинальная веб-страница Machine Reading Text с выбором голоса и скорости; облачный API и ключи не нужны.
* Раннее наблюдение за `AudioContext.decodeAudioData` из прототипа MRT Desktop TTS: счётчик чанков и события синтеза, **без** сохранения MP3.
* Кнопки слушать / пауза / стоп, переход по фрагментам до 8000 символов, необязательное автопродолжение, сохранение позиции электронной книги.
* Для загрузки документа-старт скрипта добавлена зависимость `androidx.webkit:webkit:1.13.0`.
* Ограничения и порядок запуска: `MRT-ONLINE-README.md`.

# Читалка RSVP 1.10.0 — аудиоплеер (первый интегрированный выпуск)

Создано на базе рабочего проекта 1.9.2.4. `AudioPlayerActivity` и `AudioPlaybackService` отделены от ReaderActivity, чтобы не нарушать показ книг.

## Возможности
- Импорт одного или нескольких локальных аудиофайлов через системный выбор (форматы зависят от декодеров устройства, в первую очередь MP3/M4A/M4B/OGG).
- Фоновое воспроизведение через foreground service; плей/пауза, -15 сек, +30 сек, прогресс, скорость 0.5–3x, таймер сна, закладки и сохранение позиции.
- Кнопки управления в системном уведомлении, мини-панель в главном меню при запущенном сервисе.
- Озвучивание EPUB/FB2/HTML через установленный Android TTS с переходом к текущему фрагменту и сохранением позиции.
- Отдельный переход «♫» в ReaderActivity. Исправлено визуальное центрирование RSVP Play: изображение в FrameLayout с Gravity.CENTER вместо compoundDrawable.

## Ограничения текущего выпуска
- В аудиорежиме синхронизация с текстом реализована на уровне приблизительной позиции фрагмента, не точного слова; автоматическая временная разметка стороннего MP3 относительно EPUB отсутствует.
- Системный TTS зависит от наличия русского голосового пакета на телефоне. Для фонового воспроизведения Android может потребовать разрешения уведомлений.
- Список аудиофайлов упорядочен по URI; корректировка порядка глав и извлечение встроенных глав M4B требуют дальнейшей доработки.
- APK ещё не собирался и не проверялся на устройстве. Перед выпуском тега v1.10.0 выполните ручную Actions-сборку.

## Релиз
Используйте прежние `RELEASE_*` секреты, один и тот же ключ подписи для обновлений и workflow `.github/workflows/release.yml`. Установку из GitHub Releases запускайте после успешного теста.

## URL audio import (v1.11.0)

From AudioPlayerActivity press `+` → `Импорт по ссылке`. The independent UrlImportActivity previews the public HTML's title, cover, chapter names and runtimes. Downloads are enabled only when each selected track has a direct, unambiguous HTTPS `.mp3` link bound to its chapter in page markup. Global playlist lists are **not** guessed or paired by their order. If the site uses dynamic/obfuscated player URLs or prohibits downloading, the app displays an explicit explanation and does not circumvent access controls. Only download works you are authorized to save.

The foreground DownloadWorker stores its queue in the existing `reader` SharedPreferences and stores verified files in app-specific external music/Audiobooks/<title>. Incomplete files remain `.part`. Completed items are merged into the existing AudioLibrary model, without touching existing TTS reading state.

Known limitations requiring device/site verification: the example site's currently accessible HTML exposes chapter names and durations but no verified chapter-bound MP3 sources; there is no claim of a successful end-to-end MP3 download. App-specific storage is removed when the app is uninstalled. Queue state survives process restart; if Android terminates the download process, the user may need to press Continue to resume. Foreground Android dataSync restrictions vary by Android version. Background download is a foreground service rather than WorkManager. Data size, ETA and speed are best-effort when HTTP Content-Length is provided. Android SDK build was not performed in this environment.


## v1.11.1 - поиск MP3 как в Python-скрипте

KvuheParser дополнительно ищет MP3 в строках JavaScript в порядке их появления, снимает экранирование `\/` и `\u002F`, убирает дубликаты. Сопоставление с главами по позиции разрешено только при совпадении количества уникальных ссылок с количеством глав и отсутствии конфликтов с URL, явно привязанными к DOM-элементам. Перед скачиванием пользователь получает уведомление о том, что соответствие основано на порядке в JavaScript; его необходимо сверить с главами. Если количество не совпадает, соответствие не предполагается и скачивание неподтвержденных файлов блокируется.

Полная сборка Android и онлайн-загрузка MP3 не тестировались в этой среде.


## v1.11.2 — обновления и ручной запуск аудио
- UpdateManager сравнивает GitHub tag с Android versionName по числовым компонентам; versionCode используется только Android для установки APK.
- Выбор файловой аудиокниги и выбор главы устанавливает состояние паузы; запуск только через Play.
- Изменение скорости в паузе не вызывает MediaPlayer.setPlaybackParams, поскольку этот вызов способен включить воспроизведение.
- Проверьте сборку в GitHub Actions, APK здесь не компилировался.
