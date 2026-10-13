## ChitalkaRSVP v1.11.25-beta — selective MRT fragment redownload

In **Список очереди**, each fragment now has a rounded reload icon (↻). Tap it and confirm to re-download only that fragment. A damaged or incompatible cached audio file is evicted from the native playlist, deleted from the book-local audio cache (all supported extensions, temporary .part and its legacy duplicate), and regenerated with priority by the existing background `MrtDownloadService`. Other downloaded fragments and `queue.json` are preserved, and playback of another fragment is not interrupted. The currently *audible* fragment is protected: pause it before requesting replacement.

If auto-download is paused, confirming a reload starts it. The queue explicitly shows «Перезагрузка»/«Скачивается» and live chunks/KB. Forced redownload works even when the selected fragment is outside the normal prefetch window. ExoPlayer reinserts the rebuilt media item in correct word order.

Caveat: Some external Android file browsers lack OGG codecs. If a newly generated file is OGG, redownloading does not automatically convert it into MP3 and may not resolve external player incompatibility.

Tests: `bash tests/test_mrt_selective_redownload.sh` plus existing MRT tests. Build APK through GitHub Actions.

---

# ChitalkaRSVP v1.11.22-beta

Новая архитектура независимого MRT MP3 Player (Media3 ExoPlayer) — см. `MRT-PLAYBACK-1.11.22.md`.

## v1.11.20-beta — очередь MRT и постоянный кэш

* В MRT отображается сохраняемая очередь готовых, загружаемых и прослушанных MP3. Серые строки уже прослушаны; во время получения данных показываются чанки, КБ и длина текста в символах.
* Количество в настройке означает **всю готовящуюся очередь**: 3 = текущий фрагмент + следующие 2. Первый воспроизводится сразу после подготовки, не ожидая остальные. После перехода на следующий автоматически готовится ещё один.
* Пауза и ручной «Стоп» не прерывают предзагрузку. «Стоп» завершает только текущую озвучку, повторный Play начинает этот фрагмент с начала; переключение на другие аудиорежимы полностью останавливает MRT.
* Аудиофайлы и `queue.json` сохраняются по отдельности для каждой книги в `getExternalFilesDir(DIRECTORY_DOCUMENTS)/ChitalkaRSVP/Books/<title>--<book-id>/Аудио/`. Копия электронной книги лежит рядом с папкой «Аудио»; оригинал, открытый через SAF, не перемещается.
* «Папка аудио» открывает встроенный просмотрщик MP3. Системный файловый менеджер на новых Android ограничивает прямой доступ к `Android/data`; кнопка «Экспорт в проводник» предлагает выбрать доступную папку и копирует туда MP3 и `queue.json`.
* «Очистить MP3 и очередь» удаляет только аудиофайлы и индекс выбранной книги, сохраняя текст книги и позицию. Удаление книги из библиотеки асинхронно удаляет и её папку аудиокэша.
* Перехваченные MP3-байты сохраняются без перекодирования. Исправлена проверка MPEG-1 Layer III без ID3. Кэш не удаляет старые MP3 по лимиту, пока книга находится в библиотеке.

**Ограничение:** оригинальная книга может быть расположена в защищённом системном или облачном `content://`-хранилище. Android не всегда выдаёт право записывать файлы рядом с оригиналом, поэтому приложение хранит локальную копию книги и её MP3 в собственной постоянной папке и предлагает экспорт для доступа через системный проводник. Работа реального MRT на телефоне и полный Android build должны быть проверены после сборки.

# ChitalkaRSVP v1.11.19-beta — гарнитура, TTS и единое аудиоуправление

Изменения поверх v1.11.18-beta:

