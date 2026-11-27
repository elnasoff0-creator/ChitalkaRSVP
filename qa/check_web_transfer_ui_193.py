#!/usr/bin/env python3
"""Contracts for the user-facing 1.11.193 UI and shared Wi-Fi download history."""
from pathlib import Path
from xml.etree import ElementTree as ET
import re, subprocess, shutil
r=Path(__file__).resolve().parents[1]
p=r/'app/src/main/java/ru/reader/rsvp'
read=lambda name:(p/name).read_text(encoding='utf-8')
version=(r/'app/build.gradle').read_text()
assert "versionCode 19400; versionName '1.11.194-beta'" in version
ui=read('WebTransferActivity.java')
assert 'AppToolbar.add(this,content,"Передача по Wi-Fi")' in ui
assert 'ic_web_server_start' in ui and 'ic_web_server_stop' in ui
assert 'ic_web_copy_address' in ui and 'ClipData.newPlainText' in ui
assert 'DownloadFolderPicker.choose(this' in ui and 'DownloadFolderPicker.receive(this' in ui
assert 'help_section","Передача книг по Wi-Fi"' in ui
assert 'Настройки Web Transfer · Онлайн' not in ui
assert 'Компьютер и телефон должны быть' not in ui
assert 'button("Запустить сервер")' not in ui
assert 'current.activeUploads()>0' in ui and 'setTitle("Остановить сервер?")' in ui
helptext=read('ActivityInfo.java')
assert 'section(entries,"Передача книг по Wi-Fi"' in helptext
for key in ['Введите PIN','QR-код','отмен','Настройки → Онлайн','Загрузки']:
    assert key.lower() in helptext.lower(),key
for fn in ['ic_web_server_start.xml','ic_web_server_stop.xml','ic_web_copy_address.xml']:
    tree=ET.parse(r/'app/src/main/res/drawable'/fn)
    node=tree.getroot()
    ns='{http://schemas.android.com/apk/res/android}'
    assert node.attrib[ns+'viewportWidth']=='24'
    assert node.find('path').attrib[ns+'strokeWidth']=='1.5'
store=read('WebTransferDownloadStore.java')
for k in ['MAX_HISTORY=300','DownloadChangeBus.publish','static synchronized boolean begin','static synchronized void progress',
          'static synchronized void importing','static synchronized void finish','static synchronized boolean cancel',
          'static synchronized void recover','static synchronized boolean remove','CANCELLED.equals(e.state)']:
    assert k in store,k
repo=read('DownloadsRepository.java')
assert 'WIFI="wifi"' in repo and 'WebTransferDownloadStore.all(ctx)' in repo
assert 'item.type=WIFI' in repo and 'item.coveragePercent' in repo
act=read('DownloadActivity.java')
assert '"Wi-Fi"' in act and 'filter==7' in act
assert 'WebTransferService.cancelTransfer(this,item.id)' in act
assert 'WebTransferDownloadStore.remove(this,item.id)' in act
assert 'DownloadsRepository.WIFI.equals(item.type)' in act
server=read('WebTransferServer.java')
for k in ['transfers.put(transferId,client)','WebTransferDownloadStore.begin','WebTransferDownloadStore.progress',
          'WebTransferDownloadStore.importing','WebTransferDownloadStore.finish','"/api/cancel".equals(path)',
          'WebTransferDownloadStore.interruptActive','WebTransferDownloadStore.cancelled']:
    assert k in server,k
browser=(r/'app/src/main/assets/webtransfer/index.html').read_text()
assert 'X-WT-Transfer-ID' in browser and "'/api/cancel'" in browser
assert 'id:makeId()' in browser
assert 'web_transfer_download_history_v193' in store
if shutil.which('node'):
    code=re.search(r'<script>(.*?)</script>',browser,re.S)
    assert code
    result=subprocess.run(['node','--check','-'],input=code.group(1),text=True,capture_output=True)
    assert result.returncode==0,result.stderr
names=sorted(x.name for x in r.glob('*.md') if x.name != 'README.md')
assert (r/'README.md').is_file(), 'README.md missing'
assert len(names)==3 and all('1.11.194' in name for name in names),names
print('PASS: v193 toolbar, shared Wi-Fi download history, cancellation, help, SAF, browser IDs and CLEAN docs')
