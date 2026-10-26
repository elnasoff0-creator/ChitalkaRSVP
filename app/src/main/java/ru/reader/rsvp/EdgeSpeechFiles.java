package ru.reader.rsvp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Stores only completed, validated MP3 data; atomically publishes files.
 * No growing files are ever passed to ExoPlayer and incomplete recordings
 * cannot pollute the permanent cache.
 */
final class EdgeSpeechFiles {
    private EdgeSpeechFiles(){}
    static File save(File directory,String key,String text,byte[] data,
                     List<EdgeWordTimings.Boundary> metadata)throws IOException {
        EdgeAudioFormat format=data==null?null:EdgeAudioFormat.identify(data,data.length);
        if(data==null||data.length<1500||format==null||!".mp3".equals(format.extension))
            throw new IOException("Incomplete or invalid MP3 response");
        if(!key.matches("[a-f0-9]{20,80}"))throw new IOException("Invalid MP3 cache key");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create MP3 cache");
        File file=new File(directory,key+".mp3");
        if(file.isFile()&&file.length()>1500)return file;
        File part=new File(directory,key+".speech.part");
        try{
            try(FileOutputStream out=new FileOutputStream(part,false)){
                out.write(data);out.getFD().sync();
            }
            if(file.exists()&&!file.delete())throw new IOException("Cannot replace incomplete MP3");
            if(!part.renameTo(file))throw new IOException("Cannot finalize MP3");
            // WordBoundary remains compatible with EdgeOnlineActivity highlighting.
            EdgeWordTimings.store(file,text,metadata);
            return file;
        }finally{part.delete();}
    }
}
