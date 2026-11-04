#!/usr/bin/env python3
"""Selected v1.11.132 regression suite. No Android SDK / APK build."""
from pathlib import Path
import subprocess, sys, xml.etree.ElementTree as ET, re
root=Path(__file__).resolve().parent
suite=[
 ('Java syntax', ['bash','qa/check_java_syntax.sh']),
 ('New Android API signatures via stand-ins', ['bash','qa/typecheck_v11132_dictionary.sh']),
 ('Regex / offsets / >100 rules', ['bash','qa/test_v11132_dictionary_regex.sh']),
 ('Previous Edge speech normalization', ['bash','qa/test_edge_text_transform.sh']),
 ('Unified book progress hotfix typecheck', ['python3','qa/test_v11131_progress_chapter_types.py']),
 ('Pet-game and unified-progress Java API typecheck', ['bash','qa/typecheck_131_new_classes.sh']),
 ('Sentence seek', ['bash','qa/test_edge_sentence_seek.sh']),
 ('Silent 429 handling', ['bash','qa/test_edge_429_silent.sh']),
 ('Edge audio-save contract', ['bash','qa/test_edge_save_contract.sh']),
 ('Audio focus', ['bash','qa/test_v11129_audio_focus.sh']),
 ('Fixed Edge cooldown', ['bash','qa/test_edge_500ms_policy.sh']),
]
passed=0
for name,cmd in suite:
 try:
  process=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=70)
  if process.returncode:
   print('FAIL '+name+'\n'+(process.stdout+process.stderr)[-2000:]);continue
  passed+=1;print('PASS '+name)
 except Exception as exc: print('FAIL '+name+': '+repr(exc))
try:
 gradle=(root/'app/build.gradle').read_text()
 src=root/'app/src/main/java/ru/reader/rsvp'
 files=list(root.glob('app/src/main/res/**/*.xml'))
 for f in files:ET.parse(f)
 assert "versionCode 13200; versionName '1.11.132-beta'" in gradle
 assert 'implementation' in gradle and 'sqlite' not in gradle.lower() # native SQLite; no dependency
 store=(src/'EdgeDictionaryStore.java').read_text()
 assert 'extends SQLiteOpenHelper' in store and '10000' in store and '5000' in store and '1000' in store
 assert 'putBoolean(MIGRATED,true)' in store and 'db.setTransactionSuccessful()' in store
 assert 'SELECT id,kind,pattern,context,replacement,is_regex' in store
 for file in ['EdgeStreamingService.java','EdgeFullBookService.java']:
  assert 'EdgeDictionaryStore.get(this).options()' in (src/file).read_text(),file
 manual=(src/'ActivityInfo.java').read_text()
 assert 'Регулярные выражения словаря Edge TTS' in manual
 assert 'Ударение: выберите гласную' in (src/'EdgeDictionaryActivity.java').read_text()
 assert not list(root.rglob('chteico_game_*.png'))
 print(f'PASS version, resource XML ({len(files)}), SQLite & UI migration contracts')
 passed+=1
except Exception as exc: print('FAIL version/resource/SQLite contract:',repr(exc))
print(f'\nSUMMARY: {passed}/{len(suite)+1} groups PASS')
sys.exit(0 if passed==len(suite)+1 else 1)
