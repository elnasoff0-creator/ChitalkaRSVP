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
assert 'startLive(ahead)' not in service, 'following MP3 is still being synthesized speculatively'
assert 'nearEndOfCurrentAudio' not in service, 'old speculative lookahead threshold remains'
assert 'onTrackFinished(int start,int end,String key)' in service
assert 'anchorWord=end;' in service[service.index('onTrackFinished('):service.index('onPlaybackStateChanged()')]
assert 'onTrackFinished(completed.start,completed.end,completed.key)' in player
assert 'state == Player.STATE_ENDED' in player
assert 'else if (insertion==oldCount && player.getPlaybackState()==Player.STATE_ENDED && requestedPlay)' in player
assert 'socketTerminated(ws)' in engine and 'endSocket(old,false)' in engine
assert 'setInteractivePlayback' in queue and 'filter(t->!interactivePlayback||t.priority==PLAY)' in queue
assert 'FIRST_TARGET=900' in (root/'EdgeSpeechSegments.java').read_text()
print('PASS: no future-block synthesis, end-of-track continuation, close-ack queue, 900-char groups')
PY
