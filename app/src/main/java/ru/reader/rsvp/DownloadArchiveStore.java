package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Logical archive of completed dashboard rows. It NEVER moves or deletes media. */
final class DownloadArchiveStore {
    private static final String PREF="downloads_archive_v153";
    private static final String ITEMS="records";
    private static final String DISMISSED="dismissed";

    static final class Entry {
        String id="",type="",title="",sourceTitle="",displayTitle="",cover="",description="",source="",bookUri="";
        long archivedAt;
        JSONObject json()throws Exception{
            JSONObject v=new JSONObject();v.put("id",id);v.put("type",type);v.put("title",title);
            v.put("cover",cover);v.put("sourceTitle",sourceTitle);v.put("description",description);v.put("source",source);
            v.put("bookUri",bookUri);v.put("archivedAt",archivedAt);return v;
        }
        static Entry from(JSONObject o){
            Entry r=new Entry();r.id=o.optString("id","");r.type=o.optString("type","");
            r.title=o.optString("title","");r.sourceTitle=o.optString("sourceTitle",r.title);r.displayTitle=r.title;r.cover=o.optString("cover","");
            r.description=o.optString("description","");r.source=o.optString("source","");
            r.bookUri=o.optString("bookUri","");r.archivedAt=o.optLong("archivedAt",0);return r;
        }
    }
    private DownloadArchiveStore(){}
    private static SharedPreferences pref(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    static synchronized ArrayList<Entry> entries(Context c){
        ArrayList<Entry> out=new ArrayList<>();
        try{JSONArray a=new JSONArray(pref(c).getString(ITEMS,"[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){Entry e=Entry.from(o);if(!e.id.isEmpty()){
                    e.displayTitle=BookTitleStore.title(c,BookTitleStore.DOWNLOAD,e.id,e.title);
                    out.add(e);
                }}}
        }catch(Exception ignored){}return out;
    }
    static synchronized Set<String> hiddenIds(Context c){
        HashSet<String> hidden=new HashSet<>(pref(c).getStringSet(DISMISSED,java.util.Collections.emptySet()));
        for(Entry e:entries(c))hidden.add(e.id);
        return hidden;
    }
    private static boolean persist(Context c,ArrayList<Entry> values,Set<String> dismissed){
        JSONArray a=new JSONArray();try{for(Entry e:values)a.put(e.json());}catch(Exception ex){return false;}
        return pref(c).edit().putString(ITEMS,a.toString())
            .putStringSet(DISMISSED,new HashSet<>(dismissed)).commit();
    }
    static synchronized boolean archive(Context c,DownloadsRepository.Item item){
        if(item==null||!item.ready||item.id==null||item.id.isEmpty())return false;
        ArrayList<Entry> all=entries(c);
        for(Entry e:all)if(item.id.equals(e.id))return true;
        Entry e=new Entry();e.id=item.id;e.type=item.type;e.title=item.title;e.sourceTitle=item.sourceTitle.isEmpty()?item.title:item.sourceTitle;e.displayTitle=item.title;e.cover=item.cover;
        e.description=item.description;e.source=item.source;e.bookUri=item.bookUri;
        e.archivedAt=System.currentTimeMillis();all.add(e);
        Set<String> discarded=new HashSet<>(pref(c).getStringSet(DISMISSED,java.util.Collections.emptySet()));
        discarded.remove(e.id);return persist(c,all,discarded);
    }
    static synchronized boolean restore(Context c,String id){
        ArrayList<Entry> all=entries(c);boolean exists=all.removeIf(e->id.equals(e.id));
        if(!exists)return false;
        Set<String> discarded=new HashSet<>(pref(c).getStringSet(DISMISSED,java.util.Collections.emptySet()));
        discarded.remove(id);return persist(c,all,discarded);
    }
    /** A successful physical deletion removes the archive record and hides any stale scanner projection. */
    static synchronized boolean removedFiles(Context c,String id){
        ArrayList<Entry> all=entries(c);all.removeIf(e->id.equals(e.id));
        Set<String> discarded=new HashSet<>(pref(c).getStringSet(DISMISSED,java.util.Collections.emptySet()));
        discarded.add(id);return persist(c,all,discarded);
    }
    /** Clear the archive index, not the files. Suppression avoids old jobs immediately reappearing. */
    static synchronized boolean clear(Context c){
        ArrayList<Entry> entries=entries(c);
        Set<String> discarded=new HashSet<>(pref(c).getStringSet(DISMISSED,java.util.Collections.emptySet()));
        for(Entry e:entries)discarded.add(e.id);
        return persist(c,new ArrayList<>(),discarded);
    }
}
