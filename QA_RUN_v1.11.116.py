#!/usr/bin/env python3
"""Run isolated JVM/static QA scripts in parallel; not an Android compilation."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor,as_completed
import subprocess,sys
root=Path(__file__).resolve().parent
scripts=sorted(list((root/'qa').glob('*.sh'))+list((root/'qa').glob('*.py')))
def run(path):
    cmd=['python',str(path)] if path.suffix=='.py' else ['bash',str(path)]
    try:
        p=subprocess.run(cmd,cwd=root,capture_output=True,text=True,timeout=45)
        return path.name,p.returncode,p.stdout[-1600:],p.stderr[-1600:]
    except subprocess.TimeoutExpired:
        return path.name,124,'','Timed out after 45s'
results=[]
with ThreadPoolExecutor(max_workers=4) as pool:
    for fut in as_completed([pool.submit(run,x) for x in scripts]):results.append(fut.result())
lines=[]
for name,code,out,err in sorted(results):
    lines.append(('PASS' if code==0 else 'FAIL')+' '+name)
    if code:lines.append('  STDOUT: '+out.replace('\n',' ')[:500]);lines.append('  STDERR: '+err.replace('\n',' ')[:500])
passed=sum(x[1]==0 for x in results)
lines.append(f'TOTAL {len(results)} PASS {passed} FAIL {len(results)-passed}')
lines.append('Scope: Java syntax, source structural checks and desktop JVM tests only; no Android SDK, Gradle build or device rendering.')
report='\n'.join(lines)+'\n'
(root/'QA_RESULTS_v1.11.116.txt').write_text(report)
print(report)
sys.exit(0 if passed==len(results) else 1)
