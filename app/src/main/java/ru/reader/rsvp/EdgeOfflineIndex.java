package ru.reader.rsvp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Comparator;

/** Read-only, backwards-compatible view of speech files saved by both Edge producers.
 * Coverage counts verified MP3 byte files rather than stale 'ready' JSON flags.
 * No cache migration/deletion is performed. All offsets are global word indices.
 */
final class EdgeOfflineIndex {
    static final class Segment {
        final int start,end; final String key; final long bytes;
        Segment(int from,int to,String hash,long size){start=from;end=to;key=hash;bytes=size;}
    }
    static final class Stats {
        final int covered,total,files; final long bytes;
        Stats(int covered,int total,int files,long bytes){this.covered=covered;this.total=total;this.files=files;this.bytes=bytes;}
        int percent(){return total<=0?0:Math.min(100,(int)(100L*covered/total));}
        boolean complete(){return total>0&&covered>=total;}
    }
    private EdgeOfflineIndex(){}
    private static JSONArray read(File file){
        try{
            if(!file.isFile()||file.length()<2||file.length()>24*1024*1024)return new JSONArray();
            byte[] data=new byte[(int)file.length()];int position=0;
            try(FileInputStream in=new FileInputStream(file)){
                int n;while(position<data.length&&(n=in.read(data,position,data.length-position))>0)position+=n;
            }
            return new JSONArray(new String(data,0,position,StandardCharsets.UTF_8));
        }catch(Exception ignored){return new JSONArray();}
    }
    static ArrayList<Segment> readySegments(File edgeFolder){
        ArrayList<Segment> found=new ArrayList<>();
        if(edgeFolder==null||!edgeFolder.isDirectory())return found;
        HashSet<String> seen=new HashSet<>();
        for(String manifest:new String[]{"sentences.json","queue.json","full_queue.json","selected_clips.json"}){
            JSONArray rows=read(new File(edgeFolder,manifest));
            for(int i=0;i<rows.length();i++){
                JSONObject o=rows.optJSONObject(i);if(o==null)continue;
                int start=o.optInt("start",-1),end=o.optInt("end",-1);
                String key=o.optString("key","");
                if(start<0||end<=start||end>100000000||!key.matches("[a-fA-F0-9]{64}"))continue;
                if(!seen.add(start+":"+end+":"+key))continue;
                File mp3=new File(edgeFolder,key+".mp3");
                if(mp3.isFile()&&mp3.length()>1500)found.add(new Segment(start,end,key,mp3.length()));
            }
        }
        found.sort(Comparator.comparingInt((Segment v)->v.start).thenComparingInt(v->v.end));
        return found;
    }
    static Stats stats(ArrayList<Segment> rows,int from,int to){
        int total=Math.max(0,to-from),covered=0,current=from,files=0;
        long bytes=0;HashSet<String> keys=new HashSet<>();
        for(Segment segment:rows){
            if(segment.end<=from||segment.start>=to)continue;
            int lo=Math.max(from,segment.start),hi=Math.min(to,segment.end);
            if(hi<=lo)continue;
            if(hi>current){covered+=hi-Math.max(lo,current);current=hi;}
            if(keys.add(segment.key)){bytes+=segment.bytes;files++;}
        }
        return new Stats(Math.min(total,covered),total,files,bytes);
    }
    static Stats stats(File folder,int from,int to){return stats(readySegments(folder),from,to);}
}
