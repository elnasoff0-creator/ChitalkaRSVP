#!/usr/bin/env python3
"""Compile real aggregation code and execute JVM assertions without Android SDK."""
from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory(prefix='mangagroup135_') as folder:
    cls=Path(folder)
    commands=[
      ['javac','-encoding','UTF-8','-d',str(cls),
       str(root/'app/src/main/java/ru/reader/rsvp/MangaLibDownloadGroup.java'),
       str(root/'qa/TestMangaLibDownloadGroup.java')],
      ['java','-cp',str(cls),'ru.reader.rsvp.TestMangaLibDownloadGroup']]
    for cmd in commands:
        r=subprocess.run(cmd,capture_output=True,text=True)
        if r.returncode:raise RuntimeError(r.stderr+'\n'+r.stdout)
        if r.stdout:print(r.stdout.strip())
