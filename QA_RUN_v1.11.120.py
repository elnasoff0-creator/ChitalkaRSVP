#!/usr/bin/env python3
"""Run source/JVM QA in isolated processes; not an Android APK build."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor,as_completed
import subprocess,sys
root=Path(__file__).resolve().parent
scripts=sorted(list((root/'qa').glob('*.sh'))+list((root/'qa').glob('*.py')))
def run(path):
    cmd=['python',str(path)] if path.suffix=='.py' else ['bash',str(path)]
    try:
        p=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=65)
        return path.name,p.returncode,p.stdout[-1400:],p.stderr[-1400:]
    except subprocess.TimeoutExpired:
        return path.name,124,'','Timed out after 65s'
with ThreadPoolExecutor(max_workers=3) as pool:
    results=sorted((f.result() for f in as_completed([pool.submit(run,x) for x in scripts])))
lines=[]
for name,code,out,err in results:
    lines.append(('PASS' if code==0 else 'FAIL')+' '+name)
    if code:
        lines.append('  STDOUT: '+out.replace('\n',' ')[:450]);lines.append('  STDERR: '+err.replace('\n',' ')[:450])
passed=sum(code==0 for _,code,_,_ in results)
lines.append(f'TOTAL {len(results)} PASS {passed} FAIL {len(results)-passed}')
lines.append('Scope: Java syntax, XML/resources, static assertions, desktop JVM tests. No Android SDK or device rendering.')
report='\n'.join(lines)+'\n'
(root/'QA_RESULTS_v1.11.120.txt').write_text(report)
print(report)
sys.exit(0 if passed==len(results) else 1)
