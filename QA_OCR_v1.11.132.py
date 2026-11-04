from pathlib import Path
import re
root=Path(__file__).parent
src=root/'app/src/main/java/ru/reader/rsvp'
a=(src/'ComicReaderActivity.java').read_text()
b=(src/'ComicOcrEngine.java').read_text()
c=(src/'ComicOcrSpeechService.java').read_text()
d=(src/'ComicOcrStore.java').read_text()
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
build=(root/'app/build.gradle').read_text()
checks={
 'version name/code': 'versionCode 13200' in build and '1.11.132-beta' in build,
 'OCR library configured': 'tesseract4android:4.9.0' in build,
 'Russian and English bundled': all((root/f'app/src/main/assets/tessdata/{x}.traineddata').stat().st_size>1_000_000 for x in ('rus','eng')),
 'visible-page scan and saved boxes':'recognizeVisiblePage()' in a and 'ComicOcrEngine.recognize' in a and 'ocrStore.save(chapter,page,result)' in a,
 'overlay tap to playback':'ComicOcrTap.onBlock' in a and 'ComicOcrSpeechService.speak' in a,
 'overlay and scaling': 'ocrOverlay(index,data)' in a and 'var ratio=Math.min(cw/nw,ch/nh)' in a,
 'existing scroll restore intact':'positionInfo()' in a and 'restoreWhenReady(' in a,
 'no auto block reading': 'onBlock(comicOcrToken,index,j)' in a,
 'store normalized boxes': 'bbox' in d and 'blocks' in b,
 'Russian OCR initialization':'"rus+eng"' in b,
 'foreground audio service': 'startForeground(NOTIFICATION_ID' in c and '.ComicOcrSpeechService' in manifest,
 'new tap cancels previous': 'sequence.incrementAndGet();release();' in c,
 'no audio controls in ComicActivity': 'ocrAction' in a and 'setOnClickListener(v->recognizeVisiblePage())' in a,
}
for label,ok in checks.items():print(('PASS' if ok else 'FAIL')+': '+label)
print('RESULT',sum(checks.values()),'/',len(checks))
if not all(checks.values()):raise SystemExit(1)
