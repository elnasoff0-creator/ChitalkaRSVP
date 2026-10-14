# ChitalkaRSVP v1.11.33-beta

Native Android source project (Java, Gradle 8.7, JDK 17, Android SDK 35).

## Building on GitHub

Upload the **contents** of this archive to the repository root, including
`.github/workflows/`. Files removed from this source tree must also be removed
from the repository: uploading files through the browser does not automatically
remove older files.

GitHub Actions workflows: `.github/workflows/build-debug-apk.yml` and
`.github/workflows/release.yml`. The release workflow uses repository signing
secrets. Android SDK and signing material are not bundled.

## What's new (v1.11.33-beta / versionCode 11040)

- Edge toolbar icons centered; the toolbar speed control displays the effective
  playback rate (1×, 1.25×, etc.) and persists it between service starts.
- Edge cover card no longer displays the extra voice-change explanation.
- System TTS screen uses just **Previous fragment / Play-Pause / Next fragment**;
  chapter and seconds skip buttons remain available for ordinary audio files.
- In system TTS the embedded book cover is above the fragment text; the
  whole-audiobook duration slider is hidden. Standard audiobook UI is preserved.
- Settings tabs: Appearance / Speed Reading / Audio / Online, each with an icon.
  RSVP settings are separated from appearance settings.
- Audio settings: equalizer presets for supported MP3 playback audio sessions,
  installed system TTS engine selection, and voice/pitch. Built-in Android
  TextToSpeech does not expose an independent audio session for this equalizer.
- Online settings and existing Edge download/cache workflows are retained.

## Testing

Run `for script in tests/*.sh; do bash "$script"; done` from project root.
A full Android Gradle build and on-device testing are required before release;
local source tests do not validate APK compilation or runtime functionality.
