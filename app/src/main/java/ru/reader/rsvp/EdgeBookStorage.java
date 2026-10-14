package ru.reader.rsvp;

import android.content.Context;
import android.os.Environment;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Stable folder identity and cache-file identity for Edge TTS. */
public final class EdgeBookStorage {
    private EdgeBookStorage() {}
    private static File booksRoot(Context context) {
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
    public static File bookFolder(Context context, String uri, String title) {
        String clean=title==null?"Книга":title.trim().replaceAll("[\\/:*?\"<>|\\p{Cntrl}]","_");
        if(clean.isEmpty())clean="Книга";
        if(clean.length()>65)clean=clean.substring(0,65);
        return new File(booksRoot(context),clean+"--"+sha256(uri,8));
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
