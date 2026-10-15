# ChitalkaRSVP v1.11.36-beta

Full Android Java source project — `ru.reader.rsvp`, `versionCode 11043`.

## v1.11.35: Unified online downloads

- New native Downloads screen with two approved icon buttons **Поиск аудиокниги** and **Импорт по ссылке**, four tabs, accurate counters and grouped cards for ongoing / completed tasks.
- Search through public knigavuhe.org `search/?q=` results; parse `div.bookkitem`, cover, title, author / narrator, preview text, duration and exact book URL. Multiple narrators remain separate; next-page button where the HTML exposes pagination.
- Selecting a search card opens the existing `UrlImportActivity` with its chapter preview and manual download confirmation; `DownloadWorker` is unchanged.
- `DownloadsRepository` reads the existing audiobook jobs and Edge TTS `queue.json` and connects to currently running Edge service for live status. No MP3 is duplicated.
- EPUB/FB2 book covers load with CoverLoader; audiobook covers load asynchronously from HTML image URLs with memory / disk caching. The approved two PNG icons are included as Android drawable-nodpi resources.
- Audio-book task actions: resume/pause/cancel and optionally keep or delete downloads. Edge cards open queue and player; deleting Edge cache never deletes the underlying text book. File explorer works for both Edge and downloaded audiobook folders.
- New Edge metadata file `book.json` is created when books are loaded; older Edge caches with MainActivity library entries are discovered without destructive migration. The total-progress percentage is not claimed until coverage is known.
- No change to the independent Edge player/download architecture, and no WebView/MRT.

## v1.11.36: Offline audiobook artwork

- Download `cover.jpg` from the book's real `og:image` when an audiobook download starts, save beside MP3 files under `Audiobooks/<title>/`, and validate/convert it to a bounded JPEG. Existing artwork is reused rather than downloaded again.
- Use that local image in **Загрузки**, **Библиотека**, and the audio player, including offline. A manually chosen cover is never replaced by the automatic default.
- Recover missing covers from already downloaded audiobooks when visiting the library, download dashboard, or audio player, without re-fetching chapters. Failed artwork requests do not stop MP3 downloading; background retries are throttled.
- Keep all prior downloads, source/playlist identifiers, and user-selected audio covers. Removing downloaded files intentionally also deletes the associated `cover.jpg`.

## Build

Use `.github/workflows/release.yml` (signed APK using existing repository secrets) or `.github/workflows/build-debug-apk.yml` (debug artifact). Java 17, Android SDK 35.

This archive contains source, **not a compiled or device-tested APK**. The public site may change its markup / throttle requests; network integration should be tested on a device. The parser uses only public page HTML and does not bypass login, DRM or protected media URLs.
