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
import android.widget.EditText;
import android.text.InputType;

/** Separate, persistent reader settings with live typography/background preview. */
public class SettingsActivity extends Activity {
    private static final int NO_COLOR=0x00000000;
    private static final int NORMAL=0,RSVP=1,CURSOR=2;

    private SharedPreferences prefs;
    private int currentTab=0; // 0 appearance, 1 reading, 2 audio, 3 online, 4 Chteico
    private LinearLayout root, list;
    private int chteicoHelpTapCount=0;
    private TextView preview, sizeValue;
    private int bg, fg, font, size, display, buttonColor, accentButtonColor;
    private final String[] fontNames={"Без засечек", "С засечками", "Моноширинный"};
    private final int[] backgrounds={0xffffffff,0xfff3e7d5,0xfff1e0c7,0xffdfe9d9,0xffe3e8ee,0xffe6dff1,0xff1e2025,0xff303438,0xff283d32,0xffded8c9,0xfffff0da,0xffdde5df};
    private final int[] textColors={0xff202020,0xff30221e,0xff444444,0xff5b4638,0xffe8e5db,0xff365541,0xff604b7b};
    private final int[] accentColors={NO_COLOR,0xffa26949,0xff915439,0xff2e6be5,0xffd96c1a,0xff4c9a60,0xff9454d2,0xff39424f};

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        prefs=getSharedPreferences("reader",MODE_PRIVATE);
        if(getIntent().getBooleanExtra("audio_tab",false)) currentTab=2;
        if(getIntent().getBooleanExtra("online_tab",false)) currentTab=3;
        if(getIntent().getBooleanExtra("rsvp_tab",false)) currentTab=1;
        if(getIntent().getBooleanExtra("chteico_tab",false)) currentTab=4;
        load();draw();
    }
    int dp(int n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}    
    void load(){bg=prefs.getInt("bg",0xfff3e7d5);fg=prefs.getInt("fg",0xff30221e);font=prefs.getInt("font",1);size=prefs.getInt("size",36);display=prefs.getInt("display",1);buttonColor=prefs.getInt("button_color",NO_COLOR);accentButtonColor=prefs.getInt("button_accent_color",NO_COLOR);}    
    void put(String k,int v){prefs.edit().putInt(k,v).apply();}
    private PackageInfo installedPackageInfo(){
        try{return getPackageManager().getPackageInfo(getPackageName(),0);}catch(Exception ignored){return null;}
    }
    String installedVersion(){PackageInfo info=installedPackageInfo();return info!=null && info.versionName!=null ? info.versionName : "неизвестна";}
    long installedCode(){PackageInfo info=installedPackageInfo();if(info==null)return 0;return Build.VERSION.SDK_INT>=28 ? info.getLongVersionCode() : info.versionCode;}
    int contrast(int background){double v=(Color.red(background)*0.299+Color.green(background)*0.587+Color.blue(background)*0.114);return v<140?Color.WHITE:0xff30221e;}
    GradientDrawable rounded(int c,int rad){GradientDrawable gd=new GradientDrawable();gd.setColor(c);gd.setCornerRadius(dp(rad));return gd;}
    GradientDrawable outlined(int color,int radius){GradientDrawable d=rounded(color,radius);d.setStroke(dp(1),0xff938475);return d;}
    int cardColor(){return contrast(bg)==Color.WHITE?0xff404348:0xfffff9f1;}
    int activeColor(){return accentButtonColor==NO_COLOR?0xffa26949:accentButtonColor;}
    Typeface face(){return font==1?Typeface.SERIF:font==2?Typeface.MONOSPACE:Typeface.SANS_SERIF;}
    LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    TextView text(String str,int sp,boolean bold){TextView t=new TextView(this);t.setText(str);t.setTextSize(sp);t.setTextColor(contrast(bg));t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);return t;}
    void gap(int height){View v=new View(this);list.addView(v,new LinearLayout.LayoutParams(-1,dp(height)));}
    void header(String name){TextView t=text(name,18,true);t.setPadding(0,dp(14),0,dp(9));list.addView(t);}    
    TextView action(String name,Runnable click){TextView t=text(name,16,false);t.setBackground(rounded(cardColor(),15));t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setOnClickListener(v->click.run());return t;}
    TextView action(String name,int icon,Runnable click){TextView t=action(name,click);t.setCompoundDrawablesWithIntrinsicBounds(icon,0,0,0);t.setCompoundDrawablePadding(dp(10));for(android.graphics.drawable.Drawable d:t.getCompoundDrawables())if(d!=null)d.mutate().setTint(contrast(bg));return t;}
    TextView tabAction(String name,int icon,boolean active,Runnable click){
        TextView t=text(name,11,false);t.setGravity(Gravity.CENTER);
        android.graphics.drawable.Drawable d=getDrawable(icon);
        if(d!=null){d=d.mutate();d.setTint(active?0xffffffff:contrast(bg));d.setBounds(0,0,dp(24),dp(24));t.setCompoundDrawables(null,d,null,null);}
        t.setCompoundDrawablePadding(dp(3));t.setTextColor(active?0xffffffff:contrast(bg));
        t.setBackground(rounded(active?activeColor():cardColor(),15));
        t.setPadding(dp(2),dp(8),dp(2),dp(7));t.setOnClickListener(v->click.run());return t;
    }
    void draw(){
        root=col();root.setBackgroundColor(bg);root.setPadding(dp(14),dp(25),dp(14),dp(9));setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(contrast(bg));back.setPadding(dp(12),dp(12),dp(12),dp(12));back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView name=text("Настройки",24,true);name.setTypeface(Typeface.SERIF,Typeface.BOLD);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));root.addView(top);
        ScrollView sc=new ScrollView(this);list=col();sc.addView(list);root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout tabs=new LinearLayout(this);
        int[] icons={R.drawable.ic_tab_appearance,R.drawable.ic_book,R.drawable.ic_tab_music,R.drawable.ic_tab_online,R.drawable.ic_chteico_nav};
        String[] names={"Оформление","Чтение","Аудио","Онлайн","Чтейцо"};
        for(int i=0;i<names.length;i++){
            final int tab=i;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(67),1);
            if(i>0)lp.leftMargin=dp(5);
            tabs.addView(tabAction(names[i],icons[i],currentTab==i,()->{currentTab=tab;draw();}),lp);
        }
        list.addView(tabs);
        if(currentTab==1){drawRsvpSettings();return;}
        if(currentTab==2){drawAudioSettings();return;}
        if(currentTab==3){drawOnlineSettings();return;}
        if(currentTab==4){drawChteicoSettings();return;}
        preview=new TextView(this);preview.setText("Предпросмотр текста");preview.setGravity(Gravity.CENTER);preview.setLineSpacing(dp(5),1.18f);preview.setPadding(dp(19),dp(21),dp(19),dp(21));LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(-1,dp(90));previewLp.topMargin=dp(15);list.addView(preview,previewLp);applyPreview();
        header("Шрифт");
        list.addView(action(fontNames[font]+"   ▾",()->new AlertDialog.Builder(this).setTitle("Выбор шрифта").setSingleChoiceItems(fontNames,font,(d,n)->{font=n;put("font",font);d.dismiss();draw();}).setNegativeButton("Отмена",null).show()));
        gap(9);header("Размер шрифта");
        LinearLayout sizing=new LinearLayout(this);sizing.setGravity(Gravity.CENTER_VERTICAL);
        Button minus=new Button(this);minus.setText("−");minus.setOnClickListener(v->changeSize(-2));sizing.addView(minus,new LinearLayout.LayoutParams(dp(70),dp(54)));
        sizeValue=text(String.valueOf(size),20,true);sizeValue.setGravity(Gravity.CENTER);sizing.addView(sizeValue,new LinearLayout.LayoutParams(0,dp(54),1));
        Button plus=new Button(this);plus.setText("+");plus.setOnClickListener(v->changeSize(2));sizing.addView(plus,new LinearLayout.LayoutParams(dp(70),dp(54)));
        list.addView(sizing);
        header("Тема чтения");
        LinearLayout themes=new LinearLayout(this);themes.setOrientation(LinearLayout.HORIZONTAL);
        theme(themes,"Светлая",0xffffffff,0xff202020);theme(themes,"Сепия",0xfff3e7d5,0xff30221e);theme(themes,"Ночная",0xff1e2025,0xffe8e5db);theme(themes,"Мягкая",0xffdfe9d9,0xff223a29);list.addView(themes);
        header("Цвет фона");palette(backgrounds,true);
        header("Цвет текста");palette(textColors,false);
        header("Цвет обычных кнопок");buttonPalette(false);
        header("Цвет акцентных кнопок · Play, режимы");buttonPalette(true);
        gap(18);
    }
    void drawChteicoSettings(){
        final android.content.SharedPreferences game=ChteicoStore.prefs(this);
        gap(12);
        android.widget.Switch enabled=new android.widget.Switch(this);
        enabled.setText("Включить Чтейцо");enabled.setTextColor(contrast(bg));enabled.setTextSize(16);
        enabled.setPadding(dp(4),dp(12),dp(4),dp(12));
        enabled.setChecked(ChteicoStore.enabled(this));list.addView(enabled);
        enabled.setOnCheckedChangeListener((v,on)->{ChteicoStore.setEnabled(this,on);draw();});
        gap(12);
        // Folded by default. No code symbols, countdowns or hidden evolutionary rules in user-facing help.
        LinearLayout helpBox=col();helpBox.setBackground(rounded(cardColor(),16));helpBox.setPadding(dp(14),dp(12),dp(14),dp(12));
        TextView helpHeader=text("ⓘ  Справка   ▾",17,true);helpBox.addView(helpHeader);
        TextView helpText=text("Чтейцо — маленький спутник ваших книжных приключений. Ему нравятся истории, уютные минуты чтения и удивительные аудиопутешествия.\n\nСо временем питомец меняется и взрослеет. Ваши привычки могут сделать его особенным — какой сюрприз ждёт впереди, узнаете сами.\n\nПочаще заглядывайте к нему: ласковое прикосновение поднимает настроение, а книги помогают расти. Если долго не читать, питомец может мягко напомнить о себе.",14,false);
        helpText.setPadding(dp(2),dp(13),dp(2),dp(5));helpText.setVisibility(View.GONE);helpBox.addView(helpText);
        helpHeader.setOnClickListener(v->{boolean open=helpText.getVisibility()!=View.VISIBLE;helpText.setVisibility(open?View.VISIBLE:View.GONE);helpHeader.setText(open?"ⓘ  Справка   ▴":"ⓘ  Справка   ▾");if(!open)chteicoHelpTapCount=0;});
        helpText.setOnClickListener(v->{
            if(helpText.getVisibility()!=View.VISIBLE || ChteicoStore.developerUnlocked(SettingsActivity.this))return;
            chteicoHelpTapCount++;
            if(chteicoHelpTapCount>=7){
                ChteicoStore.unlockDeveloper(SettingsActivity.this);
                chteicoHelpTapCount=0;
                android.widget.Toast.makeText(SettingsActivity.this,"Режим разработчика разблокирован",android.widget.Toast.LENGTH_SHORT).show();
                draw();
            }
        });
        list.addView(helpBox);gap(12);
        if(ChteicoStore.developerUnlocked(this)){
            LinearLayout devBox=col();devBox.setBackground(outlined(cardColor(),16));devBox.setPadding(dp(14),dp(14),dp(14),dp(14));
            LinearLayout devHeader=new LinearLayout(this);devHeader.setGravity(Gravity.CENTER_VERTICAL);
            ImageView devIcon=new ImageView(this);devIcon.setImageResource(R.drawable.ic_dev_beaker);devIcon.setColorFilter(contrast(bg));
            devHeader.addView(devIcon,new LinearLayout.LayoutParams(dp(24),dp(24)));
            LinearLayout devTexts=col();LinearLayout.LayoutParams devTextsLp=new LinearLayout.LayoutParams(-2,-2);devTextsLp.leftMargin=dp(10);
            TextView devTitle=text("Режим разработчика",17,true);devTexts.addView(devTitle);
            TextView devSub=text("Разблокировано! Вы нашли скрытые настройки Чтейцо.",12,false);devSub.setTextColor(0xff8c8073);devTexts.addView(devSub);
            devHeader.addView(devTexts,devTextsLp);devBox.addView(devHeader);
            final int[] stageValues={ChteicoStore.EGG,ChteicoStore.CHICK,ChteicoStore.ADULT};
            final String[] stageLabels={"Чтейцо","Чтенец","Чтун"};
            final int[] branchValues={ChteicoStore.CLASSIC,ChteicoStore.SPEED,ChteicoStore.AUDIO};
            final String[] branchLabels={"Обычное чтение","Скорочтение","Аудиокниги"};
            int stageIndex=Math.max(0,Math.min(2,ChteicoStore.stage(this)));
            int currentBranch=ChteicoStore.branch(this); if(currentBranch<ChteicoStore.CLASSIC||currentBranch>ChteicoStore.AUDIO)currentBranch=ChteicoStore.CLASSIC;
            int branchIndex=currentBranch==ChteicoStore.SPEED?1:currentBranch==ChteicoStore.AUDIO?2:0;
            View spaceTop=new View(this);devBox.addView(spaceTop,new LinearLayout.LayoutParams(-1,dp(10)));
            LinearLayout stageRow=new LinearLayout(this);stageRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView stageLabel=text("Этап",16,true);stageRow.addView(stageLabel,new LinearLayout.LayoutParams(dp(64),-2));
            android.widget.Spinner stageSpinner=new android.widget.Spinner(this);
            android.widget.ArrayAdapter<String> stageAdapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_item,stageLabels);
            stageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);stageSpinner.setAdapter(stageAdapter);stageSpinner.setSelection(stageIndex,false);
            stageSpinner.setBackground(rounded(0xffffffff,12));stageSpinner.setPadding(dp(12),0,dp(12),0);
            stageRow.addView(stageSpinner,new LinearLayout.LayoutParams(0,dp(50),1));devBox.addView(stageRow);
            View spaceMid=new View(this);devBox.addView(spaceMid,new LinearLayout.LayoutParams(-1,dp(8)));
            LinearLayout branchRow=new LinearLayout(this);branchRow.setGravity(Gravity.CENTER_VERTICAL);
            TextView branchLabel=text("Вид",16,true);branchRow.addView(branchLabel,new LinearLayout.LayoutParams(dp(64),-2));
            android.widget.Spinner branchSpinner=new android.widget.Spinner(this);
            android.widget.ArrayAdapter<String> branchAdapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_item,branchLabels);
            branchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);branchSpinner.setAdapter(branchAdapter);branchSpinner.setSelection(branchIndex,false);
            branchSpinner.setBackground(rounded(0xffffffff,12));branchSpinner.setPadding(dp(12),0,dp(12),0);
            branchRow.addView(branchSpinner,new LinearLayout.LayoutParams(0,dp(50),1));devBox.addView(branchRow);
            final Runnable applyDeveloperChoice=()->{
                int targetStage=stageValues[Math.max(0,Math.min(stageValues.length-1,stageSpinner.getSelectedItemPosition()))];
                int targetBranch=branchValues[Math.max(0,Math.min(branchValues.length-1,branchSpinner.getSelectedItemPosition()))];
                if(targetStage!=ChteicoStore.stage(SettingsActivity.this) || (targetStage!=ChteicoStore.EGG && targetBranch!=ChteicoStore.branch(SettingsActivity.this))){
                    ChteicoStore.setDeveloperState(SettingsActivity.this,targetStage,targetBranch);
                }
            };
            stageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){applyDeveloperChoice.run();}
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            });
            branchSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){applyDeveloperChoice.run();}
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            });
            list.addView(devBox);gap(12);
        }
        if(ChteicoStore.canName(this)){
            list.addView(action("✎  Назвать питомца",()->{
                android.widget.EditText input=new android.widget.EditText(this);input.setSingleLine(true);input.setHint("Имя питомца");
                input.setText(ChteicoStore.petName(this));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(18)});
                LinearLayout wrap=col();wrap.setPadding(dp(16),0,dp(16),0);wrap.addView(input);
                new AlertDialog.Builder(this).setTitle("Имя питомца")
                    .setMessage("Имя выбирается только один раз и остаётся после взросления.")
                    .setView(wrap).setNegativeButton("Отмена",null)
                    .setPositiveButton("Сохранить",(dialog,which)->{
                        if(ChteicoStore.setPetName(this,input.getText().toString()))draw();
                        else android.widget.Toast.makeText(this,"Введите имя до 18 символов",android.widget.Toast.LENGTH_SHORT).show();
                    }).show();
            }));
        }else list.addView(action("Имя питомца: "+ChteicoStore.petName(this),()->{}));
        gap(14);header("Напоминания и оповещения");
        chteicoSwitch("Ежедневное напоминание","daily");
        chteicoSwitch("Напомнить при неактивности","inactive");
        LinearLayout daysRow=new LinearLayout(this);daysRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView daysLabel=text("Дней без чтения или аудио",15,false);
        daysRow.addView(daysLabel,new LinearLayout.LayoutParams(0,-2,1));
        EditText days=new EditText(this);days.setSingleLine(true);days.setGravity(Gravity.CENTER);
        days.setInputType(InputType.TYPE_CLASS_NUMBER);days.setText(String.valueOf(game.getInt("inactive_days",3)));
        days.setSelectAllOnFocus(true);daysRow.addView(days,new LinearLayout.LayoutParams(dp(80),dp(52)));
        days.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence seq,int start,int count,int after){}
            public void onTextChanged(CharSequence seq,int start,int before,int count){
                try{long n=Long.parseLong(seq.toString().trim());if(n>=1&&n<=36500){
                    game.edit().putInt("inactive_days",(int)n).apply();ChteicoNotifications.schedule(SettingsActivity.this);
                }}catch(NumberFormatException ignored){}
            }
            public void afterTextChanged(android.text.Editable e){}
        });
        days.setOnFocusChangeListener((v,focus)->{if(!focus)saveInactiveDays(days);});
        days.setOnEditorActionListener((v,action,event)->{saveInactiveDays(days);days.clearFocus();return false;});
        list.addView(daysRow);
        chteicoSwitch("Уведомления о достижениях","achievements_notice");
        list.addView(action("Время напоминаний: "+game.getInt("reminder_hour",19)+":00",()->{
            new android.app.TimePickerDialog(this,(v,h,m)->{game.edit().putInt("reminder_hour",h).apply();ChteicoNotifications.schedule(this);draw();},game.getInt("reminder_hour",19),0,true).show();
        }));
        gap(16);header("Сброс и тестирование");
        list.addView(action("Сбросить вариант развития",()->new AlertDialog.Builder(this)
            .setTitle("Начать новую историю?")
            .setMessage("Питомец вновь станет яйцом и сможет выбрать другой облик. Книжная статистика, библиотека и сохранённое имя останутся.")
            .setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(dialog,which)->{ChteicoStore.resetBranch(this);draw();}).show()));
        gap(8);list.addView(action("Полностью сбросить Чтейцо",()->new AlertDialog.Builder(this)
            .setTitle("Сбросить Чтейцо?")
            .setMessage("Будут сброшены игровой прогресс, имя и статистика питомца. Ваши книги, аудио, закладки и позиции чтения останутся нетронутыми.")
            .setNegativeButton("Отмена",null).setPositiveButton("Сбросить",(dialog,which)->{ChteicoStore.resetPet(this);draw();}).show()));
        gap(18);
    }
    void saveInactiveDays(EditText input){
        try{
            long value=Long.parseLong(input.getText().toString().trim());
            if(value<1)throw new NumberFormatException();
            int days=(int)Math.min(value,36500L);
            ChteicoStore.prefs(this).edit().putInt("inactive_days",days).apply();
            ChteicoNotifications.schedule(this);
            input.setText(String.valueOf(days));
        }catch(NumberFormatException ignored){
            input.setText(String.valueOf(ChteicoStore.prefs(this).getInt("inactive_days",3)));
            android.widget.Toast.makeText(this,"Укажите целое число дней больше нуля",android.widget.Toast.LENGTH_SHORT).show();
        }
    }
    void chteicoSwitch(String label,String key){
        final android.content.SharedPreferences game=ChteicoStore.prefs(this);
        android.widget.Switch sw=new android.widget.Switch(this);sw.setText(label);sw.setTextColor(contrast(bg));sw.setTextSize(15);
        sw.setPadding(dp(4),dp(9),dp(4),dp(9));sw.setChecked(game.getBoolean(key,false));list.addView(sw);
        sw.setOnCheckedChangeListener((view,on)->{
            game.edit().putBoolean(key,on).apply();ChteicoNotifications.schedule(this);
            if(on&&android.os.Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},721);
            }
        });
    }
    void drawRsvpSettings(){
        header("Режим чтения");
        int current=Math.max(0,Math.min(2,prefs.getInt("reading_mode",prefs.getBoolean("rsvp",true)?1:0)));
        LinearLayout selector=new LinearLayout(this);selector.setGravity(Gravity.CENTER_VERTICAL);
        ImageView modeImage=new ImageView(this);
        modeImage.setImageResource(modeIconFor(current));modeImage.setColorFilter(contrast(bg));
        modeImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        modeImage.setBackground(rounded(cardColor(),14));
        modeImage.setPadding(dp(13),dp(13),dp(13),dp(13));
        selector.addView(modeImage,new LinearLayout.LayoutParams(dp(52),dp(52)));
        android.widget.Spinner spinner=new android.widget.Spinner(this);
        String[] labels={"Обычный","Скорочтение","Курсор"};
        android.widget.ArrayAdapter<String> items=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_item,labels);
        items.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(items);spinner.setSelection(current,false);
        spinner.setBackground(rounded(cardColor(),14));
        spinner.setPadding(dp(14),0,dp(14),0);
        LinearLayout.LayoutParams spinnerLp=new LinearLayout.LayoutParams(0,dp(52),1);spinnerLp.leftMargin=dp(9);
        selector.addView(spinner,spinnerLp);list.addView(selector);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int n,long id){
                int chosen=Math.max(0,Math.min(2,prefs.getInt("reading_mode",prefs.getBoolean("rsvp",true)?1:0)));
                if(n!=chosen){prefs.edit().putInt("reading_mode",n).putBoolean("rsvp",n==RSVP).apply();draw();}
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        gap(10);
        addFullscreenSetting(current);
        if(current==NORMAL){drawNormalModeSettings();return;}
        addStartDelaySetting(current);
        if(current==CURSOR){drawCursorSettings();return;}
        addFullscreenWordPositionSetting();
        drawSpeedReadingSettings();
    }
    void addFullscreenSetting(int selectedMode){
        final String key=selectedMode==NORMAL?"reading_fullscreen_normal":
            selectedMode==RSVP?"reading_fullscreen_rsvp":"reading_fullscreen_cursor";
        header("Отображение");
        android.widget.Switch toggle=new android.widget.Switch(this);
        toggle.setText("Во весь экран");toggle.setTextColor(contrast(bg));toggle.setTextSize(16);
        toggle.setChecked(prefs.getBoolean(key,false));
        toggle.setPadding(dp(6),dp(8),dp(6),dp(8));
        list.addView(toggle);
        String explanation=selectedMode==NORMAL?
            "При открытии книги интерфейс скрыт. Одно короткое касание текста показывает или скрывает панели. Прокрутка остаётся обычной и не меняет состояние интерфейса. При включённом выборе слова касание выделяет слово.":
            "При нажатии Play скрываются панели управления, системные индикаторы и навигация. Остаётся только кнопка Pause поверх текста. После Pause весь интерфейс возвращается.";
        TextView info=text(explanation,13,false);
        info.setPadding(dp(4),0,dp(4),dp(12));list.addView(info);
        toggle.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(key,checked).apply());
        gap(6);
    }
    void addStartDelaySetting(int mode){
        final String suffix=mode==RSVP?"rsvp":"cursor";
        final String enabledKey="reading_start_delay_"+suffix+"_enabled";
        final String secondsKey="reading_start_delay_"+suffix+"_seconds";
        header("Задержка перед стартом");
        android.widget.Switch toggle=new android.widget.Switch(this);
        toggle.setText("Задержка перед Play");toggle.setTextSize(16);
        toggle.setTextColor(contrast(bg));toggle.setPadding(dp(6),dp(5),dp(6),dp(5));
        boolean enabled=prefs.getBoolean(enabledKey,false);
        toggle.setChecked(enabled);list.addView(toggle);
        final int seconds=Math.max(1,Math.min(10,prefs.getInt(secondsKey,2)));
        TextView value=text("Длительность задержки: "+seconds+" сек",15,false);
        value.setPadding(dp(4),dp(7),0,dp(2));list.addView(value);
        SeekBar seek=new SeekBar(this);seek.setMax(9);seek.setProgress(seconds-1);
        seek.setEnabled(enabled);list.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                int updated=progress+1;value.setText("Длительность задержки: "+updated+" сек");
                if(fromUser)put(secondsKey,updated);
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        TextView note=text("От 1 до 10 секунд. При Play появится прозрачный контурный круг без цифр: полная окружность постепенно исчезает по часовой стрелке.",13,false);
        note.setPadding(dp(4),dp(4),dp(4),dp(7));list.addView(note);
        toggle.setOnCheckedChangeListener((b,checked)->{
            prefs.edit().putBoolean(enabledKey,checked).apply();seek.setEnabled(checked);
        });
        gap(8);
    }
    void addFullscreenWordPositionSetting(){
        header("Положение слова во весь экран");
        TextView note=text("Слово и линии фокусировки перемещаются вместе. По умолчанию сохраняется высота слова в обычном интерфейсе.",13,false);
        note.setPadding(dp(4),0,dp(4),dp(8));list.addView(note);
        final String manualKey="rsvp_fullscreen_position_manual";
        final String valueKey="rsvp_fullscreen_position";
        boolean manual=prefs.getBoolean(manualKey,false);
        int lastAuto=Math.max(-100,Math.min(100,prefs.getInt("rsvp_fullscreen_auto_percent",-28)));
        int initial=manual?prefs.getInt(valueKey,0):lastAuto;
        initial=Math.max(-100,Math.min(100,initial));
        TextView label=text(manual?"Положение: "+(initial>0?"+":"")+initial+"%":
            "Авто: "+(initial>0?"+":"")+initial+"% · исходная высота",15,false);
        list.addView(label);
        SeekBar slider=new SeekBar(this);slider.setMax(200);slider.setProgress(initial+100);
        list.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                int value=progress-100;
                if(fromUser){
                    label.setText("Положение: "+(value>0?"+":"")+value+"%");
                    prefs.edit().putInt(valueKey,value).putBoolean(manualKey,true).apply();
                }
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        LinearLayout labels=new LinearLayout(this);labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView top=text("−100% · верх",12,false);labels.addView(top,new LinearLayout.LayoutParams(0,-2,1));
        TextView middle=text("0% · центр",12,false);middle.setGravity(Gravity.CENTER);
        labels.addView(middle,new LinearLayout.LayoutParams(0,-2,1));
        TextView bottom=text("+100% · низ",12,false);bottom.setGravity(Gravity.RIGHT);
        labels.addView(bottom,new LinearLayout.LayoutParams(0,-2,1));list.addView(labels);
        gap(8);
        list.addView(action("↺ Авто · как в обычном интерфейсе",()->{
            prefs.edit().putBoolean(manualKey,false).apply();draw();
        }));
        gap(8);
    }
    int modeIconFor(int mode){
        return mode==NORMAL?R.drawable.ic_book:mode==RSVP?R.drawable.ic_speed:R.drawable.ic_mode_cursor;
    }
    void drawNormalModeSettings(){
        header("Выбор слова в обычном режиме");
        TextView info=text("Кнопка выбора слова работает как переключатель: коснитесь её, затем выбирайте стартовое слово в тексте столько раз, сколько нужно. Фон у выделенного слова не используется.",14,false);
        info.setPadding(0,0,0,dp(8));list.addView(info);
        colorSection("Цвет слова","normal_select_text",0xff915439,true);
        percentSlider("Увеличение выбранного слова","normal_select_scale",100,180,110,1);
        gap(18);
    }
    void drawCursorSettings(){
        header("Выделение режима «Курсор»");
        colorSection("Цвет фона","cursor_background",0xff96583f,true);
        colorSection("Цвет текста","cursor_text",NO_COLOR,true);
        percentSlider("Размер выделенного слова","cursor_scale",100,180,100,1);
        TextView info=text("В режиме «Курсор» слово можно выбирать касанием постоянно, без отдельного переключателя. По умолчанию размер слова не увеличивается: 100%. При выборе «Без цвета» фон или цвет текста не применяются.",13,false);
        info.setPadding(0,0,0,dp(10));list.addView(info);
        gap(18);
    }
    void drawSpeedReadingSettings(){
        gap(8);header("Размер порции");
        int currentPortion=ReadingWordMap.portion(prefs.getInt("rsvp_portion",1));
        TextView portionLabel=text("Слов за один показ: "+currentPortion+" из 4",15,false);list.addView(portionLabel);
        SeekBar portionSlider=new SeekBar(this);portionSlider.setMax(3);portionSlider.setProgress(currentPortion-1);list.addView(portionSlider);
        portionSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean user){portionLabel.setText("Слов за один показ: "+(v+1)+" из 4");if(user)put("rsvp_portion",v+1);}            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        TextView portionNote=text("Последовательно отображать или выделять от 1 до 4 слов. Книга уже разбивается на слова, поэтому изменение порции работает быстрее и без повторного анализа текста.",13,false);portionNote.setPadding(0,dp(4),0,dp(13));list.addView(portionNote);
        gap(8);list.addView(action("Дополнительные паузы",this::pauses));gap(12);
        header("Точка фокусировки");
        addFocusSwitch("Выделять опорную букву слова", "rsvp_focus_point",true);
        TextView focusNote=text("Оптимальное место для фиксации взгляда при скорочтении.",13,false);focusNote.setPadding(0,0,0,dp(10));list.addView(focusNote);
        colorSection("Цвет точки фокусировки","rsvp_focus_color",0xffed3030,true);
        gap(12);header("Линии фокусировки");
        addFocusSwitch("Показывать две линии и вертикальные засечки", "rsvp_focus_lines",true);
        colorSection("Цвет линий","rsvp_line_color",0xff915439,true);
        final int initial=Math.max(0,Math.min(100,prefs.getInt("rsvp_line_position",50)));
        TextView positionLabel=text("Горизонтальная позиция: "+initial+"%",15,false);positionLabel.setPadding(0,dp(12),0,0);list.addView(positionLabel);
        SeekBar position=new SeekBar(this);position.setMax(100);position.setProgress(initial);list.addView(position);
        position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean user){positionLabel.setText("Горизонтальная позиция: "+v+"%");if(user)put("rsvp_line_position",v);}            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        TextView extra=text("Во время прокрутки вверх и вниз в скорочтении снова показывается весь текст книги с подсветкой текущего места.",13,false);extra.setPadding(0,dp(8),0,0);list.addView(extra);
        gap(18);
    }
    void addFocusSwitch(String caption,String key,boolean defaultValue){
        android.widget.Switch sw=new android.widget.Switch(this);sw.setText(caption);sw.setTextSize(15);
        sw.setTextColor(contrast(bg));sw.setChecked(prefs.getBoolean(key,defaultValue));
        sw.setPadding(dp(4),dp(8),dp(4),dp(8));list.addView(sw);
        sw.setOnCheckedChangeListener((button,checked)->prefs.edit().putBoolean(key,checked).apply());
    }
    void colorSection(String caption,String key,int fallback,boolean allowNone){
        header(caption);
        int selected=prefs.getInt(key,fallback);
        final int[] colors=allowNone?new int[]{NO_COLOR,0xffff1212,0xffff9100,0xffffdf00,0xff00dd28,0xff1150e8,0xffe000e0,0xff915439,0xff30221e}:new int[]{0xffff1212,0xffff9100,0xffffdf00,0xff00dd28,0xff1150e8,0xffe000e0,0xff915439,0xff30221e};
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);list.addView(row);
        for(int color:colors){
            TextView circle=text(color==selected?"✓":(color==NO_COLOR?"Ø":""),12,true);circle.setTextColor(color==NO_COLOR?contrast(bg):contrast(color));circle.setGravity(Gravity.CENTER);
            GradientDrawable gd=color==NO_COLOR?outlined(cardColor(),35):rounded(color,35);if(color==selected)gd.setStroke(dp(3),0xff937263);
            circle.setBackground(gd);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(36),1);lp.setMargins(dp(2),dp(3),dp(2),dp(3));row.addView(circle,lp);
            circle.setOnClickListener(v->{put(key,color);draw();});
        }
        list.addView(action("Добавить цвет RGB",()->customFocusColor(key,prefs.getInt(key,fallback),allowNone)));
    }
    void percentSlider(String title,String key,int min,int max,int fallback,int step){
        final int initial=Math.max(min,Math.min(max,prefs.getInt(key,fallback)));
        TextView label=text(title+": "+initial+"%",15,false);label.setPadding(0,dp(4),0,0);list.addView(label);
        SeekBar seek=new SeekBar(this);seek.setMax((max-min)/Math.max(1,step));seek.setProgress((initial-min)/Math.max(1,step));list.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean user){int value=min+v*Math.max(1,step);label.setText(title+": "+value+"%");if(user)put(key,value);}            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
    }
    void customFocusColor(String key,int original,boolean allowNone){
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(original==NO_COLOR?"":String.format(java.util.Locale.ROOT,"#%06X",original&0xFFFFFF));
        input.setHint(allowNone?"Пусто = без цвета":"#RRGGBB");
        LinearLayout content=col();content.setPadding(dp(18),0,dp(18),0);content.addView(input);
        new AlertDialog.Builder(this).setTitle("Цвет · #RRGGBB").setView(content)
            .setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(d,n)->{
                try{
                    String value=input.getText().toString().trim();
                    if(value.isEmpty()&&allowNone){put(key,NO_COLOR);draw();return;}
                    int c=Color.parseColor(value);put(key,c);draw();
                }catch(IllegalArgumentException ex){android.widget.Toast.makeText(this,"Неверный цвет",android.widget.Toast.LENGTH_SHORT).show();}
            }).show();
    }
    void drawAudioSettings(){
        header("Настройки аудио");
        list.addView(action("Эквалайзер · "+equalizerLabel(),R.drawable.ic_equalizer,this::equalizerDialog));gap(8);
        list.addView(action("Выбор движка TTS",R.drawable.ic_tts_engine,this::ttsEngineDialog));gap(8);
        list.addView(action("Автоотмотка после паузы: "+autoRewindLabel(),this::autoRewindDialog));gap(8);
        list.addView(action("Перемотка кнопками секунд: "+AudioPlaybackPrefs.seekSeconds(this)+" сек",()->seekStepDialog(false)));gap(8);
        list.addView(action("Перемотка кнопками минут: "+AudioPlaybackPrefs.seekMinutes(this)+" мин",()->seekStepDialog(true)));gap(8);
        gap(18);
    }
    String autoRewindLabel(){int sec=AudioPlaybackPrefs.pauseRewindSeconds(this);
        return sec==0?"выключена":sec==60?"1 мин":sec+" сек";
    }
    void autoRewindDialog(){
        final int[] values={0,5,10,15,30,60};
        final String[] names={"Выключено","5 секунд","10 секунд","15 секунд","30 секунд","1 минута"};
        int selected=0,stored=AudioPlaybackPrefs.pauseRewindSeconds(this);
        for(int i=0;i<values.length;i++)if(stored==values[i])selected=i;
        new AlertDialog.Builder(this).setTitle("Автоотмотка после паузы")
            .setSingleChoiceItems(names,selected,(d,n)->{prefs.edit().putInt(AudioPlaybackPrefs.REWIND_ON_RESUME,values[n]).apply();d.dismiss();draw();})
            .setNegativeButton("Отмена",null).show();
    }
    void seekStepDialog(boolean minutes){
        int current=minutes?AudioPlaybackPrefs.seekMinutes(this):AudioPlaybackPrefs.seekSeconds(this);
        int maximum=minutes?120:3600;
        EditText field=new EditText(this);field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setSingleLine(true);field.setText(String.valueOf(current));field.selectAll();
        LinearLayout panel=col();panel.setPadding(dp(22),dp(5),dp(22),0);
        panel.addView(text("Интервал от 1 до "+maximum+(minutes?" минут":" секунд"),14,false));
        panel.addView(field);
        new AlertDialog.Builder(this).setTitle(minutes?"Интервал перемотки в минутах":"Интервал перемотки в секундах")
            .setView(panel).setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                try{int value=Integer.parseInt(field.getText().toString().trim());
                    int bounded=AudioPlaybackPrefs.clamp(value,1,maximum);
                    prefs.edit().putInt(minutes?AudioPlaybackPrefs.SEEK_MINUTES:AudioPlaybackPrefs.SEEK_SECONDS,bounded).apply();
                }catch(NumberFormatException ignored){}
                draw();
            }).show();
    }
    String equalizerLabel(){
        String current=prefs.getString("audio_equalizer","off");
        for(int i=0;i<AudioEqualizer.KEYS.length;i++)if(AudioEqualizer.KEYS[i].equals(current))
            return "custom".equals(current)?prefs.getString("eq_custom_name","Пользовательский"):AudioEqualizer.LABELS[i];
        return "Выключен";
    }
    void equalizerDialog(){
        java.util.ArrayList<String> labels=new java.util.ArrayList<>(java.util.Arrays.asList(AudioEqualizer.LABELS));
        java.util.ArrayList<String> values=new java.util.ArrayList<>(java.util.Arrays.asList(AudioEqualizer.KEYS));
        try{
            org.json.JSONObject saved=new org.json.JSONObject(prefs.getString("eq_saved_profiles","{}"));
            java.util.Iterator<String> keys=saved.keys();
            while(keys.hasNext()){
                String key=keys.next();labels.add("Сохранённый · "+key);values.add("saved:"+key);
            }
        }catch(Exception ignored){}
        new AlertDialog.Builder(this).setTitle("Эквалайзер")
            .setItems(labels.toArray(new String[0]),(d,which)->{
                String choice=values.get(which);
                if("custom".equals(choice)){customEqualizerDialog();return;}
                if(choice.startsWith("saved:")){
                    try{org.json.JSONObject saved=new org.json.JSONObject(prefs.getString("eq_saved_profiles","{}"));
                        String key=choice.substring(6);org.json.JSONArray arr=saved.getJSONArray(key);
                        float[] curve=new float[5];for(int i=0;i<5;i++)curve[i]=(float)arr.optDouble(i,0);
                        saveCustomCurve(curve,key);
                    }catch(Exception ignored){}
                }else prefs.edit().putString("audio_equalizer",choice).apply();
                AudioEqualizer.apply(this);draw();
            }).setNegativeButton("Отмена",null).show();
    }
    void saveCustomCurve(float[] curve,String name){
        StringBuilder csv=new StringBuilder();for(int i=0;i<5;i++){if(i>0)csv.append(',');csv.append(curve[i]);}
        prefs.edit().putString("audio_equalizer","custom")
            .putString("eq_custom_bands",csv.toString()).putString("eq_custom_name",name).apply();
        AudioEqualizer.apply(this);
    }
    void customEqualizerDialog(){
        float[] initial=AudioEqualizer.gains(this);
        LinearLayout panel=col();panel.setPadding(dp(16),0,dp(16),dp(2));
        int[] freq={60,230,910,3600,14000};
        final SeekBar[] sliders=new SeekBar[5];final TextView[] indicators=new TextView[5];
        for(int i=0;i<5;i++){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);panel.addView(row);
            TextView name=text(freq[i]>=1000?String.format(java.util.Locale.ROOT,"%.1f кГц",freq[i]/1000f):freq[i]+" Гц",12,false);
            row.addView(name,new LinearLayout.LayoutParams(dp(71),-2));
            SeekBar slider=new SeekBar(this);slider.setMax(24);
            slider.setProgress(Math.max(0,Math.min(24,Math.round(initial[i])+12)));
            row.addView(slider,new LinearLayout.LayoutParams(0,dp(56),1));
            final int index=i;
            TextView val=text(String.valueOf(slider.getProgress()-12)+" дБ",12,false);
            row.addView(val,new LinearLayout.LayoutParams(dp(55),-2));sliders[i]=slider;indicators[i]=val;
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar s,int v,boolean user){indicators[index].setText((v-12)+" дБ");}
                public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            });
        }
        android.widget.EditText profile=new android.widget.EditText(this);
        profile.setSingleLine(true);profile.setHint("Название профиля");
        profile.setText(prefs.getString("eq_custom_name","Мой профиль"));panel.addView(profile);
        new AlertDialog.Builder(this).setTitle("Пользовательский эквалайзер")
            .setView(panel).setNeutralButton("Сброс",(d,w)->{saveCustomCurve(new float[5],"Пользовательский");draw();})
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(d,w)->{
                float[] curve=new float[5];for(int i=0;i<5;i++)curve[i]=sliders[i].getProgress()-12;
                String label=profile.getText().toString().trim();if(label.isEmpty())label="Пользовательский";
                if(label.length()>40)label=label.substring(0,40);
                saveCustomCurve(curve,label);
                try{org.json.JSONObject saved=new org.json.JSONObject(prefs.getString("eq_saved_profiles","{}"));
                    org.json.JSONArray data=new org.json.JSONArray();for(float v:curve)data.put(v);
                    saved.put(label,data);prefs.edit().putString("eq_saved_profiles",saved.toString()).apply();
                }catch(Exception ignored){}
                draw();
            }).show();
    }
    void ttsEngineDialog(){
        String[] actions={"com.android.settings.TTS_SETTINGS","android.settings.TTS_SETTINGS",
                android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS,android.provider.Settings.ACTION_SETTINGS};
        for(String action:actions){
            try {startActivity(new Intent(action));return;}
            catch(android.content.ActivityNotFoundException ignored){}
            catch(SecurityException ignored){}
        }
        new AlertDialog.Builder(this).setMessage("Системные настройки синтеза речи недоступны на этом устройстве.")
                .setPositiveButton("OK",null).show();
    }
    void drawOnlineSettings(){
        header("Версия и обновления");
        TextView installed=text("Установлена: v" + installedVersion() + " (сборка " + installedCode() + ")",14,false);installed.setPadding(dp(4),0,0,dp(9));list.addView(installed);
        list.addView(action("Проверить версию в GitHub", R.drawable.ic_web_update, () -> new UpdateManager(this).checkVersion()));
        gap(10);
        header("Онлайн-озвучивание Edge TTS");
        list.addView(action("Количество фрагментов предзагрузки: " + EdgePrefetchSettings.get(prefs),()-> EdgePrefetchSettings.show(this,prefs,count->draw())));gap(8);
        list.addView(action("Размер онлайн-фрагмента: " + EdgeSegmentSettings.get(prefs) + " символов",()-> EdgeSegmentSettings.show(this,prefs,value->draw())));gap(8);
    }
    void theme(LinearLayout row,String name,int background,int ink){TextView chip=text(name,13,false);chip.setTextColor(ink);chip.setGravity(Gravity.CENTER);chip.setBackground(outlined(background,14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(56),1);lp.setMargins(dp(2),0,dp(2),0);row.addView(chip,lp);chip.setOnClickListener(v->{bg=background;fg=ink;put("bg",bg);put("fg",fg);draw();});}
    void palette(int[] colors,boolean background){
        for(int rowStart=0;rowStart<colors.length;rowStart+=4){LinearLayout row=new LinearLayout(this);for(int k=rowStart;k<Math.min(colors.length,rowStart+4);k++){
            int color=colors[k];TextView tile=new TextView(this);tile.setText(color==(background?bg:fg)?"✓":" ");tile.setGravity(Gravity.CENTER);tile.setTextSize(19);tile.setTextColor(contrast(color));tile.setBackground(outlined(color,13));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,lp);
            tile.setOnClickListener(v->{if(background){bg=color;put("bg",bg);if(contrast(bg)==contrast(fg)) {fg=contrast(bg);put("fg",fg);}draw();}else{fg=color;put("fg",fg);draw();}});
        }list.addView(row);}list.addView(action("Настроить RGB вручную",()->colorDialog(background)));
    }
    void buttonPalette(boolean primary){
        final String setting=primary?"button_accent_color":"button_color";
        final int selected=primary?accentButtonColor:buttonColor;
        String description=primary?"Play / Pause, выбор режима и другие основные действия.":"Назад, закладки, настройки, нейтральные команды.";
        TextView note=text(description+" Значок Ø — стандартный цвет темы.",13,false);
        note.setPadding(0,0,0,dp(6));list.addView(note);
        for(int rowStart=0;rowStart<accentColors.length;rowStart+=4){
            LinearLayout row=new LinearLayout(this);
            for(int k=rowStart;k<Math.min(accentColors.length,rowStart+4);k++){
                final int color=accentColors[k];
                TextView tile=new TextView(this);
                tile.setText(color==selected?"✓":(color==NO_COLOR?"Ø":" "));
                tile.setGravity(Gravity.CENTER);tile.setTextSize(18);
                tile.setTextColor(color==NO_COLOR?contrast(bg):contrast(color));
                tile.setBackground(outlined(color==NO_COLOR?cardColor():color,13));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(54),1);
                lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(tile,lp);
                tile.setOnClickListener(v->{setButtonColor(primary,color);draw();});
            }
            list.addView(row);
        }
        list.addView(action("Настроить RGB вручную",()->buttonColorDialog(primary)));
    }
    void setButtonColor(boolean primary,int color){
        if(primary)accentButtonColor=color;else buttonColor=color;
        put(primary?"button_accent_color":"button_color",color);
    }
    void applyPreview(){if(preview==null)return;preview.setTextColor(fg);preview.setTextSize(Math.max(16,(int)(size*.60f)));preview.setTypeface(face());preview.setBackground(rounded(bg==0xff1e2025?0xff31343a:0xfffff9f1,20));}
    void changeSize(int delta){size=Math.max(16,Math.min(76,size+delta));put("size",size);sizeValue.setText(String.valueOf(size));applyPreview();}
    void pauses(){
        LinearLayout panel=col();panel.setPadding(dp(14),dp(8),dp(14),0);
        addPauseSlider(panel,"После знаков пунктуации","pausePunctuationPercent",60,300);
        addPauseSlider(panel,"На длинных словах (12+ букв)","pauseLongPercent",50,300);
        addPauseSlider(panel,"В конце предложения","pauseSentencePercent",120,400);
        addPauseSlider(panel,"Между абзацами","pauseParagraphPercent",150,400);
        new AlertDialog.Builder(this).setTitle("Паузы в скорочтении")
            .setView(panel).setPositiveButton("Сохранить",(d,w)->draw())
            .setNegativeButton("Отмена",null).show();
    }
    void addPauseSlider(LinearLayout panel,String caption,String key,int fallback,int max){
        int initial=Math.max(0,Math.min(max,prefs.getInt(key,fallback)));
        TextView label=text(caption+": "+initial+"%",14,true);panel.addView(label);
        SeekBar slider=new SeekBar(this);slider.setMax(max);slider.setProgress(initial);panel.addView(slider);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int n,boolean user){label.setText(caption+": "+n+"%");if(user)put(key,n);}            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        TextView desc=text("0% — пауза отключена.",12,false);desc.setPadding(0,0,0,dp(10));panel.addView(desc);
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
    void buttonColorDialog(boolean primary){
        int selected=primary?accentButtonColor:buttonColor;
        int c=selected==NO_COLOR?(primary?0xffa26949:0xfffff9f0):selected;
        LinearLayout wrapper=col();wrapper.setPadding(dp(20),0,dp(20),0);
        View sample=new View(this);sample.setBackground(outlined(c,8));
        LinearLayout.LayoutParams sampleLp=new LinearLayout.LayoutParams(dp(58),dp(58));
        sampleLp.gravity=Gravity.CENTER_HORIZONTAL;sampleLp.bottomMargin=dp(12);
        wrapper.addView(sample,sampleLp);
        SeekBar[] bars=new SeekBar[3];String[] channels={"Красный","Зелёный","Синий"};
        int[] rgb={Color.red(c),Color.green(c),Color.blue(c)};
        for(int i=0;i<3;i++){
            wrapper.addView(text(channels[i],14,false));SeekBar bar=new SeekBar(this);
            bar.setMax(255);bar.setProgress(rgb[i]);bars[i]=bar;wrapper.addView(bar);
        }
        for(SeekBar bar:bars)bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int value,boolean from){
                sample.setBackground(outlined(Color.rgb(bars[0].getProgress(),bars[1].getProgress(),bars[2].getProgress()),8));
            }
            public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}
        });
        new AlertDialog.Builder(this).setTitle(primary?"Цвет акцентных кнопок":"Цвет обычных кнопок")
            .setView(wrapper).setNeutralButton("Стандартный",(d,w)->{setButtonColor(primary,NO_COLOR);draw();})
            .setPositiveButton("Применить",(d,w)->{
                int value=Color.rgb(bars[0].getProgress(),bars[1].getProgress(),bars[2].getProgress());
                setButtonColor(primary,value);draw();
            }).setNegativeButton("Отмена",null).show();
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
