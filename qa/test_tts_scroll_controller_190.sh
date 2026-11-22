#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/src/android/os" "$tmp/src/androidx/recyclerview/widget" "$tmp/src/ru/reader/rsvp"
cat > "$tmp/src/android/os/SystemClock.java" <<'JAVA'
package android.os;
public class SystemClock {public static long elapsedRealtime(){return 123456L;}}
JAVA
cat > "$tmp/src/androidx/recyclerview/widget/LinearLayoutManager.java" <<'JAVA'
package androidx.recyclerview.widget;
public class LinearLayoutManager {
 public int findFirstVisibleItemPosition(){return 0;}
 public int findLastVisibleItemPosition(){return 3;}
}
JAVA
cat > "$tmp/src/androidx/recyclerview/widget/RecyclerView.java" <<'JAVA'
package androidx.recyclerview.widget;
public class RecyclerView {
 public int scroll=0;
 public java.util.ArrayDeque<Runnable> frames=new java.util.ArrayDeque<>();
 public int getHeight(){return 792;}
 public int computeVerticalScrollOffset(){return scroll;}
 public void scrollBy(int x,int y){scroll+=y;}
 public void postOnAnimation(Runnable r){frames.addLast(r);}
 public void removeCallbacks(Runnable r){frames.remove(r);}
 public void pump(){if(!frames.isEmpty())frames.removeFirst().run();}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/ReaderDiagnosticLog.java" <<'JAVA'
package ru.reader.rsvp;
final class ReaderDiagnosticLog {static void event(String k,String v){}
}
JAVA
cat > "$tmp/src/ru/reader/rsvp/TestController190.java" <<'JAVA'
package ru.reader.rsvp;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
public class TestController190 {
 static class Host implements ReaderTtsScrollController.Host {
    final RecyclerView rv;int word=0;long epoch=1;boolean allowed=true;int jumps=0;
    Host(RecyclerView rv){this.rv=rv;}
    public ReaderTtsScrollController.WordLocation locateWord(int index){
        return new ReaderTtsScrollController.WordLocation(1,525+(index==0?0:30)-rv.scroll,true);
    }
    public long navigationEpoch(){return epoch;}
    public boolean mayFollow(boolean explicit){return allowed;}
    public void jumpToAdapterPosition(int position,int offsetPx){jumps++;}
 }
 static void yes(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 public static void main(String[] args){
   RecyclerView rv=new RecyclerView();Host host=new Host(rv);
   ReaderTtsScrollController c=new ReaderTtsScrollController(rv,new LinearLayoutManager(),host,88,88,5,1,22);
   c.follow(0,false);int frames=0;
   while(!rv.frames.isEmpty()&&frames++<120)rv.pump();
   int anchor=88+Math.round((792-88-88)*.61f);
   yes(frames>4&&frames<120,"follow must run multiple frames then stop");
   yes(Math.abs(525-rv.scroll-anchor)<=5,"follow reached anchor not just 69% threshold");
   yes(host.jumps==0,"routine TTS never jumps");
   rv.scroll=0;
   c.follow(0,true);rv.pump();c.follow(1,false);frames=0;
   while(!rv.frames.isEmpty()&&frames++<120)rv.pump();
   int explicitAnchor=88+Math.round((792-88-88)*.50f);
   yes(Math.abs(555-rv.scroll-explicitAnchor)<=5,"explicit return must survive next WordBoundary");
   rv.scroll=0;c.follow(0,false);rv.pump();int frozen=rv.scroll;
   host.epoch++;while(!rv.frames.isEmpty())rv.pump();
   yes(frozen==rv.scroll,"stale epoch cannot scroll");
   c.follow(0,false);rv.pump();int prior=rv.scroll;c.cancel("MANUAL_SCROLL");
   while(!rv.frames.isEmpty())rv.pump();
   yes(prior==rv.scroll,"manual touch cancels animation");
   c.dispose();yes(rv.frames.isEmpty(),"disposed controller leaves no callbacks");
   System.out.println("PASS: full follow animation, explicit retarget, epoch cancellation, manual scroll");
 }
}
JAVA
javac -encoding UTF-8 -d "$tmp/bin" \
 "$tmp/src/android/os/SystemClock.java" \
 "$tmp/src/androidx/recyclerview/widget/RecyclerView.java" \
 "$tmp/src/androidx/recyclerview/widget/LinearLayoutManager.java" \
 "$tmp/src/ru/reader/rsvp/ReaderDiagnosticLog.java" \
 "$root/app/src/main/java/ru/reader/rsvp/ReaderTtsScrollPolicy.java" \
 "$root/app/src/main/java/ru/reader/rsvp/ReaderTtsScrollController.java" \
 "$tmp/src/ru/reader/rsvp/TestController190.java"
java -cp "$tmp/bin" ru.reader.rsvp.TestController190
