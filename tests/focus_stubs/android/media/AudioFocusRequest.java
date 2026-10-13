package android.media;
public class AudioFocusRequest {
  public AudioManager.OnAudioFocusChangeListener listener;
  public static class Builder {
    private final AudioFocusRequest request=new AudioFocusRequest();
    public Builder(int n){}
    public Builder setAudioAttributes(AudioAttributes a){return this;}
    public Builder setAcceptsDelayedFocusGain(boolean b){return this;}
    public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener l){request.listener=l;return this;}
    public AudioFocusRequest build(){return request;}
  }
}
