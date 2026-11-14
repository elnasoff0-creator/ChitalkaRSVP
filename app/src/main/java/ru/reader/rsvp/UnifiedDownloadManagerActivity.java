package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared view-only download manager: source engines retain their existing queues. */
public final class UnifiedDownloadManagerActivity extends NavigationActivity {
    static final String EXTRA_ID="unified_download_id", EXTRA_TITLE="unified_download_title",
        EXTRA_COVER="unified_download_cover", EXTRA_TYPE="unified_download_type";
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,MUTED=0xff716259,ACCENT=0xffa26949;
    private final ExecutorService disk=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private String id="",hintTitle="",hintCover="",hintType="",tab="files";
    private TextView title,metadata,summary,percent,modeLabel;
    private ImageView artwork;
    private LinearLayout content,tabRow,buttons;
    private ProgressBar progress;
    private boolean visible,loading;
    private int page=0;
    private DownloadsRepository.Item selected;
    private final Runnable ticker=new Runnable(){@Override public void run(){if(!visible)return;reload();main.postDelayed(this,4000);}};
    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable shape(int color){GradientDrawable bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(dp(13));return bg;}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(INK);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView button(String s,Runnable act){TextView v=text(s,13,true);v.setGravity(Gravity.CENTER);v.setPadding(dp(10),dp(11),dp(10),dp(11));v.setBackground(shape(UiColorScheme.neutral(this)));v.setOnClickListener(w->act.run());return v;}
    private void add(LinearLayout target,View v,int gap){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(gap);target.addView(v,p);}
    private static String human(long n){if(n<=0)return "";return String.format(Locale.getDefault(),"%.1f МБ",n/1048576.0);}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        id=getIntent().getStringExtra(EXTRA_ID);if(id==null||id.isEmpty()){finish();return;}
        hintTitle=getIntent().getStringExtra(EXTRA_TITLE);if(hintTitle==null)hintTitle="";
        hintCover=getIntent().getStringExtra(EXTRA_COVER);if(hintCover==null)hintCover="";
        hintType=getIntent().getStringExtra(EXTRA_TYPE);if(hintType==null)hintType="";
        if(state!=null){tab=state.getString("tab","files");page=state.getInt("page",0);}
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);root.setPadding(dp(13),dp(24),dp(13),dp(10));setContentView(root);SafeSystemBars.matchBackground(this,BG);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(50)));
        ImageView menu=new ImageView(this);menu.setImageResource(R.drawable.ic_menu_lines);menu.setColorFilter(INK);menu.setPadding(dp(10),dp(10),dp(10),dp(10));menu.setBackground(shape(PANEL));toolbar.addView(menu,new LinearLayout.LayoutParams(dp(44),dp(44)));menu.setOnClickListener(v->UniversalDrawer.show(this));
        TextView heading=text("Менеджер загрузки",22,true);heading.setTypeface(Typeface.SERIF,Typeface.BOLD);heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(0,-1,1);h.leftMargin=dp(12);toolbar.addView(heading,h);
        TextView back=button("Назад",this::finish);toolbar.addView(back,new LinearLayout.LayoutParams(dp(68),dp(44)));
        LinearLayout hero=new LinearLayout(this);hero.setPadding(dp(11),dp(11),dp(11),dp(11));hero.setGravity(Gravity.TOP);hero.setBackground(shape(PANEL));add(root,hero,13);
        FrameLayout coverContainer=new FrameLayout(this);coverContainer.setBackground(shape(UiColorScheme.neutral(this)));
        hero.addView(coverContainer,new LinearLayout.LayoutParams(dp(82),dp(113)));
        ImageView blankCover=new ImageView(this);blankCover.setImageResource(R.drawable.ic_book);
        blankCover.setColorFilter(ACCENT);blankCover.setPadding(dp(17),dp(28),dp(17),dp(28));
        coverContainer.addView(blankCover,new FrameLayout.LayoutParams(-1,-1));
        artwork=new ImageView(this);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverContainer.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(11),0,0,0);hero.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        title=text(hintTitle.isEmpty()?"Загрузка":hintTitle,19,true);title.setMaxLines(3);info.addView(title);
        metadata=text("",12,false);metadata.setTextColor(MUTED);add(info,metadata,7);
        summary=text("Загружаем информацию о файлах…",13,false);add(root,summary,12);
        LinearLayout progressLine=new LinearLayout(this);progressLine.setGravity(Gravity.CENTER_VERTICAL);add(root,progressLine,9);
        modeLabel=text("Общий прогресс",13,true);progressLine.addView(modeLabel,new LinearLayout.LayoutParams(0,-2,1));percent=text("…",14,true);progressLine.addView(percent);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);LinearLayout.LayoutParams pl=new LinearLayout.LayoutParams(-1,dp(8));pl.topMargin=dp(6);root.addView(progress,pl);
        buttons=new LinearLayout(this);buttons.setOrientation(LinearLayout.HORIZONTAL);add(root,buttons,11);
        tabRow=new LinearLayout(this);tabRow.setGravity(Gravity.CENTER_VERTICAL);add(root,tabRow,13);
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,0,1);sp.topMargin=dp(8);root.addView(scroll,sp);
        content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);scroll.addView(content);
        if(!hintCover.isEmpty())CoverImageLoader.show(artwork,hintCover,"edge".equals(hintType));
        drawTabs();reload();
    }
    @Override protected void onResume(){super.onResume();visible=true;main.removeCallbacks(ticker);main.post(ticker);}
    @Override protected void onPause(){visible=false;main.removeCallbacks(ticker);super.onPause();}
    @Override protected void onDestroy(){visible=false;disk.shutdownNow();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("tab",tab);state.putInt("page",page);super.onSaveInstanceState(state);}
    private void drawTabs(){tabRow.removeAllViews();for(String[] pair:new String[][]{{"meta","Мета"},{"files","Файлы"},{"cover","Обложка"}}){
        TextView b=button(pair[1],()->{tab=pair[0];page=0;drawTabs();render();});
        if(tab.equals(pair[0])){b.setBackground(shape(UiColorScheme.accent(this)));b.setTextColor(UiColorScheme.onAccent(this));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.rightMargin=dp(5);tabRow.addView(b,lp);
    }}
    private static final class FileRow {String name="",status="",detail="";int percent=-1;long bytes;
        FileRow(String n,String s,int p,long b){name=n;status=s;percent=p;bytes=b;}}
    private static final class Snapshot {DownloadsRepository.Item item;ArrayList<FileRow> files=new ArrayList<>();String error="";}
    private void reload(){if(loading)return;loading=true;
        disk.execute(()->{
            Snapshot next=new Snapshot();
            try{for(DownloadsRepository.Item item:DownloadsRepository.load(getApplicationContext(),null))if(id.equals(item.id)){next.item=item;break;}
                if(next.item!=null)listFiles(next);}
            catch(Exception ex){next.error=ex.getMessage()==null?ex.toString():ex.getMessage();}
            main.post(()->{loading=false;if(isFinishing()||isDestroyed())return;
                if(next.item!=null){selected=next.item;visibleFiles=next.files;render();}
                else if(selected==null){summary.setText(next.error.isEmpty()?"Запись пока недоступна. Попробуйте обновить список загрузок.":next.error);}
            });
        });
    }
    private ArrayList<FileRow> visibleFiles=new ArrayList<>();
    private void listFiles(Snapshot s){DownloadsRepository.Item i=s.item;
        if(DownloadsRepository.AUDIO.equals(i.type)){
            BookImportManager.Job job=i.job; if(job==null)for(BookImportManager.Job j:BookImportManager.load(this))if(j.source.equals(i.source)){job=j;break;}
            if(job==null)return;
            for(int n=0;n<job.chapters.size();n++){
                int state=n<job.states.size()?job.states.get(n):-1;
                String label=job.chapters.get(n).name;
                FileRow r=new FileRow(label,state<0?"Не выбрано":state>=100?"Скачано":job.paused?"Пауза":job.cancelled?"Отменено":state>0?"Скачивается":"Ожидает",Math.max(0,state),0);
                File file=BookImportManager.trackFile(this,job,n);
                r.bytes=file.isFile()?file.length():0;
                String uri=BookImportManager.relocated(job,n);if(!uri.isEmpty())r.detail="Перенесён в выбранную папку";
                else if(r.bytes>0)r.detail=human(r.bytes);
                s.files.add(r);
            }
        } else if(DownloadsRepository.RANOBE.equals(i.type)){
            for(RanobeLibStorage.Job j:RanobeLibStorage.series(this,i.source)){
                FileRow r=new FileRow("Том "+j.volume+" · Глава "+j.number+(j.chapter.isEmpty()?"":" · "+j.chapter),j.status, "complete".equals(j.status)?100:0,j.bytes);r.detail=j.error;s.files.add(r);
            }
        } else if(DownloadsRepository.MANGA.equals(i.type)){
            for(MangaLibStorage.Job j:MangaLibStorage.jobs(this))if(i.source.equals(j.slug)){
                FileRow r=new FileRow("Том "+j.volume+" · Глава "+j.number+(j.chapter.isEmpty()?"":" · "+j.chapter),j.status,j.total>0?Math.min(100,j.done*100/j.total):"complete".equals(j.status)?100:0,j.bytes);
                r.detail=j.error.isEmpty()?"Страниц: "+j.done+"/"+j.total:j.error;s.files.add(r);
            }
        } else if(DownloadsRepository.LINK.equals(i.type)){
            LinkDownloadStore.Batch b=i.link!=null?i.link:LinkDownloadStore.get(this,i.id.replaceFirst("^link:",""));if(b==null)return;
            for(LinkDownloadStore.Entry e:b.files){FileRow r=new FileRow(e.displayTitle.isEmpty()?e.name:e.displayTitle,e.status,e.size>0?(int)Math.min(100,100*e.bytes/e.size):"completed".equals(e.status)?100:-1,e.bytes);r.detail=e.error;s.files.add(r);}
        } else if(DownloadsRepository.EDGE.equals(i.type)){
            EdgeFullBookStore.Job job=i.fullJob!=null?i.fullJob:EdgeFullBookStore.find(this,i.bookUri,i.title);
            if(job!=null)for(EdgeFullBookStore.Task t:job.tasks){FileRow r=new FileRow(t.chapter==EdgeFullBookStore.PLAN?"Подготовка всей книги":"Глава "+(t.chapter+1),t.status,t.total>0?(int)Math.min(100,100L*t.done/t.total):EdgeFullBookStore.DONE.equals(t.status)?100:0,0);r.detail=t.error;s.files.add(r);}
        } else if(DownloadsRepository.RANOBES.equals(i.type)){
            for(RanobesStorage.Job j:RanobesStorage.jobs(this,i.source)){
                FileRow r=new FileRow("Том "+j.volume+" · "+j.title,j.status,"complete".equals(j.status)?100:0,j.bytes);r.detail=j.error;s.files.add(r);
            }
        }
    }
    private void render(){DownloadsRepository.Item item=selected;if(item==null)return;
        title.setText(item.title);StringBuilder meta=new StringBuilder();
        appendMeta(meta,item.author);appendMeta(meta,item.provider.isEmpty()?item.type:item.provider);appendMeta(meta,item.format);
        if(!item.narrator.isEmpty())appendMeta(meta,"Читает: "+item.narrator);
        metadata.setText(meta.toString());
        if(!item.cover.isEmpty())CoverImageLoader.show(artwork,item.cover,DownloadsRepository.EDGE.equals(item.type));
        summary.setText(item.status+" · "+item.description);
        int value=item.coveragePercent;
        if(item.total>0&&value<=0)value=(int)Math.min(100,100L*item.done/item.total);
        if(item.ready)value=100;
        progress.setProgress(Math.max(0,value));percent.setText(value+"%");
        buttons.removeAllViews();
        if(!item.ready){TextView p=button(item.running?"Пауза":"Продолжить",()->control(item));buttons.addView(p,new LinearLayout.LayoutParams(0,dp(45),1));}
        if(item.ready||item.done>0){TextView open=button(DownloadsRepository.AUDIO.equals(item.type)||DownloadsRepository.EDGE.equals(item.type)?"Слушать":"Читать / Открыть",()->openContent(item));LinearLayout.LayoutParams o=new LinearLayout.LayoutParams(0,dp(45),1);o.leftMargin=dp(6);buttons.addView(open,o);}
        TextView detailed=button("Подробнее",()->openAdvanced(item));LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(0,dp(45),1);d.leftMargin=dp(6);buttons.addView(detailed,d);
        content.removeAllViews();
        if("meta".equals(tab))showMeta(item);
        else if("cover".equals(tab))showCover(item);
        else showFiles();
    }
    private static void appendMeta(StringBuilder b,String value){if(value!=null&&!value.trim().isEmpty()){if(b.length()>0)b.append(" · ");b.append(value);}}
    private void info(String label,String value){if(value==null||value.isEmpty())return;
        TextView row=text(label+"  "+value,14,false);row.setPadding(dp(13),dp(13),dp(13),dp(13));row.setBackground(shape(PANEL));add(content,row,7);}
    private void showMeta(DownloadsRepository.Item i){info("Название:",i.title);info("Источник:",i.provider);
        info("Автор:",i.author);info("Читает:",i.narrator);info("Формат:",i.format);
        info("Главы / файлы:",i.done+" / "+i.total);info("Загружено:",human(i.downloadedBytes));
        info("Состояние:",i.status);if(!i.description.isEmpty())info("Описание:",i.description);
        if(i.folder!=null)info("Папка:",i.folder.getAbsolutePath());}
    private void showCover(DownloadsRepository.Item i){ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(290));p.topMargin=dp(15);content.addView(img,p);
        if(i.cover.isEmpty()){
            img.setImageResource(R.drawable.ic_book);img.setColorFilter(ACCENT);
            add(content,text("Обложка не найдена. Загрузка книги не зависит от обложки.",13,false),12);
        }
        else CoverImageLoader.show(img,i.cover,DownloadsRepository.EDGE.equals(i.type));}
    private void showFiles(){if(visibleFiles.isEmpty()){add(content,text("Пока нет отдельных файлов или глав. Нажмите «Подробнее» для управления исходной задачей.",13,false),12);return;}
        int from=page*80,to=Math.min(visibleFiles.size(),from+80);
        if(from>=visibleFiles.size()){page=0;from=0;to=Math.min(visibleFiles.size(),80);}
        add(content,text("Файлы / главы: "+visibleFiles.size(),15,true),6);
        for(int k=from;k<to;k++){FileRow r=visibleFiles.get(k);LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);block.setPadding(dp(12),dp(9),dp(12),dp(9));block.setBackground(shape(PANEL));add(content,block,7);
            TextView name=text(r.name,14,true);name.setMaxLines(2);block.addView(name);
            TextView status=text(r.status+(r.detail.isEmpty()?"":" · "+r.detail)+(r.bytes>0?" · "+human(r.bytes):""),12,false);status.setTextColor(MUTED);add(block,status,4);
            if(r.percent>=0&&r.percent<100){ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(r.percent);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(5));p.topMargin=dp(5);block.addView(bar,p);}
        }
        if(to<visibleFiles.size())add(content,button("Следующие файлы ("+to+" / "+visibleFiles.size()+")",()->{page++;render();}),10);
        if(page>0)add(content,button("В начало",()->{page=0;render();}),7);
    }
    private void control(DownloadsRepository.Item i){try{
        if(DownloadsRepository.AUDIO.equals(i.type)){
            Intent intent=new Intent(this,DownloadWorker.class).setAction(i.running?DownloadWorker.PAUSE:DownloadWorker.START).putExtra("source",i.source);launch(intent,!i.running);
        }else if(DownloadsRepository.RANOBE.equals(i.type)){if(i.running)RanobeLibQueue.pause(this,i.source);else RanobeLibQueue.resume(this,i.source);}
        else if(DownloadsRepository.RANOBES.equals(i.type)){if(i.running)RanobesQueue.pause(this,i.source);else RanobesQueue.resume(this,i.source);}
        else if(DownloadsRepository.MANGA.equals(i.type)){if(i.running)MangaLibQueue.pause(this,i.source);else MangaLibQueue.resume(this,i.source);}
        else if(DownloadsRepository.LINK.equals(i.type)){
            Intent intent=new Intent(this,DirectLinkDownloadService.class).setAction(i.running?DirectLinkDownloadService.PAUSE:DirectLinkDownloadService.START).putExtra(DirectLinkDownloadService.EXTRA_ID,i.id.substring(5));launch(intent,!i.running);
        }else if(DownloadsRepository.EDGE.equals(i.type)){
            Intent intent=new Intent(this,EdgeFullBookService.class).setAction(i.running?EdgeFullBookService.PAUSE:EdgeFullBookService.RESUME).putExtra(EdgeFullBookService.EXTRA_URI,i.bookUri);launch(intent,!i.running);
        }
        main.postDelayed(this::reload,400);
    }catch(Exception e){Toast.makeText(this,"Ошибка: "+e.getMessage(),Toast.LENGTH_LONG).show();}}
    private void launch(Intent intent,boolean foreground){if(foreground&&Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);}
    private void openContent(DownloadsRepository.Item i){
        Intent request=null;
        if(DownloadsRepository.AUDIO.equals(i.type))request=new Intent(this,AudioPlayerActivity.class).putExtra("audio_book_id","url:"+i.source);
        else if(DownloadsRepository.RANOBE.equals(i.type)&&RanobeLibStorage.hasBook(this,i.source))request=new Intent(this,ReaderActivity.class).putExtra("book_uri",RanobeLibStorage.bookUri(this,i.source));
        else if(DownloadsRepository.RANOBES.equals(i.type)&&RanobesStorage.hasBook(this,i.source))request=new Intent(this,ReaderActivity.class).putExtra("book_uri",RanobesStorage.bookUri(this,i.source));
        else if(DownloadsRepository.EDGE.equals(i.type))request=new Intent(this,ReaderActivity.class).putExtra("book_uri",i.bookUri).putExtra("reading_mode_override",3);
        else if(DownloadsRepository.MANGA.equals(i.type))request=new Intent(this,ComicReaderActivity.class).putExtra("comic_uri",MangaLibStorage.comicUri(i.source));
        if(request!=null)startActivity(request);else Toast.makeText(this,"Файл пока не готов. Откройте подробности загрузки.",Toast.LENGTH_LONG).show();
    }
    private void openAdvanced(DownloadsRepository.Item i){Intent detail=null;
        if(DownloadsRepository.EDGE.equals(i.type))detail=new Intent(this,EdgeChapterDownloadsActivity.class).putExtra("book_uri",i.bookUri).putExtra("book_title",i.title);
        else if(DownloadsRepository.RANOBE.equals(i.type))detail=new Intent(this,RanobeLibBookActivity.class).putExtra("ranobelib_slug",i.source);
        else if(DownloadsRepository.RANOBES.equals(i.type))detail=new Intent(this,RanobesBookActivity.class).putExtra("ranobes_id",i.source);
        else if(DownloadsRepository.MANGA.equals(i.type))detail=new Intent(this,MangaLibChapterDownloadsActivity.class).putExtra("mangalib_slug",i.source);
        else if(DownloadsRepository.AUDIO.equals(i.type))detail=new Intent(this,KvuheBookActivity.class).putExtra("source",i.source).putExtra("title",i.sourceTitle).putExtra("auto_fetch",false);
        else if(DownloadsRepository.LINK.equals(i.type))detail=new Intent(this,LinkDownloadDetailsActivity.class).putExtra(DirectLinkDownloadService.EXTRA_ID,i.id.substring(5));
        if(detail!=null)startActivity(detail);
    }
}
