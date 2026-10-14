package ru.reader.rsvp;

import android.app.Activity;
import android.content.Intent;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Separate, persistent reader settings with live typography/background preview. */
public class SettingsActivity extends Activity {
    private SharedPreferences prefs;
    private int currentTab=0; // 0 reading, 1 audio, 2 online
    private LinearLayout root, list;
    private TextView preview, sizeValue;
    private int bg, fg, font, size, display;
    private final String[] fontNames={"Без засечек", "С засечками", "Моноширинный"};
    private final int[] backgrounds={0xffffffff,0xfff3e7d5,0xfff1e0c7,0xffdfe9d9,0xffe3e8ee,0xffe6dff1,0xff1e2025,0xff303438,0xff283d32,0xffded8c9,0xfffff0da,0xffdde5df};
    private final int[] textColors={0xff202020,0xff30221e,0xff444444,0xff5b4638,0xffe8e5db,0xffffffff,0xff365541,0xff604b7b};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        if(getIntent().getBooleanExtra("audio_tab",false)) currentTab=1;
        if(getIntent().getBooleanExtra("online_tab",false)) currentTab=2;
        load();draw();
    }
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}    
    void load(){bg=prefs.getInt("bg",0xfff3e7d5);fg=prefs.getInt("fg",0xff30221e);font=prefs.getInt("font",1);size=prefs.getInt("size",36);display=prefs.getInt("display",1);}    
    void put(String k,int v){prefs.edit().putInt(k,v).apply();}
    private PackageInfo installedPackageInfo(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0);}catch(Exception ignored){return null;}
    }
    String installedVersion(){PackageInfo info=installedPackageInfo();return info!=null && info.versionName!=null ? info.versionName : "неизвестна";}
    long installedCode(){PackageInfo info=installedPackageInfo();if(info==null)return 0;return Build.VERSION.SDK_INT>=28 ? info.getLongVersionCode() : info.versionCode;}
    int contrast(int background){double v=(Color.red(background)*0.299+Color.green(background)*0.587+Color.blue(background)*0.114);return v<140?Color.WHITE:0xff30221e;}
    GradientDrawable rounded(int c,int rad){GradientDrawable gd=new GradientDrawable();gd.setColor(c);gd.setCornerRadius(dp(rad));return gd;}
    Typeface face(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    TextView text(String str,int sp,boolean bold){TextView t=new TextView(this);t.setText(str);t.setTextSize(sp);t.setTextColor(contrast(bg));t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
    void gap(int height){View v=new View(this);list.addView(v,new LinearLayout.LayoutParams(-1,dp(height)));}
    void header(String name){TextView t=text(name,18,true);t.setPadding(0,dp(14),0,dp(9));list.addView(t);}    
    TextView action(String name,Runnable click){TextView t=text(name,16,false);t.setBackground(rounded(contrast(bg)==Color.WHITE?0xff404348:0xfffff9f1,15));t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setOnClickListener(v->click.run());return t;}
    String onOff(boolean value){return value?"включено":"выключено";}
    void toggle(String key, Runnable after){prefs.edit().putBoolean(key,!prefs.getBoolean(key,true)).apply(); if(after!=null) after.run();}
    void draw(){
        root=col();root.setBackgroundColor(bg);root.setPadding(dp(14),dp(25),dp(14),dp(9));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(contrast(bg));back.setPadding(dp(12),dp(12),dp(12),dp(12));back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView name=text("Настройки чтения",24,true);name.setTypeface(Typeface.SERIF,Typeface.BOLD);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        TextView version=text("ChitalkaRSVP v" + installedVersion() + " · сборка " + installedCode(),11,false);
        version.setGravity(Gravity.END);version.setPadding(0,0,dp(4),dp(8));version.setContentDescription("Текущая установленная версия " + installedVersion() + ", сборка " + installedCode());root.addView(version);
        ScrollView sc=new ScrollView(this);list=col();sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tabs=new LinearLayout(this);
        tabs.addView(action("Чтение",()->{currentTab=0;draw();}),new LinearLayout.LayoutParams(0,dp(55),1));
        LinearLayout.LayoutParams midLp=new LinearLayout.LayoutParams(0,dp(55),1); midLp.leftMargin=dp(6);
        tabs.addView(action("Аудио",()->{currentTab=1;draw();}),midLp);
        LinearLayout.LayoutParams lastLp=new LinearLayout.LayoutParams(0,dp(55),1); lastLp.leftMargin=dp(6);
        tabs.addView(action("Онлайн",()->{currentTab=2;draw();}),lastLp);
        list.addView(tabs);
        header("Версия и обновления");
        TextView installed=text("Установлена: v" + installedVersion() + " (сборка " + installedCode() + ")",14,false);installed.setPadding(dp(4),0,0,dp(9));list.addView(installed);
        list.addView(action("Проверить версию в GitHub", () -> new UpdateManager(this).checkVersion()));
        gap(10);
        if(currentTab==1){drawAudioSettings();return;}
        if(currentTab==2){drawOnlineSettings();return;}
        header("Предпросмотр текста");
        preview=new TextView(this);preview.setText("Глава 1\n\nВ дороге солнце мягко освещало страницы книги. Читать должно быть удобно для глаз.");preview.setLineSpacing(dp(5),1.18f);preview.setPadding(dp(19),dp(21),dp(19),dp(21));list.addView(preview,new LinearLayout.LayoutParams(-1,-2));applyPreview();
        header("Шрифт");
        list.addView(action(fontNames[font]+"   ▾",()->new AlertDialog.Builder(this).setTitle("Выбор шрифта").setSingleChoiceItems(fontNames,font,(d,n)->{font=n;put("font",font);d.dismiss();draw();}).setNegativeButton("Отмена",null).show()));
        gap(9);header("Шрифт");
        LinearLayout sizing=new LinearLayout(this);sizing.setGravity(Gravity.CENTER_VERTICAL);
        Button minus=new Button(this);minus.setText("−");minus.setOnClickListener(v->changeSize(-2));sizing.addView(minus,new LinearLayout.LayoutParams(dp(70),dp(54)));
        sizeValue=text(String.valueOf(size),20,true);sizeValue.setGravity(Gravity.CENTER);sizing.addView(sizeValue,new LinearLayout.LayoutParams(0,dp(54),1));
        Button plus=new Button(this);plus.setText("+");plus.setOnClickListener(v->changeSize(2));sizing.addView(plus,new LinearLayout.LayoutParams(dp(70),dp(54)));
        list.addView(sizing);
        header("Тема чтения");
        LinearLayout themes=new LinearLayout(this);themes.setOrientation(LinearLayout.HORIZONTAL);
        theme(themes,"Светлая",0xffffffff,0xff202020);theme(themes,"Сепия",0xfff3e7d5,0xff30221e);theme(themes,"Ночная",0xff1e2025,0xffe8e5db);theme(themes,"Мягкая",0xffdfe9d9,0xff223a29);list.addView(themes);
        header("Цвет фона — палитра");palette(backgrounds,true);
        header("Цвет текста — палитра");palette(textColors,false);
        gap(8);header("Скорочтение");
        String[] modes={"Только одно слово","Предыдущее и следующее","Текущее и следующее"};
        list.addView(action("Показ слов: "+modes[display],()->new AlertDialog.Builder(this).setTitle("Показ слов").setSingleChoiceItems(modes,display,(d,i)->{display=i;put("display",i);d.dismiss();draw();}).setNegativeButton("Отмена",null).show()));
        gap(8);list.addView(action("Дополнительные паузы",this::pauses));gap(18);
    }
    void drawAudioSettings(){
        header("Управление аудиокнигами");
        list.addView(action("+ Добавить папку с аудиокнигой",()->startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_action","import"))));gap(8);
        list.addView(action("Озвучить текущую текстовую книгу",()->startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_action","tts"))));gap(8);
        list.addView(action("Голос TTS и высота тона",()->startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_action","voice"))));gap(8);
        list.addView(action("Аудиозакладки",()->startActivity(new Intent(this,AudioPlayerActivity.class).putExtra("audio_action","marks"))));gap(8);
    }
    void drawOnlineSettings(){
        header("Онлайн-озвучивание MRT / Edge TTS");
        list.addView(action("Автоозвучка следующих фрагментов: " + onOff(prefs.getBoolean("mrt_auto_continue",true)),()->{toggle("mrt_auto_continue", this::draw);}));gap(8);
        list.addView(action("Предзагрузка аудио в кэш: " + onOff(prefs.getBoolean("mrt_prefetch",true)),()->{prefs.edit().putBoolean("mrt_prefetch",!prefs.getBoolean("mrt_prefetch",true)).apply();draw();}));gap(8);
        list.addView(action("Количество фрагментов предзагрузки: " + MrtPrefetchSettings.get(prefs),()-> MrtPrefetchSettings.show(this,prefs,count->draw())));gap(8);
        list.addView(action("Размер онлайн-фрагмента: " + MrtSegmentSettings.get(prefs) + " символов",()-> MrtSegmentSettings.show(this,prefs,value->draw())));gap(8);
        TextView note=text("Количество и размер фрагментов используются в MRT и Edge. Аудио каждого движка хранится отдельно.",13,false);
        note.setPadding(dp(4),0,dp(4),dp(6));list.addView(note);
        header("Edge TTS · голоса и скорость");
        list.addView(action("Голос Edge: " + (prefs.getString("edge_voice","ru-RU-SvetlanaNeural").contains("Dmitry")?"Дмитрий":"Светлана"),()->{
            String[] options={"Светлана · русский","Дмитрий · русский"};
            String[] values={"ru-RU-SvetlanaNeural","ru-RU-DmitryNeural"};
            new AlertDialog.Builder(this).setTitle("Голос Edge TTS").setItems(options,(dialog,index)->{
                prefs.edit().putString("edge_voice",values[index]).apply();draw();
            }).setNegativeButton("Отмена",null).show();
        }));gap(8);
        list.addView(action("Скорость Edge: " + prefs.getString("edge_rate","+0%"),()->{
            String[] rates={"-30%","-20%","-10%","+0%","+10%","+20%","+30%","+40%"};
            new AlertDialog.Builder(this).setTitle("Скорость Edge TTS").setItems(rates,(dialog,index)->{
                prefs.edit().putString("edge_rate",rates[index]).apply();draw();
            }).setNegativeButton("Отмена",null).show();
        }));gap(10);
    }
    GradientDrawable outlined(int color,int radius){GradientDrawable d=rounded(color,radius);d.setStroke(dp(1),0xff938475);return d;}
    void theme(LinearLayout row,String name,int background,int ink){TextView chip=text(name,13,false);chip.setTextColor(ink);chip.setGravity(Gravity.CENTER);chip.setBackground(outlined(background,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);lp.setMargins(dp(2),0,dp(2),0);row.addView(chip,lp);chip.setOnClickListener(v->{bg=background;fg=ink;put("bg",bg);put("fg",fg);draw();});}
    void palette(int[] colors,boolean background){
        for(int rowStart=0;rowStart<colors.length;rowStart+=4){LinearLayout row=new LinearLayout(this);for(int k=rowStart;k<Math.min(colors.length,rowStart+4);k++){
            int color=colors[k];TextView tile=new TextView(this);tile.setText(color==(background?bg:fg)?"✓":" ");tile.setGravity(Gravity.CENTER);tile.setTextSize(19);tile.setTextColor(contrast(color));tile.setBackground(outlined(color,13));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,lp);
            tile.setOnClickListener(v->{if(background){bg=color;put("bg",bg);if(contrast(bg)==contrast(fg)) {fg=contrast(bg);put("fg",fg);}draw();}else{fg=color;put("fg",fg);draw();}});
        }list.addView(row);}list.addView(action("Настроить RGB вручную",()->colorDialog(background)));
    }
    void applyPreview(){if(preview==null)return;preview.setTextColor(fg);preview.setTextSize(Math.max(16,(int)(size*.60f)));preview.setTypeface(face());preview.setBackground(rounded(bg==0xff1e2025?0xff31343a:0xfffff9f1,20));}
    void changeSize(int delta){size=Math.max(16,Math.min(76,size+delta));put("size",size);sizeValue.setText(String.valueOf(size));applyPreview();}
    void pauses(){
        String[] labels={"После знаков пунктуации","На длинных словах (12+ букв)","В конце предложения"};
        String[] keys={"pausePunctuation","pauseLong","pauseSentence"};
        boolean[] checked=new boolean[3];for(int i=0;i<3;i++)checked[i]=prefs.getBoolean(keys[i],true);
        LinearLayout panel=col();panel.setPadding(dp(14),dp(8),dp(14),0);
        TextView pct=text("Дополнительная задержка: "+prefs.getInt("pausePercent",100)+"%",15,true);panel.addView(pct);
        SeekBar strength=new SeekBar(this);strength.setMax(300);strength.setProgress(prefs.getInt("pausePercent",100));panel.addView(strength);
        strength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){pct.setText("Дополнительная задержка: "+n+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        new AlertDialog.Builder(this).setTitle("Паузы в скорочтении").setMultiChoiceItems(labels,checked,(d,n,v)->checked[n]=v)
            .setView(panel).setPositiveButton("Сохранить",(d,w)->{SharedPreferences.Editor e=prefs.edit();for(int i=0;i<3;i++)e.putBoolean(keys[i],checked[i]);e.putInt("pausePercent",strength.getProgress());e.apply();})
            .setNegativeButton("Отмена",null).show();
    }
    void colorDialog(boolean background){
        int c=background?bg:fg;
        LinearLayout wrapper=col();wrapper.setPadding(dp(20),0,dp(20),0);
        View sample=new View(this);sample.setBackground(outlined(c,8));
        LinearLayout.LayoutParams sampleLp=new LinearLayout.LayoutParams(dp(58),dp(58));sampleLp.gravity=Gravity.CENTER_HORIZONTAL;sampleLp.bottomMargin=dp(12);
        wrapper.addView(sample,sampleLp);
        SeekBar[] bars=new SeekBar[3];String[] channels={"Красный","Зелёный","Синий"};
        int[] rgb={Color.red(c),Color.green(c),Color.blue(c)};
        for(int i=0;i<3;i++){wrapper.addView(text(channels[i],14,false));SeekBar bar=new SeekBar(this);bar.setMax(255);bar.setProgress(rgb[i]);bars[i]=bar;wrapper.addView(bar);}
        for(SeekBar bar:bars)bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean from){sample.setBackground(outlined(Color.rgb(bars[0].getProgress(),bars[1].getProgress(),bars[2].getProgress()),8));}
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle("Выбор RGB").setView(wrapper)
            .setPositiveButton("Применить",(d,w)->{int color=Color.rgb(bars[0].getProgress(),bars[1].getProgress(),bars[2].getProgress());if(background){bg=color;put("bg",bg);}else{fg=color;put("fg",fg);}draw();})
            .setNegativeButton("Отмена",null).show();
    }
}
