package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Status and controls for one persistent direct-link batch. */
public final class LinkDownloadDetailsActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String id;private LinearLayout root;private boolean visible;
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));return d;}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(0xff30221e);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(0xff30221e);b.setBackground(bg(0xfffff9f0));return b;}
    private void add(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);root.addView(v,p);}
    @Override public void onCreate(Bundle b){super.onCreate(b);id=getIntent().getStringExtra(DirectLinkDownloadService.EXTRA_ID);ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setPadding(dp(15),dp(30),dp(15),dp(20));root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xfff3e7d5);scroll.addView(root);setContentView(scroll);}
    @Override public void onResume(){super.onResume();visible=true;handler.post(ticker);}
    @Override public void onPause(){visible=false;handler.removeCallbacks(ticker);super.onPause();}
    private final Runnable ticker=new Runnable(){@Override public void run(){if(!visible)return;render();handler.postDelayed(this,1100);}};
    private void command(String cmd){Intent intent=new Intent(this,DirectLinkDownloadService.class).setAction(cmd).putExtra(DirectLinkDownloadService.EXTRA_ID,id);if(Build.VERSION.SDK_INT>=26&&DirectLinkDownloadService.START.equals(cmd))startForegroundService(intent);else startService(intent);handler.postDelayed(this::render,250);}
    private void render(){LinkDownloadStore.Batch b=LinkDownloadStore.get(this,id);root.removeAllViews();if(b==null){add(text("Задание не найдено",20));return;}
        Button back=button("← Назад");add(back);back.setOnClickListener(v->BackNavigation.back(this));
        TextView title=text(b.title,24);title.setTypeface(Typeface.SERIF,Typeface.BOLD);add(title);
        add(text("Сохранение: "+b.treeLabel+(b.subfolder.isEmpty()?"":" / "+b.subfolder),14));
        add(text("Готово: "+b.completed()+" / "+b.files.size()+" · "+status(b.status),15));
        if(!b.error.isEmpty())add(text("Ошибка: "+b.error,13));
        LinearLayout actions=new LinearLayout(this);add(actions);
        Button pause=button("running".equals(b.status)?"Пауза":b.completed()==b.files.size()?"Готово":"Продолжить");actions.addView(pause,new LinearLayout.LayoutParams(0,dp(47),1));pause.setEnabled(b.completed()<b.files.size());pause.setOnClickListener(v->command("running".equals(b.status)?DirectLinkDownloadService.PAUSE:DirectLinkDownloadService.START));
        Button cancel=button("Отменить");actions.addView(cancel,new LinearLayout.LayoutParams(0,dp(47),1));cancel.setOnClickListener(v->new SwipeDialogs.Builder(this).setTitle("Отменить скачивание?").setMessage("Готовые файлы сохранятся.").setNegativeButton("Нет",null).setPositiveButton("Да",(d,w)->command(DirectLinkDownloadService.CANCEL)).show());
        Button folder=button("▣ Открыть папку");add(folder);folder.setOnClickListener(v->{try{Uri tree=Uri.parse(b.treeUri);Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);if(Build.VERSION.SDK_INT>=26)i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,tree);startActivity(i);}catch(Exception ex){Toast.makeText(this,"Папка недоступна",Toast.LENGTH_SHORT).show();}});
        int completedAudio=0;for(LinkDownloadStore.Entry e:b.files)if("mp3".equals(e.format)&&"completed".equals(e.status))completedAudio++;
        if(completedAudio>0){Button library=button("Добавить MP3 в аудиобиблиотеку");add(library);library.setOnClickListener(v->{LinkDownloadStore.addToLibrary(this,b);Toast.makeText(this,"Готовые MP3 добавлены в аудиобиблиотеку",Toast.LENGTH_SHORT).show();});}
        add(text("Книги FB2 и EPUB автоматически добавляются в библиотеку после скачивания.",12));
        for(LinkDownloadStore.Entry entry:b.files){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(11),dp(12),dp(11),dp(12));card.setBackground(bg(0xfffff9f0));add(card);
            TextView name=text(entry.name,16);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(name);
            String summary=status(entry.status)+" · "+String.format(java.util.Locale.ROOT,"%.1f МБ",entry.bytes/1048576.0)+(entry.size>0?" / "+String.format(java.util.Locale.ROOT,"%.1f МБ",entry.size/1048576.0):"");card.addView(text(summary,13));
            if(!entry.error.isEmpty())card.addView(text(entry.error,12));
            if("completed".equals(entry.status)&&!entry.savedUri.isEmpty()){
                Button play=button("mp3".equals(entry.format)?"▶ Открыть MP3":"Читать "+LinkFileTypes.formatName(entry.format));card.addView(play);play.setOnClickListener(v->{try{
                    if(!"mp3".equals(entry.format)){
                        Intent intent=new Intent(this,ReaderActivity.class).putExtra("book_uri",entry.savedUri).putExtra("reset",false);
                        startActivity(intent);
                    }else{
                        Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(entry.savedUri),"audio/mpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(intent,"Открыть аудиофайл"));
                    }
                }catch(Exception ex){Toast.makeText(this,"Не удалось открыть файл: "+ex.getMessage(),Toast.LENGTH_LONG).show();}});
            }
        }
    }
    private String status(String s){switch(s){case "running":return "Скачивается";case "queued":return "Ожидает";case "paused":return "Приостановлено";case "completed":return "Готово";case "cancelled":return "Отменено";case "error":return "Ошибка";default:return s;}}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
