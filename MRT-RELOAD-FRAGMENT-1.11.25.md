# MRT selective reload: v1.11.25-beta

- Per-fragment retry icon in `MrtDownloadActivity` with confirmation.
- `MrtDownloadService.redownloadClip(startWord)` owns the operation and reprioritizes its next generation.
- `MrtAudioCache.invalidate(key, callback)` is serialized on the cache writer thread, deletes only the chosen audio, `.part` and legacy matching copy; retains `queue.json`.
- `MrtPlaybackService.evictCachedTrack()` detaches the stale queue item (unless currently audible); the rebuilt file is inserted in word order by `appendReady()`.
- Deduplicated cache keys are handled across all fragment positions.
- Supports a paused loader; selection starts foreground autoload. Independent playback of other files remains unaffected by synthesis failures.
- Caveat: a system file explorer may reject OGG codecs even when audio bytes are valid. Retry does not transcode.
