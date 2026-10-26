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

/** On-demand Edge speech coordinator: complete MP3 file cache -> Media3.
 * At most one active network synthesis. The next cached text group is prepared
 * during playback; no growing MP3 source or custom PCM decoder is involved.
 */
public final class EdgeStreamingService extends Service {
    public interface Observer {void onChanged();}
    public static final String ACTION_BEGIN="ru.reader.rsvp.edge.download.BEGIN";
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
    /** No background full-book queue during listening. At most the current live
     * request and one prefetched fragment are visible to the player. The next
     * request may begin only AFTER the selected stream has buffered initial audio. */
    private final LinkedHashMap<String,LiveJob> liveJobs=new LinkedHashMap<>();
    private static final class LiveJob {
        final Clip clip;
        final File folder;
        final String fileId,bookId;
        volatile boolean networkDone;
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
    private Clip selectedOverride;

    private final ArrayList<Observer> observers=new ArrayList<>();
    private String bookUri="",title="",bookText="",error="";
    private BookChapterIndex chapters=BookChapterIndex.empty();
    private File directory;
    private boolean bookReady,active,destroyed,playerBound;
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
                if(selectedOverride!=null&&start>=selectedOverride.end)selectedOverride=null;
                anchorWord=start;persist();pump();
            });
        }
        @Override public void onPlaybackStateChanged(){main.post(()->{if(bookReady)pump();});}
    };
    private final ServiceConnection playbackConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            player=((EdgePlaybackService.LocalBinder)b).getService();
            player.addListener(listener);
            if(!bookUri.isEmpty())player.prepareBook("edge:"+bookUri);
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
            String uri=intent.getStringExtra(EXTRA_URI);
            if(uri!=null&&!uri.isEmpty())openBook(uri,intent.getIntExtra(EXTRA_ANCHOR,0));
            active=true;pump();
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
    public int anchor(){return player!=null&&player.currentStartWord()>=0?player.currentStartWord():anchorWord;}
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
                    String key=EdgeBookStorage.key(part.text,voice,rate,"edge-direct-v1");
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
        if(selectedOverride!=null && (selectedOverride.key+":"+selectedOverride.start).equals(playing))
            return selectedOverride.copy();
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
        selectedOverride=null;selectedSeekWord=-1;
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
        selectedOverride=null;selectedSeekWord=-1;
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
                ArrayList<SentenceChunks.Chunk> newParts=EdgeSpeechSegments.split(raw);
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
                    else {SentenceChunks.Chunk initial=SentenceChunks.forWord(sentenceParts,anchorWord);
                        priorityStart=initial==null?-1:initial.fromWord;persist();}
                    pump();
                });
            }catch(Exception ex){main.post(()->{if(requested.equals(bookUri)){error="Ошибка чтения книги: "+ex.getMessage();changed();}});}
        });
    }
    public void refreshSettings(){pump();}
    public void ensurePlaySelection(){
        if(bookReady){Clip current=clipAt(anchorWord);
            if(current!=null&&player!=null&&player.playRequested()){
                // An explicit Play lets the user retry a previously failed stream.
                if(!current.ready() && (current.retryAt>0 ||
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
        if(enabled){pump();return;}
        // Leaving the TTS listener cancels network synthesis. Completed cache
        // commits are independent and continue on their own worker.
        cancelLiveExcept(null);
        changed();stopSelf();
    }
    private void cancelLiveExcept(Clip selected){
        for(LiveJob job:new ArrayList<>(liveJobs.values())){
            if(job.networkDone)continue; // complete cache must be committed
            if(selected!=null && job.clip==selected)continue;
            liveJobs.remove(job.fileId);
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
        selectedOverride=null;
        selectedSeekWord=requested;
        Clip target=base;
        if(requested>base.start){
            int relative=charForWord(requested)-charForWord(base.start);
            long position=base.ready()?
                EdgeSentenceSeek.at(relative,EdgeWordTimings.read(base.file,base.text)):-1L;
            if(position<0){
                // No trusted word timing: generate ONLY the remainder beginning
                // at the tapped sentence, never replay an earlier one.
                SentenceChunks.Chunk grouped=SentenceChunks.forWord(sentenceParts,requested);
                int from=charForWord(requested);
                if(grouped!=null&&from<grouped.toChar){
                    String remainder=bookText.substring(from,grouped.toChar);
                    String key=EdgeBookStorage.key(remainder,voice(),rate(),"edge-direct-v1");
                    target=new Clip(requested,base.end,remainder,key);
                    selectedOverride=target;
                }
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
    /** The normal 600-character group, without a temporary sentence override. */
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
    /** A manual sentence override is valid only until its group's end. */
    private Clip clipAt(int word){
        if(selectedOverride!=null && word>=selectedOverride.start && word<selectedOverride.end){
            if(!selectedOverride.ready()&&directory!=null){
                File cached=new File(directory,selectedOverride.key+".mp3");
                if(cached.isFile()&&cached.length()>1500){selectedOverride.file=cached;selectedOverride.bytes=cached.length();}
            }
            return selectedOverride;
        }
        return baseClipAt(word);
    }
    /** The on-demand engine streams encoded MP3 to Media3 immediately; the final
     * validated MP3 is committed to the existing persistent cache separately.
     */
    private void pump(){
        if(!bookReady||directory==null||destroyed||!active)return;
        Clip current=clipAt(anchorWord);
        if(current==null)return;
        Clip ahead=clipAt(current.end);
        if(player!=null){
            player.setAutoContinue(true);
            if(current.ready()){
                // Position is armed *before* appendReady can call Media3.play().
                if(selectedSeekWord>=0){
                    int relative=charForWord(selectedSeekWord)-charForWord(current.start);
                    long ms=EdgeSentenceSeek.at(relative,EdgeWordTimings.read(current.file,current.text));
                    if(ms<0)ms=0; // overrides start at the requested sentence
                    Log.i("EdgeSentenceSeek","word="+selectedSeekWord+
                        " clip="+current.start+" startMs="+ms+
                        (selectedOverride==current?" exact-fragment":" WordBoundary"));
                    player.armSelectedPosition(current.key+":"+current.start,ms);
                    selectedSeekWord=-1;
                }
                addTrack(current);
                if(ahead!=null)addTrack(ahead);
            }
            // Never enqueue a later cached fragment when the selected one is absent.
        }
        if(player==null||!player.playRequested()){changed();return;}
        if(EdgeSpeechQueue.remainingMs()>0){changed();return;}
        boolean busy=false;
        for(LiveJob j:liveJobs.values())if(!j.networkDone)busy=true;
        if(!busy&&!current.ready()&&liveFor(current)==null&&
                !current.awaitingUserRetry && current.retryAt<=SystemClock.elapsedRealtime())startLive(current);
        else if(!busy&&current.ready()&&ahead!=null&&!ahead.ready()&&liveFor(ahead)==null&&
                EdgeSpeechQueue.allowPrefetch() && nearEndOfCurrentAudio(current) &&
                !ahead.awaitingUserRetry && ahead.retryAt<=SystemClock.elapsedRealtime())startLive(ahead);
        changed();
    }
    /** Restrict speculative work until the listener is approaching the end.
     * Reduced background traffic helps avoid Edge's occasional rate limits. */
    private boolean nearEndOfCurrentAudio(Clip clip){
        if(player==null||!player.isPlaying()||clip==null)return false;
        if(!(clip.key+":"+clip.start).equals(player.currentKey()))return false;
        long duration=player.durationMs();
        return duration>0 && duration-player.positionMs()<=12000L;
    }
    private void addTrack(Clip clip){
        if(player!=null&&clip!=null&&clip.ready())
            player.appendReady(clip.key+":"+clip.start,clip.start,clip.end,clip.file);
    }
    private LiveJob liveFor(Clip clip){
        if(clip==null||directory==null)return null;
        return liveJobs.get(new File(directory,clip.key+".mp3").getAbsolutePath());
    }
    /** No growing audio source and no intermediate Android PCM pipeline.
     * The cached MP3 is published atomically before Media3 can play it.
     */
    private void startLive(Clip clip){
        if(clip==null||clip.ready()||directory==null||liveFor(clip)!=null)return;
        final LiveJob job=new LiveJob(clip,directory,bookUri);
        final boolean foreground=(clip==clipAt(anchorWord));
        if(foreground && player!=null){
            job.buffer=new EdgeLiveBuffer();
            // Bind Media3 to streaming bytes BEFORE starting network synthesis.
            // The first MP3 frame can begin decoding without waiting for turn.end.
            player.appendLive(clip.key+":"+clip.start,clip.start,clip.end,job.buffer);
        }
        liveJobs.put(job.fileId,job);
        error="";changed();
        job.request=EdgeTtsClient.synthesize(clip.text,voice(),rate(),
            foreground?EdgeSpeechQueue.PLAY:EdgeSpeechQueue.PREFETCH,
            new EdgeTtsClient.Callback(){
                @Override public void progress(int count,long bytes){}
                @Override public void audio(byte[] packet,int start,int length){
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
                        });
                        return;
                    }
                    if(job.buffer!=null)job.buffer.complete();
                    io.execute(()->{
                        File saved=null;String failed=null;
                        try{saved=EdgeSpeechFiles.save(job.folder,clip.key,clip.text,mp3,metadata);}
                        catch(Exception e){failed=e.getMessage();}
                        final File ready=saved;final String cacheError=failed;
                        main.post(()->{
                            liveJobs.remove(job.fileId);
                            if(!job.bookId.equals(bookUri))return;
                            if(cacheError==null){
                                getSharedPreferences("reader",0).edit().remove(
                                    "edge_v2_retry_after_epoch_ms").apply();
                                clip.file=ready;clip.bytes=ready.length();clip.chunks=parts;
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
                        });
                    });
                }
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
        active=false;cancelLiveExcept(null);
        for(LiveJob job:new ArrayList<>(liveJobs.values())){
            if(job.request!=null)job.request.cancel();
        }
        liveJobs.clear();
        if(player!=null)player.clearQueue();
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        selectedOverride=null;selectedSeekWord=-1;File folder=directory;
        io.execute(()->{if(folder!=null){File[] children=folder.listFiles();if(children!=null)for(File f:children)f.delete();}
            main.post(()->{restore();persist();changed();if(done!=null)done.run();});});
    }
    /** Reconstruct the COMPLETE ordered sentence catalogue for this voice.
     * The manifest includes downloaded and missing sentences, so jumping over gaps
     * cannot silently redirect the player to the last ready sentence. */
    private void restore(){
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        selectedOverride=null;selectedSeekWord=-1;
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
            String key=EdgeBookStorage.key(part.text,selectedVoice,selectedRate,"edge-direct-v1");
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
        cancelLiveExcept(null);
        liveJobs.clear();
        if(player!=null)player.removeListener(listener);
        try{unregisterReceiver(switchReceiver);}catch(Exception ignored){}
        if(playerBound)unbindService(playbackConnection);
        io.shutdown();tailAnalysis.shutdown();super.onDestroy();
    }
}
