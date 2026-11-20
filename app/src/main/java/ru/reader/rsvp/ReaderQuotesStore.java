package ru.reader.rsvp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A book-independent quote archive. Never edits book files or existing annotation databases. */
final class ReaderQuotesStore extends SQLiteOpenHelper {
    static final class Quote {
        long id,created,updated;
        String book,bookTitle,chapter,original,text;
        int fromChar,toChar,fromWord,toWord;
        boolean petEnabled;
        Quote(long id,String book,String bookTitle,String chapter,int fromChar,int toChar,
              int fromWord,int toWord,String original,String text,boolean petEnabled,long created,long updated){
            this.id=id;this.book=book;this.bookTitle=bookTitle;this.chapter=chapter;
            this.fromChar=fromChar;this.toChar=toChar;this.fromWord=fromWord;this.toWord=toWord;
            this.original=original;this.text=text;this.petEnabled=petEnabled;
            this.created=created;this.updated=updated;
        }
    }
    ReaderQuotesStore(Context context){super(context.getApplicationContext(),"reader_quotes_v184.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE quotes (id INTEGER PRIMARY KEY AUTOINCREMENT,"+
            "book TEXT NOT NULL,book_title TEXT NOT NULL DEFAULT '',chapter TEXT NOT NULL DEFAULT '',"+
            "from_char INTEGER NOT NULL,to_char INTEGER NOT NULL,"+
            "from_word INTEGER NOT NULL,to_word INTEGER NOT NULL,"+
            "original TEXT NOT NULL,text TEXT NOT NULL,pet_enabled INTEGER NOT NULL DEFAULT 1,"+
            "created_ms INTEGER NOT NULL,updated_ms INTEGER NOT NULL,"+
            "UNIQUE(book,from_char,to_char))");
        db.execSQL("CREATE INDEX quotes_by_book ON quotes(book,from_char)");
        db.execSQL("CREATE INDEX quotes_for_pet ON quotes(pet_enabled,id)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion!=newVersion)throw new IllegalStateException("Unknown quotes migration "+oldVersion);
    }
    private static final String[] FIELDS={"id","book","book_title","chapter","from_char","to_char",
        "from_word","to_word","original","text","pet_enabled","created_ms","updated_ms"};
    private Quote from(Cursor c){return new Quote(c.getLong(0),c.getString(1),c.getString(2),c.getString(3),
        c.getInt(4),c.getInt(5),c.getInt(6),c.getInt(7),c.getString(8),c.getString(9),
        c.getInt(10)!=0,c.getLong(11),c.getLong(12));}
    synchronized List<Quote> forBook(String book){
        ArrayList<Quote> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("quotes",FIELDS,"book=?",new String[]{book},
            null,null,"from_char,id")){while(c.moveToNext())result.add(from(c));}
        return result;
    }
    synchronized Quote get(long id){
        try(Cursor c=getReadableDatabase().query("quotes",FIELDS,"id=?",new String[]{Long.toString(id)},null,null,null)){
            return c.moveToFirst()?from(c):null;
        }
    }
    /** Adding the same marked range twice does not create another entry. */
    synchronized long add(String book,String title,String chapter,int fromChar,int toChar,int fromWord,int toWord,String original){
        if(book==null||book.isEmpty()||original==null||original.trim().isEmpty()||fromChar<0||toChar<=fromChar)
            throw new IllegalArgumentException("Empty or invalid quote");
        try(Cursor c=getReadableDatabase().query("quotes",new String[]{"id"},
                "book=? AND from_char=? AND to_char=?",
                new String[]{book,Integer.toString(fromChar),Integer.toString(toChar)},null,null,null)){
            if(c.moveToFirst())return c.getLong(0);
        }
        long now=System.currentTimeMillis();ContentValues v=new ContentValues();
        v.put("book",book);v.put("book_title",title==null?"":title);
        v.put("chapter",chapter==null?"":chapter);v.put("from_char",fromChar);v.put("to_char",toChar);
        v.put("from_word",fromWord);v.put("to_word",toWord);v.put("original",original);v.put("text",original);
        v.put("pet_enabled",1);v.put("created_ms",now);v.put("updated_ms",now);
        long inserted=getWritableDatabase().insertWithOnConflict("quotes",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        if(inserted>=0)return inserted;
        // Another store instance may have saved this same range concurrently.
        try(Cursor c=getReadableDatabase().query("quotes",new String[]{"id"},
            "book=? AND from_char=? AND to_char=?",
            new String[]{book,Integer.toString(fromChar),Integer.toString(toChar)},null,null,null)){
            if(c.moveToFirst())return c.getLong(0);
        }
        throw new IllegalStateException("Failed to save quote");
    }
    synchronized void edit(long id,String text){
        if(text==null||text.trim().isEmpty())throw new IllegalArgumentException("Цитата не может быть пустой");
        ContentValues values=new ContentValues();values.put("text",text);values.put("updated_ms",System.currentTimeMillis());
        if(getWritableDatabase().update("quotes",values,"id=?",new String[]{Long.toString(id)})!=1)
            throw new IllegalStateException("Цитата не найдена");
    }
    synchronized void togglePet(long id,boolean show){
        ContentValues values=new ContentValues();values.put("pet_enabled",show?1:0);
        getWritableDatabase().update("quotes",values,"id=?",new String[]{Long.toString(id)});
    }
    synchronized void delete(long id){getWritableDatabase().delete("quotes","id=?",new String[]{Long.toString(id)});}
    synchronized boolean hasPetQuotes(){
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM quotes WHERE pet_enabled=1 AND LENGTH(TRIM(text))>0 LIMIT 1",null)){
            return c.moveToFirst();
        }
    }
    /** Reservoir sampling over distinct actual texts: duplicates do not increase their chance. */
    synchronized Quote randomForPet(Random random,long previousId){
        ArrayList<Long> ids=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery(
            "SELECT MIN(id) FROM quotes WHERE pet_enabled=1 AND LENGTH(TRIM(text))>0 GROUP BY LOWER(TRIM(text))",null)){
            while(c.moveToNext())ids.add(c.getLong(0));
        }
        if(ids.isEmpty())return null;
        if(ids.size()>1)ids.remove(previousId);
        return get(ids.get(random.nextInt(ids.size())));
    }
}
