#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/.."
jar=${JSOUP_JAR:-}
if [ -z "$jar" ]; then
  jar=$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.jsoup/jsoup/1.18.3" -name 'jsoup-1.18.3.jar' -print -quit 2>/dev/null || true)
fi
work=$(mktemp -d); trap 'rm -rf "$work"' EXIT
mkdir -p "$work/android/content" "$work/android/database" "$work/android/net" "$work/android/provider"
cat > "$work/android/content/Context.java" <<'JAVA'
package android.content; public class Context {public ContentResolver getContentResolver(){return null;}}
JAVA
cat > "$work/android/content/ContentResolver.java" <<'JAVA'
package android.content; public class ContentResolver {public android.database.Cursor query(android.net.Uri u,String[] p,String s,String[] a,String o){return null;}public java.io.InputStream openInputStream(android.net.Uri u)throws java.io.IOException{return null;}}
JAVA
cat > "$work/android/database/Cursor.java" <<'JAVA'
package android.database; public interface Cursor extends AutoCloseable{boolean moveToFirst();String getString(int n);void close();}
JAVA
cat > "$work/android/net/Uri.java" <<'JAVA'
package android.net; public class Uri {public String getLastPathSegment(){return null;}}
JAVA
cat > "$work/android/provider/OpenableColumns.java" <<'JAVA'
package android.provider; public class OpenableColumns {public static final String DISPLAY_NAME="_display_name";}
JAVA
mode=()
if [ ! -f "$jar" ]; then
  if [ "${REQUIRE_JSOUP:-0}" = 1 ]; then echo 'Missing jsoup from Gradle cache' >&2; exit 1; fi
  echo 'jsoup unavailable: compile against API doubles and run only real XML FB2 fixture.'
  mkdir -p "$work/org/jsoup/nodes" "$work/org/jsoup/select"
  cat > "$work/org/jsoup/Jsoup.java" <<'JAVA'
package org.jsoup; public class Jsoup {public static org.jsoup.nodes.Document parse(String s){throw new UnsupportedOperationException();}}
JAVA
  cat > "$work/org/jsoup/nodes/Node.java" <<'JAVA'
package org.jsoup.nodes; public class Node {public Node parent(){return null;}public java.util.List<Node> childNodes(){return java.util.Collections.emptyList();}public void remove(){}}
JAVA
  cat > "$work/org/jsoup/nodes/TextNode.java" <<'JAVA'
package org.jsoup.nodes; public class TextNode extends Node{public String getWholeText(){return "";}}
JAVA
  cat > "$work/org/jsoup/nodes/Element.java" <<'JAVA'
package org.jsoup.nodes; public class Element extends Node {public org.jsoup.select.Elements select(String s){return new org.jsoup.select.Elements();}public Element selectFirst(String s){return null;}public String attr(String s){return "";}public boolean hasAttr(String s){return false;}public String normalName(){return "";}public String id(){return "";}public String text(){return "";}public Element parent(){return null;}public Element clone(){return this;}public int childrenSize(){return 0;}public Element child(int n){return null;}}
JAVA
  cat > "$work/org/jsoup/nodes/Document.java" <<'JAVA'
package org.jsoup.nodes; public class Document extends Element{public Element body(){return null;}public String title(){return "";}}
JAVA
  cat > "$work/org/jsoup/select/Elements.java" <<'JAVA'
package org.jsoup.select; public class Elements extends java.util.ArrayList<org.jsoup.nodes.Element>{public Elements remove(){return this;}}
JAVA
  mode=(fb2-only)
  jar="$work"
fi
src=app/src/main/java/ru/reader/rsvp
javac -encoding UTF-8 -cp "$jar" -d "$work/classes" $(find "$work" -name '*.java') "$src/BookLoader.java" "$src/BookChapterIndex.java" qa/TestBookOriginalText197.java
java -cp "$work/classes:$jar" ru.reader.rsvp.TestBookOriginalText197 "${mode[@]}"
