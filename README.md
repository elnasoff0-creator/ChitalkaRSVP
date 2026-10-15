# ChitalkaRSVP v1.11.37-beta

**versionCode: 11044.** Complete Android Java source project intended for GitHub Actions Gradle build.

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

## Updated v1.11.37-beta (same versionCode 11044) — text files via link

- The original v1.11.37-beta source package is updated in-place as requested (no version bump).
- Direct and supported public cloud HTTPS links now accept MP3, FB2, and EPUB with explicit content verification and correct filename extensions/MIME types.
- Save FB2/EPUB directly to the user-selected SAF folder, alongside MP3 if selected. Rename/duplicate handling retains the actual extension.
- Validated FB2/EPUB are automatically added to the reading library and can be opened from download details. Existing MP3/audio-library flow remains unchanged.
- No EPUB/FB2 parsing of arbitrary websites or private cloud file shares. ZIP EPUB, XML FB2 and audio signatures are verified before files are published.
