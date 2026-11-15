package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Small per-book Info tab snapshot. Reading it never enumerates SAF folders. */
final class DownloadInfoSnapshot {
    private static final String PREFS="download_info_snapshot_v169";
    private DownloadInfoSnapshot(){}
    static final class Entry {
        String id="",status="",description="",type="",folderCategory="",path="",issue="";
        int done,total;
        long bytes;
        String signature(){return status+"|"+done+"|"+total+"|"+bytes+"|"+description+"|"+
            type+"|"+folderCategory+"|"+path+"|"+issue;}
    }
    static Entry load(Context context,String id){
        String raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(id,"");
        if(raw.isEmpty())return null;
        try{
            JSONObject json=new JSONObject(raw);
            if(json.optInt("schema")!=1||!id.equals(json.optString("id")))return null;
            Entry e=new Entry();e.id=id;e.status=json.optString("status");
            e.description=json.optString("description");e.type=json.optString("type");
            e.folderCategory=json.optString("folderCategory");e.path=json.optString("path");
            e.issue=json.optString("issue");e.done=json.optInt("done");e.total=json.optInt("total");
            e.bytes=json.optLong("bytes");return e;
        }catch(Exception ignored){return null;}
    }
    static void save(Context context,Entry e){
        if(e==null||e.id.isEmpty())return;
        try{
            JSONObject json=new JSONObject();json.put("schema",1);json.put("id",e.id);
            json.put("status",e.status);json.put("description",e.description);json.put("type",e.type);
            json.put("folderCategory",e.folderCategory);json.put("path",e.path);json.put("issue",e.issue);
            json.put("done",e.done);json.put("total",e.total);json.put("bytes",e.bytes);
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(e.id,json.toString()).apply();
        }catch(Exception ignored){}
    }
    static void remove(Context context,String id){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().remove(id).apply();
    }
}
