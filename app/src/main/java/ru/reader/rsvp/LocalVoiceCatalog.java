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
    public static void discover(Context context,Callback callback){
        TextToSpeech[] probe=new TextToSpeech[1];
        final boolean[] completed={false};
        Handler handler=new Handler(Looper.getMainLooper());
        try{probe[0]=new TextToSpeech(context,status->{
            new Handler(Looper.getMainLooper()).postDelayed(()->{
                if(completed[0])return;
                completed[0]=true;
                TextToSpeech tts=probe[0];
                ArrayList<TextToSpeech.EngineInfo> engines=new ArrayList<>();
                if(tts!=null){
                    if(status==TextToSpeech.SUCCESS)engines.addAll(tts.getEngines());
                    tts.shutdown();
                }
                if(engines.isEmpty()){callback.complete(new ArrayList<>());return;}
                scan(context,engines,0,new ArrayList<>(),callback);
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
                        Set<Voice> voices=ref[0].getVoices();
                        if(voices!=null)for(Voice v:voices){
                            if(v.getLocale()!=null&&"ru".equals(v.getLocale().getLanguage())&&!v.isNetworkConnectionRequired())
                                found.add(new Entry(info.name,info.label==null?info.name:info.label,v));
                        }
                    }
                    advance.run();
                });
            },info.name);
            h.postDelayed(advance,4000);
        }catch(Exception e){advance.run();}
    }
    public static void show(android.app.Activity activity,Runnable afterSelect){
        android.app.AlertDialog loading=new SwipeDialogs.Builder(activity).setTitle("Локальные голоса")
            .setMessage("Поиск голосов Samsung, Google и других движков…").create();
        loading.show();
        discover(activity,voices->{
            if(activity.isFinishing()||activity.isDestroyed()||!loading.isShowing())return;
            loading.dismiss();
            if(voices.isEmpty()){
                new SwipeDialogs.Builder(activity).setTitle("Локальные голоса")
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
            new SwipeDialogs.Builder(activity).setTitle("Локальные голоса")
                .setSingleChoiceItems(labels,checked,(dialog,index)->{
                    Entry selected=voices.get(index);
                    prefs.edit().putString("tts_engine",selected.engine).putString("tts_voice",selected.voice.getName()).apply();
                    if(afterSelect!=null)afterSelect.run();dialog.dismiss();
                }).show();
        });
    }
}
