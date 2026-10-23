# Edge WordBoundary synchronization for Android

Reference: https://github.com/sguzman/edge-tts

The browser extension is not ported directly. Its fundamental sync invariant is:
**WordBoundary offsets in the ORIGINAL encoded audio clock map to text offsets,
while the audio player's source-media clock drives the currently highlighted word.**

`EdgeTtsClient`: sends `wordBoundaryEnabled=true`, receives `Path:audio.metadata`
JSON (WordBoundary; `Data.Offset`, `Data.Duration`, `Data.text.Text`) alongside
`Path:audio` frames. Each offset/duration is in 100-nanosecond ticks.

`EdgeWordTimings`: aligns the emitted spoken words against actual source text in
UTF-16 positions, using NFKC normalization and punctuation-insensitive sequential
matching. Unexpected words are skipped, not estimated. Converts ticks to ms.

`EdgeDownloadService` and `EdgeFullBookService`: save source MP3 and sidecar
`filename.mp3.words.json` in `EdgeTTS` under the existing book folder. The sidecar
stores a SHA-256 text fingerprint and ordered time/range tuples, written atomically.
It is not deleted during normal playback or routine app restart.

`EdgeOnlineActivity`: keeps its displayed source text; a light 85ms callback looks
up `EdgePlaybackService.positionMs()` against the loaded timing list and applies a
span only when the matched word changes. This is independent of user-selected WPM;
Media3 `setPlaybackSpeed()` changes how quickly media-time advances without changing
the meaning of the original timing positions.

Old MP3 cache: untouched. No true word timings are available for it, therefore no
fabricated per-word highlighting is shown. Existing queue action can re-download a
clip to obtain a fresh sidecar (avoid syncing metadata from a DIFFERENT generation).

Do not remove scheduler/prefetch before a separate migration plan. Existing job
model has durable audio and resume semantics which are separate from highlighting.
