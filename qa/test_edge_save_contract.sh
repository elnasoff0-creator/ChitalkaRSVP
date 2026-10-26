#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/ru/reader/rsvp" "$TMP/classes"
python3 - "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeStreamingService.java" "$TMP/src/ru/reader/rsvp/TestEdgeSaveContract.java" <<'PY'
import re, sys
from pathlib import Path
source = Path(sys.argv[1]).read_text(encoding="utf-8")
pattern = r'try\{saved=EdgeSpeechFiles\.save\(job\.folder,clip\.key,clip\.text,mp3,metadata\);.*?\}'
match = re.search(pattern, source)
if not match:
    raise SystemExit("FAIL: EdgeSpeechFiles.save completion handler not found")
statement = match.group(0)
code = '''package ru.reader.rsvp;
import java.io.File;
import java.util.List;
class TestEdgeSaveContract {
  static class Job { File folder; }
  static class Clip { String key, text; }
  static class EdgeAudioManager { static void invalidate(File folder) {} }
  static void save(Job job,Clip clip,byte[] mp3,List<EdgeWordTimings.Boundary> metadata){
    File saved=null;
    String failed=null;
    %s
    catch(Exception e){failed=e.getMessage();}
  }
}
''' % statement
Path(sys.argv[2]).write_text(code, encoding="utf-8")
PY
cat > "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" <<'JAVA'
package ru.reader.rsvp;
import java.io.File;
import java.util.List;
class EdgeWordTimings {
 static class Boundary {}
 static void store(File file,String text,List<Boundary> marks) {}
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
  "$TMP/src/ru/reader/rsvp/EdgeWordTimings.java" \
  "$TMP/src/ru/reader/rsvp/TestEdgeSaveContract.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeAudioFormat.java" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeSpeechFiles.java"
echo "PASS: EdgeStreamingService save expression matches File return type"
