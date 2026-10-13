package ru.reader.rsvp;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;

/** Identifies the encoded bytes delivered to AudioContext.decodeAudioData().
 * They are NOT guaranteed to be MP3 just because they were captured from MRT.
 * Keep original data; do not transcode or append misleading ".mp3" extensions.
 */
public final class MrtAudioFormat {
    public final String label;
    public final String extension;
    public final String mime;
    private MrtAudioFormat(String label, String extension, String mime) {
        this.label=label;this.extension=extension;this.mime=mime;
    }
    private static final MrtAudioFormat MP3=new MrtAudioFormat("MP3", ".mp3", "audio/mpeg");
    private static final MrtAudioFormat WAV=new MrtAudioFormat("WAV", ".wav", "audio/wav");
    private static final MrtAudioFormat OGG=new MrtAudioFormat("OGG", ".ogg", "audio/ogg");
    private static final MrtAudioFormat MP4=new MrtAudioFormat("MP4/AAC", ".m4a", "audio/mp4");
    private static final MrtAudioFormat AAC=new MrtAudioFormat("AAC/ADTS", ".aac", "audio/aac");
    private static final MrtAudioFormat WEBM=new MrtAudioFormat("WebM", ".webm", "audio/webm");
    private static final MrtAudioFormat FLAC=new MrtAudioFormat("FLAC", ".flac", "audio/flac");

    public static final String[] EXTENSIONS={".mp3",".wav",".ogg",".m4a",".aac",".webm",".flac"};
    public static MrtAudioFormat forFilename(String filename) {
        if(filename==null)return null;
        String name=filename.toLowerCase(Locale.ROOT);
        for(MrtAudioFormat type:new MrtAudioFormat[]{MP3,WAV,OGG,MP4,AAC,WEBM,FLAC})
            if(name.endsWith(type.extension))return type;
        return null;
    }
    private static boolean prefix(byte[] bytes,int n,String value) {
        if(n<value.length())return false;
        for(int i=0;i<value.length();i++)if((bytes[i]&255)!=(value.charAt(i)&255))return false;
        return true;
    }
    private static boolean mpegFrame(byte[] b,int i,int n) {
        if(i+4>n)return false;
        int x=b[i]&255,y=b[i+1]&255,z=b[i+2]&255;
        if(x!=255||(y&0xe0)!=0xe0)return false;
        int version=(y>>3)&3,layer=(y>>1)&3;
        return version!=1&&layer==1&&(z>>4&15)!=0&&(z>>4&15)!=15&&((z>>2)&3)!=3;
    }
    public static MrtAudioFormat identify(byte[] bytes,int n) {
        if(bytes==null||n<4)return null;
        if(prefix(bytes,n,"ID3"))return MP3;
        if(prefix(bytes,n,"RIFF")&&n>=12&&bytes[8]=='W'&&bytes[9]=='A'&&bytes[10]=='V'&&bytes[11]=='E')return WAV;
        if(prefix(bytes,n,"OggS"))return OGG;
        if(prefix(bytes,n,"fLaC"))return FLAC;
        if(n>=8&&bytes[0]==0x1a&&(bytes[1]&255)==0x45&&(bytes[2]&255)==0xdf&&(bytes[3]&255)==0xa3)return WEBM;
        if(n>=12&&bytes[4]=='f'&&bytes[5]=='t'&&bytes[6]=='y'&&bytes[7]=='p')return MP4;
        if(n>=7&&(bytes[0]&255)==0xff&&((bytes[1]&255)&0xf6)==0xf0)return AAC;
        // Tolerate short ID3/padding bytes before the first MP3 frame.
        for(int i=0;i<Math.min(n-3,64);i++)if(mpegFrame(bytes,i,n))return MP3;
        return null;
    }
    public static MrtAudioFormat identify(File file) throws IOException {
        try(FileInputStream in=new FileInputStream(file)) {
            byte[] head=new byte[8192];int total=0,n;
            while(total<head.length&&(n=in.read(head,total,head.length-total))>0)total+=n;
            return identify(head,total);
        }
    }
    public static String signature(File file) {
        try(FileInputStream in=new FileInputStream(file)){
            byte[] head=new byte[16];int n=in.read(head);
            StringBuilder out=new StringBuilder();
            for(int i=0;i<n;i++)out.append(String.format(Locale.ROOT,"%02X",head[i]&255)).append(i+1==n?"":" ");
            return out.toString();
        }catch(Exception ex){return "нет доступа к данным";}
    }
}
