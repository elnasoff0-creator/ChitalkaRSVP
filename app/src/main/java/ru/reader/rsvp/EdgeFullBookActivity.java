package ru.reader.rsvp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

/** Choose a text book and a fixed voice before creating an offline Edge synthesis job. */
public final class EdgeFullBookActivity extends NavigationActivity {
    private static final int BG=0xfff3e7d5,PANEL=0xfffff9f0,INK=0xff30221e,ACCENT=0xffa26949;
    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable rounded(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
    private TextView label(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(INK);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    @Override public void onCreate(Bundle b){super.onCreate(b);build();
        String uri=getIntent().getStringExtra("book_uri");
        if(uri!=null&&!uri.isEmpty()){
            String title=getIntent().getStringExtra("book_title");
            choose(uri,title==null?"Книга":title);
        }
    }
    private void build(){
        LinearLayout outer=new LinearLayout(this);outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(15),dp(24),dp(15),dp(12));outer.setBackgroundColor(BG);setContentView(outer);SafeSystemBars.matchBackground(this,BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);outer.addView(head);
        TextView back=label("☰",26,false);back.setGravity(Gravity.CENTER);back.setBackground(rounded(PANEL));
        head.addView(back,new LinearLayout.LayoutParams(dp(50),dp(50)));back.setOnClickListener(v->UniversalDrawer.show(this));
        TextView title=label("Подготовить аудиокнигу",23,true);title.setTypeface(Typeface.SERIF,Typeface.BOLD);
        LinearLayout.LayoutParams ht=new LinearLayout.LayoutParams(0,-2,1);ht.leftMargin=dp(12);head.addView(title,ht);
        TextView help=label("Выберите FB2 или EPUB из библиотеки. Edge TTS подготовит все недостающие MP3 в фоне. Существующие аудиофайлы останутся на месте.",14,false);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=dp(16);outer.addView(help,hp);
        ScrollView scroll=new ScrollView(this);LinearLayout.LayoutParams scp=new LinearLayout.LayoutParams(-1,0,1);scp.topMargin=dp(14);outer.addView(scroll,scp);
        LinearLayout items=new LinearLayout(this);items.setOrientation(LinearLayout.VERTICAL);scroll.addView(items);
        int count=0;
        try{
            JSONArray books=new JSONArray(getSharedPreferences("reader",0).getString("library","[]"));
            for(int i=0;i<books.length();i++){
                JSONObject book=books.optJSONObject(i);if(book==null)continue;
                String uri=book.optString("uri","");if(uri.isEmpty())continue;
                String name=book.optString("title","Книга");
                String format=book.optString("format","").toLowerCase(java.util.Locale.ROOT);
                if(!(format.contains("fb2")||format.contains("epub")||uri.toLowerCase(java.util.Locale.ROOT).contains(".fb2")
                    ||uri.toLowerCase(java.util.Locale.ROOT).contains(".epub")||format.isEmpty()))continue;
                count++;
                LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(rounded(PANEL));
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(116));cp.bottomMargin=dp(10);items.addView(card,cp);
                ImageView cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                card.addView(cover,new LinearLayout.LayoutParams(dp(68),dp(95)));
                CoverImageLoader.show(cover,uri,true);
                LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,-2,1);ip.leftMargin=dp(12);card.addView(info,ip);
                TextView bookTitle=label(name,18,true);bookTitle.setMaxLines(2);info.addView(bookTitle);
                TextView detail=label("FB2 / EPUB · Озвучить целиком ›",13,false);detail.setTextColor(ACCENT);info.addView(detail);
                EdgeFullBookStore.Job job=EdgeFullBookStore.find(this,uri,name);
                if(job!=null){TextView status=label("Готово: "+EdgeFullBookStore.readyCount(job)+" из "+job.total+" · "+job.status,12,false);info.addView(status);}
                card.setOnClickListener(v->choose(uri,name));
            }
        }catch(Exception ignored){}
        if(count==0){TextView empty=label("В текстовой библиотеке пока нет EPUB или FB2. Добавьте книгу через кнопку + на главном экране.",16,false);
            empty.setPadding(dp(12),dp(25),dp(12),dp(25));items.addView(empty);}
    }
    private void choose(String uri,String title){
        String[] voiceNames={"Светлана · ru-RU","Дмитрий · ru-RU"};
        String[] voices={"ru-RU-SvetlanaNeural","ru-RU-DmitryNeural"};
        SharedPreferences prefs=getSharedPreferences("reader",0);
        String selected=prefs.getString("edge_voice",voices[0]);
        int chosen=selected.contains("Dmitry")?1:0;
        new SwipeDialogs.Builder(this).setTitle("Голос для всей книги")
            .setSingleChoiceItems(voiceNames,chosen,(dialog,which)->{
                dialog.dismiss();int length=EdgeSegmentSettings.get(prefs);
                String message="Книга: "+title+"\nГолос: "+voiceNames[which]
                    +"\nРазмер фрагмента: "+length+" символов.\n\nЗагрузка будет работать без воспроизведения. Готовые MP3 повторно не скачиваются, если голос и границы совпадают.";
                new SwipeDialogs.Builder(this).setTitle("Подготовить всю книгу?").setMessage(message)
                    .setNegativeButton("Отмена",null).setPositiveButton("Начать",(d,b)->{
                        Intent i=new Intent(this,EdgeFullBookService.class).setAction(EdgeFullBookService.ADD)
                            .putExtra(EdgeFullBookService.EXTRA_URI,uri)
                            .putExtra(EdgeFullBookService.EXTRA_TITLE,title)
                            .putExtra(EdgeFullBookService.EXTRA_VOICE,voices[which])
                            .putExtra(EdgeFullBookService.EXTRA_RATE,prefs.getString("edge_rate","+0%"))
                            .putExtra(EdgeFullBookService.EXTRA_CHARS,length);
                        try{if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                            startActivity(new Intent(this,EdgeChapterDownloadsActivity.class)
                                .putExtra("book_uri",uri).putExtra("book_title",title));
                            finish();
                        }catch(Exception e){Toast.makeText(this,"Не удалось запустить Edge: "+e.getMessage(),Toast.LENGTH_LONG).show();}
                    }).show();
            }).setNegativeButton("Отмена",null).show();
    }
    @Override public void onBackPressed(){BackNavigation.back(this);}
}
