package ru.reader.rsvp;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Presentation;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.Image;
import android.graphics.PixelFormat;
import android.view.Display;
import android.view.WindowManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationCompat;
import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sole owner of the MRT online generator and persistent download queue.
 * Neither this Service nor its JS callbacks issue PLAY, PAUSE or STOP.
 * Playback position is an INPUT to the scheduler, never a trigger for audio transport.
 */
public final class MrtDownloadService extends Service {
    public interface Observer { void onQueueChanged(); }
    public static final String ACTION_BEGIN="ru.reader.rsvp.mrt.download.BEGIN";
    public static final String ACTION_DISABLE="ru.reader.rsvp.mrt.download.DISABLE";
    public static final String EXTRA_URI="book_uri";
    public static final String EXTRA_ANCHOR="anchor_word";
    private static final String CHANNEL="mrt_download_channel";
    private static final String PAGE="https://alkohole.github.io/machine-reading-text/index.html";
    private static final String ORIGIN="https://alkohole.github.io";
    private static final String TAG="MRT-Queue";
    private static final int ID=1353;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Observer> observers=new ArrayList<>();
    private final ArrayList<Clip> archive=new ArrayList<>();
    private final ArrayList<Integer> offsets=new ArrayList<>();
    private final LinkedHashMap<String,String> voices=new LinkedHashMap<>(),speeds=new LinkedHashMap<>(),emotions=new LinkedHashMap<>();
    private MrtAudioCache cache;
    private WebView web;
    private Presentation virtualWindow;
    private VirtualDisplay virtualDisplay;
    private ImageReader virtualFrames;
    private PowerManager.WakeLock synthesisWakeLock;
    private MrtPlaybackService player;
    private boolean playerBound, ready, bookReady, active, preparing, finishing, destroyed, foreground;
    private String bookUri="", bookText="", title="", error="";
    private int anchorWord, generatingSession, segmentLength, windowCount, lastPlaylistStart=-1;
    private long chunkBytes, retryTime;
    private int chunkCount;
    private Clip generating;
    private final BroadcastReceiver switchReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent intent){
            if(AudioModeCoordinator.STOP_MRT.equals(intent.getAction())) setActive(false);
        }
    };

    public static final class Clip {
        public final int start,end;
        public final String text,key;
        public File file;
        public long bytes;
        public int chunks;
        public boolean played;
        public int errors;
        public long retryAfter;
        Clip(int start,int end,String text,String key) {
            this.start=start;this.end=end;this.text=text;this.key=key;
        }
        public boolean ready() {return file!=null && file.isFile();}
        public Clip copy(){Clip c=new Clip(start,end,text,key);c.file=file;c.bytes=bytes;c.chunks=chunks;c.played=played;c.errors=errors;return c;}
    }
    public final class LocalBinder extends Binder {public MrtDownloadService getService(){return MrtDownloadService.this;}}
    private final IBinder binder=new LocalBinder();
    private final MrtPlaybackService.Listener playerListener=new MrtPlaybackService.Listener(){
        @Override public void onTrackChanged(int start,int end,String key,boolean finishedPrevious){
            main.post(()->{
                if(!bookReady)return;
                if(finishedPrevious) for(Clip c:archive) if(c.end==start && c.ready())c.played=true;
                anchorWord=Math.max(0,start);
                persist();
                pump();
            });
        }
        @Override public void onPlaybackStateChanged(){
            main.post(()->{if(bookReady){
                if(player!=null && player.isAtEnd()) for(Clip c:archive)
                    if((c.key+":"+c.start).equals(player.currentKey()))c.played=true;
                pump();
            }});
        }
    };
    private final ServiceConnection playerConnection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            player=((MrtPlaybackService.LocalBinder)binder).getService();
            player.addListener(playerListener);
            if(!bookUri.isEmpty())player.prepareBook(bookUri);
            pump();
        }
        @Override public void onServiceDisconnected(ComponentName name){player=null;}
    };
    @Override public void onCreate(){
        super.onCreate();
        if(Build.VERSION.SDK_INT>=26){NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            if(m!=null)m.createNotificationChannel(new NotificationChannel(CHANNEL,"MRT: фоновая загрузка",NotificationManager.IMPORTANCE_LOW));}
        PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);
        if(pm!=null){synthesisWakeLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"ChitalkaRSVP:MRT-synthesis");
            synthesisWakeLock.setReferenceCounted(false);}
        playerBound=bindService(new Intent(this,MrtPlaybackService.class),playerConnection,BIND_AUTO_CREATE);
        IntentFilter filter=new IntentFilter(AudioModeCoordinator.STOP_MRT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(switchReceiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(switchReceiver,filter);
        createEngine();
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null){
            if(ACTION_BEGIN.equals(intent.getAction())){
                String uri=intent.getStringExtra(EXTRA_URI);
                int anchor=intent.getIntExtra(EXTRA_ANCHOR,0);
                if(uri!=null && !uri.isEmpty())openBook(uri,anchor);
                active=true;
                promote();
                pump();
            } else if(ACTION_DISABLE.equals(intent.getAction())){
                setActive(false);
            }
        }
        return START_REDELIVER_INTENT;
    }
    private Notification notice(){
        PendingIntent open=PendingIntent.getActivity(this,24,new Intent(this,MrtDownloadActivity.class).putExtra(EXTRA_URI,bookUri),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String summary=generating!=null?"Синтез: "+(archive.indexOf(generating)+1)+" · "+chunkCount+" чанков · "+(chunkBytes/1024)+" КБ"
                :"Готовых MP3: "+countReady()+" · ожидание";
        return new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_reader)
                .setContentTitle("MRT · автоподгрузка").setContentText(summary)
                .setOnlyAlertOnce(true).setOngoing(active).setContentIntent(open).build();
    }
    private void promote(){if(!active)return;try{startForeground(ID,notice());foreground=true;}
        catch(Exception ex){Log.w(TAG,"Foreground service not granted",ex);error="Фоновое выполнение ограничено Android: "+ex.getClass().getSimpleName();}}
    private void refreshNotice(){if(!active || !foreground)return;
        NotificationManager m=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);if(m!=null)m.notify(ID,notice());}
    public void addObserver(Observer o){if(o!=null&&!observers.contains(o))observers.add(o);if(o!=null)o.onQueueChanged();}
    public void removeObserver(Observer o){observers.remove(o);}
    private void changed(){for(Observer o:new ArrayList<>(observers))o.onQueueChanged();refreshNotice();}
    public String bookUri(){return bookUri;}
    public String bookTitle(){return title;}
    public boolean isActive(){return active;}
    public boolean isReady(){return ready&&bookReady;}
    public String error(){return error;}
    public int anchor(){return player!=null&&player.currentStartWord()>=0?player.currentStartWord():anchorWord;}
    public int words(){return offsets.size();}
    public int countReady(){int n=0;for(Clip c:archive)if(c.ready())n++;return n;}
    public int currentOrdinal(){int cursor=anchor();int i=0;for(Clip c:archive)if(c.start<=cursor)i++;return i;}
    public ArrayList<Clip> snapshot(){ArrayList<Clip> result=new ArrayList<>();for(Clip c:archive)result.add(c.copy());return result;}
    public Clip currentClip(){int a=anchor();for(Clip c:archive)if(c.start==a)return c.copy();return null;}
    public File folder(){return cache==null?null:cache.directory();}
    public long progressBytes(){return chunkBytes;}
    public int progressChunks(){return chunkCount;}
    public int generatingStart(){return generating==null?-1:generating.start;}
    public LinkedHashMap<String,String> options(String kind){return new LinkedHashMap<>("voice".equals(kind)?voices:"speed".equals(kind)?speeds:emotions);}
    public void selectOption(String kind,String value){
        String element="voice".equals(kind)?"ttsModel":"speed".equals(kind)?"ttsSpeed":"ttsEmotion";
        String pref="voice".equals(kind)?"mrt_selected_voice":"speed".equals(kind)?"mrt_selected_speed":"mrt_selected_emotion";
        getSharedPreferences("reader",0).edit().putString(pref,value).apply();
        eval("(function(){var e=document.getElementById("+JSONObject.quote(element)+");if(!e)return false;e.value="+JSONObject.quote(value)+";e.dispatchEvent(new Event('change',{bubbles:true}));return true;})()",null);
        // Rebuild candidates using the new voice key; previous audio remains cached.
        // Existing MP3 remain on disk, and previous listening history is preserved.
        // New settings are included in the content hash for future fragments.
        int word=anchor();stopCapture();anchorWord=word;pump();
    }
    public void openBook(String uri,int anchor){
        if(uri==null||uri.isEmpty())return;
        if(uri.equals(bookUri)){
            if(anchor>=0 && (player==null || player.currentStartWord()<0))anchorWord=anchor;
            pump();return;
        }
        stopCapture();
        if(cache!=null)cache.close();
        bookUri=uri;bookReady=false; // engine readiness is independent of book
        anchorWord=Math.max(0,anchor);archive.clear();offsets.clear();bookText="";title="";
        if(player!=null)player.prepareBook(uri);
        changed();
        final String requested=uri;
        new Thread(()->{
            try{
                BookLoader.Book book=BookLoader.load(this,Uri.parse(requested));
                String text=book.text==null?"":book.text;
                ArrayList<Integer> found=new ArrayList<>();Matcher m=Pattern.compile("\\S+").matcher(text);while(m.find())found.add(m.start());
                main.post(()->{
                    if(destroyed||!requested.equals(bookUri))return;
                    bookText=text;title=book.title==null?"Книга":book.title;offsets.addAll(found);
                    bookReady=!offsets.isEmpty();
                    SharedPreferences prefs=getSharedPreferences("reader",0);
                    cache=new MrtAudioCache(this,bookUri,title,prefs.getInt("mrt_cache_limit_mb",100));
                    cache.keepBookCopy(this,bookUri);
                    restore();error=bookReady?"":"В книге нет текста для озвучивания";pump();
                });
            }catch(Exception ex){main.post(()->{if(requested.equals(bookUri)){error="Ошибка чтения книги: "+ex.getMessage();changed();}});}
        },"mrt-download-book").start();
    }
    public void refreshSettings(){pump();}
    public void setActive(boolean enabled){
        if(active==enabled)return;
        active=enabled;
        if(enabled){promote();pump();}
        else{
            stopCapture();
            if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}
            changed();
            stopSelf();
        }
    }
    public void setAnchor(int start,boolean userSelected){
        if(!bookReady)return;
        start=Math.max(0,Math.min(start,offsets.size()-1));
        if(userSelected){
            // A user-initiated jump cancels an irrelevant synthesis, not playback itself.
            stopCapture();
            if(player!=null)player.clearQueue();
        }
        anchorWord=start;
        pump();
    }
    public void clearAudio(Runnable completed){
        setActive(false);
        if(player!=null)player.clearQueue();
        archive.clear();
        if(cache==null){changed();if(completed!=null)completed.run();return;}
        cache.clear(()->{changed();if(completed!=null)completed.run();});
    }
    private Clip clipAt(int start){
        if(start<0||start>=offsets.size())return null;
        int begin=offsets.get(start),end=Math.min(bookText.length(),begin+segmentLength);
        if(end<bookText.length()){
            int sentence=-1;for(char c:new char[]{'.','!','?','\n'}) sentence=Math.max(sentence,bookText.lastIndexOf(c,end-1));
            if(sentence>=begin+segmentLength/2)end=sentence+1;
            else {int space=bookText.lastIndexOf(' ',end-1);if(space>begin+segmentLength/2)end=space+1;}
        }
        int after=Collections.binarySearch(offsets,end);if(after<0)after=-after-1;
        after=Math.min(offsets.size(),Math.max(start+1,after));
        String text=bookText.substring(begin,end).trim();if(text.isEmpty())return null;
        SharedPreferences prefs=getSharedPreferences("reader",0);
        String key=MrtAudioCache.key(text,prefs.getString("mrt_selected_voice",""),prefs.getString("mrt_selected_speed",""),prefs.getString("mrt_selected_emotion",""));
        for(Clip c:archive)if(c.start==start && c.end==after && c.key.equals(key))return c;
        Clip c=new Clip(start,after,text,key);c.file=cache.cached(key);if(c.file!=null)c.bytes=c.file.length();
        archive.add(c);Collections.sort(archive,(a,b)->Integer.compare(a.start,b.start));persist();changed();return c;
    }
    private void pump(){
        if(destroyed||!bookReady||cache==null)return;
        SharedPreferences prefs=getSharedPreferences("reader",0);
        segmentLength=MrtSegmentSettings.get(prefs);windowCount=MrtPrefetchSettings.get(prefs);
        int cursor=anchor();
        ArrayList<Clip> window=new ArrayList<>();
        ArrayList<Integer> starts=MrtQueuePlanner.window(cursor,offsets.size(),windowCount,
                st->{Clip item=clipAt(st);return item==null?st:item.end;});
        for(int st:starts){Clip clip=clipAt(st);if(clip!=null)window.add(clip);}
        if(player!=null){
            player.setAutoContinue(prefs.getBoolean("mrt_auto_continue",true));
            // Append only contiguous downloaded MP3s in order, independently of Play.
            for(Clip c:window){if(!c.ready())break;player.appendReady(c.key+":"+c.start,c.start,c.end,c.file);}
        }
        if(active&&ready&&generating==null){
            for(Clip c:window){
                if(c.ready())continue;
                if(SystemClock.uptimeMillis()<c.retryAfter)break;
                generate(c);break;
            }
        }
        changed();
    }
    private void generate(Clip c){
        if(!active||!ready||generating!=null)return;
        generating=c;finishing=false;int s=++generatingSession;
        try{if(synthesisWakeLock!=null)synthesisWakeLock.acquire(130000L);}catch(Exception ignored){}
        chunkBytes=0;chunkCount=0;
        cache.begin(s,c.key);
        eval("(function(){var m=window.__rsvpMrt;return !!m&&m.setText("+JSONObject.quote(c.text)+")&&m.captureBegin("+s+");})()",result->{
            if(!same(c,s))return;
            if(!"true".equals(result)){retry("MRT не принял текст");return;}
            waitReady(c,s,SystemClock.uptimeMillis()+22000);
        });
        main.postDelayed(()->{if(same(c,s)&&!finishing)retry("Тайм-аут синтеза 120 секунд");},120000);
        changed();
    }
    private boolean same(Clip c,int s){return !destroyed && active && generating==c && generatingSession==s;}
    private void waitReady(Clip c,int s,long deadline){
        if(!same(c,s))return;
        eval("window.__rsvpMrt && window.__rsvpMrt.prepareStart()",status->{
            if(!same(c,s))return;
            if("1".equals(status)){
                // JS click first, then a real MotionEvent fallback when possible.
                eval("window.__rsvpMrt && window.__rsvpMrt.startPlayback()",null);
                main.postDelayed(()->{
                    if(!same(c,s)||chunkCount>0)return;
                    tapWebButton(s);
                },1100);
                main.postDelayed(()->{
                    if(!same(c,s)||chunkCount>0)return;
                    retry("Нет аудиочанков; WebView мог запретить фоновое нажатие");
                },9000);
            }else if(SystemClock.uptimeMillis()<deadline){main.postDelayed(()->waitReady(c,s,deadline),300);}
            else retry("MRT не готов ("+status+")");
        });
    }
    private void tapWebButton(int session){
        if(web==null||session!=generatingSession)return;
        eval("(function(){var b=document.getElementById('textSpeaker');if(!b)return null;var r=b.getBoundingClientRect();return {x:(r.left+r.width/2)/window.innerWidth,y:(r.top+r.height/2)/window.innerHeight};})()",reply->{
            if(session!=generatingSession||web==null)return;
            try{
                JSONObject pos=new JSONObject(reply);
                int w=web.getWidth(),h=web.getHeight();if(w<=0||h<=0)return;
                float x=(float)pos.getDouble("x")*w,y=(float)pos.getDouble("y")*h;
                long time=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                web.dispatchTouchEvent(down);down.recycle();
                main.postDelayed(()->{if(web!=null && session==generatingSession){MotionEvent up=MotionEvent.obtain(time,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,x,y,0);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);web.dispatchTouchEvent(up);up.recycle();}},80);
            }catch(Exception ex){Log.w(TAG,"MRT synthetic tap",ex);}
        });
    }
    private void endCapture(){
        Clip c=generating;if(c==null||finishing)return;
        int s=generatingSession;finishing=true;
        eval("window.__rsvpMrt && window.__rsvpMrt.captureEnd()",null);
        cache.finish(s,(file,bytes,chunks,failure)->{
            if(!same(c,s))return;
            generating=null;finishing=false;
            releaseWakeLock();
            if(file==null){failed(c,"Аудиофайл не распознан: "+failure);return;}
            c.file=file;c.bytes=bytes;c.chunks=chunks;c.errors=0;error="";
            persist();pump();
        });
    }
    private void retry(String reason){Clip c=generating;if(c==null)return;
        ++generatingSession;generating=null;finishing=false;
        releaseWakeLock();
        if(cache!=null)cache.abort();eval("window.__rsvpMrt && window.__rsvpMrt.captureEnd()",null);
        failed(c,reason);
    }
    private void failed(Clip c,String reason){
        c.errors++;long wait=c.errors<4?c.errors*2500L:12000L;
        c.retryAfter=SystemClock.uptimeMillis()+wait;
        error="Фрагмент "+(archive.indexOf(c)+1)+": "+reason+"; повтор через "+wait/1000+" с";
        Log.w(TAG,error);changed();main.postDelayed(this::pump,wait+50);
    }
    private void onBridge(String raw){
        if(destroyed||generating==null)return;
        try{
            JSONObject j=new JSONObject(raw);if(j.optInt("session",-1)!=generatingSession)return;
            String type=j.optString("type","");
            if("audioPart".equals(type)){cache.append(generatingSession,j.optString("data",""));}
            else if("chunk".equals(type)){chunkBytes=j.optLong("bytes",chunkBytes);chunkCount=j.optInt("chunks",chunkCount);changed();}
            else if("finished".equals(type))endCapture();
            else if("error".equals(type)&&!finishing)retry(j.optString("detail","MRT error"));
        }catch(Exception ex){Log.w(TAG,"Bad MRT message",ex);}
    }
    private void createEngine(){
        web=new WebView(getApplicationContext()){
            @Override public boolean onCheckIsTextEditor(){return false;}
        };
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);s.setAllowFileAccess(false);s.setAllowContentAccess(false);
        // A Service does not have an Activity window; this is best-effort headless WebView.
        int w=getResources().getDisplayMetrics().widthPixels;
        web.measure(View.MeasureSpec.makeMeasureSpec(Math.max(360,w),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(900,View.MeasureSpec.EXACTLY));
        web.layout(0,0,Math.max(360,w),900);
        attachPrivateDisplay();
        Set<String> origins=new HashSet<>(Collections.singleton(ORIGIN));
        try{
            if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)){
                WebViewCompat.addWebMessageListener(web,"RsvpMrtBridge",origins,(v,message,origin,frame,reply)->{
                    if(!frame||origin==null||!ORIGIN.equalsIgnoreCase(origin.toString())||message.getType()!=WebMessageCompat.TYPE_STRING)return;
                    String payload=message.getData();if(payload!=null)main.post(()->onBridge(payload));
                });
            }
            if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)){
                String script=asset();if(!script.isEmpty())WebViewCompat.addDocumentStartJavaScript(web,script,origins);
            }
        }catch(Exception ex){Log.w(TAG,"Bridge initialization",ex);}
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){
                if(url!=null&&url.startsWith(PAGE)){
                    eval("!!window.__rsvpMrt",result->{if(!"true".equals(result))eval(asset(),null);});
                    pollReady(0);
                }
            }
        });
        web.loadUrl(PAGE);
    }
    /**
     * A private virtual display creates a real Android window for WebView without
     * requesting a public overlay permission or opening another Activity.
     * If restricted by the device, download service remains alive and reports
     * the normal headless-WebView failure rather than affecting native playback.
     */
    private void attachPrivateDisplay() {
        try {
            DisplayManager manager=(DisplayManager)getSystemService(DISPLAY_SERVICE);
            if(manager==null)return;
            int density=getResources().getDisplayMetrics().densityDpi;
            virtualFrames=ImageReader.newInstance(480,800,PixelFormat.RGBA_8888,3);
            virtualFrames.setOnImageAvailableListener(reader->{
                Image frame=null;
                try {frame=reader.acquireLatestImage();}
                catch(Exception ignored){}
                finally{if(frame!=null)frame.close();}
            },main);
            virtualDisplay=manager.createVirtualDisplay("MRT background engine",480,800,density,
                    virtualFrames.getSurface(),DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
            if(virtualDisplay==null)return;
            Display display=virtualDisplay.getDisplay();
            if(display==null)return;
            virtualWindow=new Presentation(this,display);
            virtualWindow.setContentView(web);
            if(virtualWindow.getWindow()!=null)virtualWindow.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            virtualWindow.show();
            Log.i(TAG,"WebView hosted on a private virtual display");
        }catch(Exception exception){
            Log.w(TAG,"Virtual window unavailable; using headless WebView",exception);
            if(virtualWindow!=null){try{virtualWindow.dismiss();}catch(Exception ignored){}virtualWindow=null;}
            if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}
            if(virtualFrames!=null){virtualFrames.close();virtualFrames=null;}
        }
    }

    private String asset(){try(InputStream in=getAssets().open("mrt_online_capture.js")){
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        return out.toString(StandardCharsets.UTF_8.name());
    }catch(Exception ex){return "";}}
    private void pollReady(int attempt){
        if(destroyed||web==null)return;
        eval("(function(){return !!(window.__rsvpMrt&&window.app&&document.getElementById('textSpeaker')&&document.getElementById('textarea'));})()",result->{
            if("true".equals(result)){
                ready=true;
                eval("window.__rsvpMrt.setNativeUiHidden(true)",null);
                loadOptions();pump();
            }else if(attempt<80)main.postDelayed(()->pollReady(attempt+1),350);
            else {error="MRT WebView не загрузился";changed();}
        });
    }
    private void loadOptions(){
        eval("(function(){function a(id){var e=document.getElementById(id);return e?Array.from(e.options).filter(function(o){return !!o.value}).map(function(o){return {value:o.value,label:o.textContent||o.value};}):[];}return {voice:a('ttsModel'),speed:a('ttsSpeed'),emotion:a('ttsEmotion')};})()",res->{
            try{
                JSONObject j=new JSONObject(res);fill(voices,j.optJSONArray("voice"));fill(speeds,j.optJSONArray("speed"));fill(emotions,j.optJSONArray("emotion"));
                for(String kind:new String[]{"voice","speed","emotion"}){
                    String value=getSharedPreferences("reader",0).getString("mrt_selected_"+kind,"");
                    if(!value.isEmpty()){
                        String el="voice".equals(kind)?"ttsModel":"speed".equals(kind)?"ttsSpeed":"ttsEmotion";
                        eval("(function(){var e=document.getElementById("+JSONObject.quote(el)+");if(e){e.value="+JSONObject.quote(value)+";e.dispatchEvent(new Event('change',{bubbles:true}));}})()",null);
                    }
                }
                changed();
            }catch(Exception ignored){}
        });
    }
    private void fill(LinkedHashMap<String,String> out,JSONArray a){out.clear();if(a==null)return;for(int i=0;i<a.length();i++){
        JSONObject j=a.optJSONObject(i);if(j!=null)out.put(j.optString("value",""),j.optString("label",""));}}
    private void eval(String script,android.webkit.ValueCallback<String> callback){
        if(web==null||destroyed)return;
        web.evaluateJavascript(script,result->{
            if(callback==null)return;
            String decoded=result;
            if(result!=null&&result.startsWith("\"")){
                try{decoded=new JSONArray("["+result+"]").getString(0);}catch(Exception ignored){}
            }
            callback.onReceiveValue(decoded);
        });
    }
    private void releaseWakeLock(){try{if(synthesisWakeLock!=null&&synthesisWakeLock.isHeld())synthesisWakeLock.release();}catch(Exception ignored){}}
    private void stopCapture(){++generatingSession;generating=null;finishing=false;
        releaseWakeLock();
        if(cache!=null)cache.abort();
        eval("window.__rsvpMrt && (window.__rsvpMrt.captureEnd(),window.__rsvpMrt.stopPlayback())",null);
    }
    private void restore(){
        archive.clear();if(cache==null)return;
        try{JSONArray a=new JSONArray(cache.readIndex());for(int i=0;i<a.length();i++){
            JSONObject j=a.optJSONObject(i);if(j==null)continue;
            int st=j.optInt("start",-1),end=j.optInt("end",-1);
            if(st<0||end<=st||end>offsets.size())continue;
            String text=j.optString("text",""),key=j.optString("key","");
            if(text.isEmpty()||key.isEmpty())continue;
            Clip c=new Clip(st,end,text,key);c.file=cache.cached(key);c.bytes=j.optLong("bytes",c.file==null?0:c.file.length());
            c.chunks=j.optInt("chunks",0);c.played=j.optBoolean("played");archive.add(c);
        }}catch(Exception ex){Log.w(TAG,"Queue index",ex);}
        Collections.sort(archive,(a,b)->Integer.compare(a.start,b.start));changed();
    }
    private void persist(){if(cache==null)return;
        JSONArray list=new JSONArray();try{for(Clip c:archive){JSONObject j=new JSONObject();
            j.put("start",c.start).put("end",c.end).put("text",c.text).put("key",c.key).put("bytes",c.bytes).put("chunks",c.chunks).put("played",c.played);
            list.put(j);
        }cache.saveIndex(list.toString());}catch(Exception ignored){}
    }
    @Override public void onDestroy(){
        destroyed=true;active=false;stopCapture();main.removeCallbacksAndMessages(null);
        if(player!=null)player.removeListener(playerListener);
        try{unregisterReceiver(switchReceiver);}catch(Exception ignored){}
        if(playerBound){try{unbindService(playerConnection);}catch(Exception ignored){}}
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        if(virtualWindow!=null){try{virtualWindow.dismiss();}catch(Exception ignored){}virtualWindow=null;}
        if(virtualDisplay!=null){virtualDisplay.release();virtualDisplay=null;}
        if(virtualFrames!=null){virtualFrames.close();virtualFrames=null;}
        if(cache!=null)cache.close();
        if(foreground)stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
