#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/android/os" "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return System.nanoTime()/1_000_000L;}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
final class ReaderDiagnosticLog {static void event(String c,String d){} }
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestEdgeClockSync.java" <<'JAVA'
package ru.reader.rsvp;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

final class TestEdgeClockSync {
 static class Memory implements EdgeServerClock.OffsetStore {
  final AtomicLong skew=new AtomicLong(),when=new AtomicLong();
  volatile CountDownLatch changed=new CountDownLatch(1);
  @Override public long savedSkew(){return skew.get();}
  @Override public long savedAt(){return when.get();}
  @Override public void save(long a,long b){skew.set(a);when.set(b);changed.countDown();}
 }
 static void check(boolean value,String msg){if(!value)throw new AssertionError(msg);}
 static String date(long wall){
  SimpleDateFormat f=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'",Locale.US);
  f.setTimeZone(TimeZone.getTimeZone("GMT"));return f.format(new Date(wall));
 }
 static long delta(){return EdgeServerClock.adjustedEpochSeconds()-System.currentTimeMillis()/1000L;}
 static void reset()throws Exception{
  for(String name:new String[]{"persistence","skewMillis","lastSampleElapsed"}){
   Field f=EdgeServerClock.class.getDeclaredField(name);f.setAccessible(true);
   if(name.equals("persistence"))f.set(null,null);
   else f.setLong(null,name.equals("lastSampleElapsed")?-1:0);
  }
 }
 public static void main(String[] args)throws Exception {
  reset();Memory store=new Memory();EdgeServerClock.initialize(store);
  long now=System.currentTimeMillis(),tick=android.os.SystemClock.elapsedRealtime();
  long began=System.nanoTime();
  EdgeServerClock.observeHttpDate(date(now+600_000),101,now-80,tick-80,now,tick);
  check((System.nanoTime()-began)<150_000_000L,"the callback waited for sync");
  check(store.changed.await(3,TimeUnit.SECONDS),"101 sample wasn't persisted");
  check(Math.abs(delta()-600)<3,"101 did not calibrate GEC time: "+delta());
  long saved=store.skew.get();
  // Neither malformed Date nor invalid status may overwrite a valid sample.
  EdgeServerClock.observeHttpDate("invalid",101,now-5,tick-5,now,tick+1);
  EdgeServerClock.observeHttpDate(date(now-900_000),500,now-5,tick-5,now,tick+1);
  EdgeServerClock.observeHttpDate(date(now-900_000),101,now-30_000,tick-30_000,now,tick+1);
  Thread.sleep(120);
  check(store.skew.get()==saved,"invalid/long RTT sample mutated clock");
  // Older observations cannot undo a newer successful sync.
  EdgeServerClock.observeHttpDate(date(now-900_000),101,now-5,tick-5,now,tick-1);
  Thread.sleep(120);
  check(store.skew.get()==saved,"stale time sample won race");
  // Restored calibration remains available before any new Edge request.
  reset();EdgeServerClock.initialize(store);
  check(Math.abs(delta()-600)<3,"saved offset not restored");
  // A stale persistence value is ignored after 24h.
  reset();store.when.set(System.currentTimeMillis()-25L*60*60*1000);EdgeServerClock.initialize(store);
  check(Math.abs(delta())<3,"stale offset reused");
  // A 429 sample can be observed without any retry mechanism or network request.
  reset();Memory response429=new Memory();EdgeServerClock.initialize(response429);
  long t=android.os.SystemClock.elapsedRealtime(),w=System.currentTimeMillis();
  EdgeServerClock.observeHttpDate(date(w+300_000),429,w-10,t-10,w,t);
  check(response429.changed.await(3,TimeUnit.SECONDS),"429 Date not used passively");
  check(Math.abs(delta()-300)<3,"429 date offset invalid");
  System.out.println("PASS: 101 background sync, 429 passive sample, no blocking, durable store, stale/invalid protection");
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" $(find "$TMP/src" -name '*.java') \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeServerClock.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeToken.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestEdgeClockSync
# Verify Android SharedPreferences adapter syntax with a small SDK stand-in.
mkdir -p "$TMP/src/android/content"
cat > "$TMP/src/android/content/Context.java" <<'JAVA'
package android.content;
public abstract class Context {
 public static final int MODE_PRIVATE=0;
 public abstract Context getApplicationContext();
 public abstract SharedPreferences getSharedPreferences(String name,int mode);
}
JAVA
cat > "$TMP/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {
 long getLong(String name,long otherwise);
 Editor edit();
 interface Editor {Editor putLong(String name,long value); void apply();}
}
JAVA
javac -encoding UTF-8 -cp "$TMP/classes" -d "$TMP/classes" \
 "$TMP/src/android/content/Context.java" "$TMP/src/android/content/SharedPreferences.java" \
 "$ROOT/app/src/main/java/ru/reader/rsvp/EdgeClockPreferences.java"
echo 'PASS: Android SharedPreferences adapter compiles against SDK stand-in'
python3 - "$ROOT" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1])/'app/src/main/java/ru/reader/rsvp'
app=(p/'ReaderApp.java').read_text()
pool=(p/'EdgeWebSocketPool.java').read_text()
engine=(p/'EdgeSpeechEngine.java').read_text()
clock=(p/'EdgeServerClock.java').read_text()
assert 'EdgeServerClock.initialize(new EdgeClockPreferences(this))' in app
assert 'observeDate(lease,response)' in pool
assert 'response.code()!=403' in pool
assert 'observeHttpDate(serverDate,403' in engine
assert 'Sec-MS-GEC='+ '"+gec' in engine
assert 'newWebSocket' not in clock and 'OkHttpClient' not in clock
assert 'MAX_SAMPLE_AGE_MS=24L*60*60*1000' in clock
print('PASS: callback wiring and no extra time-sync network request')
PY
