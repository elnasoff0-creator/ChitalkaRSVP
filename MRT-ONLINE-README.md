# Online listening in Chitalka RSVP 1.11.12-beta

This update builds on 1.11.11-beta and uses the original hosted Machine Reading Text WebSpeechKit UI, **not** the official Yandex Cloud API.

## Open online listening

* Open an EPUB/FB2/HTML book -> ReaderActivity -> audio / text-to-speech -> **Слушать онлайн (MRT)**.
* Or: Settings -> **Аудио** -> **Слушать онлайн (MRT)**.
* Or: AudioPlayerActivity -> More -> **Слушать онлайн (MRT)** (reads the current electronic book, not MP3 tracks).

Wait until MRT loads. Choose voice, emotion, and speed in the hosted MRT page and tap **Слушать**. On some WebView builds the first click MUST be on MRT's visible web button because browser user activation cannot always be synthesized.

Controls: start, pause/resume, stop, previous/next text segment, optional automatic advancement. Segments are max 8000 characters and split on punctuation/word boundaries. The position is stored at segment boundaries in `tts_word:<uri>` and the text-book library record. MP3 audiobook positions and book IDs are not changed.

## Capture mechanism from the user's PyQt5 prototype

`app/src/main/assets/mrt_online_capture.js` intercepts `AudioContext.decodeAudioData` early in the document (AndroidX WebKit `addDocumentStartJavaScript`). It forwards **chunk counts / encoded-byte counts and lifecycle events only** through an origin-restricted `addWebMessageListener`. It does not transfer bytes, produce MP3, or save audio. The original WebAudio playback is used for listening.

To protect the native bridge, only `https://alkohole.github.io` main frames are accepted, and navigation is restricted to `/machine-reading-text/`. On old WebViews without `DOCUMENT_START_SCRIPT`, listening remains available, but the JS observer is installed late and some completion detection may not work.

## Honest limitations

* Original MRT page requires internet and can change its interface or remove features without notice.
* The first speech start and automatic continuation may require a physical click depending on Chromium / WebView policy and the MRT version; the on-screen source button remains available.
* Progress is an approximation by completed **text segments**, not word-level speech timestamps.
* No separate Android audio foreground service or notification controls for WebView audio yet; playback may stop when the app is backgrounded or the screen turns off.
* Detected playback completion is based on the source button's `state-playing` state with a debounce. Pausing manually and slow network responses can affect it.
* No audiobook/MP3 generation, FFmpeg, or saving synthesized speech in this update.
* This code is not verified by a full Android build or run on a physical phone in this environment.
