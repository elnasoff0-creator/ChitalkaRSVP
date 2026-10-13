package android.media;
public class AudioManager {
  public static final int AUDIOFOCUS_GAIN=1, AUDIOFOCUS_LOSS=-1,
      AUDIOFOCUS_LOSS_TRANSIENT=-2, AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK=-3,
      AUDIOFOCUS_REQUEST_GRANTED=1, STREAM_MUSIC=3;
  public interface OnAudioFocusChangeListener { void onAudioFocusChange(int change); }
  public OnAudioFocusChangeListener callback;
  public int requests=0,abandons=0;
  public int requestAudioFocus(AudioFocusRequest r){callback=r.listener;requests++;return AUDIOFOCUS_REQUEST_GRANTED;}
  public int requestAudioFocus(OnAudioFocusChangeListener l,int stream,int duration){callback=l;requests++;return AUDIOFOCUS_REQUEST_GRANTED;}
  public int abandonAudioFocusRequest(AudioFocusRequest r){abandons++;return 1;}
  public int abandonAudioFocus(OnAudioFocusChangeListener l){abandons++;return 1;}
  public void dispatch(int change){if(callback!=null)callback.onAudioFocusChange(change);}
}
