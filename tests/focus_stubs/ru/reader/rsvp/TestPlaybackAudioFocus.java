package ru.reader.rsvp;
import android.content.Context;
import android.media.AudioManager;
public class TestPlaybackAudioFocus {
  public static void main(String[] args) {
    AudioManager manager = new AudioManager();
    Context context=new Context(){@Override public Object getSystemService(String n){return manager;}};
    int[] stops={0};
    PlaybackAudioFocus focus=new PlaybackAudioFocus(context,()->stops[0]++);
    if(!focus.acquire()||manager.requests!=1)throw new AssertionError("Acquire failed");
    if(!focus.acquire()||manager.requests!=1)throw new AssertionError("Duplicate focus request");
    manager.dispatch(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
    if(stops[0]!=1)throw new AssertionError("External app did not pause audio");
    // GAIN must not auto resume audio.
    manager.dispatch(AudioManager.AUDIOFOCUS_GAIN);
    if(stops[0]!=1)throw new AssertionError("Unexpected automatic restart");
    if(!focus.acquire()||manager.requests!=2)throw new AssertionError("Manual restart failed");
    focus.release();
    if(manager.abandons!=1)throw new AssertionError("Audio focus not abandoned");
    System.out.println("PASS: audio focus loss pauses, gain does not automatically resume, manual Play acquires focus");
  }
}
