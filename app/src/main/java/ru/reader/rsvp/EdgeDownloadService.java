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
    private final ArrayList<Clip> archive=new ArrayList<>();
    private final ArrayList<Observer> observers=new ArrayList<>();
    private String bookUri="",title="",bookText="",error="";
    private File directory;
    private boolean bookReady,active,destroyed,foreground,playerBound;
    private int anchorWord,segmentLength,windowCount,session,partCount;
    private long partBytes;
    private Clip generating;
    private EdgeTtsClient transport;
    private EdgePlaybackService player;
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
        PendingIntent click=PendingIntent.getActivity(this,49,new Intent(this,EdgeQueueActivity.class).putExtra(EXTRA_URI,bookUri),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_reader)
            .setContentTitle("Edge TTS · автоподгрузка")
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
    public int currentOrdinal(){int n=0;for(Clip c:archive)if(c.start<=anchor())n++;return n;}
    public int countReady(){int n=0;for(Clip c:archive)if(c.ready())n++;return n;}
    public int generatingStart(){return generating==null?-1:generating.start;}
    public boolean isRedownloading(int start){return generating!=null&&generating.start==start;}
    public int progressChunks(){return partCount;}
    public long progressBytes(){return partBytes;}
    public File folder(){return directory;}
    public ArrayList<Clip> snapshot(){ArrayList<Clip> list=new ArrayList<>();for(Clip c:archive)list.add(c.copy());return list;}
    public Clip currentClip(){for(Clip c:archive)if(c.start==anchor())return c.copy();return null;}
    public String voice(){return getSharedPreferences("reader",0).getString("edge_voice","ru-RU-SvetlanaNeural");}
    public String rate(){return getSharedPreferences("reader",0).getString("edge_rate","+0%");}
    public void choose(String voice,String rate){
        SharedPreferences.Editor edit=getSharedPreferences("reader",0).edit();
        if(voice!=null)edit.putString("edge_voice",voice);
        if(rate!=null)edit.putString("edge_rate",rate);
        edit.apply();cancelGeneration();
        if(player!=null)player.clearQueue();
        pump();
    }
    public void openBook(String uri,int initialWord){
        if(uri==null||uri.isEmpty())return;
        if(uri.equals(bookUri)){if(player==null||player.currentStartWord()<0)anchorWord=Math.max(0,initialWord);pump();return;}
        cancelGeneration();bookUri=uri;bookReady=false;anchorWord=Math.max(0,initialWord);
        archive.clear();offsets.clear();bookText="";directory=null;error="";
        if(player!=null)player.prepareBook("edge:"+uri);
        changed();
        final String requested=uri;
        io.execute(()->{
            try{
                BookLoader.Book book=BookLoader.load(this,Uri.parse(requested));
                String raw=book.text==null?"":book.text;
                String name=book.title==null?"Книга":book.title;
                ArrayList<Integer> positions=new ArrayList<>();
                Matcher matcher=Pattern.compile("\\S+").matcher(raw);while(matcher.find())positions.add(matcher.start());
                File dir=new File(EdgeBookStorage.bookFolder(this,requested,name),"EdgeTTS");dir.mkdirs();
                main.post(()->{
                    if(destroyed||!requested.equals(bookUri))return;
                    bookText=raw;title=name;offsets.addAll(positions);directory=dir;
                    bookReady=!offsets.isEmpty();restore();
                    if(!bookReady)error="Книга не содержит текста";
                    pump();
                });
            }catch(Exception ex){main.post(()->{if(requested.equals(bookUri)){error="Ошибка чтения книги: "+ex.getMessage();changed();}});}
        });
    }
    public void refreshSettings(){pump();}
    public void setActive(boolean enabled){
        if(active==enabled)return;active=enabled;
        if(enabled){promote();pump();}
        else {cancelGeneration();if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}changed();stopSelf();}
    }
    public void setAnchor(int word,boolean selected){
        if(!bookReady)return;
        if(selected){cancelGeneration();if(player!=null)player.clearQueue();}
        anchorWord=Math.max(0,Math.min(offsets.size()-1,word));pump();
    }
    private Clip clipAt(int start){
        if(start<0||start>=offsets.size())return null;
        int begin=offsets.get(start),end=Math.min(bookText.length(),begin+segmentLength);
        if(end<bookText.length()){
            int sentence=-1;for(char ch:new char[]{'.','!','?','\n'})sentence=Math.max(sentence,bookText.lastIndexOf(ch,end-1));
            if(sentence>=begin+segmentLength/2)end=sentence+1;
            else {int space=bookText.lastIndexOf(' ',end-1);if(space>begin+segmentLength/2)end=space+1;}
        }
        int after=Collections.binarySearch(offsets,end);if(after<0)after=-after-1;
        after=Math.max(start+1,Math.min(offsets.size(),after));
        String text=bookText.substring(begin,end).trim();if(text.isEmpty())return null;
        String key=EdgeBookStorage.key(text,voice(),rate(),"edge-direct-v1");
        for(Clip c:archive)if(c.start==start&&c.end==after&&c.key.equals(key))return c;
        Clip c=new Clip(start,after,text,key);
        c.file=new File(directory,key+".mp3");if(!c.ready())c.file=null;
        if(c.ready())c.bytes=c.file.length();
        archive.add(c);Collections.sort(archive,(a,b)->Integer.compare(a.start,b.start));persist();changed();return c;
    }
    private void pump(){
        if(!bookReady||directory==null||destroyed)return;
        SharedPreferences pref=getSharedPreferences("reader",0);
        segmentLength=EdgeSegmentSettings.get(pref);windowCount=pref.getBoolean(EdgeSettings.PREFETCH,true)?EdgePrefetchSettings.get(pref):1;
        int from=anchor();ArrayList<Clip> window=new ArrayList<>();
        ArrayList<Integer> indices=EdgeQueuePlanner.window(from,offsets.size(),windowCount,st->{Clip item=clipAt(st);return item==null?st:item.end;});
        for(int at:indices){Clip c=clipAt(at);if(c!=null)window.add(c);}
        if(player!=null){
            player.setAutoContinue(pref.getBoolean(EdgeSettings.AUTO_CONTINUE,true));
            for(Clip c:window){if(!c.ready())break;player.appendReady(c.key+":"+c.start,c.start,c.end,c.file);}
        }
        if(active&&generating==null){
            for(Clip c:window){
                if(c.ready())continue;
                if(SystemClock.uptimeMillis()<c.retryAt)break;
                download(c);break;
            }
        }
        changed();
    }
    private void download(Clip c){
        if(generating!=null||!active)return;
        generating=c;int id=++session;partBytes=0;partCount=0;error="";changed();
        transport=EdgeTtsClient.synthesize(c.text,voice(),rate(),new EdgeTtsClient.Callback(){
            @Override public void progress(int chunks,long bytes){main.post(()->{if(generating==c&&id==session){partCount=chunks;partBytes=bytes;changed();}});}
            @Override public void finished(byte[] mp3,int chunks,String failure){
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
                        final String err=problem;
                        main.post(()->{
                            if(destroyed||generating!=c||id!=session||!uri.equals(bookUri))return;
                            generating=null;transport=null;
                            if(err!=null){retry(c,err);return;}
                            c.file=result;c.bytes=result.length();c.chunks=chunks;c.failures=0;error="";
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
        c.failures++;long seconds=Math.min(120,5L*(1<<Math.min(4,c.failures-1)));
        c.retryAt=SystemClock.uptimeMillis()+seconds*1000;
        error="Фрагмент "+(archive.indexOf(c)+1)+": "+reason+" · повтор через "+seconds+" с";
        changed();main.postDelayed(this::pump,seconds*1000+100);
    }
    private void cancelGeneration(){session++;if(transport!=null)transport.cancel();transport=null;generating=null;}
    public boolean deleteClipAudio(int start){
        for(Clip c:archive)if(c.start==start){
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            if(generating==c)cancelGeneration();
            if(player!=null)player.evictCachedTrack(c.key+":"+c.start);
            c.file=null;c.bytes=0;c.chunks=0;c.played=false;c.failures=0;c.retryAt=0;
            File item=new File(directory,c.key+".mp3");io.execute(()->{item.delete();main.post(()->{persist();pump();});});
            error="";changed();return true;
        }return false;
    }
    public boolean redownloadClip(int start){
        for(Clip c:archive)if(c.start==start){
            if(player!=null && player.isPlaying() && (c.key+":"+c.start).equals(player.currentKey())){error="Сначала приостановите воспроизведение текущего MP3";changed();return false;}
            if(generating==c)cancelGeneration();
            if(player!=null)player.evictCachedTrack(c.key+":"+c.start);
            c.file=null;c.bytes=0;c.chunks=0;c.played=false;c.failures=0;c.retryAt=0;
            File item=new File(directory,c.key+".mp3");io.execute(()->{item.delete();main.post(()->{persist();setActive(true);pump();});});
            changed();return true;
        }return false;
    }
    public void clearAudio(Runnable done){
        setActive(false);if(player!=null)player.clearQueue();
        archive.clear();File folder=directory;
        io.execute(()->{if(folder!=null){File[] children=folder.listFiles();if(children!=null)for(File f:children)f.delete();}
            main.post(()->{changed();if(done!=null)done.run();});});
    }
    private void restore(){archive.clear();if(directory==null)return;
        try{File f=new File(directory,"queue.json");if(!f.isFile())return;
            byte[] input=new byte[(int)Math.min(f.length(),4*1024*1024L)];int total=0;
            try(java.io.FileInputStream in=new java.io.FileInputStream(f)){
                int count;while(total<input.length&&(count=in.read(input,total,input.length-total))>0)total+=count;
            }
            JSONArray data=new JSONArray(new String(input,0,total,StandardCharsets.UTF_8));
            for(int i=0;i<data.length()&&i<10000;i++){
                JSONObject row=data.optJSONObject(i);if(row==null)continue;
                int st=row.optInt("start",-1),end=row.optInt("end",-1);
                if(st<0||end<=st||end>offsets.size())continue;
                String text=row.optString("text",""),key=row.optString("key","");
                if(text.isEmpty()||!key.matches("[a-f0-9]{64}"))continue;
                Clip c=new Clip(st,end,text,key);c.file=new File(directory,key+".mp3");
                if(!c.ready())c.file=null;
                c.bytes=c.ready()?c.file.length():0;c.chunks=row.optInt("chunks",0);c.played=row.optBoolean("played",false);
                archive.add(c);
            }
            Collections.sort(archive,(a,b)->Integer.compare(a.start,b.start));
        }catch(Exception ex){Log.w("EdgeQueue","index restore failed",ex);}
    }
    private void persist(){if(directory==null||destroyed)return;
        JSONArray data=new JSONArray();try{for(Clip c:archive){JSONObject row=new JSONObject();row.put("start",c.start).put("end",c.end)
                .put("text",c.text).put("key",c.key).put("chunks",c.chunks).put("played",c.played);data.put(row);}}catch(Exception ignored){}
        File folder=directory;
        File file=new File(folder,"queue.json");String json=data.toString();
        io.execute(()->{File temp=new File(folder,"queue.json.tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(json.getBytes(StandardCharsets.UTF_8));out.flush();
                if(file.exists())file.delete();temp.renameTo(file);
            }catch(Exception ex){temp.delete();Log.w("EdgeQueue","Cannot persist index",ex);}});
    }
    @Override public void onDestroy(){destroyed=true;cancelGeneration();if(player!=null)player.removeListener(listener);
        try{unregisterReceiver(switchReceiver);}catch(Exception ignored){}
        if(playerBound)unbindService(playbackConnection);io.shutdown();super.onDestroy();}
}
