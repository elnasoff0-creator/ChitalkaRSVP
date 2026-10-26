package ru.reader.rsvp;
import android.content.Context;
import android.content.SharedPreferences;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
/** Sequentially query every installed Android TTS engine, including Samsung and Google. */
public final class LocalVoiceCatalog {
    private LocalVoiceCatalog(){}
    public static final class Entry {
        public final String engine,label;public final Voice voice;
        Entry(String pkg,String name,Voice v){engine=pkg;label=name;voice=v;}
    }
    public interface Callback {void complete(ArrayList<Entry> voices);}
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static ArrayList<Entry> cached;
    private static long cachedAt;
    private static boolean fetching;
    private static final ArrayList<Callback> subscribers=new ArrayList<>();
    /** Shared asynchronous discovery: cached voices open immediately on subsequent taps. */
    public static void ready(Context context,Callback callback){
        synchronized(LocalVoiceCatalog.class){
            if(cached!=null&&android.os.SystemClock.uptimeMillis()-cachedAt<120000){
                ArrayList<Entry> copy=new ArrayList<>(cached);
                MAIN.post(()->callback.complete(copy));return;
            }
            subscribers.add(callback);
            if(fetching)return;
            fetching=true;
        }
        discover(context.getApplicationContext(),voices->{
            ArrayList<Callback> waiting;
            synchronized(LocalVoiceCatalog.class){
                cached=new ArrayList<>(voices);
                cachedAt=android.os.SystemClock.uptimeMillis();
                fetching=false;
                waiting=new ArrayList<>(subscribers);subscribers.clear();
            }
            for(Callback next:waiting)next.complete(new ArrayList<>(voices));
        });
    }
    public static void prefetch(Context context){ready(context,voices->{});}

    public static void discover(Context context,Callback callback){
        TextToSpeech[] probe=new TextToSpeech[1];
        final boolean[] completed={false};
        Handler handler=new Handler(Looper.getMainLooper());
        try{probe[0]=new TextToSpeech(context,status->{
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if(completed[0])return;
                completed[0]=true;
                TextToSpeech tts=probe[0];
                if(tts==null||status!=TextToSpeech.SUCCESS){
                    if(tts!=null)tts.shutdown();callback.complete(new ArrayList<>());return;
                }
                new Thread(()->{
                    ArrayList<TextToSpeech.EngineInfo> engines=new ArrayList<>();
                    try{engines.addAll(tts.getEngines());}catch(Exception ignored){}
                    handler.post(()->{
                        tts.shutdown();
                        if(engines.isEmpty()){callback.complete(new ArrayList<>());return;}
                        scan(context,engines,0,new ArrayList<>(),callback);
                    });
                },"tts-local-engines").start();
            },80);
        });
        handler.postDelayed(()->{
            if(!completed[0]){
                completed[0]=true;
                if(probe[0]!=null)probe[0].shutdown();
                callback.complete(new ArrayList<>());
            }
        },4500);
        }catch(Exception e){callback.complete(new ArrayList<>());}
    }
    private static void scan(Context context,ArrayList<TextToSpeech.EngineInfo> engines,int pos,
                             ArrayList<Entry> found,Callback done){
        if(pos>=engines.size()){
            Collections.sort(found,(a,b)->(a.label+a.voice.getName()).compareToIgnoreCase(b.label+b.voice.getName()));
            done.complete(found);return;
        }
        TextToSpeech.EngineInfo info=engines.get(pos);
        final TextToSpeech[] ref=new TextToSpeech[1];
        final boolean[] completed={false};
        Runnable advance=()->{
            if(completed[0])return;
            completed[0]=true;
            if(ref[0]!=null){ref[0].shutdown();ref[0]=null;}
            scan(context,engines,pos+1,found,done);
        };
        Handler h=new Handler(Looper.getMainLooper());
        try{
            ref[0]=new TextToSpeech(context,status->{
                h.post(()->{
                    if(completed[0])return;
                    if(ref[0]==null){h.postDelayed(advance,200);return;}
                    if(status==TextToSpeech.SUCCESS){
                        final TextToSpeech ready=ref[0];
                        new Thread(()->{
                            Set<Voice> voices=null;
                            try{voices=ready.getVoices();}catch(Exception ignored){}
                            final Set<Voice> discovered=voices;
                            h.post(()->{
                                if(completed[0])return;
                                if(discovered!=null)for(Voice v:discovered){
                                    if(v.getLocale()!=null&&"ru".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired())
                                        found.add(new Entry(info.name,info.label==null?info.name:info.label,v));
                                }
                                advance.run();
                            });
                        },"tts-local-voices").start();
                    }else advance.run();
                });
            },info.name);
            h.postDelayed(advance,4000);
        }catch(Exception e){advance.run();}
    }
    public static void show(android.app.Activity activity,Runnable afterSelect){
        // Discovery occurs without a placeholder sheet. Present the selector only when complete.
        ready(activity.getApplicationContext(),voices->{
            if(activity.isFinishing()||activity.isDestroyed())return;
            if(voices.isEmpty()){
                new SwipeDialogs.Builder(activity).fromTop().setTitle("Локальные голоса")
                    .setMessage("Русские локальные голоса не обнаружены или движок ограничивает доступ для сторонних приложений.")
                    .setPositiveButton("ОК",null).show();return;
            }
            String[] labels=new String[voices.size()];
            SharedPreferences prefs=activity.getSharedPreferences("reader",0);
            String selectedEngine=prefs.getString("tts_engine","");String selectedVoice=prefs.getString("tts_voice","");int checked=-1;
            for(int i=0;i<voices.size();i++){
                Entry e=voices.get(i);
                String brand=e.engine.toLowerCase().contains("samsung")?"Samsung":e.engine.toLowerCase().contains("google")?"Google":e.label;
                labels[i]=brand+" · "+e.voice.getName();
                if(e.engine.equals(selectedEngine)&&e.voice.getName().equals(selectedVoice))checked=i;
            }
            UiNavigationSheet.showTopChoices(activity,"Локальные голоса",labels,checked,index->{
                Entry selected=voices.get(index);
                prefs.edit().putString("tts_engine",selected.engine)
                    .putString("tts_voice",selected.voice.getName()).apply();
                if(afterSelect!=null)afterSelect.run();
            });
        });
    }
}
