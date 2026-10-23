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
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(1);layout.setBackgroundColor(bg);layout.setPadding(dp(12),dp(22),dp(12),dp(10));setContentView(layout);
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
        TextView choose=row("Движок: "+(engine.isEmpty()?"Системный по умолчанию":engine)+"   ▾",17);choose.setOnClickListener(v->chooseEngine());items.addView(choose);
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
        system.setOnClickListener(v->{try{startActivity(new Intent("com.android.settings.TTS_SETTINGS"));}catch(Exception e){new AlertDialog.Builder(this).setMessage("Настройки TTS недоступны").setPositiveButton("ОК",null).show();}});items.addView(system);
    });}
    private void addVoice(Voice voice,boolean network){addVoiceTo(items,voice,network);}
    private void addVoiceTo(LinearLayout container,Voice voice,boolean network){
        String chosen=getSharedPreferences("reader",0).getString("tts_voice","");
        TextView item=row((voice.getName().equals(chosen)?"◉ ":"○ ")+voice.getName()+"\n"+(network?"Сетевой":"Локальный")+" · "+(engine.isEmpty()?"Системный TTS":engine)+"     ▶",15);
        item.setOnClickListener(v->{
            getSharedPreferences("reader",0).edit().putString("tts_engine",engine.isEmpty()?synth.getDefaultEngine():engine).putString("tts_voice",voice.getName()).apply();
            synth.setVoice(voice);synth.speak("Привет! Это пример выбранного голоса.",TextToSpeech.QUEUE_FLUSH,null,"preview");
            startService(new Intent(this,AudioPlaybackService.class).setAction(AudioPlaybackService.ENGINE));
            ready(TextToSpeech.SUCCESS);
        });container.addView(item);
    }
    private void chooseEngine(){
        ArrayList<String> names=new ArrayList<>();names.add("Системный по умолчанию");
        for(TextToSpeech.EngineInfo info:engines)names.add(info.label+" ("+info.name+")");
        new AlertDialog.Builder(this).setTitle("Выбор движка TTS")
            .setItems(names.toArray(new String[0]),(d,n)->{engine=n==0?"":engines.get(n-1).name;
                getSharedPreferences("reader",0).edit().putString("tts_engine",engine).remove("tts_voice").apply();loadEngine();})
            .setNegativeButton("Отмена",null).show();
    }
    @Override protected void onDestroy(){if(synth!=null){synth.stop();synth.shutdown();}super.onDestroy();}
}
