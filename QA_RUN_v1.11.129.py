#!/usr/bin/env python3
"""Reproducible offline v1.11.129-beta QA. Does NOT compile Android SDK targets."""
from pathlib import Path
import subprocess,sys
ROOT=Path(__file__).resolve().parent
COMMANDS=[
 ('Release 129 implementation/source/XML contracts',[sys.executable,'qa/test_v11129_requirements.py']),
 ('Java parser: all sources',['bash','qa/check_java_syntax.sh']),
 ('Autopause: call mandatory, media optional',['bash','qa/test_v11129_audio_focus.sh']),
 ('TTS sentence boundaries/navigation',['bash','-c','tmp=$(mktemp -d); trap "rm -rf $tmp" EXIT; javac -encoding UTF-8 -d "$tmp" app/src/main/java/ru/reader/rsvp/SentenceChunks.java app/src/main/java/ru/reader/rsvp/SentenceIndex.java qa/TestSentenceIndex129.java && java -cp "$tmp" ru.reader.rsvp.TestSentenceIndex129']),
 ('Previous sentence chunking regression',['bash','qa/check_sentence_chunks.sh']),
 ('Edge sentence seek regression',['bash','qa/test_edge_sentence_seek.sh']),
 ('Comic gestures and pinch zoom',[sys.executable,'qa/test_v11126_comic_gestures_zoom.py']),
 ('Comic feed tap-only regression',[sys.executable,'qa/test_v11127_feed_tap_only.py']),
 ('Comic checked exception hotfix',[sys.executable,'qa/test_v11126_compile_exception_fix.py']),
 ('Edge method reference compilation hotfix',[sys.executable,'qa/test_v11127_edge_resume_binding.py']),
 ('Independent audio/TTS sleep clocks',['bash','qa/test_v11119_listening_clock.sh']),
 ('Edge clock synchronization',['bash','qa/test_edge_clock_sync.sh']),
 ('Shared book session',['bash','qa/test_shared_book_session.sh']),
]
failed=[]
for name,cmd in COMMANDS:
 print('\n### '+name,flush=True)
 result=subprocess.run(cmd,cwd=ROOT)
 if result.returncode: failed.append(name)
print('\nRELEASE 1.11.129: %d/%d groups PASS'%(len(COMMANDS)-len(failed),len(COMMANDS)),flush=True)
if failed:
 for n in failed: print('FAIL: '+n)
 sys.exit(1)
