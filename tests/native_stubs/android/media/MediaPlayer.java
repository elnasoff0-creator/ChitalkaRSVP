package android.media;
import java.util.ArrayList;
public final class MediaPlayer {
    public interface OnPreparedListener {void onPrepared(MediaPlayer p);}
    public interface OnCompletionListener {void onCompletion(MediaPlayer p);}
    public interface OnErrorListener {boolean onError(MediaPlayer p,int w,int e);}
    public static final ArrayList<MediaPlayer> ALL = new ArrayList<>();
    public boolean started, prepared, closed;
    public int startCalls, pauseCalls;
    public String path;
    private OnPreparedListener prepare;
    private OnCompletionListener finish;
    private OnErrorListener error;
    public MediaPlayer(){ ALL.add(this); }
    public void setAudioAttributes(AudioAttributes a){}
    public void setDataSource(String p){path=p;}
    public void setOnPreparedListener(OnPreparedListener l){prepare=l;}
    public void setOnCompletionListener(OnCompletionListener l){finish=l;}
    public void setOnErrorListener(OnErrorListener l){error=l;}
    public void prepareAsync(){prepared=true;prepare.onPrepared(this);}
    public void start(){if(closed)throw new IllegalStateException();started=true;startCalls++;}
    public void pause(){started=false;pauseCalls++;}
    public void release(){closed=true;}
    public void simulateCompletion(){finish.onCompletion(this);}
    public void simulateFailure(){error.onError(this, 12, 34);}
}
