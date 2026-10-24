package ru.reader.rsvp;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.AudioFormat;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Detects ONLY the trailing silence of a complete Edge sentence.
 *  Never edits or re-encodes the MP3 and never alters its WordBoundary timestamps.
 *  Run detectAndStore on a background executor; decoding can be expensive.
 */
final class EdgeTrailingSilence {
    private EdgeTrailingSilence() {}
    private static File metadata(File audio){return new File(audio.getAbsolutePath()+".tail.json");}
    private static boolean endsSentence(String text) {
        return text!=null && text.trim().matches("(?s).*[.!?…][\\s\\\"»”’)]*");
    }
    static long clippedEnd(File audio){
        try{
            File data=metadata(audio);
            if(!data.isFile()||data.length()>2048)return -1;
            byte[] bytes=new byte[(int)data.length()];
            try(java.io.FileInputStream input=new java.io.FileInputStream(data)){
                int at=0,n;
                while(at<bytes.length&&(n=input.read(bytes,at,bytes.length-at))>0)at+=n;
                if(at!=bytes.length)return -1;
            }
            JSONObject obj=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(!obj.optString("source").equals("trailing-silence-v1"))return -1;
            long end=obj.optLong("endMs",-1);
            if(obj.optLong("audioBytes",-1)!=audio.length())return -1;
            return end>0?end:-1;
        }catch(Exception ignored){return -1;}
    }
    static void detectAndStore(File mp3,String text) {
        if(mp3==null||!mp3.isFile()||!endsSentence(text))return;
        long end=-1;
        try { end=detect(mp3,text); }catch(Exception ignored){}
        if(end<0)return; // leave the original alone if the analysis is uncertain
        File out=metadata(mp3),part=new File(out.getAbsolutePath()+".part");
        try(FileOutputStream stream=new FileOutputStream(part)){
            JSONObject object=new JSONObject();object.put("source","trailing-silence-v1");
            object.put("endMs",end);object.put("audioBytes",mp3.length());
            stream.write(object.toString().getBytes(StandardCharsets.UTF_8));stream.getFD().sync();
            if(out.exists())out.delete();part.renameTo(out);
        }catch(Exception ignored){part.delete();}
    }
    /** Full PCM decode is short for 600-character sentences. The last loud audio bin
     *  is located with an absolute + relative RMS threshold; do not cut the tail of a word.
     */
    private static long detect(File mp3,String text) throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try {
            extractor.setDataSource(mp3.getAbsolutePath());
            int track=-1;MediaFormat input=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                String mime=f.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){track=i;input=f;break;}
            }
            if(track<0||input==null)return -1;
            extractor.selectTrack(track);
            String mime=input.getString(MediaFormat.KEY_MIME);
            codec=MediaCodec.createDecoderByType(mime);codec.configure(input,null,null,0);codec.start();
            boolean inEnd=false,outEnd=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            ArrayList<long[]> bins=new ArrayList<>();long maxRms=0;
            int loops=0;
            while(!outEnd&&loops++<10000){
                if(!inEnd){
                    int id=codec.dequeueInputBuffer(10000);
                    if(id>=0){ByteBuffer buf=codec.getInputBuffer(id);
                        if(buf==null)return -1;
                        buf.clear();int size=extractor.readSampleData(buf,0);
                        if(size<0){codec.queueInputBuffer(id,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inEnd=true;}
                        else{codec.queueInputBuffer(id,0,size,extractor.getSampleTime(),0);extractor.advance();}
                    }
                }
                int out=codec.dequeueOutputBuffer(info,10000);
                if(out>=0){
                    if(info.size>0){
                        MediaFormat fmt=codec.getOutputFormat();
                        int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):0;
                        int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):0;
                        int pcm=fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)?fmt.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT;
                        if(rate<=0||channels<=0||pcm!=AudioFormat.ENCODING_PCM_16BIT)return -1;
                        ByteBuffer sound=codec.getOutputBuffer(out);
                        if(sound!=null){
                            sound.position(info.offset);sound.limit(info.offset+info.size);
                            ByteBuffer pcmBuffer=sound.slice().order(ByteOrder.LITTLE_ENDIAN);
                            int samples=info.size/2,binFrames=Math.max(1,rate/50); // ~20 ms
                            int frameCount=samples/channels;
                            for(int frame=0;frame<frameCount;frame+=binFrames){
                                int limit=Math.min(frameCount,frame+binFrames);
                                double energy=0;int count=0;
                                for(int j=frame;j<limit;j++)for(int ch=0;ch<channels;ch++){
                                    int pos=(j*channels+ch)*2;
                                    if(pos+1>=pcmBuffer.limit())continue;
                                    int sample=pcmBuffer.getShort(pos);
                                    energy+=(double)sample*sample;count++;
                                }
                                if(count==0)continue;
                                long rms=Math.round(Math.sqrt(energy/count));
                                long atMs=info.presentationTimeUs/1000L+Math.round(frame*1000.0/rate);
                                bins.add(new long[]{atMs,rms});maxRms=Math.max(maxRms,rms);
                            }
                        }
                    }
                    outEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    codec.releaseOutputBuffer(out,false);
                }
            }
            if(!outEnd||bins.isEmpty()||maxRms<150)return -1;
            MediaMetadataRetriever retriever=new MediaMetadataRetriever();long duration;
            try{retriever.setDataSource(mp3.getAbsolutePath());duration=Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));}
            finally{retriever.release();}
            if(duration<1500)return -1;
            long threshold=Math.max(175,Math.round(maxRms*.045));
            long lastAudible=0;
            for(long[] bin:bins)if(bin[1]>threshold)lastAudible=bin[0];
            if(lastAudible<=0)return -1;
            // The last WordBoundary includes a duration when Edge supplies one.
            // Retain a generous guard after it as well as after decoded speech.
            long lastBoundaryEnd=0;
            List<EdgeWordTimings.Word> words=EdgeWordTimings.read(mp3,text);
            for(EdgeWordTimings.Word word:words)
                lastBoundaryEnd=Math.max(lastBoundaryEnd,word.startMs+Math.max(0,word.durationMs));
            long finish=Math.max(lastAudible+400,lastBoundaryEnd+350);
            // Do not clip anything if there is under 650ms of confirmed tail silence.
            if(finish>=duration-650 || finish>=duration)return -1;
            return Math.max(1,finish);
        }finally {
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
            try{extractor.release();}catch(Exception ignored){}
        }
    }
}
