#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$ROOT" <<'PY'
import pathlib, sys
root=pathlib.Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
service=(root/'EdgeStreamingService.java').read_text()
player=(root/'EdgePlaybackService.java').read_text()
engine=(root/'EdgeSpeechEngine.java').read_text()
queue=(root/'EdgeSpeechQueue.java').read_text()
assert 'prefetchWindow(current)' in service, 'bounded candidate prefetch missing'
assert 'nearEndOfCurrentAudio' not in service, 'old speculative lookahead threshold remains'
assert 'onTrackFinished(int start,int end,String key)' in service
assert 'anchorWord=end;' in service[service.index('onTrackFinished('):service.index('onPlaybackStateChanged()')]
assert 'onTrackFinished(completed.start,completed.end,completed.key)' in player
assert 'state == Player.STATE_ENDED' in player
assert 'else if (insertion==oldCount && player.getPlaybackState()==Player.STATE_ENDED && requestedPlay)' in player
assert 'EdgeWebSocketPool.recycle(resource)' in engine and 'EdgeWebSocketPool.discard(resource,true)' in engine
assert 'setInteractivePlayback' in queue and 'MAX_PREFETCH=2' in queue
assert 'FIRST_TARGET=800' in (root/'EdgeSpeechSegments.java').read_text()
print('PASS: candidate prefetch, end-of-track continuation, reusable sockets, 800-char groups')
PY
