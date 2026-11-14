package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Large SQLite-backed dictionary editor with literal/Regex replacements and ignores. */
public final class EdgeDictionaryActivity extends NavigationActivity {
    static final String EXTRA_WORD="dictionary_selected_word",EXTRA_CONTEXT="dictionary_selected_context";
    static final String EXTRA_SENTENCE="dictionary_current_sentence";
    private SharedPreferences prefs;
    private EdgeDictionaryStore store;
    private LinearLayout entries;
    private EditText filter;
    private int ink,bg,accent,neutral;
    private boolean ignoredOnly=false;
    private int visibleRows=100;
    private MediaPlayer preview;
    private EdgeTtsClient request;
    private File previewFile;
    private final Handler main=new Handler(Looper.getMainLooper());

    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable fill(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private android.graphics.drawable.Drawable touch(int color){
        return new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(UiColorScheme.blend(color,ink,.15f)),fill(color),null);
    }
    private TextView label(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ink);return v;}
    private EditText input(String hint,String text,LinearLayout parent){
        EditText edit=new EditText(this);edit.setSingleLine(true);edit.setHint(hint);edit.setText(text);
        edit.setTextColor(ink);edit.setHintTextColor(UiColorScheme.blend(ink,bg,.42f));
        edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));
        parent.addView(edit,new LinearLayout.LayoutParams(-1,dp(54)));return edit;
    }
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        store=EdgeDictionaryStore.get(this);store.ensureMigrated();
        bg=prefs.getInt("bg",0xfff3e7d5);ink=prefs.getInt("fg",0xff30221e);
        accent=UiColorScheme.accent(this);neutral=UiColorScheme.neutral(this);
        render();
    }
    private void render(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(15),dp(12),dp(15),dp(8));page.setBackgroundColor(bg);
        setContentView(page);SafeSystemBars.matchBackground(this,bg);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(toolbar);
        ImageButton drawer=new ImageButton(this);drawer.setImageResource(R.drawable.ic_menu_lines);
        drawer.setColorFilter(ink);drawer.setBackground(touch(neutral));
        toolbar.addView(drawer,new LinearLayout.LayoutParams(dp(44),dp(44)));
        drawer.setOnClickListener(v->UniversalDrawer.show(this));
        TextView title=label("Словарь Edge TTS",20);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton plus=new ImageButton(this);plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(UiColorScheme.onAccent(this));plus.setBackground(touch(accent));
        plus.setContentDescription("Добавить правило");
        toolbar.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        plus.setOnClickListener(v->editRule(null,ignoredOnly?EdgeDictionaryStore.IGNORE:EdgeDictionaryStore.REPLACE,"","","",false,false));

        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(6),0,dp(6));page.addView(tabs);
        TextView replaceTab=label("Замены",15),ignoreTab=label("Игнорировать",15);
        replaceTab.setGravity(Gravity.CENTER);ignoreTab.setGravity(Gravity.CENTER);
        tabs.addView(replaceTab,new LinearLayout.LayoutParams(0,dp(47),1));
        tabs.addView(ignoreTab,new LinearLayout.LayoutParams(0,dp(47),1));
        TextView help=label("? Регулярные выражения словаря Edge TTS — справка",13);
        help.setGravity(Gravity.CENTER);help.setPadding(dp(4),dp(8),dp(4),dp(8));
        help.setBackground(fill(neutral));
        page.addView(help);help.setOnClickListener(v->{
            Intent manual=new Intent(this,ActivityInfo.class);
            manual.putExtra("help_section","Регулярные выражения словаря Edge TTS");
            startActivity(manual);
        });
        Runnable refreshTabs=()->{
            replaceTab.setBackground(fill(ignoredOnly?neutral:accent));
            ignoreTab.setBackground(fill(ignoredOnly?accent:neutral));
            replaceTab.setTextColor(ignoredOnly?ink:UiColorScheme.onAccent(this));
            ignoreTab.setTextColor(ignoredOnly?UiColorScheme.onAccent(this):ink);
            visibleRows=100;showEntries();
        };
        replaceTab.setOnClickListener(v->{ignoredOnly=false;refreshTabs.run();});
        ignoreTab.setOnClickListener(v->{ignoredOnly=true;refreshTabs.run();});
        filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Поиск по словарю");
        page.addView(filter);
        ScrollView scroll=new ScrollView(this);
        entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        filter.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int cnt,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable e){visibleRows=100;showEntries();}
        });refreshTabs.run();
        String selected=getIntent().getStringExtra(EXTRA_WORD);
        if(selected!=null&&!selected.trim().isEmpty()){
            editRule(null,EdgeDictionaryStore.REPLACE,selected.trim(),
                getIntent().getStringExtra(EXTRA_CONTEXT)==null?"":getIntent().getStringExtra(EXTRA_CONTEXT),
                selected.trim(),false,false);
        }
    }
    private void showEntries(){
        if(entries==null||store==null)return;
        entries.removeAllViews();
        int kind=ignoredOnly?EdgeDictionaryStore.IGNORE:EdgeDictionaryStore.REPLACE;
        int total=store.count(kind);
        List<EdgeDictionaryStore.Rule> rows=store.query(kind,
            filter==null?"":filter.getText().toString(),visibleRows+1);
        TextView count=label((ignoredOnly?"Игнорирование":"Замены")+" · "+total+" правил",13);
        count.setPadding(dp(8),dp(8),0,dp(8));entries.addView(count);
        int shown=Math.min(visibleRows,rows.size());
        if(shown==0){TextView empty=label("Записей не найдено. Добавьте новое правило.",15);
            empty.setPadding(dp(8),dp(20),0,dp(20));entries.addView(empty);}
        for(int i=0;i<shown;i++){
            EdgeDictionaryStore.Rule rule=rows.get(i);
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(7),dp(8),dp(7));row.setBackground(fill(neutral));
            String text=(rule.regex?"Regex · ":"")+rule.pattern;
            if(!ignoredOnly){text+="  →  "+(rule.replacement.isEmpty()?"(удалить)":rule.replacement);
                if(!rule.context.isEmpty())text+="\nКонтекст: "+rule.context;}
            TextView value=label(text,15);value.setMaxLines(3);
            row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
            value.setOnClickListener(v->editExisting(rule));
            if(!ignoredOnly){
                ImageButton listen=new ImageButton(this);listen.setImageResource(R.drawable.ic_microphone);
                listen.setColorFilter(ink);listen.setBackground(touch(UiColorScheme.selected(this)));
                listen.setContentDescription("Прослушать замену");row.addView(listen,new LinearLayout.LayoutParams(dp(42),dp(42)));
                listen.setOnClickListener(v->listen(rule.replacement));
            }
            ImageButton edit=new ImageButton(this);edit.setImageResource(R.drawable.ic_edit_name);
            edit.setColorFilter(ink);edit.setBackground(touch(UiColorScheme.selected(this)));row.addView(edit,new LinearLayout.LayoutParams(dp(42),dp(42)));
            edit.setOnClickListener(v->editExisting(rule));
            LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
            params.bottomMargin=dp(7);entries.addView(row,params);
        }
        if(rows.size()>visibleRows){
            Button more=new Button(this);more.setText("Показать ещё 100");more.setAllCaps(false);
            entries.addView(more);more.setOnClickListener(v->{visibleRows+=100;showEntries();});
        }
    }
    private void editExisting(EdgeDictionaryStore.Rule rule){
        editRule(rule,rule.kind,rule.pattern,rule.context,rule.replacement,rule.regex,false);
    }
    private void editRule(EdgeDictionaryStore.Rule current,int kind,String pattern,
                          String context,String spoken,boolean initialRegex,boolean symbols){
        EdgeDictionaryRuleDialog.show(this,current,kind,pattern,context,spoken,initialRegex,this::showEntries);
    }
    private void listen(String text){
        if(text.trim().isEmpty())return;
        stopPreview();Toast.makeText(this,"Подготовка произношения…",Toast.LENGTH_SHORT).show();
        String voice=prefs.getString("edge_voice","ru-RU-SvetlanaNeural");
        String rate=prefs.getString("edge_rate","+0%");
        request=EdgeTtsClient.synthesize(text,voice,rate,EdgeSpeechQueue.PREFETCH,new EdgeTtsClient.Callback(){
            public void progress(int chunks,long size){}
            public void finished(byte[] mp3,int chunks,String error){main.post(()->{
                if(isFinishing()||isDestroyed())return;
                if(error!=null||mp3==null||mp3.length==0){
                    Toast.makeText(EdgeDictionaryActivity.this,"Не удалось озвучить: "+
                        (error==null?"нет аудио":error),Toast.LENGTH_LONG).show();return;
                }
                try{
                    previewFile=File.createTempFile("edge_dictionary_",".mp3",getCacheDir());
                    try(FileOutputStream output=new FileOutputStream(previewFile)){output.write(mp3);}
                    preview=new MediaPlayer();preview.setDataSource(previewFile.getAbsolutePath());
                    preview.setOnCompletionListener(player->stopPreview());preview.prepare();
                    preview.setPlaybackParams(preview.getPlaybackParams().setSpeed(TtsWpm.rate(EdgeDictionaryActivity.this)));
                    preview.start();
                }catch(Exception ex){Toast.makeText(EdgeDictionaryActivity.this,"Ошибка воспроизведения",Toast.LENGTH_SHORT).show();stopPreview();}
            });}
        });
    }
    private void stopPreview(){if(request!=null){request.cancel();request=null;}
        if(preview!=null){try{preview.release();}catch(Exception ignored){}preview=null;}
        if(previewFile!=null){previewFile.delete();previewFile=null;}
    }
    @Override protected void onResume(){super.onResume();
        if(prefs!=null&&store!=null){int fresh=UiColorScheme.accent(this),surface=UiColorScheme.neutral(this);
            if(fresh!=accent||surface!=neutral){accent=fresh;neutral=surface;render();}}
    }
    @Override protected void onDestroy(){stopPreview();super.onDestroy();}
}
