package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Lightweight on-device cache for parsed books and word maps to open reader modes faster. */
public final class ReaderBookCache {
    public static final class CachedBook {
        public final String title;
        public final String author;
        public final String text;
        public final ReadingWordMap map;
        CachedBook(String title,String author,String text,ReadingWordMap map){
            this.title=title;this.author=author;this.text=text;this.map=map;
        }
    }
    private ReaderBookCache(){}

    private static File file(Context context,Uri uri){
        File dir=new File(context.getCacheDir(),"reader-book-cache");
        if(!dir.exists()) dir.mkdirs();
        return new File(dir,key(uri.toString())+".bin");
    }
    private static String key(String value){
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            byte[] bytes=md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte b:bytes) out.append(String.format(java.util.Locale.ROOT,"%02x",b));
            return out.toString();
        }catch(Exception ex){
            return Integer.toHexString(value.hashCode());
        }
    }
    private static void writeString(DataOutputStream out,String value) throws java.io.IOException{
        byte[] data=(value==null?"":value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(data.length);
        out.write(data);
    }
    private static String readString(DataInputStream in) throws java.io.IOException{
        int length=in.readInt();
        if(length<0||length>100_000_000) throw new java.io.IOException("bad length");
        byte[] data=new byte[length];
        in.readFully(data);
        return new String(data,StandardCharsets.UTF_8);
    }
    public static CachedBook load(Context context,Uri uri){
        File f=file(context,uri);
        if(!f.exists()) return null;
        try(DataInputStream in=new DataInputStream(new FileInputStream(f))){
            if(in.readInt()!=1) return null;
            String title=readString(in);
            String author=readString(in);
            String text=readString(in);
            int count=in.readInt();
            if(count<=0 || count>2_000_000) return null;
            String[] words=new String[count];
            int[] starts=new int[count];
            int[] ends=new int[count];
            for(int i=0;i<count;i++){
                words[i]=readString(in);
                starts[i]=in.readInt();
                ends[i]=in.readInt();
            }
            return new CachedBook(title,author,text,ReadingWordMap.fromArrays(words,starts,ends));
        }catch(Exception ex){
            return null;
        }
    }
    public static void store(Context context,Uri uri,String title,String author,String text,ReadingWordMap map){
        if(uri==null||map==null||map.size()==0||text==null) return;
        File f=file(context,uri);
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(f))){
            out.writeInt(1);
            writeString(out,title);
            writeString(out,author);
            writeString(out,text);
            out.writeInt(map.words.length);
            for(int i=0;i<map.words.length;i++){
                writeString(out,map.words[i]);
                out.writeInt(map.starts[i]);
                out.writeInt(map.ends[i]);
            }
        }catch(Exception ignored){}
    }
}
