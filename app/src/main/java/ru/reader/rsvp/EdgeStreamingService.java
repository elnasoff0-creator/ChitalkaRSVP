package ru.reader.rsvp;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;


import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded Quickify-inspired prefetch scheduler with live Media3 streaming.
 * Up to six nearby candidates are cache-checked; the shared priority queue
 * leases at most four reusable WebSockets with at most two speculative jobs.
 */
public final class EdgeStreamingService extends Service {
    public interface Observer {void onChanged();}
    public static final String ACTION_BEGIN="ru.reader.rsvp.edge.download.BEGIN";
    public static final String EXTRA_FORCE_EDGE="force_edge_user_choice";
    public static final String ACTION_PREFETCH="ru.reader.rsvp.edge.download.PREFETCH";
    public static final String ACTION_RESUME_FROM_BACKUP="ru.reader.rsvp.edge.RESUME_BACKUP";
    public static final String EXTRA_URI="book_uri";
    public static final String EXTRA_ANCHOR="anchor_word";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    // Speech-only, bounded verification never blocks Media3, the active synthesis lane or disk IO.
    private final ExecutorService dictionaryScan=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EdgeDictionaryRefresh");t.setPriority(Thread.MIN_PRIORITY);return t;
    });
    private final java.util.concurrent.atomic.AtomicInteger dictionaryGeneration=new java.util.concurrent.atomic.AtomicInteger();
    private boolean previousChaptersPending;
    private volatile boolean interactiveListening;
    private final BroadcastReceiver dictionaryReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(EdgeDictionaryStore.ACTION_CHANGED.equals(intent.getAction())){
                main.post(()->refreshSettings());
            }
        }
    };
    // SAF chapter publication must never hold the active speech/cache IO queue.
    private final ExecutorService clipRestore=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EdgeClipRestore");t.setPriority(Thread.NORM_PRIORITY);return t;
    });
    private final ExecutorService chapterWriter=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"EdgeChapterPublisher");t.setPriority(Thread.MIN_PRIORITY);return t;
    });
    // MP3 decode must never hold the queue responsible for producing playable files.
    private final ExecutorService tailAnalysis=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"EdgeCoverage");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Runnable saveIndex=this::persistNow;
    private final ArrayList<Integer> offsets=new ArrayList<>();
    private final ArrayList<SentenceChunks.Chunk> sentenceParts=new ArrayList<>();
    private SentenceIndex sentenceIndex=SentenceIndex.fromText("");
    private final TtsSentenceHistory sentenceHistory=new TtsSentenceHistory();
    private int cachedSavedPercent=0;
    private boolean calculatingCoverage=false;
    private long lastCoverageCheck=0;
    private final ArrayList<Clip> archive=new ArrayList<>();
    // The complete, sentence-indexed catalogue is independent of which MP3 has been cached.
    private final Map<Integer,Clip> byStart=new HashMap<>();
    /** Only the currently needed speech fragments carry expensive offset maps. */
    private final LinkedHashMap<Clip,EdgeSpeechTextTransform.Result> preparedSpeech=
        new LinkedHashMap<Clip,EdgeSpeechTextTransform.Result>(20,0.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<Clip,EdgeSpeechTextTransform.Result> e){
                return size()>20;
            }
        };
    private EdgeSpeechTextTransform.Options speechOptions;
    private String speechRevision="";
    private long restoreStartedAt=0L;
    private long trackReadyLoggedAt=-1L;
    private static final long IDLE_REUSE_MS=120000L;
    private String lastTransitionWaitKey="";
    private long lastTransitionWaitAtMs;
    private long lastTransitionStallLoggedAtMs;
    private void invalidateSpeech(){
        speechOptions=null;speechRevision="";preparedSpeech.clear();
    }
    private String revision(){
        if(speechOptions==null){
            speechOptions=EdgeDictionaryStore.get(this).options();
            speechRevision=speechOptions.fingerprint()+"|"+voice()+"|"+rate();
        }
        return speechRevision;
    }
    private void prepareClip(Clip clip){
        if(clip==null)return;
        String revision=revision();
        if(revision.equals(clip.revision)){
            // A whole-book download may commit audio after this fragment was
            // prepared. Probe only on demand, and not on every 180-ms UI tick.
            long now=SystemClock.elapsedRealtime();
            if(!clip.ignored && directory!=null && !clip.key.isEmpty() &&
                    !clip.ready() && now-clip.lastDiskProbe>1200L){
                clip.lastDiskProbe=now;
                File file=new File(directory,clip.key+".mp3");
                if(EdgeAudioManager.ready(file)){
                    clip.file=file;clip.bytes=file.length();firstTrackReady(clip,"cache");
                }
            }
            return;
        }
        long started=SystemClock.elapsedRealtime();
        EdgeSpeechTextTransform.Result speech=speechOptions.apply(clip.text);
        String newKey=speech.cacheKey(voice(),rate());
        if(!newKey.equals(clip.key)){
            clip.file=null;clip.bytes=0;clip.failures=0;
            clip.awaitingUserRetry=false;clip.retryAt=0;
        }
        clip.key=newKey;
        clip.lastDiskProbe=SystemClock.elapsedRealtime();
        clip.revision=revision;
        clip.ignored=speech.skipSynthesis();
        preparedSpeech.put(clip,speech);
        if(!clip.ignored && directory!=null && !clip.ready()){
            File audio=new File(directory,newKey+".mp3");
            if(EdgeAudioManager.ready(audio)){
                clip.file=audio;clip.bytes=audio.length();pendingStarts.remove(clip.start);
                firstTrackReady(clip,"cache");
            }
        }
        ReaderDiagnosticLog.event("READER_STARTUP","clip_prepare_ms="+
            (SystemClock.elapsedRealtime()-started)+" start="+clip.start);
        EdgeStartupTrace.stage("CLIP_PREPARED","duration_ms="+
            (SystemClock.elapsedRealtime()-started)+" cached="+clip.ready());
    }
    private EdgeSpeechTextTransform.Result speechFor(Clip clip){
        prepareClip(clip);
        EdgeSpeechTextTransform.Result speech=preparedSpeech.get(clip);
        if(speech==null){speech=speechOptions.apply(clip.text);preparedSpeech.put(clip,speech);}
        return speech;
    }
    private void firstTrackReady(Clip clip,String source){
        if(clip==null||trackReadyLoggedAt>=0||!active && !idlePrefetchRequested)return;
        if(clip.start!=anchorWord && !(clip.start<=anchorWord && clip.end>anchorWord))return;
        trackReadyLoggedAt=SystemClock.elapsedRealtime();
        ReaderDiagnosticLog.event("READER_STARTUP","first_track_ready source="+source+
            " start="+clip.start+" since_restore_ms="+
            (restoreStartedAt<=0?0:trackReadyLoggedAt-restoreStartedAt));
        EdgeStartupTrace.stage("TRACK_READY","source="+source);
    }
    /** Playing clip and a bounded, cache-first window of nearby candidates. */
    private final LinkedHashMap<String,LiveJob> liveJobs=new LinkedHashMap<>();
    private int prefetchForStart=-1,prefetchIssued;

    private static final class LiveJob {
        final Clip clip;
        final File folder;
        final String fileId,bookId;
        EdgeSpeechTextTransform.Result speech;
        volatile boolean networkDone;
        volatile boolean firstAudioNotified;
        boolean attached;
        EdgeTtsClient request;
        EdgeLiveBuffer buffer;
        volatile List<EdgeWordTimings.Word> timings=Collections.emptyList();
        LiveJob(Clip clip,File folder,String bookId){
            this.clip=clip;this.folder=folder;this.bookId=bookId;
            fileId=new File(folder,clip.key+".mp3").getAbsolutePath();
        }
    }

    // Previous foreground download requests are not resumed during listening.
    private final LinkedHashSet<Integer> pendingStarts=new LinkedHashSet<>();
    private int priorityStart=-1;
    private int selectedSeekWord=-1;
    // A selected sentence may start *inside* a cached multi-sentence MP3.
    // Preserve its word anchor across Media3's asynchronous track callback,
    // which otherwise resets anchorWord to the older clip's first word.
    private int replayStartWord=-1;
    private String replayTrackKey="";
    // Explicit UI/MediaSession sentence selections are distinct from progress
    // callbacks. ReaderActivity can follow them without accepting older tracks.
    private long selectionSerial=0L;
    private int lastSelectedWord=-1;
    public long selectionSerial(){return selectionSerial;}
    public int lastSelectedWord(){return lastSelectedWord;}
    // A tap may arrive before the asynchronous chapter index finishes loading.
    private int deferredSelectedWord=-1;
    private final ArrayList<Clip> selectedOverrides=new ArrayList<>();

    private final ArrayList<Observer> observers=new ArrayList<>();
    private String bookUri="",title="",bookText="",error="";
    private String pendingBookUri="";private int pendingBookWord=0;
    // Set before failing a live buffer so Media3's generic I/O error cannot
    // accidentally surface the same HTTP 429 as a user-visible Toast.
    private volatile boolean lastHttp429;
    private boolean backupTtsActivated;
    private boolean resumeAfterBackupPending;
    private void startBackupTtsForFailedClip(Clip failed, boolean deterministicFailure){
        if(backupTtsActivated||!active||failed==null||player==null||!player.playRequested())return;
        android.content.SharedPreferences pref=getSharedPreferences("reader",0);
        if(!pref.getBoolean("edge_backup_tts_enabled",false)||bookUri==null||bookUri.isEmpty())return;
        int playingStart=player.currentStartWord();
        if(playingStart>=0&&playingStart!=failed.start)return;
        // On the first stream Media3 may not yet have a current track.
        if(playingStart<0&&(anchorWord<failed.start||anchorWord>=failed.end))return;
        if(failed.failures<2&&!deterministicFailure){
            ReaderDiagnosticLog.event("EDGE","fallback_deferred retries="+failed.failures);
            return;
        }
        backupTtsActivated=true;
        final int word=Math.max(0,failed.start);
        String fallbackEngine=pref.getString("edge_backup_tts_engine","");
        ReaderDiagnosticLog.event("EDGE","FALLBACK_ACTIVATED start="+word);
        ReaderDiagnosticLog.event("TTS_SWITCH","TTS_FALLBACK_ACTIVATED after_edge_failure=true");
        EdgeStartupTrace.cancelled("edge_failure_fallback");
        // Pause Edge before handing off the same sentence to Android TTS.
        player.pause();
        try{
            startService(new Intent(this,AudioPlaybackService.class)
                .setAction(AudioPlaybackService.TTS).putExtra("bookUri",bookUri)
                .putExtra("word_index",word).putExtra("auto_on_selection",true)
                .putExtra("fallback_engine_override",fallbackEngine));
        }catch(Exception ex){
            backupTtsActivated=false;
            error="Не удалось запустить резервный TTS";
            changed();
        }
    }

    private BookChapterIndex chapters=BookChapterIndex.empty();
    // Only requested clips are ever recovered from SAF. One attempt per key/revision.
    private final java.util.HashSet<String> lazyAttempted=new java.util.HashSet<>();
    private final java.util.HashSet<String> lazyPending=new java.util.HashSet<>();
    private final java.util.HashSet<String> lazyForeground=new java.util.HashSet<>();
    private final java.util.HashMap<String,java.util.concurrent.atomic.AtomicBoolean>
        lazyCommitPermits=new java.util.HashMap<>();
    private File directory;
    private boolean bookReady,active,destroyed,playerBound,idlePrefetchRequested;
    private int anchorWord;
    private EdgePlaybackService player;
    // Initialized after active/player: Java rejects field reads of later declarations
    // inside a field-initializer lambda (illegal forward reference).
    private final Runnable idleExpiry=()->{
        if(!active && (player==null||!player.playRequested()))stopSelf();
    };
    private final Runnable refreshReady=new Runnable(){@Override public void run(){
        if(destroyed)return;
        if(bookReady&&directory!=null&&active){
            recoverEndedTransition();
            pump();
        }
        if(active&&bookReady&&player!=null&&player.isPlaying()&&offsets.size()>0)
            UnifiedReadingProgress.save(EdgeStreamingService.this,bookUri,
                currentSpeechWord(),offsets.size(),currentSharedChapter(currentSpeechWord()),true);
        main.postDelayed(this,2500);
    }};
    private final BroadcastReceiver switchReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            // Switching to local TTS stops sound, not previously requested downloads.
            if(AudioModeCoordinator.STOP_EDGE.equals(i.getAction()))changed();
        }
    };

    public static final class Clip {
        public final int start,end;
        public final String text;
        public String key;
        String revision="";
        boolean ignored;
        public File file;
        public long bytes;
        long lastDiskProbe=0L;
        public int chunks,failures;
        public long retryAt;
        public boolean played;
        public boolean awaitingUserRetry;
        Clip(int start,int end,String text,String key){this.start=start;this.end=end;this.text=text;this.key=key;}
        public boolean ready(){return file!=null&&file.isFile()&&file.length()>1500;}
        Clip copy(){Clip c=new Clip(start,end,text,key);c.revision=revision;c.ignored=ignored;c.file=file;c.bytes=bytes;c.chunks=chunks;c.played=played;c.failures=failures;c.awaitingUserRetry=awaitingUserRetry;return c;}
    }
    public final class LocalBinder extends Binder { public EdgeStreamingService getService(){return EdgeStreamingService.this;} }
    private final IBinder binder=new LocalBinder();
    private final EdgePlaybackService.Listener listener=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean completedPrevious){
            main.post(()->{if(!bookReady||player==null||!key.equals(player.currentKey()))return;
                // A delayed transition from the old playlist must not undo a tap.
                if(selectedSeekWord>=0 && clipAt(selectedSeekWord)!=null &&
                        start!=clipAt(selectedSeekWord).start)return;
                if(completedPrevious)for(Clip c:archive)if(c.end==start)c.played=true;
                selectedOverrides.removeIf(c->start>=c.end);
                if(key.equals(replayTrackKey) && replayStartWord>=start && replayStartWord<end){
                    anchorWord=replayStartWord; // The clip's earlier sentences are not the selection.
                }else{
                    replayStartWord=-1;replayTrackKey="";
                    anchorWord=start;
                }
                UnifiedReadingProgress.save(EdgeStreamingService.this,bookUri,anchorWord,offsets.size(),
                    currentSharedChapter(anchorWord),true);
                persist();pump();
            });
        }
        /** A playlist without an attached next item reaches STATE_ENDED.
         * An idempotent coordinator handles both callback and lost-callback recovery. */
        @Override public void onTrackFinished(int start,int end,String key){
            main.post(()->advanceAfterTrack(start,end,key,"media3"));
        }
        @Override public void onTrackPlaybackError(int start,int end,String key,
                boolean cachedFile,String errorCode){
            main.post(()->recoverFailedTrack(start,end,key,cachedFile,errorCode));
        }
        @Override public void onPlaybackStateChanged(){main.post(()->{
            interactiveListening=active && player!=null && player.playRequested();
            EdgeSpeechQueue.setInteractivePlayback(interactiveListening);
            if(bookReady){
                pump();
                // Past chapters cannot consume CPU while interactive listening is active.
                if(previousChaptersPending && (player==null||!player.playRequested()))
                    scheduleDictionaryScan(true);
            }
        });}
    };
    /** Move the cursor only once, after the matching audible track has ended.
     * Duplicate notifications and user seeks must not skip entire sentences. */
    private void advanceAfterTrack(int start,int end,String key,String source){
        if(destroyed||!active||!bookReady||player==null||!player.playRequested()||
                start<0||end<=start)return;
        if(anchorWord>=end){
            // The first callback has already advanced the cursor; re-pump only.
            if(player.isAtEnd())pump();
            return;
        }
        if(anchorWord<start)return; // A manual selection superseded the event.
        // A late completion from an older playlist/voice may have identical word
        // bounds. Only the exact current media key is authorised to advance.
        if(!key.equals(player.currentKey()))return;
        Clip finished=clipAt(start);
        if(finished!=null&&finished.end==end)finished.played=true;
        ReaderDiagnosticLog.event("PLAYER","CLIP_FINISHED start="+start+" end="+end+
            " source="+source);
        EdgeTtsDiagnostics.trackEnded(key);
        selectedOverrides.removeIf(c->end>=c.end);
        selectedSeekWord=-1;replayStartWord=-1;replayTrackKey="";
        anchorWord=end;
        lastTransitionWaitKey="";
        persist();
        if(end>=offsets.size()||player.shouldStopAfterWord(end)){
            player.pause();changed();return;
        }
        ReaderDiagnosticLog.event("PLAYER","NEXT_CLIP_SELECTED word="+end);
        pump();
    }
    /** Media3 can finish before the next cached/live source is attached. The
     * normal callback is immediate; this low-frequency safety net only covers
     * an actual ENDED state with Play still requested. */
    private void recoverEndedTransition(){
        if(!active||!bookReady||player==null||!player.playRequested()||!player.isAtEnd())return;
        int start=player.currentStartWord(),end=player.currentEndWord();
        if(start<0||end<=start)return;
        long now=SystemClock.elapsedRealtime();
        if(now-lastTransitionStallLoggedAtMs>=8000L){
            lastTransitionStallLoggedAtMs=now;
            ReaderDiagnosticLog.event("PLAYER","TRANSITION_STALLED at_end="+end);
        }
        if(anchorWord<end)advanceAfterTrack(start,end,player.currentKey(),"watchdog");
        else if(anchorWord==end)pump();
    }
    /** Recover one corrupt cached file, or wait for the active WebSocket's own
     * completion callback to retry a failed streaming source. */
    private void recoverFailedTrack(int start,int end,String key,boolean cachedFile,String errorCode){
        if(destroyed||!active||!bookReady||player==null||!player.playRequested())return;
        Clip clip=clipAt(start);
        if(clip==null||clip.end!=end||anchorWord<start||anchorWord>=end)return;
        ReaderDiagnosticLog.event("PLAYER","TRANSITION_STALLED playback_error="+errorCode+
            " start="+start+" cached="+cachedFile);
        if(!cachedFile){
            // A decoder or loader failure can happen independently from the
            // WebSocket callback. EdgeSpeechEngine.cancel() does not call
            // Callback.finished(), so explicitly release the job here.
            LiveJob live=liveFor(clip);
            if(live!=null){
                liveJobs.remove(live.fileId,live);
                if(live.buffer!=null)live.buffer.fail(new java.io.IOException("Edge playback source failed"));
                if(live.request!=null)live.request.cancel();
            }
            player.evictFailedLive(key);
            clip.failures++;
            if(clip.failures>1){
                clip.awaitingUserRetry=true;
                error="Edge TTS: ошибка декодирования или ожидания MP3";
                startBackupTtsForFailedClip(clip,true);
                if(!backupTtsActivated)player.pause();
                changed();
            }else{
                clip.awaitingUserRetry=false;
                clip.retryAt=SystemClock.elapsedRealtime()+750L;
                main.postDelayed(()->{
                    if(destroyed||!active||player==null||!player.playRequested()||
                        anchorWord<start||anchorWord>=end)return;
                    ReaderDiagnosticLog.event("PLAYER","TRANSITION_RECOVERED retry_stream="+start);
                    pump();
                },800L);
            }
            return;
        }
        if(clip.failures>=1){
            clip.awaitingUserRetry=true;
            error="Edge TTS: повреждённый MP3 не удалось восстановить";
            startBackupTtsForFailedClip(clip,true);
            if(!backupTtsActivated)player.pause();
            changed();return;
        }
        clip.failures++;
        if(!player.evictCachedTrack(key))return;
        final File failedFile=clip.file;
        clip.file=null;clip.bytes=0;
        // Block a disk probe from re-attaching this corrupt MP3 while the
        // background executor is still deleting/quarantining it.
        clip.awaitingUserRetry=true;
        clip.lastDiskProbe=Long.MAX_VALUE;
        io.execute(()->{
            if(failedFile!=null){
                failedFile.delete();
                EdgeWordTimings.sidecar(failedFile).delete();
            }
            main.post(()->{
                if(destroyed||!bookReady||anchorWord<start||anchorWord>=end)return;
                clip.lastDiskProbe=SystemClock.elapsedRealtime();
                clip.awaitingUserRetry=false;
                ReaderDiagnosticLog.event("PLAYER","TRANSITION_RECOVERED resynthesize="+start);
                pump();
            });
        });
    }
    private final ServiceConnection playbackConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            player=((EdgePlaybackService.LocalBinder)b).getService();
            player.addListener(listener);
            player.setResumeSelection(EdgeStreamingService.this::selectSentenceOnResume);
            player.setPauseSelection(EdgeStreamingService.this::capturePausedSentence);
            player.setSentenceNavigation(EdgeStreamingService.this::navigateSentence);
            if(!bookUri.isEmpty())player.prepareBook("edge:"+bookUri);
            if(resumeAfterBackupPending && active){
                resumeAfterBackupPending=false;
                player.play();
            }
            // Service and player can bind in either order. Warm exactly one clip,
            // only when TTS is active and the user has not pressed Play.
            warmCurrent();
            pump();
        }
        @Override public void onServiceDisconnected(ComponentName n){player=null;}
    };
    @Override public void onCreate(){super.onCreate();
        EdgeSettings.migrate(getSharedPreferences("reader",0));
        // v2 keeps only explicit Retry-After deadlines. Discard legacy synthetic
        // 30-minute exponential blocking from pre-v1.11.92 versions.
        EdgeSpeechQueue.restoreCooldown(getSharedPreferences("reader",0)
                .getLong("edge_v2_retry_after_epoch_ms",0));
        main.postDelayed(refreshReady,2500);
        playerBound=bindService(new Intent(this,EdgePlaybackService.class),playbackConnection,BIND_AUTO_CREATE);
        IntentFilter filter=new IntentFilter(AudioModeCoordinator.STOP_EDGE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(switchReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(switchReceiver,filter);
        IntentFilter dictionaryFilter=new IntentFilter(EdgeDictionaryStore.ACTION_CHANGED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(dictionaryReceiver,dictionaryFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(dictionaryReceiver,dictionaryFilter);
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public boolean onUnbind(Intent intent){
        // A mode switch unbinds ReaderActivity, but should not destroy the
        // prepared book index. The idle lease is capped to avoid memory leaks.
        if(!active)main.postDelayed(idleExpiry,IDLE_REUSE_MS);
        return super.onUnbind(intent);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && ACTION_RESUME_FROM_BACKUP.equals(intent.getAction())){
            // Local TTS has finished its current chunk. Return to Edge only at a
            // sentence boundary, preserving the exact next-word selection.
            String uri=intent.getStringExtra(EXTRA_URI);
            if(uri!=null&&!uri.isEmpty()){
                int next=Math.max(0,intent.getIntExtra(EXTRA_ANCHOR,0));
                main.removeCallbacks(idleExpiry);
                active=true;backupTtsActivated=false;
                // Queue the explicit playback request until the bound Media3 service exists.
                resumeAfterBackupPending=player==null;
                openBook(uri,next);
                if(player!=null)player.prepareBook("edge:"+uri);
                setAnchor(next,true);
                if(player!=null)player.play();
                pump();
                ReaderDiagnosticLog.event("EDGE","fallback_restored word="+next);
            }
        }else if(intent!=null && ACTION_BEGIN.equals(intent.getAction())){
            resumeAfterBackupPending=false;
            main.removeCallbacks(idleExpiry);
            if(!active){trackReadyLoggedAt=-1L;restoreStartedAt=SystemClock.elapsedRealtime();}
            idlePrefetchRequested=false;
            String uri=intent.getStringExtra(EXTRA_URI);
            if(uri!=null&&!uri.isEmpty())openBook(uri,intent.getIntExtra(EXTRA_ANCHOR,0));
            active=true;
            backupTtsActivated=false;
            if(intent.getBooleanExtra(EXTRA_FORCE_EDGE,false)){
                // Ignore late callbacks from the previous failed Edge attempt.
                cancelLiveExcept(null);liveJobs.clear();
                error="";lastHttp429=false;
                int selected=Math.max(0,intent.getIntExtra(EXTRA_ANCHOR,anchorWord));
                Clip current=bookReady?clipAt(selected):null;
                if(current!=null){current.failures=0;current.retryAt=0;
                    current.awaitingUserRetry=false;}
                // A reselected Edge mode must not replay the dead Media3 track.
                // setAnchor clears the previous queue and selects a full sentence.
                setAnchor(selected,true);
                ReaderDiagnosticLog.event("TTS_SWITCH","TTS_EDGE_ACTIVATED forced=true cooldown_ms="+
                    EdgeSpeechQueue.remainingMs());
            }else ReaderDiagnosticLog.event("TTS_SWITCH","TTS_EDGE_ACTIVATED forced=false");
            if(uri!=null&&!uri.isEmpty())DownloadsRepository.showEdge(this,uri);
            pump();
        }else if(intent!=null && ACTION_PREFETCH.equals(intent.getAction()) && !active){
            String uri=intent.getStringExtra(EXTRA_URI);
            if(uri!=null&&!uri.isEmpty()&&
                getSharedPreferences("reader",0).getBoolean("edge_warm_reader",true)){
                idlePrefetchRequested=true;
                openBook(uri,intent.getIntExtra(EXTRA_ANCHOR,0));
                warmCurrent();
            }
        }
        return START_NOT_STICKY;
    }
    private void changed(){for(Observer o:new ArrayList<>(observers))o.onChanged();}
    public void addObserver(Observer o){if(o!=null&&!observers.contains(o))observers.add(o);if(o!=null)o.onChanged();}
    public void removeObserver(Observer o){observers.remove(o);}
    public boolean isReady(){return bookReady;}
    public boolean isActive(){return active;}
    public String error(){return error;}
    public boolean lastRateLimit(){return lastHttp429;}
    public String bookUri(){return bookUri;}
    public String title(){return title==null||title.trim().isEmpty()?"Книга":title.trim();}
    public int anchor(){
        if(selectedSeekWord>=0)return selectedSeekWord;
        return player!=null&&player.currentStartWord()>=0?player.currentStartWord():anchorWord;
    }
    public int words(){return offsets.size();}
    public String fullText(){return bookText;}
    public BookChapterIndex chapterIndex(){return chapters;}
    public int charForWord(int word){return offsets.isEmpty()?0:offsets.get(Math.max(0,Math.min(word,offsets.size()-1)));}
    public int wordForChar(int at){return Math.max(0,Math.min(offsets.size()-1,SentenceChunks.lowerBound(offsets,at)));}
    /** Word-based coverage from files physically stored on disk. Never count temporary downloads. */
    public int savedPercent(){
        if(offsets.isEmpty()||directory==null)return 0;
        if(!calculatingCoverage && android.os.SystemClock.uptimeMillis()-lastCoverageCheck>5000){
            calculatingCoverage=true;
            final String uri=bookUri;
            final File folder=directory;
            final int total=offsets.size();
            // Disk coverage is independent of the active voice's dictionary rules.
            // Never run the full-book speech transformer on the listener thread.
            tailAnalysis.execute(()->{
                EdgeOfflineIndex.Stats stats=EdgeAudioManager.coverage(folder,0,total);
                final int percent=stats.percent();
                main.post(()->{
                    calculatingCoverage=false;
                    if(uri.equals(bookUri)){
                        cachedSavedPercent=percent;
                        lastCoverageCheck=android.os.SystemClock.uptimeMillis();
                        changed();
                    }
                });
            });
        }
        return cachedSavedPercent;
    }
    public int currentOrdinal(){int n=0;for(Clip c:archive)if(c.start<=anchor())n++;return n;}
    public int countReady(){int n=0;for(Clip c:archive)if(c.ready())n++;return n;}
    public int generatingStart(){
        for(LiveJob j:liveJobs.values())if(!j.networkDone)return j.clip.start;
        return -1;
    }
    public boolean isRedownloading(int start){
        for(LiveJob j:liveJobs.values())if(j.clip.start==start&&!j.networkDone)return true;
        return false;
    }
    public int progressChunks(){return 0;}
    public long progressBytes(){return 0;}
    public File folder(){return directory;}
    public ArrayList<Clip> snapshot(){ArrayList<Clip> list=new ArrayList<>();for(Clip c:archive)list.add(c.copy());return list;}
    public Clip currentClip(){
        String playing=player==null?"":player.currentKey();
        for(Clip override:selectedOverrides)
            if((override.key+":"+override.start).equals(playing))return override.copy();
        if(!playing.isEmpty())for(Clip c:archive)
            if((c.key+":"+c.start).equals(playing))return c.copy();
        // Never report an unrelated previously played MP3 as the selected sentence.
        Clip candidate=clipAt(anchorWord);
        return candidate==null?null:candidate.copy();
    }
    public String voice(){return getSharedPreferences("reader",0).getString("edge_voice","ru-RU-SvetlanaNeural");}
    public String rate(){return getSharedPreferences("reader",0).getString("edge_rate","+0%");}
    public void choose(String voice,String rate){
        SharedPreferences.Editor edit=getSharedPreferences("reader",0).edit();
        if(voice!=null)edit.putString("edge_voice",voice);
        if(rate!=null)edit.putString("edge_rate",rate);
        edit.apply();cancelLiveExcept(null);liveJobs.clear();
        selectedOverrides.clear();selectedSeekWord=-1;replayStartWord=-1;replayTrackKey="";
        if(player!=null)player.clearQueue();
        // Keep the lightweight index. Only requested fragments acquire new voice keys.
        invalidateSpeech();
        priorityStart=-1;
        pump();
    }
    public void openBook(String uri,int initialWord){
        if(uri==null||uri.isEmpty())return;
        if(player!=null&&player.playRequested()&&!uri.equals(bookUri)){
            // The current book keeps playing while another reader is opened.
            pendingBookUri=uri;pendingBookWord=Math.max(0,initialWord);return;
        }
        if(uri.equals(bookUri)){
            pendingBookUri="";
            ReaderDiagnosticLog.event("READER_STARTUP","edge_book_reuse ready="+bookReady);
            EdgeStartupTrace.stage("BOOK_REUSED","ready="+bookReady);
            // Do not overwrite an explicit selection with a stale activity bind.
            if(deferredSelectedWord<0 && selectedSeekWord<0 &&
                    (player==null||player.currentStartWord()<0))anchorWord=Math.max(0,initialWord);
            pump();return;
        }
        // Flush the previous book's pending index before switching its directory.
        main.removeCallbacks(saveIndex);persistNow();
        cancelLiveExcept(null);liveJobs.clear();bookUri=uri;bookReady=false;anchorWord=Math.max(0,initialWord);
        dictionaryGeneration.incrementAndGet();previousChaptersPending=false;
        invalidateSpeech();trackReadyLoggedAt=-1L;restoreStartedAt=SystemClock.elapsedRealtime();
        cachedSavedPercent=0;lastCoverageCheck=0;
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        lazyAttempted.clear();lazyPending.clear();lazyForeground.clear();
        for(java.util.concurrent.atomic.AtomicBoolean token:lazyCommitPermits.values())
            token.set(false);
        lazyCommitPermits.clear();
        lastTransitionWaitKey="";lastTransitionWaitAtMs=0L;
        lastTransitionStallLoggedAtMs=0L;
        selectedOverrides.clear();selectedSeekWord=-1;deferredSelectedWord=-1;
        replayStartWord=-1;replayTrackKey="";
        prefetchForStart=-1;prefetchIssued=0;
        offsets.clear();sentenceParts.clear();sentenceIndex=SentenceIndex.fromText("");sentenceHistory.bind(sentenceIndex);bookText="";chapters=BookChapterIndex.empty();directory=null;error="";
        if(player!=null)player.prepareBook("edge:"+uri);
        changed();
        final String requested=uri;
        io.execute(()->{
            try{
                long indexingStart=SystemClock.elapsedRealtime();
                boolean reusedBook=UnifiedBookSession.current(requested)!=null;
                UnifiedBookSession session=UnifiedBookSession.open(this,Uri.parse(requested));
                long sessionOpenMs=SystemClock.elapsedRealtime()-indexingStart;
                EdgeStartupTrace.stage("BOOK_OPENED","duration_ms="+sessionOpenMs+
                    " reused="+reusedBook);
                BookLoader.Book book=session.book;
                String raw=book.text==null?"":book.text;
                String name=book.title==null?"Книга":book.title;
                BookChapterIndex parsedChapters=book.chapters;
                ArrayList<Integer> positions=session.ttsWordPositions;
                // Persist grouping targets once per book: changing network latency
                // cannot invalidate previously saved audio. Books with legacy MP3
                // files keep historical 800/800 groups wherever sentences permit.
                File bookDir=EdgeBookStorage.bookFolder(this,requested,name);
                File dir=new File(bookDir,"EdgeTTS");dir.mkdirs();
                EdgeSpeechLayout layout=EdgeSpeechLayout.forFolder(dir);
                long groupingStart=SystemClock.elapsedRealtime();
                ArrayList<SentenceChunks.Chunk> newParts=session.edgeGroups(
                    EdgeDictionaryStore.get(this).options(),layout);
                EdgeStartupTrace.stage("GROUP_INDEXED","duration_ms="+
                    (SystemClock.elapsedRealtime()-groupingStart)+" groups="+newParts.size());
                // v174: do NOT restore every saved MP3/WordBoundary before bookReady.
                // Individual missing clips are recovered on demand by lazyRestore().
                DownloadsRepository.storeEdgeMeta(bookDir,requested,name,positions.size());
                main.post(()->{
                    if(destroyed||!requested.equals(bookUri))return;
                    bookText=raw;title=name;chapters=parsedChapters;offsets.addAll(positions);sentenceParts.addAll(newParts);sentenceIndex=session.sentenceIndex;sentenceHistory.bind(sentenceIndex);directory=dir;
                    bookReady=!offsets.isEmpty();restore();
                    EdgeStartupTrace.stage("BOOK_READY","words="+offsets.size());
                    // Recovery is pure disk I/O. It must never delay the first frame.
                    io.execute(()->{
                      for(File pending:EdgeStreamCache.recover(dir)){
                        File meta=new File(pending.getParentFile(),pending.getName().replace(".ready",".json"));
                        EdgeCacheCommitter.submit(this,pending,meta,dir,
                            (key,file,problem)->main.post(()->{
                                if(problem==null&&requested.equals(bookUri)){
                                    for(Clip c:archive)if(c.key.equals(key)){
                                        c.file=file;c.bytes=file.length();break;
                                    }
                                    persist();pump();
                                }
                            }));
                      }
                    });
                    if(!bookReady)error="Книга не содержит текста";
                    else {
                        // Startup restoration is not a tap: do not clear an active
                        // prefetch or request 500-char priority pieces automatically.
                        if(deferredSelectedWord>=0){
                            int selected=deferredSelectedWord;
                            deferredSelectedWord=-1;
                            setAnchor(selected,true);
                        }else restoreStartPosition();
                        persist();
                    }
                    warmCurrent();
                    pump();
                });
            }catch(Exception ex){main.post(()->{if(requested.equals(bookUri)){
                error="Ошибка чтения книги: "+ex.getMessage();changed();
                if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
            }});}
        });
    }
    /** Restore an arbitrary word without calling the manual-seek path.
     * A cached group with WordBoundary can seek locally. Otherwise synthesize
     * full sentences from the selected position, never a cut-off sentence.
     * On Play the same warm buffer is reused instead of a second handshake.
     */
    private void restoreStartPosition(){
        if(sentenceIndex.size()>0)anchorWord=sentenceIndex.beginning(anchorWord);
        selectedOverrides.clear();
        selectedSeekWord=-1;
        Clip base=baseClipAt(anchorWord);
        if(base==null || anchorWord<=base.start)return;
        int relative=charForWord(anchorWord)-charForWord(base.start);
        if(base.ready() && EdgeSentenceSeek.at(relative,base.text,
                EdgeWordTimings.read(base.file,base.text))>=0){
            selectedSeekWord=anchorWord;
            return;
        }
        SentenceChunks.Chunk group=SentenceChunks.forWord(sentenceParts,anchorWord);
        if(group==null)return;
        revision();
        for(SentenceChunks.Chunk piece:EdgeSpeechSegments.priorityPieces(
                bookText,sentenceIndex,anchorWord,group.toWord,
                source->EdgeSpeechSize.fits(speechOptions.apply(source).spoken))){
            selectedOverrides.add(new Clip(piece.fromWord,piece.toWord,piece.text,""));
        }
        // When WordBoundary is unavailable before streaming begins, play the
        // full selected sentence rather than synthesizing an amputated fragment.
        selectedSeekWord=anchorWord;
    }

    /** One silent, low-priority current-block request. Never synthesize
     * an already cached file and never run a speculative queue of chapters.
     * A subsequent Play can attach to the very same buffered live MP3.
     */
    private void warmCurrent(){
        if(!DownloadFolderPicker.granted(this,DownloadFolderPicker.BOOK))return;
        // Permission check only: avoid slow SAF provider stats on UI thread.
        if(!bookReady||directory==null)return;
        if(!idlePrefetchRequested && !(active && player!=null&&!player.playRequested()))return;
        if(active&&player!=null&&player.playRequested())return;
        Clip target=clipAt(anchorWord);
        if(target!=null&&shouldIgnore(target))return; // no warmup WebSocket
        // Never retry a failed warmup automatically. Only an explicit Play
        // or a manual selection can permit another synthesis attempt.
        if(target!=null && lazyRestore(target))return;
        if(target==null||target.ready()||target.awaitingUserRetry||
                target.retryAt>SystemClock.elapsedRealtime()||
                EdgeSpeechQueue.remainingMs()>0||!EdgeSpeechQueue.allowPrefetch()){
            if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
            return;
        }
        if(liveFor(target)!=null)return;
        startLive(target,true);
    }
    /** Dictionary edits must not turn a playing chapter into a manual seek. */
    public void refreshSelectedClip(int word){
        refreshSettings();
        // When paused, keep the existing reading position; only warm the corrected clip.
        if(player==null || !player.playRequested())warmCurrent();
    }
    public void refreshSettings(){
        EdgeSpeechTextTransform.Options updated=EdgeDictionaryStore.get(this).options();
        String requested=updated.fingerprint()+"|"+voice()+"|"+rate();
        if(speechOptions==null){speechOptions=updated;speechRevision=requested;pump();return;}
        if(requested.equals(speechRevision)){pump();return;}
        // Compute the audible clip first, rather than waiting for an all-book scan.
        int spoken=bookReady&&player!=null?currentSpeechWord():anchorWord;
        Clip current=bookReady?clipAt(Math.max(0,spoken)):null;
        String oldKey=current==null?"":current.key;
        speechOptions=updated;speechRevision=requested;
        preparedSpeech.clear();
        if(current!=null)prepareClip(current);
        boolean affected=current!=null&&!oldKey.equals(current.key);
        dictionaryGeneration.incrementAndGet();
        if(affected){
            ReaderDiagnosticLog.event("CLIP_STALE","current start="+current.start);
            boolean resume=player!=null&&player.playRequested();
            // Stop obsolete audio before it can play another frame. Old WebSockets and
            // completed cache files cannot be reused with a different speech key.
            cancelLiveExcept(null);
            liveJobs.clear();
            selectedOverrides.clear();selectedSeekWord=-1;
            if(player!=null)player.clearTracksForSelection();
            anchorWord=Math.max(0,spoken);
            if(bookReady){
                setAnchor(anchorWord,true);
                if(resume&&player!=null){player.play();ReaderDiagnosticLog.event("PLAYBACK_SWITCHED","word="+anchorWord);}
            }
        }else{
            ReaderDiagnosticLog.event("CLIP_REUSED","current word="+spoken);
            // Keep currently playing MP3, but cancel any queued candidate that is no
            // longer valid when the bounded nearby refresh recalculates its key.
        }
        if(bookReady)scheduleDictionaryScan(false);
        pump();
    }
    private static final class SpeechCheck {
        final Clip clip;final String key;final File file;final boolean skip;
        SpeechCheck(Clip clip,String key,File file,boolean skip){
            this.clip=clip;this.key=key;this.file=file;this.skip=skip;
        }
    }
    /** Work off the main thread in bounded batches; no full-cache rescans per UI tick.
     * Order: current/next five, current chapter from its beginning, later chapters,
     * and earlier chapters only when playback is idle.
     */
    private void scheduleDictionaryScan(boolean previousOnly){
        if(!bookReady||directory==null||archive.isEmpty())return;
        if(previousOnly && player!=null&&player.playRequested())return;
        final int generation=dictionaryGeneration.get();
        final String uri=bookUri, revision=speechRevision;
        final File folder=directory;
        final EdgeSpeechTextTransform.Options options=speechOptions;
        final String voiceName=voice(),rateName=rate();
        final int at=Math.max(0,player!=null?currentSpeechWord():anchorWord);
        final int chapterStart,chapterEnd;
        BookChapterIndex.Chapter chapter=chapters.atChar(charForWord(at));
        if(chapter==null){chapterStart=0;chapterEnd=bookText.length();}
        else{chapterStart=chapter.startChar;chapterEnd=chapter.endChar;}
        java.util.LinkedHashSet<Clip> ordered=new java.util.LinkedHashSet<>();
        if(previousOnly){
            previousChaptersPending=false;
            for(Clip clip:archive)if(charForWord(clip.start)<chapterStart)ordered.add(clip);
        }else{
            Clip current=clipAt(at);
            if(current!=null){
                ordered.add(current);
                int following=current.end;
                for(int n=0;n<5&&following<offsets.size();n++){
                    Clip next=clipAt(following);if(next==null||next.end<=following)break;
                    ordered.add(next);following=next.end;
                }
            }
            for(Clip clip:archive){
                int start=charForWord(clip.start);
                if(start>=chapterStart&&start<chapterEnd)ordered.add(clip);
            }
            for(Clip clip:archive)if(charForWord(clip.start)>=chapterEnd)ordered.add(clip);
            previousChaptersPending=chapterStart>0;
        }
        final ArrayList<Clip> target=new ArrayList<>(ordered);
        final java.util.HashSet<Clip> urgent=new java.util.HashSet<>();
        if(!previousOnly)for(int i=0;i<Math.min(6,target.size());i++)urgent.add(target.get(i));
        dictionaryScan.execute(()->{
            for(int begin=0;begin<target.size();begin+=20){
                if(generation!=dictionaryGeneration.get()||Thread.currentThread().isInterrupted())return;
                if(previousOnly&&interactiveListening){
                    main.post(()->{if(generation==dictionaryGeneration.get())previousChaptersPending=true;});
                    return;
                }
                ArrayList<SpeechCheck> checks=new ArrayList<>();
                for(int k=begin;k<Math.min(target.size(),begin+20);k++){
                    Clip clip=target.get(k);
                    EdgeSpeechTextTransform.Result result=options.apply(clip.text);
                    String key=result.cacheKey(voiceName,rateName);
                    File file=result.skipSynthesis()?null:new File(folder,key+".mp3");
                    if(file!=null&&!EdgeAudioManager.ready(file))file=null;
                    checks.add(new SpeechCheck(clip,key,file,result.skipSynthesis()));
                }
                main.post(()->{
                    if(destroyed||generation!=dictionaryGeneration.get()||
                        !uri.equals(bookUri)||!revision.equals(speechRevision))return;
                    if(previousOnly&&interactiveListening){previousChaptersPending=true;return;}
                    boolean refreshNearby=false;
                    for(SpeechCheck check:checks){
                        Clip clip=check.clip;
                        // An active override can supersede a structural chunk; never
                        // disturb it from a background result.
                        String old=clip.key;
                        if(!old.equals(check.key)){
                            if(urgent.contains(clip))refreshNearby=true;
                            if(player!=null&&!old.isEmpty() &&
                                !(player.currentKey().equals(old+":"+clip.start)))
                                player.evictCachedTrack(old+":"+clip.start);
                            clip.failures=0;clip.awaitingUserRetry=false;clip.retryAt=0;
                            ReaderDiagnosticLog.event("CLIP_STALE","start="+clip.start);
                        }
                        clip.key=check.key;clip.revision=revision;clip.ignored=check.skip;
                        clip.file=check.file;clip.bytes=check.file==null?0:check.file.length();
                        preparedSpeech.remove(clip);
                    }
                    if(active&&refreshNearby)pump();
                });
                // Let interactive synthesis and frame decoding win CPU scheduling.
                if(!previousOnly)try{Thread.sleep(90L);}catch(InterruptedException stop){
                    Thread.currentThread().interrupt();return;
                }
            }
            ReaderDiagnosticLog.event("EDGE","dictionary_scan_finished previous="+previousOnly+
                " candidates="+target.size());
            main.post(()->{
                if(generation!=dictionaryGeneration.get()||!uri.equals(bookUri))return;
                persist();changed();
                if(!previousOnly&&previousChaptersPending&&!interactiveListening)
                    scheduleDictionaryScan(true);
            });
        });
    }
    public void ensurePlaySelection(){
        if(bookReady){Clip current=clipAt(anchorWord);
            if(current!=null&&player!=null&&player.playRequested()){
                // An explicit Play lets the user retry a previously failed stream.
                if(!current.ready() && liveFor(current)==null &&
                        (current.retryAt>0 ||
                        (player.playbackError()!=null&&!player.playbackError().isEmpty()))){
                    // Media3 may have rejected an old failed live DataSource. Rebuild
                    // only on an explicit user Play, never in a retry loop.
                    cancelLiveExcept(null);
                    liveJobs.clear();
                    player.clearTracksForSelection();
                    player.play();
                }
                current.awaitingUserRetry=false;
                if(EdgeSpeechQueue.remainingMs()==0){
                    current.retryAt=0;
                    current.failures=0;
                    error="";
                }else{
                    // Do not display HTTP 429 as a user-visible error.
                    // EdgeSpeechQueue still blocks the actual request until cooldown ends.
                    error="";
                }
                pump();
            }
        }
    }
    public void setActive(boolean enabled){
        if(enabled&&!active){trackReadyLoggedAt=-1L;restoreStartedAt=SystemClock.elapsedRealtime();}
        active=enabled;
        main.removeCallbacks(idleExpiry);
        if(enabled){warmCurrent();pump();return;}
        EdgeSpeechQueue.setInteractivePlayback(false);
        // Leaving the TTS listener cancels network synthesis. Completed cache
        // commits are independent and continue on their own worker.
        cancelLiveExcept(null);
        idlePrefetchRequested=false;
        changed();main.postDelayed(idleExpiry,IDLE_REUSE_MS);
    }
    private void cancelLiveExcept(Clip selected){
        for(LiveJob job:new ArrayList<>(liveJobs.values())){
            if(job.networkDone)continue; // complete cache must be committed
            if(selected!=null && job.clip==selected)continue;
            liveJobs.remove(job.fileId);
            EdgeTtsDiagnostics.abandon(job.clip.key+":"+job.clip.start);
            if(job.buffer!=null)job.buffer.fail(new java.io.IOException("Navigation canceled Edge stream"));
            if(job.request!=null)job.request.cancel();
            // No partial MP3 is ever stored by the new engine.
        }
    }
    /** Actual spoken word, including cached/live WordBoundary in the middle of an MP3 group. */
    private int currentSpeechWord(){
        int current=player!=null&&player.currentStartWord()>=0?player.currentStartWord():anchorWord;
        Clip clip=clipAt(Math.max(0,current));
        if(clip!=null&&player!=null){
            File source=player.currentTrackFile();
            java.util.List<EdgeWordTimings.Word> timing=source!=null&&source.isFile()
                ?EdgeWordTimings.read(source,clip.text):liveWords(clip);
            long ms=player.positionMs();int mark=EdgeWordTimings.at(timing,ms);
            if(mark<0)for(int j=timing.size()-1;j>=0;j--)
                if(timing.get(j).startMs<=ms){mark=j;break;}
            if(mark>=0&&mark<timing.size()){
                int sourceChar=charForWord(current)+timing.get(mark).from;
                current=wordForChar(sourceChar);
            }
            // During the short MP3 pre-roll the extractor can still report a
            // preceding word in the same saved group. Never report that word
            // as the start of an explicitly replayed sentence.
            if(player.currentKey().equals(replayTrackKey)&&replayStartWord>=0)
                current=Math.max(current,replayStartWord);
        }
        if(sentenceIndex.size()>0)sentenceHistory.observe(current);
        return current;
    }
    /** Playback's pause boundary records the sentence seen in WordBoundary/highlighting. */
    private void capturePausedSentence(){
        if(bookReady&&sentenceIndex.size()>0)sentenceHistory.capturePause(currentSpeechWord());
    }
    /** Resume from the saved complete current/previous sentence, not an MP3 chunk. */
    private void selectSentenceOnResume(){
        if(!bookReady||player==null||sentenceIndex.size()==0)return;
        int spoken=currentSpeechWord();
        int target=TtsResumeChoice.previous(this)
            ?sentenceHistory.previousOnResume(spoken)
            :sentenceHistory.currentOnResume(spoken);
        sentenceHistory.manuallySelect(target);
        setAnchor(target,true);
    }
    /** Previous/next commands from UI, MediaSession, headset and notification. */
    private void navigateSentence(int direction){
        if(!bookReady||sentenceIndex.size()==0)return;
        int position=currentSpeechWord();
        int target=direction<0?sentenceIndex.previous(position):sentenceIndex.next(position);
        sentenceHistory.manuallySelect(target);
        setAnchor(target,true);
    }
    /** Called only on explicit PLAY in the newly opened reader. */
    public void activatePreparedBook(){
        if(pendingBookUri.isEmpty())return;
        String uri=pendingBookUri;int word=pendingBookWord;pendingBookUri="";
        if(player!=null&&player.playRequested())player.pause();
        openBook(uri,word);
        if(player!=null)player.prepareBook("edge:"+uri);
    }
    public boolean hasPendingBook(){return !pendingBookUri.isEmpty();}
    public void setAnchor(int word,boolean selected){
        // An explicit replay always starts at the first word of its sentence.
        // The original tap can land anywhere inside the sentence.
        if(selected && bookReady && sentenceIndex.size()>0)
            word=sentenceIndex.beginning(Math.max(0,word));
        if(selected){selectionSerial++;lastSelectedWord=Math.max(0,word);}
        if(!pendingBookUri.isEmpty()){
            pendingBookWord=Math.max(0,word);return;
        }
        if(!bookReady){
            anchorWord=Math.max(0,word);
            if(selected)deferredSelectedWord=anchorWord;
            return;
        }
        int requested=Math.max(0,Math.min(offsets.size()-1,word));
        if(selected){sentenceHistory.manuallySelect(requested);
            ReaderDiagnosticLog.event("NAVIGATION","PLAY_TARGET selected_word="+requested+" source=manual");}
        if(!selected){anchorWord=requested;persist();pump();return;}
        Clip base=baseClipAt(requested);
        if(base==null)return;
        // Manual seek invalidates the speculative window; defer new jobs until
        // the selected stream has produced playable audio.
        prefetchForStart=-1;prefetchIssued=0;
        // Reset the playlist first, before any new file can begin playing.
        boolean resumeSelectedPlay=player!=null&&player.playRequested();
        if(player!=null){
            player.clearTracksForSelection();
            if(resumeSelectedPlay)player.play();
        }
        selectedOverrides.clear();
        replayStartWord=-1;replayTrackKey="";
        selectedSeekWord=requested;
        Clip target=base;
        int relative=charForWord(requested)-charForWord(base.start);
        long position=base.ready()?
            EdgeSentenceSeek.at(relative,base.text,EdgeWordTimings.read(base.file,base.text)):-1L;
        boolean exactLive=requested==base.start&&liveFor(base)!=null;
        if(position<0&&!exactLive){
            // Full sentences only, with a low-latency soft byte/character target.
            // Nothing starts at the middle of the tapped sentence.
            SentenceChunks.Chunk grouped=SentenceChunks.forWord(sentenceParts,requested);
            if(grouped!=null){
                revision(); // Compile a consistent dictionary snapshot once.
                for(SentenceChunks.Chunk piece:EdgeSpeechSegments.priorityPieces(
                        bookText,sentenceIndex,requested,grouped.toWord,
                        source->EdgeSpeechSize.fits(speechOptions.apply(source).spoken))){
                    Clip clip=new Clip(piece.fromWord,piece.toWord,piece.text,"");
                    selectedOverrides.add(clip);
                }
                if(!selectedOverrides.isEmpty())target=selectedOverrides.get(0);
            }
        }
        // Keep an already-running synthesis only if it produces the exact
        // selected audio; otherwise cancel it to avoid old audio and traffic.
        cancelLiveExcept(target);
        // A manual tap does not cancel the server's 429 cooldown.
        target.retryAt=0;target.failures=0;target.awaitingUserRetry=false;
        anchorWord=requested;
        pendingStarts.clear();priorityStart=-1;
        persist();pump();
    }
    /** The normal v158 Edge group, without temporary priority overrides. */
    private Clip baseClipAt(int word){
        if(word<0||word>=offsets.size()||sentenceParts.isEmpty())return null;
        SentenceChunks.Chunk part=SentenceChunks.forWord(sentenceParts,word);
        if(part==null)return null;
        Clip clip=byStart.get(part.fromWord);
        prepareClip(clip);
        return clip;
    }
    /** Manual priority pieces cover the remainder of the selected normal group. */
    private Clip clipAt(int word){
        for(Clip override:selectedOverrides)if(word>=override.start&&word<override.end){
            prepareClip(override);
            return override;
        }
        return baseClipAt(word);
    }
    /** The on-demand engine streams encoded MP3 to Media3 immediately; the final
     * validated MP3 is committed to the existing persistent cache separately.
     */
    private boolean shouldIgnore(Clip clip){
        if(clip==null)return false;
        prepareClip(clip);
        return clip.ignored;
    }
    /** Skip every ignored segment before touching the player or WebSocket.
     * This also handles a direct seek into an ignored segment. */
    private Clip skipIgnored(Clip current){
        if(current==null||!shouldIgnore(current))return current;
        int next=anchorWord;int skipped=0;
        while(current!=null&&shouldIgnore(current)&&current.end>next){
            ReaderDiagnosticLog.event("EDGE","ignore segment start="+current.start+" end="+current.end);
            next=current.end;skipped++;
            if(next>=offsets.size())break;
            current=clipAt(next);
        }
        if(skipped==0)return current;
        final int nextAnchor=next;
        selectedSeekWord=-1;selectedOverrides.removeIf(c->c.end<=nextAnchor);
        anchorWord=nextAnchor;prefetchForStart=-1;prefetchIssued=0;
        if(player!=null){
            boolean play=player.playRequested();
            player.clearTracksForSelection();
            if(play&&next<offsets.size())player.play();
            else if(play)player.pause();
        }
        persist();changed();
        return next>=offsets.size()?null:current;
    }
    private void pump(){
        interactiveListening=active && player!=null && player.playRequested();
        EdgeSpeechQueue.setInteractivePlayback(interactiveListening);
        if(!bookReady||directory==null||destroyed||!active)return;
        Clip current=skipIgnored(clipAt(anchorWord));
        if(current==null)return;
        if(player!=null && player.playRequested() && player.isAtEnd() &&
                !current.ready() && liveFor(current)==null){
            String waitingKey=current.key+":"+current.start;
            if(!waitingKey.equals(lastTransitionWaitKey)){
                lastTransitionWaitKey=waitingKey;
                lastTransitionWaitAtMs=SystemClock.elapsedRealtime();
                ReaderDiagnosticLog.event("PLAYER","NEXT_CLIP_WAITING start="+current.start);
            }
        }
        if(lazyRestore(current)){changed();return;}
        Clip ahead=clipAt(current.end);
        // A skipped next segment must never be added as a cached track.
        if(shouldIgnore(ahead))ahead=null;
        if(player!=null){
            player.setAutoContinue(true);
            LiveJob waiting=liveFor(current);
            if(waiting!=null && player.playRequested() && waiting.request!=null)
                waiting.request.promoteToPlay();
            if(waiting!=null && waiting.buffer!=null && !waiting.attached && player.playRequested()){
                waiting.attached=true;
                player.appendLive(current.key+":"+current.start,current.start,current.end,waiting.buffer);
            }
            if(current.ready()){
                EdgeStartupTrace.stage("CACHE_READY","cached=true");
                firstTrackReady(current,"cache");
                // Position is armed *before* appendReady can call Media3.play().
                if(selectedSeekWord>=0){
                    int relative=charForWord(selectedSeekWord)-charForWord(current.start);
                    long ms=EdgeSentenceSeek.at(relative,current.text,EdgeWordTimings.read(current.file,current.text));
                    // No matching boundary in a multi-sentence cached group:
                    // never replay that group's beginning or its second word.
                    // setAnchor() should already have selected an exact-sentence
                    // override. Refuse an unsafe seek if metadata changed meanwhile.
                    if(ms<0 && current.start!=selectedSeekWord){
                        ReaderDiagnosticLog.event("NAVIGATION","SEEK_REFUSED missing_first_word start="+
                            current.start+" requested="+selectedSeekWord);
                        error="Не удалось найти точное начало предложения для повтора";
                        changed();return;
                    }
                    if(ms<0)ms=0; // An isolated sentence must start at 0.
                    Log.i("EdgeSentenceSeek","word="+selectedSeekWord+
                        " clip="+current.start+" startMs="+ms+
                        (selectedOverrides.contains(current)?" exact-fragment":" WordBoundary"));
                    replayStartWord=selectedSeekWord;
                    replayTrackKey=current.key+":"+current.start;
                    player.armSelectedPosition(replayTrackKey,ms);
                    selectedSeekWord=-1;
                }
                addTrack(current);
            }
            // Attach the following cached/live source as soon as first bytes
            // exist, even if the currently playing MP3 is still a live source.
            // Never skip an unprepared current clip to append a later one.
            if(player.hasTrack(current.key+":"+current.start) && ahead!=null){
                if(ahead.ready()){
                    addTrack(ahead);
                }else{
                    LiveJob preloaded=liveFor(ahead);
                    if(preloaded!=null&&preloaded.buffer!=null&&!preloaded.attached&&
                            preloaded.buffer.available()>0){
                        preloaded.attached=true;
                        player.appendLive(ahead.key+":"+ahead.start,ahead.start,
                            ahead.end,preloaded.buffer);
                    }
                }
            }
            if(player.playRequested())player.resumeEndedAt(current.start);
            if(player.hasTrack(current.key+":"+current.start)){
                if(!lastTransitionWaitKey.isEmpty() &&
                        lastTransitionWaitKey.equals(current.key+":"+current.start)){
                    ReaderDiagnosticLog.event("PLAYER","NEXT_CLIP_READY start="+current.start+
                        " waited_ms="+(SystemClock.elapsedRealtime()-lastTransitionWaitAtMs));
                    lastTransitionWaitKey="";
                }
            }
        }
        if(player==null||!player.playRequested()){changed();return;}
        if(EdgeSpeechQueue.remainingMs()>0){changed();return;}
        if(!current.ready()&&liveFor(current)==null&&
                !current.awaitingUserRetry && current.retryAt<=SystemClock.elapsedRealtime())startLive(current);
        // Do not flood the endpoint before the first playable frames exist.
        LiveJob currentJob=liveFor(current);
        boolean startedAudio=current.ready()||(currentJob!=null&&currentJob.buffer!=null&&
                currentJob.buffer.available()>0);
        if(startedAudio&&EdgeSpeechQueue.allowPrefetch())prefetchWindow(current);
        changed();
    }
    /** Returns true while the currently requested clip is being restored from SAF.
     * A failed/absent clip is attempted only once and then falls back to synthesis.
     * No SAF operation runs on the main thread or blocks bookReady.
     */
    private boolean lazyRestore(Clip clip){
        if(clip==null||directory==null||clip.ready()||clip.ignored)return false;
        prepareClip(clip);
        if(clip.ready()||clip.key==null||clip.key.isEmpty())return false;
        String identity=bookUri+"|"+clip.key;
        final boolean foreground=active&&player!=null&&player.playRequested()&&
            clip==clipAt(anchorWord);
        if(lazyPending.contains(identity)){
            if(!EdgeFirstAudioPolicy.bypassPendingRestore(
                    foreground,lazyForeground.contains(identity)))return true;
            // A paused warmup might already be waiting 9 seconds for SAF.
            // User Play cancels that restore immediately instead of inheriting it.
            java.util.concurrent.atomic.AtomicBoolean old=lazyCommitPermits.remove(identity);
            if(old!=null)old.set(false);
            lazyPending.remove(identity);lazyForeground.remove(identity);
            ReaderDiagnosticLog.event("EDGE_STARTUP","SAF_RESTORE_BYPASS pending_warmup=true");
            EdgeStartupTrace.stage("SAF_RESTORE_BYPASS","previous_warmup=true");
            return false;
        }
        if(!lazyAttempted.add(identity))return false;
        if(EdgeFolderPicker.selected(this)==null)return false;
        final long waitMs=EdgeFirstAudioPolicy.restoreWaitMs(foreground);
        final java.util.concurrent.atomic.AtomicBoolean commitAllowed=
            new java.util.concurrent.atomic.AtomicBoolean(true);
        final long restoreCallAt=SystemClock.elapsedRealtime();
        ReaderDiagnosticLog.event("EDGE_STARTUP","SAF_RESTORE_START foreground="+foreground+
            " grace_ms="+waitMs);
        if(foreground)EdgeStartupTrace.stage("SAF_RESTORE_CHECK","grace_ms="+waitMs);
        int chapterNumber=0;
        int character=charForWord(clip.start);
        if(chapters!=null)for(int i=0;i<chapters.size();i++){
            if(chapters.get(i).startChar<=character)chapterNumber=i;else break;
        }
        final int chapter=chapterNumber;
        final String requested=bookUri,requestedKey=clip.key,requestedTitle=title;
        final File folder=directory;
        lazyPending.add(identity);
        if(foreground)lazyForeground.add(identity);
        lazyCommitPermits.put(identity,commitAllowed);
        final Runnable restoreDeadline=()->{
            if(destroyed||!requested.equals(bookUri)||directory!=folder)return;
            if(!lazyPending.remove(identity))return;
            lazyForeground.remove(identity);
            // A timed-out worker is forbidden from publishing over a new live MP3.
            commitAllowed.set(false);
            lazyCommitPermits.remove(identity);
            ReaderDiagnosticLog.event("EDGE_STARTUP","SAF_RESTORE_TIMEOUT elapsed_ms="+
                (SystemClock.elapsedRealtime()-restoreCallAt)+" foreground="+foreground);
            ReaderDiagnosticLog.event("PLAYER","TRANSITION_STALLED saf_restore_timeout start="+clip.start);
            // The attempted key remains recorded; a second SAF read is not
            // started while the first provider call is still pending.
            if(active)pump();else if(idlePrefetchRequested)warmCurrent();
        };
        main.postDelayed(restoreDeadline,waitMs);
        clipRestore.execute(()->{
            boolean restored=commitAllowed.get()&&EdgeChapterArchive.restoreClip(
                this,requested,requestedTitle,folder,chapter,requestedKey,
                commitAllowed::get);
            main.post(()->{
                main.removeCallbacks(restoreDeadline);
                boolean stillPending=lazyPending.remove(identity);
                lazyForeground.remove(identity);
                lazyCommitPermits.remove(identity);
                if(!stillPending||!commitAllowed.get())return;
                ReaderDiagnosticLog.event("EDGE_STARTUP","SAF_RESTORE_RESULT hit="+restored+
                    " elapsed_ms="+(SystemClock.elapsedRealtime()-restoreCallAt));
                if(destroyed||!requested.equals(bookUri)||directory!=folder||
                    !requestedKey.equals(clip.key))return;
                if(restored){
                    File file=new File(folder,requestedKey+".mp3");
                    if(EdgeAudioManager.ready(file)){
                        clip.file=file;clip.bytes=file.length();
                        for(Clip candidate:archive)if(requestedKey.equals(candidate.key)){
                            candidate.file=file;candidate.bytes=file.length();
                        }
                        firstTrackReady(clip,"saf-lazy");
                        ReaderDiagnosticLog.event("PLAYER","TRANSITION_RECOVERED saf_restored start="+clip.start);
                    }
                }
                if(active)pump();
                else if(idlePrefetchRequested)warmCurrent();
            });
        });
        return true;
    }
    /** Quickify-inspired finite window: candidate selection and network concurrency
     * are separate. Cache, existing jobs, and failed requests are checked first. */
    private void prefetchWindow(Clip current){
        if(current==null||directory==null||player==null||!player.playRequested())return;
        if(prefetchForStart!=current.start){prefetchForStart=current.start;prefetchIssued=0;}
        if(prefetchIssued>=EdgeSpeechQueue.MAX_PREFETCH)return;
        int budget=EdgePrefetchPlanner.budget(current.text.length(),false);
        ArrayList<Clip> candidates=new ArrayList<>();
        ArrayList<Integer> lengths=new ArrayList<>();
        int position=current.end;
        for(int i=0;i<EdgePrefetchPlanner.MAX_CANDIDATES&&position<offsets.size();i++){
            Clip candidate=clipAt(position);
            if(candidate==null||candidate.end<=position)break;
            if(!shouldIgnore(candidate)){
                candidates.add(candidate);lengths.add(candidate.text.length());
            }
            position=candidate.end;
        }
        int pending=0;
        for(LiveJob j:liveJobs.values())if(!j.networkDone&&j.clip!=current)pending++;
        for(int index:EdgePrefetchPlanner.select(lengths,budget,
                EdgePrefetchPlanner.countLimit(budget))){
            if(pending>=EdgeSpeechQueue.MAX_PREFETCH||prefetchIssued>=EdgeSpeechQueue.MAX_PREFETCH)break;
            Clip candidate=candidates.get(index);
            if(lazyRestore(candidate))continue;
            if(candidate.ready()||liveFor(candidate)!=null||candidate.awaitingUserRetry||
                    candidate.retryAt>SystemClock.elapsedRealtime())continue;
            // Cache key is scoped by spoken text, voice, rate and pronunciation.
            ReaderDiagnosticLog.event("PREFETCH","schedule chars="+candidate.text.length()+" pending_before="+pending);
            startLive(candidate,false,true);
            pending++;prefetchIssued++;
        }
    }
    private void addTrack(Clip clip){
        if(player!=null&&clip!=null&&clip.ready())
            player.appendReady(clip.key+":"+clip.start,clip.start,clip.end,clip.file);
    }
    private LiveJob liveFor(Clip clip){
        if(clip==null||directory==null)return null;
        return liveJobs.get(new File(directory,clip.key+".mp3").getAbsolutePath());
    }
    /** MP3 frames stream immediately; speculative segments keep their own
     * buffers so the reader can attach when the playback cursor catches up. */
    private void startLive(Clip clip){startLive(clip,false,false);}
    private void startLive(Clip clip,boolean warmup){startLive(clip,warmup,false);}
    private void startLive(Clip clip,boolean warmup,boolean ahead){
        if(!DownloadFolderPicker.granted(this,DownloadFolderPicker.BOOK)){
            error="Выберите папку Edge TTS в настройках";changed();return;
        }
        if(clip==null||clip.ready()||directory==null||liveFor(clip)!=null||shouldIgnore(clip))return;
        if(lazyRestore(clip))return;
        ReaderDiagnosticLog.event("CACHE","cache_miss schedule start="+clip.start+" end="+clip.end+
            " warmup="+warmup+" ahead="+ahead);
        if(!warmup&&!ahead)EdgeStartupTrace.stage("NETWORK_REQUEST","chars="+clip.text.length());
        lastHttp429=false; // a new explicit synthesis may report other real errors
        final LiveJob job=new LiveJob(clip,directory,bookUri);
        job.speech=speechFor(clip);
        if(!job.speech.cacheKey(voice(),rate()).equals(clip.key))return;
        final boolean foreground=!warmup&&!ahead&&(clip==clipAt(anchorWord));
        if(foreground||warmup||ahead){
            job.buffer=new EdgeLiveBuffer();
        }
        if(foreground && player!=null){
            job.attached=true;
            // Bind Media3 to streaming bytes BEFORE starting network synthesis.
            // The first MP3 frame can begin decoding without waiting for turn.end.
            // Priority clips start at a real sentence boundary. WordBoundary may
            // later refine the requested word position for a cached MP3.
            if(selectedSeekWord==clip.start && selectedOverrides.contains(clip)){
                replayStartWord=selectedSeekWord;
                replayTrackKey=clip.key+":"+clip.start;
                selectedSeekWord=-1;
            }
            player.appendLive(clip.key+":"+clip.start,clip.start,clip.end,job.buffer);
        }
        liveJobs.put(job.fileId,job);
        final String diagnosticKey=clip.key+":"+clip.start;
        final String voiceNameAtRequest=voice(),rateAtRequest=rate();
        EdgeTtsDiagnostics.requested(diagnosticKey,clip.text.length(),warmup);
        error="";changed();
        job.request=EdgeTtsClient.synthesize(job.speech.spoken,voiceNameAtRequest,rateAtRequest,
            foreground?EdgeSpeechQueue.PLAY:EdgeSpeechQueue.PREFETCH,
            new EdgeTtsClient.Callback(){
                @Override public void progress(int count,long bytes){}
                @Override public void connected(){
                    EdgeTtsDiagnostics.connected(diagnosticKey);
                    if(foreground)EdgeStartupTrace.stage("WS_CONNECTED","");
                }
                @Override public void audio(byte[] packet,int start,int length){
                    EdgeTtsDiagnostics.firstMp3(diagnosticKey,length);
                    if(foreground)EdgeStartupTrace.stage("FIRST_MP3_PACKET","bytes="+length);
                    if(job.buffer!=null)try{job.buffer.append(packet,start,length);}
                    catch(java.io.IOException e){Log.w("EdgeSpeech","Live buffer rejected MP3",e);}
                    if(!job.firstAudioNotified){
                        job.firstAudioNotified=true;
                        main.post(()->{if(job.bookId.equals(bookUri)&&active){
                            firstTrackReady(clip,"stream");pump();
                        }});
                    }
                }
                @Override public void metadata(List<EdgeWordTimings.Boundary> marks){
                    job.timings=job.speech.aligned(marks);
                }
                @Override public void finished(byte[] mp3,int parts,String problem){
                    finished(mp3,parts,problem,Collections.emptyList());
                }
                @Override public void finished(byte[] mp3,int parts,String problem,
                        List<EdgeWordTimings.Boundary> metadata){
                    job.networkDone=true;
                    ReaderDiagnosticLog.event("EDGE","request callback "+(problem==null?"ok":
                        problem.contains("HTTP 429")?"http429":"error")+" frames="+parts+
                        " start="+clip.start);
                    EdgeTtsDiagnostics.finished(diagnosticKey,problem,
                        job.timings==null?0:job.timings.size());
                    if(problem!=null){
                        if(foreground)EdgeStartupTrace.stage("NETWORK_ERROR","category="+
                            (problem.contains("HTTP 429")?"http429":
                                problem.contains("Предложение превышает")?"ssml_limit":"transport"));
                        if(problem.contains("HTTP 429"))lastHttp429=true;
                        if(job.buffer!=null)job.buffer.fail(new java.io.IOException(problem));
                        main.post(()->{
                            if(!liveJobs.remove(job.fileId,job))return;
                            if(player!=null && job.buffer!=null)
                                player.evictFailedLive(diagnosticKey);
                            if(!job.bookId.equals(bookUri)||!clip.key.equals(job.speech.cacheKey(voice(),rate())))return;
                            clip.failures++;
                            clip.awaitingUserRetry=!problem.contains("прерван ради активного чтения");
                            if(problem.contains("HTTP 429")){
                                getSharedPreferences("reader",0).edit().putLong(
                                    "edge_v2_retry_after_epoch_ms",
                                    System.currentTimeMillis()+EdgeSpeechQueue.remainingMs()).apply();
                                // One failed request is not an indefinite block. The
                                // queue honors an actual Retry-After, if supplied.
                                clip.retryAt=SystemClock.elapsedRealtime()+
                                    Math.max(1,EdgeSpeechQueue.remainingMs());
                            }else{
                                // No background retry loop: new tap/Play can retry now.
                                clip.retryAt=0;
                            }
                            // HTTP 429 remains diagnostic-only (Logcat), not a UI popup.
                            if(problem.contains("HTTP 429")){
                                Log.w("EdgeSpeech", "Edge TTS HTTP 429; remaining cooldown="+
                                    EdgeSpeechQueue.remainingMs()+"ms");
                                error="";
                            }else error="Edge TTS: "+problem;
                            persist();changed();
                            // A transient first-packet timeout is not proof Edge is offline.
                            // Retry only the active foreground fragment once, never prefetch.
                            boolean hardLimitExceeded=problem.contains("Предложение превышает допустимый размер");
                            boolean retryNow=!hardLimitExceeded&&clip.failures==1&&active&&player!=null&&
                                player.playRequested()&&!problem.contains("HTTP 429")&&
                                !problem.contains("прерван ради активного чтения")&&
                                (anchorWord>=clip.start&&anchorWord<clip.end);
                            if(retryNow){
                                ReaderDiagnosticLog.event("EDGE","EDGE_RETRY_SCHEDULED attempt=2");
                                main.postDelayed(()->{
                                    if(destroyed||!active||player==null||!player.playRequested()||
                                        !bookUri.equals(job.bookId)||clip.ready()||
                                        anchorWord<clip.start||anchorWord>=clip.end)return;
                                    clip.awaitingUserRetry=false;clip.retryAt=0;
                                    startLive(clip);
                                },900);
                            }else if(!problem.contains("прерван ради активного чтения")){
                                startBackupTtsForFailedClip(clip,hardLimitExceeded);
                            }
                            if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
                        });
                        return;
                    }
                    if(job.buffer!=null)job.buffer.complete();
                    io.execute(()->{
                        File saved=null;String failed=null;
                        try{saved=EdgeSpeechFiles.saveMapped(job.folder,
                            job.speech.cacheKey(voiceNameAtRequest,rateAtRequest),clip.text,
                            mp3,metadata,job.speech.aligned(metadata));
                            if(saved!=null)EdgeAudioManager.invalidate(job.folder);}
                        catch(Exception e){failed=e.getMessage();}
                        final File ready=saved;final String cacheError=failed;
                        main.post(()->{
                            if(!liveJobs.remove(job.fileId,job))return;
                            // Never attach a late completion of a canceled, previous
                            // dictionary generation to a newly prepared clip.
                            if(!job.bookId.equals(bookUri) ||
                                !clip.key.equals(job.speech.cacheKey(voiceNameAtRequest,rateAtRequest)))return;
                            if(cacheError==null && ready!=null){
                                getSharedPreferences("reader",0).edit().remove(
                                    "edge_v2_retry_after_epoch_ms").apply();
                                clip.file=ready;clip.bytes=ready.length();clip.chunks=parts;
                                // Every newly completed fragment can finish a chapter.
                                // Publication runs off the UI thread; the reader continues
                                // using its temporary per-clip audio without a source jump.
                                try{
                                    org.json.JSONArray pending=new org.json.JSONArray();
                                    for(Clip part:archive){
                                        org.json.JSONObject row=new org.json.JSONObject();
                                        row.put("key",part.key==null?"":part.key);
                                        row.put("start",part.start);row.put("end",part.end);
                                        row.put("text",part.text);pending.put(row);
                                    }
                                    final String saveUri=bookUri,saveTitle=title;
                                    final File saveDir=directory;
                                    final BookChapterIndex saveChapters=chapters;
                                    final ArrayList<Integer> saveOffsets=new ArrayList<>(offsets);
                                    if(saveDir!=null)chapterWriter.execute(()->{
                                        EdgeChapterArchive.publishFragments(getApplicationContext(),
                                            saveUri,saveTitle,saveDir,pending,saveChapters,saveOffsets);
                                    });
                                }catch(Exception ex){Log.w("EdgeChapterArchive","Could not schedule chapter",ex);}
                                ReaderDiagnosticLog.event("CACHE","saved mp3 bytes="+clip.bytes+
                                    " start="+clip.start+" end="+clip.end);
                                SentenceChunks.Chunk basePart=SentenceChunks.forWord(sentenceParts,clip.start);
                                if(basePart!=null&&(clip.start!=basePart.fromWord||clip.end!=basePart.toWord))
                                    saveOverride(clip);
                                clip.failures=0;clip.retryAt=0;clip.awaitingUserRetry=false;
                            }else{
                                clip.failures++;clip.retryAt=0;clip.awaitingUserRetry=true;
                                error="Ошибка сохранения MP3: "+cacheError;
                            }
                            persist();pump();
                            if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
                        });
                    });
                }
            });
    }
    /** A selected sentence can have its own cache key. Preserve its coverage
     * even after the base sentence catalogue is regenerated for another voice.
     */
    private void saveOverride(Clip part){
        if(directory==null||part==null||!part.ready())return;
        final File folder=directory;final int start=part.start,end=part.end;
        final String key=part.key;
        io.execute(()->{
            try{
                File index=new File(folder,"selected_clips.json");
                JSONArray rows=new JSONArray();
                if(index.isFile()&&index.length()<4*1024*1024){
                    byte[] content=new byte[(int)index.length()];int pos=0;
                    try(java.io.FileInputStream in=new java.io.FileInputStream(index)){
                        int n;while(pos<content.length&&(n=in.read(content,pos,content.length-pos))>0)pos+=n;
                    }
                    rows=new JSONArray(new String(content,0,pos,StandardCharsets.UTF_8));
                }
                for(int i=0;i<rows.length();i++){
                    JSONObject old=rows.optJSONObject(i);
                    if(old!=null&&start==old.optInt("start",-1)&&end==old.optInt("end",-1)
                        &&key.equals(old.optString("key")))return;
                }
                rows.put(new JSONObject().put("start",start).put("end",end).put("key",key));
                android.util.AtomicFile atomic=new android.util.AtomicFile(index);
                java.io.FileOutputStream out=null;
                try{out=atomic.startWrite();out.write(rows.toString().getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}
                catch(Exception e){if(out!=null)atomic.failWrite(out);throw e;}
            }catch(Exception ex){Log.w("EdgeSpeech","Cannot index selected MP3",ex);}
        });
    }
    /** Word timings become available with the committed MP3 sidecar. */
    public List<EdgeWordTimings.Word> liveWords(Clip c){
        LiveJob j=liveFor(c);
        return j==null?Collections.emptyList():j.timings;
    }

    // During ordinary listening no legacy download()/retry() or notification exists.
    // EdgeFullBookService owns explicit whole-book background preparation.
    public boolean deleteClipAudio(int start){
        for(Clip c:archive)if(c.start==start){
            prepareClip(c);
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            LiveJob live=liveFor(c);
            if(live!=null){liveJobs.remove(live.fileId);
                if(live.request!=null)live.request.cancel();}
            if(player!=null)player.evictCachedTrack(c.key+":"+c.start);
            c.file=null;c.bytes=0;c.chunks=0;c.played=false;c.failures=0;c.retryAt=0;
            File item=new File(directory,c.key+".mp3");io.execute(()->{item.delete();EdgeWordTimings.sidecar(item).delete();main.post(()->{persist();pump();});});
            error="";changed();return true;
        }return false;
    }
    public boolean redownloadClip(int start){
        for(Clip c:archive)if(c.start==start){
            prepareClip(c);
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            LiveJob live=liveFor(c);
            if(live!=null){liveJobs.remove(live.fileId);
                if(live.request!=null)live.request.cancel();}
            if(player!=null)player.evictCachedTrack(c.key+":"+c.start);
            c.file=null;c.bytes=0;c.chunks=0;c.played=false;c.failures=0;c.retryAt=0;
            File item=new File(directory,c.key+".mp3");io.execute(()->{item.delete();EdgeWordTimings.sidecar(item).delete();main.post(()->{persist();setActive(true);pump();});});
            changed();return true;
        }return false;
    }
    public void clearAudio(Runnable done){
        // An explicit deletion must stop all ongoing writers first.
        active=false;
        EdgeSpeechQueue.setInteractivePlayback(false);
        cancelLiveExcept(null);
        for(LiveJob job:new ArrayList<>(liveJobs.values())){
            if(job.request!=null)job.request.cancel();
        }
        liveJobs.clear();
        if(player!=null)player.clearQueue();
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        invalidateSpeech();
        selectedOverrides.clear();selectedSeekWord=-1;File folder=directory;
        io.execute(()->{if(folder!=null){File[] children=folder.listFiles();if(children!=null)for(File f:children)f.delete();}
            main.post(()->{restore();persist();changed();if(done!=null)done.run();});});
    }
    /** Restore only the ordered STRUCTURAL catalogue on the main thread.
     * No dictionary, MP3 stat, or JSON manifest operations are allowed here.
     * Each Clip is prepared lazily by clipAt() immediately before use.
     */
    private void restore(){
        long started=SystemClock.elapsedRealtime();
        ReaderDiagnosticLog.event("READER_STARTUP","edge_restore_start segments="+sentenceParts.size());
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        selectedOverrides.clear();selectedSeekWord=-1;
        for(SentenceChunks.Chunk part:sentenceParts){
            Clip clip=new Clip(part.fromWord,part.toWord,part.text,"");
            archive.add(clip);byStart.put(clip.start,clip);
        }
        ReaderDiagnosticLog.event("READER_STARTUP","edge_restore_end_ms="+
            (SystemClock.elapsedRealtime()-started)+" segments="+archive.size());
    }
    private String currentSharedChapter(int at){
        if(chapters==null||chapters.size()==0)return "";
        BookChapterIndex.Chapter chapter=chapters.atChar(charForWord(at));
        return chapter==null?"":chapter.title;
    }
    private void persist(){
        if(directory==null||destroyed)return;
        // Debounce user seeks, download progress and track transitions. The entire
        // sentence index can be large; don't serialize it on every tap.
        main.removeCallbacks(saveIndex);
        main.postDelayed(saveIndex,250);
    }
    private void persistNow(){
        if(directory==null)return;
        if(player!=null&&player.playRequested()&&bookReady)
            UnifiedReadingProgress.save(this,bookUri,currentSpeechWord(),offsets.size(),
                currentSharedChapter(currentSpeechWord()),true);
        JSONArray data=new JSONArray();
        try{for(Clip c:archive){
            if(c.key.isEmpty())continue; // unopened fragments need no MP3 manifest record
            JSONObject row=new JSONObject();row.put("start",c.start).put("end",c.end)
                .put("key",c.key).put("ready",c.ready()).put("queued",pendingStarts.contains(c.start))
                .put("status",c.ready()?"saved":(liveFor(c)!=null?"downloading":"missing"))
                .put("played",c.played);data.put(row);
        }}catch(Exception ex){Log.w("EdgeSentences","Cannot serialize index",ex);}
        final File folder=directory;final String json=data.toString();
        io.execute(()->{
            // Preserve previously downloaded MP3 index rows for unopened
            // fragments. Lazy restore must not erase older cache coverage.
            android.util.AtomicFile atomic=new android.util.AtomicFile(new File(folder,"sentences.json"));
            java.io.FileOutputStream output=null;
            try{
                JSONArray merged=new JSONArray(json);
                java.util.HashSet<String> seen=new java.util.HashSet<>();
                for(int i=0;i<merged.length();i++){
                    JSONObject row=merged.optJSONObject(i);
                    if(row!=null)seen.add(row.optInt("start")+":"+row.optInt("end")+":"+row.optString("key"));
                }
                File original=new File(folder,"sentences.json");
                if(original.isFile()&&original.length()>2&&original.length()<8*1024*1024L){
                    byte[] buffer=new byte[(int)original.length()];int at=0;
                    try(java.io.FileInputStream input=new java.io.FileInputStream(original)){
                        int n;while(at<buffer.length&&(n=input.read(buffer,at,buffer.length-at))>0)at+=n;
                    }
                    JSONArray previous=new JSONArray(new String(buffer,0,at,StandardCharsets.UTF_8));
                    for(int i=0;i<previous.length();i++){
                        JSONObject row=previous.optJSONObject(i);
                        if(row==null)continue;
                        String key=row.optString("key","");
                        String identity=row.optInt("start")+":"+row.optInt("end")+":"+key;
                        if(key.matches("[a-fA-F0-9]{64}")&&seen.add(identity))merged.put(row);
                    }
                }
                output=atomic.startWrite();
                output.write(merged.toString().getBytes(StandardCharsets.UTF_8));
                atomic.finishWrite(output);
            }catch(Exception ex){if(output!=null)atomic.failWrite(output);Log.w("EdgeSentences","Cannot persist index",ex);}
        });
    }
    @Override public void onDestroy(){
        if(player!=null){player.setResumeSelection(null);player.setSentenceNavigation(null);player.setPauseSelection(null);}
        main.removeCallbacks(idleExpiry);
        main.removeCallbacks(saveIndex);
        persistNow(); // Last selected sentence / interrupted work survives process shutdown.
        destroyed=true;dictionaryGeneration.incrementAndGet();dictionaryScan.shutdownNow();
        clipRestore.shutdown();chapterWriter.shutdown();main.removeCallbacks(refreshReady);
        EdgeSpeechQueue.setInteractivePlayback(false);
        cancelLiveExcept(null);
        liveJobs.clear();
        if(player!=null)player.removeListener(listener);
        try{unregisterReceiver(switchReceiver);}catch(Exception ignored){}
        try{unregisterReceiver(dictionaryReceiver);}catch(Exception ignored){}
        if(playerBound)unbindService(playbackConnection);
        io.shutdown();tailAnalysis.shutdown();super.onDestroy();
    }
}
