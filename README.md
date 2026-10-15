# ChitalkaRSVP v1.11.34-beta

Android (Java) source project. Package: `ru.reader.rsvp`, `versionCode 11041`.

## Changes

- Edge TTS: synthesis/download begins only from **Автоподгрузка**. The native player is independent and continues ready MP3 fragments without a separate autoplay setting. Cached MP3s can play with download disabled.
- Edge toolbar and controls: centered icons with dedicated ImageViews, current speed multiplier, queue and fragments selection.
- Online settings: removed redundant autoplay and prefetch switches, explanation and voice options; retained prefetch count and segment length. Voice selection remains in the Edge player.
- Audio settings: system TTS settings shortcut, 14 built-in equalizer modes and custom five-band profiles (including named saves).
- Appearance: centered preview text and simplified color headings. Updated tab music-note icon and GitHub web-update icon.
- Reader and audio players: microphone for TTS, list icon for chapters/fragments, unified rounded square buttons; removed player-level add button.
- MainActivity: universal add dialog for book files or audiobook folders. URL import remains only under Downloads.
- Back navigation and Downloads/URL import buttons aligned to the common design.

## Build

Use `.github/workflows/release.yml` or run `gradle assembleRelease` with Android SDK 35, Java 17, and signing configured.

The project is source code, **not a compiled APK**. Runtime / device testing still required.
