# ChitalkaRSVP 1.11.28-beta — Edge-only online synthesis

- Removed the retired MRT browser TTS, SpeechKit experiment, associated UI, services, asset and tests.
- EdgeDownloadService + EdgeQueueActivity remain as the **only** online synthesis queue.
- Preserved Edge TTS file cache and audio queue; migrated shared preferences to Edge-only names.
- Retained independent foreground media playback for Edge TTS and existing Android TTS/audiobooks.
- Fixed Edge folder explorer access to actual EdgeTTS directory.
- No Gradle/Android SDK available in this container; run Android Studio or GitHub Actions for final APK compilation.
