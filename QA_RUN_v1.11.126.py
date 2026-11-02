#!/usr/bin/env python3
"""Run current source-level QA. Pass --full for all historical regression tests.
Does not build an APK or exercise Android rendering/network providers.
"""
from pathlib import Path
import subprocess,sys
root=Path(__file__).resolve().parent
focused=[
 'check_java_syntax.sh','test_v11126_comic_gestures_zoom.py',
 'test_v11126_comics_timer.py','test_v11125_mangalib.py','test_v11124_assets.py',
 'test_v11123_features.py','test_v11122_features.py','test_v11121_navigation_settings.py',
 'test_v11120_lazy_startup.py','test_v11119_navigation_highlight_sleep.py',
 'test_v11117_symbols_local_caption.py','test_v11117_shared_navigation.py','test_v11116_requirements.py',
]
full='--full' in sys.argv
checks=sorted([p for p in (root/'qa').iterdir() if p.suffix in ('.py','.sh')]) if full else [root/'qa'/x for x in focused]
lines=[]
for path in checks:
    command=['python',str(path)] if path.suffix=='.py' else ['bash',str(path)]
    try:
        result=subprocess.run(command,cwd=root,capture_output=True,text=True,timeout=65 if full else 30)
        status='PASS' if result.returncode==0 else 'FAIL'
        detail=(result.stdout+'\n'+result.stderr).strip()[-450:]
    except subprocess.TimeoutExpired:
        status='TIMEOUT';detail='Test exceeded its time limit'
    lines.append(f'{status} {path.name}')
    if status!='PASS':lines.append('  '+detail.replace('\n',' | '))
passed=sum(x.startswith('PASS ') for x in lines)
lines.append(f'TOTAL {len(checks)} PASS {passed} FAIL/TIMEOUT {len(checks)-passed}')
lines.append('Scope: Java syntax, XML/resources, source assertions, and desktop JVM tests. No APK, device UI or live MangaLib API verification.')
report='\n'.join(lines)+'\n'
(root/('QA_FULL_RESULTS_v1.11.126.txt' if full else 'QA_RESULTS_v1.11.126.txt')).write_text(report)
print(report)
sys.exit(0 if passed==len(checks) else 1)
