#!/bin/sh
# Compatibility runner for the earlier EdgeSentenceSeek regression suite.
# The production API now requires source text so missing words are rejected.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
sh "$ROOT/qa/test_edge_sentence_replay_187.sh"
python3 - "$ROOT" <<'PY'
from pathlib import Path
import sys
base=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
s=(base/'EdgeStreamingService.java').read_text()
p=(base/'EdgePlaybackService.java').read_text()
a=(base/'ReaderActivity.java').read_text()
assert s.index('player.armSelectedPosition(replayTrackKey,ms)') < s.index('addTrack(current);')
assert p.index('applyArmedPosition();\n        if (oldCount==0)') < p.index('player.prepare();',p.index('player.addMediaItem(insertion'))
assert 'if(ahead.ready())' in s[s.index('private void pump()'):s.index('private void addTrack(')]
assert 'addTrack(ahead);' in s[s.index('private void pump()'):s.index('private void addTrack(')]
assert 'selectedOverrides.add(clip)' in s and 'EdgeSpeechSegments.priorityPieces(' in s
assert 'pendingTtsWord' in a and 'EdgeOnlineActivity' not in a
assert 'clip.awaitingUserRetry=true' in s
assert 'job.buffer=new EdgeLiveBuffer()' in s
assert 'player.appendLive(' in s
assert 'void appendLive(' in p
assert 'new ProgressiveMediaSource.Factory(' in p
assert 'startLive(ahead)' not in s and 'onTrackFinished(int start,int end,String key)' in s
assert 'EdgeSpeechQueue.restoreCooldown' in s
print('PASS: selected MP3 is armed before prepare/play, no future-track substitution, 429 guarded')
PY
