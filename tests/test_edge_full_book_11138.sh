#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/SegmentTest.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.ArrayList;
public class SegmentTest {
  public static void main(String[] args) {
    String raw="Начало главы. Первое предложение книги. Второе предложение.\nТретий раздел текста. Окончание.";
    ArrayList<Integer> positions=EdgeTextSegments.positions(raw);
    ArrayList<EdgeTextSegments.Part> all=EdgeTextSegments.all(raw,32);
    if(all.isEmpty()||all.get(0).start!=0||all.get(all.size()-1).end!=positions.size())
      throw new AssertionError("Whole book not covered");
    int next=0;
    for(EdgeTextSegments.Part p:all){
      if(p.start!=next||p.end<=p.start)throw new AssertionError("Gap or overlap");
      EdgeTextSegments.Part same=EdgeTextSegments.at(raw,positions,p.start,32);
      if(!same.text.equals(p.text)||same.end!=p.end)throw new AssertionError("Playback segmentation mismatch");
      next=p.end;
    }
    if(!EdgeTextSegments.all("",100).isEmpty())throw new AssertionError("Empty book");
    System.out.println("PASS: shared segment planner full-book coverage and playback compatibility");
  }
}
JAVA
javac -d "$OUT" app/src/main/java/ru/reader/rsvp/EdgeTextSegments.java "$OUT/SegmentTest.java"
java -cp "$OUT" ru.reader.rsvp.SegmentTest
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
root=Path('app/src/main/java/ru/reader/rsvp')
service=(root/'EdgeFullBookService.java').read_text()
form=(root/'EdgeFullBookActivity.java').read_text()
dashboard=(root/'DownloadActivity.java').read_text()
store=(root/'EdgeFullBookStore.java').read_text()
download=(root/'EdgeDownloadService.java').read_text()
repo=(root/'DownloadsRepository.java').read_text()
assert 'versionCode 11045' in Path('app/build.gradle').read_text()
for term in ('startForeground','EdgeFullBookStore.readyCount','EdgeTextSegments.all','EdgeBookStorage.key','EdgeTtsClient.synthesize','EdgeGenerationGuard.claim','EdgeFullBookStore.PAUSED'):
 assert term in service, term
for term in ('Подготовить аудиокнигу','Светлана','Дмитрий','CoverImageLoader.show','getString("library"','EdgeFullBookService.ADD'):
 assert term in form,term
for term in ('Подготовить всю книгу Edge TTS','Пауза','Продолжить','Слушать офлайн'):
 assert term in dashboard,term
assert 'full_queue.json' in store and 'full_job.json' in store
assert 'new File(directory,key+".mp3")' in download and 'EdgeTextSegments.at' in download
assert 'EdgeFullBookStore.Job full' in repo
assert 'player.prepareBook' not in service and 'new Intent(this,EdgePlaybackService.class)' not in service
manifest=ET.parse('app/src/main/AndroidManifest.xml')
ns='{http://schemas.android.com/apk/res/android}'
for tag,name in [('service','EdgeFullBookService'),('activity','EdgeFullBookActivity')]:
 assert any(x.get(ns+'name')=='.'+name for x in manifest.findall('.//'+tag))
print('PASS: independent full-book Edge service, job persistence, UI, manifest')
PY
