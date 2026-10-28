#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/ru/reader/rsvp"
cat > "$TMP/ru/reader/rsvp/TestEdgePrefetchPlanner.java" <<'JAVA'
package ru.reader.rsvp;
import java.util.*;
class TestEdgePrefetchPlanner {
 static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
 public static void main(String[] args){
  List<Integer> all=Arrays.asList(100,100,100,100,100,100,100,100,100,100);
  check(EdgePrefetchPlanner.select(all,1200,12).size()==6,"take(6) cap missing");
  check(EdgePrefetchPlanner.select(all,200,12).size()==2,"text budget missing");
  check(EdgePrefetchPlanner.select(all,1200,2).size()==2,"count cap missing");
  check(EdgePrefetchPlanner.select(Arrays.asList(900,200),400,6).size()==1,"oversize first candidate");
  check(EdgePrefetchPlanner.budget(650,false)==1200,"large text budget");
  check(EdgePrefetchPlanner.budget(100,true)==100,"idle budget");
  check(EdgePrefetchPlanner.select(Collections.emptyList(),1200,6).isEmpty(),"empty case");
  System.out.println("PASS: Quickify-inspired six-candidate limit, char budget, count limit");
 }
}
JAVA
javac -encoding UTF-8 -d "$TMP" \
  "$ROOT/app/src/main/java/ru/reader/rsvp/EdgePrefetchPlanner.java" \
  "$TMP/ru/reader/rsvp/TestEdgePrefetchPlanner.java"
java -cp "$TMP" ru.reader.rsvp.TestEdgePrefetchPlanner
