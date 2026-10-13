package android.content;
public class Context {
  public static final String AUDIO_SERVICE="audio";
  public Object getSystemService(String n){return new android.media.AudioManager();}
}
