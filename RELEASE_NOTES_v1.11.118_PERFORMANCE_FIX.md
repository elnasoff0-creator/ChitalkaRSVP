# PROChitalka 1.11.118-beta / versionCode 11800 — TTS performance and sleep timer fix

Base: PROChitalka-v1.11.118-beta-CLEAN-Full-Source.zip.
This is a same-version source update, not a new APK build.

## Changes

1. ReaderActivity sleep icon is now a 44dp rounded icon+minutes badge in Android TTS and Edge TTS. For fixed timers it shows the ceiling of remaining minutes; for end-of-chapter timers it estimates the time to the selected chapter boundary using the actual playback position and current WPM. When no trustworthy table of contents exists, existing text blocks provide the stop boundary.
2. AudioPlayerActivity end-of-chapter badge now uses AudioPlaybackService.getChapterSleepRemainingMillis() and converts track duration to wall-clock time at the current playback speed. If audio duration metadata is unavailable it displays the chapter indicator instead of an invented time.
3. Removed dictionarySelectedWord and its independent background spans from ReaderActivity. WordBoundary still highlights the active word and a tap still selects the sentence for playback from its beginning.
4. ReaderActivity passes the current sentence into EdgeDictionaryActivity without forcing a word choice or auto-opening an edit dialog. In its word editor, the adjacent "Из предложения" button opens a word list from that actual sentence and copies the selected word into "Слово в книге".
5. TTS UI refresh no longer polls while playback is idle, except for a 15-second timer-badge refresh if a sleep timer is active. While playing, the existing 180ms position polling remains for word-level synchronization. Android TTS media state broadcasts and Edge playback callbacks can restart the loop immediately. UI updates for unchanged play icons, overlay text, and timer labels are suppressed.
6. LocalTtsWordTimings sidecars are decoded once per chunk / file-metadata change in AudioPlaybackService. LocalTtsWordTimings.at() uses binary search.
7. ReaderActivity avoids an unnecessary full initial render before a book is loaded; shows a lightweight loading view and creates the complete reader once after the book session is prepared.
8. ReaderDiagnosticLog events prefixed READER_STARTUP record create / first UI time, background book preparation, post-load UI work, first full frame, and Edge service bind times. These are measurement hooks; **no on-device timing measurements were performed**.

## Regression scope

- Core Edge streaming, text transforms, cache, WebSocket and WordBoundary paths unchanged.
- Dictionary pronunciation rules, ignore rules, stress selection, and audio preview remain.
- Same versionName / versionCode as requested.

## Testing and caveats

- Java syntax parser and structural call-resolution tests pass (these are not Android type checks).
- 41 existing/new source/JVM test scripts: 40 passed in the parallel runner, one timing-sensitive clock-sync test failed under parallel load but passed when rerun independently; a previous run had all 40 original tests passing apart from an outdated static assertion revised for the approved removal of dictionarySelectedWord.
- New performance/sleep/dictionary source contract test passes all 13 checks.
- No Android SDK/Gradle build or phone instrumentation available; actual layout, service lifecycle, playback focus, and startup latency require device QA.
