package ru.reader.rsvp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/** User annotations are separate from EPUB/FB2/HTML and never rewrite book bytes. */
final class ReaderAnnotationsStore extends SQLiteOpenHelper {
    static final class Annotation {
        long id;
        final String book;
        final int fromWord,toWord; // inclusive start, exclusive end
        final String excerpt,context,note;
        final int color;
        Annotation(long id,String book,int from,int to,String excerpt,String context,int color,String note){
            this.id=id;this.book=book;this.fromWord=from;this.toWord=to;
            this.excerpt=excerpt;this.context=context;this.color=color;this.note=note;
        }
        boolean intersects(int from,int to){return fromWord<to&&toWord>from;}
        boolean contains(int word){return fromWord<=word&&word<toWord;}
    }
    ReaderAnnotationsStore(Context context){super(context.getApplicationContext(),"reader_annotations_v161.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE annotations (id INTEGER PRIMARY KEY AUTOINCREMENT, book TEXT NOT NULL,"+
            "from_word INTEGER NOT NULL,to_word INTEGER NOT NULL,excerpt TEXT NOT NULL,"+
            "context TEXT NOT NULL DEFAULT '',color INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '',"+
            "updated_ms INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_annotations_book ON annotations(book,from_word,to_word)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion>newVersion)throw new IllegalArgumentException("Unsupported annotation database");
    }
    synchronized List<Annotation> load(String book){
        ArrayList<Annotation> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("annotations",
            new String[]{"id","book","from_word","to_word","excerpt","context","color","note"},
            "book=?",new String[]{book},null,null,"from_word,id")){
            while(cursor.moveToNext())result.add(new Annotation(cursor.getLong(0),cursor.getString(1),
                cursor.getInt(2),cursor.getInt(3),cursor.getString(4),cursor.getString(5),
                cursor.getInt(6),cursor.getString(7)));
        }
        return result;
    }
    synchronized long save(Annotation item){
        if(item.book.isEmpty()||item.fromWord<0||item.toWord<=item.fromWord)throw new IllegalArgumentException("Invalid annotation range");
        ContentValues v=new ContentValues();v.put("book",item.book);v.put("from_word",item.fromWord);
        v.put("to_word",item.toWord);v.put("excerpt",item.excerpt);v.put("context",item.context);
        v.put("color",item.color);v.put("note",item.note);v.put("updated_ms",System.currentTimeMillis());
        long id;
        if(item.id>0){int changed=getWritableDatabase().update("annotations",v,"id=? AND book=?",
            new String[]{Long.toString(item.id),item.book});
            if(changed==0)throw new IllegalStateException("Annotation not found");id=item.id;
        }else{id=getWritableDatabase().insertOrThrow("annotations",null,v);}
        return id;
    }
    synchronized void delete(long id,String book){
        if(id>0)getWritableDatabase().delete("annotations","id=? AND book=?",new String[]{Long.toString(id),book});
    }
}
