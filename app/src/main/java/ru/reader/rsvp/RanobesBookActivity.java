package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Title/volume/chapter picker. Russian is a separate on-device ML Kit translation. */
public final class RanobesBookActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5,INK=0xff30221e;
    private final ExecutorService network=Executors.newSingleThreadExecutor();
    private String url="",bookId="",language="en";private int token;
    private RanobesApi.Title title;
    private final ArrayList<RanobesApi.Chapter> chapters=new ArrayList<>();
    private final java.util.Set<String> expanded=new java.util.HashSet<>();
    private LinearLayout list;private TextView state,languageButton;private CheckBox alsoOriginal;
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(){GradientDrawable d=new GradientDrawable();d.setColor(UiColorScheme.neutral(this));d.setCornerRadius(dp(14));return d;}
    private TextView text(String value,int size,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(INK);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String value,Runnable action){TextView t=text(value,14,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),dp(12),dp(8),dp(12));t.setBackground(shape());t.setOnClickListener(v->action.run());return t;}
    private void add(LinearLayout layout,android.view.View v,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);layout.addView(v,p);}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        url=RanobesApi.validBookUrl(getIntent().getStringExtra("ranobes_url"));
        String source=getIntent().getStringExtra("ranobes_id");
        if(url.isEmpty()&&RanobesStorage.validKey(source)){
            for(RanobesStorage.Job j:RanobesStorage.jobs(this,source)){url=RanobesApi.validBookUrl(j.bookUrl);break;}
            language=source.split(":")[1];
        }
        if(url.isEmpty()){Toast.makeText(this,"Адрес книги Ranobes не найден",Toast.LENGTH_LONG).show();finish();return;}
        bookId=RanobesApi.novelId(url);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(13),dp(22),dp(13),dp(10));root.setBackgroundColor(BG);setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);root.addView(bar,new LinearLayout.LayoutParams(-1,dp(50)));
        ImageView menu=new ImageView(this);menu.setImageResource(R.drawable.ic_menu_lines);menu.setColorFilter(INK);menu.setPadding(dp(10),dp(10),dp(10),dp(10));menu.setBackground(shape());bar.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));menu.setOnClickListener(v->UniversalDrawer.show(this));
        TextView heading=text("Ranobes.net · Главы",20,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(12);bar.addView(heading,hp);
        ImageView folder=new ImageView(this);folder.setImageResource(R.drawable.ic_folder_audio);folder.setColorFilter(INK);folder.setPadding(dp(10),dp(10),dp(10),dp(10));folder.setBackground(shape());bar.addView(folder,new LinearLayout.LayoutParams(dp(44),dp(44)));folder.setOnClickListener(v->DownloadFolderPicker.choose(this,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.RANOBE));
        state=text("Загружаем сведения и оглавление…",13,false);add(root,state,12);
        LinearLayout options=new LinearLayout(this);options.setGravity(Gravity.CENTER_VERTICAL);add(root,options,8);
        languageButton=button("ru".equals(language)?"Русский · ML Kit ▾":"Оригинал · английский ▾",this::languageDialog);options.addView(languageButton,new LinearLayout.LayoutParams(-1,dp(47)));
        alsoOriginal=new CheckBox(this);alsoOriginal.setText("Сохранить оригинал отдельно при русском переводе");alsoOriginal.setTextColor(INK);alsoOriginal.setVisibility("ru".equals(language)?android.view.View.VISIBLE:android.view.View.GONE);add(root,alsoOriginal,5);
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(9);root.addView(scroll,sp);
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        TextView all=button("Скачать всю доступную книгу",()->confirm(chapters));add(root,all,6);
        fetch();
    }
    @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);DownloadFolderPicker.receive(this,req,result,data,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.RANOBE);}
    private void languageDialog(){String[] names={"Оригинальный текст (английский)","Русский — машинный перевод на устройстве (ML Kit)"};
        new SwipeDialogs.Builder(this).setTitle("Язык скачиваемой книги")
            .setItems(names,(dialog,index)->{language=index==1?"ru":"en";languageButton.setText(index==1?names[1]+" ▾":names[0]+" ▾");alsoOriginal.setVisibility(index==1?android.view.View.VISIBLE:android.view.View.GONE);})
            .setNegativeButton("Отмена",null).show();}
    private void fetch(){int request=++token;state.setText("Получаем публичное оглавление Ranobes.net…");
        network.execute(()->{try{RanobesApi api=new RanobesApi();RanobesApi.Title found=api.title(url);
            List<RanobesApi.Chapter> pages=api.chapters(url);
            RanobesStorage.remember(getApplicationContext(),found);
            runOnUiThread(()->{if(isFinishing()||request!=token)return;title=found;chapters.clear();chapters.addAll(pages);
                state.setText("Найдено глав в HTML: "+pages.size()+". Если сайт скрывает страницы оглавления, список может быть неполным.");redraw();});
        }catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&request==token){state.setText("Ranobes: "+e.getMessage());redraw();}});}});
    }
    private void redraw(){list.removeAllViews();if(title==null)return;
        LinearLayout card=new LinearLayout(this);card.setPadding(dp(11),dp(11),dp(11),dp(11));card.setOrientation(LinearLayout.VERTICAL);card.setBackground(shape());add(list,card,5);
        LinearLayout head=new LinearLayout(this);card.addView(head);
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);head.addView(cover,new LinearLayout.LayoutParams(dp(106),dp(148)));if(!title.cover.isEmpty())CoverImageLoader.show(cover,title.cover,false);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(11),0,0,0);head.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        info.addView(text(title.title,20,true));if(!title.author.isEmpty())add(info,text("Автор: "+title.author,13,false),7);
        add(info,text("Источник: Ranobes.net · HTML",12,false),5);
        if(!title.description.isEmpty()){TextView desc=text(title.description,13,false);desc.setMaxLines(5);add(card,desc,8);desc.setOnClickListener(v->desc.setMaxLines(desc.getMaxLines()==5?Integer.MAX_VALUE:5));}
        String key=bookId+":"+language;
        if(RanobesStorage.hasBook(this,key))add(card,button("Открыть сохранённую книгу",()->startActivity(new Intent(this,ReaderActivity.class).putExtra("book_uri",RanobesStorage.bookUri(this,key)))),7);
        add(card,button("Обновить список глав",this::fetch),7);
        LinkedHashMap<String,ArrayList<RanobesApi.Chapter>> volumes=new LinkedHashMap<>();
        for(RanobesApi.Chapter j:chapters)volumes.computeIfAbsent(j.volume,k->new ArrayList<>()).add(j);
        for(Map.Entry<String,ArrayList<RanobesApi.Chapter>> row:volumes.entrySet()){
            LinearLayout group=new LinearLayout(this);group.setPadding(dp(10),dp(10),dp(10),dp(10));group.setBackground(shape());group.setOrientation(LinearLayout.VERTICAL);add(list,group,10);
            String vol=row.getKey();ArrayList<RanobesApi.Chapter> items=row.getValue();
            int saved=0;for(RanobesStorage.Job j:RanobesStorage.jobs(this,key))if("complete".equals(j.status)&&vol.equals(j.volume))saved++;
            group.addView(text("Том "+vol+" · "+saved+" / "+items.size()+" глав",17,true));
            LinearLayout actions=new LinearLayout(this);add(group,actions,7);
            TextView all=button("Скачать том",()->confirm(items));actions.addView(all,new LinearLayout.LayoutParams(0,dp(46),1));
            TextView chosen=button("Выбрать главы",()->select(items,vol));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);cp.leftMargin=dp(7);actions.addView(chosen,cp);
            TextView expand=button(expanded.contains(vol)?"Скрыть главы ▴":"Показать главы ▾",()->{if(expanded.contains(vol))expanded.remove(vol);else expanded.add(vol);redraw();});add(group,expand,7);
            if(expanded.contains(vol))for(int i=0;i<Math.min(40,items.size());i++){
                RanobesApi.Chapter ch=items.get(i);TextView line=button(ch.title,()->confirm(java.util.Collections.singletonList(ch)));add(group,line,5);
            }
            if(expanded.contains(vol)&&items.size()>40)add(group,text("Остальные главы доступны через «Выбрать главы».",12,false),7);
        }
    }
    private void select(List<RanobesApi.Chapter> options,String vol){if(options.isEmpty())return;
        if(options.size()>250){LinearLayout input=new LinearLayout(this);input.setPadding(dp(16),0,dp(16),0);input.setOrientation(LinearLayout.VERTICAL);
            EditText start=new EditText(this);start.setHint("С главы № (по порядку)");input.addView(start);
            EditText end=new EditText(this);end.setHint("По главу № включительно");input.addView(end);
            new SwipeDialogs.Builder(this).setTitle("Том "+vol+" · Диапазон глав").setView(input)
                .setNegativeButton("Отмена",null).setPositiveButton("Скачать",(dialog,which)->{
                    try{int a=Integer.parseInt(start.getText().toString().trim()),b=Integer.parseInt(end.getText().toString().trim());if(a<1||b<a||b>options.size())throw new Exception();confirm(options.subList(a-1,b));}
                    catch(Exception error){Toast.makeText(this,"Неверный диапазон",Toast.LENGTH_SHORT).show();}
                }).show();return;
        }
        String[] entries=new String[options.size()];boolean[] marked=new boolean[options.size()];
        for(int i=0;i<options.size();i++)entries[i]=options.get(i).title;
        new SwipeDialogs.Builder(this).setTitle("Том "+vol+" · Выбор глав").setMultiChoiceItems(entries,marked,(dialog,index,checked)->marked[index]=checked)
            .setNegativeButton("Отмена",null).setPositiveButton("Скачать выбранные",(dialog,which)->{
                ArrayList<RanobesApi.Chapter> choice=new ArrayList<>();for(int i=0;i<marked.length;i++)if(marked[i])choice.add(options.get(i));confirm(choice);
            }).show();
    }
    private void confirm(List<RanobesApi.Chapter> selection){if(title==null||selection.isEmpty()){Toast.makeText(this,"Выберите главы",Toast.LENGTH_SHORT).show();return;}
        String detail="Глав: "+selection.size()+". Готовые главы будут пропущены.\n"+
            ("ru".equals(language)?"Русский перевод создаётся на устройстве через Google ML Kit после скачивания исходного HTML; это не перевод GTranslate сайта. Для первого запуска потребуется модель (~30 МБ).\n":"")+
            "Закрытые и защищённые главы не загружаются.";
        new SwipeDialogs.Builder(this).setTitle("Скачать с Ranobes.net?").setMessage(detail)
            .setNegativeButton("Отмена",null).setPositiveButton("Скачать",(d,w)->download(selection)).show();
    }
    private void download(List<RanobesApi.Chapter> selected){ArrayList<RanobesStorage.Job> jobs=new ArrayList<>();for(RanobesApi.Chapter ch:selected){jobs.add(RanobesStorage.Job.make(title,ch,language));if("ru".equals(language)&&alsoOriginal.isChecked())jobs.add(RanobesStorage.Job.make(title,ch,"en"));}
        int added=RanobesQueue.enqueue(this,jobs);
        Toast.makeText(this,"Поставлено в очередь: "+added+" глав",Toast.LENGTH_LONG).show();
        startActivity(new Intent(this,UnifiedDownloadManagerActivity.class)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_ID,"ranobes:"+bookId+":"+language)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_TITLE,title.title)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_COVER,title.cover)
            .putExtra(UnifiedDownloadManagerActivity.EXTRA_TYPE,DownloadsRepository.RANOBES));
    }
    @Override protected void onDestroy(){token++;network.shutdownNow();super.onDestroy();}
}
