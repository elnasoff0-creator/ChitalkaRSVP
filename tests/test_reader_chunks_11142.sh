#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/ChunkTest.java" <<'JAVA'
package ru.reader.rsvp;
public final class ChunkTest {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  StringBuilder b=new StringBuilder();
  for(int i=0;i<130997;i++){
   if(i%100==0)b.append("\n\n");
   b.append("слово").append(i).append(' ');
  }
  String source=b.toString();
  ReadingWordMap words=ReadingWordMap.parse(source);
  BookChunkIndex idx=new BookChunkIndex(source,words,560);
  check(words.size()==130997,"all words");
  check(idx.size()>100&&idx.size()<400,"bounded chunk count");
  int total=0;
  for(int i=0;i<idx.size();i++){
   BookChunkIndex.Chunk c=idx.get(i);
   check(c.startWord==total,"no missing words between chunks");
   check(c.endWord>c.startWord&&c.endWord-c.startWord<=560,"bounded chunk size");
   check(source.substring(c.startChar,c.endChar).contains(words.words[c.startWord]),"chunk text");
   check(idx.chunkForWord(c.startWord)==i,"first index");
   check(idx.chunkForWord(c.endWord-1)==i,"last index");
   total=c.endWord;
  }
  check(total==words.size(),"complete book coverage");
  check(words.atCharacter(words.starts[561]+2)==561,"direct word lookup");
  System.out.println("PASS: "+words.size()+" words in "+idx.size()+" bounded chunks; exact global positions");
 }
}
JAVA
javac -encoding UTF-8 -d "$OUT" app/src/main/java/ru/reader/rsvp/ReadingWordMap.java app/src/main/java/ru/reader/rsvp/BookChunkIndex.java "$OUT/ChunkTest.java"
java -cp "$OUT" ru.reader.rsvp.ChunkTest
python3 - <<'PYTEST'
from pathlib import Path
source=Path('app/src/main/java/ru/reader/rsvp')
reader=(source/'ReaderActivity.java').read_text()
settings=(source/'SettingsActivity.java').read_text()
main=(source/'MainActivity.java').read_text()
assert 'RecyclerView' in reader and 'ChunkAdapter' in reader
assert 'normalText.setText(body' not in reader
assert 'getItemCount(){return chunks==null?0:chunks.size();}' in reader
assert 'saveLight(false)' in reader and 'private void save()' in reader
assert 'ReaderBookCache.load' in reader and 'ReaderBookCache.store' in reader
assert 'android.widget.Spinner' in settings and 'modeIconFor' in settings
assert 'R.drawable.ic_mode_cursor' in settings and 'Режим чтения' in settings
assert 'openBook(currentUri, false, requestedScreen' not in main
assert "versionCode 11049; versionName '1.11.42-beta'" in Path('app/build.gradle').read_text()
print('PASS: virtualization, background indexing, light saves and dropdown UI')
PYTEST
