#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
mkdir -p "$OUT/android/content" "$OUT/android/os"
cat > "$OUT/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
  public java.io.File getExternalFilesDir(String name){return new java.io.File("/sdcard/AppDocs");}
  public java.io.File getFilesDir(){return new java.io.File("/data/local/app");}
}
JAVA
cat > "$OUT/android/os/Environment.java" <<'JAVA'
package android.os;
public class Environment{public static final String DIRECTORY_DOCUMENTS="Documents";}
JAVA
cat > "$OUT/EdgeStorageTest.java" <<'JAVA'
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import ru.reader.rsvp.EdgeBookStorage;
public class EdgeStorageTest {
  private static String expected(String str){
    try{byte[] b=MessageDigest.getInstance("SHA-256").digest(str.getBytes(StandardCharsets.UTF_8));
      StringBuilder result=new StringBuilder();for(byte v:b)result.append(String.format(java.util.Locale.ROOT,"%02x",v&255));return result.toString();
    }catch(Exception e){throw new AssertionError(e);}
  }
  public static void main(String[] args){
    String text="Текст книги",voice="ru-RU-SvetlanaNeural",rate="+0%",backend="edge-direct-v1";
    String historical="mrt-prefetch-v1\n"+voice+"\n"+rate+"\n"+backend+"\n"+text;
    if(!expected(historical).equals(EdgeBookStorage.key(text,voice,rate,backend)))throw new AssertionError("Edge cache keys changed");
    String uri="content://test/book/123";
    String folder=EdgeBookStorage.bookFolder(new android.content.Context(),uri,"Test Book").getName();
    if(!folder.equals("Test Book--"+expected(uri).substring(0,16)))throw new AssertionError("Existing Edge folder changed: "+folder);
    System.out.println("Edge MP3 cache backward compatibility: PASS");
  }
}
JAVA
javac -d "$OUT" "$OUT/android/content/Context.java" "$OUT/android/os/Environment.java" app/src/main/java/ru/reader/rsvp/EdgeBookStorage.java "$OUT/EdgeStorageTest.java"
java -cp "$OUT" EdgeStorageTest