- Гарнитура Bluetooth/проводная: Play/Pause/Stop через стандартную Android MediaSession как для TTS/аудиокниг, так и для MRT MP3. Кнопки надо проверить на реальном устройстве.
- Android AudioFocus: при телефонном звонке или перехвате аудиофокуса другим приложением все активные звуковые движки ставятся на паузу **без автоматического возобновления**. Отключение наушников (`ACTION_AUDIO_BECOMING_NOISY`) также ставит звук на паузу.
- При переходе к онлайн MRT останавливается фоновый `AudioPlaybackService` (TTS/аудиокнига). При запуске TTS или аудиокниги предыдущий MRT получает команду остановки.
- TTS: фиксированное по высоте прокручиваемое окно для всех фрагментов. Обложка и остальные блоки не меняют положение, текст обновляется только при смене `ttsPosition`; повторная установка неизменных заголовков отключена.
- Вход MRT на главной странице, в настройках и в меню аудиоплеера удалён. Выбор системного TTS/MRT остался в **открытой книге → кнопка ♫** (`ReaderActivity`). Настройка размера фрагмента MRT в настройках сохранена.
- Предзагрузка и кэш MRT из v1.11.18-beta сохранены.

Версия APK: **1.11.22-beta**, versionCode **11029**. Собрать и проверить на телефоне через GitHub Actions (Android SDK недоступен здесь). ZIP содержит файлы проекта для размещения непосредственно в корне репозитория, включая скрытую папку `.github`.

---

# ChitalkaRSVP v1.11.18-beta — автономная очередь MRT MP3

Экспериментальное онлайн-чтение с предзагрузкой и независимым Android-плеером. Проект обновляется поверх v1.11.17-beta; `applicationId` и библиотека не изменяются.

- Кнопка **«Слушать»** сначала проверяет кэш первого фрагмента. Если MP3 ещё нет, WebView MRT беззвучно получает аудиочанки, `MrtAudioCache` сохраняет и проверяет MP3, после чего **`MrtPlaybackEngine`** запускает Android MediaPlayer.
- В то время как плеер воспроизводит текущий фрагмент, WebView генерирует очередные MP3, до **1–3 вперёд**, согласно настройке. Подготовленный второй `MediaPlayer` НЕ запускается раньше события окончания первого.
- На каждом переключении очередь сдвигается и освобождается место для нового MP3. Если следующего файла ещё нет, плеер ждёт его независимо от синтеза — уже играющее аудио не может быть остановлено из-за проблем MRT.
- Состояния MRT `0` (кнопка временно отключена) и `2` (веб-движок ещё занят) **ожидаются до 18 секунд** с опросом раз в 250 мс; это больше не мгновенная ошибка.
- Ошибка синтеза приводит только к повтору **этого же** фрагмента с задержкой. Повтор не снимает галочку предзагрузки, не очищает MP3-очередь и не прерывает MediaPlayer. Повторяемые ошибки отражаются в подсказке и Logcat (`MRT-Prefetch`).
- JavaScript сообщения содержат идентификатор сеанса; запоздавшие `finished`, `chunk`, `error` другого сеанса игнорируются. Пока файл завершается в фоновом файловом потоке, новый синтез не запускается.
- Кэш остаётся приватным (`cacheDir/mrt_encoded_audio`), индекс SHA-256 от текста, голоса, скорости и эмоции, лимит 100 МБ по умолчанию. Есть запасной режим старого MRT без кэша (снимите галочку).

**Почему не отдельная Activity:** `Activity` — экран, а не изолированный аудиодвижок. Вместо второго экрана выделен `MrtPlaybackEngine.java`, который владеет двумя MediaPlayer и не зависит от методов синтеза WebView. Для гарантированной работы после закрытия экрана в будущем понадобится отдельный foreground service, он в этот выпуск не входит.

**Известные ограничения:** MRT/WebView по-прежнему требуется для генерации каждого MP3. Если облачный синтез работает медленнее реального чтения, между файлами может возникнуть пауза; очередность и отсутствие одновременного звучания гарантируются состояниями приложения, но полная бесшовность зависит от формата MP3. У исходного Python-перехватчика есть FFmpeg-пересборка; в Android её пока нет. APK и работа на физическом телефоне в данной среде не проверялись.

