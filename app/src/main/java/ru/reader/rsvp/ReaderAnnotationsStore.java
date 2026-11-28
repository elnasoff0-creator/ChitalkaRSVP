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
        final int fromChar,toChar; // exact UTF-16 text offsets, -1 for legacy records
        final String excerpt,context,note;
        final int color;
        Annotation(long id,String book,int from,int to,String excerpt,String context,int color,String note){
            this(id,book,from,to,-1,-1,excerpt,context,color,note);
        }
        Annotation(long id,String book,int from,int to,int startChar,int endChar,
                   String excerpt,String context,int color,String note){
            this.id=id;this.book=book;this.fromWord=from;this.toWord=to;
            this.fromChar=startChar;this.toChar=endChar;
            this.excerpt=excerpt;this.context=context;this.color=color;this.note=note;
        }
        boolean intersects(int from,int to){return fromWord<to&&toWord>from;}
        boolean contains(int word){return fromWord<=word&&word<toWord;}
    }
    ReaderAnnotationsStore(Context context){super(context.getApplicationContext(),"reader_annotations_v161.db",null,2);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE annotations (id INTEGER PRIMARY KEY AUTOINCREMENT, book TEXT NOT NULL,"+
            "from_word INTEGER NOT NULL,to_word INTEGER NOT NULL,excerpt TEXT NOT NULL,"+
            "context TEXT NOT NULL DEFAULT '',color INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '',"+
            "updated_ms INTEGER NOT NULL,from_char INTEGER NOT NULL DEFAULT -1,to_char INTEGER NOT NULL DEFAULT -1)");
        db.execSQL("CREATE INDEX idx_annotations_book ON annotations(book,from_word,to_word)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            db.execSQL("ALTER TABLE annotations ADD COLUMN from_char INTEGER NOT NULL DEFAULT -1");
            db.execSQL("ALTER TABLE annotations ADD COLUMN to_char INTEGER NOT NULL DEFAULT -1");
        }
    }
    synchronized List<Annotation> load(String book){
        ArrayList<Annotation> result=new ArrayList<>();
        try(Cursor cursor=getReadableDatabase().query("annotations",
            new String[]{"id","book","from_word","to_word","excerpt","context","color","note","from_char","to_char"},
            "book=?",new String[]{book},null,null,"from_word,id")){
            while(cursor.moveToNext())result.add(new Annotation(cursor.getLong(0),cursor.getString(1),
                cursor.getInt(2),cursor.getInt(3),cursor.getInt(8),cursor.getInt(9),
                cursor.getString(4),cursor.getString(5),
                cursor.getInt(6),cursor.getString(7)));
        }
        return result;
    }
    synchronized long save(Annotation item){
        if(item.book.isEmpty()||item.fromWord<0||item.toWord<=item.fromWord)throw new IllegalArgumentException("Invalid annotation range");
        ContentValues v=new ContentValues();v.put("book",item.book);v.put("from_word",item.fromWord);
        v.put("to_word",item.toWord);v.put("excerpt",item.excerpt);v.put("context",item.context);
        v.put("color",item.color);v.put("note",item.note);
        v.put("from_char",item.fromChar);v.put("to_char",item.toChar);
        v.put("updated_ms",System.currentTimeMillis());
        long id;
        if(item.id>0){int changed=getWritableDatabase().update("annotations",v,"id=? AND book=?",
            new String[]{Long.toString(item.id),item.book});
            if(changed==0)throw new IllegalStateException("Annotation not found");id=item.id;
        }else{id=getWritableDatabase().insertOrThrow("annotations",null,v);}
        return id;
    }
    /** Preserve annotation content and IDs. SQLite transaction makes a retry idempotent. */
    synchronized void reanchorRbi5(String book,ReaderTextReanchor map,ReadingWordMap words){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            db.execSQL("CREATE TABLE IF NOT EXISTS reader_text_migrations (book TEXT PRIMARY KEY NOT NULL)");
            try(Cursor c=db.query("reader_text_migrations",new String[]{"book"},"book=?",new String[]{book},null,null,null)){
                if(c.moveToNext()){db.setTransactionSuccessful();return;}
            }
            for(Annotation a:load(book)){
                int start=map.word(a.fromWord),last=Math.max(start,map.word(a.toWord-1));
                int from=map.startChar(a.fromChar,a.fromWord,start);
                int to=map.endChar(a.toChar,a.toWord-1,last);
                if(from>=0&&to<=from){from=words.starts[start];to=words.ends[last];}
                ContentValues v=new ContentValues();v.put("from_word",start);v.put("to_word",last+1);
                v.put("from_char",from);v.put("to_char",to);
                db.update("annotations",v,"id=? AND book=?",new String[]{Long.toString(a.id),book});
            }
            ContentValues done=new ContentValues();done.put("book",book);db.insertOrThrow("reader_text_migrations",null,done);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    synchronized void delete(long id,String book){
        if(id>0)getWritableDatabase().delete("annotations","id=? AND book=?",new String[]{Long.toString(id),book});
    }
}
