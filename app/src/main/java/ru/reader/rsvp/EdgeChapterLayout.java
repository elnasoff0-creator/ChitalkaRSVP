package ru.reader.rsvp;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/** Friendly per-chapter view over the immutable hash-keyed Edge playback cache.
 * Hash filenames remain the canonical source to preserve older downloads.
 * Uses hard links when possible (no extra audio bytes); falls back to copies. */
final class EdgeChapterLayout {
    private EdgeChapterLayout(){}
    private static String safe(String s){String t=s==null?"Глава":s.replaceAll("[\\/:*?\"<>|\\p{Cntrl}]","_").trim();if(t.isEmpty())t="Глава";return t.length()>53?t.substring(0,53):t;}
    private static void cloneFile(File source,File target) throws Exception {
        if(!source.isFile()||source.length()<=1500)return;
        if(target.isFile()&&target.length()==source.length())return;
        File temp=new File(target.getParentFile(),target.getName()+".new");
        if(android.os.Build.VERSION.SDK_INT>=26){try{java.nio.file.Files.createLink(temp.toPath(),source.toPath());}catch(Exception ignored){}}
        if(!temp.isFile())try(FileInputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(temp)){
            byte[] buf=new byte[32768];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
        }
        if(target.isFile())target.delete();if(!temp.renameTo(target))temp.delete();
    }
    static void publish(EdgeFullBookStore.Job job,JSONArray queue,BookChapterIndex chapters,ArrayList<Integer> starts){
        if(job==null||queue==null)return;
        try{
            File chapterRoot=new File(job.mp3Folder(),"Главы");chapterRoot.mkdirs();
            JSONArray mapping=new JSONArray();HashMap<Integer,Integer> order=new HashMap<>();
            for(int i=0;i<queue.length();i++){
                JSONObject item=queue.optJSONObject(i);if(item==null)continue;
                String key=item.optString("key","");if(!key.matches("[0-9a-f]{64}"))continue;
                int word=item.optInt("start",0);int charIndex=word>=0&&word<starts.size()?starts.get(word):0;
                int chapter=0;
                if(chapters!=null)for(int c=0;c<chapters.size();c++)if(chapters.get(c).startChar<=charIndex)chapter=c;else break;
                String chapterTitle=chapters!=null&&chapters.size()>0?chapters.get(chapter).title:"Без оглавления";
                String dirName=String.format(Locale.ROOT,"%03d_%s",chapter+1,safe(chapterTitle));
                File folder=new File(chapterRoot,dirName);folder.mkdirs();
                int sequence=order.getOrDefault(chapter,0)+1;order.put(chapter,sequence);
                String filename=String.format(Locale.ROOT,"%04d.mp3",sequence);
                File original=new File(job.mp3Folder(),key+".mp3");
                if(EdgeFullBookStore.ready(original)){
                    cloneFile(original,new File(folder,filename));
                    File wordTiming=EdgeWordTimings.sidecar(original);
                    if(wordTiming.isFile()){
                        File timingDst=new File(folder,filename+".words.json");
                        if(!timingDst.isFile()||timingDst.length()!=wordTiming.length()){
                            File tmp=new File(folder,filename+".words.json.part");
                            try(FileInputStream in=new FileInputStream(wordTiming);FileOutputStream out=new FileOutputStream(tmp)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
                            if(timingDst.exists())timingDst.delete();tmp.renameTo(timingDst);
                        }
                    }
                }
                JSONObject row=new JSONObject();row.put("chapter",chapter+1).put("chapter_title",chapterTitle)
                    .put("number",sequence).put("filename",dirName+"/"+filename).put("key",key)
                    .put("start_word",word).put("end_word",item.optInt("end",word));mapping.put(row);
            }
            File dest=new File(chapterRoot,"index.json"),tmp=new File(chapterRoot,"index.json.partial");
            try(FileOutputStream out=new FileOutputStream(tmp)){out.write(mapping.toString(2).getBytes(StandardCharsets.UTF_8));}
            if(dest.exists())dest.delete();tmp.renameTo(dest);
        }catch(Exception ex){android.util.Log.w("EdgeChapterLayout","Chapter export unavailable",ex);}
    }
}
