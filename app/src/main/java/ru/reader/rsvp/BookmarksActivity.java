package ru.reader.rsvp;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

/** Book-local navigable bookmark list, with safe return to an exact text word. */
public final class BookmarksActivity extends NavigationActivity {
    private String uri,title;private LinearLayout rows;private boolean byDate=false;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){super.onCreate(b);uri=getIntent().getStringExtra("book_uri");title=getIntent().getStringExtra("book_title");if(uri==null)uri="";if(title==null)title="Книга";draw();}
    private TextView text(String label,int size){TextView t=new TextView(this);t.setText(label);t.setTextSize(size);t.setPadding(dp(12),dp(10),dp(12),dp(10));return t;}
    private void draw(){int bg=getSharedPreferences("reader",0).getInt("bg",0xfff3e7d5),fg=getSharedPreferences("reader",0).getInt("fg",0xff30221e);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);setContentView(root);SafeSystemBars.matchBackground(this,bg);
        TextView header=text("‹    Закладки · "+title,20);header.setTextColor(fg);header.setOnClickListener(v->finish());root.addView(header);
        TextView sort=text(byDate?"Сортировка: по дате ▾":"Сортировка: по позиции ▾",14);sort.setTextColor(fg);sort.setOnClickListener(v->{byDate=!byDate;draw();});root.addView(sort);
        ScrollView scroll=new ScrollView(this);rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        ArrayList<ReaderBookmarks.Item> items=ReaderBookmarks.list(this,uri);if(byDate)items.sort((a,b)->Long.compare(b.created,a.created));
        if(items.isEmpty()){TextView empty=text("В этой книге пока нет закладок.",15);empty.setTextColor(fg);rows.addView(empty);}
        for(ReaderBookmarks.Item item:items){LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(8),dp(12),dp(8));
            TextView name=text((item.title.isEmpty()?"Закладка":item.title)+" · "+item.chapter,16);name.setTextColor(fg);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);card.addView(name);
            TextView excerpt=text(item.excerpt.isEmpty()?"Слово № "+(item.word+1):item.excerpt,13);excerpt.setTextColor(fg);card.addView(excerpt);
            int words=Math.max(1,getSharedPreferences("reader",0).getInt("book_words:"+uri,1));
            int percent=Math.max(0,Math.min(100,item.word*100/words));
            TextView position=text("Слово № "+(item.word+1)+" · "+percent+"% книги",12);
            position.setTextColor(fg);card.addView(position);
            String date=item.created==0?"Из старой версии":DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(item.created));
            TextView go=text(date+"    ·    Перейти",12);go.setTextColor(fg);card.addView(go);
            card.setOnClickListener(v->{Intent result=new Intent();result.putExtra("word_index",item.word);setResult(RESULT_OK,result);finish();});
            card.setOnLongClickListener(v->{new android.app.AlertDialog.Builder(this).setItems(new String[]{"Переименовать","Удалить"},(d,which)->{
                if(which==1)new android.app.AlertDialog.Builder(this).setMessage("Удалить закладку?").setNegativeButton("Отмена",null).setPositiveButton("Удалить",(dd,w)->{ArrayList<ReaderBookmarks.Item> all=ReaderBookmarks.list(this,uri);all.removeIf(b->b.word==item.word);ReaderBookmarks.store(this,uri,all);draw();}).show();
                else{EditText edit=new EditText(this);edit.setText(item.title);new android.app.AlertDialog.Builder(this).setTitle("Название закладки").setView(edit).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",(dd,w)->{ArrayList<ReaderBookmarks.Item> all=ReaderBookmarks.list(this,uri);for(ReaderBookmarks.Item b:all)if(b.word==item.word)b.title=edit.getText().toString().trim();ReaderBookmarks.store(this,uri,all);draw();}).show();}
            }).show();return true;});rows.addView(card);View div=new View(this);div.setBackgroundColor(0x3330221e);rows.addView(div,new LinearLayout.LayoutParams(-1,dp(1)));}
    }
}
