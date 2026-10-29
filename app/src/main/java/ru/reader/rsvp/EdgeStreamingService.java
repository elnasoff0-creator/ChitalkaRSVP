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
    public static final String ACTION_PREFETCH="ru.reader.rsvp.edge.download.PREFETCH";
    public static final String EXTRA_URI="book_uri";
    public static final String EXTRA_ANCHOR="anchor_word";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    // MP3 decode must never hold the queue responsible for producing playable files.
    private final ExecutorService tailAnalysis=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"EdgeTrailingSilence");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final Runnable saveIndex=this::persistNow;
    private final ArrayList<Integer> offsets=new ArrayList<>();
    private final ArrayList<SentenceChunks.Chunk> sentenceParts=new ArrayList<>();
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
    private final Runnable idleExpiry=()->{
        if(!active && (player==null||!player.playRequested()))stopSelf();
    };
    private static final long IDLE_REUSE_MS=120000L;
    private void invalidateSpeech(){
        speechOptions=null;speechRevision="";preparedSpeech.clear();
    }
    private String revision(){
        if(speechOptions==null){
            speechOptions=EdgeSpeechTextTransform.Options.read(getSharedPreferences("reader",0));
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
    // A tap may arrive before the asynchronous chapter index finishes loading.
    private int deferredSelectedWord=-1;
    private final ArrayList<Clip> selectedOverrides=new ArrayList<>();

    private final ArrayList<Observer> observers=new ArrayList<>();
    private String bookUri="",title="",bookText="",error="";
    // Set before failing a live buffer so Media3's generic I/O error cannot
    // accidentally surface the same HTTP 429 as a user-visible Toast.
    private volatile boolean lastHttp429;
    private BookChapterIndex chapters=BookChapterIndex.empty();
    private File directory;
    private boolean bookReady,active,destroyed,playerBound,idlePrefetchRequested;
    private int anchorWord;
    private EdgePlaybackService player;
    private final Runnable refreshReady=new Runnable(){@Override public void run(){
        if(destroyed)return;
        if(bookReady&&directory!=null&&active)pump(); // ready offline files only
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
                anchorWord=start;persist();pump();
            });
        }
        /** A playlist with no queued next file reaches STATE_ENDED. Continue
         * from the completed clip's exclusive end word, never replay it. */
        @Override public void onTrackFinished(int start,int end,String key){
            main.post(()->{
                if(!bookReady||player==null||!player.playRequested()||
                        !key.equals(player.currentKey()))return;
                Clip ended=clipAt(start);
                if(ended==null||ended.end!=end)return;
                ended.played=true;
                EdgeTtsDiagnostics.trackEnded(key);
                selectedOverrides.removeIf(c->end>=c.end);
                selectedSeekWord=-1;
                anchorWord=end;
                persist();
                if(end>=offsets.size()||player.shouldStopAfterWord(end)){
                    player.pause();changed();return;
                }
                // The engine's network arbiter holds the next handshake until
                // the previous WebSocket has closed, even after a user cancel.
                pump();
            });
        }
        @Override public void onPlaybackStateChanged(){main.post(()->{
            EdgeSpeechQueue.setInteractivePlayback(active && player!=null && player.playRequested());
            if(bookReady)pump();
        });}
    };
    private final ServiceConnection playbackConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            player=((EdgePlaybackService.LocalBinder)b).getService();
            player.addListener(listener);
            if(!bookUri.isEmpty())player.prepareBook("edge:"+bookUri);
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
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public boolean onUnbind(Intent intent){
        // A mode switch unbinds ReaderActivity, but should not destroy the
        // prepared book index. The idle lease is capped to avoid memory leaks.
        if(!active)main.postDelayed(idleExpiry,IDLE_REUSE_MS);
        return super.onUnbind(intent);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && ACTION_BEGIN.equals(intent.getAction())){
            main.removeCallbacks(idleExpiry);
            if(!active){trackReadyLoggedAt=-1L;restoreStartedAt=SystemClock.elapsedRealtime();}
            idlePrefetchRequested=false;
            String uri=intent.getStringExtra(EXTRA_URI);
            if(uri!=null&&!uri.isEmpty())openBook(uri,intent.getIntExtra(EXTRA_ANCHOR,0));
            active=true;
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
        selectedOverrides.clear();selectedSeekWord=-1;
        if(player!=null)player.clearQueue();
        // Keep the lightweight index. Only requested fragments acquire new voice keys.
        invalidateSpeech();
        priorityStart=-1;
        pump();
    }
    public void openBook(String uri,int initialWord){
        if(uri==null||uri.isEmpty())return;
        if(uri.equals(bookUri)){
            ReaderDiagnosticLog.event("READER_STARTUP","edge_book_reuse ready="+bookReady);
            // Do not overwrite an explicit selection with a stale activity bind.
            if(deferredSelectedWord<0 && selectedSeekWord<0 &&
                    (player==null||player.currentStartWord()<0))anchorWord=Math.max(0,initialWord);
            pump();return;
        }
        // Flush the previous book's pending index before switching its directory.
        main.removeCallbacks(saveIndex);persistNow();
        cancelLiveExcept(null);liveJobs.clear();bookUri=uri;bookReady=false;anchorWord=Math.max(0,initialWord);
        invalidateSpeech();trackReadyLoggedAt=-1L;restoreStartedAt=SystemClock.elapsedRealtime();
        cachedSavedPercent=0;lastCoverageCheck=0;
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        selectedOverrides.clear();selectedSeekWord=-1;deferredSelectedWord=-1;
        prefetchForStart=-1;prefetchIssued=0;
        offsets.clear();sentenceParts.clear();bookText="";chapters=BookChapterIndex.empty();directory=null;error="";
        if(player!=null)player.prepareBook("edge:"+uri);
        changed();
        final String requested=uri;
        io.execute(()->{
            try{
                UnifiedBookSession session=UnifiedBookSession.open(this,Uri.parse(requested));
                BookLoader.Book book=session.book;
                String raw=book.text==null?"":book.text;
                String name=book.title==null?"Книга":book.title;
                BookChapterIndex parsedChapters=book.chapters;
                ArrayList<Integer> positions=session.ttsWordPositions;
                // Reuse the same immutable source text/word index from ReaderActivity;
                // grouping is structural only and never applies dictionary rules.
                ArrayList<SentenceChunks.Chunk> newParts=session.edgeGroups();
                File bookDir=EdgeBookStorage.bookFolder(this,requested,name);
                File dir=new File(bookDir,"EdgeTTS");dir.mkdirs();
                DownloadsRepository.storeEdgeMeta(bookDir,requested,name,positions.size());
                main.post(()->{
                    if(destroyed||!requested.equals(bookUri))return;
                    bookText=raw;title=name;chapters=parsedChapters;offsets.addAll(positions);sentenceParts.addAll(newParts);directory=dir;
                    bookReady=!offsets.isEmpty();restore();
                    // Recovery is pure disk I/O. It must never delay the first frame.
                    io.execute(()->{
                      for(File pending:EdgeStreamCache.recover(dir)){
                        File meta=new File(pending.getParentFile(),pending.getName().replace(".ready",".json"));
                        EdgeCacheCommitter.submit(this,pending,meta,dir,
                            getSharedPreferences("reader",0).getBoolean("edge_trim_sentence_pause",true),
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
     * just the remainder of the normal <=800-char group, as ONE request.
     * On Play the same warm buffer is reused instead of a second handshake.
     */
    private void restoreStartPosition(){
        selectedOverrides.clear();
        selectedSeekWord=-1;
        Clip base=baseClipAt(anchorWord);
        if(base==null || anchorWord<=base.start)return;
        int relative=charForWord(anchorWord)-charForWord(base.start);
        if(base.ready() && EdgeSentenceSeek.at(relative,
                EdgeWordTimings.read(base.file,base.text))>=0){
            selectedSeekWord=anchorWord;
            return;
        }
        int begin=charForWord(anchorWord);
        SentenceChunks.Chunk group=SentenceChunks.forWord(sentenceParts,anchorWord);
        if(group==null || begin>=group.toChar)return;
        String remaining=bookText.substring(begin,group.toChar);
        Clip start=new Clip(anchorWord,group.toWord,remaining,"");
        selectedOverrides.add(start);
    }

    /** One silent, low-priority current-block request. Never synthesize
     * an already cached file and never run a speculative queue of chapters.
     * A subsequent Play can attach to the very same buffered live MP3.
     */
    private void warmCurrent(){
        if(!bookReady||directory==null)return;
        if(!idlePrefetchRequested && !(active && player!=null&&!player.playRequested()))return;
        if(active&&player!=null&&player.playRequested())return;
        Clip target=clipAt(anchorWord);
        if(target!=null&&shouldIgnore(target))return; // no warmup WebSocket
        // Never retry a failed warmup automatically. Only an explicit Play
        // or a manual selection can permit another synthesis attempt.
        if(target==null||target.ready()||target.awaitingUserRetry||
                target.retryAt>SystemClock.elapsedRealtime()||
                EdgeSpeechQueue.remainingMs()>0||!EdgeSpeechQueue.allowPrefetch()){
            if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
            return;
        }
        if(liveFor(target)!=null)return;
        startLive(target,true);
    }
    public void refreshSettings(){
        // A different pronunciation rule changes both the transmitted text
        // and the MP3 identity. Rebuild the catalogue without deleting old files.
        String requested=EdgeSpeechTextTransform.Options.read(getSharedPreferences("reader",0)).fingerprint()
            +"|"+voice()+"|"+rate();
        if(speechOptions!=null&&!requested.equals(speechRevision)){
            choose(null,null);return;
        }
        pump();
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
    public void setAnchor(int word,boolean selected){
        if(!bookReady){
            anchorWord=Math.max(0,word);
            if(selected)deferredSelectedWord=anchorWord;
            return;
        }
        int requested=Math.max(0,Math.min(offsets.size()-1,word));
        if(selected)ReaderDiagnosticLog.event("NAVIGATION","PLAY_TARGET selected_word="+requested+" source=manual");
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
        selectedSeekWord=requested;
        Clip target=base;
        int relative=charForWord(requested)-charForWord(base.start);
        long position=base.ready()?
            EdgeSentenceSeek.at(relative,EdgeWordTimings.read(base.file,base.text)):-1L;
        boolean exactLive=requested==base.start&&liveFor(base)!=null;
        if(position<0&&!exactLive){
            // When jumping to an uncached position, start with up to 500
            // characters instead of waiting for the normal 800-char group.
            // Additional pieces cover the rest of the same normal group;
            // this prevents replaying or skipping text on the next track.
            SentenceChunks.Chunk grouped=SentenceChunks.forWord(sentenceParts,requested);
            if(grouped!=null){
                for(SentenceChunks.Chunk piece:EdgeSpeechSegments.priorityPieces(
                        bookText,offsets,requested,grouped.toWord,grouped.toChar)){
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
    /** The normal 800-character group, without temporary priority overrides. */
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
        EdgeSpeechQueue.setInteractivePlayback(active && player!=null && player.playRequested());
        if(!bookReady||directory==null||destroyed||!active)return;
        Clip current=skipIgnored(clipAt(anchorWord));
        if(current==null)return;
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
                firstTrackReady(current,"cache");
                // Position is armed *before* appendReady can call Media3.play().
                if(selectedSeekWord>=0){
                    int relative=charForWord(selectedSeekWord)-charForWord(current.start);
                    long ms=EdgeSentenceSeek.at(relative,EdgeWordTimings.read(current.file,current.text));
                    if(ms<0)ms=0; // overrides start at the requested sentence
                    Log.i("EdgeSentenceSeek","word="+selectedSeekWord+
                        " clip="+current.start+" startMs="+ms+
                        (selectedOverrides.contains(current)?" exact-fragment":" WordBoundary"));
                    player.armSelectedPosition(current.key+":"+current.start,ms);
                    selectedSeekWord=-1;
                }
                addTrack(current);
                if(ahead!=null && ahead.ready())addTrack(ahead); // disk only, no network
            }
            // Never enqueue a later cached fragment when the selected one is absent.
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
        if(clip==null||clip.ready()||directory==null||liveFor(clip)!=null||shouldIgnore(clip))return;
        ReaderDiagnosticLog.event("CACHE","cache_miss schedule start="+clip.start+" end="+clip.end+
            " warmup="+warmup+" ahead="+ahead);
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
            // A priority clip starts at the tapped word: no delayed seek is needed.
            if(selectedSeekWord==clip.start && selectedOverrides.contains(clip))
                selectedSeekWord=-1;
            player.appendLive(clip.key+":"+clip.start,clip.start,clip.end,job.buffer);
        }
        liveJobs.put(job.fileId,job);
        final String diagnosticKey=clip.key+":"+clip.start;
        EdgeTtsDiagnostics.requested(diagnosticKey,clip.text.length(),warmup);
        error="";changed();
        job.request=EdgeTtsClient.synthesize(job.speech.spoken,voice(),rate(),
            foreground?EdgeSpeechQueue.PLAY:EdgeSpeechQueue.PREFETCH,
            new EdgeTtsClient.Callback(){
                @Override public void progress(int count,long bytes){}
                @Override public void connected(){EdgeTtsDiagnostics.connected(diagnosticKey);}
                @Override public void audio(byte[] packet,int start,int length){
                    EdgeTtsDiagnostics.firstMp3(diagnosticKey,length);
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
                        if(problem.contains("HTTP 429"))lastHttp429=true;
                        if(job.buffer!=null)job.buffer.fail(new java.io.IOException(problem));
                        main.post(()->{
                            liveJobs.remove(job.fileId);
                            if(player!=null && job.buffer!=null)
                                player.evictFailedLive(clip.key+":"+clip.start);
                            if(!job.bookId.equals(bookUri))return;
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
                            if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
                        });
                        return;
                    }
                    if(job.buffer!=null)job.buffer.complete();
                    io.execute(()->{
                        File saved=null;String failed=null;
                        try{saved=EdgeSpeechFiles.saveMapped(job.folder,clip.key,clip.text,mp3,metadata,job.speech.aligned(metadata));if(saved!=null)EdgeAudioManager.invalidate(job.folder);}
                        catch(Exception e){failed=e.getMessage();}
                        final File ready=saved;final String cacheError=failed;
                        main.post(()->{
                            liveJobs.remove(job.fileId);
                            if(!job.bookId.equals(bookUri))return;
                            if(cacheError==null){
                                getSharedPreferences("reader",0).edit().remove(
                                    "edge_v2_retry_after_epoch_ms").apply();
                                clip.file=ready;clip.bytes=ready.length();clip.chunks=parts;
                                ReaderDiagnosticLog.event("CACHE","saved mp3 bytes="+clip.bytes+
                                    " start="+clip.start+" end="+clip.end);
                                SentenceChunks.Chunk basePart=SentenceChunks.forWord(sentenceParts,clip.start);
                                if(basePart!=null&&(clip.start!=basePart.fromWord||clip.end!=basePart.toWord))
                                    saveOverride(clip);
                                clip.failures=0;clip.retryAt=0;clip.awaitingUserRetry=false;
                                // Analyze trailing silence after the MP3 is already playable.
                                if(getSharedPreferences("reader",0).getBoolean("edge_trim_sentence_pause",true))
                                    tailAnalysis.execute(()->{
                                        try{EdgeTrailingSilence.detectAndStore(ready,clip.text);}
                                        catch(Exception ex){Log.w("EdgeSpeech","Tail detection skipped",ex);}
                                    });
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
    private void persist(){
        if(directory==null||destroyed)return;
        // Debounce user seeks, download progress and track transitions. The entire
        // sentence index can be large; don't serialize it on every tap.
        main.removeCallbacks(saveIndex);
        main.postDelayed(saveIndex,250);
    }
    private void persistNow(){
        if(directory==null)return;
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
        main.removeCallbacks(idleExpiry);
        main.removeCallbacks(saveIndex);
        persistNow(); // Last selected sentence / interrupted work survives process shutdown.
        destroyed=true;main.removeCallbacks(refreshReady);
        EdgeSpeechQueue.setInteractivePlayback(false);
        cancelLiveExcept(null);
        liveJobs.clear();
        if(player!=null)player.removeListener(listener);
        try{unregisterReceiver(switchReceiver);}catch(Exception ignored){}
        if(playerBound)unbindService(playbackConnection);
        io.shutdown();tailAnalysis.shutdown();super.onDestroy();
    }
}
