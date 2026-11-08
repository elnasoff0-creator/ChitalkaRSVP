#!/bin/sh
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/android/content" "$tmp/android/media" "$tmp/android/os" "$tmp/ru/reader/rsvp"
cat > "$tmp/android/content/Context.java" <<'JAVA'
package android.content;
import android.media.AudioManager;
public class Context {
  public static final String AUDIO_SERVICE = "audio";
  public final AudioManager manager = new AudioManager();
  public Object getSystemService(String name) { return manager; }
}
JAVA
cat > "$tmp/android/media/AudioAttributes.java" <<'JAVA'
package android.media;
public class AudioAttributes {
 public static final int USAGE_MEDIA=1,CONTENT_TYPE_SPEECH=2;
 public static class Builder {
   public Builder setUsage(int x){return this;}
   public Builder setContentType(int x){return this;}
   public AudioAttributes build(){return new AudioAttributes();}
 }
}
JAVA
cat > "$tmp/android/media/AudioFocusRequest.java" <<'JAVA'
package android.media;
public class AudioFocusRequest {
 public AudioManager.OnAudioFocusChangeListener listener;
 public boolean willPause;
 public static class Builder {
   private final AudioFocusRequest request=new AudioFocusRequest();
   public Builder(int type){}
   public Builder setAudioAttributes(AudioAttributes a){return this;}
   public Builder setAcceptsDelayedFocusGain(boolean b){return this;}
   public Builder setWillPauseWhenDucked(boolean b){request.willPause=b;return this;}
   public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener l){request.listener=l;return this;}
   public AudioFocusRequest build(){return request;}
 }
}
JAVA
cat > "$tmp/android/media/AudioManager.java" <<'JAVA'
package android.media;
public class AudioManager {
 public static final int AUDIOFOCUS_GAIN=1,AUDIOFOCUS_LOSS=-1,AUDIOFOCUS_LOSS_TRANSIENT=-2,AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK=-3;
 public static final int AUDIOFOCUS_REQUEST_GRANTED=1,STREAM_MUSIC=3;
 public interface OnAudioFocusChangeListener { void onAudioFocusChange(int change); }
 public OnAudioFocusChangeListener listener;
 public AudioFocusRequest lastRequest;
 public int abandonCount;
 public int requestAudioFocus(AudioFocusRequest request){lastRequest=request;listener=request.listener;return AUDIOFOCUS_REQUEST_GRANTED;}
 public int requestAudioFocus(OnAudioFocusChangeListener l,int stream,int gain){listener=l;return AUDIOFOCUS_REQUEST_GRANTED;}
 public int abandonAudioFocusRequest(AudioFocusRequest request){abandonCount++;return AUDIOFOCUS_REQUEST_GRANTED;}
 public int abandonAudioFocus(OnAudioFocusChangeListener l){abandonCount++;return AUDIOFOCUS_REQUEST_GRANTED;}
 public void send(int event){if(listener!=null)listener.onAudioFocusChange(event);}
}
JAVA
cat > "$tmp/android/os/Build.java" <<'JAVA'
package android.os;
public class Build { public static class VERSION { public static int SDK_INT=35; } }
JAVA
cat > "$tmp/ru/reader/rsvp/TestAudioFocusPolicy.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
import android.media.AudioManager;
public class TestAudioFocusPolicy {
 static void yes(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
   Context c=new Context();int[] interruptions={0};
   PlaybackAudioFocus audiobook=new PlaybackAudioFocus(c,()->interruptions[0]++,true);
   yes(audiobook.acquire(),"audiobook focus acquired");
   yes(!c.manager.lastRequest.willPause,"duck-only alert must not pause audiobook");
   c.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
   yes(interruptions[0]==0,"brief alert must not trigger pause");
   yes(audiobook.acquire(),"notification must not clear existing focus");
   c.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
   yes(interruptions[0]==1,"call / competing audio transient must interrupt");
   audiobook.release();yes(c.manager.abandonCount==1,"release registered request after loss");
   yes(audiobook.acquire(),"can acquire again");
   c.manager.send(AudioManager.AUDIOFOCUS_LOSS);
   yes(interruptions[0]==2,"media takeover must interrupt");
   audiobook.release();yes(c.manager.abandonCount==2,"release after permanent loss");
   Context other=new Context();int[] blocked={0};
   PlaybackAudioFocus strict=new PlaybackAudioFocus(other,()->blocked[0]++);
   yes(strict.acquire(),"edge focus acquired");
   yes(other.manager.lastRequest.willPause,"other players retain speech duck policy");
   other.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
   yes(blocked[0]==1,"default edge/local focus remains unchanged");
   strict.release();yes(other.manager.abandonCount==1,"registered default request released");
   System.out.println("PASS: notification duck keeps audiobook running; calls/media pause; focus release and default behavior");
 }
}
JAVA
javac -encoding UTF-8 -d "$tmp/out" $(find "$tmp/android" -name '*.java') "$root/app/src/main/java/ru/reader/rsvp/PlaybackAudioFocus.java" "$tmp/ru/reader/rsvp/TestAudioFocusPolicy.java"
java -cp "$tmp/out" ru.reader.rsvp.TestAudioFocusPolicy
