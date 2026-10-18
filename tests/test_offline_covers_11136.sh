#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 - <<'PY'
from pathlib import Path
p=Path('app/src/main/java/ru/reader/rsvp')
a=(p/'AudiobookCoverStore.java').read_text()
assert '"cover.jpg"' in a and '"cover.jpg.part"' in a
assert 'Bitmap.CompressFormat.JPEG' in a and 'MAX_BYTES=5*1024*1024' in a
assert 'static synchronized boolean ensure(' in a
assert 'if(destination.isFile()&&destination.length()>100)' in a
assert 'previous.isEmpty()' in a and 'new File(path).isFile()' in a
assert 'backfillOne(' in a and 'scheduleBackfill(' in a
assert 'BookImportManager.updateLibrary(app,job)' in a
assert 'if(!IN_PROGRESS.add(job.source))return;' in a
w=(p/'DownloadWorker.java').read_text()
assert 'AudiobookCoverStore.ensure(this,job)' in w
assert w.index('AudiobookCoverStore.ensure(this,job)') < w.index('KvuheDownloader.download(this,job,i')
assert 'AudiobookCoverStore.registerDefault(c,j)' in (p/'BookImportManager.java').read_text()
assert 'AudiobookCoverStore.coverFile(context,job).delete()' in (p/'BookImportManager.java').read_text()
assert 'localCover=AudiobookCoverStore.localUri(context,job)' in (p/'DownloadsRepository.java').read_text()
assert '"file".equalsIgnoreCase(u.getScheme())' in (p/'CoverImageLoader.java').read_text()
assert 'AudiobookCoverStore.localUri(this,b.id)' in (p/'MainActivity.java').read_text()
assert 'AudiobookCoverStore.localUri(this,id)' in (p/'AudioPlayerActivity.java').read_text()
assert 'AudiobookCoverStore.backfillOne(this,b.id' in (p/'AudioPlayerActivity.java').read_text()
assert 'AudiobookCoverStore.backfill(this' in (p/'DownloadActivity.java').read_text()
g=Path('app/build.gradle').read_text()
assert "versionCode 11046; versionName '1.11.39-beta'" in g
print('PASS: v1.11.36 offline audiobook covers, backfill, manual override, version')
PY
