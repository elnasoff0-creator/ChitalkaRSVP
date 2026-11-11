#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
P="$T/src";mkdir -p "$P/android/app" "$P/android/content" "$P/android/net" "$P/android/widget" "$P/android/provider" "$P/androidx/documentfile/provider" "$P/ru/reader/rsvp"
cat > "$P/android/net/Uri.java" <<'JAVA'
package android.net;public class Uri{public static Uri parse(String s){return null;} public String toString(){return "";}}
JAVA
cat > "$P/android/content/SharedPreferences.java" <<'JAVA'
package android.content;public interface SharedPreferences{String getString(String name,String def);Editor edit();interface Editor{Editor putString(String name,String value);void apply();}}
JAVA
cat > "$P/android/content/UriPermission.java" <<'JAVA'
package android.content;public class UriPermission{public android.net.Uri getUri(){return null;}public boolean isReadPermission(){return true;}public boolean isWritePermission(){return true;}}
JAVA
cat > "$P/android/content/ContentResolver.java" <<'JAVA'
package android.content;public class ContentResolver{public java.util.List<UriPermission> getPersistedUriPermissions(){return null;}public void takePersistableUriPermission(android.net.Uri uri,int flags){}}
JAVA
cat > "$P/android/content/Context.java" <<'JAVA'
package android.content;public class Context{public SharedPreferences getSharedPreferences(String a,int b){return null;}public ContentResolver getContentResolver(){return null;}}
JAVA
cat > "$P/android/app/Activity.java" <<'JAVA'
package android.app;public class Activity extends android.content.Context{public static final int RESULT_OK=-1;public void startActivityForResult(android.content.Intent i,int id){}}
JAVA
cat > "$P/android/content/Intent.java" <<'JAVA'
package android.content;public class Intent{public static final String ACTION_OPEN_DOCUMENT_TREE="";public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_GRANT_WRITE_URI_PERMISSION=2,FLAG_GRANT_PERSISTABLE_URI_PERMISSION=4;public Intent(String s){}public Intent addFlags(int flags){return this;}public Intent putExtra(String key,android.net.Uri value){return this;}public android.net.Uri getData(){return null;}}
JAVA
cat > "$P/android/provider/DocumentsContract.java" <<'JAVA'
package android.provider;public class DocumentsContract{public static final String EXTRA_INITIAL_URI="";}
JAVA
cat > "$P/android/widget/Toast.java" <<'JAVA'
package android.widget;public class Toast{public static final int LENGTH_SHORT=0,LENGTH_LONG=1;public static Toast makeText(android.content.Context c,String s,int v){return null;}public void show(){}}
JAVA
cat > "$P/androidx/documentfile/provider/DocumentFile.java" <<'JAVA'
package androidx.documentfile.provider;public class DocumentFile{public static DocumentFile fromTreeUri(android.content.Context c,android.net.Uri u){return null;}public boolean exists(){return true;}public boolean isDirectory(){return true;}public boolean canWrite(){return true;}}
JAVA
cat > "$P/ru/reader/rsvp/EdgeChapterArchive.java" <<'JAVA'
package ru.reader.rsvp;public class EdgeChapterArchive{
 public static void invalidate(){}
}
JAVA
cat > "$P/ru/reader/rsvp/SwipeDialogs.java" <<'JAVA'
package ru.reader.rsvp; public class SwipeDialogs{public static class Builder{public Builder(android.app.Activity a){}public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setNegativeButton(String s,Click c){return this;}public Builder setPositiveButton(String s,Click c){return this;}public void show(){}}public interface Click{void click(Object dialog,int number);}}
JAVA
javac -encoding UTF-8 -source 17 -target 17 -d "$T/bin" $(find "$P" -name '*.java') app/src/main/java/ru/reader/rsvp/EdgeFolderPicker.java
printf 'PASS: Android SAF folder picker Java API signatures verified with test stubs\n'
