#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/src/android/content" "$TMP/src/android/os" "$TMP/src/android/util" "$TMP/src/ru/reader/rsvp" "$TMP/classes"
cat > "$TMP/src/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 private final java.io.File root;
 public Context(java.io.File root){this.root=root;}
 public Context getApplicationContext(){return this;}
 public java.io.File getFilesDir(){return root;}
}
JAVA
cat > "$TMP/src/android/os/Looper.java" <<'JAVA'
package android.os;
public class Looper {public static Looper getMainLooper(){return new Looper();}}
JAVA
cat > "$TMP/src/android/os/Handler.java" <<'JAVA'
package android.os;
public class Handler {public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}
JAVA
cat > "$TMP/src/android/util/Log.java" <<'JAVA'
package android.util;
public class Log {public static int w(String key,String msg){System.err.println(key+" "+msg);return 0;}}
JAVA
cat > "$TMP/src/ru/reader/rsvp/TestReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
public final class TestReaderDiagnosticLog {
 static void yes(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 static String snapshot()throws Exception{
   CountDownLatch ready=new CountDownLatch(1);String[] answer={null};
   ReaderDiagnosticLog.snapshot(s->{answer[0]=s;ready.countDown();});
   yes(ready.await(10,TimeUnit.SECONDS),"snapshot did not complete");return answer[0];
 }
 public static void main(String[] args)throws Exception {
   File root=Files.createTempDirectory("prochitalka_log_test").toFile();
   ReaderDiagnosticLog.init(new Context(root));
   ReaderDiagnosticLog.event("EDGE","HTTP 429 status=429 url=https://private.example.com/?secret=xxx");
   ReaderDiagnosticLog.event("POOL","resource=2 reused=true");
   ReaderDiagnosticLog.event("SECURITY","Authorization: Bearer SECRET_VALUE_123");
   String initial=snapshot();
   yes(initial.contains("HTTP 429"),"rate limit not recorded");
   yes(initial.contains("resource=2"),"pool event not recorded");
   yes(!initial.contains("private.example.com"),"private URL leaked");
   yes(!initial.contains("SECRET_VALUE_123"),"authorization secret leaked");
   // Bounded files, old events rotated, export includes both files in order.
   String payload="x".repeat(230);
   for(int i=0;i<4000;i++)ReaderDiagnosticLog.event("TEST","index="+i+" "+payload);
   String end=snapshot();
   yes(end.contains("index=3999"),"most recent event missing");
   File dir=new File(root,"diagnostics");
   File cur=new File(dir,"prochitalka_log_0.txt"),old=new File(dir,"prochitalka_log_1.txt");
   yes(cur.length()<=512*1024,"current log exceeded size limit");
   yes(old.length()<=512*1024,"rotated log exceeded size limit");
   CountDownLatch done=new CountDownLatch(1);File[] exported={null};
   ReaderDiagnosticLog.prepareExport(f->{exported[0]=f;done.countDown();});
   yes(done.await(10,TimeUnit.SECONDS)&&exported[0].isFile(),"export failed");
   String output=Files.readString(exported[0].toPath());
   yes(output.contains("index=3999"),"export missing final events");
   CountDownLatch cleared=new CountDownLatch(1);
   ReaderDiagnosticLog.clear(cleared::countDown);
   yes(cleared.await(10,TimeUnit.SECONDS),"clear callback missing");
   yes(!cur.exists()&&!old.exists()&&!exported[0].exists(),"clear left log files");
   System.out.println("PASS: local diagnostic log persists, redacts URLs, rotates, exports, clears");
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP/classes" \
 "$TMP/src/android/content/Context.java" "$TMP/src/android/os/Looper.java" \
 "$TMP/src/android/os/Handler.java" "$TMP/src/android/util/Log.java" \
 app/src/main/java/ru/reader/rsvp/ReaderDiagnosticLog.java \
 "$TMP/src/ru/reader/rsvp/TestReaderDiagnosticLog.java"
java -cp "$TMP/classes" ru.reader.rsvp.TestReaderDiagnosticLog
python3 - <<'PY'
from pathlib import Path
root=Path('app/src/main')
manifest=(root/'AndroidManifest.xml').read_text()
paths=(root/'res/xml/file_paths.xml').read_text()
settings=(root/'java/ru/reader/rsvp/SettingsActivity.java').read_text()
viewer=(root/'java/ru/reader/rsvp/ReaderLogActivity.java').read_text()
app=(root/'java/ru/reader/rsvp/ReaderApp.java').read_text()
assert '<activity android:name=".ReaderLogActivity" android:exported="false" />' in manifest
assert 'diagnostic_logs' in paths and 'diagnostics/' in paths
assert 'Показать лог-файл' in settings and 'ReaderLogActivity.class' in settings
assert 'ReaderDiagnosticLog.init(this)' in app
assert 'Intent.FLAG_GRANT_READ_URI_PERMISSION' in viewer
assert 'android.permission.READ_LOGS' not in manifest
for label in ['EdgeSpeechEngine.java','EdgeWebSocketPool.java','EdgeStreamingService.java','EdgePlaybackService.java','EdgeSpeechQueue.java']:
 assert 'ReaderDiagnosticLog.event' in (root/'java/ru/reader/rsvp'/label).read_text(),label
print('PASS: settings, manifest, FileProvider, Edge event instrumentation, no READ_LOGS')
PY
