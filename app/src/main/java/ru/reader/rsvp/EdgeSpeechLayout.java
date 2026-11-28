package ru.reader.rsvp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Stable per-book audio grouping. Transport latency never invalidates ready MP3.
 * Existing pre-v182 cache keeps the 800/800 layout wherever sentence boundaries
 * permit. Uses java.io only so the minimum supported Android API remains 24.
 */
final class EdgeSpeechLayout {
    private static final String NAME="edge-block-layout-v182.txt";
    final int first,normal;
    final boolean legacyParagraphs;
    private EdgeSpeechLayout(int first,int normal){this(first,normal,false);}
    private EdgeSpeechLayout(int first,int normal,boolean legacy){this.first=first;this.normal=normal;legacyParagraphs=legacy;}
    static EdgeSpeechLayout current(){
        return new EdgeSpeechLayout(EdgeAdaptiveBlocks.priorityTarget(),
            EdgeAdaptiveBlocks.normalTarget());
    }
    private static String readSmall(File file)throws Exception {
        try(FileInputStream input=new FileInputStream(file);
                ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
            byte[] buffer=new byte[64];int count;
            while((count=input.read(buffer))>=0){
                if(bytes.size()+count>80)throw new IllegalStateException("Oversized layout metadata");
                bytes.write(buffer,0,count);
            }
            return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    static synchronized EdgeSpeechLayout forFolder(File folder){
        if(folder==null)return current();
        File marker=new File(folder,NAME);
        try{
            if(marker.isFile()){
                String[] parts=readSmall(marker).trim().split(",");
                int first=Integer.parseInt(parts[0]),normal=Integer.parseInt(parts[1]);
                if(first>=300&&first<=800&&normal>=600&&normal<=800){
                    if(parts.length>=3)return new EdgeSpeechLayout(first,normal,"legacy".equals(parts[2]));
                    // A legacy marker can refer to externally archived audio, even when
                    // the local folder is empty. Do not silently change its grouping.
                    return new EdgeSpeechLayout(first,normal,true);
                }
            }
        }catch(Exception ignored){}
        File[] mp3=folder.listFiles((parent,name)->name.endsWith(".mp3"));
        final boolean oldAudio=mp3!=null&&mp3.length>0;
        EdgeSpeechLayout layout=oldAudio?new EdgeSpeechLayout(800,800,true):current();
        try{
            if(folder.isDirectory()||folder.mkdirs()){
                File pending=new File(folder,NAME+".pending");
                try(FileOutputStream stream=new FileOutputStream(pending)){
                    stream.write((layout.first+","+layout.normal+","+(layout.legacyParagraphs?"legacy":"v198")+"\n")
                        .getBytes(StandardCharsets.UTF_8));
                    stream.getFD().sync();
                }
                if(!pending.renameTo(marker)){
                    // A previous marker is authoritative if another session wrote it.
                    if(marker.isFile())pending.delete();
                    else {
                        try(FileOutputStream stream=new FileOutputStream(marker)){
                            stream.write((layout.first+","+layout.normal+","+(layout.legacyParagraphs?"legacy":"v198")+"\n")
                                .getBytes(StandardCharsets.UTF_8));
                        }
                        pending.delete();
                    }
                }
            }
        }catch(Exception ignored){}
        return layout;
    }
}
