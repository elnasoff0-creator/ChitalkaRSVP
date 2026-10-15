package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

/** Durable, independent list of public-link download batches. */
final class LinkDownloadStore {
    private static final String KEY="direct_link_batches_v1";
    static final class Entry {
        String source="",cloudPath="",url="",name="",format="mp3",status="queued",error="",savedUri="";
        long size=-1,bytes=0;
        JSONObject json() throws Exception {JSONObject o=new JSONObject();o.put("source",source);o.put("cloudPath",cloudPath);o.put("url",url);o.put("name",name);o.put("format",format);o.put("status",status);o.put("error",error);o.put("savedUri",savedUri);o.put("size",size);o.put("bytes",bytes);return o;}
        static Entry parse(JSONObject o){Entry e=new Entry();e.source=o.optString("source");e.cloudPath=o.optString("cloudPath");e.url=o.optString("url");e.name=o.optString("name");e.format=o.optString("format",LinkFileTypes.extension(e.name));if(!LinkFileTypes.supported("file."+e.format))e.format="mp3";e.status=o.optString("status","queued");e.error=o.optString("error");e.savedUri=o.optString("savedUri");e.size=o.optLong("size",-1);e.bytes=o.optLong("bytes");return e;}
    }
    static final class Batch {
        String id=UUID.randomUUID().toString(),title="Загрузка по ссылке",treeUri="",treeLabel="",subfolder="",status="queued",error="";
        long updated=System.currentTimeMillis();
        ArrayList<Entry> files=new ArrayList<>();
        int completed(){int n=0;for(Entry e:files)if("completed".equals(e.status))n++;return n;}
        JSONObject json() throws Exception {JSONObject o=new JSONObject();o.put("id",id);o.put("title",title);o.put("treeUri",treeUri);o.put("treeLabel",treeLabel);o.put("subfolder",subfolder);o.put("status",status);o.put("error",error);o.put("updated",updated);JSONArray a=new JSONArray();for(Entry e:files)a.put(e.json());o.put("files",a);return o;}
        static Batch parse(JSONObject o){Batch b=new Batch();b.id=o.optString("id",b.id);b.title=o.optString("title",b.title);b.treeUri=o.optString("treeUri");b.treeLabel=o.optString("treeLabel");b.subfolder=o.optString("subfolder");b.status=o.optString("status","queued");b.error=o.optString("error");b.updated=o.optLong("updated");JSONArray a=o.optJSONArray("files");if(a!=null)for(int i=0;i<a.length();i++){JSONObject item=a.optJSONObject(i);if(item!=null)b.files.add(Entry.parse(item));}return b;}
    }
    static synchronized ArrayList<Batch> load(Context c){ArrayList<Batch> out=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences("reader",0).getString(KEY,"[]"));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(Batch.parse(o));}}catch(Exception ignored){}return out;}
    static synchronized void put(Context c,Batch b){ArrayList<Batch> a=load(c);boolean found=false;for(int i=0;i<a.size();i++)if(a.get(i).id.equals(b.id)){a.set(i,b);found=true;break;}if(!found)a.add(b);persist(c,a);}
    static synchronized Batch get(Context c,String id){for(Batch b:load(c))if(b.id.equals(id))return b;return null;}
    static synchronized void recoverInterrupted(Context c){
        ArrayList<Batch> batches=load(c);boolean changed=false;
        for(Batch batch:batches){
            if("running".equals(batch.status)){
                batch.status="paused";changed=true;
                for(Entry f:batch.files)if("running".equals(f.status))f.status="paused";
            }
        }
        if(changed)persist(c,batches);
    }
    static synchronized void remove(Context c,String id){ArrayList<Batch> a=load(c);a.removeIf(b->id.equals(b.id));persist(c,a);}
    private static void persist(Context c,ArrayList<Batch> a){JSONArray arr=new JSONArray();try{for(Batch b:a)arr.put(b.json());c.getSharedPreferences("reader",0).edit().putString(KEY,arr.toString()).commit();}catch(Exception ignored){}}
    static String safeName(String raw){String s=raw==null?"":raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replaceAll("[. ]+$","").trim();if(s.length()>120)s=s.substring(0,120).trim();if(s.isEmpty()||".".equals(s)||"..".equals(s))s="audio";return s;}
    static String safeMp3(String name){return LinkFileTypes.safeFilename(name,"mp3");}
    /** Text files from direct links appear in the ordinary reading library automatically. */
    static synchronized void registerTextBook(Context context,Entry entry){
        if(!"completed".equals(entry.status)||entry.savedUri.isEmpty()||"mp3".equals(entry.format))return;
        try{
            android.content.SharedPreferences prefs=context.getSharedPreferences("reader",0);
            JSONArray old=new JSONArray(prefs.getString("library","[]"));JSONArray result=new JSONArray();
            for(int i=0;i<old.length();i++){
                JSONObject row=old.optJSONObject(i);
                if(row!=null&&!entry.savedUri.equals(row.optString("uri")))result.put(row);
            }
            JSONObject book=new JSONObject();book.put("uri",entry.savedUri);
            book.put("title",entry.name.replaceFirst("(?i)\\.(fb2|epub)$",""));
            book.put("format",entry.format);book.put("index",0);book.put("progress",0);
            book.put("openedAt",0);result.put(book);
            prefs.edit().putString("library",result.toString()).commit();
        }catch(Exception ignored){}
    }
    static void addToLibrary(Context c,Batch b){AudioLibrary.Book book=new AudioLibrary.Book();book.id="direct:"+b.id;book.name=b.subfolder.isEmpty()?b.title:b.subfolder;book.updated=System.currentTimeMillis();for(Entry f:b.files)if("mp3".equals(f.format)&&"completed".equals(f.status)&&!f.savedUri.isEmpty()){book.uris.add(f.savedUri);book.chapters.add(AudioLibrary.clean(f.name));}if(book.uris.isEmpty())return;ArrayList<AudioLibrary.Book> books=AudioLibrary.load(c);books.removeIf(x->x.id.equals(book.id));books.add(book);AudioLibrary.save(c,books);}
}
