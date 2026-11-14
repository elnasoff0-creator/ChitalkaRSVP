#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
python3 - "$ROOT" <<'PYTEST'
from pathlib import Path
import sys
root=Path(sys.argv[1]);src=root/'app/src/main/java/ru/reader/rsvp'
q=(src/'EdgeSpeechQueue.java').read_text();e=(src/'EdgeSpeechEngine.java').read_text()
assert 'MAX_LOCAL_DELAY_MS=15*60_000L' in q
assert 'BACKGROUND_GAP_MS=500L' in q
assert 'Math.min(remaining,MAX_LOCAL_DELAY_MS)' in q
assert 'INITIAL_429_DELAY_MS=60_000L' in q
assert 'consecutiveLimits' in q and 'retryAfterMillis(response)' in q
assert 'EdgeWebSocketPool.discard(resource,true)' in e and 'socketTerminated(resource)' in e
assert '},500,TimeUnit.MILLISECONDS);' in e
assert '1500,TimeUnit.MILLISECONDS' not in e
assert '250,TimeUnit.MILLISECONDS' not in e
assert 'INITIAL_429_DELAY_MS' in q
assert not (src/'EdgeRateLimitPolicy.java').exists()
print('PASS: adaptive 429 cooldown and guarded abort fallback')
PYTEST
