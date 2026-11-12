package ru.reader.rsvp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

/** Bounded, display-only snapshot. Never substitutes for verified live downloader jobs. */
final class DownloadListSnapshot {
    private static final String KEY="downloads_dashboard_snapshot_v154";
    private static final int MAX=150;
    private DownloadListSnapshot(){}
    static void save(Context context,ArrayList<DownloadsRepository.Item> items){
        try{
            JSONArray arr=new JSONArray();
            for(DownloadsRepository.Item entry:items){
                if(arr.length()>=MAX)break;
                JSONObject o=new JSONObject();
                o.put("id",entry.id).put("type",entry.type).put("title",entry.title)
                    .put("cover",entry.cover).put("source",entry.source).put("bookUri",entry.bookUri)
                    .put("status",entry.status).put("description",entry.description)
                    .put("ready",entry.ready).put("running",entry.running).put("paused",entry.paused)
                    .put("failed",entry.failed).put("done",entry.done).put("total",entry.total)
                    .put("coverage",entry.coveragePercent);
                arr.put(o);
            }
            context.getSharedPreferences("reader",0).edit().putString(KEY,arr.toString()).apply();
        }catch(Exception ignored){}
    }
    static ArrayList<DownloadsRepository.Item> load(Context context){
        ArrayList<DownloadsRepository.Item> result=new ArrayList<>();
        try{
            JSONArray arr=new JSONArray(context.getSharedPreferences("reader",0).getString(KEY,"[]"));
            for(int i=0;i<Math.min(MAX,arr.length());i++){
                JSONObject o=arr.optJSONObject(i);if(o==null)continue;
                DownloadsRepository.Item item=new DownloadsRepository.Item();
                item.id=o.optString("id");item.type=o.optString("type");
                if(item.id.isEmpty()||item.type.isEmpty())continue;
                item.title=o.optString("title");item.cover=o.optString("cover");
                item.source=o.optString("source");item.bookUri=o.optString("bookUri");
                item.status=o.optString("status");item.description=o.optString("description");
                item.ready=o.optBoolean("ready");item.running=o.optBoolean("running");
                item.paused=o.optBoolean("paused");item.failed=o.optBoolean("failed");
                item.done=o.optInt("done");item.total=o.optInt("total");
                item.coveragePercent=o.optInt("coverage");item.previewOnly=true;
                item.title=BookTitleStore.downloadTitle(context,item);
                result.add(item);
            }
        }catch(Exception ignored){}
        return result;
    }
}
