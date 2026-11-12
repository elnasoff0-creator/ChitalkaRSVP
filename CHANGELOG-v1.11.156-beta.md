# PROChitalka v1.11.156-beta

Base: v1.11.155-beta CLEAN Full Source. Source-only release.

## Rename books and download jobs

- Added **Rename** to the existing three-dot menus for textual books, audio books and comics on Home (Library, Audio Library and Comic Library).
- Added **Rename** to the download overflow menus for RanobeLIB, MangaLib, Kvuhe/audiobook, Edge TTS and direct-link downloads.
- Added Rename via a three-dot overflow to the archived download cards.
- A shared title editor pre-fills the existing name, limits input to 120 characters, rejects blank names and saves changes synchronously before closing.
- Aliases persist across app restarts, background progress updates, chapter downloads and library rescans.
- When the library and download point to the same source (RanobeLIB, MangaLib, Edge, Kvuhe, direct-link audio), renaming one updates the visible name in the other.
- Source files are **never** moved or renamed. Original downloader job titles, IDs and URI-based reading progress remain intact. Archive records keep a separate `sourceTitle` to avoid using a user alias in filesystem operations.
- The inactive audiobook *Change cover* menu action in the previous source was accidentally wired to the library-removal action. It now clearly reports unavailable rather than deleting the library entry. This is not a new cover picker.

## CI build error

- Fixed the checked `org.json.JSONException` thrown by `RanobeLibApi.Title.json().toString(2)` in `RanobeLibStorage.rememberTitle()` (the cause of the supplied `compileReleaseJavaWithJavac` failure).

## Files

- New: `BookTitleStore.java`, `BookRenameDialog.java`.
- Updated: `MainActivity.java`, `ReaderActivity.java`, `DownloadsRepository.java`, `DownloadActivity.java`, `DownloadArchiveActivity.java`, `DownloadArchiveStore.java`, `DownloadArchiveDelete.java`, `DownloadListSnapshot.java`, `AudioLibrary.java`, `ComicLibrary.java`, `BookImportManager.java`, `LinkDownloadStore.java`, `RanobeLibStorage.java`, `RanobeLibBookActivity.java`.
- Android version: `versionCode 15600`, `versionName 1.11.156-beta`.

## Validation

- Java syntax parsed successfully for all 156 source files.
- All Android XML resources and manifest are well-formed.
- Standalone compiled Java test (small Android API stubs) validates the alias registry and bidirectional library/download name synchronization.
- Existing v155 RanobeLIB/game regression test passes; v152 Edge queue contract regression test passes.

**Limitations:** Android SDK/Gradle are not installed in the authoring container. A full `:app:assembleRelease` build and device test could not be performed here. Please run the CI build and report any additional compiler messages.
