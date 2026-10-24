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
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.common.util.UnstableApi;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;

/**
 * Independent native MP3 playlist. Independent of any browser-based synthesis engine.
 * Only ready local files enter ExoPlayer; loading a new file never starts playback.
 * All binder calls are expected on Android main thread.
 */
@UnstableApi
public final class EdgePlaybackService extends Service {
    public interface Listener {
        void onTrackChanged(int startWord, int endWord, String key, boolean completedPrevious);
        void onPlaybackStateChanged();
    }
    public static final String ACTION_TOGGLE = "ru.reader.rsvp.edge.TOGGLE";
    public static final String ACTION_STOP = "ru.reader.rsvp.edge.STOP";
    private static final String CHANNEL = "edge_player";
    private static final int NOTIFY = 1382;
    private static final class Track {
        String key;
        int start, end;
        File file;
        Track(String key, int start, int end, File file) { this.key=key;this.start=start;this.end=end;this.file=file; }
    }
    public final class LocalBinder extends Binder {
        public EdgePlaybackService getService() { return EdgePlaybackService.this; }
    }
    private final IBinder binder = new LocalBinder();
    private final ArrayList<Track> tracks = new ArrayList<>();
    private ExoPlayer player;
    private EdgeLiveAudio.Factory liveDataSources;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private PlaybackAudioFocus focus;
    private MediaSession mediaSession;
    private boolean requestedPlay;
    private boolean autoContinue = true;
    private String activeBookId = "";
    private boolean notificationShowing;
    private String playbackError = "";
    private int lastIndex = -1;
    private float playbackSpeed = 1f;
    private long sleepAtMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable chteicoAudioTick=new Runnable(){@Override public void run(){
        if(isPlaying()){
            ChteicoStore.tickAudio(EdgePlaybackService.this,activeBookId,1);
            String book=activeBookId.startsWith("edge:")?activeBookId.substring(5):activeBookId;
            android.content.SharedPreferences t=getSharedPreferences("reader",0);
            t.edit().putLong("tts_listened_seconds:"+book,t.getLong("tts_listened_seconds:"+book,0)+1).apply();
        }
        handler.postDelayed(this,1000);
    }};
    private final Runnable sleepRunnable = () -> {
        sleepAtMs = 0L;
        getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",0L).apply();
        stopAudio();
        clearQueue();
        stopSelf();
    };
    private final BroadcastReceiver exclusiveSource = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioModeCoordinator.STOP_EDGE.equals(i.getAction())) {
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
        handler.postDelayed(chteicoAudioTick,1000);
        sleepAtMs=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0L);
        if(sleepAtMs>System.currentTimeMillis())handler.postDelayed(sleepRunnable,sleepAtMs-System.currentTimeMillis());
        else sleepAtMs=0L;
        playbackSpeed=getSharedPreferences("reader",0).getFloat("edge_playback_speed",1f);
        playbackSpeed=Math.max(0.5f,Math.min(3f,playbackSpeed));
        liveDataSources=new EdgeLiveAudio.Factory(this);
        player = new ExoPlayer.Builder(this,new DefaultMediaSourceFactory(liveDataSources))
            .setLoadControl(new DefaultLoadControl.Builder()
                .setBufferDurationsMs(500,15000,250,500).build()).build();
        player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
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
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if(isPlaying)android.util.Log.i("EdgeTtsLatency","rendering MP3; track="+currentKey());
                updateSession(); updateNotification(); notifyState();
            }
            @Override public void onAudioSessionIdChanged(int audioSessionId) { AudioEqualizer.attach(EdgePlaybackService.this,audioSessionId); }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error) {
                requestedPlay = false;
                playbackError = "Android не смог воспроизвести аудиофайл: "
                        + error.getErrorCodeName();
                player.pause();
                notifyState();
            }
        });
        focus = new PlaybackAudioFocus(this, this::pause);
        mediaSession = new MediaSession(this, "ChitalkaRSVP-Edge-MP3");
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
        IntentFilter exclusiveFilter = new IntentFilter(AudioModeCoordinator.STOP_EDGE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(exclusiveSource,exclusiveFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(exclusiveSource,exclusiveFilter);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if (manager!=null) manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Edge аудиоплеер",NotificationManager.IMPORTANCE_LOW));
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
    public String playbackError() { return playbackError; }
    public float playbackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(float value) {
        playbackSpeed = Math.max(0.5f, Math.min(5f, value));
        getSharedPreferences("reader",0).edit().putFloat("edge_playback_speed",playbackSpeed).apply();
        if (player != null) player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
        updateSession();
        notifyState();
    }
    /** Refresh shared sleep deadline after switching from the Android TTS engine. */
    public void restoreSharedSleepTimer() {
        handler.removeCallbacks(sleepRunnable);
        sleepAtMs=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0L);
        if(sleepAtMs>System.currentTimeMillis())
            handler.postDelayed(sleepRunnable,sleepAtMs-System.currentTimeMillis());
        else sleepAtMs=0L;
    }
    public void setSleepMinutes(int minutes) {
        handler.removeCallbacks(sleepRunnable);
        if (minutes <= 0) {
            sleepAtMs = 0L;
            getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",0L).apply();
            notifyState();return;
        }
        sleepAtMs = System.currentTimeMillis() + minutes * 60_000L;
        getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",sleepAtMs).apply();
        handler.postDelayed(sleepRunnable, minutes * 60_000L);
        notifyState();
    }
    public long sleepRemainingMs() {
        return sleepAtMs <= 0L ? 0L : Math.max(0L, sleepAtMs - System.currentTimeMillis());
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
        playbackError="";
        // Preserve the shared sleep deadline while changing book or voice.
        player.stop();
        player.clearMediaItems();
        tracks.clear();
        if(liveDataSources!=null)liveDataSources.clear();
        lastIndex=-1;
        focus.release();
        hideNotification();
        updateSession();
        notifyState();
    }
    /** Seek to a different sentence. Keep an active sleep timer unchanged. */
    public void clearTracksForSelection() {
        requestedPlay=false;
        playbackError="";
        player.stop();player.clearMediaItems();tracks.clear();
        if(liveDataSources!=null)liveDataSources.clear();
        lastIndex=-1;
        focus.release();hideNotification();updateSession();notifyState();
    }
    /** Remove an invalid clip from the playlist without clearing other MP3 files.
     * A currently audible clip is protected against deletion; caller can retry later.
     */
    public boolean evictCachedTrack(String key) {
        if (key == null) return false;
        for (int i = 0; i < tracks.size(); i++) {
            if (!tracks.get(i).key.equals(key)) continue;
            if (i == player.getCurrentMediaItemIndex() && player.isPlaying()) return false;
            tracks.remove(i);
            player.removeMediaItem(i);
            lastIndex = player.getCurrentMediaItemIndex();
            playbackError = "";
            updateSession();
            notifyState();
            return true;
        }
        return true;
    }

    /** Start decoding a growing MP3 byte stream; no disk write or completed network
     * response is required. Background download owns the producer even after a seek.
     */
    public void appendLive(String key,int start,int end,EdgeLiveAudio.Buffer bytes) {
        if(key==null||bytes==null)return;
        for(Track t:tracks)if(t.key.equals(key))return;
        if(liveDataSources==null)return;
        liveDataSources.register(key,bytes);
        Track track=new Track(key,start,end,null);
        int insertion=0;
        while(insertion<tracks.size()&&tracks.get(insertion).start<start)insertion++;
        tracks.add(insertion,track);
        MediaItem media=new MediaItem.Builder().setMediaId(key)
            .setUri(android.net.Uri.parse("edge-live:///"+android.net.Uri.encode(key)))
            .setMimeType("audio/mpeg").build();
        player.addMediaItem(insertion,media);
        if(player.getPlaybackState()==Player.STATE_IDLE)player.prepare();
        if(requestedPlay)player.play();
        notifyState();
    }

    /** Only called with fully assembled encoded audio files.
     * Use word-order insertion so a manually redownloaded clip returns to its original
     * position rather than being appended behind later, already downloaded clips.
     */
    public void appendReady(String key,int start,int end,File file) {
        if (key==null || file==null || !file.isFile()) return;
        for (Track t:tracks) if (t.key.equals(key)) return;
        int oldCount = tracks.size();
        Track t=new Track(key,start,end,file);
        int insertion = 0;
        while (insertion < tracks.size() && tracks.get(insertion).start < start) insertion++;
        tracks.add(insertion,t);
        EdgeAudioFormat format=EdgeAudioFormat.forFilename(file.getName());
        playbackError="";
        MediaItem.Builder itemBuilder=new MediaItem.Builder().setMediaId(key)
                .setUri(android.net.Uri.fromFile(file));
        if(format!=null)itemBuilder.setMimeType(format.mime);
        // Clipping starts at zero, so every unmodified WordBoundary time stays valid.
        if(getSharedPreferences("reader",0).getBoolean("edge_trim_sentence_pause",true)){
            long clipEnd=EdgeTrailingSilence.clippedEnd(file);
            if(clipEnd>0){
                itemBuilder.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(0).setEndPositionMs(clipEnd).build());
            }
        }
        player.addMediaItem(insertion,itemBuilder.build());
        if (oldCount==0) {
            player.prepare();
        } else if (insertion==oldCount && player.getPlaybackState()==Player.STATE_ENDED && requestedPlay) {
            player.seekTo(oldCount,0);
            player.prepare();
        }
        if (requestedPlay && !player.isPlaying() && player.getPlaybackState()!=Player.STATE_ENDED) player.play();
        updateSession();
        notifyState();
    }
    /** Explicit Play is the only user action which requests audible sound. */
    public void play() {
        if (!focus.acquire()) { requestedPlay=false;notifyState();return; }
        requestedPlay=true;
        if(activeBookId!=null&&!activeBookId.isEmpty())ChteicoStore.openAudio(this,activeBookId);
        if (player.getMediaItemCount()>0) {
            if (player.getPlaybackState()==Player.STATE_ENDED) {
                // At end of available cache wait for the downloader, NEVER replay last.
                if (player.getCurrentMediaItemIndex()>=player.getMediaItemCount()-1) {
                    updateNotification(); updateSession();notifyState();return;
                }
                player.seekToNextMediaItem();
            }
            if (player.getPlaybackState()==Player.STATE_IDLE ||
                    player.getPlaybackState()==Player.STATE_ENDED) player.prepare();
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
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,EdgeOnlineActivity.class)
                .putExtra("book_uri",activeBookId.startsWith("edge:")?activeBookId.substring(5):activeBookId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle=PendingIntent.getService(this,1,new Intent(this,EdgePlaybackService.class)
                .setAction(ACTION_TOGGLE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL)
                .setSmallIcon(R.drawable.ic_reader).setColor(Color.rgb(153,97,68))
                .setContentTitle("ПРОЧиталка · Edge TTS")
                .setContentText(isPlaying()?"Воспроизведение MP3":"Пауза")
                .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(isPlaying())
                .addAction(isPlaying()?R.drawable.ic_pause:R.drawable.ic_play,isPlaying()?"Пауза":"Play",toggle)
                .build();
    }
    private void updateNotification() {
        if (player==null)return;
        if(isPlaying()) {
            try {startForeground(NOTIFY,notification());notificationShowing=true;}
            catch(Exception e) {android.util.Log.w("Edge-Player","Foreground playback notification",e);}
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
        handler.removeCallbacks(sleepRunnable);
        focus.release();
        hideNotification();
        if(mediaSession!=null){mediaSession.setActive(false);mediaSession.release();mediaSession=null;}
        if(player!=null){player.release();player=null;}
        if(liveDataSources!=null)liveDataSources.clear();
        AudioEqualizer.release();
        super.onDestroy();
    }
}
