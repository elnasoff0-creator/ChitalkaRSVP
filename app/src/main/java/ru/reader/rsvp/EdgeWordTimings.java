package ru.reader.rsvp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Edge Read Aloud WordBoundary ticks -> exact ranges in the synthesized text.
 * The sidecar is stored next to the persistent MP3, keyed by the same file name.
 * Missing legacy timing never becomes a fabricated time-based word highlight.
 */
final class EdgeWordTimings {
    static final class Boundary {
        final long offsetTicks, durationTicks;
        final String spoken;
        Boundary(long offsetTicks, long durationTicks, String spoken) {
            this.offsetTicks=offsetTicks;this.durationTicks=durationTicks;this.spoken=spoken;
        }
    }
    static final class Word {
        final long startMs, durationMs;
        final int from, to;
        Word(long startMs,long durationMs,int from,int to){
            this.startMs=startMs;this.durationMs=durationMs;this.from=from;this.to=to;
        }
    }
    private static final Pattern TOKENS=Pattern.compile("\\S+");
    private EdgeWordTimings() {}

    static void parseMetadata(String body,List<Boundary> into) {
        try {
            JSONObject root=new JSONObject(body);
            JSONArray metadata=root.optJSONArray("Metadata");
            if(metadata==null)return;
            for(int i=0;i<metadata.length();i++){
                JSONObject item=metadata.optJSONObject(i);
                if(item==null||!"WordBoundary".equals(item.optString("Type")))continue;
                JSONObject data=item.optJSONObject("Data");if(data==null)continue;
                JSONObject word=data.optJSONObject("text");
                if(word==null)word=data.optJSONObject("Text");
                String spoken=word==null?"":word.optString("Text","");
                long ticks=data.optLong("Offset",-1);
                long duration=data.optLong("Duration",0);
                if(ticks>=0&&!spoken.isEmpty()&&into.size()<12000)
                    into.add(new Boundary(ticks,Math.max(0,duration),spoken));
            }
        }catch(Exception ignored){ /* Malformed metadata must not break audio playback. */ }
    }
    private static String normalized(String text){
        String value=Normalizer.normalize(text,Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim();
        return value.replaceAll("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}]+$","");
    }
    static List<Word> align(String text,List<Boundary> boundaries){
        if(text==null||boundaries==null||boundaries.isEmpty())return Collections.emptyList();
        ArrayList<int[]> spans=new ArrayList<>();ArrayList<String> names=new ArrayList<>();
        Matcher m=TOKENS.matcher(text);
        while(m.find()){spans.add(new int[]{m.start(),m.end()});names.add(normalized(m.group()));}
        ArrayList<Word> result=new ArrayList<>();int cursor=0;
        ArrayList<Boundary> sorted=new ArrayList<>(boundaries);
        sorted.sort(Comparator.comparingLong(a->a.offsetTicks));
        long lastTime=-1;
        for(Boundary b:sorted){
            String target=normalized(b.spoken);
            if(target.isEmpty())continue;
            int selected=-1;
            for(int j=cursor;j<Math.min(names.size(),cursor+10);j++){
                if(target.equals(names.get(j))){selected=j;break;}
            }
            // Never guess from the word index: speech may skip punctuation or normalize text.
            if(selected<0)continue;
            long time=Math.max(0,b.offsetTicks/10000L); // Edge uses 100-ns ticks.
            if(time<lastTime)continue;
            int[] span=spans.get(selected);
            result.add(new Word(time,Math.max(0,b.durationTicks/10000L),span[0],span[1]));
            lastTime=time;cursor=selected+1;
        }
        return result;
    }
    static File sidecar(File audio){return new File(audio.getParentFile(),audio.getName()+".words.json");}
    private static String hash(String text){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex=new StringBuilder();for(byte v:bytes)hex.append(String.format(Locale.ROOT,"%02x",v&255));
            return hex.toString();
        }catch(Exception e){return Integer.toHexString(text.hashCode());}
    }
    static void store(File audio,String text,List<Boundary> boundaries) {
        List<Word> aligned=align(text,boundaries);
        if(aligned.isEmpty())return;
        File file=sidecar(audio);File tmp=new File(file.getAbsolutePath()+".part");
        try{
            JSONObject obj=new JSONObject();obj.put("version",1);obj.put("sha256",hash(text));
            JSONArray list=new JSONArray();
            for(Word w:aligned){JSONArray row=new JSONArray();row.put(w.startMs);row.put(w.durationMs);
                row.put(w.from);row.put(w.to);list.put(row);}
            obj.put("words",list);
            try(FileOutputStream out=new FileOutputStream(tmp,false)){
                out.write(obj.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            if(!tmp.renameTo(file)){
                if(file.exists()&&!file.delete())throw new Exception("Cannot replace timing file");
                if(!tmp.renameTo(file))throw new Exception("Cannot write timing file");
            }
        }catch(Exception ignored){tmp.delete();}
    }
    static List<Word> read(File audio,String text){
        if(audio==null||text==null)return Collections.emptyList();
        File file=sidecar(audio);
        if(!file.isFile()||file.length()<8||file.length()>2*1024*1024)return Collections.emptyList();
        try{
            byte[] bytes=new byte[(int)file.length()];int n=0;
            try(FileInputStream in=new FileInputStream(file)){
                int read;while(n<bytes.length&&(read=in.read(bytes,n,bytes.length-n))>0)n+=read;}
            if(n!=bytes.length)return Collections.emptyList();
            JSONObject obj=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(obj.optInt("version")!=1||!hash(text).equals(obj.optString("sha256")))return Collections.emptyList();
            JSONArray list=obj.optJSONArray("words");if(list==null)return Collections.emptyList();
            ArrayList<Word> words=new ArrayList<>();long last=-1;
            for(int i=0;i<list.length()&&i<12000;i++){
                JSONArray row=list.optJSONArray(i);if(row==null||row.length()<4)continue;
                long start=row.optLong(0,-1),duration=row.optLong(1,0);
                int from=row.optInt(2,-1),to=row.optInt(3,-1);
                if(start<last||from<0||to<=from||to>text.length())continue;
                words.add(new Word(start,Math.max(0,duration),from,to));last=start;
            }
            return words;
        }catch(Exception ignored){return Collections.emptyList();}
    }
    static int at(List<Word> words,long sourcePositionMs){
        if(words==null||words.isEmpty())return -1;
        int lo=0,hi=words.size()-1,found=-1;
        while(lo<=hi){int mid=(lo+hi)>>>1;
            if(words.get(mid).startMs<=sourcePositionMs){found=mid;lo=mid+1;}
            else hi=mid-1;
        }
        if(found<0)return -1;
        Word w=words.get(found);
        // Do not highlight a word during long silence: allow 90ms decoding tolerance.
        if(w.durationMs>0&&sourcePositionMs>w.startMs+w.durationMs+90)return -1;
        return found;
    }
}
