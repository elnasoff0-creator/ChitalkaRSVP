package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** SAF tree catalog: a book is a folder containing audio tracks. */
public final class AudioLibrary {
    static final String STORE="audio_books_v2";
    public static class Book {
        public String id="",name="";public boolean customName=false;
        public ArrayList<String> uris=new ArrayList<>(),chapters=new ArrayList<>();
        public long updated=0;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("id",id);o.put("name",name);o.put("customName",customName);o.put("updated",updated);o.put("uris",new JSONArray(uris));o.put("chapters",new JSONArray(chapters));}catch(Exception ignored){}return o;}
        static Book parse(JSONObject o){Book b=new Book();b.id=o.optString("id");b.name=o.optString("name");b.customName=o.optBoolean("customName",false);b.updated=o.optLong("updated");JSONArray u=o.optJSONArray("uris"),c=o.optJSONArray("chapters");if(u!=null)for(int i=0;i<u.length();i++)b.uris.add(u.optString(i));if(c!=null)for(int i=0;i<c.length();i++)b.chapters.add(c.optString(i));return b;}
    }
    public static ArrayList<Book> load(Context ctx){ArrayList<Book> result=new ArrayList<>();try{JSONArray a=new JSONArray(ctx.getSharedPreferences("reader",0).getString(STORE,"[]"));for(int i=0;i<a.length();i++)if(a.optJSONObject(i)!=null)result.add(Book.parse(a.getJSONObject(i)));}catch(Exception ignored){}return result;}
    public static void save(Context ctx,ArrayList<Book> all){JSONArray a=new JSONArray();for(Book b:all)a.put(b.json());ctx.getSharedPreferences("reader",0).edit().putString(STORE,a.toString()).apply();}
    static class Entry {String docId,name,mime; Entry(String d,String n,String m){docId=d;name=n;mime=m;}}
    static ArrayList<Entry> children(Context ctx,Uri tree,String id){ArrayList<Entry> out=new ArrayList<>();Uri query=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);try(Cursor cur=ctx.getContentResolver().query(query,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){if(cur!=null)while(cur.moveToNext())out.add(new Entry(cur.getString(0),cur.getString(1),cur.getString(2)));}return out;}
    static boolean isAudio(String filename){return filename!=null&&filename.toLowerCase(Locale.ROOT).matches(".*\\.(mp3|m4a|m4b|aac|ogg|opus|flac|wav|wma)$");}
    static String clean(String name){return name.replaceAll("(?i)\\.(mp3|m4a|m4b|aac|ogg|opus|flac|wav|wma)$","");}
    static void traverse(Context ctx,Uri tree,String id,String folder,boolean recursive,ArrayList<Book> result,int depth){if(depth>12||result.size()>400)return;ArrayList<Entry> entries=children(ctx,tree,id);ArrayList<Entry> tracks=new ArrayList<>(),subfolders=new ArrayList<>();for(Entry e:entries){if(DocumentsContract.Document.MIME_TYPE_DIR.equals(e.mime))subfolders.add(e);else if(isAudio(e.name)||e.mime!=null&&e.mime.startsWith("audio/"))tracks.add(e);}
        if(!tracks.isEmpty()) {tracks.sort((a,b)->naturalCompare(a.name,b.name));Book book=new Book();book.id=tree.toString()+"#"+id;book.name=folder;book.updated=System.currentTimeMillis();for(Entry e:tracks){book.uris.add(DocumentsContract.buildDocumentUriUsingTree(tree,e.docId).toString());book.chapters.add(clean(e.name));}result.add(book);}
        if(recursive)for(Entry e:subfolders)traverse(ctx,tree,e.docId,e.name,true,result,depth+1);
    }
    static int naturalCompare(String a,String b){java.util.regex.Matcher x=java.util.regex.Pattern.compile("\\d+|\\D+").matcher(a.toLowerCase(Locale.ROOT)),y=java.util.regex.Pattern.compile("\\d+|\\D+").matcher(b.toLowerCase(Locale.ROOT));while(x.find()){if(!y.find())return 1;String p=x.group(),q=y.group();if(p.matches("\\d+")&&q.matches("\\d+")){int n=Long.compare(parseNum(p),parseNum(q));if(n!=0)return n;}else{int n=p.compareTo(q);if(n!=0)return n;}}return y.find()?-1:0;}
    static long parseNum(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    public static ArrayList<Book> importTree(Context ctx,Uri tree,boolean recursive)throws Exception{ArrayList<Book> found=new ArrayList<>();String root=DocumentsContract.getTreeDocumentId(tree);String title="Аудиокнига";try(Cursor c=ctx.getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(tree,root),new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())title=c.getString(0);}traverse(ctx,tree,root,title,recursive,found,0);return found;}
    public static void merge(Context ctx,ArrayList<Book> found){ArrayList<Book> all=load(ctx);for(Book b:found){boolean replaced=false;for(int i=0;i<all.size();i++)if(all.get(i).id.equals(b.id)){if(all.get(i).customName){b.name=all.get(i).name;b.customName=true;}all.set(i,b);replaced=true;break;}if(!replaced)all.add(b);}save(ctx,all);}
}
