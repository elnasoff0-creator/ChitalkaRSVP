package ru.reader.rsvp;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;

/** Quote manager for the current book; consistent with notes/bookmarks, with per-quote actions. */
public final class QuotesActivity extends NavigationActivity {
    private String book="",title="";
    private int background,foreground,panel;
    private boolean byDate;
    private RecyclerView recycler;
    private QuotesAdapter adapter;
    private TextView summary,sorting;
    private final ArrayList<ReaderQuotesStore.Quote> quotes=new ArrayList<>();
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int color,int radius){GradientDrawable drawable=new GradientDrawable();
        drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private TextView label(String content,int sp,boolean bold){TextView view=new TextView(this);
        view.setText(content);view.setTextColor(foreground);view.setTextSize(sp);
        if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        String raw=getIntent().getStringExtra("book_uri");book=BookUriMigration.legacy(this,raw==null?"":raw);
        title=getIntent().getStringExtra("book_title");if(title==null||title.trim().isEmpty())title="Книга";
        SharedPreferences prefs=getSharedPreferences("reader",0);
        background=prefs.getInt("bg",0xfff3e7d5);foreground=prefs.getInt("fg",0xff30221e);
        panel=UiColorScheme.neutral(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);root.setPadding(dp(12),dp(8),dp(12),dp(8));
        setContentView(root);SafeSystemBars.matchBackground(this,background);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading,new LinearLayout.LayoutParams(-1,dp(54)));
        ImageView drawer=new ImageView(this);drawer.setImageResource(R.drawable.ic_menu_lines);drawer.setColorFilter(foreground);
        drawer.setPadding(dp(12),dp(12),dp(12),dp(12));drawer.setBackground(shape(panel,14));
        heading.addView(drawer,new LinearLayout.LayoutParams(dp(46),dp(46)));
        drawer.setContentDescription("Открыть боковое меню");drawer.setOnClickListener(v->UniversalDrawer.show(this));
        TextView titleView=label("Все цитаты",23,true);
        LinearLayout.LayoutParams headingParams=new LinearLayout.LayoutParams(0,-2,1);headingParams.leftMargin=dp(12);
        heading.addView(titleView,headingParams);
        TextView close=label("×",26,false);close.setGravity(Gravity.CENTER);close.setContentDescription("Назад к чтению");
        heading.addView(close,new LinearLayout.LayoutParams(dp(42),dp(44)));close.setOnClickListener(v->finish());
        LinearLayout sub=new LinearLayout(this);sub.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(sub,new LinearLayout.LayoutParams(-1,dp(46)));
        TextView bookName=label(title,13,false);bookName.setSingleLine(true);bookName.setEllipsize(TextUtils.TruncateAt.END);
        sub.addView(bookName,new LinearLayout.LayoutParams(0,-2,1));
        sorting=label("По порядку ▾",12,false);sorting.setPadding(dp(9),dp(11),dp(9),dp(11));
        sub.addView(sorting);sorting.setOnClickListener(v->{byDate=!byDate;refresh();});
        recycler=new RecyclerView(this);recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(null);adapter=new QuotesAdapter();recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        summary=label("",12,false);summary.setGravity(Gravity.CENTER);
        summary.setPadding(dp(12),dp(9),dp(12),dp(9));root.addView(summary);
        refresh();
    }
    private void refresh(){
        quotes.clear();
        try(ReaderQuotesStore store=new ReaderQuotesStore(this)){quotes.addAll(store.forBook(book));}
        catch(Exception error){Toast.makeText(this,"Не удалось открыть цитаты: "+error.getMessage(),Toast.LENGTH_LONG).show();}
        if(byDate)quotes.sort((a,b)->Long.compare(b.created,a.created));
        else quotes.sort(Comparator.comparingInt(q->q.fromChar));
        sorting.setText(byDate?"По дате ▾":"По порядку ▾");
        summary.setText(quotes.isEmpty()?"Здесь появятся цитаты, сохранённые из выделенного текста":"Цитат: "+quotes.size());
        adapter.notifyDataSetChanged();
    }
    private void goTo(ReaderQuotesStore.Quote quote){
        Intent result=new Intent();result.putExtra("word_index",quote.fromWord);
        result.putExtra("quote_from_char",quote.fromChar);result.putExtra("quote_to_char",quote.toChar);
        setResult(RESULT_OK,result);finish();
    }
    private void edit(ReaderQuotesStore.Quote quote){
        EditText editor=new EditText(this);editor.setText(quote.text);editor.setMinLines(3);editor.setMaxLines(12);
        editor.setSelection(editor.length());editor.setTextColor(foreground);
        LinearLayout wrap=new LinearLayout(this);wrap.setPadding(dp(18),0,dp(18),0);wrap.addView(editor);
        new SwipeDialogs.Builder(this).setTitle("Изменить цитату").setView(wrap)
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Сохранить",(dialog,which)->{
                try(ReaderQuotesStore store=new ReaderQuotesStore(this)){
                    store.edit(quote.id,editor.getText().toString());refresh();
                }catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}
            }).show();
    }
    private void delete(ReaderQuotesStore.Quote quote){
        new SwipeDialogs.Builder(this).setTitle("Удалить цитату?")
            .setMessage("Цитата будет удалена из цитатника и памяти питомца.")
            .setNegativeButton("Отмена",null)
            .setPositiveButton("Удалить",(dialog,which)->{
                try(ReaderQuotesStore store=new ReaderQuotesStore(this)){store.delete(quote.id);refresh();}
                catch(Exception error){Toast.makeText(this,"Не удалось удалить цитату",Toast.LENGTH_LONG).show();}
            }).show();
    }
    private void menu(View anchor,ReaderQuotesStore.Quote quote){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add(0,1,0,"Изменить");
        menu.getMenu().add(0,2,1,"Удалить");
        menu.getMenu().add(0,3,2,quote.petEnabled?"Не показывать питомцем":"Показывать питомцем");
        menu.setOnMenuItemClickListener(item->{
            if(item.getItemId()==1)edit(quote);
            else if(item.getItemId()==2)delete(quote);
            else if(item.getItemId()==3){
                try(ReaderQuotesStore store=new ReaderQuotesStore(this)){
                    store.togglePet(quote.id,!quote.petEnabled);refresh();
                }catch(Exception error){Toast.makeText(this,"Не удалось обновить цитату",Toast.LENGTH_LONG).show();}
            }
            return true;
        });menu.show();
    }
    private final class QuotesAdapter extends RecyclerView.Adapter<QuotesAdapter.Holder>{
        final class Holder extends RecyclerView.ViewHolder{
            final LinearLayout card;
            Holder(LinearLayout card){super(card);this.card=card;}
        }
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int viewType){
            LinearLayout item=new LinearLayout(QuotesActivity.this);item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(14),dp(12),dp(14),dp(12));
            RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(5));
            item.setLayoutParams(lp);item.setBackground(shape(panel,17));return new Holder(item);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            ReaderQuotesStore.Quote quote=quotes.get(position);LinearLayout card=holder.card;
            card.removeAllViews();
            LinearLayout top=new LinearLayout(QuotesActivity.this);top.setGravity(Gravity.TOP);
            card.addView(top,new LinearLayout.LayoutParams(-1,-2));
            TextView body=label("«"+quote.text+"»",15,false);body.setMaxLines(7);
            body.setEllipsize(TextUtils.TruncateAt.END);
            body.setOnClickListener(v->{
                if(body.getMaxLines()==7)body.setMaxLines(Integer.MAX_VALUE);
                else goTo(quote);
            });
            top.addView(body,new LinearLayout.LayoutParams(0,-2,1));
            TextView actions=label("⋮",25,true);actions.setGravity(Gravity.CENTER);
            actions.setContentDescription("Действия с цитатой");
            top.addView(actions,new LinearLayout.LayoutParams(dp(46),dp(48)));
            actions.setOnClickListener(v->menu(actions,quote));
            TextView chapter=label(quote.chapter==null||quote.chapter.isEmpty()?"Глава не определена":quote.chapter,12,false);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(10);card.addView(chapter,cp);
            String date=DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(quote.created));
            TextView meta=label("Слово № "+(quote.fromWord+1)+"  ·  "+date+(quote.petEnabled?"":"  ·  Скрыто от питомца"),11,false);
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.topMargin=dp(4);card.addView(meta,mp);
            card.setOnClickListener(v->goTo(quote));
        }
        @Override public int getItemCount(){return quotes.size();}
    }
}
