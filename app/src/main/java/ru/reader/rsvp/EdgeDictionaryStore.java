package ru.reader.rsvp;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Persistent Edge TTS dictionary. Android's built-in SQLite; no extra APK dependency.
 * Legacy SharedPreferences data stays untouched as a recovery copy after migration. */
final class EdgeDictionaryStore extends SQLiteOpenHelper {
    static final int REPLACE=0, IGNORE=1;
    static final int MAX_LITERAL_REPLACE=10000, MAX_REGEX=1000, MAX_LITERAL_IGNORE=5000;
    static final int MAX_PATTERN=400, MAX_REPLACEMENT=1000, MAX_CONTEXT=240;
    private static final String DB="edge_dictionary_v132.db";
    private static final String MIGRATED="edge_dictionary_sqlite_migrated_v132";
    private static final String REVISION="edge_dictionary_revision_v132";
    private final Context app;
    private static EdgeDictionaryStore shared;
    static synchronized EdgeDictionaryStore get(Context context){
        if(shared==null)shared=new EdgeDictionaryStore(context);
        return shared;
    }
    private static EdgeSpeechTextTransform.Options cached;
    private static int cachedRevision=-1;

    static final class Rule {
        final long id;
        final int kind;
        final String pattern,context,replacement;
        final boolean regex;
        Rule(long id,int kind,String pattern,String context,String replacement,boolean regex){
            this.id=id;this.kind=kind;this.pattern=pattern;this.context=context;
            this.replacement=replacement;this.regex=regex;
        }
    }

