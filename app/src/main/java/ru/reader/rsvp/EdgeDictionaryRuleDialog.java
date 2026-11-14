package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;

/** Reusable, themed, inline dictionary editor for the Reader and Dictionary activities. */
final class EdgeDictionaryRuleDialog {
    private final Activity activity;
    private final EdgeDictionaryStore store;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final int foreground,surface,accent;
    private MediaPlayer preview;
    private File previewFile;
    private EdgeTtsClient request;
    private AlertDialog dialog;
    private EdgeDictionaryRuleDialog(Activity activity){this.activity=activity;store=EdgeDictionaryStore.get(activity);
        SharedPreferences p=activity.getSharedPreferences("reader",0);
        foreground=p.getInt("fg",0xff30221e);
        surface=UiColorScheme.neutral(activity);accent=UiColorScheme.accent(activity);
    }
    static void show(Activity activity,EdgeDictionaryStore.Rule existing,int type,
        String selected,String context,String replacement,boolean regex,Runnable onSaved){
        new EdgeDictionaryRuleDialog(activity).open(existing,type,selected,context,replacement,regex,onSaved);
    }
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private EditText field(LinearLayout host,String hint,String initial){
        EditText v=new EditText(activity);v.setSingleLine(true);v.setText(initial==null?"":initial);v.setHint(hint);
        v.setTextSize(15);v.setTextColor(foreground);v.setHintTextColor(UiColorScheme.blend(foreground,surface,.5f));
        v.setBackgroundTintList(ColorStateList.valueOf(accent));
        host.addView(v,new LinearLayout.LayoutParams(-1,dp(54)));return v;
    }
    private void open(EdgeDictionaryStore.Rule previous,int type,String selected,String context,String spoken,boolean initialRegex,Runnable onSaved){
        store.ensureMigrated();
        ScrollView scroll=new ScrollView(activity);LinearLayout layout=new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(16),dp(6),dp(16),dp(10));
        layout.setBackground(bg(surface));scroll.addView(layout);
        final EditText find=field(layout,"Найти: слово или выражение",selected);
        final CheckBox ignore=new CheckBox(activity);ignore.setText("Игнорировать при озвучивании");
        ignore.setTextColor(foreground);ignore.setButtonTintList(ColorStateList.valueOf(accent));
        ignore.setChecked(type==EdgeDictionaryStore.IGNORE);layout.addView(ignore);
        LinearLayout spokenRow=new LinearLayout(activity);spokenRow.setGravity(Gravity.CENTER_VERTICAL);
        layout.addView(spokenRow);
        LinearLayout replacementCol=new LinearLayout(activity);replacementCol.setOrientation(1);
        spokenRow.addView(replacementCol,new LinearLayout.LayoutParams(0,-2,1));
        final EditText replace=field(replacementCol,"Произносить как",spoken==null?selected:spoken);
        ImageButton listen=new ImageButton(activity);listen.setImageResource(R.drawable.ic_microphone);
        listen.setColorFilter(foreground);listen.setBackground(bg(surface));
        listen.setContentDescription("Прослушать произношение");
        spokenRow.addView(listen,new LinearLayout.LayoutParams(dp(48),dp(48)));
        listen.setOnClickListener(v->listen(replace.getText().toString()));
        CheckBox regex=new CheckBox(activity);regex.setText("Регулярное выражение (Java Regex)");
        regex.setChecked(initialRegex);regex.setTextColor(foreground);
        regex.setButtonTintList(ColorStateList.valueOf(accent));layout.addView(regex);
        CheckBox contextualEnabled=new CheckBox(activity);
        contextualEnabled.setText("Ограничить правилом для данного контекста");
        contextualEnabled.setTextColor(foreground);contextualEnabled.setButtonTintList(ColorStateList.valueOf(accent));
        contextualEnabled.setChecked(previous!=null&&context!=null&&!context.isEmpty());
        layout.addView(contextualEnabled);
        final EditText contextual=field(layout,"Контекст: часть предложения (не для Regex)",context);
        TextView vowelsTitle=new TextView(activity);vowelsTitle.setText("Ударение: коснитесь нужной гласной");
        vowelsTitle.setTextColor(foreground);vowelsTitle.setTextSize(13);vowelsTitle.setPadding(0,dp(9),0,dp(4));layout.addView(vowelsTitle);
        LinearLayout vowels=new LinearLayout(activity);vowels.setGravity(Gravity.CENTER_VERTICAL);layout.addView(vowels);
        Runnable renderVowels=()->{
            vowels.removeAllViews();boolean visible=!regex.isChecked()&&!ignore.isChecked();
            vowelsTitle.setVisibility(visible?View.VISIBLE:View.GONE);vowels.setVisibility(visible?View.VISIBLE:View.GONE);
            if(!visible)return;
            String raw=replace.getText().toString().replace("\u0301","");
            // Large words/phrases can have multiple vowels: avoid a row wider than the screen.
            int choices=0;for(int i=0;i<raw.length();i++)if("аеёиоуыэюяАЕЁИОУЫЭЮЯ".indexOf(raw.charAt(i))>=0)choices++;
            if(choices>14){TextView hint=new TextView(activity);hint.setText("Для длинной фразы укажите ударение в поле «Произносить как»");hint.setTextColor(foreground);vowels.addView(hint);return;}
            for(int i=0;i<raw.length();i++){
                final int pos=i;char letter=raw.charAt(i);
                if("аеёиоуыэюяАЕЁИОУЫЭЮЯ".indexOf(letter)<0)continue;
                TextView btn=new TextView(activity);btn.setText(String.valueOf(letter));btn.setTextColor(foreground);
                btn.setGravity(Gravity.CENTER);btn.setBackground(bg(UiColorScheme.selected(activity)));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(39),1);lp.rightMargin=dp(3);vowels.addView(btn,lp);
                btn.setOnClickListener(v->{String clear=replace.getText().toString().replace("\u0301","");
                    if(pos<clear.length()){replace.setText(clear.substring(0,pos+1)+"\u0301"+clear.substring(pos+1));replace.setSelection(replace.length());}});
            }
        };
        replace.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){}
            public void afterTextChanged(Editable e){renderVowels.run();}});
        Runnable state=()->{boolean disabled=ignore.isChecked();spokenRow.setVisibility(disabled?View.GONE:View.VISIBLE);
            contextualEnabled.setVisibility(disabled||regex.isChecked()?View.GONE:View.VISIBLE);
            contextual.setVisibility(disabled||regex.isChecked()||!contextualEnabled.isChecked()?View.GONE:View.VISIBLE);
            renderVowels.run();};
        ignore.setOnCheckedChangeListener((b,v)->state.run());
        regex.setOnCheckedChangeListener((b,v)->state.run());
        contextualEnabled.setOnCheckedChangeListener((b,v)->state.run());state.run();
        AlertDialog.Builder builder=new SwipeDialogs.Builder(activity)
            .setTitle(previous==null?"Добавить в словарь Edge TTS":"Редактировать правило")
            .setView(scroll).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null);
        if(previous!=null)builder.setNeutralButton("Удалить",(d,w)->{store.delete(previous.id);if(onSaved!=null)onSaved.run();});
        dialog=builder.create();dialog.setOnDismissListener(d->stopPreview());dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setBackgroundDrawable(bg(surface));}
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            int kind=ignore.isChecked()?EdgeDictionaryStore.IGNORE:EdgeDictionaryStore.REPLACE;
            String pattern=find.getText().toString().trim();String to=kind==EdgeDictionaryStore.IGNORE?"":replace.getText().toString();
            String ctx=kind==EdgeDictionaryStore.IGNORE||regex.isChecked()||!contextualEnabled.isChecked()?
                "":contextual.getText().toString();
            if(regex.isChecked())EdgeSpeechTextTransform.validateReplacement(EdgeSpeechTextTransform.compileUserRegex(pattern),to);
            store.upsert(previous==null?0:previous.id,kind,pattern,ctx,to,regex.isChecked());
            if(onSaved!=null)onSaved.run();dialog.dismiss();
        }catch(Exception e){Toast.makeText(activity,"Не сохранено: "+e.getMessage(),Toast.LENGTH_LONG).show();}});
    }
    private void listen(String phrase){
        if(phrase==null||phrase.trim().isEmpty())return;
        stopPreview();Toast.makeText(activity,"Подготовка произношения…",Toast.LENGTH_SHORT).show();
        SharedPreferences p=activity.getSharedPreferences("reader",0);
        request=EdgeTtsClient.synthesize(phrase,p.getString("edge_voice","ru-RU-SvetlanaNeural"),
            p.getString("edge_rate","+0%"),EdgeSpeechQueue.PREFETCH,new EdgeTtsClient.Callback(){
                public void progress(int parts,long bytes){}
                public void finished(byte[] mp3,int parts,String error){main.post(()->{
                    if(activity.isFinishing()||dialog==null||!dialog.isShowing())return;
                    if(error!=null||mp3==null||mp3.length==0){Toast.makeText(activity,"Ошибка озвучивания: "+error,Toast.LENGTH_LONG).show();return;}
                    try{
                        previewFile=File.createTempFile("edge_rule_", ".mp3",activity.getCacheDir());
                        try(FileOutputStream out=new FileOutputStream(previewFile)){out.write(mp3);}
                        preview=new MediaPlayer();preview.setDataSource(previewFile.getAbsolutePath());
                        preview.setOnCompletionListener(player->stopPreview());preview.prepare();
                        preview.setPlaybackParams(preview.getPlaybackParams().setSpeed(TtsWpm.rate(activity)));
                        preview.start();
                    }catch(Exception ex){Toast.makeText(activity,"Не удалось воспроизвести",Toast.LENGTH_SHORT).show();stopPreview();}
                });}
            });
    }
    private void stopPreview(){if(request!=null){request.cancel();request=null;}
        if(preview!=null){try{preview.release();}catch(Exception ignored){}preview=null;}
        if(previewFile!=null){previewFile.delete();previewFile=null;}
    }
}
