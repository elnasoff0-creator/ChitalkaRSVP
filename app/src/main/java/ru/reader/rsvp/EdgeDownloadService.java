package ru.reader.rsvp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Independent Edge TTS online synthesis queue. No WebView, no playback commands.
 * Only verified MP3 files enter EdgePlaybackService. Each book gets its own cache.
 */
public final class EdgeDownloadService extends Service {
    public interface Observer {void onChanged();}
    public static final String ACTION_BEGIN="ru.reader.rsvp.edge.download.BEGIN";
    public static final String EXTRA_URI="book_uri";
    public static final String EXTRA_ANCHOR="anchor_word";
    private static final int NOTIFY_ID=1375;
    private static final String CHANNEL="edge_download_channel";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<Integer> offsets=new ArrayList<>();
    private final ArrayList<SentenceChunks.Chunk> sentenceParts=new ArrayList<>();
    private int cachedSavedPercent=0;
    private boolean calculatingCoverage=false;
    private long lastCoverageCheck=0;
    private final ArrayList<Clip> archive=new ArrayList<>();
    // The complete, sentence-indexed catalogue is independent of which MP3 has been cached.
    private final Map<Integer,Clip> byStart=new HashMap<>();
    // Requested clips survive jumps and process restarts; foreground selection takes precedence.
    private final LinkedHashSet<Integer> pendingStarts=new LinkedHashSet<>();
    private int priorityStart=-1;
    private final ArrayList<Observer> observers=new ArrayList<>();
    private String bookUri="",title="",bookText="",error="";
    private File directory;
    private boolean bookReady,active,destroyed,foreground,playerBound;
    private int anchorWord,segmentLength,windowCount,session,partCount;
    private long partBytes;
    private Clip generating;
    private String generationLock;
    private EdgeTtsClient transport;
    private EdgePlaybackService player;
    private final Runnable refreshReady=new Runnable(){@Override public void run(){
        if(destroyed)return;
        if(bookReady&&directory!=null)pump(); // pick up full-book MP3 files made by independent service
        main.postDelayed(this,2500);
    }};
    private final BroadcastReceiver switchReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(AudioModeCoordinator.STOP_EDGE.equals(i.getAction()))setActive(false);
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
    public final class LocalBinder extends Binder { public EdgeDownloadService getService(){return EdgeDownloadService.this;} }
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
        if(Build.VERSION.SDK_INT>=26){NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(manager!=null)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Edge TTS: загрузка",NotificationManager.IMPORTANCE_LOW));}
        EdgeSettings.migrate(getSharedPreferences("reader",0));
        main.postDelayed(refreshReady,2500);
        playerBound=bindService(new Intent(this,EdgePlaybackService.class),playbackConnection,BIND_AUTO_CREATE);
        IntentFilter filter=new IntentFilter(AudioModeCoordinator.STOP_EDGE);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(switchReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(switchReceiver,filter);
    }
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null && ACTION_BEGIN.equals(intent.getAction())){
            String uri=intent.getStringExtra(EXTRA_URI);
            if(uri!=null&&!uri.isEmpty())openBook(uri,intent.getIntExtra(EXTRA_ANCHOR,0));
            active=true;promote();pump();
        }
        return START_REDELIVER_INTENT;
    }
    private Notification notice(){
        PendingIntent click=PendingIntent.getActivity(this,49,new Intent(this,EdgeOnlineActivity.class).putExtra(EXTRA_URI,bookUri),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_reader)
            .setContentTitle("Edge TTS · чтение вслух")
            .setContentText(generating==null?"Готово: "+countReady()+" файлов":"Синтез: "+partCount+" частей · "+(partBytes/1024)+" КБ")
            .setOnlyAlertOnce(true).setOngoing(active).setContentIntent(click).build();
    }
    private void promote(){try{startForeground(NOTIFY_ID,notice());foreground=true;}
        catch(Exception e){error="Android ограничил фоновую работу: "+e.getMessage();}}
    private void changed(){for(Observer o:new ArrayList<>(observers))o.onChanged();
        if(active&&foreground){NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(m!=null)m.notify(NOTIFY_ID,notice());}}
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
    public int generatingStart(){return generating==null?-1:generating.start;}
    public boolean isRedownloading(int start){return generating!=null&&generating.start==start;}
    public int progressChunks(){return partCount;}
    public long progressBytes(){return partBytes;}
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
        edit.apply();cancelGeneration();
        if(player!=null)player.clearQueue();
        // Every voice has separate MP3 keys; rebuild the index without deleting older audio.
        if(bookReady){restore();priorityStart=SentenceChunks.forWord(sentenceParts,anchorWord)==null?-1:
                SentenceChunks.forWord(sentenceParts,anchorWord).fromWord;persist();}
        pump();
    }
    public void openBook(String uri,int initialWord){
        if(uri==null||uri.isEmpty())return;
        if(uri.equals(bookUri)){if(player==null||player.currentStartWord()<0)anchorWord=Math.max(0,initialWord);pump();return;}
        cancelGeneration();bookUri=uri;bookReady=false;anchorWord=Math.max(0,initialWord);
        cachedSavedPercent=0;lastCoverageCheck=0;
        archive.clear();byStart.clear();pendingStarts.clear();priorityStart=-1;
        offsets.clear();sentenceParts.clear();bookText="";directory=null;error="";
        if(player!=null)player.prepareBook("edge:"+uri);
        changed();
        final String requested=uri;
        io.execute(()->{
            try{
                BookLoader.Book book=BookLoader.load(this,Uri.parse(requested));
                String raw=book.text==null?"":book.text;
                String name=book.title==null?"Книга":book.title;
                ArrayList<Integer> positions=EdgeTextSegments.positions(raw);
                ArrayList<SentenceChunks.Chunk> newParts=SentenceChunks.split(raw);
                File bookDir=EdgeBookStorage.bookFolder(this,requested,name);
                File dir=new File(bookDir,"EdgeTTS");dir.mkdirs();
                DownloadsRepository.storeEdgeMeta(bookDir,requested,name,positions.size());
                main.post(()->{
                    if(destroyed||!requested.equals(bookUri))return;
                    bookText=raw;title=name;offsets.addAll(positions);sentenceParts.addAll(newParts);directory=dir;
                    bookReady=!offsets.isEmpty();restore();
                    if(!bookReady)error="Книга не содержит текста";
                    else {SentenceChunks.Chunk initial=SentenceChunks.forWord(sentenceParts,anchorWord);
                        priorityStart=initial==null?-1:initial.fromWord;persist();}
                    pump();
                });
            }catch(Exception ex){main.post(()->{if(requested.equals(bookUri)){error="Ошибка чтения книги: "+ex.getMessage();changed();}});}
        });
    }
    public void refreshSettings(){pump();}
    public void setActive(boolean enabled){
        if(active==enabled)return;active=enabled;
        if(enabled){if(!bookUri.isEmpty())DownloadsRepository.showEdge(this,bookUri);promote();pump();}
        else {cancelGeneration();if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}changed();stopSelf();}
    }
    public void setAnchor(int word,boolean selected){
        if(!bookReady){anchorWord=Math.max(0,word);return;}
        int requested=Math.max(0,Math.min(offsets.size()-1,word));
        SentenceChunks.Chunk part=SentenceChunks.forWord(sentenceParts,requested);
        if(part==null)return;
        Clip target=clipAt(requested);
        if(selected){
            // Preempt any download, even if the chosen sentence is far outside the
            // previously prepared range. The interrupted job remains pending.
            if(generating!=null&&generating!=target){
                pendingStarts.add(generating.start);
                cancelGeneration();
            }
            if(player!=null)player.clearTracksForSelection();
            priorityStart=target.start;
            pendingStarts.remove(target.start);
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
    /** Select the exact requested sentence first, then the next sentence, then
     * unfinished requests interrupted by seeking. No missing earlier sentence blocks playback. */
    private void pump(){
        if(!bookReady||directory==null||destroyed)return;
        Clip current=clipAt(anchorWord);
        Clip ahead=current==null?null:clipAt(current.end);
        if(player!=null){
            player.setAutoContinue(true);
            if(current!=null&&current.ready()){
                player.appendReady(current.key+":"+current.start,current.start,current.end,current.file);
                if(ahead!=null&&ahead.ready())
                    player.appendReady(ahead.key+":"+ahead.start,ahead.start,ahead.end,ahead.file);
            }
        }
        if(active&&generating==null){
            Clip next=null;
            if(priorityStart>=0){
                Clip priority=byStart.get(priorityStart);
                if(priority!=null&&!priority.ready())next=priority;
                else priorityStart=-1;
            }
            if(next==null&&current!=null&&!current.ready())next=current;
            if(next==null&&ahead!=null&&!ahead.ready())next=ahead;
            if(next==null){
                // Resume requests that were displaced by a user's manual jump.
                for(Integer start:new ArrayList<>(pendingStarts)){
                    Clip item=byStart.get(start);
                    if(item==null||item.ready()){pendingStarts.remove(start);continue;}
                    next=item;break;
                }
            }
            if(next!=null&&SystemClock.uptimeMillis()>=next.retryAt){
                pendingStarts.remove(next.start);
                download(next);
            }
        }
        changed();
    }
    private void download(Clip c){
        if(generating!=null||!active)return;
        String candidate=new File(directory,c.key+".mp3").getAbsolutePath();
        if(!EdgeGenerationGuard.claim(candidate)){
            main.postDelayed(this::pump,1500);return;
        }
        generationLock=candidate;
        generating=c;int id=++session;partBytes=0;partCount=0;error="";persist();changed();
        transport=EdgeTtsClient.synthesize(c.text,voice(),rate(),new EdgeTtsClient.Callback(){
            @Override public void progress(int chunks,long bytes){main.post(()->{if(generating==c&&id==session){partCount=chunks;partBytes=bytes;changed();}});}
            @Override public void finished(byte[] mp3,int chunks,String failure){
                finished(mp3,chunks,failure,java.util.Collections.emptyList());
            }
            @Override public void finished(byte[] mp3,int chunks,String failure,
                    java.util.List<EdgeWordTimings.Boundary> boundaries){
                main.post(()->{
                    if(destroyed||generating!=c||id!=session)return;
                    if(failure!=null){retry(c,failure);return;}
                    final String key=c.key,uri=bookUri;
                    final File folder=directory;
                    io.execute(()->{
                        String problem=null;File result=new File(folder,key+".mp3");
                        File tmp=new File(folder,key+".part");
                        try(FileOutputStream out=new FileOutputStream(tmp,false)){
                            if(mp3==null||mp3.length<1500)throw new Exception("Пустой ответ Edge");
                            if(!isMp3(mp3))throw new Exception("Полученные данные не похожи на MP3");
                            out.write(mp3);out.flush();
                        }catch(Exception e){problem=e.getMessage();}
                        if(problem==null){
                            if(result.exists()&&!result.delete())problem="Нельзя заменить старый MP3";
                            else if(!tmp.renameTo(result))problem="Не удалось сохранить MP3";
                        }
                        if(problem!=null)tmp.delete();
                        else {
                            EdgeWordTimings.store(result,c.text,boundaries);
                            EdgeTrailingSilence.detectAndStore(result,c.text);
                        }
                        final String err=problem;
                        main.post(()->{
                            if(destroyed||generating!=c||id!=session||!uri.equals(bookUri))return;
                            if(err!=null){retry(c,err);return;}
                            generating=null;transport=null;
                            EdgeGenerationGuard.release(generationLock);generationLock=null;
                            c.file=result;c.bytes=result.length();c.chunks=chunks;c.failures=0;error="";
                            pendingStarts.remove(c.start);
                            if(priorityStart==c.start)priorityStart=-1;
                            persist();pump();
                        });
                    });
                });
            }
        });
        main.postDelayed(()->{if(id==session&&generating==c)retry(c,"Тайм-аут Edge TTS (45 с)");},45000);
    }
    private static boolean isMp3(byte[] bytes){
        if(bytes.length<4)return false;
        if(bytes[0]=='I'&&bytes[1]=='D'&&bytes[2]=='3')return true;
        return (bytes[0]&255)==255 && (bytes[1]&0xe0)==0xe0 && ((bytes[1]>>1)&3)!=0;
    }
    private void retry(Clip c,String reason){
        if(generating!=c)return;
        if(transport!=null){transport.cancel();transport=null;}
        generating=null;session++;
        EdgeGenerationGuard.release(generationLock);generationLock=null;
        pendingStarts.add(c.start);
        c.failures++;long seconds=Math.min(120,5L*(1<<Math.min(4,c.failures-1)));
        c.retryAt=SystemClock.uptimeMillis()+seconds*1000;
        error="Фрагмент "+(archive.indexOf(c)+1)+": "+reason+" · повтор через "+seconds+" с";
        changed();main.postDelayed(this::pump,seconds*1000+100);
    }
    private void cancelGeneration(){session++;if(transport!=null)transport.cancel();transport=null;generating=null;
        EdgeGenerationGuard.release(generationLock);generationLock=null;}
    public boolean deleteClipAudio(int start){
        for(Clip c:archive)if(c.start==start){
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            if(generating==c)cancelGeneration();
            if(player!=null)player.evictCachedTrack(c.key+":"+c.start);
            c.file=null;c.bytes=0;c.chunks=0;c.played=false;c.failures=0;c.retryAt=0;
            File item=new File(directory,c.key+".mp3");io.execute(()->{item.delete();EdgeWordTimings.sidecar(item).delete();main.post(()->{persist();pump();});});
            error="";changed();return true;
        }return false;
    }
    public boolean redownloadClip(int start){
        for(Clip c:archive)if(c.start==start){
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            if(generating==c)cancelGeneration();
            if(player!=null)player.evictCachedTrack(c.key+":"+c.start);
            c.file=null;c.bytes=0;c.chunks=0;c.played=false;c.failures=0;c.retryAt=0;
            File item=new File(directory,c.key+".mp3");io.execute(()->{item.delete();EdgeWordTimings.sidecar(item).delete();main.post(()->{persist();setActive(true);pump();});});
            changed();return true;
        }return false;
    }
    public void clearAudio(Runnable done){
        setActive(false);if(player!=null)player.clearQueue();
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
                if(!c.ready()&&(old.optBoolean("queued",false)||"downloading".equals(old.optString("status",""))))
                    pendingStarts.add(c.start);
            }
            archive.add(c);byStart.put(c.start,c);
        }
        // Readying a previously downloaded sentence is a direct file lookup;
        // old MP3 files for other voices or old chunk sizes are never deleted.
    }
    private void persist(){
        if(directory==null||destroyed)return;
        JSONArray data=new JSONArray();
        try{for(Clip c:archive){
            JSONObject row=new JSONObject();row.put("start",c.start).put("end",c.end)
                .put("key",c.key).put("ready",c.ready()).put("queued",pendingStarts.contains(c.start))
                .put("status",c.ready()?"saved":(generating==c?"downloading":(pendingStarts.contains(c.start)?"pending":"missing")))
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
    @Override public void onDestroy(){destroyed=true;main.removeCallbacks(refreshReady);cancelGeneration();if(player!=null)player.removeListener(listener);
        try{unregisterReceiver(switchReceiver);}catch(Exception ignored){}
        if(playerBound)unbindService(playbackConnection);io.shutdown();super.onDestroy();}
}
