package ru.reader.rsvp;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.PlaybackParams;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Decode live Edge MP3 frames to PCM as they arrive, and output with AudioTrack.
 * This avoids parsing a growing MP3 resource via Media3. Original MP3 cache and
 * Edge WordBoundary metadata are completely independent of the PCM sink.
 */
final class EdgePcmStream {
    interface Callback {void completed(String error);}
    private final EdgeLiveAudio.Buffer source;
    private final Callback callback;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean stopped,paused=true,started;
    private volatile float speed=1f;
    private volatile int rate=24000;
    private AudioTrack track;
    private Thread thread;
    EdgePcmStream(EdgeLiveAudio.Buffer source,Callback callback){this.source=source;this.callback=callback;}
    synchronized void play(){
        if(stopped)return;
        paused=false;
        if(track!=null&&track.getPlayState()!=AudioTrack.PLAYSTATE_PLAYING)try{track.play();}catch(Exception ignored){}
        if(!started){started=true;thread=new Thread(this::run,"EdgePcmDecoder");thread.setDaemon(true);thread.start();}
        notifyAll();
    }
    synchronized void pause(){paused=true;if(track!=null)try{track.pause();}catch(Exception ignored){}}
    synchronized boolean isPlaying(){return started&&!paused&&!stopped;}
    void setSpeed(float value){speed=Math.max(.5f,Math.min(3f,value));AudioTrack t=track;
        if(t!=null&&Build.VERSION.SDK_INT>=23)try{t.setPlaybackParams(new PlaybackParams().setSpeed(speed));}catch(Exception ignored){}
    }
    synchronized void stop(){stopped=true;paused=true;notifyAll();source.wake();
        if(track!=null)try{track.pause();track.flush();}catch(Exception ignored){}
    }
    long positionMs(){AudioTrack t=track;return t==null?0:((t.getPlaybackHeadPosition()&0xffffffffL)*1000L/Math.max(1,rate));}
    private static final int[] BITRATE_MPEG1={0,32,40,48,56,64,80,96,112,128,160,192,224,256,320,0};
    private static final int[] BITRATE_MPEG2={0,8,16,24,32,40,48,56,64,80,96,112,128,144,160,0};
    private static int frameLength(byte[] b,int offset){
        if(offset+4>b.length)return -1;
        int a=b[offset]&255,c=b[offset+1]&255,d=b[offset+2]&255;
        int version=(c>>3)&3,layer=(c>>1)&3,bitrate=(d>>4)&15,frequency=(d>>2)&3;
        if(a!=255||(c&0xe0)!=0xe0||version==1||layer!=1||bitrate==0||bitrate==15||frequency==3)return -1;
        int sample=frequency==0?44100:frequency==1?48000:32000;
        if(version==2)sample/=2;else if(version==0)sample/=4;
        int kbps=(version==3?BITRATE_MPEG1:BITRATE_MPEG2)[bitrate];
        return ((version==3?144000:72000)*kbps/sample)+((d>>1)&1);
    }
    private void ensureOutput(int sampleRate,int channels){
        if(track!=null)return;
        rate=sampleRate;
        int channel=channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO;
        int buffer=Math.max(8192,AudioTrack.getMinBufferSize(sampleRate,channel,AudioFormat.ENCODING_PCM_16BIT)*2);
        track=new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(channel).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(buffer).build();
        setSpeed(speed);
        if(!paused)track.play();
    }
    private void waitForPlay() throws InterruptedException{
        synchronized(this){while(paused&&!stopped)wait(250);}
    }
    private void run(){
        MediaCodec decoder=null;String problem=null;long byteOffset=0;long ptsUs=0;
        int samplerate=24000;boolean eos=false,outputEnded=false;byte[] pending=new byte[32768];int length=0;
        long firstPcmAt=0L;long queuedFrames=0L;boolean eosQueued=false;
        byte[] incoming=new byte[8192];int skippedId3=0;
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
        try{
            decoder=MediaCodec.createDecoderByType("audio/mpeg");
            MediaFormat format=MediaFormat.createAudioFormat("audio/mpeg",24000,1);
            decoder.configure(format,null,null,0);decoder.start();
            while(!stopped){
                waitForPlay();if(stopped)break;
                if(!eos&&length<512){
                    int count=source.readForPcm(byteOffset,incoming,0,incoming.length,()->stopped);
                    if(count>0){
                        if(length+count>pending.length)pending=Arrays.copyOf(pending,Math.max(length+count,pending.length*2));
                        System.arraycopy(incoming,0,pending,length,count);length+=count;byteOffset+=count;
                    }else if(count<0)eos=true;
                }
                if(length>=10&&pending[0]=='I'&&pending[1]=='D'&&pending[2]=='3'){
                    int id3=10+((pending[6]&127)<<21)+((pending[7]&127)<<14)+((pending[8]&127)<<7)+(pending[9]&127);
                    skippedId3=Math.max(skippedId3,id3);
                }
                if(skippedId3>0){
                    int remove=Math.min(skippedId3,length);
                    System.arraycopy(pending,remove,pending,0,length-remove);length-=remove;skippedId3-=remove;
                }
                int offset=0,frame=-1;
                while(length-offset>=4){frame=frameLength(pending,offset);if(frame>0)break;offset++;}
                if(offset>0){System.arraycopy(pending,offset,pending,0,length-offset);length-=offset;}
                // Do not dequeue a codec input slot until a COMPLETE MP3 frame exists:
                // a dequeued-but-unqueued slot otherwise starves the decoder.
                if(!eosQueued&&((frame>0&&length>=frame)||eos)){
                    int input=decoder.dequeueInputBuffer(2000);
                    if(input>=0){
                        if(frame>0&&length>=frame){
                            ByteBuffer dst=decoder.getInputBuffer(input);
                            if(dst==null||dst.capacity()<frame)throw new IOException("MP3 frame exceeds codec input buffer");
                            dst.clear();dst.put(pending,0,frame);decoder.queueInputBuffer(input,0,frame,ptsUs,0);
                            ptsUs+=576_000_000L/24000;
                            System.arraycopy(pending,frame,pending,0,length-frame);length-=frame;
                        }else if(eos){
                            length=0;decoder.queueInputBuffer(input,0,0,ptsUs,MediaCodec.BUFFER_FLAG_END_OF_STREAM);eosQueued=true;
                        }
                    }
                }
                while(!stopped){
                    int out=decoder.dequeueOutputBuffer(info,1000);
                    if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat f=decoder.getOutputFormat();
                        samplerate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        ensureOutput(samplerate,channels);
                    }else if(out>=0){
                        ByteBuffer data=decoder.getOutputBuffer(out);
                        if(data!=null && info.size>0){
                            if(track==null)ensureOutput(samplerate,1);
                            if(firstPcmAt==0){firstPcmAt=android.os.SystemClock.elapsedRealtime();
                                Log.i("EdgeTtsLatency","first PCM output ready");}
                            data.position(info.offset);data.limit(info.offset+info.size);
                            // AudioTrack.write blocking is intentionally on the decoder thread.
                            while(data.hasRemaining()&&!stopped){
                                waitForPlay();if(stopped)break;
                                int n=track.write(data,data.remaining(),AudioTrack.WRITE_BLOCKING);
                                if(n>0)queuedFrames+=n/(2L*(track.getChannelCount()));
                                if(n<0)throw new IOException("AudioTrack PCM error "+n);
                            }
                        }
                        boolean last=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        decoder.releaseOutputBuffer(out,false);
                        if(last){outputEnded=true;break;}
                    }else break;
                }
                if(outputEnded)break;
            }
        }catch(Exception ex){if(!stopped)problem=ex.toString();Log.w("EdgePcm","Streaming decoder",ex);}
        finally{
            if(decoder!=null){try{decoder.stop();}catch(Exception ignored){}try{decoder.release();}catch(Exception ignored){}}
            AudioTrack t=track;track=null;
            if(t!=null && !stopped && problem==null){
                // Drain the PCM frames already handed to AudioTrack before moving
                // the book cursor to the next sentence. A fixed 65 ms truncated
                // speech on slow output devices, especially at lower WPM.
                long deadline=android.os.SystemClock.elapsedRealtime()+Math.min(5000L,
                    500L+(queuedFrames*1000L/Math.max(1,rate)));
                while(!stopped && android.os.SystemClock.elapsedRealtime()<deadline){
                    long rendered=t.getPlaybackHeadPosition()&0xffffffffL;
                    if(rendered>=queuedFrames)break;
                    if(paused){ // Do not keep a finished decoder forever if paused.
                        try{Thread.sleep(50);}catch(InterruptedException ex){Thread.currentThread().interrupt();break;}
                    }else{
                        try{Thread.sleep(16);}catch(InterruptedException ex){Thread.currentThread().interrupt();break;}
                    }
                }
            }
            if(t!=null){try{t.stop();}catch(Exception ignored){}try{t.release();}catch(Exception ignored){}}
            if(!stopped){final String fail=problem;main.post(()->callback.completed(fail));}
        }
    }
}
