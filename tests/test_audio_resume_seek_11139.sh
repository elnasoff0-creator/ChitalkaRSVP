#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/AudioSeekTest.java" <<'JAVA'
package ru.reader.rsvp;
public class AudioSeekTest {
  static void equal(long got,long expected){if(got!=expected)throw new AssertionError(got+" != "+expected);}
  public static void main(String[] args){
    equal(AudioSeekBounds.step(1,3,60000,65000),1);
    equal(AudioSeekBounds.remaining(65000,60000,1),5000);
    equal(AudioSeekBounds.step(1,3,60000,-25000),-1);
    equal(AudioSeekBounds.step(0,3,60000,-25000),0);
    equal(AudioSeekBounds.clamped(-25000,60000),0);
    equal(AudioSeekBounds.step(2,3,60000,80000),0);
    equal(AudioSeekBounds.clamped(80000,60000),60000);
    equal(AudioSeekBounds.step(1,3,60000,125000),1);
    equal(AudioSeekBounds.step(2,3,40000,65000),0);
    System.out.println("PASS: relative seek boundaries, clamping, long skips");
  }
}
JAVA
javac -d "$OUT" app/src/main/java/ru/reader/rsvp/AudioSeekBounds.java "$OUT/AudioSeekTest.java"
java -cp "$OUT" ru.reader.rsvp.AudioSeekTest
python3 - <<'PYTEST'
from pathlib import Path
p=Path('app/src/main/java/ru/reader/rsvp')
main=(p/'MainActivity.java').read_text()
player=(p/'AudioPlayerActivity.java').read_text()
service=(p/'AudioPlaybackService.java').read_text()
settings=(p/'SettingsActivity.java').read_text()
edge=(p/'EdgeOnlineActivity.java').read_text()
assert 'resume_audio' in main and 'openAudioBook(lastBook.id,true)' in main
assert 'openSelected(selected,resumeAudio)' in player
assert 'SEEK_RELATIVE' in player and 'AudioPlaybackPrefs.seekSeconds' in player and 'AudioPlaybackPrefs.seekMinutes' in player
assert 'void autoRewindDialog()' in settings and 'void seekStepDialog(boolean minutes)' in settings
for n in (0,5,10,15,30,60): assert str(n) in settings
assert 'rewindOnNextPlay' in service and 'playerReady' in service and 'seekPending' in service
assert 'preparedPosition=target' in service and 'AudioSeekBounds.step' in service
assert 'boolean shouldPlay=!paused' in service
assert 'sleepValue' in edge and 'timerValue' in player
assert "versionCode 11047; versionName '1.11.40-beta'" in Path('app/build.gradle').read_text()
print('PASS: 1.11.39 audio settings, resume, persistent seek, timer and playback continuity')
PYTEST
