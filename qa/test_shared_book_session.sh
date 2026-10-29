#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/src/android/content" "$WORK/src/android/net" "$WORK/src/android/provider" "$WORK/src/android/database" "$WORK/src/ru/reader/rsvp" "$WORK/classes"
cat > "$WORK/src/android/net/Uri.java" <<'EOF'
package android.net;
public final class Uri {final String s;private Uri(String value){s=value;}
public static Uri parse(String value){return new Uri(value);}
@Override public String toString(){return s;}}
EOF
cat > "$WORK/src/android/provider/OpenableColumns.java" <<'EOF'
package android.provider;
public final class OpenableColumns {public static final String SIZE="_size";}
EOF
cat > "$WORK/src/android/database/Cursor.java" <<'EOF'
package android.database;
public interface Cursor extends AutoCloseable {boolean moveToFirst();boolean isNull(int i);long getLong(int i);void close();}
EOF
cat > "$WORK/src/android/content/ContentResolver.java" <<'EOF'
package android.content;
import android.net.Uri;import android.database.Cursor;
public class ContentResolver {public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){return null;}}
EOF
cat > "$WORK/src/android/content/Context.java" <<'EOF'
package android.content;
import java.io.File;
public class Context {private final File root;public Context(File file){root=file;}
public File getFilesDir(){return root;}public ContentResolver getContentResolver(){return new ContentResolver();}}
EOF
cat > "$WORK/src/ru/reader/rsvp/BookLoader.java" <<'EOF'
package ru.reader.rsvp;
import android.content.Context;import android.net.Uri;import java.util.Arrays;
public final class BookLoader {
 static int parsed=0;
 static final class Book {final String title,author,text;final BookChapterIndex chapters;
 Book(String title,String author,String text,BookChapterIndex toc){this.title=title;this.author=author;this.text=text;chapters=toc;}}
 static Book load(Context ctx,Uri uri){parsed++;
  String text="Глава 1\\n Первый абзац. Второе предложение.\\n\\nГлава 2\\n Третье предложение.";
  BookChapterIndex toc=BookChapterIndex.build(Arrays.asList(new BookChapterIndex.Chapter("Глава 1",0,0,1),
      new BookChapterIndex.Chapter("Глава 2",text.indexOf("Глава 2"),0,1)),text.length());
  return new Book("Книга","Автор",text,toc);
 }
}
EOF
cat > "$WORK/src/ru/reader/rsvp/TestSharedBook.java" <<'EOF'
package ru.reader.rsvp;
import android.net.Uri;import android.content.Context;import java.io.File;import java.lang.reflect.Field;
public class TestSharedBook {
 private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  Context context=new Context(new File(args[0]));Uri uri=Uri.parse("content://one-book.epub");
  UnifiedBookSession first=UnifiedBookSession.open(context,uri);
  check(BookLoader.parsed==1,"first open parsed once");
  check(first.book.chapters.size()==2,"EPUB chapter navigation preserved");
  check(first.words.size()>5 && first.blocks.size()>0,"shared UI indexes are built");
  check(UnifiedBookSession.open(context,uri)==first,"mode switch is zero-reload");
  check(BookLoader.parsed==1,"mode switch never reopened EPUB");
  check(first.edgeGroups()==first.edgeGroups(),"Edge grouped sentence list reused between mode switches");
  Field active=UnifiedBookSession.class.getDeclaredField("active");active.setAccessible(true);active.set(null,null);
  UnifiedBookSession disk=UnifiedBookSession.open(context,uri);
  check(BookLoader.parsed==1,"cold app start used disk cache without original EPUB");
  check(disk.book.chapters.size()==2,"disk chapter names restored");
  check(disk.words.size()==first.words.size(),"word positions retained");
  check(disk.speechSegments.size()==first.speechSegments.size(),"speech index regenerated consistently");
  System.out.println("PASS shared book session, one parse, RBI3 cache chapters, word/UI/TTS indexes");
 }
}
EOF
javac -encoding UTF-8 -d "$WORK/classes" \
 "$WORK/src/android/net/Uri.java" "$WORK/src/android/provider/OpenableColumns.java" \
 "$WORK/src/android/database/Cursor.java" "$WORK/src/android/content/Context.java" \
 "$WORK/src/android/content/ContentResolver.java" \
 "$WORK/src/ru/reader/rsvp/BookLoader.java" \
 app/src/main/java/ru/reader/rsvp/BookChapterIndex.java \
 app/src/main/java/ru/reader/rsvp/ReaderBookCache.java \
 app/src/main/java/ru/reader/rsvp/ReadingWordMap.java \
 app/src/main/java/ru/reader/rsvp/BookChunkIndex.java \
 app/src/main/java/ru/reader/rsvp/SentenceChunks.java \
 app/src/main/java/ru/reader/rsvp/EdgeSpeechSegments.java \
 app/src/main/java/ru/reader/rsvp/UnifiedBookSession.java \
 "$WORK/src/ru/reader/rsvp/TestSharedBook.java"
java -cp "$WORK/classes" ru.reader.rsvp.TestSharedBook "$WORK"
