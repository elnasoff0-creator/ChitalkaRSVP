package ru.reader.rsvp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Display-only per-book row cache. Downloader stores, never this snapshot, authorize file actions. */
final class DownloadRowsSnapshot {
    private DownloadRowsSnapshot(){}
    private static File file(Context c,String id){
        File dir=new File(c.getFilesDir(),"download_rows_v168");
        return new File(dir,Integer.toHexString(id.hashCode())+"_"+id.length()+".json");
    }
    static ArrayList<UnifiedDownloadManagerActivity.FileRow> load(Context c,String id){
        ArrayList<UnifiedDownloadManagerActivity.FileRow> result=new ArrayList<>();
        File f=file(c,id);
        try{
            if(!f.isFile()||f.length()<2||f.length()>2_000_000L)return result;
            byte[] raw=new byte[(int)f.length()];int n=0;
            try(FileInputStream in=new FileInputStream(f)){
                int count;while(n<raw.length&&(count=in.read(raw,n,raw.length-n))>0)n+=count;
            }
            JSONObject data=new JSONObject(new String(raw,0,n,StandardCharsets.UTF_8));
            if(!id.equals(data.optString("id")))return result;
            JSONArray rows=data.optJSONArray("rows");
            if(rows==null)return result;
            for(int i=0;i<rows.length();i++){
                JSONObject o=rows.optJSONObject(i);if(o==null)continue;
                String key=o.optString("key");if(key.isEmpty())continue;
                UnifiedDownloadManagerActivity.FileRow row=new UnifiedDownloadManagerActivity.FileRow(
                    key,o.optString("name"),o.optString("status"),o.optInt("percent",-1),
                    o.optLong("bytes"),o.optInt("ordinal",-1),null);
                row.detail=o.optString("detail");result.add(row);
            }
        }catch(Exception ignored){result.clear();}
        return result;
    }
    static void save(Context c,String id,ArrayList<UnifiedDownloadManagerActivity.FileRow> rows){
        File f=file(c,id),tmp=new File(f.getAbsolutePath()+".part");
        try{
            if(!f.getParentFile().isDirectory()&&!f.getParentFile().mkdirs())return;
            JSONObject data=new JSONObject();data.put("id",id);JSONArray entries=new JSONArray();
            for(UnifiedDownloadManagerActivity.FileRow row:rows){
                JSONObject o=new JSONObject();o.put("key",row.key);o.put("name",row.name);
                o.put("status",row.status);o.put("detail",row.detail);o.put("percent",row.percent);
                o.put("bytes",row.bytes);o.put("ordinal",row.ordinal);entries.put(o);
            }
            data.put("rows",entries);
            byte[] raw=data.toString().getBytes(StandardCharsets.UTF_8);
            if(raw.length>2_000_000)return;
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(raw);out.getFD().sync();}
            // On Android/Linux rename over an existing file is atomic; keep the old copy if it fails.
            if(!tmp.renameTo(f))tmp.delete();
        }catch(Exception ignored){tmp.delete();}
    }
}
