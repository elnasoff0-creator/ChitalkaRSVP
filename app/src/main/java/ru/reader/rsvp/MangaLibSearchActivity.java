package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** MangaLib-only search, chapter selection and durable CBZ queue UI. */
public final class MangaLibSearchActivity extends NavigationActivity {
    private int BG=0xfff3e7d5,PANEL=0xfffff9f2,INK=0xff30221e;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private LinearLayout list;private TextView message;private EditText query;
    private int generation=0;
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable panel(){GradientDrawable b=new GradientDrawable();b.setColor(PANEL);b.setCornerRadius(dp(16));return b;}
    private GradientDrawable tintPanel(int color){GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(dp(16));return b;}
    private TextView label(String text,int size,boolean bold){TextView t=new TextView(this);t.setText(text);t.setTextColor(INK);t.setTextSize(size);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        android.content.SharedPreferences theme=getSharedPreferences("reader",MODE_PRIVATE);
        BG=theme.getInt("bg",BG);INK=theme.getInt("fg",INK);
        PANEL=UiColorScheme.neutral(this);
        LinearLayout root=new LinearLayout(this);root.setBackgroundColor(BG);root.setPadding(dp(15),dp(18),dp(15),dp(10));root.setOrientation(LinearLayout.VERTICAL);
        setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);root.addView(head);
        ImageView menu=new ImageView(this);menu.setImageResource(R.drawable.ic_menu_lines);
        menu.setColorFilter(INK);menu.setPadding(dp(10),dp(10),dp(10),dp(10));
        menu.setBackground(panel());head.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));
        menu.setContentDescription("Главное меню");menu.setOnClickListener(v->UniversalDrawer.show(this));
        TextView title=label("Поиск манги · MangaLib",21,true);title.setGravity(Gravity.CENTER);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageView folder=new ImageView(this);folder.setImageResource(R.drawable.ic_folder_audio);
        folder.setColorFilter(INK);folder.setPadding(dp(10),dp(10),dp(10),dp(10));
        folder.setBackground(panel());head.addView(folder,new LinearLayout.LayoutParams(dp(44),dp(44)));
        folder.setContentDescription("Выбрать папку сохранения манги");
        folder.setOnClickListener(v->DownloadFolderPicker.choose(this,DownloadFolderPicker.MANGA_REQUEST,DownloadFolderPicker.sourceKey("mangalib")));
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(INK);query.setTextSize(15);
        query.setHint("Название манги или ссылка MangaLib");query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setBackground(panel());query.setPadding(dp(14),dp(10),dp(14),dp(10));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(52));qp.topMargin=dp(18);root.addView(query,qp);
        query.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search();return true;}return false;});
        TextView search=label("Найти на MangaLib",16,true);search.setGravity(Gravity.CENTER);search.setBackground(tintPanel(UiColorScheme.accent(this)));
        search.setTextColor(UiColorScheme.onAccent(this));
        LinearLayout.LayoutParams sb=new LinearLayout.LayoutParams(-1,dp(50));sb.topMargin=dp(10);root.addView(search,sb);search.setOnClickListener(v->search());
        message=label("Поиск по публичному каталогу MangaLib. Выберите главы для сохранения в CBZ.",13,false);
        message.setPadding(0,dp(12),0,dp(12));root.addView(message);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        String initial=getIntent().getStringExtra("mangalib_query");if(initial!=null){query.setText(initial);search();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        DownloadFolderPicker.receive(this,request,result,data,DownloadFolderPicker.MANGA_REQUEST,DownloadFolderPicker.sourceKey("mangalib"));
    }
    private void loading(String text){message.setText(text);list.removeAllViews();}
    private void showError(String text){message.setText(text);Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void search(){
        String q=query.getText().toString().trim();if(q.isEmpty())return;
        int token=++generation;loading("Поиск MangaLib…");
        executor.execute(()->{
            try{
                String slug=MangaLibApi.slugFromLink(q);
                if(!slug.isEmpty()){
                    MangaLibApi api=new MangaLibApi();
                    MangaLibApi.Title t;
                    try{t=api.titleBySlug(slug);}catch(Exception ignored){
                        // A metadata error must not prevent trying the public chapter endpoint.
                        t=new MangaLibApi.Title(slug,slug,"");
                    }
                    final MangaLibApi.Title selected=t;
                    runOnUiThread(()->{if(token==generation&&!isFinishing())loadChapters(selected);});return;
                }
                String searchQuery=MangaLibApi.queryFromLink(q);
                if(searchQuery.isEmpty())throw new java.io.IOException("Ссылка MangaLib не содержит названия или идентификатора произведения");
                List<MangaLibApi.Title> found=new MangaLibApi().search(searchQuery);
                runOnUiThread(()->{if(token!=generation||isFinishing())return;
                    message.setText(found.isEmpty()?"По запросу ничего не найдено. Проверьте название или ссылку MangaLib":"Найдено: "+found.size()+" · MangaLib");list.removeAllViews();
                    for(MangaLibApi.Title t:found)addRow(t.title+"\n"+t.slug,()->loadChapters(t));
                });
            }catch(Exception e){runOnUiThread(()->{if(token==generation)showError("Поиск: "+e.getMessage());});}
        });
    }
    private void addRow(String title,Runnable action){TextView t=label(title,15,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackground(panel());
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(9);list.addView(t,lp);t.setOnClickListener(v->action.run());}
    private void loadChapters(MangaLibApi.Title title){int token=++generation;loading("Загрузка глав: "+title.title);
        executor.execute(()->{
            try{List<MangaLibApi.Chapter> chapters=new MangaLibApi().chapters(title.slug);
                runOnUiThread(()->{if(token!=generation||isFinishing())return;message.setText(title.title+" · Глав: "+chapters.size());
                    list.removeAllViews();addRow("← Вернуться к поиску",()->search());
                    addRow("Выбрать главы для скачивания (CBZ)",()->selectChapters(title,chapters));
                    for(MangaLibApi.Chapter c:chapters){
                        MangaLibStorage.Job old=MangaLibStorage.find(this,title.slug+"|"+c.key());
                        String suffix=old!=null&&"complete".equals(old.status)?"  ✓":old!=null&&"running".equals(old.status)?"  …":"";
                        addRow(c.display()+suffix,()->queue(title,java.util.Collections.singletonList(c)));
                    }
                });
            }catch(Exception e){runOnUiThread(()->{if(token==generation)showError("Главы: "+e.getMessage());});}
        });
    }
    private void selectChapters(MangaLibApi.Title title,List<MangaLibApi.Chapter> chapters){
        if(chapters.isEmpty())return;
        if(chapters.size()>1000){Toast.makeText(this,"Слишком много глав для одного списка",Toast.LENGTH_LONG).show();return;}
        String[] names=new String[chapters.size()];boolean[] checked=new boolean[chapters.size()];
        for(int i=0;i<chapters.size();i++){names[i]=chapters.get(i).display();MangaLibStorage.Job j=MangaLibStorage.find(this,title.slug+"|"+chapters.get(i).key());
            names[i]+=j!=null&&"complete".equals(j.status)?" ✓":"";
        }
        new SwipeDialogs.Builder(this).setTitle("Главы · "+title.title)
            .setMultiChoiceItems(names,checked,(d,index,value)->checked[index]=value)
            .setNegativeButton("Отмена",null).setPositiveButton("Скачать в CBZ",(d,w)->{
                ArrayList<MangaLibApi.Chapter> selected=new ArrayList<>();
                for(int i=0;i<checked.length;i++)if(checked[i])selected.add(chapters.get(i));
                queue(title,selected);
            }).show();
    }
    private void queue(MangaLibApi.Title title,List<MangaLibApi.Chapter> selected){
        if(selected.isEmpty()){Toast.makeText(this,"Выберите главы",Toast.LENGTH_SHORT).show();return;}
        ArrayList<MangaLibStorage.Job> requested=new ArrayList<>();
        for(MangaLibApi.Chapter chapter:selected){
            MangaLibStorage.Job job=new MangaLibStorage.Job();job.slug=title.slug;job.series=title.title;
            job.volume=chapter.volume;job.number=chapter.number;job.branch=chapter.branch;job.chapter=chapter.display();
            MangaLibStorage.Job existing=MangaLibStorage.find(this,job.id());
            if(existing!=null){
                if("complete".equals(existing.status)||"running".equals(existing.status)
                    ||"queued".equals(existing.status))continue;
                // Retain downloaded-page metadata/cache when retrying a failed chapter.
                job=existing;
            }
            requested.add(job);
        }
        int added=MangaLibQueue.enqueue(this,requested);
        Toast.makeText(this,added>0?"Глав в очереди: "+added:"Эти главы уже загружены или в очереди",Toast.LENGTH_LONG).show();
        if(added>0)startActivity(new Intent(this,DownloadActivity.class));
    }
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}