    EdgeDictionaryStore(Context context){super(context.getApplicationContext(),DB,null,1);
        app=context.getApplicationContext();}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE rules (id INTEGER PRIMARY KEY AUTOINCREMENT, kind INTEGER NOT NULL,"+
            "pattern TEXT NOT NULL, context TEXT NOT NULL DEFAULT '', replacement TEXT NOT NULL DEFAULT '',"+
            "is_regex INTEGER NOT NULL DEFAULT 0, ordinal INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_edge_kind_order ON rules(kind,ordinal,id)");
        db.execSQL("CREATE INDEX idx_edge_find ON rules(kind,pattern COLLATE NOCASE)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        // Future schema upgrades must preserve the dictionary; never drop this table.
        if(oldVersion>newVersion)throw new IllegalStateException("Unknown dictionary schema");
    }
    private SharedPreferences prefs(){return app.getSharedPreferences("reader",Context.MODE_PRIVATE);}
    private static ContentValues values(int kind,String pattern,String context,String replacement,boolean regex,int order){
        ContentValues v=new ContentValues();
        v.put("kind",kind);v.put("pattern",pattern);v.put("context",context);
        v.put("replacement",replacement);v.put("is_regex",regex?1:0);v.put("ordinal",order);
        return v;
    }
    private static void insertLegacy(SQLiteDatabase db,int kind,String pattern,String context,String replacement,int order){
        if(pattern.isEmpty())return;
        db.insertOrThrow("rules",null,values(kind,pattern,context,replacement,false,order));
    }
    synchronized void ensureMigrated(){
        SharedPreferences p=prefs();
        if(p.getBoolean(MIGRATED,false))return;
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            int order=0;
            // Do not re-import on an interrupted migration; transaction is all-or-nothing.
            db.delete("rules",null,null);
            String old=p.getString(EdgeSpeechTextTransform.DICTIONARY,"");
            boolean hadOldSample=false;
            for(String line:old.split("\\r?\\n")){
                int eq=line.indexOf('=');if(eq<=0)continue;
                String left=line.substring(0,eq).trim(),spoken=line.substring(eq+1).trim();
                int bar=left.indexOf('|');
                String word=(bar<0?left:left.substring(0,bar)).trim();
                String context=bar<0?"":left.substring(bar+1).trim();
                if("***".equals(word)&&context.isEmpty()&&".".equals(spoken)){
                    hadOldSample=true;continue;
                }
                if(word.isEmpty()||spoken.isEmpty())continue;
                insertLegacy(db,REPLACE,word,context,spoken,order++);
            }
            String ignored=p.getString(EdgeSpeechTextTransform.IGNORED,"");
            boolean sampleImported=false;
            for(String line:ignored.split("\\r?\\n")){
                String pattern=line.trim();
                if(pattern.isEmpty())continue;
                if("***".equals(pattern))sampleImported=true;
                insertLegacy(db,IGNORE,pattern,"","",order++);
            }
            if(hadOldSample&&!sampleImported)insertLegacy(db,IGNORE,"***","","",order++);
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
        // Commit the marker only after database commit, preventing silent loss on errors.
        if(!p.edit().putBoolean(MIGRATED,true).putInt(REVISION,p.getInt(REVISION,0)+1).commit())
            throw new IllegalStateException("Failed to record dictionary migration");
        invalidate();
    }
    private static void invalidate(){synchronized(EdgeDictionaryStore.class){cached=null;cachedRevision=-1;}}
    private void changed(){
        SharedPreferences p=prefs();
        if(!p.edit().putInt(REVISION,p.getInt(REVISION,0)+1).commit())
            throw new IllegalStateException("Failed to update dictionary revision");
        invalidate();
    }
    int count(int kind){ensureMigrated();try(Cursor c=getReadableDatabase().rawQuery(
        "SELECT count(*) FROM rules WHERE kind=?",new String[]{String.valueOf(kind)})){
        return c.moveToFirst()?c.getInt(0):0;
    }}
    List<Rule> query(int kind,String search,int limit){
        ensureMigrated();ArrayList<Rule> out=new ArrayList<>();
        String query=search==null?"":search.trim();
        String sql="SELECT id,kind,pattern,context,replacement,is_regex FROM rules WHERE kind=?"+
            (query.isEmpty()?"":" AND (pattern LIKE ? ESCAPE '\\' OR context LIKE ? ESCAPE '\\' OR replacement LIKE ? ESCAPE '\\')")+
            " ORDER BY ordinal,id LIMIT ?";
        String escaped=query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
        String[] args=query.isEmpty()?new String[]{String.valueOf(kind),String.valueOf(limit)}:
            new String[]{String.valueOf(kind),"%"+escaped+"%","%"+escaped+"%","%"+escaped+"%",String.valueOf(limit)};
        try(Cursor c=getReadableDatabase().rawQuery(sql,args)){
            while(c.moveToNext())out.add(new Rule(c.getLong(0),c.getInt(1),c.getString(2),
                c.getString(3),c.getString(4),c.getInt(5)!=0));
        }
        return out;
    }
    private int countType(SQLiteDatabase db,int kind,boolean regex){
        String sql=regex?"SELECT count(*) FROM rules WHERE is_regex=1":
            "SELECT count(*) FROM rules WHERE kind=? AND is_regex=0";
        try(Cursor c=db.rawQuery(sql,regex?null:new String[]{String.valueOf(kind)})){
            return c.moveToFirst()?c.getInt(0):0;
        }
    }
    synchronized void upsert(long id,int kind,String pattern,String context,String replacement,boolean regex){
        ensureMigrated();
        if(kind!=REPLACE&&kind!=IGNORE)throw new IllegalArgumentException("Unknown rule type");
        if(pattern==null||pattern.trim().isEmpty()||pattern.length()>MAX_PATTERN)
            throw new IllegalArgumentException("Шаблон: от 1 до "+MAX_PATTERN+" символов");
        if(context==null)context="";if(replacement==null)replacement="";
        if(context.length()>MAX_CONTEXT||replacement.length()>MAX_REPLACEMENT)
            throw new IllegalArgumentException("Слишком длинный контекст или замена");
        if(regex){
            EdgeSpeechTextTransform.validateRegexPattern(pattern);
            Pattern compiled=Pattern.compile(pattern,Pattern.UNICODE_CHARACTER_CLASS);
            // Reject an invalid capture reference or dangling escape in the replacement.
            EdgeSpeechTextTransform.validateReplacement(compiled,replacement);
        }
        SQLiteDatabase db=getWritableDatabase();
        int order;
        if(id>0){
            try(Cursor c=db.rawQuery("SELECT ordinal FROM rules WHERE id=?",new String[]{String.valueOf(id)})){
                if(!c.moveToFirst())throw new IllegalArgumentException("Правило уже удалено");
                order=c.getInt(0);
            }
        }else{
            int maximum=regex?MAX_REGEX:(kind==REPLACE?MAX_LITERAL_REPLACE:MAX_LITERAL_IGNORE);
            if(countType(db,kind,regex)>=maximum)
                throw new IllegalArgumentException("Лимит "+maximum+" правил достигнут");
            try(Cursor c=db.rawQuery("SELECT COALESCE(MAX(ordinal),0)+1 FROM rules",null)){
                c.moveToFirst();order=c.getInt(0);
            }
        }
        ContentValues v=values(kind,pattern,regex?"":context,replacement,regex,order);
        db.beginTransaction();
        try{
            if(id>0){
                // If switching a rule's mode, enforce the destination cap as well.
                try(Cursor c=db.rawQuery("SELECT kind,is_regex FROM rules WHERE id=?",new String[]{String.valueOf(id)})){
                    c.moveToFirst();int oldKind=c.getInt(0);boolean oldRegex=c.getInt(1)!=0;
                    if(oldKind!=kind||oldRegex!=regex){
                        int limit=regex?MAX_REGEX:(kind==REPLACE?MAX_LITERAL_REPLACE:MAX_LITERAL_IGNORE);
                        if(countType(db,kind,regex)>=limit)throw new IllegalArgumentException("Лимит "+limit+" правил достигнут");
                    }
                }
                db.update("rules",v,"id=?",new String[]{String.valueOf(id)});
            }else db.insertOrThrow("rules",null,v);
            db.setTransactionSuccessful();
        }finally {db.endTransaction();}
        changed();
    }
    synchronized void delete(long id){
        ensureMigrated();if(getWritableDatabase().delete("rules","id=?",new String[]{String.valueOf(id)})>0)changed();
    }
    EdgeSpeechTextTransform.Options options(){
        ensureMigrated();SharedPreferences p=prefs();int revision=p.getInt(REVISION,0);
        synchronized(EdgeDictionaryStore.class){
            if(cached!=null&&cachedRevision==revision &&
                cached.mode.equals(p.getString(EdgeSpeechTextTransform.MODE,"off"))&&
                cached.replacement.equals(p.getString(EdgeSpeechTextTransform.REPLACEMENT,",")))return cached;
            ArrayList<EdgeSpeechTextTransform.Entry> entries=new ArrayList<>();
            try(Cursor c=getReadableDatabase().rawQuery(
                "SELECT kind,pattern,context,replacement,is_regex FROM rules ORDER BY ordinal,id",null)){
                while(c.moveToNext()){
                    try{entries.add(new EdgeSpeechTextTransform.Entry(
                        c.getInt(0),c.getString(1),c.getString(2),c.getString(3),c.getInt(4)!=0));}
                    catch(IllegalArgumentException bad){/* invalid saved regex: skip */}
                }
            }
            cached=new EdgeSpeechTextTransform.Options(
                p.getString(EdgeSpeechTextTransform.MODE,"off"),
                p.getString(EdgeSpeechTextTransform.REPLACEMENT,","),entries,revision);
            cachedRevision=revision;
            return cached;
        }
    }
}
