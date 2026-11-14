package ru.reader.rsvp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact shared bookmark/note manager. No book parsing, no reading-session reset. */
public final class BookmarksActivity extends NavigationActivity {
    private String bookUri="",title="";
    private boolean notesMode,byDate;
    private int background,foreground,panel;
    private LinearLayout root;
    private RecyclerView list;
    private MarksAdapter adapter;
    private TextView countLabel,sortLabel,undoAction;
    private final ArrayList<Mark> marks=new ArrayList<>();
    private String revealed="";
    private Runnable undo;

    private static final class Mark {
        String id,main,quote,chapter;
        long noteId,created;
        int word,color;
        ReaderBookmarks.Item bookmark;
        ReaderAnnotationsStore.Annotation annotation;
    }
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView text(String str,int size,boolean bold){TextView v=new TextView(this);v.setText(str);v.setTextSize(size);v.setTextColor(foreground);
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    @Override public void onCreate(Bundle state){super.onCreate(state);
        bookUri=getIntent().getStringExtra("book_uri");title=getIntent().getStringExtra("book_title");
        if(bookUri==null)bookUri="";if(title==null)title="Книга";
        notesMode=getIntent().getBooleanExtra("show_notes",false);
        SharedPreferences p=getSharedPreferences("reader",0);
        background=p.getInt("bg",0xfff3e7d5);foreground=p.getInt("fg",0xff30221e);
        panel=UiColorScheme.neutral(this);
        draw();
    }
    private void draw(){
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);
        root.setPadding(dp(12),dp(23),dp(12),dp(8));setContentView(root);SafeSystemBars.matchBackground(this,background);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(heading,new LinearLayout.LayoutParams(-1,dp(52)));
        ImageView drawer=new ImageView(this);drawer.setImageResource(R.drawable.ic_menu_lines);drawer.setColorFilter(foreground);
        drawer.setPadding(dp(12),dp(12),dp(12),dp(12));drawer.setBackground(shape(panel,14));
        heading.addView(drawer,new LinearLayout.LayoutParams(dp(46),dp(46)));
        drawer.setContentDescription("Открыть боковое меню");drawer.setOnClickListener(v->UniversalDrawer.show(this));
        TextView headingText=text(notesMode?"Все заметки":"Все закладки",23,true);
        LinearLayout.LayoutParams h=new LinearLayout.LayoutParams(0,-2,1);h.leftMargin=dp(12);heading.addView(headingText,h);
        TextView back=text("×",26,false);back.setGravity(Gravity.CENTER);heading.addView(back,new LinearLayout.LayoutParams(dp(40),dp(42)));
        back.setContentDescription("Назад к чтению");back.setOnClickListener(v->finish());
        LinearLayout sub=new LinearLayout(this);sub.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams spl=new LinearLayout.LayoutParams(-1,dp(44));spl.topMargin=dp(3);root.addView(sub,spl);
        TextView name=text(title,13,false);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        sortLabel=text("Сортировка ▾",12,false);sortLabel.setPadding(dp(7),dp(8),dp(7),dp(8));
        sub.addView(sortLabel);sortLabel.setOnClickListener(v->{byDate=!byDate;refresh();});
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));
        adapter=new MarksAdapter();list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,ItemTouchHelper.LEFT){
            @Override public boolean onMove(RecyclerView rv,RecyclerView.ViewHolder from,RecyclerView.ViewHolder to){return false;}
            @Override public boolean isLongPressDragEnabled(){return false;}
            @Override public float getSwipeThreshold(RecyclerView.ViewHolder holder){return .22f;}
            @Override public void onSwiped(RecyclerView.ViewHolder holder,int direction){
                int position=holder.getBindingAdapterPosition();
                revealed=position>=0&&position<marks.size()?marks.get(position).id:"";
                adapter.notifyDataSetChanged();
            }
        }).attachToRecyclerView(list);
        countLabel=text("",12,false);countLabel.setGravity(Gravity.CENTER);
        countLabel.setPadding(dp(10),dp(9),dp(10),dp(9));root.addView(countLabel);
        undoAction=text("",13,true);undoAction.setGravity(Gravity.CENTER);undoAction.setPadding(dp(9),dp(10),dp(9),dp(10));
        undoAction.setBackground(shape(panel,12));undoAction.setVisibility(View.GONE);
        root.addView(undoAction);undoAction.setOnClickListener(v->{Runnable task=undo;undo=null;undoAction.setVisibility(View.GONE);if(task!=null)task.run();});
        refresh();
    }
    private void refresh(){
        marks.clear();revealed="";
        sortLabel.setText(byDate?"По дате ▾":"По порядку ▾");
        if(notesMode){
            try(ReaderAnnotationsStore store=new ReaderAnnotationsStore(this)){
                List<ReaderAnnotationsStore.Annotation> stored=store.load(BookUriMigration.legacy(this,bookUri));
                for(ReaderAnnotationsStore.Annotation item:stored){
                    if(item.note==null||item.note.trim().isEmpty())continue;
                    Mark m=new Mark();m.id="note:"+item.id;m.noteId=item.id;m.word=item.fromWord;m.color=item.color;
                    m.main=item.note;m.quote=item.excerpt;m.annotation=item;
                    m.chapter=ReaderChapterLabels.atWord(this,bookUri,item.fromWord);
                    marks.add(m);
                }
            }catch(Exception ex){Toast.makeText(this,"Не удалось прочитать заметки: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        }else{
            for(ReaderBookmarks.Item item:ReaderBookmarks.list(this,bookUri)){
                Mark m=new Mark();m.id="bookmark:"+item.word;m.word=item.word;m.created=item.created;
                m.main=item.excerpt==null||item.excerpt.isEmpty()?item.title:item.excerpt;
                m.chapter=ReaderChapterLabels.atWord(this,bookUri,item.word);
                if(m.chapter.isEmpty())m.chapter=item.chapter;
                m.bookmark=item;marks.add(m);
            }
        }
        if(byDate)marks.sort((a,b)->Long.compare(b.created,a.created));
        else marks.sort(Comparator.comparingInt(m->m.word));
        if(countLabel!=null)countLabel.setText((notesMode?"Заметок: ":"Закладок: ")+marks.size());
        if(adapter!=null)adapter.notifyDataSetChanged();
    }
    private void go(Mark mark){
        Intent result=new Intent();result.putExtra("word_index",mark.word);
        if(notesMode)result.putExtra("annotation_id",mark.noteId);
        setResult(RESULT_OK,result);finish();
    }
    private void delete(Mark item){
        if(notesMode){
            ReaderAnnotationsStore.Annotation old=item.annotation;
            try(ReaderAnnotationsStore store=new ReaderAnnotationsStore(this)){
                if(old.color==0)store.delete(old.id,old.book);
                else store.save(new ReaderAnnotationsStore.Annotation(old.id,old.book,old.fromWord,old.toWord,
                    old.excerpt,old.context,old.color,""));
            }catch(Exception ex){Toast.makeText(this,"Ошибка удаления заметки",Toast.LENGTH_LONG).show();return;}
            undo=()->{try(ReaderAnnotationsStore store=new ReaderAnnotationsStore(this)){
                // Restore both when the highlight remains and when the whole row was deleted.
                long id=old.color==0?0:old.id;
                store.save(new ReaderAnnotationsStore.Annotation(id,old.book,old.fromWord,old.toWord,
                    old.excerpt,old.context,old.color,old.note));
                refresh();
            }catch(Exception ignored){Toast.makeText(this,"Не удалось восстановить заметку",Toast.LENGTH_LONG).show();}};
        }else{
            ReaderBookmarks.Item old=item.bookmark;
            ArrayList<ReaderBookmarks.Item> all=ReaderBookmarks.list(this,bookUri);
            all.removeIf(bookmark->bookmark.word==old.word);ReaderBookmarks.store(this,bookUri,all);
            undo=()->{ReaderBookmarks.add(this,bookUri,old);refresh();};
        }
        refresh();undoAction.setText("Удалено · Отменить");undoAction.setVisibility(View.VISIBLE);
        final Runnable expected=undo;
        undoAction.postDelayed(()->{if(undo==expected){undo=null;undoAction.setVisibility(View.GONE);}},7000);
    }
    private final class MarksAdapter extends RecyclerView.Adapter<MarksAdapter.Holder>{
        class Holder extends RecyclerView.ViewHolder{
            final FrameLayout root;final LinearLayout foreground,actions;
            Holder(FrameLayout f,LinearLayout front,LinearLayout rear){super(f);root=f;foreground=front;actions=rear;}
        }
        @Override public Holder onCreateViewHolder(android.view.ViewGroup parent,int viewType){
            FrameLayout frame=new FrameLayout(BookmarksActivity.this);
            frame.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));
            LinearLayout actions=new LinearLayout(BookmarksActivity.this);actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams a=new FrameLayout.LayoutParams(dp(152),-1,Gravity.RIGHT);
            frame.addView(actions,a);
            LinearLayout front=new LinearLayout(BookmarksActivity.this);front.setOrientation(LinearLayout.VERTICAL);
            front.setPadding(dp(14),dp(11),dp(14),dp(10));front.setBackground(shape(panel,17));
            FrameLayout.LayoutParams c=new FrameLayout.LayoutParams(-1,-2);c.setMargins(0,dp(4),0,dp(4));
            frame.addView(front,c);
            return new Holder(frame,front,actions);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            Mark item=marks.get(position);holder.foreground.removeAllViews();holder.actions.removeAllViews();
            TextView main=text(item.main==null||item.main.isEmpty()?"Закладка":item.main,16,true);
            main.setMaxLines(3);main.setEllipsize(android.text.TextUtils.TruncateAt.END);
            holder.foreground.addView(main);
            if(notesMode&&item.quote!=null&&!item.quote.isEmpty()){
                TextView quote=text(item.quote,12,false);quote.setMaxLines(2);quote.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.topMargin=dp(4);
                holder.foreground.addView(quote,q);
            }
            View divider=new View(BookmarksActivity.this);divider.setBackgroundColor((foreground&0x00ffffff)|0x24000000);
            LinearLayout.LayoutParams d=new LinearLayout.LayoutParams(-1,dp(1));d.topMargin=dp(8);d.bottomMargin=dp(6);holder.foreground.addView(divider,d);
            TextView chapter=text(item.chapter==null||item.chapter.isEmpty()?"Глава не определена":item.chapter,12,false);
            chapter.setMaxLines(2);chapter.setEllipsize(android.text.TextUtils.TruncateAt.END);holder.foreground.addView(chapter);
            TextView word=text("Слово № "+(item.word+1),11,false);word.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams w=new LinearLayout.LayoutParams(-1,-2);w.topMargin=dp(2);holder.foreground.addView(word,w);
            holder.foreground.setOnClickListener(v->{if(!revealed.isEmpty()){revealed="";notifyDataSetChanged();}else go(item);});
            TextView delete=actionButton("Удалить",0xffbb4741);TextView cancel=actionButton("Отмена",0xff75665d);
            holder.actions.addView(delete,new LinearLayout.LayoutParams(dp(76),-1));
            holder.actions.addView(cancel,new LinearLayout.LayoutParams(dp(76),-1));
            delete.setOnClickListener(v->delete(item));
            cancel.setOnClickListener(v->{revealed="";notifyDataSetChanged();});
            holder.foreground.setTranslationX(item.id.equals(revealed)?-dp(152):0);
            holder.actions.setVisibility(item.id.equals(revealed)?View.VISIBLE:View.INVISIBLE);
        }
        @Override public int getItemCount(){return marks.size();}
    }
    private TextView actionButton(String value,int color){TextView text=text(value,12,true);text.setTextColor(0xffffffff);
        text.setGravity(Gravity.CENTER);text.setBackground(shape(color,12));return text;}
}
