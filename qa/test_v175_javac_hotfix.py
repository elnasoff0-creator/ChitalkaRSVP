#!/usr/bin/env python3
"""Compile the two source regions that caused the v1.11.175 CI failure.

This targeted Java 17 check is NOT a substitute for Android Gradle assembleRelease.
"""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/ru/reader/rsvp'
reader = (src / 'ReaderActivity.java').read_text(encoding='utf-8')
link = (src / 'LinkDownloadDetailsActivity.java').read_text(encoding='utf-8')

start = reader.index('public void onTextChanged(CharSequence s,int start,int before,int count){')
end = reader.index('public void afterTextChanged(android.text.Editable e){}', start)
block = reader[start:end]

match = re.search(r'int offset\s*=\s*0\s*,\s*([A-Za-z_][\w]*)\s*=\s*0\s*;', block)
assert match is not None, 'Search result-count declaration changed; review the javac regression check.'
var = match.group(1)
assert re.search(r'\b'+re.escape(var)+r'\s*\+\+\s*;', block), 'Missing result-counter increment.'
assert re.search(r'final int total\s*=\s*'+re.escape(var)+r'\s*;', block), 'Missing total assignment.'

reader_snippet = '''public final class ReaderCountCompileCheck {
  public void onTextChanged(CharSequence s,int start,int before,int count) {
    int offset=0,%s=0;
    %s++;
    final int total=%s;
  }
}
''' % (var,var,var)

start = link.index('private final Runnable refreshRow=')
end = link.index('private void command(',start)
field_snippet = link[start:end]
assert 'downloadListener' in field_snippet
link_snippet = '''public final class LinkCallbackCompileCheck {
  private boolean visible;
  private String id;
  private final Handler handler = new Handler();
  private void render() {}
  private interface Listener { void onChange(String id); }
  private static final class DownloadChangeBus { interface Listener { void onChange(String id); } }
  private static final class Handler {
    void removeCallbacks(Runnable task) {}
    void postDelayed(Runnable task,long delay) {}
  }
  %s
}
''' % field_snippet

with tempfile.TemporaryDirectory() as tmp:
    tmp = Path(tmp)
    for name,code in [('ReaderCountCompileCheck',reader_snippet),('LinkCallbackCompileCheck',link_snippet)]:
        (tmp / (name+'.java')).write_text(code,encoding='utf-8')
    proc=subprocess.run(['javac','-encoding','UTF-8','-d',str(tmp),
                        str(tmp/'ReaderCountCompileCheck.java'),
                        str(tmp/'LinkCallbackCompileCheck.java')],
                        text=True,capture_output=True)
    if proc.returncode:
        raise SystemExit('FAIL: targeted javac regression:\n'+proc.stdout+'\n'+proc.stderr)
print('PASS: targeted Java 17 compile of onTextChanged counter and LinkDownloadDetails callbacks')
