package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
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

/** Cover, metadata, volume chooser and chapter selection for a RanobeLIB title. */
public final class RanobeLibBookActivity extends NavigationActivity {
    private final ExecutorService background=Executors.newSingleThreadExecutor();
    private int bg=0xfff3e7d5,ink=0xff30221e,panel=0xfffff9f2;
    private String slug;private RanobeLibApi.Title title;
    private List<RanobeLibApi.Chapter> chapters=new ArrayList<>();
    private LinearLayout body;private TextView notice;private int generation=0;
    private final java.util.HashMap<String,RanobeLibStorage.Job> currentJobs=new java.util.HashMap<>();
    private final java.util.HashSet<String> expanded=new java.util.HashSet<>();
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));return d;}
    private TextView label(String name,int size,boolean bold){TextView t=new TextView(this);t.setText(name);t.setTextSize(size);t.setTextColor(ink);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private TextView button(String name,Runnable click){TextView b=label(name,14,true);b.setGravity(Gravity.CENTER);b.setBackground(shape(UiColorScheme.accent(this)));b.setTextColor(UiColorScheme.onAccent(this));b.setPadding(dp(10),dp(11),dp(10),dp(11));b.setOnClickListener(v->click.run());return b;}
    private void gap(LinearLayout layout,int value){android.view.View empty=new android.view.View(this);layout.addView(empty,new LinearLayout.LayoutParams(-1,dp(value)));}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        slug=getIntent().getStringExtra("ranobelib_slug");try{slug=RanobeLibApi.validSlug(slug);}catch(Exception error){finish();return;}
        android.content.SharedPreferences prefs=getSharedPreferences("reader",0);bg=prefs.getInt("bg",bg);ink=prefs.getInt("fg",ink);panel=UiColorScheme.neutral(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);root.setPadding(dp(14),dp(20),dp(14),dp(10));setContentView(root);SafeSystemBars.matchBackground(this,bg);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar);
        ImageView menu=new ImageView(this);menu.setImageResource(R.drawable.ic_menu_lines);menu.setColorFilter(ink);menu.setBackground(shape(panel));menu.setPadding(dp(10),dp(10),dp(10),dp(10));menu.setContentDescription("Главное меню");toolbar.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));menu.setOnClickListener(v->UniversalDrawer.show(this));
        TextView heading=label("RanobeLIB · Книга",20,true);heading.setGravity(Gravity.CENTER);toolbar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        ImageView folder=new ImageView(this);folder.setImageResource(R.drawable.ic_folder_audio);folder.setColorFilter(ink);folder.setBackground(shape(panel));folder.setPadding(dp(10),dp(10),dp(10),dp(10));folder.setContentDescription("Выбрать папку HTML");toolbar.addView(folder,new LinearLayout.LayoutParams(dp(44),dp(44)));folder.setOnClickListener(v->DownloadFolderPicker.choose(this,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.sourceKey("ranobelib")));
        notice=label("Загружаем книгу и оглавление…",13,false);notice.setPadding(dp(4),dp(13),dp(4),dp(10));root.addView(notice);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
        title=RanobeLibStorage.savedTitle(this,slug);redraw();refreshOnline();
    }
    @Override protected void onResume(){super.onResume();if(body!=null&&!chapters.isEmpty())redraw();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.RANOBE_REQUEST,DownloadFolderPicker.sourceKey("ranobelib"));}
    private void refreshOnline(){int token=++generation;notice.setText("Получаем сведения и список глав…");
        background.execute(()->{try{RanobeLibApi api=new RanobeLibApi();RanobeLibApi.Title loaded=api.title(slug);
            List<RanobeLibApi.Chapter> list=api.chapters(slug);
            RanobeLibStorage.rememberTitle(getApplicationContext(),loaded);
            RanobeLibStorage.ensureCover(getApplicationContext(),loaded,api);
            runOnUiThread(()->{if(token!=generation||isFinishing())return;title=loaded;chapters=list;notice.setText("Томов: "+countVolumes(list)+" · Глав: "+primaryTranslations(list).size());redraw();});
        }catch(Exception error){runOnUiThread(()->{if(token==generation&&!isFinishing()){notice.setText("Не удалось получить оглавление: "+error.getMessage());redraw();}});}});
    }
    private static int countVolumes(List<RanobeLibApi.Chapter> chapters){java.util.HashSet<String> volumes=new java.util.HashSet<>();for(RanobeLibApi.Chapter ch:chapters)volumes.add(ch.volume);return volumes.size();}
    /** Bulk downloads choose exactly one published translation per volume and chapter. */
    private static List<RanobeLibApi.Chapter> primaryTranslations(List<RanobeLibApi.Chapter> candidates){
        java.util.HashSet<String> seen=new java.util.HashSet<>();ArrayList<RanobeLibApi.Chapter> selected=new ArrayList<>();
        for(RanobeLibApi.Chapter chapter:candidates)if(seen.add(chapter.volume+"|"+chapter.number))selected.add(chapter);
        return selected;
    }
    private void redraw(){body.removeAllViews();currentJobs.clear();
        // One disk read per refresh, never one read per chapter row.
        for(RanobeLibStorage.Job j:RanobeLibStorage.series(this,slug))currentJobs.put(j.id(),j);
        LinearLayout card=new LinearLayout(this);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(shape(panel));card.setOrientation(LinearLayout.VERTICAL);body.addView(card);
        LinearLayout coverRow=new LinearLayout(this);coverRow.setGravity(Gravity.TOP);card.addView(coverRow);
        ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);coverRow.addView(cover,new LinearLayout.LayoutParams(dp(112),dp(160)));
        java.io.File image=RanobeLibStorage.cover(this,slug);CoverImageLoader.show(cover,image.isFile()?image.toURI().toString():title.cover,false);
        LinearLayout description=new LinearLayout(this);description.setOrientation(LinearLayout.VERTICAL);description.setPadding(dp(12),0,0,0);coverRow.addView(description,new LinearLayout.LayoutParams(0,-2,1));
        description.addView(label(BookTitleStore.title(this,BookTitleStore.TEXT,RanobeLibStorage.bookUri(this,slug),title.title.isEmpty()?slug:title.title),21,true));
        if(!title.author.isEmpty())description.addView(label("Автор: "+title.author,13,false));
        if(!title.status.isEmpty())description.addView(label("Статус: "+title.status,12,false));
        if(!title.genres.isEmpty())description.addView(label(title.genres,12,false));
        if(!title.description.isEmpty()){gap(card,9);TextView summary=label(title.description,14,false);summary.setMaxLines(6);card.addView(summary);summary.setOnClickListener(v->summary.setMaxLines(summary.getMaxLines()==6?Integer.MAX_VALUE:6));}
        gap(card,11);
        if(RanobeLibStorage.book(this,slug).isFile())card.addView(button("Открыть скачанную HTML-книгу",this::readBook));
        gap(card,8);
        card.addView(button("Скачать всю книгу",()->confirm("Скачать всю книгу?","Глав для загрузки: "+primaryTranslations(chapters).size()+". По умолчанию выбирается первая публичная ветка перевода.",primaryTranslations(chapters))));
        gap(card,8);card.addView(button("Обновить оглавление",this::refreshOnline));
        if(chapters.isEmpty()){body.addView(label("Пока нет списка глав. При проблемах с сайтом проверьте доступ к RanobeLIB.",13,false));return;}
        LinkedHashMap<String,ArrayList<RanobeLibApi.Chapter>> volumes=new LinkedHashMap<>();
        for(RanobeLibApi.Chapter chapter:chapters)volumes.computeIfAbsent(chapter.volume,k->new ArrayList<>()).add(chapter);
        for(Map.Entry<String,ArrayList<RanobeLibApi.Chapter>> entry:volumes.entrySet())addVolume(entry.getKey(),entry.getValue());
    }
    private void readBook(){java.io.File path=RanobeLibStorage.book(this,slug);if(!path.isFile()){Toast.makeText(this,"Сначала скачайте главы",Toast.LENGTH_SHORT).show();return;}
        startActivity(new Intent(this,ReaderActivity.class).putExtra("book_uri",android.net.Uri.fromFile(path).toString()));
    }
    private void addVolume(String volume,ArrayList<RanobeLibApi.Chapter> list){
        LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);block.setPadding(dp(12),dp(12),dp(12),dp(12));block.setBackground(shape(panel));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);body.addView(block,lp);
        int completed=0;for(RanobeLibApi.Chapter ch:list){RanobeLibStorage.Job old=currentJobs.get(slug+"|"+ch.key());if(old!=null&&"complete".equals(old.status))completed++;}
        TextView caption=label("Том "+volume+" · "+completed+" из "+list.size()+" вариантов глав",17,true);caption.setPadding(0,0,0,dp(8));block.addView(caption);
        LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);block.addView(actions);
        TextView download=button("Скачать том",()->confirm("Скачать том "+volume+"?", "Глав в томе: "+primaryTranslations(list).size()+" (основная ветка перевода)",primaryTranslations(list)));actions.addView(download,new LinearLayout.LayoutParams(0,-2,1));
        TextView select=button("Выбрать главы",()->selectChapters(volume,list));LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=dp(7);actions.addView(select,sp);
        gap(block,8);
        boolean open=expanded.contains(volume);
        TextView toggle=label(open?"Скрыть список глав  ▴":"Показать главы  ▾",13,true);toggle.setGravity(Gravity.CENTER);toggle.setPadding(dp(8),dp(10),dp(8),dp(10));toggle.setBackground(shape(UiColorScheme.neutral(this)));block.addView(toggle);
        toggle.setOnClickListener(v->{if(expanded.contains(volume))expanded.remove(volume);else expanded.add(volume);redraw();});
        if(open){int limit=Math.min(list.size(),120);
            for(int i=0;i<limit;i++){RanobeLibApi.Chapter chapter=list.get(i);RanobeLibStorage.Job old=currentJobs.get(slug+"|"+chapter.key());
                String state=old==null?"":("complete".equals(old.status)?" ✓":"running".equals(old.status)?" …":"failed".equals(old.status)?" !":"blocked".equals(old.status)?" ⛔":"");
                TextView line=label(chapter.display()+state,13,false);line.setPadding(dp(5),dp(11),dp(5),dp(11));block.addView(line);
                line.setOnClickListener(v->confirm("Скачать главу?",chapter.fullTitle(),java.util.Collections.singletonList(chapter)));
            }
            if(list.size()>limit)block.addView(label("Остальные "+(list.size()-limit)+" глав доступны через выбор диапазона и скачивание тома.",12,false));
        }
    }
    private void confirm(String heading,String description,List<RanobeLibApi.Chapter> selected){
        if(selected.isEmpty()){Toast.makeText(this,"Доступных глав не найдено",Toast.LENGTH_SHORT).show();return;}
        new SwipeDialogs.Builder(this).setTitle(heading).setMessage(description+"\nУже скачанные главы будут пропущены.")
            .setNegativeButton("Отмена",null).setPositiveButton("Скачать",(d,w)->enqueue(selected)).show();
    }
    private void selectChapters(String volume,List<RanobeLibApi.Chapter> list){
        if(list.size()>300){
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(18),0,dp(18),0);
            EditText from=new EditText(this);from.setSingleLine(true);from.setHint("Номер первой главы, например 1");box.addView(from);
            EditText to=new EditText(this);to.setSingleLine(true);to.setHint("Номер последней главы, например 133");box.addView(to);
            new SwipeDialogs.Builder(this).setTitle("Диапазон глав · Том "+volume).setView(box)
                .setNegativeButton("Отмена",null).setPositiveButton("Скачать диапазон",(d,w)->{
                    try{double start=Double.parseDouble(from.getText().toString().trim().replace(',','.'));
                        double end=Double.parseDouble(to.getText().toString().trim().replace(',','.'));
                        ArrayList<RanobeLibApi.Chapter> chosen=new ArrayList<>();for(RanobeLibApi.Chapter ch:list){double v=Double.parseDouble(ch.number.replace(',','.'));if(v>=start&&v<=end)chosen.add(ch);}enqueue(chosen);
                    }catch(Exception error){Toast.makeText(this,"Введите корректные номера главы",Toast.LENGTH_LONG).show();}
                }).show();return;
        }
        String[] names=new String[list.size()];boolean[] selection=new boolean[list.size()];
        for(int i=0;i<list.size();i++){RanobeLibApi.Chapter chapter=list.get(i);
            RanobeLibStorage.Job job=currentJobs.get(slug+"|"+chapter.key());
            names[i]=chapter.display()+(job!=null&&"complete".equals(job.status)?" ✓":"");
        }
        new SwipeDialogs.Builder(this).setTitle("Том "+volume+" · Выбор глав")
            .setMultiChoiceItems(names,selection,(dialog,position,checked)->selection[position]=checked)
            .setNegativeButton("Отмена",null).setPositiveButton("Скачать выбранные",(d,w)->{
                ArrayList<RanobeLibApi.Chapter> selected=new ArrayList<>();for(int i=0;i<selection.length;i++)if(selection[i])selected.add(list.get(i));enqueue(selected);
            }).show();
    }
    private void enqueue(List<RanobeLibApi.Chapter> chosen){if(chosen.isEmpty()){Toast.makeText(this,"Выберите главы",Toast.LENGTH_SHORT).show();return;}
        ArrayList<RanobeLibStorage.Job> requests=new ArrayList<>();for(RanobeLibApi.Chapter ch:chosen)requests.add(RanobeLibStorage.Job.create(title,ch));
        final RanobeLibApi.Title requestedTitle=title;
        Toast.makeText(this,"Добавляем главы в очередь…",Toast.LENGTH_SHORT).show();
        background.execute(()->{
            try{RanobeLibStorage.rememberTitle(getApplicationContext(),requestedTitle);
                int added=RanobeLibQueue.enqueue(getApplicationContext(),requests);
                runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
                    Toast.makeText(this,added>0?"Глав в очереди: "+added:"Главы уже скачаны или загружаются",Toast.LENGTH_LONG).show();
                    if(added>0)startActivity(new Intent(this,DownloadActivity.class));
                });
            }catch(Exception error){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())
                Toast.makeText(this,"Ошибка очереди: "+error.getMessage(),Toast.LENGTH_LONG).show();});}
        });
    }
    @Override protected void onDestroy(){generation++;background.shutdownNow();super.onDestroy();}
}
