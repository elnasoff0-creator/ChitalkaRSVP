#!/bin/sh
set -eu
HERE="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
javac -d "$WORK" "$HERE/app/src/main/java/ru/reader/rsvp/MrtQueuePlanner.java"
cat > "$WORK/TestMrtQueue.java" <<'JAVA'
import ru.reader.rsvp.MrtQueuePlanner;
import java.util.*;
public class TestMrtQueue {
 public static void main(String[] args) {
   for (int i=3;i<=10;i++) {
     List<Integer> slots=MrtQueuePlanner.window(300,10000,i,start->start+100);
     if(slots.size()!=i || slots.get(0)!=300 || slots.get(i-1)!=300+100*(i-1)) throw new AssertionError(slots);
   }
   if(!MrtQueuePlanner.window(900,1000,5,s->s+100).equals(Arrays.asList(900)))throw new AssertionError("end");
   if(!MrtQueuePlanner.window(0,0,3,s->s+100).isEmpty())throw new AssertionError("empty");
   if(!MrtQueuePlanner.window(3,1000,3,s->s).isEmpty())throw new AssertionError("stalled split");
   System.out.println("PASS MRT queue window, 3-10, end and invalid boundary");
 }
}
JAVA
javac -cp "$WORK" -d "$WORK" "$WORK/TestMrtQueue.java"
java -cp "$WORK" TestMrtQueue
