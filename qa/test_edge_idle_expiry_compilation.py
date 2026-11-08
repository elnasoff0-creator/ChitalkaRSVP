#!/usr/bin/env python3
"""Compile the real idle-expiry field initializer in a minimal Java harness.

This guards against javac's illegal-forward-reference error, which a parser-only
Java source check cannot detect. It is not a complete Android application build.
"""
from pathlib import Path
import re,subprocess,tempfile
src=(Path(__file__).resolve().parents[1] / 'app/src/main/java/ru/reader/rsvp/EdgeStreamingService.java').read_text()
field=re.search(r'(?m)^\s*private final Runnable idleExpiry=\(\)->\{\s*.*?\n\s*\};',src,re.S)
assert field, 'idleExpiry field not found'
for declaration in ('private boolean bookReady,active', 'private EdgePlaybackService player;'):
    assert src.index(declaration) < field.start(), f'Forward field declaration: {declaration}'
with tempfile.TemporaryDirectory() as tmp:
    harness=Path(tmp)/'EdgeIdleExpiryCompileCheck.java'
    harness.write_text('''public class EdgeIdleExpiryCompileCheck {
    private boolean bookReady, active;
    private EdgePlaybackService player;
    private void stopSelf() {}
    private static final class EdgePlaybackService { boolean playRequested() {return false;} }
'''+field.group(0)+'''\n}\n''')
    result=subprocess.run(['javac','-encoding','UTF-8',str(harness)],capture_output=True,text=True)
    assert result.returncode==0, 'javac compile failed: '+result.stderr
print('PASS: idleExpiry original initializer compiles with javac (no illegal forward reference)')
