package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Versioned compact index: stores book text ONCE and word offsets, not repeated word strings. */
public final class ReaderBookCache {
    private static final int MAGIC=0x52424932; // RBI2
    private static final int MAX_WORDS=2_500_000;
    public static final class CachedBook {
        public final String title,author,text;
        public final ReadingWordMap map;
        CachedBook(String title,String author,String text,ReadingWordMap map){
            this.title=title;this.author=author;this.text=text;this.map=map;
        }
    }
    private ReaderBookCache(){}
    private static String digest(String value){
        try{
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            byte[] data=hash.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(byte b:data)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            return out.toString();
        }catch(Exception e){return Integer.toHexString(value.hashCode());}
    }
    private static File file(Context context,Uri uri){
        File dir=new File(context.getFilesDir(),"reader-index");
        if(!dir.exists())dir.mkdirs();
        return new File(dir,digest(uri.toString())+".bin");
    }
    private static long sourceSize(Context context,Uri uri){
        try(Cursor c=context.getContentResolver().query(uri,new String[]{OpenableColumns.SIZE},null,null,null)){
            if(c!=null&&c.moveToFirst()&&!c.isNull(0))return c.getLong(0);
        }catch(Exception ignored){}
        return -1;
    }
    private static void writeString(DataOutputStream out,String value)throws IOException{
        byte[] bytes=(value==null?"":value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);out.write(bytes);
    }
    private static String readString(DataInputStream in,int limit)throws IOException{
        int len=in.readInt();
        if(len<0||len>limit)throw new IOException("Corrupt book cache");
        byte[] bytes=new byte[len];in.readFully(bytes);
        return new String(bytes,StandardCharsets.UTF_8);
    }
    public static CachedBook load(Context context,Uri uri){
        File f=file(context,uri);
        if(!f.exists())return null;
        try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(f)))){
            if(in.readInt()!=MAGIC)return null;
            long original=in.readLong(),actual=sourceSize(context,uri);
            if(original>=0&&actual>=0&&original!=actual)return null;
            String title=readString(in,2_000_000),author=readString(in,2_000_000),text=readString(in,60_000_000);
            int count=in.readInt();
            if(count<=0||count>MAX_WORDS)return null;
            int[] starts=new int[count],ends=new int[count];
            for(int i=0;i<count;i++){
                starts[i]=in.readInt();ends[i]=in.readInt();
                if(starts[i]<0||ends[i]>text.length()||ends[i]<=starts[i]||(i>0&&starts[i]<ends[i-1]))return null;
            }
            return new CachedBook(title,author,text,ReadingWordMap.fromOffsets(text,starts,ends));
        }catch(Exception e){return null;}
    }
    public static void store(Context context,Uri uri,String title,String author,String text,ReadingWordMap map){
        if(uri==null||text==null||map==null||map.size()==0)return;
        File destination=file(context,uri),temp=new File(destination.getParentFile(),destination.getName()+".tmp");
        try(DataOutputStream out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(temp)))){
            out.writeInt(MAGIC);out.writeLong(sourceSize(context,uri));
            writeString(out,title);writeString(out,author);writeString(out,text);
            out.writeInt(map.size());
            for(int i=0;i<map.size();i++){out.writeInt(map.starts[i]);out.writeInt(map.ends[i]);}
        }catch(Exception e){temp.delete();return;}
        if(!temp.renameTo(destination)){
            destination.delete();
            if(!temp.renameTo(destination))temp.delete();
        }
    }
}
