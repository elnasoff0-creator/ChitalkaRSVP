package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Locale;

/** Reader-compatible editable dictionary for EdgeSpeechTextTransform, with pronunciation preview. */
public final class EdgeDictionaryActivity extends NavigationActivity {
    static final String EXTRA_WORD="dictionary_selected_word",EXTRA_CONTEXT="dictionary_selected_context";
    static final String EXTRA_SENTENCE="dictionary_current_sentence";
    private String activeSentence="";
    private SharedPreferences prefs;
    private LinearLayout entries;
    private EditText filter;
    private int ink,bg;
    private MediaPlayer preview;
    private EdgeTtsClient request;
    private File previewFile;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ArrayList<Rule> rules=new ArrayList<>();
    private final ArrayList<String> ignored=new ArrayList<>();
    private static class Rule {
        String word,context,spoken;
        Rule(String w,String c,String s){word=w;context=c;spoken=s;}
        String line(){return word+(context.isEmpty()?"":"|"+context)+"="+spoken;}
    }
    private int dp(int v){return (int)(getResources().getDisplayMetrics().density*v+.5f);}
    private GradientDrawable fill(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(12));return d;}
    private TextView label(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        bg=prefs.getInt("bg",0xfff3e7d5);ink=prefs.getInt("fg",0xff30221e);
        parse();
        if(prefs.getString(EdgeSpeechTextTransform.DICTIONARY,"").contains("***=."))save();
        activeSentence=getIntent().getStringExtra(EXTRA_SENTENCE);
        if(activeSentence==null)activeSentence="";
        render(); // Select a word in the dictionary, not in the reader's visual spans.
    }
    private void parse(){ignored.clear();
        for(String line:prefs.getString(EdgeSpeechTextTransform.IGNORED,"").split("\\r?\\n")){
            String value=line.trim();if(!value.isEmpty()&&!ignored.contains(value))ignored.add(value);
        }
        rules.clear();for(String line:prefs.getString(EdgeSpeechTextTransform.DICTIONARY,"").split("\\r?\\n")){
        int eq=line.indexOf('=');if(eq<1||eq==line.length()-1)continue;
        String left=line.substring(0,eq).trim();int bar=left.indexOf('|');
        String w=(bar<0?left:left.substring(0,bar)).trim();String ctx=bar<0?"":left.substring(bar+1).trim();
        String spoken=line.substring(eq+1).trim();
        if("***".equals(w)&&ctx.isEmpty()&&".".equals(spoken)){
            if(!ignored.contains("***"))ignored.add("***");continue;
        }
        if(!w.isEmpty())rules.add(new Rule(w,ctx,spoken));
    }}
    private void save(){StringBuilder b=new StringBuilder();int count=0;
        for(Rule r:rules){if(count>=100)break;String line=r.line();if(b.length()+line.length()+1>8192)break;
            if(b.length()>0)b.append('\n');b.append(line);count++;}
        StringBuilder blocked=new StringBuilder();
        for(String item:ignored){if(blocked.length()+item.length()+1>8192)break;
            if(blocked.length()>0)blocked.append('\n');blocked.append(item);}
        prefs.edit().putString(EdgeSpeechTextTransform.DICTIONARY,b.toString())
            .putString(EdgeSpeechTextTransform.IGNORED,blocked.toString()).apply();
    }
    private void render(){
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(15),dp(12),dp(15),dp(8));page.setBackgroundColor(bg);setContentView(page);SafeSystemBars.matchBackground(this,bg);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);page.addView(bar);
        ImageButton drawer=new ImageButton(this);drawer.setImageResource(R.drawable.ic_menu_lines);drawer.setColorFilter(ink);drawer.setBackground(fill(bg));
        bar.addView(drawer,new LinearLayout.LayoutParams(dp(44),dp(44)));drawer.setOnClickListener(v->UniversalDrawer.show(this));
        TextView heading=label("Словарь MS Edge",20);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        // Symbols have their own entry action and a punctuation-friendly
        // keyboard; they are NOT restricted to dictionary word tokens.
        Button addSymbols=new Button(this);addSymbols.setText("Знаки +");
        addSymbols.setTextSize(12);addSymbols.setAllCaps(false);addSymbols.setTextColor(ink);
        bar.addView(addSymbols,new LinearLayout.LayoutParams(dp(90),dp(44)));
        addSymbols.setOnClickListener(v->editRule(-1,"","","",true));
        Button addIgnored=new Button(this);addIgnored.setText("Игнорировать");
        addIgnored.setTextSize(12);addIgnored.setAllCaps(false);addIgnored.setTextColor(ink);
        page.addView(addIgnored,new LinearLayout.LayoutParams(-1,dp(44)));
        addIgnored.setOnClickListener(v->editIgnored(-1));
        ImageButton add=new ImageButton(this);add.setImageResource(R.drawable.ic_plus);add.setColorFilter(ink);add.setBackground(fill(bg));
        bar.addView(add,new LinearLayout.LayoutParams(dp(44),dp(44)));add.setOnClickListener(v->editRule(-1,"","",""));
        filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Поиск по словарю");page.addView(filter);
        ScrollView scroll=new ScrollView(this);entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int c,int after){}
            public void afterTextChanged(Editable e){showEntries();}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
        });showEntries();
    }
    private void showEntries(){if(entries==null)return;entries.removeAllViews();
        String needle=filter==null?"":filter.getText().toString().toLowerCase(Locale.ROOT);
        if(rules.isEmpty()&&ignored.isEmpty()){TextView empty=label("Словарь пуст. Добавьте слово, замену знаков или игнорируемый текст.",15);
            empty.setPadding(12,22,12,22);entries.addView(empty);return;}
        for(int i=0;i<rules.size();i++){int id=i;Rule r=rules.get(i);
            if(!r.word.toLowerCase(Locale.ROOT).contains(needle)&&!r.spoken.toLowerCase(Locale.ROOT).contains(needle))continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(7),dp(7),dp(7),dp(7));
            TextView value=label(r.word+"  →  "+r.spoken+(r.context.isEmpty()?"":"\nКонтекст: "+r.context),15);
            row.addView(value,new LinearLayout.LayoutParams(0,-2,1));value.setOnClickListener(v->editRule(id,r.word,r.context,r.spoken));
            ImageButton listen=new ImageButton(this);listen.setImageResource(R.drawable.ic_tts_engine);listen.setColorFilter(ink);
            row.addView(listen,new LinearLayout.LayoutParams(dp(42),dp(42)));
            listen.setOnClickListener(v->listen(r.spoken));
            ImageButton edit=new ImageButton(this);edit.setImageResource(R.drawable.ic_edit_name);edit.setColorFilter(ink);
            row.addView(edit,new LinearLayout.LayoutParams(dp(42),dp(42)));edit.setOnClickListener(v->editRule(id,r.word,r.context,r.spoken));
            entries.addView(row,new LinearLayout.LayoutParams(-1,dp(r.context.isEmpty()?64:82)));
            View divider=new View(this);divider.setBackgroundColor((ink&0x00ffffff)|0x22000000);
            entries.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        }
        if(!ignored.isEmpty()){
            TextView section=label("ИГНОРИРОВАТЬ · не отправлять в Edge TTS",13);
            section.setPadding(dp(8),dp(14),0,dp(7));entries.addView(section);
        }
        for(int i=0;i<ignored.size();i++){
            int index=i;String item=ignored.get(i);
            if(!item.toLowerCase(Locale.ROOT).contains(needle))continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8),dp(5),dp(8),dp(5));
            TextView label=label("Пропуск: "+item,15);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));label.setGravity(Gravity.CENTER_VERTICAL);
            label.setOnClickListener(v->editIgnored(index));
            ImageButton edit=new ImageButton(this);edit.setImageResource(R.drawable.ic_edit_name);edit.setColorFilter(ink);
            row.addView(edit,new LinearLayout.LayoutParams(dp(42),dp(42)));
            edit.setOnClickListener(v->editIgnored(index));entries.addView(row);
        }
    }
    private void editIgnored(int index){
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setHint("Например: *** или целое предложение");
        if(index>=0)field.setText(ignored.get(index));
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20),0,dp(20),0);body.addView(field);
        TextView help=label("Совпадения исключаются из отправки в WebSocket. Если весь фрагмент состоит из игнорируемого текста, он пропускается без синтеза. Внутри обычного предложения удаляется только совпавшая часть.",13);
        body.addView(help);
        AlertDialog.Builder builder=new AlertDialog.Builder(this)
            .setTitle(index>=0?"Изменить игнорируемый текст":"Добавить в игнорирование")
            .setView(body).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if(index>=0)builder.setNeutralButton("Удалить",(d,w)->{ignored.remove(index);save();showEntries();});
        AlertDialog dialog=builder.create();dialog.setOnShowListener(v->{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
                String value=field.getText().toString().trim();
                if(value.isEmpty()||value.length()>400||value.contains("\n")||value.contains("\r")){
                    Toast.makeText(this,"Введите строку до 400 символов",Toast.LENGTH_LONG).show();return;
                }
                if(index>=0)ignored.set(index,value);
                else if(!ignored.contains(value)&&ignored.size()<100)ignored.add(value);
                else {Toast.makeText(this,"Уже существует или достигнут лимит 100 строк",Toast.LENGTH_LONG).show();return;}
                save();showEntries();dialog.dismiss();
            });
        });dialog.show();
    }
    private EditText input(String hint,String value,LinearLayout host){
        EditText edit=new EditText(this);edit.setHint(hint);edit.setText(value);edit.setSingleLine(true);
        host.addView(edit,new LinearLayout.LayoutParams(-1,dp(55)));return edit;
    }
    private void editRule(int index,String word,String context,String spoken){
        editRule(index,word,context,spoken,!word.isEmpty()&&!word.matches(".*[\\p{L}\\p{N}].*"));
    }
    private void editRule(int index,String word,String context,String spoken,boolean symbols){
        LinearLayout pane=new LinearLayout(this);pane.setOrientation(LinearLayout.VERTICAL);pane.setPadding(dp(15),0,dp(15),0);
        LinearLayout wordRow=new LinearLayout(this);wordRow.setGravity(Gravity.CENTER_VERTICAL);
        pane.addView(wordRow,new LinearLayout.LayoutParams(-1,-2));
        EditText original=new EditText(this);original.setSingleLine(true);
        original.setHint(symbols?"Знаки в книге (например !)":"Слово в книге");original.setText(word);
        wordRow.addView(original,new LinearLayout.LayoutParams(0,dp(55),1));
        if(!symbols){
            Button fromSentence=new Button(this);fromSentence.setAllCaps(false);
            fromSentence.setText("Из предложения");fromSentence.setTextSize(11);
            wordRow.addView(fromSentence,new LinearLayout.LayoutParams(dp(128),dp(52)));
            fromSentence.setContentDescription("Добавить слово из текущего предложения");
            fromSentence.setOnClickListener(v->chooseWordFromSentence(original));
        }
        if(symbols)original.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pronunciation=input(symbols?"Заменить на (например -)":"Произношение / замена",spoken.isEmpty()?word:spoken,pane);
        if(symbols)pronunciation.setInputType(InputType.TYPE_CLASS_TEXT|
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        TextView stress=label("Ударение: выберите гласную",13);pane.addView(stress);
        if(symbols)stress.setVisibility(View.GONE);
        LinearLayout vowels=new LinearLayout(this);vowels.setGravity(Gravity.CENTER_VERTICAL);pane.addView(vowels);
        if(symbols)vowels.setVisibility(View.GONE);
        if(symbols){TextView hint=label("Символьная замена перед Edge TTS. Например: ! → -. Для полного пропуска используйте «Игнорировать».",12);pane.addView(hint);}
        final Runnable renderVowels=()->{
            vowels.removeAllViews();String text=pronunciation.getText().toString().replace("\u0301","");
            for(int i=0;i<text.length();i++){
                int letter=i;char c=text.charAt(i);if("аеёиоуыэюяАЕЁИОУЫЭЮЯ".indexOf(c)<0)continue;
                TextView button=label(String.valueOf(c),17);button.setGravity(Gravity.CENTER);
                button.setBackground(fill(0x22a26949));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1);lp.rightMargin=dp(3);
                vowels.addView(button,lp);
                button.setOnClickListener(v->{String clean=pronunciation.getText().toString().replace("\u0301","");
                    pronunciation.setText(clean.substring(0,letter+1)+"\u0301"+clean.substring(letter+1));
                    pronunciation.setSelection(pronunciation.length());});
            }
        };
        pronunciation.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int x){}
            public void afterTextChanged(Editable e){renderVowels.run();}
            public void onTextChanged(CharSequence s,int a,int b,int c){}
        });renderVowels.run();
        EditText contextInput=input("Контекст (необязательно)",context,pane);
        TextView contextHelp=label("Контекст: точная фраза рядом со словом. Для омографов создайте несколько отдельных правил.",12);
        pane.addView(contextHelp);
        Button previewButton=new Button(this);previewButton.setText("▶ Прослушать");pane.addView(previewButton);
        previewButton.setOnClickListener(v->listen(pronunciation.getText().toString().trim()));
        AlertDialog.Builder dlg=new AlertDialog.Builder(this).setTitle(symbols?
            (index<0?"Добавить знаки":"Редактировать знаки"):
            (index<0?"Добавить слово":"Редактировать слово")).setView(pane)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,w)->{
                String o=original.getText().toString().trim(),s=pronunciation.getText().toString().trim(),c=contextInput.getText().toString().trim();
                if(o.isEmpty()||s.isEmpty()||o.contains("=")||o.contains("|")||c.contains("=")||c.contains("|")||o.contains("\n")||s.contains("\n")){
                    Toast.makeText(this,"Неверные знаки, слово или замена",Toast.LENGTH_LONG).show();return;}
                Rule r=new Rule(o,c,s);if(index>=0&&index<rules.size())rules.set(index,r);else if(rules.size()<100)rules.add(r);
                save();showEntries();
            });
        if(index>=0)dlg.setNeutralButton("Удалить",(d,w)->{rules.remove(index);save();showEntries();});
        dlg.show();
    }
    /** The source sentence is passed by ReaderActivity; the book view does not need a dictionary selection span. */
    private void chooseWordFromSentence(EditText target){
        if(activeSentence.trim().isEmpty()){
            Toast.makeText(this,"Текущее предложение недоступно. Откройте словарь из книги.",Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<String> words=new ArrayList<>();
        java.util.regex.Matcher matcher=java.util.regex.Pattern
            .compile("[\\p{L}\\p{N}]+(?:[-’'][\\p{L}\\p{N}]+)*").matcher(activeSentence);
        while(matcher.find()&&words.size()<100)words.add(matcher.group());
        if(words.isEmpty()){
            Toast.makeText(this,"В предложении нет слов",Toast.LENGTH_SHORT).show();return;
        }
        String[] choices=words.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Выберите слово из предложения")
            .setItems(choices,(dialog,which)->{
                target.setText(choices[which]);target.setSelection(target.length());
            }).setNegativeButton("Отмена",null).show();
    }
    private void listen(String word){
        if(word.isEmpty())return;
        stopPreview();Toast.makeText(this,"Подготовка произношения…",Toast.LENGTH_SHORT).show();
        String voice=prefs.getString("edge_voice","ru-RU-SvetlanaNeural");
        String rate=prefs.getString("edge_rate","+0%");
        request=EdgeTtsClient.synthesize(word,voice,rate,EdgeSpeechQueue.PREFETCH,new EdgeTtsClient.Callback(){
            public void progress(int chunks,long size){}
            public void finished(byte[] mp3,int chunks,String error){main.post(()->{
                if(isFinishing()||isDestroyed())return;
                if(error!=null||mp3==null||mp3.length==0){Toast.makeText(EdgeDictionaryActivity.this,"Не удалось озвучить: "+(error==null?"нет аудио":error),Toast.LENGTH_LONG).show();return;}
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
