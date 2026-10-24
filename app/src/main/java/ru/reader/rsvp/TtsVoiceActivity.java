package ru.reader.rsvp;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.*;

/** Discover installed Android voices dynamically, with offline Russian voices first. */
public class TtsVoiceActivity extends Activity {
    private TextToSpeech synth;
    private LinearLayout items;
    private String engine="";
    private final ArrayList<TextToSpeech.EngineInfo> engines=new ArrayList<>();
    private int bg,fg;
    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView row(String title,int size){TextView t=new TextView(this);t.setText(title);t.setTextSize(size);t.setTextColor(fg);t.setPadding(dp(14),dp(14),dp(14),dp(14));return t;}
    @Override public void onCreate(Bundle b){super.onCreate(b);android.content.SharedPreferences pref=getSharedPreferences("reader",0);
        bg=pref.getInt("bg",0xfff3e7d5);fg=pref.getInt("fg",0xff30221e);engine=pref.getString("tts_engine","");
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);layout.setPadding(dp(12),dp(22),dp(12),dp(10));setContentView(layout);SafeSystemBars.matchBackground(this,bg);
        TextView title=row("←   Голос и озвучивание",23);title.setOnClickListener(v->finish());layout.addView(title);
        ScrollView scroll=new ScrollView(this);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));items=new LinearLayout(this);items.setOrientation(1);scroll.addView(items);
        loadEngine();
    }
    private void loadEngine(){
        if(synth!=null){synth.stop();synth.shutdown();synth=null;}
        items.removeAllViews();items.addView(row("Загрузка локальных голосов…",16));
        try{synth=engine.isEmpty()?new TextToSpeech(this,this::ready):new TextToSpeech(this,this::ready,engine);}
        catch(Exception e){items.removeAllViews();items.addView(row("Движок TTS недоступен",16));}
    }
    private void ready(int result){runOnUiThread(()->{
        if(synth==null||isFinishing())return;
        items.removeAllViews();if(result!=TextToSpeech.SUCCESS){items.addView(row("Не удалось инициализировать TTS",16));return;}
        engines.clear();engines.addAll(synth.getEngines());
        TextView catalogTitle=row("Установленные локальные голоса (все движки)",17);
        items.addView(catalogTitle);
        LinearLayout catalogRows=new LinearLayout(this);catalogRows.setOrientation(1);
        items.addView(catalogRows);
        LocalVoiceCatalog.discover(this,found->{
            if(isFinishing()||isDestroyed()||catalogRows.getParent()==null)return;
            catalogRows.removeAllViews();
            if(found.isEmpty())catalogRows.addView(row("Доступные локальные русские голоса не найдены",14));
            for(LocalVoiceCatalog.Entry candidate:found){
                String brand=candidate.engine.toLowerCase(Locale.ROOT).contains("samsung")?"Samsung":
                    candidate.engine.toLowerCase(Locale.ROOT).contains("google")?"Google":candidate.label;
                TextView item=row(brand+"  ·  "+candidate.voice.getName(),14);
                catalogRows.addView(item);
                item.setOnClickListener(view->{
                    getSharedPreferences("reader",0).edit()
                       .putString("tts_engine",candidate.engine)
                       .putString("tts_voice",candidate.voice.getName()).apply();
                    engine=candidate.engine;
                    startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.ENGINE));
                    loadEngine();
                });
            }
        });
        TextView choose=row("Текущий движок: "+(engine.isEmpty()?"Системный по умолчанию":engine)+"   ▾",17);choose.setOnClickListener(v->chooseEngine());items.addView(choose);
        items.addView(row("Русские голоса · локальные в приоритете",15));
        ArrayList<Voice> local=new ArrayList<>();ArrayList<Voice> online=new ArrayList<>();
        Set<Voice> voices=synth.getVoices();if(voices!=null)for(Voice v:voices)if(v.getLocale()!=null&&"ru".equals(v.getLocale().getLanguage())){
            if(v.isNetworkConnectionRequired())online.add(v);else local.add(v);
        }
        local.sort((a,b)->a.getName().compareToIgnoreCase(b.getName()));online.sort((a,b)->a.getName().compareToIgnoreCase(b.getName()));
        if(local.isEmpty())items.addView(row("Локальные русские голоса не установлены",15));
        for(Voice voice:local)addVoice(voice,false);
        if(!online.isEmpty()){
            TextView more=row("Показать сетевые варианты ("+online.size()+")   ▾",15);items.addView(more);
            LinearLayout hidden=new LinearLayout(this);hidden.setOrientation(1);hidden.setVisibility(View.GONE);items.addView(hidden);
            for(Voice v:online)addVoiceTo(hidden,v,true);
            more.setOnClickListener(view->hidden.setVisibility(hidden.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE));
        }
        TextView system=row("Загрузить дополнительные голоса в настройках Android  ›",15);
        system.setOnClickListener(v->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){new SwipeDialogs.Builder(this).setMessage("Настройки TTS недоступны").setPositiveButton("ОК",null).show();}});items.addView(system);
    });}
    private void addVoice(Voice voice,boolean network){addVoiceTo(items,voice,network);}
    private void addVoiceTo(LinearLayout container,Voice voice,boolean network){
        String chosen=getSharedPreferences("reader",0).getString("tts_voice","");
        String packageName=engine.isEmpty()&&synth!=null?synth.getDefaultEngine():engine;
        android.content.SharedPreferences prefs=getSharedPreferences("reader",0);
        String id=packageName+":"+voice.getName();
        boolean checked=prefs.getBoolean("tts_word_checked:"+id,false);
        boolean verified=prefs.getBoolean("tts_word_boundary:"+id,false);
        String capability=network?"Сетевой":!checked?"Локальный · метки пока не проверены":
            verified?"Локальный · точная подсветка слов":"Локальный · подсветка предложения";
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);
        TextView item=row((voice.getName().equals(chosen)?"◉ ":"○ ")+voice.getName()+"\n"+capability,14);
        card.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        item.setOnClickListener(v->{
            prefs.edit().putString("tts_engine",packageName).putString("tts_voice",voice.getName()).apply();
            synth.setVoice(voice);
            synth.speak("Привет! Это пример выбранного голоса.",TextToSpeech.QUEUE_FLUSH,null,"preview");
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.ENGINE));
            ready(TextToSpeech.SUCCESS);
        });
        if(!network){
            Button test=new Button(this);test.setText("Проверить");test.setTextSize(11);test.setAllCaps(false);
            card.addView(test,new LinearLayout.LayoutParams(dp(105),dp(50)));
            test.setOnClickListener(v->testWordBoundaries(voice,packageName));
        }
        container.addView(card);
    }
    /** Probe the selected on-device voice by synthesizing a real cached WAV. */
    private void testWordBoundaries(Voice voice,String enginePackage){
        if(synth==null)return;
        if(voice.isNetworkConnectionRequired())return;
        final String utterance="probe_"+System.currentTimeMillis();
        final String key=enginePackage+":"+voice.getName();
        final java.util.HashSet<Integer> ranges=new java.util.HashSet<>();
        final File probe=new File(getCacheDir(),utterance+".wav");
        Toast.makeText(this,"Проверяем метки произносимых слов…",Toast.LENGTH_SHORT).show();
        synth.setOnUtteranceProgressListener(new UtteranceProgressListener(){
            @Override public void onStart(String id){}
            @Override public void onRangeStart(String id,int from,int to,int frame){
                if(utterance.equals(id)&&to>from&&frame>=0){synchronized(ranges){ranges.add(from);}}
            }
            @Override public void onDone(String id){
                if(!utterance.equals(id))return;
                boolean valid;
                synchronized(ranges){valid=ranges.size()>=3&&probe.isFile()&&probe.length()>256;}
                finishProbe(key,valid,false,probe);
            }
            @Override public void onError(String id){
                if(utterance.equals(id))finishProbe(key,false,true,probe);
            }
        });
        int code=synth.setVoice(voice);
        if(code!=TextToSpeech.SUCCESS){finishProbe(key,false,true,probe);return;}
        int synthesis=synth.synthesizeToFile(
            "Сегодня вечером ветер постепенно стих, а город погрузился в тишину.",
            new android.os.Bundle(),probe,utterance);
        if(synthesis!=TextToSpeech.SUCCESS)finishProbe(key,false,true,probe);
    }
    private void finishProbe(String key,boolean wordTimings,boolean failed,File probe){
        runOnUiThread(()->{
            if(probe!=null&&probe.exists())probe.delete();
            if(isFinishing()||synth==null)return;
            if(!failed){getSharedPreferences("reader",0).edit()
                .putBoolean("tts_word_checked:"+key,true)
                .putBoolean("tts_word_boundary:"+key,wordTimings).apply();}
            Toast.makeText(this,failed?"Синтез недоступен для этого голоса":
                wordTimings?"Подтверждены метки слов":"Метки слов не обнаружены: будет подсветка предложения",
                Toast.LENGTH_LONG).show();
            ready(TextToSpeech.SUCCESS);
        });
    }
    private void chooseEngine(){
        ArrayList<String> names=new ArrayList<>();names.add("Системный по умолчанию");
        for(TextToSpeech.EngineInfo info:engines)names.add(info.label+" ("+info.name+")");
        new SwipeDialogs.Builder(this).setTitle("Выбор движка TTS")
            .setItems(names.toArray(new String[0]),(d,n)->{engine=n==0?"":engines.get(n-1).name;
                getSharedPreferences("reader",0).edit().putString("tts_engine",engine).remove("tts_voice").apply();loadEngine();})
            .setNegativeButton("Отмена",null).show();
    }
    @Override protected void onDestroy(){if(synth!=null){synth.stop();synth.shutdown();}super.onDestroy();}
}
