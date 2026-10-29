package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

/** On-device log viewer; shows only diagnostics written by this application. */
public final class ReaderLogActivity extends NavigationActivity {
    private TextView logView,info;
    private int foreground,background;
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView label(String s,int sp){TextView v=new TextView(this);v.setText(s);
        v.setTextSize(sp);v.setTextColor(foreground);return v;}
    private TextView button(String text,Runnable task){
        TextView v=label(text,14);v.setGravity(Gravity.CENTER);v.setPadding(dp(7),dp(12),dp(7),dp(12));
        v.setOnClickListener(w->task.run());return v;
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);
        background=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5);
        foreground=(Color.red(background)*.299+Color.green(background)*.587+Color.blue(background)*.114)<140
            ?Color.WHITE:0xff30221e;
        SafeSystemBars.matchBackground(this,background);
        LinearLayout base=new LinearLayout(this);base.setOrientation(1);base.setBackgroundColor(background);
        base.setPadding(dp(13),dp(20),dp(13),dp(10));setContentView(base);
        LinearLayout title=new LinearLayout(this);title.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=button("☰",()->UniversalDrawer.show(this));back.setTextSize(30);
        title.addView(back,new LinearLayout.LayoutParams(dp(42),dp(54)));
        TextView heading=label("Лог-файл ПРОЧиталки",20);heading.setTypeface(null,Typeface.BOLD);
        title.addView(heading,new LinearLayout.LayoutParams(0,-2,1));base.addView(title);
        info=label("Данные сохраняются только на устройстве. Текст книги и ключи не записываются.",12);
        info.setPadding(dp(5),dp(5),dp(5),dp(8));base.addView(info);
        LinearLayout actions=new LinearLayout(this);
        addAction(actions,"Обновить",this::refresh);
        addAction(actions,"Поделиться",this::share);
        addAction(actions,"Очистить",this::askClear);
        base.addView(actions);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        logView=label("Загрузка…",12);logView.setTypeface(Typeface.MONOSPACE);logView.setTextIsSelectable(true);
        logView.setPadding(dp(8),dp(10),dp(8),dp(18));
        scroll.addView(logView);base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ReaderDiagnosticLog.event("UI","log_view_open");refresh();
    }
    private void addAction(LinearLayout parent,String name,Runnable action){
        parent.addView(button(name,action),new LinearLayout.LayoutParams(0,dp(52),1));
    }
    private void refresh(){ReaderDiagnosticLog.snapshot(s->logView.setText(s));}
    private void askClear(){new AlertDialog.Builder(this).setTitle("Очистить журнал?")
        .setMessage("Удалить сохранённые события диагностики? Книги и кэш MP3 не затрагиваются.")
        .setNegativeButton("Отмена",null)
        .setPositiveButton("Очистить",(d,w)->ReaderDiagnosticLog.clear(this::refresh)).show();}
    private void share(){ReaderDiagnosticLog.prepareExport(this::sendExport);}
    private void sendExport(File file){
        if(file==null){Toast.makeText(this,"Не удалось подготовить лог-файл",Toast.LENGTH_LONG).show();return;}
        try{
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",file);
            Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM,uri);
            intent.setClipData(ClipData.newRawUri("PROChitalka log",uri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent,"Отправить лог-файл"));
        }catch(Exception e){Toast.makeText(this,"Не удалось открыть отправку журнала",Toast.LENGTH_LONG).show();}
    }
}
