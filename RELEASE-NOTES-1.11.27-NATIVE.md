# ChitalkaRSVP v1.11.27-beta — native online SpeechKit prototype

Based on v1.11.26-beta from the Chitalka source ZIP.

## Changes

- `MrtNativeSpeechKitClient.java`: native OkHttp HTTP streaming of **official Yandex SpeechKit API v1**, preserving actual OGG/Opus bytes, no WebView / HTML / JS for this backend.
- MRT reader UI: `Настроить нативный TTS` opens a two-mode selector and API-key entry.
- `MrtDownloadService`: selectable synthesis backend while reusing persistent queue, per-book cache, per-clip redownload, notifications, foreground dataSync service, and independent native Media3 playback service.
- Backend-specific voice/speed/emotion preferences and cache keys prevent mixing web MRT clips with SpeechKit clips.
- Native 5000-character request limit respected by reducing the fragment length to 4900 without modifying the user's stored segmentation value.
- HTTP 401/403 stop autoload instead of infinite retry, network/decoder errors surface in queue status.
- Existing original MRT (WebView) and Edge TTS remain selectable. No regressions intended.
- New GitHub Actions `Build debug APK` workflow works without release-keystore secrets.

## Critical boundaries

**NOT a successful reverse-engineering of MRT UniProxy!** The native backend uses official SpeechKit API, not `uni.ws`, and requires the user's own Yandex Cloud API key and appropriate service access/billing. It cannot currently reproduce free MRT exactly. The original MRT mode still uses WebView for synthesis (but its playback is already native). Edge TTS remains a separate keyless native mode.

Source checks were run on this branch. **The APK was not compiled locally** because Android SDK/Gradle and external dependency access are not available in the environment. Full Android build and real-device network playback tests are outstanding.

## Build

1. Import this ZIP as a GitHub repository, or replace matching files in your existing Chitalka repository.
2. In **GitHub → Actions → Build debug APK (no release signing secrets) → Run workflow**, trigger a build. A commit to `main` also triggers it.
3. Download the `ChitalkaRSVP-v1.11.27-beta-debug-apk` artifact, extract `app-debug.apk`, and install the debug variant. Existing release installations may not accept it as an in-place update due to a signing-key mismatch; **back up app data first**, and never uninstall the installed version merely to install a debug APK.
4. To produce an upgrade-compatible release, use the existing signed-release workflow with the original release keystore and its GitHub secrets.

## Try native SpeechKit

Open a book → MRT → `Настроить нативный TTS` → select **Нативный Yandex SpeechKit API** → enter your personal API key → select a supported voice → Autoload → Play.

Do not share API keys in bug reports or logcat. They are stored in private Android SharedPreferences; no encrypted secret vault has been implemented. Do not distribute this configured APK or its app data to others.

## Device validation needed

- Official SpeechKit token authenticates and returns OGG/Opus data for a 300-character sample.
- Background download and media playback continue after locking the screen.
- Clip #1 and #2 have independent and decodable OGG files, and moving between clips works.
- Forced re-download of one clip does not remove others.
- MRT Web legacy, Edge and Android TTS still function independently.
