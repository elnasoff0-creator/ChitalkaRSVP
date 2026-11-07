package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;

/** Durable small cover, generated from the first safely downloaded CBZ page. */
final class MangaDownloadCover {
    private MangaDownloadCover(){}
    static File file(Context c,String slug){File dir=new File(c.getFilesDir(),"manga_download_covers");dir.mkdirs();return new File(dir,Integer.toHexString(slug.hashCode())+".jpg");}
    static void ensure(Context c,MangaLibStorage.Job job){
        File result=file(c,job.slug);if(result.isFile()&&result.length()>100)return;
        File original=new File(job.file);if(!original.isFile())return;
        File temp=new File(result.getAbsolutePath()+".tmp");
        try(ZipFile zip=new ZipFile(original)){
            ArrayList<String> names=new ArrayList<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){ZipEntry e=entries.nextElement();if(!e.isDirectory()&&e.getSize()>0&&e.getSize()<15_000_000&&ComicLibrary.isImageName(e.getName()))names.add(e.getName());}
            if(names.isEmpty())return;
            Collections.sort(names,ComicLibrary::natural);
            ZipEntry first=zip.getEntry(names.get(0));
            byte[] image;
            try(java.io.InputStream in=zip.getInputStream(first);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>15_000_000)return;out.write(b,0,n);}image=out.toByteArray();
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(image,0,image.length,bounds);
            BitmapFactory.Options opts=new BitmapFactory.Options();int sample=1;while(bounds.outWidth/sample>400||bounds.outHeight/sample>600)sample*=2;opts.inSampleSize=sample;
            Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length,opts);if(bitmap==null)return;
            try(FileOutputStream output=new FileOutputStream(temp)){bitmap.compress(Bitmap.CompressFormat.JPEG,85,output);}
            if(!temp.renameTo(result))temp.delete();
        }catch(Exception error){android.util.Log.w("MangaDownloadCover","Could not build thumbnail",error);temp.delete();}
    }
    static void invalidate(Context c,String slug){file(c,slug).delete();}
}
