# ChitalkaRSVP v1.11.39-beta

**versionCode: 11046.** Complete Android Java source for GitHub Actions.

## v1.11.39-beta — audiobook playback continuity and controls

- Sleep-timer countdown in the ordinary MP3 player and Edge TTS toolbar timer buttons (minutes, seconds under one minute), retaining centered toolbar icons.
- MP3 second/minute skips cross chapter boundaries forwards and backwards, preserving the playing/paused state; chapter selection now preserves playback state too.
- MainActivity **Продолжить** starts playing immediately; ordinary book-card opening remains a non-autoplay action.
- More reliable chapter/offset restoration after application restart: save the intended target while asynchronous MediaPlayer seeking is pending; do not record its temporary zero position. Also supports Play pressed while chapter preparation is in progress.
- Audio tab has a configurable pause/resume auto-rewind: off, 5, 10, 15, 30 or 60 seconds. On resume, the selected rewind can also cross into the previous chapter.
- Adjustable seek button intervals: seconds 1..3600 (default 10) and minutes 1..120 (default 1). The captions update after returning from Settings.
- Maintains the full Edge offline-generation, knigavuhe search and audiobooks, direct MP3/EPUB/FB2 imports, artwork and download dashboard.

**Testing limitation:** Java syntax/source integration, chapter-boundary pure Java tests and existing shell tests can run locally; complete Gradle compilation requires Android SDK and a device test to verify MediaPlayer behavior.

---

# Earlier release notes: v1.11.38-beta

**versionCode: 11045.** Complete Android Java source project intended for GitHub Actions Gradle build.

## v1.11.38-beta — knigavuhe.org search and audiobook selection update (same versionCode 11045)

- Search queries still use the public `https://knigavuhe.org/search/?q=...` endpoint with URL-encoded Russian text. The native results screen now separates **Книги** and **Циклы**, retains genuine artwork, lists different narrator editions as individual URLs and displays the site's total count when it is available.
- Improved `KvuheSearchParser` handles book cards, a separate `/series/` result group, common lazy-loading cover attributes and both pagination links and page-number-based "Показать ещё" (capped at 100 pages, with a duplicate-page stop). If the website returns no results or rate-limits requests, the UI shows a helpful error, not made-up results.
- Selecting a book opens `KvuheBookActivity`: actual book cover, author/narrator/description from search, fetched chapters and their durations, MP3 availability, **Скачать выбранные главы**, **Скачать всю аудиокнигу**. Selecting a series opens `KvuheSeriesActivity` with its book cards; no mass downloading starts without confirmation.
- Existing `UrlImportActivity` chapter parsing, `BookImportManager`, `DownloadWorker`, downloaded MP3 and offline `cover.jpg` behavior are retained. Edge TTS full-book generation, standalone MP3/FB2/EPUB link downloader and original audio player remain unchanged.
- New `KvuheSearchPaging` helpers have runtime tests for Russian count forms and preservation of URL query during pagination. A static source integration test checks screen navigation and legacy downloader connections.

**Important testing limitation:** the live site's raw HTML could not be fetched from the build environment; HTML selectors are implemented defensively but still need a device test against today's website. Java sources were parsed and local shell checks passed; complete Gradle APK compilation requires Android SDK in GitHub Actions.

## Changes — «Скачать по ссылке»

