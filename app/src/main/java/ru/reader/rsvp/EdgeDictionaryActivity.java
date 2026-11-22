package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.app.Activity;
import android.content.DialogInterface;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
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
    private LinearLayout entries,tabsHost;
    private EditText filter;
    private int ink,bg,accent,neutral;
    private boolean ignoredOnly=false;
    private int visibleRows=100;
    private static final int PICK_CSV=1901,CREATE_CSV=1902;
    private boolean ioBusy=false;
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
        TextView title=label("Словарь TTS",20);
        toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton plus=new ImageButton(this);plus.setImageResource(R.drawable.ic_plus);
        plus.setColorFilter(UiColorScheme.onAccent(this));plus.setBackground(touch(accent));
        plus.setContentDescription("Добавить правило");
        toolbar.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        plus.setOnClickListener(v->editRule(null,ignoredOnly?EdgeDictionaryStore.IGNORE:EdgeDictionaryStore.REPLACE,"","","",false,false));
        ImageButton more=new ImageButton(this);more.setImageResource(R.drawable.ic_more_vertical);
        more.setColorFilter(ink);more.setBackground(touch(neutral));
        more.setContentDescription("Импорт и экспорт словаря TTS");
        LinearLayout.LayoutParams moreLp=new LinearLayout.LayoutParams(dp(44),dp(44));
        moreLp.leftMargin=dp(8);toolbar.addView(more,moreLp);
        more.setOnClickListener(this::showCsvMenu);

        tabsHost=new LinearLayout(this);tabsHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tabLp=new LinearLayout.LayoutParams(-1,dp(52));tabLp.topMargin=dp(10);page.addView(tabsHost,tabLp);
        LinearLayout content=BrowserTabs.panel(this);page.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        TextView help=label("? Регулярные выражения словаря Edge TTS — справка",13);
        help.setGravity(Gravity.CENTER);help.setPadding(dp(4),dp(8),dp(4),dp(8));
        help.setBackground(fill(neutral));
        content.addView(help);help.setOnClickListener(v->{
            Intent manual=new Intent(this,ActivityInfo.class);
            manual.putExtra("help_section","Регулярные выражения словаря Edge TTS");
            startActivity(manual);
        });
        Runnable refreshTabs=()->{renderDictionaryTabs();visibleRows=100;showEntries();};
        filter=new EditText(this);filter.setSingleLine(true);filter.setHint("Поиск по словарю");
        content.addView(filter);
        ScrollView scroll=new ScrollView(this);
        entries=new LinearLayout(this);entries.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(entries);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
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
    private void renderDictionaryTabs(){
        if(tabsHost==null)return;
        tabsHost.removeAllViews();
        tabsHost.addView(BrowserTabs.strip(this,new String[]{"Замены","Игнорировать"},ignoredOnly?1:0,index->{
            ignoredOnly=index==1;visibleRows=100;renderDictionaryTabs();showEntries();
        }),new LinearLayout.LayoutParams(-1,dp(52)));
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
            String text=(rule.enabled?"":"(выключено) ")+(rule.regex?"Regex · ":"")+rule.pattern;
            if(!ignoredOnly){text+="  →  "+(rule.replacement.isEmpty()?"(удалить)":rule.replacement);
                if(!rule.context.isEmpty())text+="\nКонтекст: "+rule.context;}
            if(!rule.note.isEmpty())text+="\n"+rule.note;
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

    private void showCsvMenu(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("Импорт словаря (.csv)").setOnMenuItemClickListener(item->{
            if(!ioBusy)pickCsvFile();return true;
        });
        menu.getMenu().add("Экспорт словаря (.csv)").setOnMenuItemClickListener(item->{
            if(!ioBusy)createCsvFile();return true;
        });
        if(store.latestCsvBackup()!=null){
            menu.getMenu().add("Восстановить последнюю копию").setOnMenuItemClickListener(item->{
                if(!ioBusy)restoreLastBackup();return true;
            });
        }
        menu.show();
    }
    private void pickCsvFile(){
        Intent request=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        request.addCategory(Intent.CATEGORY_OPENABLE);
        request.setType("*/*");
        request.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/csv","text/comma-separated-values",
            "text/plain","application/vnd.ms-excel","application/octet-stream"});
        request.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(request,PICK_CSV);
    }
    private void createCsvFile(){
        Intent request=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        request.addCategory(Intent.CATEGORY_OPENABLE);
        request.setType("text/csv");
        request.putExtra(Intent.EXTRA_TITLE,"PROChitalka_TTS_Dictionary_"+
            new SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date())+".csv");
        request.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(request,CREATE_CSV);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==PICK_CSV)readCsvInBackground(uri,null);
        else if(request==CREATE_CSV)exportCsvInBackground(uri);
    }
    private void exportCsvInBackground(Uri target){
        if(ioBusy)return;ioBusy=true;
        new Thread(()->{
            try{
                List<TtsDictionaryCsv.Row> rows=store.csvRows();
                try(OutputStream out=getContentResolver().openOutputStream(target,"w")){
                    if(out==null)throw new java.io.IOException("Файл не открывается для записи");
                    OutputStreamWriter writer=new OutputStreamWriter(out,StandardCharsets.UTF_8);
                    TtsDictionaryCsv.write(writer,rows);writer.flush();
                }
                ui(()->{ioBusy=false;Toast.makeText(this,
                    "Экспортировано правил: "+rows.size()+" (.csv)",Toast.LENGTH_LONG).show();});
            }catch(Exception error){uiError("Ошибка экспорта CSV",error);}
        },"tts-dictionary-csv-export").start();
    }
    private void restoreLastBackup(){
        File backup=store.latestCsvBackup();
        if(backup==null){Toast.makeText(this,"Резервная копия не найдена",Toast.LENGTH_SHORT).show();return;}
        readCsvInBackground(null,backup);
    }
    private void readCsvInBackground(Uri source,File backup){
        if(ioBusy)return;ioBusy=true;
        new Thread(()->{
            try{
                List<TtsDictionaryCsv.Row> incoming;
                if(backup!=null){
                    try(InputStream input=new FileInputStream(backup)){
                        incoming=TtsDictionaryCsv.read(new InputStreamReader(input,StandardCharsets.UTF_8));
                    }
                }else{
                    try(InputStream input=getContentResolver().openInputStream(source)){
                        if(input==null)throw new java.io.IOException("Не удалось открыть CSV");
                        incoming=TtsDictionaryCsv.read(new InputStreamReader(input,StandardCharsets.UTF_8));
                    }
                }
                // Evaluate ALL modes off the UI thread: merge can exceed a limit,
                // while replacing the entire dictionary may still be valid.
                for(TtsDictionaryCsv.Row rule:incoming)EdgeDictionaryStore.validateCsvRule(rule);
                List<TtsDictionaryCsv.Row> existing=store.csvRows();
                TtsDictionaryImportPlan[] plans=new TtsDictionaryImportPlan[3];
                String[] failures=new String[3];boolean anyValid=false;
                TtsDictionaryImportPlan.Mode[] modes=TtsDictionaryImportPlan.Mode.values();
                for(int i=0;i<modes.length;i++){
                    try{plans[i]=TtsDictionaryImportPlan.build(existing,incoming,modes[i]);anyValid=true;}
                    catch(Exception error){failures[i]=error.getMessage();}
                }
                if(!anyValid)throw new IllegalArgumentException(failures[0]);
                ui(()->{ioBusy=false;showCsvPreview(incoming,plans,failures,backup!=null);});
            }catch(Exception error){uiError("Ошибка чтения CSV",error);}
        },"tts-dictionary-csv-preview").start();
    }
    private void showCsvPreview(List<TtsDictionaryCsv.Row> incoming,TtsDictionaryImportPlan[] plans,
                                String[] failures,boolean restoring){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16),dp(6),dp(16),dp(8));
        TextView summary=label("",14);
        summary.setPadding(0,0,0,dp(12));form.addView(summary);
        TextView description=label("Режим импорта:",15);form.addView(description);
        RadioGroup choices=new RadioGroup(this);choices.setOrientation(LinearLayout.VERTICAL);
        String[] names={"Объединить: сохранить мои правила",
            "Объединить: обновить совпадающие из CSV",
            "Заменить весь словарь (с резервной копией)"};
        TtsDictionaryImportPlan.Mode[] modes=TtsDictionaryImportPlan.Mode.values();
        for(int i=0;i<names.length;i++){
            RadioButton radio=new RadioButton(this);radio.setText(names[i]);radio.setTextSize(14);
            radio.setTextColor(ink);radio.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
            radio.setId(9000+i);
            if(plans[i]==null){radio.setEnabled(false);
                radio.setText(names[i]+" (недоступно: "+failures[i]+")");}
            choices.addView(radio);
        }
        int initial=(restoring&&plans[2]!=null)?2:0;
        if(plans[initial]==null)for(int i=0;i<plans.length;i++)if(plans[i]!=null){initial=i;break;}
        choices.check(9000+initial);form.addView(choices);
        choices.setOnCheckedChangeListener((group,checked)->{
            int index=checked-9000;if(index<0||index>=plans.length||plans[index]==null)return;
            TtsDictionaryImportPlan preview=plans[index];
            summary.setText("В файле: "+incoming.size()+" строк правил\n"+
                "Новых: "+preview.added+", совпадений: "+preview.unchanged+
                ", конфликтов: "+preview.conflicts+", обновлено: "+preview.updated+
                ", удалено: "+preview.deleted+"\n"+
                "Изменения будут применены одной транзакцией SQLite.");
        });
        // setOnCheckedChangeListener does not fire for the already-selected choice
        TtsDictionaryImportPlan initialPlan=plans[initial];
        summary.setText("В файле: "+incoming.size()+" строк правил\n"+
            "Новых: "+initialPlan.added+", совпадений: "+initialPlan.unchanged+
            ", конфликтов: "+initialPlan.conflicts+", обновлено: "+initialPlan.updated+
            ", удалено: "+initialPlan.deleted+"\n"+
            "Изменения будут применены одной транзакцией SQLite.");
        TextView warning=label("CSV: UTF-8; разделитель — точка с запятой.\n"+
            "В Excel открывайте все столбцы как текст, чтобы избежать автозамены дат и чисел.\n"+
            "После импорта пересинтезируются только затронутые аудиофрагменты.",12);
        warning.setPadding(0,dp(14),0,0);form.addView(warning);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=new SwipeDialogs.Builder(this)
            .setTitle(restoring?"Восстановление словаря из копии":"Импорт словаря TTS (.csv)")
            .setView(scroll).setNegativeButton("Отмена",null)
            .setPositiveButton("Импортировать",null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{
            if(ioBusy)return;
            int selected=choices.getCheckedRadioButtonId()-9000;
            if(selected<0||selected>=modes.length||plans[selected]==null)return;
            TtsDictionaryImportPlan.Mode mode=modes[selected];
            if(mode==TtsDictionaryImportPlan.Mode.REPLACE_ALL){
                new SwipeDialogs.Builder(this).setTitle("Заменить весь словарь?")
                    .setMessage("Текущие правила будут сохранены в резервную копию. "+
                        "Все записи словаря заменятся строками выбранного CSV.")
                    .setNegativeButton("Отмена",null)
                    .setPositiveButton("Заменить",(d,w)->commitCsvImport(incoming,mode,dialog))
                    .show();
            }else commitCsvImport(incoming,mode,dialog);
        });
    }
    private void commitCsvImport(List<TtsDictionaryCsv.Row> incoming,
                                 TtsDictionaryImportPlan.Mode mode,AlertDialog dialog){
        if(ioBusy)return;ioBusy=true;
        new Thread(()->{
            try{
                TtsDictionaryImportPlan result=store.importCsv(incoming,mode);
                ui(()->{
                    ioBusy=false;if(dialog.isShowing())dialog.dismiss();
                    showEntries();
                    Toast.makeText(this,result.changed?
                        "Импорт: добавлено "+result.added+", обновлено "+result.updated:
                        "Изменений нет. Совпадающие правила сохранены.",Toast.LENGTH_LONG).show();
                });
            }catch(Exception error){uiError("Ошибка импорта CSV",error);}
        },"tts-dictionary-csv-import").start();
    }
    private void ui(Runnable task){main.post(()->{if(!isFinishing()&&!isDestroyed())task.run();});}
    private void uiError(String prefix,Exception error){ui(()->{
        ioBusy=false;
        Toast.makeText(this,prefix+": "+error.getMessage(),Toast.LENGTH_LONG).show();
    });}

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
