package android.media;
public final class AudioAttributes {
    public static final int USAGE_MEDIA=1, CONTENT_TYPE_SPEECH=1;
    public static final class Builder {
        public Builder setUsage(int u){return this;}
        public Builder setContentType(int t){return this;}
        public AudioAttributes build(){return new AudioAttributes();}
    }
}
