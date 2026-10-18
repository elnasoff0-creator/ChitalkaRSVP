#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/ReaderModesTest.java" <<'JAVA'
package ru.reader.rsvp;
public class ReaderModesTest {
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args){
  String raw="  Первый  второй\nтретий, четвёртый!\nПятый.";
  ReadingWordMap m=ReadingWordMap.parse(raw);
  ok(m.size()==5,"token count");
  ok(m.words[0].equals("Первый")&&m.words[3].equals("четвёртый!"),"text");
  ok(m.atCharacter(raw.indexOf("Первый")+1)==0,"first start");
  ok(m.atCharacter(raw.indexOf("третий")+3)==2,"middle");
  ok(m.atCharacter(raw.indexOf("Пятый")+1)==4,"last");
  ok(m.atCharacter(raw.length()+10)==4,"after last");
  ok(ReadingWordMap.portion(0)==1&&ReadingWordMap.portion(5)==4,"portion bounds");
  ok(ReadingWordMap.advance(3,3,5)==4,"end bound");
  ok(ReadingWordMap.delayMillis(300,4)==800,"word speed");
  ok(ReadingWordMap.orp(1)==0&&ReadingWordMap.orp(10)==3,"orp");
  System.out.println("PASS: ordinary/RSVP/cursor shared word map, batch timing and tap positions");
 }
}
JAVA
javac -d "$OUT" app/src/main/java/ru/reader/rsvp/ReadingWordMap.java "$OUT/ReaderModesTest.java"
java -cp "$OUT" ru.reader.rsvp.ReaderModesTest
python3 - <<'PYTEST'
from pathlib import Path
base=Path('app/src/main/java/ru/reader/rsvp')
reader=(base/'ReaderActivity.java').read_text()
settings=(base/'SettingsActivity.java').read_text()
player=(base/'AudioPlayerActivity.java').read_text()
service=(base/'AudioPlaybackService.java').read_text()
edge=(base/'EdgeOnlineActivity.java').read_text()
assert all(x in reader for x in ['NORMAL=0, RSVP=1, CURSOR=2','void modeDialog()','void setMode(int next)','ReadingWordMap','Выбрать стартовое слово','normalText.setOnTouchListener','void updateCursor(boolean ensureVisible)','FocusLinesView','rsvp_focus_point','rsvp_focus_lines','rsvp_portion'])
assert all(x in settings for x in ['Размер порции','Точка фокусировки','Линии фокусировки','rsvp_line_position','cursor_background','cursor_text','Обычный','Скорочтение','Курсор'])
assert 'renameAudioBook()' in player and 'AudioLibrary.save(this,all)' in player and 'R.drawable.ic_edit_name' in player
assert 'case RENAME:' in service and 'title=newTitle.trim()' in service
assert 'timerValue.setVisibility(remaining>0?View.VISIBLE:View.GONE)' in player
assert 'sleepValue.setVisibility(ms>0?View.VISIBLE:View.GONE)' in edge
assert "versionCode 11047; versionName '1.11.40-beta'" in Path('app/build.gradle').read_text()
print('PASS: new reader and audio user-interface logic is present')
PYTEST
