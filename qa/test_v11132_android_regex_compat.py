#!/usr/bin/env python3
"""Android-specific regression: never pass the unsupported UNICODE_CHARACTER_CLASS flag.

Android's java.util.regex.Pattern already uses Unicode classes and Unicode-aware
case folding for (?i); JDK host tests alone cannot detect an unsupported flag.
"""
from pathlib import Path

base = Path(__file__).resolve().parent.parent / 'app/src/main/java/ru/reader/rsvp'
transform = (base / 'EdgeSpeechTextTransform.java').read_text(encoding='utf-8')
store = (base / 'EdgeDictionaryStore.java').read_text(encoding='utf-8')
editor = (base / 'EdgeDictionaryActivity.java').read_text(encoding='utf-8')

for name, source in [('store', store), ('editor', editor)]:
    assert 'Pattern.UNICODE_CHARACTER_CLASS' not in source, f'{name}: direct use of unsupported Android flag'
    assert 'EdgeSpeechTextTransform.compileUserRegex(' in source, f'{name}: bypassed Android-compatible compiler'

assert 'Pattern.compile("",Pattern.UNICODE_CHARACTER_CLASS)' in transform, 'must probe flag support only once'
assert 'catch(IllegalArgumentException androidUnsupported)' in transform, 'Android exception must be handled'
assert 'return 0;' in transform, 'Android must compile Regex with no unsupported flags'
assert 'return Pattern.compile(expression,SUPPORTED_UNICODE_FLAGS);' in transform, 'compilation must use tested flags'
assert 'validateRegexPattern(expression);' in transform, 'retain Regex validation'
assert 'compiled=compileUserRegex(key)' in transform
assert 'Pattern compiled=EdgeSpeechTextTransform.compileUserRegex(pattern);' in store
assert 'EdgeSpeechTextTransform.compileUserRegex(candidate)' in editor
print('PASS: Android-compatible Regex: UI preview, SQLite save, Edge TTS compilation use one compiler without unsupported flags')
