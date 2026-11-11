#!/usr/bin/env python3
"""Regression guard for v1.11.151 Edge downloads repository Java compile issue."""
from pathlib import Path
import re

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/ru/reader/rsvp/DownloadsRepository.java').read_text(encoding='utf-8')
assert re.search(r'private\s+static\s+Item\s+fromEdge\(\s*Context\s+ctx\s*,', source), 'fromEdge must receive Context ctx'
assert 'EdgeFullBookStore.find(ctx,uri,title)' in source
calls = re.findall(r'\bfromEdge\(([^;\n]*)\)', source)
assert len(calls) == 4, f'Expected declaration and 3 calls, got {len(calls)}'
assert all(c.strip().startswith(('Context ctx,', 'ctx,')) for c in calls), calls
print('PASS: DownloadsRepository.fromEdge declares and forwards Context at all 3 call sites')
