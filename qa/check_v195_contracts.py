#!/usr/bin/env python3
from pathlib import Path
from xml.etree import ElementTree as ET
import re, subprocess, shutil
r=Path(__file__).resolve().parents[1]; j=r/'app/src/main/java/ru/reader/rsvp'; read=lambda n:(j/n).read_text()
prefs=read('WebTransferPrefs.java');assert '8787' in prefs and 'dynamic(c)?0:fixedPort(c)' in prefs
server=read('WebTransferServer.java');auth=read('WebTransferAuth.java');folder=read('WebTransferFolder.java')
for token in ['x-wt-password','WebTransferAuth.equal','WebTransferAuth.trusted','SameSite=Strict','/api/folder','prochitalka.local:','WebTransferPolicy.sameOrigin']:assert token in server,token
for token in ['AndroidKeyStore','AES/GCM/NoPadding','SHA-256','30L*86400000L','SecureRandom']:assert token in auth,token
for token in ['MAX_MANIFEST','MAX_FILES','WebTransferPolicy.relative','Long.MAX_VALUE','MAX_BYTES','new byte[65536]','AudioLibrary.merge','pending.renameTo(name)','pending.delete()','groupCount','checkSpace']:assert token in folder,token
web=(r/'app/src/main/assets/webtransfer/index.html').read_text();assert 'webkitdirectory' in web and 'webkitGetAsEntry' in web and 'new Blob' in web and '/api/folder' in web and 'X-WT-Password' in web and 'X-WT-Pin' not in web
assert 'R.mipmap.ic_launcher' in read('UniversalDrawer.java')
tabs=read('BrowserTabs.java');assert 'panelOutline' in tabs and 'path.moveTo(start,t)' in tabs and 'pane.setForeground' in tabs
assert 'BrowserTabs.fixedViewport' in read('SettingsActivity.java')
assert 'wifiRefresh' in read('DownloadActivity.java') and 'indicator.start()' in read('WebTransferActivity.java')
reader=read('ReaderActivity.java');assert 'chteicoPendingWords+=Math.max(0,count)' in reader and 'flushChteicoWords()' in reader and 'index-chteicoLastWordIndex<=400' not in reader
assert 'record.cover' in read('DownloadsRepository.java')
for d in (r/'app/src/main/res').glob('values*'):
    seen=set()
    for f in d.glob('*.xml'):
        for el in ET.parse(f).getroot():
            name=el.get('name');typ=el.get('type',el.tag)
            if not name or typ in ('declare-styleable','public'):continue
            key=(typ,name);assert key not in seen,(d,key);seen.add(key)
if shutil.which('node'):
    code=re.search(r'<script>(.*?)</script>',web,re.S).group(1)
    subprocess.run(['node','--check','-'],input=code,text=True,check=True)
print('PASS: v195 source contracts, browser JS and unique Android resource names; device tests still required')
