package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.KeyEvent;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;

/**
 * Independent native MP3 playlist. It neither imports nor invokes WebView / JS / MRT.
 * Only ready local files enter ExoPlayer; loading a new file never starts playback.
 * All binder calls are expected on Android main thread.
 */
public final class MrtPlaybackService extends Service {
    public interface Listener {
        void onTrackChanged(int startWord, int endWord, String key, boolean completedPrevious);
        void onPlaybackStateChanged();
    }
    public static final String ACTION_TOGGLE = "ru.reader.rsvp.mrt.TOGGLE";
    public static final String ACTION_STOP = "ru.reader.rsvp.mrt.STOP";
    private static final String CHANNEL = "mrt_player";
    private static final int NOTIFY = 1282;
    private static final class Track {
        String key;
        int start, end;
        File file;
        Track(String key, int start, int end, File file) { this.key=key;this.start=start;this.end=end;this.file=file; }
    }
    public final class LocalBinder extends Binder {
        public MrtPlaybackService getService() { return MrtPlaybackService.this; }
    }
    private final IBinder binder = new LocalBinder();
    private final ArrayList<Track> tracks = new ArrayList<>();
    private ExoPlayer player;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private PlaybackAudioFocus focus;
    private MediaSession mediaSession;
    private boolean requestedPlay;
    private boolean autoContinue = true;
    private String activeBookId = "";
    private boolean notificationShowing;
    private int lastIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver exclusiveSource = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioModeCoordinator.STOP_MRT.equals(i.getAction())) {
                stopAudio();
                clearQueue();
                stopSelf();
            }
        }
    };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) pause();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        player.setAudioAttributes(new androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH).build(), false);
        player.addListener(new Player.Listener() {
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                int index = player.getCurrentMediaItemIndex();
                boolean completed = lastIndex >= 0 && index != lastIndex && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO;
                lastIndex = index;
                if (completed && !autoContinue) {
                    requestedPlay = false;
                    player.pause();
                }
                if (index >= 0 && index < tracks.size()) {
                    Track track=tracks.get(index);
                    for (Listener listener:new ArrayList<>(listeners)) listener.onTrackChanged(track.start, track.end, track.key, completed);
                }
                notifyState();
            }
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    // Preserve request to play next only when automatic continuation is enabled.
                    if (!autoContinue) requestedPlay = false;
                    if (notificationShowing && !requestedPlay) hideNotification();
                }
                notifyState();
            }
            @Override public void onIsPlayingChanged(boolean isPlaying) { updateSession(); updateNotification(); notifyState(); }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                requestedPlay = false;
                player.pause();
                notifyState();
            }
        });
        focus = new PlaybackAudioFocus(this, this::pause);
        mediaSession = new MediaSession(this, "ChitalkaRSVP-MRT-MP3");
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSession.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stopAudio(); }
            @Override public void onSkipToNext() { next(); }
            @Override public void onSkipToPrevious() { previous(); }
            @Override public boolean onMediaButtonEvent(Intent intent) {
                KeyEvent event = intent == null ? null : intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (event != null && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    switch (event.getKeyCode()) {
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE: if (isPlaying()) pause(); else play(); return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY: play(); return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE: pause(); return true;
                        case KeyEvent.KEYCODE_MEDIA_STOP: stopAudio(); return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT: next(); return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS: previous(); return true;
                    }
                }
                return super.onMediaButtonEvent(intent);
            }
        }, handler);
        mediaSession.setActive(true);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisy, filter);
        IntentFilter exclusiveFilter = new IntentFilter(AudioModeCoordinator.STOP_MRT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(exclusiveSource,exclusiveFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(exclusiveSource,exclusiveFilter);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager!=null) manager.createNotificationChannel(new NotificationChannel(CHANNEL,"MRT аудиоплеер",NotificationManager.IMPORTANCE_LOW));
        }
        updateSession();
    }
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null) {
            if (ACTION_TOGGLE.equals(intent.getAction())) { if (isPlaying()) pause(); else play(); }
            else if (ACTION_STOP.equals(intent.getAction())) stopAudio();
        }
        return START_STICKY;
    }
    public void addListener(Listener value) {
        if(value==null || listeners.contains(value))return;
        listeners.add(value);
        int index=trackIndex();
        if(index>=0 && index<tracks.size()) {
            Track current=tracks.get(index);
            value.onTrackChanged(current.start,current.end,current.key,false);
        }
        value.onPlaybackStateChanged();
    }
    public void removeListener(Listener value){listeners.remove(value);}
    /** Backwards-compatible registration; new callers should use addListener/removeListener. */
    public void setListener(Listener value) {listeners.clear();addListener(value);}
    public void prepareBook(String id) {
        String requested=id==null?"":id;
        if (!requested.equals(activeBookId)) {clearQueue(); activeBookId=requested;}
    }
    public void setAutoContinue(boolean enabled) { autoContinue=enabled; }
    public boolean isPlaying() { return player!=null && player.isPlaying(); }
    public boolean hasFiles() { return !tracks.isEmpty(); }
    public boolean isAtEnd() {return player!=null&&player.getPlaybackState()==Player.STATE_ENDED&&player.getMediaItemCount()>0;}
    public boolean playRequested() { return requestedPlay; }
    public int trackIndex() { return player==null?-1:player.getCurrentMediaItemIndex(); }
    public int trackCount() { return tracks.size(); }
    public int currentStartWord() {
        int i=trackIndex();return i<0||i>=tracks.size()?-1:tracks.get(i).start;
    }
    public int currentEndWord() {
        int i=trackIndex();return i<0||i>=tracks.size()?-1:tracks.get(i).end;
    }
    public long positionMs() { return player==null?0:Math.max(0,player.getCurrentPosition()); }
    public long durationMs() {
        if (player==null) return 0;
        long value=player.getDuration();
        return value==androidx.media3.common.C.TIME_UNSET||value<0?0:value;
    }
    public String currentKey() {
        int i=trackIndex();return i<0||i>=tracks.size()?"":tracks.get(i).key;
    }
    public void clearQueue() {
        requestedPlay=false;
        player.stop();
        player.clearMediaItems();
        tracks.clear();
        lastIndex=-1;
        focus.release();
        hideNotification();
        updateSession();
        notifyState();
    }
    /** Only called with fully assembled and validated on-disk MP3 files. */
    public void appendReady(String key,int start,int end,File file) {
        if (key==null || file==null || !file.isFile()) return;
        for (Track t:tracks) if (t.key.equals(key)) return;
        int oldCount = tracks.size();
        Track t=new Track(key,start,end,file);
        tracks.add(t);
        MediaItem item=new MediaItem.Builder().setMediaId(key)
                .setUri(android.net.Uri.fromFile(file)).build();
        player.addMediaItem(item);
        if (oldCount==0) {
            player.prepare();
        } else if (player.getPlaybackState()==Player.STATE_ENDED && requestedPlay) {
            // Playlist grew while waiting; do not replay the already completed file.
            player.seekTo(oldCount,0);
            player.prepare();
        }
        if (requestedPlay && !player.isPlaying()) player.play();
        updateSession();
        notifyState();
    }
    /** Explicit Play is the only user action which requests audible sound. */
    public void play() {
        if (!focus.acquire()) { requestedPlay=false;notifyState();return; }
        requestedPlay=true;
        if (player.getMediaItemCount()>0) {
            if (player.getPlaybackState()==Player.STATE_ENDED) {
                // At end of available cache wait for the downloader, NEVER replay last.
                if (player.getCurrentMediaItemIndex()>=player.getMediaItemCount()-1) {
                    updateNotification(); updateSession();notifyState();return;
                }
                player.seekToNextMediaItem();
            }
            player.prepare();
            player.play();
        }
        updateNotification();
        updateSession();
        notifyState();
    }
    public void pause() {
        requestedPlay=false;
        player.pause();
        focus.release();
        updateNotification();
        updateSession();
        notifyState();
    }
    public void stopAudio() {
        requestedPlay=false;
        player.pause();
        if (player.getMediaItemCount()>0) player.seekTo(0);
        focus.release();
        hideNotification();
        updateSession();
        notifyState();
    }
    public void seekWithin(long positionMs) {
        if (player.getMediaItemCount()>0) player.seekTo(Math.max(0,positionMs));
        notifyState();
    }
    public void rewind10() { seekWithin(Math.max(0,positionMs()-10000)); }
    public void forward10() { seekWithin(Math.min(durationMs()>0?durationMs():Long.MAX_VALUE,positionMs()+10000)); }
    public void next() {
        int idx=trackIndex();
        if(idx+1<tracks.size()) player.seekTo(idx+1,0);
        notifyState();
    }
    public void previous() {
        int idx=trackIndex();
        if(idx>0) player.seekTo(idx-1,0);
        else seekWithin(0);
        notifyState();
    }
    public void seekToTrack(String key,boolean playNow) {
        for(int i=0;i<tracks.size();i++) if(tracks.get(i).key.equals(key)) {
            player.seekTo(i,0);
            if (playNow) play(); else pause();
            notifyState();
            return;
        }
    }
    private void updateSession() {
        if(mediaSession==null||player==null)return;
        int state=player.isPlaying()?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|
                PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|
                PlaybackState.ACTION_SEEK_TO;
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state,positionMs(),player.isPlaying()?1f:0f).build());
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MrtOnlineActivity.class)
                .putExtra("book_uri",activeBookId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle=PendingIntent.getService(this,1,new Intent(this,MrtPlaybackService.class)
                .setAction(ACTION_TOGGLE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_reader).setColor(Color.rgb(153,97,68))
                .setContentTitle("Читалка RSVP · MRT")
                .setContentText(isPlaying()?"Воспроизведение MP3":"Пауза")
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(isPlaying())
                .addAction(isPlaying()?R.drawable.ic_pause:R.drawable.ic_play,isPlaying()?"Пауза":"Play",toggle)
                .build();
    }
    private void updateNotification() {
        if (player==null)return;
        if(isPlaying()) {
            try {startForeground(NOTIFY,notification());notificationShowing=true;}
            catch(Exception e) {android.util.Log.w("MRT-Player","Foreground playback notification",e);}
        } else if(notificationShowing) {
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFY,notification());
        }
    }
    private void hideNotification() {
        if(notificationShowing) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            notificationShowing=false;
        }
    }
    private void notifyState(){for(Listener listener:new ArrayList<>(listeners))listener.onPlaybackStateChanged();}
    @Override public void onDestroy() {
        try {unregisterReceiver(noisy);}catch(Exception ignored){}
        try {unregisterReceiver(exclusiveSource);}catch(Exception ignored){}
        focus.release();
        hideNotification();
        if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();mediaSession=null;}
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
