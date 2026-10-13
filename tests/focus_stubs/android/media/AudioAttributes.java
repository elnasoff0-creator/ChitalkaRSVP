package android.media;
public class AudioAttributes {
  public static final int USAGE_MEDIA=1, CONTENT_TYPE_SPEECH=1;
  public static class Builder {
    public Builder setUsage(int n){return this;}
    public Builder setContentType(int n){return this;}
    public AudioAttributes build(){return new AudioAttributes();}
  }
}