**Проверки:** Java-тест `tests/test_native_playback.sh` с имитацией Android MediaPlayer, тесты JS в `tests`, проверка XML и ZIP. Сборка и подпись APK выполняются через существующий GitHub Actions workflow, используя прежний keystore.

---

# Читалка RSVP — экспериментальная ветка MRT

## v1.11.15-beta — короткие фрагменты MRT и управление воспроизведением

- **Экран «Слушать онлайн · MRT»**: кнопка **«Размер фрагмента»** со значениями от 100 до 19000 символов, включая ручной ввод. Для быстрой проверки автоперехода выберите **100–300 символов**. Настройка сохраняется между запусками и доступна также в **Настройки → Аудио**.
- **«Слушать» и «Стоп»**: вместо ненадёжного JS `element.click()` при взаимодействии с MRT используется касание HTML-кнопки `#textSpeaker` посредством событий `MotionEvent`, переданных в Android `WebView`. Это адаптация подхода к реальному клику из MRT Desktop TTS. После запуска приложение проверяет, перешёл ли MRT в состояние `state-playing`. При отказе WebView появляется диагностическая подсказка.
- При остановке перед переходом к другому фрагменту дожидаемся обработки действия WebView; если WebView отклоняет касание и звук продолжает играть, предусмотрено аварийное `AudioContext.suspend()`.
- Прогресс EPUB/FB2 и аудиотеки по-прежнему независимы; сохранение MP3 и создание аудиокниги не включены.
- Поддержка фонового автоперехода зависит от реального поведения WebView: **сборка APK и работа кнопок на телефоне ещё не проверялись**.


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

## v1.11.16-beta — MRT: нативный экран озвучивания

- Вместо большого редактируемого WebUI текст текущего отрывка отображается в нативном Android TextView. Веб-движок MRT продолжает работать в WebView под панелью текста; сам код WebSpeechKit не переписывается.
- Голос, скорость и эмоция выбираются нативными кнопками. Варианты загружаются из исходных HTML-селекторов MRT, выбор сохраняется в настройках `reader`.
- Поле ввода оригинальной страницы становится read-only, WebView не запрашивает фокус и не создаёт Android-клавиатуру при программном касании.
- Сохранены Play/Pause/Stop, автоматическое продолжение, фрагменты 100–19000 символов (новое значение по умолчанию — 1200 символов; существующий выбор не сбрасывается).
- Оптимизированы *локальные* задержки при переходах (наблюдение окончания 850 мс вместо 1400 мс и старт после установки текста 80 мс вместо 700 мс). Сетевое время синтеза зависит от MRT и сервиса.
- Если запуск не подтверждается либо список голосов недоступен, появляется резервная кнопка «Показать веб-панель MRT».

**Ограничения:** фоновые ограничения WebView и фактическая работа в смартфоне не проверялись; не реализована параллельная предзагрузка следующего синтеза или сохранение MP3. Это экспериментальная функция, неофициальный API Yandex Cloud не используется.

* Предзагрузка MRT: 3–10 MP3-фрагментов (включая текущий), шаг 1. Параметр доступен в «Настройки → Аудио» и на экране MRT.


## MRT v1.11.24-beta — фоновая очередь

См. `MRT-BACKGROUND-QUEUE-v1.11.23.md`: отдельный `MrtDownloadService` и `MrtDownloadActivity`, ExoPlayer управляется только готовыми MP3, одна шкала текущего файла, автономное окно загрузки 3–10 относительно текущего фрагмента. Фоновый WebView требует проверки на устройстве.


### v1.11.24-beta — Original encoded audio format detection
MRT WebAudio can send non-MP3 encoded buffers. Keep the bytes, detect source audio MIME/extension, and play recognized formats without transcoding. See `MRT-AUDIO-FORMAT-FIX-1.11.24.md`.
