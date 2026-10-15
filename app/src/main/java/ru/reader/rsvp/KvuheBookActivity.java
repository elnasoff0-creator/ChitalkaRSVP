package ru.reader.rsvp;

import android.content.Intent;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

/** Full audiobook preview launched from search; inherits the verified chapter downloader. */
public final class KvuheBookActivity extends UrlImportActivity {
    private TextView details;
    @Override void draw(){
        handler.removeCallbacksAndMessages(null);
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(background);
        screen.setPadding(dp(13),dp(22),dp(13),dp(12));setContentView(screen);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        screen.addView(head,new LinearLayout.LayoutParams(-1,dp(50)));
        ImageView back=new ImageView(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(foreground);
        back.setPadding(dp(12),dp(12),dp(12),dp(12));back.setBackground(shape(0xfffff9f0));
        back.setContentDescription("Назад");back.setOnClickListener(v->BackNavigation.back(this));
        head.addView(back,new LinearLayout.LayoutParams(dp(49),dp(49)));
        TextView header=text("Аудиокнига",23);header.setTypeface(Typeface.SERIF,Typeface.BOLD);header.setGravity(Gravity.CENTER);
        head.addView(header,new LinearLayout.LayoutParams(0,-1,1));head.addView(new View(this),new LinearLayout.LayoutParams(dp(49),dp(49)));

        ScrollView scroll=new ScrollView(this);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(2),dp(14),dp(2),dp(16));scroll.addView(root);
        coverPreview=new ImageView(this);coverPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(coverPreview,new LinearLayout.LayoutParams(-1,dp(205)));
        String initialCover=getIntent().getStringExtra("cover");
        if(initialCover!=null)CoverImageLoader.show(coverPreview,initialCover,false);
        title=text(value("title","Загрузка книги…"),22);title.setTypeface(Typeface.SERIF,Typeface.BOLD);
        title.setGravity(Gravity.CENTER);root.addView(title);
        details=text("",14);add(root,details);
        String extra=(value("author","").isEmpty()?"":"Автор: "+value("author","")+"\n")
                +(value("reader","").isEmpty()?"":"Читает: "+value("reader","")+"\n")
                +value("description","");
        details.setText(extra);details.setVisibility(extra.isEmpty()?View.GONE:View.VISIBLE);
        summary=text("Получаю список глав…",14);add(root,summary);
        status=text("",13);add(root,status);
        TextView chapterHeader=text("Главы аудиокниги",19);chapterHeader.setTypeface(Typeface.SERIF,Typeface.BOLD);add(root,chapterHeader);
        LinearLayout toggles=new LinearLayout(this);add(root,toggles);
        all=button("Выбрать все");none=button("Снять выделение");toggles.addView(all,new LinearLayout.LayoutParams(0,dp(48),1));
        toggles.addView(none,new LinearLayout.LayoutParams(0,dp(48),1));
        all.setOnClickListener(v->{for(CheckBox c:checks)if(c.isEnabled())c.setChecked(true);});
        none.setOnClickListener(v->{for(CheckBox c:checks)c.setChecked(false);});
        chapters=new LinearLayout(this);chapters.setOrientation(LinearLayout.VERTICAL);add(root,chapters);
        find=button("Повторить анализ страницы");add(root,find);find.setOnClickListener(v->fetch());
        overall=text("",13);add(root,overall);

        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.VERTICAL);
        screen.addView(actions,new LinearLayout.LayoutParams(-1,-2));
        download=button("Скачать выбранные главы");download.setTextColor(0xffffffff);download.setBackground(shape(0xff9b6143));
        actions.addView(download,new LinearLayout.LayoutParams(-1,dp(50)));
        download.setOnClickListener(v->confirm(false));
        Button complete=button("Скачать всю аудиокнигу");
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.topMargin=dp(7);actions.addView(complete,p);
        complete.setOnClickListener(v->confirm(true));
        // Original job updates remain available; downloads start ONLY upon explicit confirmation.
        handler.postDelayed(new Runnable(){public void run(){showJob();handler.postDelayed(this,1200);}},1200);
        address=new android.widget.EditText(this);address.setText(getIntent().getStringExtra("source"));
        if(getIntent().getBooleanExtra("auto_fetch",false))handler.post(this::fetch);
    }
    private String value(String key,String fallback){String s=getIntent().getStringExtra(key);return s==null||s.isEmpty()?fallback:s;}
    @Override void render(){
        if(preview==null)return;
        title.setText(preview.title.isEmpty()?value("title","Аудиокнига"):preview.title);
        if(!preview.cover.isEmpty())CoverImageLoader.show(coverPreview,preview.cover,false);
        long seconds=0;int available=0;for(KvuheParser.Chapter c:preview.chapters){seconds+=c.seconds;if(!c.url.isEmpty())available++;}
        summary.setText("Глав: "+preview.chapters.size()+" · Доступны MP3: "+available+" · Длительность: "+format(seconds));
        status.setText(preview.warning.isEmpty()?"Проверьте выбранные главы перед скачиванием.":preview.warning);
        chapters.removeAllViews();checks.clear();
        for(int i=0;i<preview.chapters.size();i++){
            KvuheParser.Chapter c=preview.chapters.get(i);CheckBox ch=new CheckBox(this);
            ch.setText(String.format(Locale.ROOT,"%03d. %s · %s%s",i+1,c.name,c.time,c.url.isEmpty()?" · MP3 не найден":""));
            ch.setTextColor(foreground);ch.setChecked(!c.url.isEmpty());ch.setEnabled(!c.url.isEmpty());
            chapters.addView(ch);checks.add(ch);
        }
        download.setEnabled(available>0);
        if(available==0)status.setText((preview.warning.isEmpty()?"":preview.warning+"\n")+
                "Прямые MP3-ссылки не опубликованы или не определены. Главы видны, но их скачивание сейчас недоступно.");
    }
}
