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
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.common.util.UnstableApi;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.util.ArrayList;

/**
 * Media3 native progressive MP3 streaming with complete-file disk cache fallback.
 * No custom MediaCodec, AudioTrack or intermediate PCM pipeline.
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
        EdgeLiveBuffer live;
        Track(String key, int start, int end, File file) { this.key=key;this.start=start;this.end=end;this.file=file; }
    }
    public final class LocalBinder extends Binder {
        public EdgePlaybackService getService() { return EdgePlaybackService.this; }
    }
    private final IBinder binder = new LocalBinder();
    private final ArrayList<Track> tracks = new ArrayList<>();
    private ExoPlayer player;
    private final ArrayList<Listener> listeners = new ArrayList<>();
    private PlaybackAudioFocus focus;
    private MediaSession mediaSession;
    private android.graphics.Bitmap playbackCover;
    private String mediaTitle="Книга",mediaAuthor="",coverBookUri="";
    private void updateArtwork(String id){
        String book=id.startsWith("edge:")?id.substring(5):id;
        if(book.isEmpty()||book.equals(coverBookUri))return;
        coverBookUri=book;playbackCover=null;
        android.content.SharedPreferences reader=getSharedPreferences("reader",0);
        mediaTitle=reader.getString("book_title:"+book,"Книга");
        mediaAuthor=reader.getString("book_author:"+book,"");
        PlaybackArtwork.load(this,book,true,"",bitmap->{
            if(!book.equals(coverBookUri))return;
            playbackCover=bitmap;updateSession();updateNotification();
        });
    }
    private boolean requestedPlay;
    private long requestedPlayAtMs;
    private boolean autoContinue = true;
    private String activeBookId = "";
    private boolean notificationShowing;
    private String playbackError = "";
    private String armedSeekKey = "";
    private long armedSeekMs;

    private int lastIndex = -1;
    private float playbackSpeed = 1f;
    private long sleepAtMs;
    private int sleepChapterEndWord=-1;
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
        sleepChapterEndWord=getSharedPreferences("reader",0).getInt("edge_sleep_chapter_end",-1);
        sleepAtMs=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0L);
        if(sleepAtMs>System.currentTimeMillis())handler.postDelayed(sleepRunnable,sleepAtMs-System.currentTimeMillis());
        else sleepAtMs=0L;
        playbackSpeed=getSharedPreferences("reader",0).getFloat("edge_playback_speed",1f);
        playbackSpeed=Math.max(0.5f,Math.min(3f,playbackSpeed));
        player = new ExoPlayer.Builder(this)
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
                    if(sleepChapterEndWord>=0 && track.start>=sleepChapterEndWord){
                        sleepChapterEndWord=-1;getSharedPreferences("reader",0).edit().remove("edge_sleep_chapter_end").apply();
                        pause();return;
                    }
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
                if(isPlaying){
                    long now=android.os.SystemClock.elapsedRealtime();
                    if(requestedPlayAtMs>0){
                        android.util.Log.i("EdgeTtsLatency","first sound after Play="+
                            (now-requestedPlayAtMs)+"ms; track="+currentKey());
                        requestedPlayAtMs=0;
                    }
                }
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
        if (!requested.equals(activeBookId)) {clearQueue();activeBookId=requested;
            sleepChapterEndWord=-1;
            getSharedPreferences("reader",0).edit().remove("edge_sleep_chapter_end").apply();
            updateArtwork(requested);updateSession();}
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
        sleepChapterEndWord=getSharedPreferences("reader",0).getInt("edge_sleep_chapter_end",-1);
        sleepAtMs=getSharedPreferences("reader",0).getLong("audio_sleep_deadline",0L);
        if(sleepAtMs>System.currentTimeMillis())
            handler.postDelayed(sleepRunnable,sleepAtMs-System.currentTimeMillis());
        else sleepAtMs=0L;
    }
    /** Stops at the first TTS clip beginning at/after the following chapter boundary. */
    public void setSleepChapterEnd(int endWord){
        handler.removeCallbacks(sleepRunnable);sleepAtMs=0L;
        sleepChapterEndWord=endWord>0?endWord:-1;
        getSharedPreferences("reader",0).edit().putLong("audio_sleep_deadline",0L)
            .putInt("edge_sleep_chapter_end",sleepChapterEndWord).apply();notifyState();
    }
    public void setSleepMinutes(int minutes) {
        sleepChapterEndWord=-1;
        getSharedPreferences("reader",0).edit().remove("edge_sleep_chapter_end").apply();
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
        requestedPlay=false;requestedPlayAtMs=0;
        playbackError="";
        armedSeekKey="";armedSeekMs=0;
        // Preserve the shared sleep deadline while changing book or voice.
        player.stop();
        player.clearMediaItems();
        tracks.clear();
        lastIndex=-1;
        focus.release();
        hideNotification();
        updateSession();
        notifyState();
    }
    /** Seek to a different sentence. Keep an active sleep timer unchanged. */
    public void clearTracksForSelection() {
        requestedPlay=false;requestedPlayAtMs=0;
        playbackError="";
        armedSeekKey="";armedSeekMs=0;
        player.stop();player.clearMediaItems();tracks.clear();
        lastIndex=-1;
        focus.release();hideNotification();updateSession();notifyState();
    }
    /** Arm a user-selected sentence BEFORE inserting a MediaItem. An MP3 that
     * arrives later must never start audibly at the beginning of its group. */
    public void armSelectedPosition(String key,long ms) {
        armedSeekKey=key==null?"":key;
        armedSeekMs=Math.max(0,ms);
        applyArmedPosition();
    }
    private void applyArmedPosition() {
        if(armedSeekKey.isEmpty())return;
        for(int i=0;i<tracks.size();i++)if(tracks.get(i).key.equals(armedSeekKey)){
            player.seekTo(i,armedSeekMs);
            armedSeekKey="";armedSeekMs=0;
            break;
        }
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

    /** First MP3 bytes are read directly by Media3 while WebSocket is still
     * generating. Completed bytes are independently committed to disk cache.
     * The live URI has no filesystem dependency and is never persisted.
     */
    public void appendLive(String key,int start,int end,EdgeLiveBuffer stream){
        if(key==null||stream==null)return;
        for(Track t:tracks)if(t.key.equals(key))return;
        int oldCount=tracks.size();
        int insertion=0;
        while(insertion<tracks.size()&&tracks.get(insertion).start<start)insertion++;
        Track track=new Track(key,start,end,null);
        track.live=stream;
        tracks.add(insertion,track);
        MediaItem item=new MediaItem.Builder().setMediaId(key)
            .setUri("edge-live://speech/"+start).setMimeType("audio/mpeg").build();
        ProgressiveMediaSource source=new ProgressiveMediaSource.Factory(
            new EdgeLiveDataSource.Factory(stream)).createMediaSource(item);
        player.addMediaSource(insertion,source);
        applyArmedPosition();
        if(oldCount==0)player.prepare();
        else if(insertion==oldCount && player.getPlaybackState()==Player.STATE_ENDED && requestedPlay){
            player.seekTo(insertion,0);player.prepare();
        }
        if(requestedPlay && player.getPlaybackState()!=Player.STATE_ENDED)player.play();
        updateSession();notifyState();
    }
    /** Remove an aborted live item before another selection or an explicit retry. */
    public void evictFailedLive(String key){
        if(key==null)return;
        for(int i=0;i<tracks.size();i++){
            Track t=tracks.get(i);
            if(!key.equals(t.key)||t.live==null)return;
            t.live.fail(new java.io.IOException("Edge stream failed"));
            tracks.remove(i);
            player.removeMediaItem(i);
            lastIndex=player.getCurrentMediaItemIndex();
            notifyState();
            return;
        }
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
        // Perform user-selected seek before prepare()/play(), including when
        // the requested MP3 was downloaded only after the tap.
        applyArmedPosition();
        if (oldCount==0) {
            player.prepare();
        } else if (insertion==oldCount && player.getPlaybackState()==Player.STATE_ENDED && requestedPlay) {
            player.seekTo(oldCount,0);
            player.prepare();
        }
        if (requestedPlay && !player.isPlaying() && player.getPlaybackState()!=Player.STATE_ENDED &&
                trackIndex()>=0 && trackIndex()<tracks.size())player.play();
        updateSession();
        notifyState();
    }
    /** Explicit Play is the only user action which requests audible sound. */
    public void play() {
        if (!focus.acquire()) { requestedPlay=false;notifyState();return; }
        if(!requestedPlay)requestedPlayAtMs=android.os.SystemClock.elapsedRealtime();
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
        requestedPlay=false;requestedPlayAtMs=0;
        player.pause();
        focus.release();
        updateNotification();
        updateSession();
        notifyState();
    }
    public void stopAudio() {
        requestedPlay=false;requestedPlayAtMs=0;
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
        int state=isPlaying()?PlaybackState.STATE_PLAYING:
            requestedPlay?PlaybackState.STATE_BUFFERING:PlaybackState.STATE_PAUSED;
        long actions=PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE|
                PlaybackState.ACTION_STOP|PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|
                PlaybackState.ACTION_SEEK_TO;
        String book=activeBookId.startsWith("edge:")?activeBookId.substring(5):activeBookId;
        android.content.SharedPreferences p=getSharedPreferences("reader",0);
        int totalWords=p.getInt("book_words:"+book,0);
        int currentWord=p.getInt("tts_word:"+book,p.getInt("index",0));
        int rate=Math.max(100,TtsWpm.get(this));
        long duration=totalWords>0?(long)totalWords*60000L/rate:0L;
        long position=totalWords>0?(long)Math.min(totalWords,Math.max(0,currentWord))*60000L/rate:Math.max(0,positionMs());
        android.media.MediaMetadata.Builder meta=new android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE,mediaTitle)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST,mediaAuthor)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION,duration);
        if(playbackCover!=null)meta.putBitmap(android.media.MediaMetadata.METADATA_KEY_ART,playbackCover);
        mediaSession.setMetadata(meta.build());
        mediaSession.setPlaybackState(new PlaybackState.Builder().setActions(actions)
                .setState(state,position,isPlaying()?Math.max(0.5f,playbackSpeed):0f).build());
    }
    private Notification notification() {
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,ReaderActivity.class)
                .putExtra("reading_mode_override",3)
                .putExtra("book_uri",activeBookId.startsWith("edge:")?activeBookId.substring(5):activeBookId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggle=PendingIntent.getService(this,1,new Intent(this,EdgePlaybackService.class)
                .setAction(ACTION_TOGGLE),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.MediaStyle style=new Notification.MediaStyle().setShowActionsInCompactView(0);
        if(mediaSession!=null)style.setMediaSession(mediaSession.getSessionToken());
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?
                new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return builder.setSmallIcon(R.drawable.ic_reader).setColor(Color.rgb(50,110,85))
                .setContentTitle(mediaTitle).setContentText(mediaAuthor.isEmpty()?"Озвучка":mediaAuthor)
                .setLargeIcon(playbackCover).setContentIntent(open)
                .setOnlyAlertOnce(true).setOngoing(requestedPlay)
                .addAction(requestedPlay?R.drawable.ic_pause:R.drawable.ic_play,
                    requestedPlay?"Пауза":"Play",toggle).setStyle(style).build();
    }
    private void updateNotification() {
        if (player==null)return;
        if(requestedPlay) {
            // Keep synthesis alive after the user backgrounds the app before the
            // first MP3 file arrives. This also exposes a buffering Pause button.
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
        AudioEqualizer.release();
        super.onDestroy();
    }
}
