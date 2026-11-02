package ru.reader.rsvp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/** Background, bounded first-page thumbnails. No network access; safely loads SAF or offline CBZ. */
final class ComicCovers {
    private ComicCovers(){}
    static File cached(Context c,String uri){File directory=new File(c.getCacheDir(),"comic_cover_cache");
        if(!directory.exists())directory.mkdirs();return new File(directory,Integer.toHexString(uri.hashCode())+".jpg");}
    static Bitmap cover(Context c,ComicLibrary.Comic comic) throws Exception {
        File cached=cached(c,comic.uri);
        if(cached.isFile()&&cached.length()>0){Bitmap found=BitmapFactory.decodeFile(cached.getAbsolutePath());if(found!=null)return found;}
        ArrayList<ComicLibrary.Chapter> chapters=ComicLibrary.chapters(c,comic);
        if(chapters.isEmpty())return null;
        ComicLibrary.Chapter chapter=chapters.get(0);
        Bitmap bitmap=null;
        if("mangalib_cbz".equals(chapter.type)){
            ArrayList<String> pages=MangaLibStorage.extractForReading(c,new File(chapter.imagePaths.get(0)));
            if(!pages.isEmpty())bitmap=decodeFile(pages.get(0));
        }else if("archive_tree".equals(chapter.type)||"archive".equals(chapter.type)){
            ArrayList<String> pages=ComicLibrary.pagesForChapter(c,comic,chapter);
            if(!pages.isEmpty())bitmap=decodeFile(pages.get(0));
        }else if("images".equals(chapter.type)&&!chapter.imagePaths.isEmpty()){
            bitmap=fromRelative(c,comic,chapter.imagePaths.get(0));
        }else if("html".equals(chapter.type)&&chapter.htmlFile!=null){
            // Read only the first megabyte of a chapter to locate its first local image.
            try(InputStream in=c.getContentResolver().openInputStream(chapter.htmlFile.getUri())){
                if(in!=null){byte[] bytes=new byte[1024*1024];int size=0,n;
                    while(size<bytes.length&&(n=in.read(bytes,size,bytes.length-size))>0)size+=n;
                    Element image=Jsoup.parse(new String(bytes,0,size,StandardCharsets.UTF_8)).selectFirst("img");
                    if(image!=null){String path=image.hasAttr("data-src")?image.attr("data-src"):image.attr("src");
                        if(!path.startsWith("/")&&!path.contains(":")&&!path.contains(".."))bitmap=fromRelative(c,comic,path);}
                }}
        }
        if(bitmap!=null){try(FileOutputStream output=new FileOutputStream(cached)){bitmap.compress(Bitmap.CompressFormat.JPEG,84,output);}}
        return bitmap;
    }
    private static Bitmap fromRelative(Context c,ComicLibrary.Comic comic,String relative)throws Exception{
        DocumentFile file=ComicLibrary.tree(c,comic);
        if(file==null)return null;
        for(String part:relative.split("/")){if(part.isEmpty()||part.equals("."))continue;
            if(part.equals(".."))return null;file=file.findFile(part);if(file==null)return null;}
        Uri uri=file.getUri();BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream stream=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(stream,null,bounds);}
        BitmapFactory.Options opts=sample(bounds);
        try(InputStream stream=c.getContentResolver().openInputStream(uri)){return BitmapFactory.decodeStream(stream,null,opts);}
    }
    private static Bitmap decodeFile(String name){BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(name,bounds);return BitmapFactory.decodeFile(name,sample(bounds));}
    private static BitmapFactory.Options sample(BitmapFactory.Options b){BitmapFactory.Options out=new BitmapFactory.Options();int size=1;
        while(b.outWidth/size>480||b.outHeight/size>640)size*=2;
        out.inSampleSize=Math.max(1,size);return out;}
}
