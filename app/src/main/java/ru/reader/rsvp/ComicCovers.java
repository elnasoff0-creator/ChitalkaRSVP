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
import java.util.LinkedHashMap;
import java.util.Map;

/** Background, bounded first-page thumbnails. No network access; safely loads SAF or offline CBZ. */
final class ComicCovers {
    private ComicCovers(){}
    private static final int MEMORY_LIMIT=24;
    private static final Map<String,Bitmap> MEMORY=new LinkedHashMap<String,Bitmap>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Bitmap> e){return size()>MEMORY_LIMIT;}
    };
    private static synchronized Bitmap fromMemory(String key){return MEMORY.get(key);}
    private static synchronized void toMemory(String key,Bitmap bmp){if(bmp!=null)MEMORY.put(key,bmp);}
    private static synchronized void clearMemory(String prefix){
        java.util.Iterator<String> it=MEMORY.keySet().iterator();
        while(it.hasNext())if(it.next().startsWith(prefix))it.remove();
    }
    private static String signature(ComicLibrary.Chapter ch){
        if(ch==null)return "none";
        if(!ch.imagePaths.isEmpty()){
            File f=new File(ch.imagePaths.get(0));
            if(f.isFile())return f.getAbsolutePath()+":"+f.length()+":"+f.lastModified();
        }
        return ch.type+":"+ch.title;
    }

    static File cached(Context c,String uri){File directory=new File(c.getCacheDir(),"comic_cover_cache");
        if(!directory.exists())directory.mkdirs();return new File(directory,Integer.toHexString(uri.hashCode())+".jpg");}
    static void invalidate(Context c,String comicUri){
        clearMemory(comicUri+"#");
        File folder=new File(c.getCacheDir(),"comic_cover_cache");
        File[] files=folder.listFiles();if(files==null)return;
        // Cache names include per-chapter indices; safely remove only this comic's entries.
        int max=ComicLibrary.find(c,comicUri)==null?0:Math.max(1,ComicLibrary.find(c,comicUri).chapterTotal);
        for(int i=0;i<max+3;i++)cached(c,comicUri+"#"+i).delete();
    }
    static Bitmap cover(Context c,ComicLibrary.Comic comic) throws Exception {
        int index=c.getSharedPreferences("reader",0).getInt("comic_manual_cover:"+comic.uri,0);
        return coverFromChapter(c,comic,index);
    }
    static Bitmap continueCover(Context c,ComicLibrary.Comic comic) throws Exception {
        ArrayList<ComicLibrary.Chapter> chapters=ComicLibrary.chapters(c,comic);
        ComicLibrary.Chapter current=chapters.isEmpty()?null:chapters.get(Math.max(0,Math.min(comic.chapter,chapters.size()-1)));
        boolean archive=current!=null&&(current.type.equals("mangalib_cbz")||current.type.equals("archive_tree")||current.type.equals("archive"));
        return coverFromChapter(c,comic,archive?comic.chapter:0);
    }
    static Bitmap coverFromChapter(Context c,ComicLibrary.Comic comic,int chapterIndex) throws Exception {
        ArrayList<ComicLibrary.Chapter> chapters=ComicLibrary.chapters(c,comic);
        if(chapters.isEmpty())return null;
        ComicLibrary.Chapter chapter=chapters.get(Math.max(0,Math.min(chapterIndex,chapters.size()-1)));
        String key=comic.uri+"#"+chapterIndex+"#"+signature(chapter);
        Bitmap resident=fromMemory(key);if(resident!=null&&!resident.isRecycled())return resident;
        File cached=cached(c,key);
        if(cached.isFile()&&cached.length()>0){Bitmap found=BitmapFactory.decodeFile(cached.getAbsolutePath());if(found!=null){toMemory(key,found);return found;}}
        Bitmap bitmap=null;
        if("mangalib_cbz".equals(chapter.type)){
            if(!chapter.imagePaths.isEmpty())bitmap=MangaDownloadCover.firstPage(MangaLibStorage.localArchive(c,chapter.imagePaths.get(0)));
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
        if(bitmap!=null){toMemory(key,bitmap);try(FileOutputStream output=new FileOutputStream(cached)){bitmap.compress(Bitmap.CompressFormat.JPEG,84,output);}}
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
