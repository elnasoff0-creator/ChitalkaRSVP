package ru.reader.rsvp;
import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Stable, local thumbnails list for each audiobook; manual artwork is copied into app storage. */
final class AudiobookCoverChoices {
    private AudiobookCoverChoices(){}
    private static String suffix(String id){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(id.getBytes("UTF-8"));StringBuilder out=new StringBuilder();for(int i=0;i<10;i++)out.append(String.format(java.util.Locale.ROOT,"%02x",hash[i]&255));return out.toString();}catch(Exception e){return Integer.toHexString(id.hashCode());}}
    static ArrayList<String> options(Context ctx,String id){
        LinkedHashSet<String> all=new LinkedHashSet<>();
        String current=ctx.getSharedPreferences("reader",0).getString("audio_cover:"+id,"");
        if(!current.isEmpty())all.add(current);
        String downloaded=AudiobookCoverStore.localUri(ctx,id);if(!downloaded.isEmpty())all.add(downloaded);
        File folder=new File(ctx.getFilesDir(),"audiobook-covers/"+suffix(id));
        File[] files=folder.listFiles((d,name)->name.endsWith(".jpg")||name.endsWith(".png")||name.endsWith(".webp"));
        if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File f:files)all.add(Uri.fromFile(f).toString());}
        return new ArrayList<>(all);
    }
    static String importImage(Context ctx,String id,Uri input)throws IOException {
        File folder=new File(ctx.getFilesDir(),"audiobook-covers/"+suffix(id));
        if(!folder.exists()&&!folder.mkdirs())throw new IOException("Не создать папку");
        String mime=ctx.getContentResolver().getType(input);
        if(mime==null||!mime.startsWith("image/"))throw new IOException("Нужен файл изображения");
        File file=new File(folder,"cover-"+System.currentTimeMillis()+".img");
        // Normalize to JPEG to avoid depending on a temporary SAF document grant.
        android.graphics.Bitmap bitmap;
        try(InputStream in=ctx.getContentResolver().openInputStream(input)){
            if(in==null)throw new IOException("Файл недоступен");
            android.graphics.BitmapFactory.Options opt=new android.graphics.BitmapFactory.Options();opt.inSampleSize=2;
            bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opt);
        }
        if(bitmap==null)throw new IOException("Некорректное изображение");
        file=new File(folder,"cover-"+System.currentTimeMillis()+".jpg");
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,90,out);out.flush();out.getFD().sync();}finally{bitmap.recycle();}
        return Uri.fromFile(file).toString();
    }
}
