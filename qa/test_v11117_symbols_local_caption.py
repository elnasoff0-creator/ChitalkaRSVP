from pathlib import Path
import re
src=Path(__file__).resolve().parents[1]
p=src/'app/src/main/java/ru/reader/rsvp'
transform=(p/'EdgeSpeechTextTransform.java').read_text()
dictui=(p/'EdgeDictionaryActivity.java').read_text()
reader=(p/'ReaderActivity.java').read_text()
gradle=(src/'app/build.gradle').read_text()
checks={
 'separate symbols action': 'addSymbols.setOnClickListener(v->editRule(-1,"","","",true))' in dictui,
 'punctuation-friendly input': 'InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD' in dictui,
 'symbol example': '*** → .' in dictui,
 'literal escaped keys': 'Pattern.quote(originalWord)' in transform,
 'symbol boundary fix': 'boolean beginsWord=' in transform and 'boolean endsWord=' in transform,
 'no recursive transforms': '!source.substring(sourceStart,sourceEnd).equalsIgnoreCase(originalWord)' in transform,
 'custom dot preserved': 'boolean sourceDot=' in transform,
 'system TTS complete sentence': 'int through=ttsLocal ? Math.min(body.length(),part.toChar)' in reader,
 'Edge progressive caption remains': ': Math.min(body.length(),map.ends[map.clamp(index)])' in reader,
 'version code remains 11700': bool(re.search(r'versionCode\s+11700\b',gradle)),
 'version name remains': '1.11.117-beta' in gradle,
}
for label,ok in checks.items():
 print(('PASS' if ok else 'FAIL'),label)
assert all(checks.values())
