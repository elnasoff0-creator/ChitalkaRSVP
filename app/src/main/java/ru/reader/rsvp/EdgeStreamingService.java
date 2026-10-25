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

/** Lightweight streaming coordinator for a single active TTS player.
 * Only the selected sentence and one lookahead are synthesized.
 * No foreground download notification or whole-book queue in this service.
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
        final EdgeLiveAudio.Buffer audio=new EdgeLiveAudio.Buffer();
        final EdgeStreamCache.Entry cache;
        volatile List<EdgeWordTimings.Word> words=Collections.emptyList();
        volatile boolean networkDone,successful,firstAudio;
        int receivedBytes;
        EdgeTtsClient request;
        LiveJob(Clip clip,File folder,String bookId){
            this.clip=clip;this.folder=folder;this.bookId=bookId;
            fileId=new File(folder,clip.key+".mp3").getAbsolutePath();
            cache=EdgeStreamCache.create(folder,clip.key,clip.text);
        }
    }
    // Previous foreground download requests are not resumed during listening.
    private final LinkedHashSet<Integer> pendingStarts=new LinkedHashSet<>();
    private int priorityStart=-1;
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
        Clip(int start,int end,String text,String key){this.start=start;this.end=end;this.text=text;this.key=key;}
        public boolean ready(){return file!=null&&file.isFile()&&file.length()>1500;}
        Clip copy(){Clip c=new Clip(start,end,text,key);c.file=file;c.bytes=bytes;c.chunks=chunks;c.played=played;c.failures=failures;return c;}
    }
    public final class LocalBinder extends Binder { public EdgeStreamingService getService(){return EdgeStreamingService.this;} }
    private final IBinder binder=new LocalBinder();
    private final EdgePlaybackService.Listener listener=new EdgePlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean completedPrevious){
            main.post(()->{if(!bookReady)return;
                if(completedPrevious)for(Clip c:archive)if(c.end==start)c.played=true;
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
        offsets.clear();sentenceParts.clear();bookText="";chapters=BookChapterIndex.empty();directory=null;error="";
        if(player!=null)player.prepareBook("edge:"+uri);
        changed();
        final String requested=uri;
        io.execute(()->{
            try{
                BookLoader.Book book=BookLoader.load(this,Uri.parse(requested));
                String raw=book.text==null?"":book.text;
                String name=book.title==null?"Книга":book.title;
                BookChapterIndex parsedChapters=book.chapters;
                ArrayList<Integer> positions=EdgeTextSegments.positions(raw);
                ArrayList<SentenceChunks.Chunk> newParts=SentenceChunks.split(raw);
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
                current.retryAt=0;
                error="";
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
            if(job.request!=null)job.request.cancel();
            job.audio.finish("Selection changed");
            job.cache.cancel(); // never save an interrupted MP3
        }
    }
    public void setAnchor(int word,boolean selected){
        if(!bookReady){anchorWord=Math.max(0,word);return;}
        int requested=Math.max(0,Math.min(offsets.size()-1,word));
        Clip target=clipAt(requested);
        if(target==null)return;
        if(selected){
            target.retryAt=0; // explicit selection can retry a past failure
            // Jumping means abandoning both current and prefetched INCOMPLETE
            // streams. Do not queue, retry or save either truncated recording.
            cancelLiveExcept(target);
            pendingStarts.clear();priorityStart=-1;
            if(player!=null)player.clearTracksForSelection();
        }
        anchorWord=target.start;
        persist();pump();
    }
    /** Indexed by the requested sentence, never by the last downloaded playlist item. */
    private Clip clipAt(int word){
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
    /** One player, one network synthesis, at most one prepared next fragment.
     * The whole-book downloader is EdgeFullBookService and never called here. */
    private void pump(){
        if(!bookReady||directory==null||destroyed||!active)return;
        Clip current=clipAt(anchorWord);
        if(current==null)return;
        Clip ahead=clipAt(current.end);
        if(player!=null){
            player.setAutoContinue(true);
            addTrack(current);
            if(ahead!=null)addTrack(ahead);
        }
        boolean wantsAudio=player!=null&&player.playRequested();
        if(!wantsAudio){changed();return;}
        LiveJob currentLive=liveFor(current);
        int activeRequests=0;
        for(LiveJob j:liveJobs.values())if(!j.networkDone)activeRequests++;
        // Always reserve the first connection for the selected sentence.
        if(!current.ready()&&currentLive==null&&activeRequests==0
                &&current.retryAt<=SystemClock.uptimeMillis())startLive(current);
        else if(ahead!=null&&!ahead.ready()&&liveFor(ahead)==null
                &&activeRequests<2 && ahead.retryAt<=SystemClock.uptimeMillis()
                &&(current.ready()||(currentLive!=null&&(currentLive.firstAudio||currentLive.networkDone)))){
            // Exactly ONE lookahead request, never the entire book.
            startLive(ahead);
        }
        changed();
    }
    private void addTrack(Clip clip){
        if(player==null||clip==null)return;
        if(clip.ready())player.appendReady(clip.key+":"+clip.start,clip.start,clip.end,clip.file);
        else {
            LiveJob live=liveFor(clip);
            if(live!=null)player.appendLive(clip.key+":"+clip.start,clip.start,clip.end,live.audio);
        }
    }
    private LiveJob liveFor(Clip clip){
        if(clip==null||directory==null)return null;
        return liveJobs.get(new File(directory,clip.key+".mp3").getAbsolutePath());
    }
    /** Stream selected audio directly to Media3, simultaneously caching bytes.
     * A fully completed network response alone may be handed to offline commit.
     */
    private void startLive(Clip clip){
        if(clip==null||clip.ready()||directory==null||liveFor(clip)!=null)return;
        final LiveJob job=new LiveJob(clip,directory,bookUri);
        liveJobs.put(job.fileId,job);
        error="";
        addTrack(clip);
        changed();
        final String voice=voice(),rate=rate();
        job.request=EdgeTtsClient.synthesize(clip.text,voice,rate,new EdgeTtsClient.Callback(){
            @Override public boolean streamOnly(){return true;}
            @Override public void progress(int count,long length){}
            @Override public void audio(byte[] bytes,int start,int count){
                job.audio.append(bytes,start,count); // no disk I/O on playback path
                job.cache.append(bytes,start,count);
                job.receivedBytes+=count;
                if(!job.firstAudio&&job.receivedBytes>=8192){
                    job.firstAudio=true;
                    main.post(()->{if(!destroyed&&job.bookId.equals(bookUri))pump();});
                }
            }
            @Override public void words(List<EdgeWordTimings.Boundary> all){
                job.words=EdgeWordTimings.align(clip.text,all);
            }
            @Override public void finished(byte[] mp3,int parts,String error){
                finished(mp3,parts,error,Collections.emptyList());
            }
            @Override public void finished(byte[] ignored,int parts,String problem,
                    List<EdgeWordTimings.Boundary> metadata){
                job.networkDone=true;job.successful=problem==null;
                job.audio.finish(problem);
                if(problem!=null){
                    job.cache.cancel();
                    main.post(()->{
                        liveJobs.remove(job.fileId);
                        if(job.bookId.equals(bookUri)){
                            // Don't silently retry forever after a rejected WebSocket.
                            clip.retryAt=Long.MAX_VALUE/2;
                            error="Edge TTS: "+problem;persist();changed();
                        }
                    });
                }else{
                    job.cache.complete(metadata,(ready,meta,cacheError)->{
                        if(cacheError!=null){
                            main.post(()->{
                                liveJobs.remove(job.fileId);
                                if(job.bookId.equals(bookUri)){
                                    clip.retryAt=Long.MAX_VALUE/2;
                                    error="Ошибка кэша: "+cacheError;changed();
                                }
                            });
                            return;
                        }
                        EdgeCacheCommitter.submit(EdgeStreamingService.this,ready,meta,job.folder,
                            getSharedPreferences("reader",0).getBoolean("edge_trim_sentence_pause",true),
                            (key,file,commitError)->main.post(()->{
                                liveJobs.remove(job.fileId);
                                if(commitError==null){
                                    clip.file=file;clip.bytes=file.length();clip.chunks=parts;
                                    clip.failures=0;clip.retryAt=0;
                                }else if(job.bookId.equals(bookUri)){
                                    error="MP3 в кэше: "+commitError;
                                }
                                if(job.bookId.equals(bookUri)){persist();pump();}
                            }));
                    });
                }
                main.post(()->{
                    if(!destroyed&&job.bookId.equals(bookUri))pump();
                });
            }
        });
    }
    /** Accurate in-memory word timestamps while the MP3 is still arriving. */
    public List<EdgeWordTimings.Word> liveWords(Clip c){
        LiveJob job=liveFor(c);
        return job==null?Collections.emptyList():job.words;
    }

    // During ordinary listening no legacy download()/retry() or notification exists.
    // EdgeFullBookService owns explicit whole-book background preparation.
    public boolean deleteClipAudio(int start){
        for(Clip c:archive)if(c.start==start){
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            LiveJob live=liveFor(c);
            if(live!=null){liveJobs.remove(live.fileId);
                if(live.request!=null)live.request.cancel();
                live.audio.finish("File deleted by user");live.cache.cancel();}
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
                if(live.request!=null)live.request.cancel();
                live.audio.finish("File deleted by user");live.cache.cancel();}
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
            job.audio.finish("User cleared offline audio");job.cache.cancel();
        }
        liveJobs.clear();
        if(player!=null)player.clearQueue();
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;File folder=directory;
        io.execute(()->{if(folder!=null){File[] children=folder.listFiles();if(children!=null)for(File f:children)f.delete();}
            main.post(()->{restore();persist();changed();if(done!=null)done.run();});});
    }
    /** Reconstruct the COMPLETE ordered sentence catalogue for this voice.
     * The manifest includes downloaded and missing sentences, so jumping over gaps
     * cannot silently redirect the player to the last ready sentence. */
    private void restore(){
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
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
