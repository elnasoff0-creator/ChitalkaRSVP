#!/usr/bin/env python3
"""Check visible routes and safety constraints; Android Gradle build still required."""
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/ru/reader/rsvp'
read=lambda n:(src/n).read_text(encoding='utf-8')
assert "versionCode 19200; versionName '1.11.192-beta'" in (root/'app/build.gradle').read_text()
assert 'com.google.zxing:core:' in (root/'app/build.gradle').read_text()
assert 'Передача по Wi-Fi' in read('DownloadActivity.java') and 'R.drawable.ic_wifi_transfer' in read('DownloadActivity.java')
assert 'Подготовить книгу Edge' not in read('DownloadActivity.java')
assert not (src/'EdgeFullBookActivity.java').exists()
assert 'EdgeFullBookService.java' in [p.name for p in src.glob('*.java')]
assert 'Drawable activeTab' in read('BrowserTabs.java') and 'topLine.quadTo' in read('BrowserTabs.java')
assert 'View indicator=new View(context)' not in read('BrowserTabs.java')
settings=read('SettingsActivity.java')
for key in ['tts_word_highlight_color','tts_word_highlight_opacity','tts_word_highlight_enabled','WebTransferPrefs.PORT','WebTransferPrefs.TIMEOUT','WebTransferPrefs.DUPLICATE','WebTransferPrefs.NOTIFY']:
    assert key in settings,key
assert 'new ReaderWordHighlightSpan(ttsWordHighlightColor,ttsWordHighlightOpacity)' in read('ReaderActivity.java')
assert 'oldWordEnabled!=ttsWordHighlightEnabled' in read('ReaderActivity.java')
server=read('WebTransferServer.java')
for key in ['SecureRandom','ServerSocket','wifiIpv4','TRANSPORT_WIFI','WebTransferPolicy.length','WebTransferPolicy.sameOrigin','SameSite=Strict','/api/pair','/api/upload','MAX_BYTES','inflight.incrementAndGet','sockets.remove(socket)','X-WT-Skip']:
    assert key in server,key
assert 'File.createTempFile' in server and 'temp.delete()' in server
up=read('WebTransferUploadManager.java')
for key in ['DocumentFile.fromTreeUri','SafeDownloadTransfer.directory','partial.renameTo(name)','registerBook','registerComic','registerAudio','DUPLICATE_SKIP']:
    assert key in up,key
service=read('WebTransferService.java')
assert 'startForeground' in service and 'FOREGROUND_SERVICE_TYPE_DATA_SYNC' in service
manifest=(root/'app/src/main/AndroidManifest.xml').read_text()
assert 'WebTransferActivity' in manifest and 'WebTransferService' in manifest
assert 'EdgeFullBookActivity' not in manifest
assert (root/'app/src/main/assets/webtransfer/index.html').is_file()
web=(root/'app/src/main/assets/webtransfer/index.html').read_text()
for key in ['dragover','drop','/api/pair','/api/upload','XMLHttpRequest','encodeURIComponent','xhr.abort','X-WT-Request']:
    assert key in web,key
assert (root/'app/src/main/res/drawable/ic_wifi_transfer.xml').is_file()
for x in (root/'app/src/main').rglob('*.xml'):ET.parse(x)
md=list(root.glob('*.md'))
assert len(md)==4 and all('1.11.192' in f.name for f in md),md
print('PASS: v192 Web Transfer lifecycle, browser UI, SAF, tabs, TTS word settings, clean docs and XML')
