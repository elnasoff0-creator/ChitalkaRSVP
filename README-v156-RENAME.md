# v1.11.156-beta: book title rename

## Usage

**Library**: Home > Library / Audio Library / Comic Library > three dots on the book > Rename > edit title > Save.

**Downloads**: Downloads > three dots on the download > Rename > Save. Available for RanobeLIB, MangaLib, audio, Edge and direct links.

**Download archive**: Archive > three dots on the card > Rename.

The label is changed in-app only. Files are not renamed or moved; download resume, cover cache, reading progress and bookmarks continue using the original identifiers. Alias persistence is kept in private SharedPreferences `book_display_titles_v156`.

## Build

Use Android Studio or the existing GitHub Actions workflow with Java 17, Gradle plugin and Android SDK 35. Suggested command:

```sh
gradle --no-daemon :app:assembleRelease --stacktrace
```

This source ZIP is not a signed APK. The previous RanobeLIB metadata compiler error is fixed; the full build was not available in the authoring environment.
