package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

/** Per-file persistent Wi-Fi transfer journal. No file content or PIN is stored here.
 * Upload and dashboard use the SAME stable wifi: ID; never create separate cards.
 */
final class WebTransferDownloadStore {
    static final String TYPE="wifi";
    static final String WAITING="waiting",RUNNING="running",IMPORTING="importing",
        DONE="done",ERROR="error",CANCELLED="cancelled",DUPLICATE="duplicate";
    private static final String PREF="web_transfer_download_history_v193",KEY="items";
    private static final int MAX_HISTORY=300;
    private static final java.util.HashMap<String,Long> lastWritten=new java.util.HashMap<>();
    static final class Entry {
        String id="",name="",kind="",state=WAITING,uri="",message="";
        long received,total,created,updated;
        JSONObject json() throws Exception {
            JSONObject o=new JSONObject();o.put("id",id);o.put("name",name);o.put("kind",kind);
            o.put("state",state);o.put("uri",uri);o.put("message",message);
            o.put("received",received);o.put("total",total);
            o.put("created",created);o.put("updated",updated);return o;
        }
        static Entry parse(JSONObject o){Entry e=new Entry();e.id=o.optString("id","");
            e.name=o.optString("name","");e.kind=o.optString("kind","");
            e.state=o.optString("state",ERROR);e.uri=o.optString("uri","");
            e.message=o.optString("message","");e.received=o.optLong("received",0);
            e.total=o.optLong("total",0);e.created=o.optLong("created",0);
            e.updated=o.optLong("updated",e.created);return e;
        }
        boolean active(){return WAITING.equals(state)||RUNNING.equals(state)||IMPORTING.equals(state);}
    }
    private WebTransferDownloadStore(){}
    private static SharedPreferences prefs(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
    private static ArrayList<Entry> read(Context c){ArrayList<Entry> list=new ArrayList<>();
        try{JSONArray a=new JSONArray(prefs(c).getString(KEY,"[]"));
            for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);
                if(o!=null){Entry e=Entry.parse(o);if(e.id.startsWith("wifi:"))list.add(e);}}}
        catch(Exception ignored){}return list;
    }
    private static boolean write(Context c,ArrayList<Entry> entries){
        JSONArray values=new JSONArray();try{
            for(int i=0;i<Math.min(entries.size(),MAX_HISTORY);i++)values.put(entries.get(i).json());
        }catch(Exception bad){return false;}
        return prefs(c).edit().putString(KEY,values.toString()).commit();
    }
    static String newId(){return "wifi:"+UUID.randomUUID().toString();}
    /** Reject a reused ID rather than creating a duplicate row. */
    static synchronized boolean begin(Context c,String id,String filename,String kind,long total){
        if(id==null||!id.matches("wifi:[A-Za-z0-9_-]{10,90}"))return false;
        ArrayList<Entry> list=read(c);for(Entry e:list)if(id.equals(e.id))return false;
        Entry e=new Entry();e.id=id;e.name=filename;e.kind=kind;e.total=total;
        e.created=e.updated=System.currentTimeMillis();e.state=RUNNING;list.add(0,e);
        if(!write(c,list))return false;
        lastWritten.put(id,0L);DownloadChangeBus.publish(id);return true;
    }
    static synchronized void progress(Context c,String id,long received,long total){
        long tick=SystemClock.elapsedRealtime();Long previous=lastWritten.get(id);
        if(previous!=null&&tick-previous<300L&&received<total)return;
        ArrayList<Entry> list=read(c);for(Entry e:list)if(id.equals(e.id)){
            if(!e.active()||CANCELLED.equals(e.state))return;
            e.received=Math.max(e.received,Math.min(received,total));e.total=total;
            e.state=RUNNING;e.updated=System.currentTimeMillis();
            if(write(c,list)){lastWritten.put(id,tick);DownloadChangeBus.publish(id);}return;
        }
    }
    static synchronized void importing(Context c,String id){set(c,id,IMPORTING,"","",-1);}
    static synchronized void finish(Context c,String id,String state,String message,String uri,long bytes){
        set(c,id,state,message,uri,bytes);
    }
    private static void set(Context c,String id,String state,String message,String uri,long bytes){
        ArrayList<Entry> list=read(c);for(Entry e:list)if(id.equals(e.id)){
            // Cancellation wins against late socket/SAF callbacks.
            if(CANCELLED.equals(e.state)&&!CANCELLED.equals(state))return;
            e.state=state;e.message=message==null?"":message;
            if(uri!=null&&!uri.isEmpty())e.uri=uri;
            if(bytes>=0)e.received=Math.min(e.total,bytes);
            e.updated=System.currentTimeMillis();
            if(write(c,list))DownloadChangeBus.publish(id);
            lastWritten.remove(id);return;
        }
    }
    static synchronized boolean cancel(Context c,String id){
        Entry e=one(c,id);
        if(e==null||!(WAITING.equals(e.state)||RUNNING.equals(e.state)))return false;
        set(c,id,CANCELLED,"Остановлено пользователем","",-1);return true;
    }
    static synchronized boolean cancelled(Context c,String id){Entry e=one(c,id);return e!=null&&CANCELLED.equals(e.state);}
    static synchronized Entry one(Context c,String id){for(Entry e:read(c))if(id.equals(e.id))return e;return null;}
    static synchronized ArrayList<Entry> all(Context c){return read(c);}
    static synchronized void interruptActive(Context c,String reason){
        ArrayList<Entry> list=read(c);boolean changed=false;
        for(Entry e:list)if(e.active()){
            e.state=ERROR;e.message=reason;e.updated=System.currentTimeMillis();changed=true;
        }
        if(changed&&write(c,list))for(Entry e:list)if(ERROR.equals(e.state)&&reason.equals(e.message))DownloadChangeBus.publish(e.id);
        lastWritten.clear();
    }
    /** The service is non-sticky: jobs left running by process death are failures, not 100%. */
    static synchronized void recover(Context c,boolean serverRunning){
        if(!serverRunning)interruptActive(c,"Передача прервана: сервер не работает");
    }
    static synchronized boolean remove(Context c,String id){
        ArrayList<Entry> list=read(c);boolean removed=list.removeIf(e->id.equals(e.id)&&!e.active());
        if(removed&&write(c,list)){lastWritten.remove(id);DownloadChangeBus.publish(id);}
        return removed;
    }
}
