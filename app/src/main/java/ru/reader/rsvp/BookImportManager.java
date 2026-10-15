package ru.reader.rsvp;
import android.content.*;
import org.json.*;
import java.util.*;
/** Persists download jobs in the same reader preferences and merges verified tracks into AudioLibrary. */
public final class BookImportManager {
    static final String KEY="url_import_jobs_v1";
    private static final String REMOVED="url_import_removed_v1";
    public static class Job {
        public String source="",title="",cover="",status="queued",error=""; public ArrayList<KvuheParser.Chapter> chapters=new ArrayList<>();public ArrayList<Integer> states=new ArrayList<>();public long bytes=0,total=0;public int index=0;public boolean paused=false,cancelled=false;
        JSONObject json()throws JSONException{JSONObject j=new JSONObject();j.put("source",source);j.put("title",title);j.put("cover",cover);j.put("status",status);j.put("error",error);j.put("index",index);j.put("bytes",bytes);j.put("total",total);j.put("paused",paused);j.put("cancelled",cancelled);JSONArray a=new JSONArray(),s=new JSONArray();for(KvuheParser.Chapter c:chapters)a.put(c.json());for(int st:states)s.put(st);j.put("chapters",a);j.put("states",s);return j;}
        static Job from(JSONObject j){Job b=new Job();b.source=j.optString("source");b.title=j.optString("title");b.cover=j.optString("cover");b.status=j.optString("status","queued");b.error=j.optString("error");b.index=j.optInt("index");b.bytes=j.optLong("bytes");b.total=j.optLong("total");b.paused=j.optBoolean("paused");b.cancelled=j.optBoolean("cancelled");JSONArray a=j.optJSONArray("chapters"),s=j.optJSONArray("states");if(a!=null)for(int i=0;i<a.length();i++)b.chapters.add(KvuheParser.Chapter.parse(a.optJSONObject(i)));if(s!=null)for(int i=0;i<s.length();i++)b.states.add(s.optInt(i));return b;}
    }
    public static synchronized ArrayList<Job> load(Context c){ArrayList<Job> out=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences("reader",0).getString(KEY,"[]"));for(int i=0;i<a.length();i++)out.add(Job.from(a.getJSONObject(i)));}catch(Exception ignored){}return out;}
    public static synchronized void put(Context c,Job job){if(c.getSharedPreferences("reader",0).getStringSet(REMOVED,java.util.Collections.emptySet()).contains(job.source))return;ArrayList<Job> all=load(c);boolean found=false;for(int i=0;i<all.size();i++)if(all.get(i).source.equals(job.source)){all.set(i,job);found=true;break;}if(!found)all.add(job);JSONArray a=new JSONArray();try{for(Job j:all)a.put(j.json());c.getSharedPreferences("reader",0).edit().putString(KEY,a.toString()).commit();}catch(Exception ignored){}}
    /** A new explicit import may restore an earlier removed URL. */
    public static synchronized void putNew(Context context,Job job){
        android.content.SharedPreferences prefs=context.getSharedPreferences("reader",0);
        java.util.Set<String> removed=new java.util.HashSet<>(prefs.getStringSet(REMOVED,java.util.Collections.emptySet()));
        if(removed.remove(job.source))prefs.edit().putStringSet(REMOVED,removed).commit();
        put(context,job);
    }
    public static synchronized void remove(Context context,Job job,boolean deleteFiles){
        android.content.SharedPreferences prefs=context.getSharedPreferences("reader",0);
        java.util.Set<String> removed=new java.util.HashSet<>(prefs.getStringSet(REMOVED,java.util.Collections.emptySet()));
        removed.add(job.source);
        ArrayList<Job> all=load(context);all.removeIf(j->j.source.equals(job.source));
        JSONArray data=new JSONArray();try{for(Job j:all)data.put(j.json());}catch(Exception ignored){}
        prefs.edit().putStringSet(REMOVED,removed).putString(KEY,data.toString()).commit();
        if(deleteFiles){
            // Each file is owned by a job. Never delete a shared directory wholesale.
            for(int i=0;i<job.chapters.size();i++)trackFile(context,job,i).delete();
            AudiobookCoverStore.coverFile(context,job).delete();
            ArrayList<AudioLibrary.Book> books=AudioLibrary.load(context);
            books.removeIf(book->book.id.equals("url:"+job.source));
            AudioLibrary.save(context,books);
            prefs.edit().remove("audio_cover:url:"+job.source).apply();
        }
    }
    public static String safe(String s){String t=s.replaceAll("[\\x00-\\x1f\\\\/:*?\"<>|]","_").trim();return t.isEmpty()?"Аудиокнига":(t.length()>100?t.substring(0,100):t);}
    public static java.io.File directory(Context c,Job j){java.io.File base=c.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);return new java.io.File(new java.io.File(base,"Audiobooks"),safe(j.title));}
    public static java.io.File trackFile(Context c,Job j,int i){return new java.io.File(directory(c,j),String.format(java.util.Locale.ROOT,"%03d - %s.mp3",i+1,safe(j.chapters.get(i).name)));}
    public static synchronized void updateLibrary(Context c,Job j){if(c.getSharedPreferences("reader",0).getStringSet(REMOVED,java.util.Collections.emptySet()).contains(j.source))return;AudiobookCoverStore.registerDefault(c,j);AudioLibrary.Book book=new AudioLibrary.Book();book.id="url:"+j.source;book.name=j.title;book.updated=System.currentTimeMillis();for(int i=0;i<j.chapters.size();i++){java.io.File f=trackFile(c,j,i);if(f.exists()&&f.length()>1024){book.uris.add(android.net.Uri.fromFile(f).toString());book.chapters.add(j.chapters.get(i).name);}}if(book.uris.isEmpty())return;ArrayList<AudioLibrary.Book> all=AudioLibrary.load(c);boolean old=false;for(int i=0;i<all.size();i++)if(all.get(i).id.equals(book.id)){all.set(i,book);old=true;break;}if(!old)all.add(book);AudioLibrary.save(c,all);}
}
