#!/usr/bin/env python3
"""Offline, reproducible smoke/regression checks for 1.11.127 source tree.
Requires Python 3, javac, java and /bin/bash. NOT an Android Gradle build.
Historical QA files that hardcode versionCode 12600 are not a release gate.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
COMMANDS = [
    ('Java parser (syntax, no Android type checking)', ['bash', 'qa/check_java_syntax.sh']),
    ('Release-127 source contracts, Java percent calculation, XML', [sys.executable, 'qa/test_v11127_main_libraries_tts.py']),
    ('Compile hotfix: Edge resume callback qualified method reference', [sys.executable, 'qa/test_v11127_edge_resume_binding.py']),
    ('ReaderActivity matched ComicReaderActivity chrome', [sys.executable, 'qa/test_v11126_comic_reader_style.py']),
    ('Comics page/feed gesture and zoom', [sys.executable, 'qa/test_v11126_comic_gestures_zoom.py']),
    ('Previous compilation hotfix contract', [sys.executable, 'qa/test_v11126_compile_exception_fix.py']),
    ('TTS sentence chunk boundaries', ['bash', 'qa/check_sentence_chunks.sh']),
    ('Edge sentence seeking', ['bash', 'qa/test_edge_sentence_seek.sh']),
    ('Independent playback and sleep timers', ['bash', 'qa/test_v11119_listening_clock.sh']),
    ('Edge TTS clock syncing', ['bash', 'qa/test_edge_clock_sync.sh']),
    ('Shared sessions', ['bash', 'qa/test_shared_book_session.sh']),
    ('Audio focus', ['bash', 'qa/test_audio_focus_policy.sh']),
]
failed = []
for title, cmd in COMMANDS:
    print('\n### ' + title, flush=True)
    process = subprocess.run(cmd, cwd=ROOT)
    if process.returncode:
        failed.append(title)
print(f'\nRELEASE v1.11.127: {len(COMMANDS)-len(failed)}/{len(COMMANDS)} groups PASS',flush=True)
if failed:
    for item in failed: print('FAIL:', item)
    sys.exit(1)
