#!/usr/bin/env python3
"""Run supported local source checks for PROChitalka v1.11.134 CLEAN FINAL HOTFIX.
A passing run does NOT mean Android Gradle/SDK compilation passed.
"""
from pathlib import Path
import subprocess, sys
root=Path(__file__).resolve().parent
checks=[
  ('All Java syntax','bash qa/check_java_syntax.sh'),
  ('MangaLib real API/worker/queue type checks + CDN behavior','python qa/test_v11133_compile_and_behavior.py'),
  ('Manga download screen Android API shape','python qa/typecheck_134_manga_ui.py'),
  ('Final MangaLib/Edge/UI requirements and XML','python qa/test_v11134_final_changes.py'),
  ('Shared UI palette type check','python qa/typecheck_v11134_palette.py'),
  ('Shared UI behavior and resources','python qa/test_v11134_features.py'),
  ('Audio settings narrow-screen layout','python qa/test_v11134_audio_settings_layout.py'),
  ('Edge SQLite/Regex dictionary engine','bash qa/test_v11132_dictionary_regex.sh'),
  ('Dictionary editor and SQLite type checks','bash qa/typecheck_v11132_dictionary.sh'),
  ('Android Regex compatibility','python qa/test_v11132_android_regex_compat.py'),
  ('TTS sentence navigation','bash qa/test_edge_sentence_seek.sh'),
  ('Audio focus policy','bash qa/test_v11129_audio_focus.sh'),
]
passed=0
for title,cmd in checks:
    p=subprocess.run(cmd,cwd=root,shell=True,executable='/bin/bash',capture_output=True,text=True,timeout=50)
    if p.returncode==0:
        passed+=1;print('PASS',title)
    else:
        print('FAIL',title, '\n',p.stdout[-2000:],p.stderr[-2000:])
print(f'TOTAL: {passed}/{len(checks)} PASS')
sys.exit(0 if passed==len(checks) else 1)
