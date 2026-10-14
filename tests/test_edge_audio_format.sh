#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/EdgeAudioFormatTest.java" <<'JAVA'
import ru.reader.rsvp.EdgeAudioFormat;
public class EdgeAudioFormatTest {
  public static void main(String[] args) {
    if(!"audio/mpeg".equals(EdgeAudioFormat.forFilename("chapter.mp3").mime))throw new AssertionError();
    if(!"audio/ogg".equals(EdgeAudioFormat.forFilename("old.ogg").mime))throw new AssertionError();
    if(EdgeAudioFormat.forFilename("broken.txt")!=null)throw new AssertionError();
    if(!"MP3".equals(EdgeAudioFormat.identify(new byte[]{'I','D','3',1,0},5).label))throw new AssertionError();
    System.out.println("Edge audio formats: PASS");
  }
}
JAVA
javac -d "$OUT" app/src/main/java/ru/reader/rsvp/EdgeAudioFormat.java "$OUT/EdgeAudioFormatTest.java"
java -cp "$OUT" EdgeAudioFormatTest
