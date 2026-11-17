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
public final class LinkDownloadDetailsActivity extends NavigationActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String id;private LinearLayout root;private boolean visible;
    private int pageBg,panel,ink,paletteSignature;
    private void readPalette(){pageBg=UiColorScheme.background(this);
        panel=UiColorScheme.neutral(this);ink=UiColorScheme.foreground(this);
        paletteSignature=UiColorScheme.paletteSignature(this);
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(15));return d;}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(ink);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(ReaderButtonColors.contrastingText(panel));b.setBackground(bg(panel));return b;}
    private void add(View v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(9);root.addView(v,p);}
    @Override public void onCreate(Bundle b){super.onCreate(b);readPalette();id=getIntent().getStringExtra(DirectLinkDownloadService.EXTRA_ID);ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setPadding(dp(15),dp(30),dp(15),dp(20));root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(pageBg);scroll.addView(root);setContentView(scroll);SafeSystemBars.matchBackground(this,pageBg);}
    // The store publishes changes; do not rebuild the entire view tree on a timer.
    private String displayedKey="";
    private TextView batchState,batchError;
    private Button pauseAction,libraryAction;
    private final java.util.ArrayList<TextView> fileStates=new java.util.ArrayList<>();
    private final java.util.ArrayList<TextView> fileErrors=new java.util.ArrayList<>();
    private final java.util.ArrayList<Button> fileOpen=new java.util.ArrayList<>();
    private final DownloadChangeBus.Listener downloadListener=changed->{
        if(visible&&("link:"+id).equals(changed)){
            handler.removeCallbacks(refreshRow);handler.postDelayed(refreshRow,160L);
        }
    };
    private final Runnable refreshRow=this::render;
    private void command(String cmd){
        Intent intent=new Intent(this,DirectLinkDownloadService.class).setAction(cmd)
            .putExtra(DirectLinkDownloadService.EXTRA_ID,id);
        if(Build.VERSION.SDK_INT>=26&&DirectLinkDownloadService.START.equals(cmd))startForegroundService(intent);
        else startService(intent);
        handler.postDelayed(refreshRow,200);
    }
    @Override public void onResume(){super.onResume();
        if(paletteSignature!=UiColorScheme.paletteSignature(this)){recreate();return;}
        visible=true;DownloadChangeBus.add(downloadListener);render();
    }
    @Override public void onPause(){visible=false;DownloadChangeBus.remove(downloadListener);
        handler.removeCallbacks(refreshRow);super.onPause();
    }
    private String structureKey(LinkDownloadStore.Batch b){
        StringBuilder key=new StringBuilder(b.id);
        for(LinkDownloadStore.Entry file:b.files)key.append('|').append(file.name)
            .append(':').append(file.format).append(':').append(file.source);
        return key.toString();
    }
    private void render(){
        if(!visible)return;
        LinkDownloadStore.Batch batch=LinkDownloadStore.get(this,id);
        if(batch==null){if(!"missing".equals(displayedKey)){
            root.removeAllViews();add(text("Задание не найдено",20));displayedKey="missing";}
            return;
        }
        String key=structureKey(batch);
        if(!key.equals(displayedKey))buildOnce(batch,key);
        if(batchState!=null)setText(batchState,"Готово: "+batch.completed()+" / "+batch.files.size()+
            " · "+status(batch.status));
        if(batchError!=null){setText(batchError,"Ошибка: "+batch.error);
            batchError.setVisibility(batch.error.isEmpty()?View.GONE:View.VISIBLE);}
        if(pauseAction!=null){setText(pauseAction,"running".equals(batch.status)?"Пауза":
            batch.completed()==batch.files.size()?"Готово":"Продолжить");
            pauseAction.setEnabled(batch.completed()<batch.files.size());}
        int completedAudio=0;
        for(int i=0;i<batch.files.size()&&i<fileStates.size();i++){
            LinkDownloadStore.Entry entry=batch.files.get(i);
            String value=status(entry.status)+" · "+String.format(java.util.Locale.ROOT,"%.1f МБ",
                entry.bytes/1048576.0)+(entry.size>0?" / "+
                String.format(java.util.Locale.ROOT,"%.1f МБ",entry.size/1048576.0):"");
            setText(fileStates.get(i),value);
            setText(fileErrors.get(i),entry.error);
            fileErrors.get(i).setVisibility(entry.error.isEmpty()?View.GONE:View.VISIBLE);
            fileOpen.get(i).setVisibility("completed".equals(entry.status)&&
                !entry.savedUri.isEmpty()?View.VISIBLE:View.GONE);
            if("mp3".equals(entry.format)&&"completed".equals(entry.status))completedAudio++;
        }
        if(libraryAction!=null)libraryAction.setVisibility(completedAudio>0?View.VISIBLE:View.GONE);
    }
    private void setText(TextView target,String value){
        if(!value.contentEquals(target.getText()))target.setText(value);
    }
    private void buildOnce(LinkDownloadStore.Batch batch,String key){
        displayedKey=key;root.removeAllViews();fileStates.clear();fileErrors.clear();fileOpen.clear();
        Button back=button("☰ Главное меню");add(back);
        back.setOnClickListener(v->UniversalDrawer.show(this));
        TextView title=text(batch.title,24);title.setTypeface(Typeface.SERIF,Typeface.BOLD);add(title);
        add(text("Сохранение: "+batch.treeLabel+(batch.subfolder.isEmpty()?"":" / "+batch.subfolder),14));
        batchState=text("",15);add(batchState);
        batchError=text("",13);batchError.setVisibility(View.GONE);add(batchError);
        LinearLayout actions=new LinearLayout(this);add(actions);
        pauseAction=button("Продолжить");actions.addView(pauseAction,
            new LinearLayout.LayoutParams(0,dp(47),1));
        pauseAction.setOnClickListener(v->{LinkDownloadStore.Batch current=LinkDownloadStore.get(this,id);
            if(current!=null)command("running".equals(current.status)?
                DirectLinkDownloadService.PAUSE:DirectLinkDownloadService.START);});
        Button cancel=button("Отменить");actions.addView(cancel,
            new LinearLayout.LayoutParams(0,dp(47),1));
        cancel.setOnClickListener(v->new SwipeDialogs.Builder(this).setTitle("Отменить скачивание?")
            .setMessage("Готовые файлы сохранятся.").setNegativeButton("Нет",null)
            .setPositiveButton("Да",(d,w)->command(DirectLinkDownloadService.CANCEL)).show());
        Button folder=button("▣ Открыть папку");add(folder);folder.setOnClickListener(v->{try{
            LinkDownloadStore.Batch current=LinkDownloadStore.get(this,id);
            if(current==null)return;
            Uri tree=Uri.parse(current.treeUri);Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if(Build.VERSION.SDK_INT>=26)intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,tree);
            startActivity(intent);
        }catch(Exception ex){Toast.makeText(this,"Папка недоступна",Toast.LENGTH_SHORT).show();}});
        libraryAction=button("Добавить MP3 в аудиобиблиотеку");add(libraryAction);
        libraryAction.setOnClickListener(v->{LinkDownloadStore.Batch current=LinkDownloadStore.get(this,id);
            if(current!=null){LinkDownloadStore.addToLibrary(this,current);
                Toast.makeText(this,"Готовые MP3 добавлены в аудиобиблиотеку",Toast.LENGTH_SHORT).show();}});
        add(text("Книги FB2 и EPUB автоматически добавляются в библиотеку после скачивания.",12));
        for(int i=0;i<batch.files.size();i++){
            final int position=i;LinkDownloadStore.Entry entry=batch.files.get(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(11),dp(12),dp(11),dp(12));card.setBackground(bg(panel));add(card);
            TextView name=text(entry.name,16);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(name);
            TextView details=text("",13);card.addView(details);fileStates.add(details);
            TextView error=text("",12);card.addView(error);fileErrors.add(error);
            Button play=button("mp3".equals(entry.format)?"▶ Открыть MP3":
                "Читать "+LinkFileTypes.formatName(entry.format));card.addView(play);fileOpen.add(play);
            play.setOnClickListener(v->{try{
                LinkDownloadStore.Batch current=LinkDownloadStore.get(this,id);
                if(current==null||position>=current.files.size())return;
                LinkDownloadStore.Entry selected=current.files.get(position);
                if(!"completed".equals(selected.status)||selected.savedUri.isEmpty())return;
                if(!"mp3".equals(selected.format))startActivity(new Intent(this,ReaderActivity.class)
                    .putExtra("book_uri",selected.savedUri).putExtra("reset",false));
                else{Intent open=new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(selected.savedUri),
                    "audio/mpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(open,"Открыть аудиофайл"));}
            }catch(Exception ex){Toast.makeText(this,"Не удалось открыть файл: "+ex.getMessage(),
                Toast.LENGTH_LONG).show();}});
        }
    }
    private String status(String s){switch(s){case "running":return "Скачивается";case "queued":return "Ожидает";case "paused":return "Приостановлено";case "completed":return "Готово";case "cancelled":return "Отменено";case "error":return "Ошибка";default:return s;}}
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
