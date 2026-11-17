"""Type-check new Android-facing progress model against minimal API contracts (not full Gradle)."""
from pathlib import Path
import tempfile, subprocess
R=Path(__file__).resolve().parent.parent
J=R/'app/src/main/java/ru/reader/rsvp'
sources={
'android/content/Context.java':'''package android.content; public class Context{public Context getApplicationContext(){return this;}public java.io.File getFilesDir(){return new java.io.File(".");}}''',
'android/os/Looper.java':'''package android.os;public final class Looper{public static Looper getMainLooper(){return new Looper();}}''',
'android/os/Handler.java':'''package android.os;public final class Handler{public Handler(Looper loop){}public void post(Runnable action){} public void postDelayed(Runnable action,long millis){}public void removeCallbacks(Runnable action){}}''',
'android/os/SystemClock.java':'''package android.os;public final class SystemClock{public static long elapsedRealtime(){return 1000L;}}''',
'android/util/Log.java':'''package android.util;public final class Log{public static int w(String tag,String text,Throwable error){return 0;}}''',
'android/util/AtomicFile.java':'''package android.util;public final class AtomicFile{public AtomicFile(java.io.File file){}public java.io.File getBaseFile(){return new java.io.File(".");}public java.io.InputStream openRead() throws java.io.IOException{return null;}public java.io.FileOutputStream startWrite() throws java.io.IOException{return null;}public void finishWrite(java.io.FileOutputStream stream){}public void failWrite(java.io.FileOutputStream stream){}}''',
'org/json/JSONObject.java':'''package org.json;public class JSONObject{public JSONObject(){}public JSONObject(String json){}public JSONObject put(String key,Object value){return this;}public int optInt(String key){return 0;}public String optString(String key){return "";}public String toString(){return "{}";}}''',
}
classes=['DownloadProgressTracker.java','DownloadProgressStore.java','DownloadChangeBus.java','DownloadCommandController.java']
with tempfile.TemporaryDirectory() as d:
    tmp=Path(d); files=[]
    for name,content in sources.items():
        f=tmp/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(content);files.append(str(f))
    files.extend(str(J/name) for name in classes)
    result=subprocess.run(['javac','-encoding','UTF-8','-d',str(tmp/'classes'),*files],text=True,capture_output=True)
    if result.returncode:raise SystemExit('Java 17 type-check failed:\n'+result.stdout+'\n'+result.stderr)
print('PASS: new download progress/cache/commands/bus Java17 type-check with API stubs')
