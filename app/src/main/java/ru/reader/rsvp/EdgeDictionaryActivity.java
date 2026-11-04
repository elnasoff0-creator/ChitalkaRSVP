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
    private int ink,bg;
    private boolean ignoredOnly=false,selectedFromSentence=false;
    private int visibleRows=100;
    private String activeSentence="";
    private MediaPlayer preview;
    private EdgeTtsClient request;
    private File previewFile;
    private final Handler main=new Handler(Looper.getMainLooper());

    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable fill(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private TextView label(String value,int size){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(ink);return v;}
    private EditText input(String hint,String text,LinearLayout parent){
        EditText edit=new EditText(this);edit.setSingleLine(true);edit.setHint(hint);edit.setText(text);
        parent.addView(edit,new LinearLayout.LayoutParams(-1,dp(54)));return edit;
    }
    @Override public void onCreate(Bundle saved){super.onCreate(saved);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        store=EdgeDictionaryStore.get(this);store.ensureMigrated();
        bg=prefs.getInt("bg",0xfff3e7d5);ink=prefs.getInt("fg",0xff30221e);
        String sentence=getIntent().getStringExtra(EXTRA_SENTENCE);activeSentence=sentence==null?"":sentence;
        render();
    }
    private void render(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(15),dp(12),dp(15),dp(8));page.setBackgroundColor(bg);
        setContentView(page);SafeSystemBars.matchBackground(this,bg);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(toolbar);
        ImageButton drawer=new ImageButton(this);drawer.setImageResource(R.drawable.ic_menu_lines);
        drawer.setColorFilter(ink);drawer.setBackground(fill(bg));
        toolbar.addView(drawer,new LinearLayout.LayoutParams(dp(44),dp(44)));
        drawer.setOnClickListener(v->UniversalDrawer.show(this));
        TextView title=label("Словарь Edge TTS",20);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        Button symbols=new Button(this);symbols.setText("Символы +");symbols.setAllCaps(false);
        symbols.setTextSize(12);symbols.setTextColor(ink);
        toolbar.addView(symbols,new LinearLayout.LayoutParams(dp(90),dp(44)));
        symbols.setOnClickListener(v->editRule(null,EdgeDictionaryStore.REPLACE,"!","","-",false,true));
        ImageButton plus=new ImageButton(this);plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(ink);plus.setBackground(fill(bg));
        toolbar.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        plus.setOnClickListener(v->editRule(null,EdgeDictionaryStore.REPLACE,"","","",false,false));

        LinearLayout tabs=new LinearLayout(this);tabs.setPadding(0,dp(6),0,dp(6));page.addView(tabs);
        TextView replaceTab=label("Замены",15),ignoreTab=label("Игнорировать",15);
        replaceTab.setGravity(Gravity.CENTER);ignoreTab.setGravity(Gravity.CENTER);
        tabs.addView(replaceTab,new LinearLayout.LayoutParams(0,dp(47),1));
        tabs.addView(ignoreTab,new LinearLayout.LayoutParams(0,dp(47),1));
        TextView help=label("? Регулярные выражения словаря Edge TTS — справка",13);
        help.setGravity(Gravity.CENTER);help.setPadding(dp(4),dp(8),dp(4),dp(8));
        page.addView(help);help.setOnClickListener(v->{
            Intent manual=new Intent(this,ActivityInfo.class);
            manual.putExtra("help_section","Регулярные выражения словаря Edge TTS");
            startActivity(manual);
        });
        Button addIgnore=new Button(this);addIgnore.setText("＋ Добавить исключение");
        addIgnore.setAllCaps(false);addIgnore.setTextSize(12);addIgnore.setTextColor(ink);
        page.addView(addIgnore,new LinearLayout.LayoutParams(-1,dp(42)));
        addIgnore.setOnClickListener(v->editRule(null,EdgeDictionaryStore.IGNORE,"","","",false,false));
        Button blankLines=new Button(this);blankLines.setText("Убрать пустые строки · Regex");
        blankLines.setAllCaps(false);blankLines.setTextSize(12);blankLines.setTextColor(ink);
        page.addView(blankLines,new LinearLayout.LayoutParams(-1,dp(40)));
        blankLines.setVisibility(ignoredOnly?View.VISIBLE:View.GONE);
        blankLines.setOnClickListener(v->editRule(null,EdgeDictionaryStore.IGNORE,
            "(?m)^[ \\t]*\\r?\\n","","",true,false));
        Runnable refreshTabs=()->{
            replaceTab.setBackground(fill(ignoredOnly?0x11aaaaaa:0x22b58963));
            ignoreTab.setBackground(fill(ignoredOnly?0x22b58963:0x11aaaaaa));
            addIgnore.setVisibility(ignoredOnly?View.VISIBLE:View.GONE);
            blankLines.setVisibility(ignoredOnly?View.VISIBLE:View.GONE);
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
            row.setPadding(dp(8),dp(7),dp(8),dp(7));row.setBackground(fill(0x12b68b64));
            String text=(rule.regex?"Regex · ":"")+rule.pattern;
            if(!ignoredOnly){text+="  →  "+(rule.replacement.isEmpty()?"(удалить)":rule.replacement);
                if(!rule.context.isEmpty())text+="\nКонтекст: "+rule.context;}
            TextView value=label(text,15);value.setMaxLines(3);
            row.addView(value,new LinearLayout.LayoutParams(0,-2,1));
            value.setOnClickListener(v->editExisting(rule));
            if(!ignoredOnly){
                ImageButton listen=new ImageButton(this);listen.setImageResource(R.drawable.ic_tts_engine);
                listen.setColorFilter(ink);row.addView(listen,new LinearLayout.LayoutParams(dp(42),dp(42)));
                listen.setOnClickListener(v->listen(rule.replacement));
            }
            ImageButton edit=new ImageButton(this);edit.setImageResource(R.drawable.ic_edit_name);
            edit.setColorFilter(ink);row.addView(edit,new LinearLayout.LayoutParams(dp(42),dp(42)));
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
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18),0,dp(18),dp(8));
        EditText find=input(symbols?"Знак или последовательность":"Найти · слово или Regex",pattern,layout);
        if(symbols)find.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText replacement=null;
        if(kind==EdgeDictionaryStore.REPLACE){
            replacement=input("Заменить на (можно оставить пустым)",spoken,layout);
        }
        final EditText replace=replacement;
        CheckBox regex=new CheckBox(this);regex.setText("Регулярное выражение (Java Regex)");
        regex.setChecked(initialRegex);regex.setTextColor(ink);layout.addView(regex);
        EditText contextual=null;
        if(kind==EdgeDictionaryStore.REPLACE){
            contextual=input("Контекст · для обычных замен",context,layout);
            contextual.setEnabled(!initialRegex);
        }
        final EditText contextField=contextual;
        final boolean stressAvailable=kind==EdgeDictionaryStore.REPLACE&&!symbols;
        final TextView stressLabel=stressAvailable?label("Ударение: выберите гласную",13):null;
        final LinearLayout vowelButtons=stressAvailable?new LinearLayout(this):null;
        if(stressAvailable){
            layout.addView(stressLabel);
            vowelButtons.setGravity(Gravity.CENTER_VERTICAL);
            layout.addView(vowelButtons);
        }
        final Runnable renderVowels=()->{
            if(!stressAvailable)return;
            vowelButtons.removeAllViews();
            stressLabel.setVisibility(regex.isChecked()?View.GONE:View.VISIBLE);
            vowelButtons.setVisibility(regex.isChecked()?View.GONE:View.VISIBLE);
            if(regex.isChecked())return;
            String raw=replace.getText().toString().replace("\u0301","");
            for(int i=0;i<raw.length();i++){
                final int letter=i;char ch=raw.charAt(i);
                if("аеёиоуыэюяАЕЁИОУЫЭЮЯ".indexOf(ch)<0)continue;
                TextView button=label(String.valueOf(ch),17);button.setGravity(Gravity.CENTER);
                button.setBackground(fill(0x22a26949));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);
                lp.rightMargin=dp(3);vowelButtons.addView(button,lp);
                button.setOnClickListener(v->{
                    String clear=replace.getText().toString().replace("\u0301","");
                    if(letter<clear.length()){
                        replace.setText(clear.substring(0,letter+1)+"\u0301"+clear.substring(letter+1));
                        replace.setSelection(replace.length());
                    }
                });
            }
        };
        if(stressAvailable)replace.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int x){}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
            public void afterTextChanged(Editable e){renderVowels.run();}
        });
        regex.setOnCheckedChangeListener((button,checked)->{
            if(contextField!=null){contextField.setEnabled(!checked);
                contextField.setHint(checked?"Контекст не используется в Regex":"Контекст · для обычных замен");}
            renderVowels.run();
        });
        renderVowels.run();
        if(kind==EdgeDictionaryStore.REPLACE&&!symbols){
            Button fromSentence=new Button(this);fromSentence.setAllCaps(false);
            fromSentence.setText("Из предложения");layout.addView(fromSentence);
            fromSentence.setOnClickListener(v->chooseWordFromSentence(find));
        }
        TextView hint=label(kind==EdgeDictionaryStore.IGNORE?
            "Совпадения удаляются только из речи Edge TTS. Пустые строки: (?m)^[ \\t]*\\r?\\n":
            "Символьная замена перед Edge TTS. Например: ! → -. Regex поддерживает $1 и ${имя}; пустая замена удаляет совпадение.",12);
        layout.addView(hint);
        TextView testHeading=label("Проверить на примере:",13);layout.addView(testHeading);
        String initialExample=kind==EdgeDictionaryStore.IGNORE&&pattern.contains("\\n")?
            "Первый абзац.\n\nВторой абзац.":
            (activeSentence.isEmpty()?"На полку положили книгу. Возле полки стоял стол.":activeSentence);
        EditText example=input("Текст для предварительной проверки",initialExample,layout);
        example.setSingleLine(false);example.setMinLines(2);
        TextView result=label("",14);layout.addView(result);
        Runnable test=()->{
            try{
                String candidate=find.getText().toString();
                boolean isRegex=regex.isChecked();
                if(candidate.isEmpty())throw new IllegalArgumentException("Введите шаблон");
                if(isRegex)EdgeSpeechTextTransform.validateReplacement(EdgeSpeechTextTransform.compileUserRegex(candidate),
                    replace==null?"":replace.getText().toString());
                EdgeSpeechTextTransform.Entry entry=new EdgeSpeechTextTransform.Entry(
                    kind,candidate,contextField==null||isRegex?"":contextField.getText().toString(),
                    replace==null?"":replace.getText().toString(),isRegex);
                EdgeSpeechTextTransform.Options options=new EdgeSpeechTextTransform.Options(
                    "off",",",Arrays.asList(entry),0);
                result.setText("Результат: "+options.apply(example.getText().toString()).spoken);
            }catch(Exception error){result.setText("Ошибка: "+error.getMessage());}
        };
        Button tryIt=new Button(this);tryIt.setText("Проверить Regex / замену");
        tryIt.setAllCaps(false);layout.addView(tryIt);tryIt.setOnClickListener(v->test.run());
        if(kind==EdgeDictionaryStore.REPLACE){
            Button sample=new Button(this);sample.setAllCaps(false);sample.setText("▶ Прослушать результат");
            layout.addView(sample);sample.setOnClickListener(v->{
                try{EdgeSpeechTextTransform.Entry entry=new EdgeSpeechTextTransform.Entry(
                    kind,find.getText().toString(),regex.isChecked()?"":contextField.getText().toString(),
                    replace.getText().toString(),regex.isChecked());
                    String text=new EdgeSpeechTextTransform.Options("off",",",Arrays.asList(entry),0)
                        .apply(example.getText().toString()).spoken;
                    listen(text);
                }catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
            });
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(layout);
        AlertDialog.Builder builder=new AlertDialog.Builder(this)
            .setTitle(current==null?(kind==EdgeDictionaryStore.IGNORE?"Добавить игнорирование":"Добавить замену"):
                "Редактировать правило")
            .setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if(current!=null)builder.setNeutralButton("Удалить",(d,w)->{
            store.delete(current.id);showEntries();});
        AlertDialog dialog=builder.create();dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                try{
                    String from=find.getText().toString();
                    String to=replace==null?"":replace.getText().toString();
                    String ctx=contextField==null||regex.isChecked()?"":contextField.getText().toString();
                    store.upsert(current==null?0:current.id,kind,from,ctx,to,regex.isChecked());
                    if(selectedFromSentence&&!activeSentence.isEmpty()&&
                        getIntent().hasExtra("dictionary_selected_word_index")){
                        Intent answer=new Intent().putExtra("dictionary_selected_word_index",
                            getIntent().getIntExtra("dictionary_selected_word_index",0));
                        setResult(RESULT_OK,answer);
                    }
                    showEntries();dialog.dismiss();
                }catch(Exception ex){Toast.makeText(this,"Не сохранено: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
            });
        });dialog.show();test.run();
    }
    private void chooseWordFromSentence(EditText target){
        selectedFromSentence=true;
        if(activeSentence.trim().isEmpty()){
            Toast.makeText(this,"Текущее предложение недоступно. Откройте словарь из книги.",Toast.LENGTH_LONG).show();
            return;
        }
        java.util.ArrayList<String> words=new java.util.ArrayList<>();
        java.util.regex.Matcher matcher=Pattern.compile("[\\p{L}\\p{N}]+(?:[-’'][\\p{L}\\p{N}]+)*").matcher(activeSentence);
        while(matcher.find()&&words.size()<100)words.add(matcher.group());
        if(words.isEmpty()){Toast.makeText(this,"В предложении нет слов",Toast.LENGTH_SHORT).show();return;}
        new AlertDialog.Builder(this).setTitle("Выберите слово из предложения")
            .setItems(words.toArray(new String[0]),(dialog,which)->{
                target.setText(words.get(which));target.setSelection(target.length());
            }).setNegativeButton("Отмена",null).show();
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
    @Override protected void onDestroy(){stopPreview();super.onDestroy();}
}