- The **Загрузки** screen retains **Поиск аудиокниги** (knigavuhe.org) and replaces the former direct-URL entry button with **Скачать по ссылке**, using the previously approved chain/download icon.
- Independent **LinkDownloadActivity** supports pasting multiple public HTTPS MP3, FB2 and EPUB links, checking file formats and content, displaying name/size/availability, selecting files, and renaming before download.
- Android **Storage Access Framework** folder picker with persistable write permission. Save directly to the chosen folder, optionally create a named subfolder. Previous folder is remembered. Name conflicts receive numbered suffixes rather than overwrites.
- Built-in public cloud handling: **Yandex Disk** shared MP3, FB2 and EPUB files and first page (up to 100 immediate items) of a public shared folder, **Dropbox** downloadable public MP3/FB2/EPUB shares, and **Google Drive** public files *only when they resolve directly to downloadable supported files*. Unsupported preview pages, authentication, confirmation screens, or responses that are not valid MP3/FB2/EPUB are reported as errors, not saved as fake files.
- Dedicated **DirectLinkDownloadService** foreground queue with persisted batch state, per-file progress, pause/resume (`HTTP Range` when offered), cancellation and partial-file handling; file bytes are staged in an app-specific `.part` file before publishing through SAF. MP3 can be explicitly added to the audio library; completed FB2/EPUB are validated and automatically indexed in the reading library.
- **Загрузки** now includes **По ссылке** filter and direct-link batch cards, progress, context menu, details view and optional removal of owned files. Original audiobook search/download and Edge TTS components remain separate.

**Limitations:** Provider behavior may vary over time. A Google Drive public link that requires a download confirmation page is not yet supported. Mail.ru and OneDrive sharing APIs, Dropbox folders, recursive cloud folders and authenticated access are not included. The app does not scrape arbitrary web pages. Downloads use available app-specific staging storage before writing to the SAF destination, so available temporary space is required. Partial jobs require manual resumption after a process restart. No rights restrictions or access controls are bypassed.

## Build

Upload project **contents** including `.github` to your repository. Actions workflow builds debug APK. Requires Android SDK 35, Java 17 and Gradle 8.7. Run `bash tests/test_direct_links_11137.sh` and the remaining `tests/*.sh` checks before publishing.

## Legacy features retained from v1.11.37-beta — text files via link

- All direct-link improvements from v1.11.37-beta remain available.
- Direct and supported public cloud HTTPS links now accept MP3, FB2, and EPUB with explicit content verification and correct filename extensions/MIME types.
- Save FB2/EPUB directly to the user-selected SAF folder, alongside MP3 if selected. Rename/duplicate handling retains the actual extension.
- Validated FB2/EPUB are automatically added to the reading library and can be opened from download details. Existing MP3/audio-library flow remains unchanged.
- No EPUB/FB2 parsing of arbitrary websites or private cloud file shares. ZIP EPUB, XML FB2 and audio signatures are verified before files are published.


## v1.11.38-beta — full offline Edge TTS audiobook preparation

- **Загрузки → Подготовить всю книгу Edge TTS:** select a library FB2/EPUB with its actual cover, pick Svetlana or Dmitry and confirm a frozen segmentation size, voice and synthesis rate.
- Independent foreground `EdgeFullBookService` synthesizes all missing MP3 segments **from the beginning to the end of the book**, including while the Edge player is closed. The service does not bind to the playback service or change the active player book.
- The new `EdgeTextSegments` logic is shared between autoload and full-book mode to make cache segment boundaries and hashed filenames compatible. `EdgeGenerationGuard` prevents simultaneous synthesis of identical cached files.
- `EdgeFullBookStore` retains a durable `full_job.json` and a separate full segment index `EdgeTTS/full_queue.json`. Existing `EdgeTTS/<hash>.mp3` files are reused. The existing `EdgeTTS/queue.json` belongs to the autoload queue and remains independently managed.
- Download cards show real prepared/total counts, pause/resume and offline playback when the entire book is ready. Clicking **Слушать офлайн** offers to use the voice/rate/segment length from the completed job when these differ from current Edge preferences.
- The independent Edge playback service continues to use the same cache; starting/pausing a full-book download never starts or pauses the audio player. Android may suspend foreground data-sync jobs after platform-imposed runtime limits. Resume such jobs from Downloads.
- `EdgeBookStorage` reuses folders by stable URI hash, avoiding duplicate folders when embedded book titles differ from library titles.

### Verification and limits

Run `bash tests/test_edge_full_book_11138.sh` and all existing `tests/test_*.sh` scripts. Java 17 source syntax and Android XML have been checked. A full Android Gradle compile, real Edge WebSocket synthesis, Android background limits and playback on a physical device still require GitHub Actions / phone verification. This source ZIP does **not** contain an APK.
