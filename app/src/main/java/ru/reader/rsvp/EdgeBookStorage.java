package ru.reader.rsvp;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

/** Stable folder identity and cache-file identity for Edge TTS. */
public final class EdgeBookStorage {
    private EdgeBookStorage() {}
    static File booksRoot(Context context) {
        File root=context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (root==null) root=context.getFilesDir();
        return new File(root,"ChitalkaRSVP/Books");
    }
    private static String sha256(String value, int bytes) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();
            for(int i=0;i<Math.min(bytes,digest.length);i++)
                out.append(String.format(Locale.ROOT,"%02x",digest[i]&255));
            return out.toString();
        } catch(Exception e) {return Integer.toHexString(value.hashCode());}
    }
    private static final Map<String,File> folderIndex=new HashMap<>();
    private static long folderIndexTime=0L, folderRootStamp=Long.MIN_VALUE;
    private static String folderIndexRoot="";
    private static synchronized File existing(File root,String suffix){
        long now=android.os.SystemClock.elapsedRealtime();
        String path=root.getAbsolutePath();long stamp=root.lastModified();
        if(!path.equals(folderIndexRoot)||stamp!=folderRootStamp||now-folderIndexTime>5000L){
            folderIndex.clear();
            File[] dirs=root.listFiles(File::isDirectory);
            if(dirs!=null)for(File dir:dirs){String n=dir.getName();int pos=n.lastIndexOf("--");
                if(pos>=0)folderIndex.put(n.substring(pos),dir);
            }
            folderIndexTime=now;folderRootStamp=stamp;folderIndexRoot=path;
        }
        return folderIndex.get(suffix);
    }
    public static File bookFolder(Context context, String uri, String title) {
        String clean=title==null?"Книга":title.trim().replaceAll("[\\/:*?\"<>|\\p{Cntrl}]","_");
        if(clean.isEmpty())clean="Книга";
        if(clean.length()>65)clean=clean.substring(0,65);
        File root=booksRoot(context);
        String suffix="--"+sha256(uri,8);
        // Titles in library metadata and parsed EPUB/FB2 can differ. URI is the stable identity.
        File existing=existing(root,suffix);
        if(existing!=null)return existing;
        return new File(root,clean+suffix);
    }
    public static void removeBook(Context context,String uri) {
        final File root=booksRoot(context);
        final String suffix="--"+sha256(uri,8);
        new Thread(()->{
            File[] dirs=root.listFiles();
            if(dirs!=null)for(File dir:dirs)
                if(dir.isDirectory() && dir.getName().endsWith(suffix))deleteTree(dir);
        },"edge-remove-book-audio").start();
    }
    private static void deleteTree(File folder) {
        File[] files=folder.listFiles();
        if(files!=null)for(File f:files){if(f.isDirectory())deleteTree(f);else f.delete();}
        folder.delete();
    }
    public static String key(String text,String voice,String speed,String backend) {
        // Keep the historical hash namespace to reuse Edge MP3 downloaded by older app versions.
        final String historicalNamespace="mrt-prefetch-v1";
        return sha256(historicalNamespace+"\n"+voice+"\n"+speed+"\n"+backend+"\n"+text,32);
    }
}
