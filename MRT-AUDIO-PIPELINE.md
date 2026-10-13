# MRT MP3 pipeline in ChitalkaRSVP 1.11.18-beta

## Components

- `MrtOnlineActivity`: native text/controls; playlist of `PrefetchClip` structs, generation state and scheduling. Only this controller may add/remove clips to/from the queue.
- `mrt_online_capture.js`: intercepts original encoded buffers in `decodeAudioData` and sends session-tagged audio events to the trusted WebView messaging bridge; routes generated sound to zero-gain WebAudio node.
- `MrtAudioCache`: single-threaded writer for `.part` files, format validation and promotion to `.mp3`, cache reuse by SHA-256 key.
- `MrtPlaybackEngine`: a standalone Android MediaPlayer transport. It sees only ready file paths and tags; it has no WebView, JavaScript or cache handling. One audible player and one silently prepared standby player.

## Lifecycle

1. Press **Слушать**. Controller fills a window of `prefetchAhead` clips (current + 2..9 future clips, configurable 3..10). Cached MP3 paths are reused.
2. Missing first MP3: MRT receives its text, WebView decodes original encoded chunks, JS emits `audioPart` with `session` id. Writer queues data and validates final MP3.
3. File ready: playback engine starts first clip (only after MP3 validation), while synthesis continues on the next missing clip.
4. Standby second MP3 may be decoded/prepared early but MUST NOT play before active completion.
5. On `MediaPlayer` completion, queue removes the finished clip. `MrtPlaybackEngine` plays the standby file if prepared, otherwise it opens the next cached file asynchronously. The controller appends new tail clips to maintain the ahead limit and starts new WebUI synthesis.
6. If next file is missing at completion, no sound is started; as soon as generator finishes the file, the controller calls playback.play().
7. On failed generation, `scheduleRetry` delays only the generator; native player is not stopped and prefetch checkbox stays enabled. WebUI transient busy states poll every 250ms for up to 18s. Repeat retry delays grow then cap at 12s.
8. Explicit **Стоп**, manual toggle off, changing fragment size, navigation or closing the Activity still stop native playback as requested.

## Diagnostic test

- Turn on automatic continuation, set 300 chars and 1 clip ahead.
- Start uncached first clip; wait for playback. Inspect that the next text starts generating without muting the current Android MP3.
- With 2 clips ahead, wait until files 2 and 3 are cached; check file 2 is not audible until file 1 ends.
- Run the same test when first MP3 is already cached. Starting second synthesis must not release Android MediaPlayer even if WebUI is busy.
- If repeated failure occurs, Logcat filter `MRT-Prefetch` contains a clip number and reason. Playback errors are `MRT-Playback`.

## Limitations

The app does not bundle FFmpeg; concatenated encoded chunks are checked for MP3 compatibility but may not play perfectly on every Android version. The controller is Activity-scoped: background play may work while the app is minimized, but Android can suspend WebView work and closing the screen ends playback. A foreground playback service and Android notifications are a separate future change.
