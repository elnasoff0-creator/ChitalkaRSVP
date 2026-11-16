package ru.reader.rsvp;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Streaming MP3 frame writer used ONLY by an explicit export to AudioLibrary.
 * Neither normal Edge synthesis nor playback uses this class. */
final class EdgeMp3ChapterExport {
    private EdgeMp3ChapterExport() {}
    static final class Format {
        int version=-1, sampleRate=-1, channels=-1;
        long frames=0, bytes=0;
    }
    private static void writeInt(OutputStream out,int n)throws IOException{
        out.write((n>>>24)&255);out.write((n>>>16)&255);out.write((n>>>8)&255);out.write(n&255);
    }
    private static void tag(ByteArrayOutputStream all,String frame,String value)throws IOException{
        if(value==null||value.isEmpty())return;
        byte[] str=value.getBytes(StandardCharsets.UTF_16);
        all.write(frame.getBytes(StandardCharsets.US_ASCII));writeInt(all,str.length+1);
        all.write(0);all.write(0);all.write(1);all.write(str);
    }
    static void writeMetadata(OutputStream out,String chapter,String book,int number)throws IOException{
        ByteArrayOutputStream data=new ByteArrayOutputStream();
        tag(data,"TIT2",chapter);tag(data,"TALB",book);tag(data,"TRCK",String.valueOf(number));
        int len=data.size();if(len>1024*1024)throw new IOException("Слишком длинные метаданные MP3");
        out.write(new byte[]{'I','D','3',3,0,0,(byte)((len>>>21)&127),
            (byte)((len>>>14)&127),(byte)((len>>>7)&127),(byte)(len&127)});
        data.writeTo(out);
    }
    private static void skip(InputStream in,long bytes)throws IOException{
        while(bytes>0){long passed=in.skip(bytes);if(passed==0){if(in.read()<0)throw new IOException("Обрезанный ID3-заголовок");passed=1;}bytes-=passed;}
    }
    /** Appends only MPEG Layer III frames, excluding fragment-local ID3 and Xing/Info frames.
     * Frame-by-frame streaming avoids buffering a whole chapter in RAM. */
    static void append(InputStream source,OutputStream out,Format format)throws IOException{
        BufferedInputStream input=new BufferedInputStream(source,32768);
        input.mark(10);byte[] id3=new byte[10];int n=0;
        while(n<id3.length){int got=input.read(id3,n,id3.length-n);if(got<0)break;n+=got;}
        if(n==10&&id3[0]=='I'&&id3[1]=='D'&&id3[2]=='3'){
            long size=((long)(id3[6]&127)<<21)|((long)(id3[7]&127)<<14)|
                ((long)(id3[8]&127)<<7)|(id3[9]&127);
            if(size>16*1024*1024L)throw new IOException("Некорректный ID3-заголовок фрагмента");
            skip(input,size+((id3[5]&16)!=0?10:0));
        }else input.reset();
        byte[] header=new byte[4];int hread=0,trash=0;long accepted=0;
        while(true){
            int next=input.read();if(next<0)break;
            header[0]=(byte)next;
            for(int i=1;i<4;i++){next=input.read();if(next<0){hread=-1;break;}header[i]=(byte)next;}
            if(hread==-1)break;
            int h=((header[0]&255)<<24)|((header[1]&255)<<16)|((header[2]&255)<<8)|(header[3]&255);
            int ver=(h>>>19)&3,layer=(h>>>17)&3,bit=(h>>>12)&15,sr=(h>>>10)&3;
            if((h&0xffe00000)!=0xffe00000||ver==1||layer!=1||bit==0||bit==15||sr==3){
                // Push three lookahead bytes back by resetting a short mark, without rescanning all audio.
                if(accepted>0 && ((header[0]=='T' && header[1]=='A' && header[2]=='G') ||
                    (header[0]=='A' && header[1]=='P' && header[2]=='E') ||
                    (header[0]==0 && header[1]==0)))break;
                throw new IOException("Повреждён MP3-фрагмент или неподдерживаемый формат кадра");
            }
            int[] rates=ver==3?new int[]{0,32,40,48,56,64,80,96,112,128,160,192,224,256,320}:
                new int[]{0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
            int[] samples={44100,48000,32000};int hz=samples[sr];if(ver==2)hz/=2;else if(ver==0)hz/=4;
            int channels=((h>>>6)&3)==3?1:2;
            int length=((ver==3?144:72)*rates[bit]*1000)/hz+((h>>>9)&1);
            if(length<24||length>2048)throw new IOException("Некорректная длина MP3-кадра");
            if(format.sampleRate==-1){format.sampleRate=hz;format.version=ver;format.channels=channels;}
            else if(format.sampleRate!=hz||format.version!=ver||format.channels!=channels)
                throw new IOException("Фрагменты главы имеют несовместимые параметры MP3");
            byte[] frame=new byte[length];System.arraycopy(header,0,frame,0,4);
            int count=4;while(count<length){int amount=input.read(frame,count,length-count);
                if(amount<0)throw new IOException("Обрезанный MP3-кадр");count+=amount;}
            boolean xing=false;
            if(accepted==0){for(int i=4;i+4<Math.min(length,160);i++)
                if((frame[i]=='X'&&frame[i+1]=='i'&&frame[i+2]=='n'&&frame[i+3]=='g')||
                   (frame[i]=='I'&&frame[i+1]=='n'&&frame[i+2]=='f'&&frame[i+3]=='o')){
                    xing=true;break;
                }}
            if(!xing){out.write(frame);format.bytes+=length;format.frames++;accepted++;}
            // Every next candidate is expected at the next precise frame boundary.
        }
        if(accepted==0)throw new IOException("В MP3-фрагменте нет звуковых кадров");
    }
}
