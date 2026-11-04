#!/usr/bin/env python3
"""Offline source/Java-regression QA for 1.11.130. No Android APK is built."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent
runs=[
('1.11.130 release source and XML contracts',[sys.executable,'qa/test_v11130_requirements.py']),
('Java syntax parser for complete source tree',['bash','qa/check_java_syntax.sh']),
('Executable true-sentence/history Java tests',['bash','-c','tmp=$(mktemp -d); trap "rm -rf $tmp" EXIT; javac -encoding UTF-8 -d "$tmp" app/src/main/java/ru/reader/rsvp/SentenceChunks.java app/src/main/java/ru/reader/rsvp/SentenceIndex.java app/src/main/java/ru/reader/rsvp/TtsSentenceHistory.java qa/TestSentenceIndex130.java && java -cp "$tmp" ru.reader.rsvp.TestSentenceIndex130']),
('TTS 800-char synthesis fragmentation',['bash','qa/check_sentence_chunks.sh']),
('Edge word-boundary seek',['bash','qa/test_edge_sentence_seek.sh']),
('Shared reader/TTS book session',['bash','qa/test_shared_book_session.sh']),
('Auto-pause (mandatory calls / optional media)',['bash','qa/test_v11129_audio_focus.sh']),
('Checked exception regression for comics',[sys.executable,'qa/test_v11126_compile_exception_fix.py']),
('Anonymous service method reference regression',[sys.executable,'qa/test_v11127_edge_resume_binding.py']),
('Comic feed tap-only gesture regression',[sys.executable,'qa/test_v11127_feed_tap_only.py']),
('Comic page gestures and pinch zoom',[sys.executable,'qa/test_v11126_comic_gestures_zoom.py']),
('Edge timing and background sync',['bash','qa/test_edge_clock_sync.sh']),
]
failed=[]
for label,cmd in runs:
 print('\n=== '+label,flush=True)
 result=subprocess.run(cmd,cwd=root)
 print('RESULT: '+('PASS' if result.returncode==0 else 'FAIL'),flush=True)
 if result.returncode: failed.append(label)
print('\nRELEASE 1.11.130: '+str(len(runs)-len(failed))+'/'+str(len(runs))+' PASS')
for label in failed:print('FAILED:',label)
sys.exit(bool(failed))
