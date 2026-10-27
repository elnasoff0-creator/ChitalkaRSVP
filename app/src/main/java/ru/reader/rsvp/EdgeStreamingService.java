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

/** Serial Edge speech coordinator: stream the selected clip to Media3.
 * Do not synthesize the following 800-character clip in the background.
 * Request it only after playback reports that the current clip has ended.
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
    /** Only the selected clip can be synthesized during playback. No look-ahead. */
    private final LinkedHashMap<String,LiveJob> liveJobs=new LinkedHashMap<>();
    private static final class LiveJob {
        final Clip clip;
        final File folder;
        final String fileId,bookId;
        volatile boolean networkDone;
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
    private final ArrayList<Clip> selectedOverrides=new ArrayList<>();

    private final ArrayList<Observer> observers=new ArrayList<>();
    private String bookUri="",title="",bookText="",error="";
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
        public final String text,key;
        public File file;
        public long bytes;
        public int chunks,failures;
        public long retryAt;
        public boolean played;
        public boolean awaitingUserRetry;
        Clip(int start,int end,String text,String key){this.start=start;this.end=end;this.text=text;this.key=key;}
        public boolean ready(){return file!=null&&file.isFile()&&file.length()>1500;}
        Clip copy(){Clip c=new Clip(start,end,text,key);c.file=file;c.bytes=bytes;c.chunks=chunks;c.played=played;c.failures=failures;c.awaitingUserRetry=awaitingUserRetry;return c;}
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
        if(player==null||!player.playRequested()){
            cancelLiveExcept(null);stopSelf();
        }
        return super.onUnbind(intent);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && ACTION_BEGIN.equals(intent.getAction())){
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
            final ArrayList<SentenceChunks.Chunk> snapshot=new ArrayList<>(sentenceParts);
            final String voice=voice(),rate=rate(),uri=bookUri;
            final File folder=directory;
            final int total=offsets.size();
            io.execute(()->{
                int saved=0;
                for(SentenceChunks.Chunk part:snapshot){
                    String key=EdgeAudioManager.key(part.text,voice,rate);
                    File file=new File(folder,key+".mp3");
                    if(file.isFile()&&file.length()>1500)saved+=part.toWord-part.fromWord;
                }
                final int percent=total<=0?0:Math.min(100,(int)(100L*saved/total));
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
        // Every voice has separate MP3 keys; rebuild the index without deleting older audio.
        if(bookReady){restore();priorityStart=SentenceChunks.forWord(sentenceParts,anchorWord)==null?-1:
                SentenceChunks.forWord(sentenceParts,anchorWord).fromWord;persist();}
        pump();
    }
    public void openBook(String uri,int initialWord){
        if(uri==null||uri.isEmpty())return;
        if(uri.equals(bookUri)){if(player==null||player.currentStartWord()<0)anchorWord=Math.max(0,initialWord);pump();return;}
        // Flush the previous book's pending index before switching its directory.
        main.removeCallbacks(saveIndex);persistNow();
        cancelLiveExcept(null);liveJobs.clear();bookUri=uri;bookReady=false;anchorWord=Math.max(0,initialWord);
        cachedSavedPercent=0;lastCoverageCheck=0;
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        selectedOverrides.clear();selectedSeekWord=-1;
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
                ArrayList<SentenceChunks.Chunk> newParts=EdgeSpeechSegments.split(raw,parsedChapters);
                File bookDir=EdgeBookStorage.bookFolder(this,requested,name);
                File dir=new File(bookDir,"EdgeTTS");dir.mkdirs();
                DownloadsRepository.storeEdgeMeta(bookDir,requested,name,positions.size());
                main.post(()->{
                    if(destroyed||!requested.equals(bookUri))return;
                    bookText=raw;title=name;chapters=parsedChapters;offsets.addAll(positions);sentenceParts.addAll(newParts);directory=dir;
                    bookReady=!offsets.isEmpty();restore();
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
                    if(!bookReady)error="Книга не содержит текста";
                    else {
                        // Startup restoration is not a tap: do not clear an active
                        // prefetch or request 500-char priority pieces automatically.
                        restoreStartPosition();
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
        Clip start=new Clip(anchorWord,group.toWord,remaining,
                EdgeAudioManager.key(remaining,voice(),rate()));
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
    public void refreshSettings(){pump();}
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
                    long seconds=(EdgeSpeechQueue.remainingMs()+999)/1000;
                    error="Edge TTS ограничил запросы. Повтор не раньше чем через "+seconds+" с";
                }
                pump();
            }
        }
    }
    public void setActive(boolean enabled){
        active=enabled;
        if(enabled){warmCurrent();pump();return;}
        EdgeSpeechQueue.setInteractivePlayback(false);
        // Leaving the TTS listener cancels network synthesis. Completed cache
        // commits are independent and continue on their own worker.
        cancelLiveExcept(null);
        idlePrefetchRequested=false;
        changed();stopSelf();
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
        if(!bookReady){anchorWord=Math.max(0,word);return;}
        int requested=Math.max(0,Math.min(offsets.size()-1,word));
        if(!selected){anchorWord=requested;persist();pump();return;}
        Clip base=baseClipAt(requested);
        if(base==null)return;
        // Reset the playlist first, before any new file can begin playing.
        if(player!=null)player.clearTracksForSelection();
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
                    String key=EdgeAudioManager.key(piece.text,voice(),rate());
                    Clip clip=new Clip(piece.fromWord,piece.toWord,piece.text,key);
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
        if(clip!=null&&!clip.ready()&&directory!=null){
            File onDisk=new File(directory,clip.key+".mp3");
            if(onDisk.isFile()&&onDisk.length()>1500){
                clip.file=onDisk;clip.bytes=onDisk.length();pendingStarts.remove(clip.start);
                persist();
            }
        }
        return clip;
    }
    /** Manual priority pieces cover the remainder of the selected normal group. */
    private Clip clipAt(int word){
        for(Clip override:selectedOverrides)if(word>=override.start&&word<override.end){
            if(!override.ready()&&directory!=null){
                File cached=new File(directory,override.key+".mp3");
                if(cached.isFile()&&cached.length()>1500){override.file=cached;override.bytes=cached.length();}
            }
            return override;
        }
        return baseClipAt(word);
    }
    /** The on-demand engine streams encoded MP3 to Media3 immediately; the final
     * validated MP3 is committed to the existing persistent cache separately.
     */
    private void pump(){
        EdgeSpeechQueue.setInteractivePlayback(active && player!=null && player.playRequested());
        if(!bookReady||directory==null||destroyed||!active)return;
        Clip current=clipAt(anchorWord);
        if(current==null)return;
        Clip ahead=clipAt(current.end);
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
        boolean busy=false;
        for(LiveJob j:liveJobs.values())if(!j.networkDone)busy=true;
        if(!busy&&!current.ready()&&liveFor(current)==null&&
                !current.awaitingUserRetry && current.retryAt<=SystemClock.elapsedRealtime())startLive(current);
        // Intentionally no speculative synthesis for `ahead`. Only onTrackFinished
        // can move anchorWord to the next uncached 800-character block.
        changed();
    }
    private void addTrack(Clip clip){
        if(player!=null&&clip!=null&&clip.ready())
            player.appendReady(clip.key+":"+clip.start,clip.start,clip.end,clip.file);
    }
    private LiveJob liveFor(Clip clip){
        if(clip==null||directory==null)return null;
        return liveJobs.get(new File(directory,clip.key+".mp3").getAbsolutePath());
    }
    /** Play from a growing encoded MP3 buffer; cache a validated complete file
     * independently after turn.end. The next clip is not synthesized in advance.
     */
    private void startLive(Clip clip){startLive(clip,false);}
    private void startLive(Clip clip,boolean warmup){
        if(clip==null||clip.ready()||directory==null||liveFor(clip)!=null)return;
        final LiveJob job=new LiveJob(clip,directory,bookUri);
        final boolean foreground=!warmup&&(clip==clipAt(anchorWord));
        if(foreground||warmup){
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
        job.request=EdgeTtsClient.synthesize(clip.text,voice(),rate(),
            foreground?EdgeSpeechQueue.PLAY:EdgeSpeechQueue.PREFETCH,
            new EdgeTtsClient.Callback(){
                @Override public void progress(int count,long bytes){}
                @Override public void connected(){EdgeTtsDiagnostics.connected(diagnosticKey);}
                @Override public void audio(byte[] packet,int start,int length){
                    EdgeTtsDiagnostics.firstMp3(diagnosticKey,length);
                    if(job.buffer!=null)try{job.buffer.append(packet,start,length);}
                    catch(java.io.IOException e){Log.w("EdgeSpeech","Live buffer rejected MP3",e);}
                }
                @Override public void metadata(List<EdgeWordTimings.Boundary> marks){
                    job.timings=EdgeWordTimings.align(clip.text,marks);
                }
                @Override public void finished(byte[] mp3,int parts,String problem){
                    finished(mp3,parts,problem,Collections.emptyList());
                }
                @Override public void finished(byte[] mp3,int parts,String problem,
                        List<EdgeWordTimings.Boundary> metadata){
                    job.networkDone=true;
                    EdgeTtsDiagnostics.finished(diagnosticKey,problem,
                        job.timings==null?0:job.timings.size());
                    if(problem!=null){
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
                            error="Edge TTS: "+problem;
                            persist();changed();
                            if(idlePrefetchRequested&&!active){idlePrefetchRequested=false;stopSelf();}
                        });
                        return;
                    }
                    if(job.buffer!=null)job.buffer.complete();
                    io.execute(()->{
                        File saved=null;String failed=null;
                        try{saved=EdgeSpeechFiles.save(job.folder,clip.key,clip.text,mp3,metadata);if(saved!=null)EdgeAudioManager.invalidate(job.folder);}
                        catch(Exception e){failed=e.getMessage();}
                        final File ready=saved;final String cacheError=failed;
                        main.post(()->{
                            liveJobs.remove(job.fileId);
                            if(!job.bookId.equals(bookUri))return;
                            if(cacheError==null){
                                getSharedPreferences("reader",0).edit().remove(
                                    "edge_v2_retry_after_epoch_ms").apply();
                                clip.file=ready;clip.bytes=ready.length();clip.chunks=parts;
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
        selectedOverrides.clear();selectedSeekWord=-1;File folder=directory;
        io.execute(()->{if(folder!=null){File[] children=folder.listFiles();if(children!=null)for(File f:children)f.delete();}
            main.post(()->{restore();persist();changed();if(done!=null)done.run();});});
    }
    /** Reconstruct the COMPLETE ordered sentence catalogue for this voice.
     * The manifest includes downloaded and missing sentences, so jumping over gaps
     * cannot silently redirect the player to the last ready sentence. */
    private void restore(){
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        selectedOverrides.clear();selectedSeekWord=-1;
        if(directory==null)return;
        Map<Integer,JSONObject> oldRows=new HashMap<>();
        try{
            File f=new File(directory,"sentences.json");
            if(!f.isFile())f=new File(directory,"queue.json");
            byte[] input=new byte[f.isFile()?(int)Math.min(f.length(),8*1024*1024L):0];int total=0;
            if(f.isFile())try(java.io.FileInputStream in=new java.io.FileInputStream(f)){
                int count;while(total<input.length&&(count=in.read(input,total,input.length-total))>0)total+=count;
            }
            JSONArray saved=total>0?new JSONArray(new String(input,0,total,StandardCharsets.UTF_8)):new JSONArray();
            for(int i=0;i<saved.length();i++){
                JSONObject row=saved.optJSONObject(i);
                if(row!=null)oldRows.put(row.optInt("start",-1),row);
            }
        }catch(Exception ex){Log.w("EdgeSentences","Cannot restore older index",ex);}
        String selectedVoice=voice(),selectedRate=rate();
        for(SentenceChunks.Chunk part:sentenceParts){
            String key=EdgeAudioManager.key(part.text,selectedVoice,selectedRate);
            Clip c=new Clip(part.fromWord,part.toWord,part.text,key);
            File audio=new File(directory,key+".mp3");
            if(audio.isFile()&&audio.length()>1500){c.file=audio;c.bytes=audio.length();}
            JSONObject old=oldRows.get(c.start);
            if(old!=null&&key.equals(old.optString("key",""))&&c.end==old.optInt("end",-1)){
                c.played=old.optBoolean("played",false);
                // Previously interrupted background requests are intentionally ignored.
            }
            archive.add(c);byStart.put(c.start,c);
        }
        // Readying a previously downloaded sentence is a direct file lookup;
        // old MP3 files for other voices or old chunk sizes are never deleted.
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
            JSONObject row=new JSONObject();row.put("start",c.start).put("end",c.end)
                .put("key",c.key).put("ready",c.ready()).put("queued",pendingStarts.contains(c.start))
                .put("status",c.ready()?"saved":(liveFor(c)!=null?"downloading":"missing"))
                .put("played",c.played);data.put(row);
        }}catch(Exception ex){Log.w("EdgeSentences","Cannot serialize index",ex);}
        final File folder=directory;final String json=data.toString();
        io.execute(()->{
            android.util.AtomicFile atomic=new android.util.AtomicFile(new File(folder,"sentences.json"));
            java.io.FileOutputStream output=null;
            try{output=atomic.startWrite();output.write(json.getBytes(StandardCharsets.UTF_8));atomic.finishWrite(output);}
            catch(Exception ex){if(output!=null)atomic.failWrite(output);Log.w("EdgeSentences","Cannot persist index",ex);}
        });
    }
    @Override public void onDestroy(){
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
