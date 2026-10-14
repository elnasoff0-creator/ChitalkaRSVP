# ChitalkaRSVP 1.11.29-beta

Android book reader with RSVP, local audiobook player, Android system TTS, and **Edge TTS online**.

## Speech modes
- System Android TTS (device-installed voices).
- Edge TTS online: native Android Activity, independent EdgeDownloadService queue, EdgePlaybackService / Media3 ExoPlayer and validated per-book MP3 cache. No WebView. Cached clips play without a network connection.
- Edge settings: voice, speed, fragment length, prefetch window, automatic continuation.
- Per-fragment manual re-download and audio-file export from the Edge queue.

## Migration from 1.11.27
- Removed the retired browser-based synthesis engine and its Activities, Services, JavaScript and queue.
- Existing Edge files remain under Documents/ChitalkaRSVP/Books/<book-id>/EdgeTTS; hashes and folder identifiers are unchanged.
- The old shared queue preferences are migrated to `edge_*` keys once; retired credentials are cleared from app preferences.
- Old audio files from the deleted engine are NOT automatically deleted during update (to avoid deleting user data).

## Building
Open in Android Studio with JDK 17, compileSdk 35. Or use `.github/workflows/build-debug-apk.yml` on GitHub Actions. Android SDK build and end-to-end audio testing on a device are required before treating this as a verified APK release.

## Fixing an existing GitHub repository after overlay upgrades

Older tracked `Mrt*.java` files may remain in a GitHub checkout even if the
latest source ZIP excludes them. This prevents the Java compiler from running.
Release/debug CI workflows now remove them before compiling. Permanently fix
your repository by staging deletions of all legacy `Mrt*.java` files and the
old `mrt_online_capture.js`, and commit the removed paths. The Edge-only ZIP
contains no such Java source files.

Note: the few historical `mrt_` preference strings and `mrt-prefetch-v1` hash
namespace in Edge helpers are **backward-compatibility identifiers only**.
They do not run the deleted MRT engine and preserve existing Edge MP3 cache.
