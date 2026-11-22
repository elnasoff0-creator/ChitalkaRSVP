#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/src/android/content" "$tmp/src/android/database/sqlite" "$tmp/src/android/database" "$tmp/src/ru/reader/rsvp"
cat > "$tmp/src/android/content/Context.java" <<'JAVA'
package android.content;
public class Context {
 public static final int MODE_PRIVATE=0;
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String a,int b){return null;}
 public java.io.File getFilesDir(){return new java.io.File(".");}
 public String getPackageName(){return "ru.reader.rsvp";}
 public void sendBroadcast(Intent i){}
}
JAVA
cat > "$tmp/src/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public interface SharedPreferences {
 int getInt(String key,int def);
 boolean getBoolean(String key,boolean def);
 String getString(String key,String def);
 Editor edit();
 interface Editor {Editor putBoolean(String key,boolean val);
  Editor putInt(String key,int val);boolean commit();}
}
JAVA
cat > "$tmp/src/android/content/Intent.java" <<'JAVA'
package android.content;
public class Intent {
 public Intent(String action){}
 public Intent setPackage(String packageName){return this;}
 public Intent putExtra(String key,int value){return this;}
}
JAVA
cat > "$tmp/src/android/content/ContentValues.java" <<'JAVA'
package android.content;
public class ContentValues extends java.util.HashMap<String,Object> {
 public void put(String key,String value){super.put(key,value);}
 public void put(String key,Integer value){super.put(key,value);}
}
JAVA
cat > "$tmp/src/android/database/Cursor.java" <<'JAVA'
package android.database;
public interface Cursor extends AutoCloseable {
 boolean moveToNext();boolean moveToFirst();
 String getString(int col);int getInt(int col);long getLong(int col);
 void close();
}
JAVA
cat > "$tmp/src/android/database/sqlite/SQLiteDatabase.java" <<'JAVA'
package android.database.sqlite;
public class SQLiteDatabase {
 public void execSQL(String sql){}
 public long insertOrThrow(String table,String nullColumn,android.content.ContentValues content){return 0;}
 public long insert(String table,String nullColumn,android.content.ContentValues content){return 0;}
 public int update(String table,android.content.ContentValues values,String where,String[] args){return 0;}
 public int delete(String table,String where,String[] args){return 0;}
 public android.database.Cursor rawQuery(String sql,String[] args){return null;}
 public void beginTransaction(){}public void setTransactionSuccessful(){}public void endTransaction(){}
}
JAVA
cat > "$tmp/src/android/database/sqlite/SQLiteOpenHelper.java" <<'JAVA'
package android.database.sqlite;
public abstract class SQLiteOpenHelper {
 public SQLiteOpenHelper(android.content.Context c,String name,Object f,int version){}
 public abstract void onCreate(SQLiteDatabase db);
 public abstract void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion);
 public SQLiteDatabase getReadableDatabase(){return null;}
 public SQLiteDatabase getWritableDatabase(){return null;}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
final class ReaderDiagnosticLog {static void event(String key,String value){} }
JAVA
cat > "$tmp/src/ru/reader/rsvp/EdgeSpeechTextTransform.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.List;
import java.util.regex.Pattern;
final class EdgeSpeechTextTransform {
 static final String DICTIONARY="d",IGNORED="i",REPLACEMENT="r";
 static class Entry{Entry(int kind,String pattern,String context,String replacement,boolean regex){} }
 static class Options {
  String mode="off",replacement=",";
  Options(String a,String b,List<Entry> entries,int rev){}
 }
 static Pattern compileUserRegex(String regex){return Pattern.compile(regex);}
 static void validateReplacement(Pattern p,String r){}
}
JAVA
javac -encoding UTF-8 -d "$tmp/bin" \
 $(find "$tmp/src" -name '*.java') \
 "$root/app/src/main/java/ru/reader/rsvp/TtsDictionaryCsv.java" \
 "$root/app/src/main/java/ru/reader/rsvp/TtsDictionaryImportPlan.java" \
 "$root/app/src/main/java/ru/reader/rsvp/EdgeDictionaryStore.java"
echo 'PASS: EdgeDictionaryStore Java types, SQLite migration/import API, background option precompile'
