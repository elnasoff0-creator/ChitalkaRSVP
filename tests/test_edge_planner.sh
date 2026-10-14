#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
cat > "$OUT/EdgeQueuePlannerTest.java" <<'JAVA'
import java.util.ArrayList;
import ru.reader.rsvp.EdgeQueuePlanner;
public class EdgeQueuePlannerTest {
  static void check(boolean value) {if(!value)throw new AssertionError("Edge queue test failed");}
  public static void main(String[] args) {
    ArrayList<Integer> out=EdgeQueuePlanner.window(0,150,3,st->Math.min(150,st+40));
    check(out.size()==3 && out.get(0)==0 && out.get(1)==40 && out.get(2)==80);
    ArrayList<Integer> noPrefetch=EdgeQueuePlanner.window(10,80,1,st->Math.min(80,st+20));
    check(noPrefetch.size()==1 && noPrefetch.get(0)==10);
    check(EdgeQueuePlanner.window(0,0,3,st->10).isEmpty());
    check(EdgeQueuePlanner.window(0,10,3,st->st).isEmpty());
    System.out.println("Edge queue window and no-prefetch: PASS");
  }
}
JAVA
javac -d "$OUT" app/src/main/java/ru/reader/rsvp/EdgeQueuePlanner.java "$OUT/EdgeQueuePlannerTest.java"
java -cp "$OUT" EdgeQueuePlannerTest
