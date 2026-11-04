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
 public static final String AUDIO_SERVICE="audio";
 public final AudioManager manager=new AudioManager();
 public final SharedPreferences prefs=new SharedPreferences();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}
 public Object getSystemService(String name){return manager;}
}
JAVA
cat > "$tmp/android/content/SharedPreferences.java" <<'JAVA'
package android.content;
public class SharedPreferences {
 public boolean enabled=true;
 public boolean getBoolean(String key,boolean fallback){return enabled;}
}
JAVA
cat > "$tmp/android/media/AudioAttributes.java" <<'JAVA'
package android.media;
public class AudioAttributes {
 public static final int USAGE_MEDIA=1,CONTENT_TYPE_SPEECH=2;
 public static class Builder {
  public Builder setUsage(int usage){return this;}
  public Builder setContentType(int type){return this;}
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
  public Builder setAudioAttributes(AudioAttributes attrs){return this;}
  public Builder setAcceptsDelayedFocusGain(boolean value){return this;}
  public Builder setWillPauseWhenDucked(boolean value){request.willPause=value;return this;}
  public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener callback){request.listener=callback;return this;}
  public AudioFocusRequest build(){return request;}
 }
}
JAVA
cat > "$tmp/android/media/AudioManager.java" <<'JAVA'
package android.media;
public class AudioManager {
 public static final int AUDIOFOCUS_GAIN=1,AUDIOFOCUS_LOSS=-1,AUDIOFOCUS_LOSS_TRANSIENT=-2,AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK=-3;
 public static final int AUDIOFOCUS_REQUEST_GRANTED=1,STREAM_MUSIC=3;
 public static final int MODE_NORMAL=0,MODE_RINGTONE=1,MODE_IN_CALL=2,MODE_IN_COMMUNICATION=3;
 public interface OnAudioFocusChangeListener { void onAudioFocusChange(int state); }
 public OnAudioFocusChangeListener listener;
 public AudioFocusRequest request;
 public int mode=MODE_NORMAL;
 public int getMode(){return mode;}
 public int requestAudioFocus(AudioFocusRequest r){request=r;listener=r.listener;return AUDIOFOCUS_REQUEST_GRANTED;}
 public int requestAudioFocus(OnAudioFocusChangeListener l,int stream,int gain){listener=l;return AUDIOFOCUS_REQUEST_GRANTED;}
 public int abandonAudioFocusRequest(AudioFocusRequest r){return AUDIOFOCUS_REQUEST_GRANTED;}
 public int abandonAudioFocus(OnAudioFocusChangeListener l){return AUDIOFOCUS_REQUEST_GRANTED;}
 public void send(int state){listener.onAudioFocusChange(state);}
}
JAVA
cat > "$tmp/android/os/Build.java" <<'JAVA'
package android.os;
public class Build {public static class VERSION {public static int SDK_INT=35;}}
JAVA
cat > "$tmp/android/os/Looper.java" <<'JAVA'
package android.os;
public class Looper {public static Looper getMainLooper(){return new Looper();}}
JAVA
cat > "$tmp/android/os/Handler.java" <<'JAVA'
package android.os;
public class Handler {
 public Handler(Looper ignored){}
 public void removeCallbacks(Runnable callback){}
 public void post(Runnable callback){}
 public void postDelayed(Runnable callback,long delay){}
}
JAVA
cat > "$tmp/ru/reader/rsvp/TestAudioFocus129.java" <<'JAVA'
package ru.reader.rsvp;
import android.content.Context;
import android.media.AudioManager;
public final class TestAudioFocus129 {
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  Context ctx=new Context();int[] pause={0},calls={0};
  PlaybackAudioFocus policy=new PlaybackAudioFocus(ctx,telephone->{pause[0]++;if(telephone)calls[0]++;},()->{},true);
  check(policy.acquire(),"focus initially acquired");
  check(!ctx.manager.request.willPause,"notification should duck not pause");
  ctx.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
  check(pause[0]==0,"short notification cannot trigger auto-pause");
  ctx.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
  check(pause[0]==1&&calls[0]==0,"external media should pause by default");
  policy.release();ctx.prefs.enabled=false;check(policy.acquire(),"focus acquired after toggle");
  ctx.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
  check(pause[0]==1,"disabled auto-pause ignores competing media");
  policy.release();check(policy.acquire(),"focus re-acquired for phone call");
  ctx.manager.mode=AudioManager.MODE_RINGTONE;
  ctx.manager.send(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
  check(pause[0]==2&&calls[0]==1,"phone always pauses even when auto-pause disabled");
  policy.release();
  System.out.println("PASS audio focus: notifications ignored, media optional, telephone mandatory");
 }
}
JAVA
javac -encoding UTF-8 -d "$tmp/out" $(find "$tmp/android" -name '*.java') \
 "$root/app/src/main/java/ru/reader/rsvp/PlaybackAudioFocus.java" \
 "$tmp/ru/reader/rsvp/TestAudioFocus129.java"
java -cp "$tmp/out" ru.reader.rsvp.TestAudioFocus129
