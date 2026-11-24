#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/src/android/content/res" "$tmp/src/android/content" "$tmp/src/android/net" "$tmp/src/android/os" "$tmp/src/org/json" "$tmp/src/ru/reader/rsvp"
cat > "$tmp/src/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 public static final String CONNECTIVITY_SERVICE="connectivity";
 public Context getApplicationContext(){return this;}
 public Object getSystemService(String s){return null;}
 public java.io.File getCacheDir(){return new java.io.File(".");}
 public android.content.res.AssetManager getAssets(){return null;}
}
JAVA
cat > "$tmp/src/android/content/res/AssetManager.java" <<'JAVA'
package android.content.res;
public class AssetManager {public java.io.InputStream open(String path) throws java.io.IOException {return null;}}
JAVA
cat > "$tmp/src/android/net/ConnectivityManager.java" <<'JAVA'
package android.net;
public class ConnectivityManager {
 public Network getActiveNetwork(){return null;}
 public NetworkCapabilities getNetworkCapabilities(Network n){return null;}
 public LinkProperties getLinkProperties(Network n){return null;}
}
JAVA
cat > "$tmp/src/android/net/Network.java" <<'JAVA'
package android.net; public final class Network {}
JAVA
cat > "$tmp/src/android/net/NetworkCapabilities.java" <<'JAVA'
package android.net;
public class NetworkCapabilities {
 public static final int TRANSPORT_WIFI=1;
 public boolean hasTransport(int type){return false;}
}
JAVA
cat > "$tmp/src/android/net/LinkProperties.java" <<'JAVA'
package android.net;
public class LinkProperties {
 public java.util.List<LinkAddress> getLinkAddresses(){return java.util.Collections.emptyList();}
}
JAVA
cat > "$tmp/src/android/net/LinkAddress.java" <<'JAVA'
package android.net;
public class LinkAddress {public java.net.InetAddress getAddress(){return null;}}
JAVA
cat > "$tmp/src/android/os/SystemClock.java" <<'JAVA'
package android.os;
public final class SystemClock {public static long elapsedRealtime(){return 0L;}}
JAVA
cat > "$tmp/src/org/json/JSONObject.java" <<'JAVA'
package org.json;
public class JSONObject {
 public JSONObject put(String k,Object value){return this;}
 public String toString(){return "{}";}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/WebTransferDependencies.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
import java.io.File;
final class WebTransferPrefs {
 static int idleMinutes(Context c){return 15;}
 static int port(Context c){return 0;}
}
final class WebTransferPolicy {
 static boolean sameOrigin(String host,String origin,String expected){return true;}
 static long length(String s,String transferEncoding,long max){return 1L;}
}
final class WebTransferUploadManager {
 static final long MAX_BYTES=1073741824L;
 static String sanitize(String name){return name;}
 static String category(String name){return "book";}
 static Outcome commit(Context c,File temp,String name,String collision){return new Outcome();}
 static final class Outcome {String name="book",uri="content://test/1";long bytes=123L;}
}
final class WebTransferService {
 static void notifyCompleted(Context c,String name){}
}
final class DownloadFolderPicker {
 static boolean available(Context c,String type){return true;}
 static String label(String type){return type;}
}
final class WebTransferDownloadStore {
 static final String CANCELLED="cancelled",DONE="done",DUPLICATE="duplicate",ERROR="error";
 static String newId(){return "wifi:example";}
 static boolean begin(Context c,String id,String name,String kind,long length){return true;}
 static boolean cancel(Context c,String id){return true;}
 static boolean cancelled(Context c,String id){return false;}
 static void progress(Context c,String id,long received,long total){}
 static void importing(Context c,String id){}
 static void finish(Context c,String id,String status,String error,String uri,long bytes){}
 static void interruptActive(Context c,String error){}
}
JAVA
javac -encoding UTF-8 -d "$tmp/bin" \
  $(find "$tmp/src" -name '*.java') \
  "$root/app/src/main/java/ru/reader/rsvp/WebTransferServer.java"
echo 'PASS: WebTransferServer Android API typecheck using isolated javac stubs'
