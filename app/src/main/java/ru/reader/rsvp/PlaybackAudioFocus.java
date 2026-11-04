package ru.reader.rsvp;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/** Audio focus is only about external sources; home/navigation never changes playback.
 * Call and ringing states are checked even if optional transient focus callbacks are missed. */
final class PlaybackAudioFocus {
    static final String AUTO_PAUSE_KEY="audio_auto_pause_external";
    interface Interruption { void onInterruption(boolean telephone); }
    private final AudioManager manager;
    private final Context context;
    private final Interruption interrupted;
    private final Runnable callFinished;
    private final AudioManager.OnAudioFocusChangeListener listener;
    private final Handler monitor=new Handler(Looper.getMainLooper());
    private AudioFocusRequest request;
    private boolean held,registered,callInterrupted;
    private final boolean keepPlayingOnDuck;
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(!registered)return;
        boolean call=inCall();
        if(call&&!callInterrupted){callInterrupted=true;interrupted.onInterruption(true);}
        else if(!call&&callInterrupted){callInterrupted=false;if(callFinished!=null)callFinished.run();}
        monitor.postDelayed(this,450L);
    }};

    PlaybackAudioFocus(Context context,Runnable interrupted){this(context,phone->interrupted.run(),null,false);}
    PlaybackAudioFocus(Context context,Runnable interrupted,boolean keepDuck){this(context,phone->interrupted.run(),null,keepDuck);}
    PlaybackAudioFocus(Context context,Interruption interrupted,Runnable callFinished,boolean keepDuck){
        this.context=context.getApplicationContext();this.keepPlayingOnDuck=keepDuck;
        this.manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        this.interrupted=interrupted;this.callFinished=callFinished;
        this.listener=change->{
            if(change==AudioManager.AUDIOFOCUS_GAIN){held=true;return;}
            if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)return; // short notifications
            if(change==AudioManager.AUDIOFOCUS_LOSS||change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
                held=false;
                if(inCall()){
                    if(!callInterrupted){callInterrupted=true;interrupted.onInterruption(true);}
                }else if(autoPause())interrupted.onInterruption(false);
            }
        };
    }
    private boolean autoPause(){return context.getSharedPreferences("reader",0).getBoolean(AUTO_PAUSE_KEY,true);}
    private boolean inCall(){
        if(manager==null)return false;
        int mode=manager.getMode();
        return mode==AudioManager.MODE_IN_CALL||mode==AudioManager.MODE_IN_COMMUNICATION||mode==AudioManager.MODE_RINGTONE;
    }
    boolean acquire(){
        if(manager==null)return false;
        if(held)return true;
        int result;
        if(Build.VERSION.SDK_INT>=26){
            if(request==null){
                AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
                request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs).setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(!keepPlayingOnDuck).setOnAudioFocusChangeListener(listener).build();
            }
            result=manager.requestAudioFocus(request);
        }else result=manager.requestAudioFocus(listener,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        held=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if(held){registered=true;monitor.removeCallbacks(poll);monitor.post(poll);}
        return held;
    }
    void release(){
        monitor.removeCallbacks(poll);callInterrupted=false;
        if(manager==null||!registered)return;
        held=false;registered=false;
        if(Build.VERSION.SDK_INT>=26&&request!=null)manager.abandonAudioFocusRequest(request);
        else manager.abandonAudioFocus(listener);
    }
}
