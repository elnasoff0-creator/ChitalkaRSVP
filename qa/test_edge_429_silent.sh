#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$ROOT" <<'PYQ'
import sys
from pathlib import Path
src=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
queue=(src/'EdgeSpeechQueue.java').read_text()
reader=(src/'ReaderActivity.java').read_text()
stream=(src/'EdgeStreamingService.java').read_text()
full=(src/'EdgeFullBookService.java').read_text()
engine=(src/'EdgeSpeechEngine.java').read_text()
assert 'MAX_LOCAL_DELAY_MS=500L' in queue
assert 'SystemClock.elapsedRealtime()-lastLimitedAt>=MAX_LOCAL_DELAY_MS' in queue
assert 'Math.min(remaining,MAX_LOCAL_DELAY_MS)' in queue
assert 'blockedUntil=Math.max(blockedUntil' in queue
assert 'EdgeSpeechQueue.limited(response)' in engine
assert 'problem.contains("HTTP 429")' in reader
assert 'ttsStreamer.lastRateLimit()' in reader
assert 'private volatile boolean lastHttp429' in stream
assert 'if(problem.contains("HTTP 429"))lastHttp429=true;' in stream
assert 'if(problem.contains("HTTP 429")){' in stream
assert 'Log.w("EdgeSpeech", "Edge TTS HTTP 429;' in stream
assert 'error="Edge TTS ограничил запросы' not in stream
assert '"Подготовка приостановлена"' in full
print('PASS: fixed local 500ms 429 delay; no adaptive backoff; no foreground Toast')
PYQ
