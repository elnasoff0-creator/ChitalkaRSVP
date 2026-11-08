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
    private static String sourceKey(MangaLibStorage.Job job){
        File f=new File(job.file);
        return job.id()+":"+f.getAbsolutePath()+":"+f.length()+":"+f.lastModified();
    }
    static void ensure(Context c,MangaLibStorage.Job job){
        ArrayList<MangaLibStorage.Job> available=MangaLibStorage.seriesJobs(c,job.slug);
        if(available.isEmpty())return;
        MangaLibStorage.Job earliest=available.get(0);
        File result=file(c,job.slug);
        File source=new File(earliest.file);
        File marker=new File(result.getAbsolutePath()+".source");
        String key=sourceKey(earliest);
        if(result.isFile()&&result.length()>100&&marker.isFile()){
            try{String previous;
                try(java.io.FileInputStream input=new java.io.FileInputStream(marker);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                    byte[] b=new byte[256];int n;while((n=input.read(b))>0&&out.size()<2048)out.write(b,0,n);
                    previous=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                }
                if(key.equals(previous))return;}catch(Exception ignored){}
        }
        Bitmap bitmap=firstPage(source);if(bitmap==null)return;
        File temp=new File(result.getAbsolutePath()+".tmp");
        try(FileOutputStream output=new FileOutputStream(temp)){
            bitmap.compress(Bitmap.CompressFormat.JPEG,85,output);
        }catch(Exception error){android.util.Log.w("MangaDownloadCover","Thumbnail",error);temp.delete();return;}
        if(!temp.renameTo(result)){
            if(result.exists()&&!result.delete()){temp.delete();return;}
            if(!temp.renameTo(result)){temp.delete();return;}
        }
        try(FileOutputStream out=new FileOutputStream(marker)){
            out.write(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }catch(Exception error){android.util.Log.w("MangaDownloadCover","Marker",error);}
        CoverImageLoader.invalidateLocal(result.toURI().toString());
    }
    /** Reads only the first image entry, not every manga page. */
    static Bitmap firstPage(File original){
        if(!original.isFile())return null;
        try(ZipFile zip=new ZipFile(original)){
            ArrayList<String> names=new ArrayList<>();Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();String name=e.getName();
                if(e.isDirectory()||name.startsWith("__MACOSX/")||name.startsWith(".")||name.contains("/."))continue;
                if(e.getSize()>0&&e.getSize()<15_000_000&&ComicLibrary.isImageName(name))names.add(name);
            }
            if(names.isEmpty())return null;
            Collections.sort(names,ComicLibrary::natural);
            ZipEntry first=zip.getEntry(names.get(0));
            byte[] image;
            try(java.io.InputStream in=zip.getInputStream(first);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(out.size()+n>15_000_000)return null;out.write(b,0,n);}
                image=out.toByteArray();
            }
            BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
            BitmapFactory.decodeByteArray(image,0,image.length,bounds);
            BitmapFactory.Options opts=new BitmapFactory.Options();int sample=1;
            while(bounds.outWidth/sample>400||bounds.outHeight/sample>600)sample*=2;
            opts.inSampleSize=sample;
            return BitmapFactory.decodeByteArray(image,0,image.length,opts);
        }catch(Exception error){android.util.Log.w("MangaDownloadCover","First page",error);return null;}
    }
    static void invalidate(Context c,String slug){File f=file(c,slug);f.delete();new File(f.getAbsolutePath()+".source").delete();}
}
