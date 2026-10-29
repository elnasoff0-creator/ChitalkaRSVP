package ru.reader.rsvp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional frame-accurate callbacks from Android TTS. Stored ONLY beside disposable cached WAV. */
final class LocalTtsWordTimings {
    static final class Mark {
        final int start,end;final long atMs;
        Mark(int s,int e,long ms){start=s;end=e;atMs=ms;}
    }
    private LocalTtsWordTimings(){}
    static File sidecar(File wav){return new File(wav.getAbsolutePath()+".words.json");}
    static void write(File wav,int rate,List<int[]> frames){
        if(rate<=0||frames==null||frames.size()<2||!wav.isFile())return;
        try {
            JSONArray arr=new JSONArray();
            ArrayList<int[]> ordered=new ArrayList<>(frames);
            ordered.sort((a,b)->Integer.compare(a[2],b[2]));
            for(int[] event:ordered){if(event.length<3)continue;
                JSONArray item=new JSONArray();item.put(event[0]);item.put(event[1]);
                item.put(Math.round(event[2]*1000.0/rate));arr.put(item);
            }
            if(arr.length()<2)return;
            JSONObject json=new JSONObject();json.put("version",1);json.put("frames",arr);
            File dst=sidecar(wav),part=new File(dst.getAbsolutePath()+".part");
            try(FileOutputStream out=new FileOutputStream(part)){
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();
            }
            if(dst.exists())dst.delete();part.renameTo(dst);
        }catch(Exception ignored){}
    }
    static List<Mark> read(File wav){
        try{
            File path=sidecar(wav);
            if(!path.isFile()||path.length()>512000)return Collections.emptyList();
            byte[] bytes=new byte[(int)path.length()];
            try(FileInputStream in=new FileInputStream(path)){
                int n=0,read;while(n<bytes.length&&(read=in.read(bytes,n,bytes.length-n))>0)n+=read;
                if(n!=bytes.length)return Collections.emptyList();
            }
            JSONArray arr=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).optJSONArray("frames");
            if(arr==null)return Collections.emptyList();
            ArrayList<Mark> result=new ArrayList<>();long last=-1;
            for(int i=0;i<arr.length();i++){
                JSONArray row=arr.optJSONArray(i);if(row==null)continue;
                int start=row.optInt(0,-1),end=row.optInt(1,-1);long ms=row.optLong(2,-1);
                if(start>=0&&end>start&&ms>=last){result.add(new Mark(start,end,ms));last=ms;}
            }
            return result.size()<2?Collections.emptyList():result;
        }catch(Exception ignored){return Collections.emptyList();}
    }
    static Mark at(List<Mark> list,long ms){
        if(list==null||list.isEmpty())return null;
        int left=0,right=list.size()-1,best=-1;
        while(left<=right){int mid=(left+right)>>>1;
            if(list.get(mid).atMs<=ms){best=mid;left=mid+1;}else right=mid-1;
        }
        return best<0?null:list.get(best);
    }
    static long atChar(List<Mark> list,int character){
        for(Mark mark:list)if(character>=mark.start&&character<mark.end)return mark.atMs;
        return 0L;
    }
}
