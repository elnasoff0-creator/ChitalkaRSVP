#!/usr/bin/env python3
"""v1.11.131 CLEAN offline QA. The Android SDK is needed for release assembly."""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent
steps=[
 ('v1.11.131 feature contracts',[sys.executable,'qa/test_v11131_features.py']),
 ('ReaderActivity unified progress chapter type regression',[sys.executable,'qa/test_v11131_progress_chapter_types.py']),
 ('133 Java sources syntax',['bash','qa/check_java_syntax.sh']),
 ('True sentence segmentation and TTS pause history',['bash','-c','tmp=$(mktemp -d); trap "rm -rf $tmp" EXIT; javac -encoding UTF-8 -d "$tmp" app/src/main/java/ru/reader/rsvp/SentenceChunks.java app/src/main/java/ru/reader/rsvp/SentenceIndex.java app/src/main/java/ru/reader/rsvp/TtsSentenceHistory.java qa/TestSentenceIndex130.java && java -cp "$tmp" ru.reader.rsvp.TestSentenceIndex130']),
 ('TTS chunk size and text breaks',['bash','qa/check_sentence_chunks.sh']),
 ('Edge sentence seek',['bash','qa/test_edge_sentence_seek.sh']),
 ('Unified book preparation',['bash','qa/test_shared_book_session.sh']),
 ('Audio focus policy',['bash','qa/test_v11129_audio_focus.sh']),
 ('Comic exception regression',[sys.executable,'qa/test_v11126_compile_exception_fix.py']),
 ('Edge outer-method reference regression',[sys.executable,'qa/test_v11127_edge_resume_binding.py']),
 ('Comic scroll gestures regression',[sys.executable,'qa/test_v11127_feed_tap_only.py']),
 ('Comic gestures and pinch zoom',[sys.executable,'qa/test_v11126_comic_gestures_zoom.py']),
 ('Edge clock & positions',['bash','qa/test_edge_clock_sync.sh']),
]
failed=[]
for name,cmd in steps:
 print('\n###',name,flush=True)
 result=subprocess.run(cmd,cwd=root)
 print(name,'PASS' if result.returncode==0 else 'FAIL',flush=True)
 if result.returncode:failed.append(name)
print(f'\nOFFLINE QA v1.11.131: {len(steps)-len(failed)}/{len(steps)} PASS')
if failed: print('FAILED:',', '.join(failed))
sys.exit(bool(failed))
