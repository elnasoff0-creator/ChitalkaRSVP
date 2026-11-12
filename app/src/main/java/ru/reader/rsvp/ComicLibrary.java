package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** SAF comic library supporting HTML folders, image folders and CBZ/CBR archives. */
final class ComicLibrary {
    static final class Comic {
        String uri="",title="",kind="auto";int chapter=0,mode=1,scroll=0,page=0,chapterTotal=0,pageTotal=0;long opened=0;
        ArrayList<Integer> pageCounts=new ArrayList<>();
        int progressPercent(){return ComicProgressMath.percent(pageCounts,chapter,page,chapterTotal);}
        String compactProgress(){
            int files=chapterTotal,number=files>0?Math.min(files,chapter+1):chapter+1;
            int pages=pageTotal>0?pageTotal:(chapter>=0&&chapter<pageCounts.size()?pageCounts.get(chapter):0);
            String progress=(files>0?number+"/"+files:"Файл "+number)+" · Стр "+(page+1)+(pages>0?"/"+pages:"");
            int percent=progressPercent();return progress+(percent>=0?" · "+percent+"%":"");
        }
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("uri",uri).put("title",title).put("kind",kind).put("chapter",chapter).put("mode",mode).put("scroll",scroll).put("page",page).put("chapterTotal",chapterTotal)
            .put("pageTotal",pageTotal).put("opened",opened);
            JSONArray counts=new JSONArray();for(Integer count:pageCounts)counts.put(count);o.put("pageCounts",counts);}catch(Exception ignored){}return o;}
        static Comic read(JSONObject o){Comic c=new Comic();c.uri=o.optString("uri","");c.title=o.optString("title","");c.kind=o.optString("kind","auto");c.chapter=o.optInt("chapter",0);c.mode=o.optInt("mode",1);c.scroll=o.optInt("scroll",0);c.page=o.optInt("page",0);c.chapterTotal=o.optInt("chapterTotal",0);c.pageTotal=o.optInt("pageTotal",0);c.opened=o.optLong("opened",0);JSONArray counts=o.optJSONArray("pageCounts");
            if(counts!=null)for(int i=0;i<counts.length();i++)c.pageCounts.add(counts.optInt(i,-1));return c;}
    }
    static final class Chapter {
        String title="";String type="html";DocumentFile htmlFile,archiveFile;int pageCount=-1;ArrayList<String> imagePaths=new ArrayList<>();
    }

    static ArrayList<Comic> list(Context c){ArrayList<Comic> out=new ArrayList<>();try{JSONArray arr=new JSONArray(c.getSharedPreferences("reader",0).getString("comics_v1","[]"));for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o!=null){Comic item=Comic.read(o);item.title=BookTitleStore.title(c,BookTitleStore.COMIC,item.uri,item.title);out.add(item);}}}catch(Exception ignored){}return out;}
    static synchronized void save(Context c,Comic comic){ArrayList<Comic> all=list(c);for(int i=0;i<all.size();i++)if(all.get(i).uri.equals(comic.uri)){
            Comic previous=all.get(i);
            if(comic.pageCounts.isEmpty()&&!previous.pageCounts.isEmpty())comic.pageCounts.addAll(previous.pageCounts);
            else if(previous.pageCounts.size()==comic.pageCounts.size())for(int j=0;j<previous.pageCounts.size();j++)
                if(comic.pageCounts.get(j)<=0&&previous.pageCounts.get(j)>0)comic.pageCounts.set(j,previous.pageCounts.get(j));
            all.set(i,comic);write(c,all);return;
        }all.add(comic);write(c,all);}
    static synchronized void write(Context c,ArrayList<Comic> all){JSONArray a=new JSONArray();for(Comic item:all)a.put(item.json());c.getSharedPreferences("reader",0).edit().putString("comics_v1",a.toString()).apply();}
    static Comic find(Context c,String uri){for(Comic item:list(c))if(item.uri.equals(uri))return item;return null;}

    static synchronized void remove(Context c,String uri){ArrayList<Comic> all=list(c);
        for(int i=all.size()-1;i>=0;i--)if(uri.equals(all.get(i).uri))all.remove(i);
        write(c,all);
    }
    /** Populate each archive's image count on a background thread, never on MainActivity's UI thread. */
    static void indexCounts(Context context,Comic comic){
        ArrayList<Chapter> chapters=chapters(context,comic);if(chapters.isEmpty())return;
        ArrayList<Integer> indexed=new ArrayList<>();
        for(int i=0;i<chapters.size();i++){
            Chapter ch=chapters.get(i);
            int count=i<comic.pageCounts.size()?comic.pageCounts.get(i):-1;
            if(count<=0){
                try{
                    if("archive_tree".equals(ch.type)&&ch.archiveFile!=null)
                        count=countArchive(context,ch.archiveFile.getUri(),safeName(ch.archiveFile));
                    else if("mangalib_cbz".equals(ch.type)&&!ch.imagePaths.isEmpty())
                        count=countLocalCbz(new File(ch.imagePaths.get(0)));
                    else if("html".equals(ch.type)&&ch.htmlFile!=null){
                        try(InputStream in=context.getContentResolver().openInputStream(ch.htmlFile.getUri())){
                            if(in!=null){byte[] data=new byte[8*1024*1024];int n=0,k;
                                while(n<data.length&&(k=in.read(data,n,data.length-n))>0)n+=k;
                                String html=new String(data,0,n,java.nio.charset.StandardCharsets.UTF_8);
                                java.util.regex.Matcher images=Pattern.compile("(?i)<img\\b").matcher(html);
                                count=0;while(images.find())count++;
                            }
                        }
                    }else count=chapterPages(ch);
                }catch(Exception ignored){count=-1;}
            }
            indexed.add(count>0?count:-1);
        }
        synchronized(ComicLibrary.class){
            Comic latest=find(context,comic.uri);if(latest==null)return;
            latest.pageCounts=indexed;latest.chapterTotal=chapters.size();
            if(latest.chapter>=0&&latest.chapter<indexed.size()&&indexed.get(latest.chapter)>0)
                latest.pageTotal=indexed.get(latest.chapter);
            save(context,latest);
        }
    }
    private static int countLocalCbz(File path)throws Exception{
        if(!path.isFile())return -1;
        int count=0;try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(path)){
            java.util.Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){
                ZipEntry entry=entries.nextElement();if(!entry.isDirectory()&&isImageName(entry.getName()))count++;
            }
        }return count;
    }
    private static int countArchive(Context context,Uri uri,String name)throws Exception{
        File temp=File.createTempFile("comic_count_",name.toLowerCase(Locale.ROOT).endsWith(".cbr")?".rar":".zip",context.getCacheDir());
        try{
            try(InputStream in=context.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new java.io.IOException("Недоступный файл");byte[] buffer=new byte[8192];int n;long bytes=0;
                while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>850_000_000L)throw new java.io.IOException("Архив слишком большой");out.write(buffer,0,n);}
            }
            if(name.toLowerCase(Locale.ROOT).matches(".*\\.(cbr|rar)$")){
                com.github.junrar.Archive rar=new com.github.junrar.Archive(temp);
                try{int count=0;for(com.github.junrar.rarfile.FileHeader h:rar.getFileHeaders())
                    if(!h.isDirectory()&&isImageName(h.getFileName()))count++;return count;
                }finally{rar.close();}
            }
            return countLocalCbz(temp);
        }finally{temp.delete();}
    }

    static DocumentFile tree(Context c,Comic item){try{return DocumentFile.fromTreeUri(c,Uri.parse(item.uri));}catch(Exception e){return null;}}
    static DocumentFile single(Context c,Comic item){try{return DocumentFile.fromSingleUri(c,Uri.parse(item.uri));}catch(Exception e){return null;}}

    static ArrayList<Chapter> chapters(Context c,Comic comic){
        ArrayList<Chapter> out=new ArrayList<>();
        if("mangalib".equals(comic.kind))return MangaLibStorage.comicChapters(c,comic.uri.substring("mangalib:".length()));
        if(isArchiveKind(comic.kind) || isArchiveUri(c, comic)) return archiveChapters(c,comic);
        DocumentFile folder=tree(c,comic);if(folder==null||!folder.exists())return out;

        ArrayList<DocumentFile> archives=new ArrayList<>();
        ArrayList<DocumentFile> html=new ArrayList<>();
        ArrayList<DocumentFile> directImages=new ArrayList<>();
        ArrayList<DocumentFile> subFolders=new ArrayList<>();
        for(DocumentFile f:folder.listFiles()){
            String name=safeName(f);
            if(f.isFile()&&isArchiveName(name)) archives.add(f);
            else if(f.isFile()&&isHtmlName(name)) html.add(f);
            else if(f.isFile()&&isImageName(name)) directImages.add(f);
            else if(f.isDirectory()) subFolders.add(f);
        }
        archives.sort((a,b)->natural(safeName(a),safeName(b)));
        if(!archives.isEmpty()){
            comic.kind="archive_folder";
            for(DocumentFile f:archives){Chapter ch=new Chapter();ch.title=stripExt(safeName(f));ch.type="archive_tree";ch.archiveFile=f;out.add(ch);}
            return out;
        }
        html.sort((a,b)->natural(safeName(a),safeName(b)));
        directImages.sort((a,b)->natural(safeName(a),safeName(b)));
        subFolders.sort((a,b)->natural(safeName(a),safeName(b)));

        if(!html.isEmpty()){
            comic.kind="html";
            for(DocumentFile f:html){Chapter ch=new Chapter();ch.title=safeName(f);ch.type="html";ch.htmlFile=f;out.add(ch);}return out;
        }

        boolean hasImageSubfolders=false;
        for(DocumentFile dir:subFolders){ArrayList<String> pages=collectImagePaths(folder,dir);if(!pages.isEmpty()){hasImageSubfolders=true;Chapter ch=new Chapter();ch.title=safeName(dir);ch.type="images";ch.imagePaths.addAll(pages);ch.pageCount=ch.imagePaths.size();out.add(ch);}}
        if(hasImageSubfolders){comic.kind="images";out.sort(Comparator.comparing(ch->ch.title, ComicLibrary::natural));return out;}

        if(!directImages.isEmpty()){
            comic.kind="images";Chapter ch=new Chapter();ch.title=safeName(folder);ch.type="images";
            for(DocumentFile f:directImages) ch.imagePaths.add(safeName(f));
            ch.pageCount=ch.imagePaths.size();
            out.add(ch);
        }
        return out;
    }

    private static ArrayList<String> collectImagePaths(DocumentFile root, DocumentFile folder){
        ArrayList<DocumentFile> files=new ArrayList<>();
        for(DocumentFile f:folder.listFiles()) if(f.isFile()&&isImageName(safeName(f))) files.add(f);
        files.sort((a,b)->natural(safeName(a),safeName(b)));
        ArrayList<String> out=new ArrayList<>();
        String prefix=safeName(folder)+"/";
        for(DocumentFile f:files) out.add(prefix+safeName(f));
        return out;
    }

    private static boolean isArchiveUri(Context c, Comic comic){
        DocumentFile file=single(c,comic);String name=safeName(file).toLowerCase(Locale.ROOT);return isArchiveName(name);
    }
    static boolean isArchiveKind(String kind){return "cbz".equals(kind)||"cbr".equals(kind);}    
    static boolean isHtmlName(String name){String lower=name.toLowerCase(Locale.ROOT);return lower.endsWith(".html")||lower.endsWith(".htm");}
    static boolean isImageName(String name){String lower=name.toLowerCase(Locale.ROOT);return lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp")||lower.endsWith(".gif")||lower.endsWith(".bmp")||lower.endsWith(".avif");}
    static boolean isArchiveName(String name){String lower=name.toLowerCase(Locale.ROOT);return lower.endsWith(".cbz")||lower.endsWith(".zip")||lower.endsWith(".cbr")||lower.endsWith(".rar");}
    static String safeName(DocumentFile file){return file!=null&&file.getName()!=null?file.getName():"Без названия";}

    private static ArrayList<Chapter> archiveChapters(Context c,Comic comic){
        ArrayList<Chapter> out=new ArrayList<>();
        try{
            DocumentFile file=single(c,comic);if(file==null||!file.isFile())return out;
            String name=safeName(file);String lower=name.toLowerCase(Locale.ROOT);
            comic.kind=lower.endsWith(".cbr")||lower.endsWith(".rar")?"cbr":"cbz";
            File dir=ensureArchiveExtracted(c,comic,file);
            if(dir==null||!dir.isDirectory())return out;
            ArrayList<File> pages=new ArrayList<>();
            File[] files=dir.listFiles();if(files!=null) for(File f:files) if(f.isFile()&&isImageName(f.getName())) pages.add(f);
            pages.sort((a,b)->natural(a.getName(),b.getName()));
            if(!pages.isEmpty()){
                Chapter ch=new Chapter();ch.title=name;ch.type="archive";
                for(File f:pages) ch.imagePaths.add(f.getAbsolutePath());
                ch.pageCount=ch.imagePaths.size();
                out.add(ch);
            }
        }catch(Exception ignored){}
        return out;
    }


    /** Expand exactly one requested archive, never the whole series. */
    static ArrayList<String> pagesForChapter(Context c,Comic comic,Chapter ch) throws Exception {
        if("archive_tree".equals(ch.type)){
            File dir=ensureArchiveExtracted(c,comic,ch.archiveFile);
            ArrayList<File> pages=new ArrayList<>();File[] files=dir.listFiles();
            if(files!=null)for(File f:files)if(f.isFile()&&isImageName(f.getName()))pages.add(f);
            pages.sort((a,b)->natural(a.getName(),b.getName()));
            ArrayList<String> result=new ArrayList<>();for(File f:pages)result.add(f.getAbsolutePath());
            ch.pageCount=result.size();return result;
        }
        ch.pageCount=ch.imagePaths.size();return new ArrayList<>(ch.imagePaths);
    }

    /** Cheap counts for known image folders; archives are evaluated lazily when opened. */
    static int chapterPages(Chapter chapter){return chapter.pageCount>=0?chapter.pageCount:("archive_tree".equals(chapter.type)||"mangalib_cbz".equals(chapter.type))?-1:chapter.imagePaths.size()>0?chapter.imagePaths.size():-1;}

    private static File ensureArchiveExtracted(Context c,Comic comic,DocumentFile source) throws Exception {
        File root=new File(c.getCacheDir(),"comic_archives"); if(!root.exists()) root.mkdirs();
        String key=Integer.toHexString((comic.uri+"|"+source.getUri()+"|"+source.length()+"|"+source.lastModified()).hashCode());
        File dir=new File(root,key); File ready=new File(dir,".ready");
        if(ready.exists()) return dir;
        delete(dir); dir.mkdirs();
        File temp=new File(dir,"source.tmp");
        try(InputStream in=c.getContentResolver().openInputStream(source.getUri()); OutputStream out=new FileOutputStream(temp)){
            if(in==null) throw new Exception("Архив недоступен");
            byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
        }
        String lower=safeName(source).toLowerCase(Locale.ROOT);
        if(lower.endsWith(".cbz")||lower.endsWith(".zip")) extractCbz(temp,dir); else extractCbr(temp,dir);
        temp.delete();
        ready.createNewFile();
        return dir;
    }
    private static void extractCbz(File archive,File dir) throws Exception {
        // ZipFile gives random access, allowing natural order regardless of archive entry order.
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)){
            ArrayList<? extends ZipEntry> entries=java.util.Collections.list(zip.entries());
            ArrayList<ZipEntry> images=new ArrayList<>();
            for(ZipEntry e:entries){String base=e.getName().replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);
                if(!e.isDirectory()&&isImageName(base))images.add(e);}
            images.sort((a,b)->natural(a.getName(),b.getName()));
            if(images.size()>1500)throw new java.io.IOException("Слишком много страниц в архиве");
            long total=0;int i=0;
            for(ZipEntry e:images){String base=e.getName().replace('\\','/');base=base.substring(base.lastIndexOf('/')+1);
                String ext=base.substring(base.lastIndexOf('.')).toLowerCase(Locale.ROOT);
                File out=new File(dir,String.format(Locale.US,"%04d%s",++i,ext));
                try(InputStream in=zip.getInputStream(e);OutputStream os=new FileOutputStream(out)){
                    byte[] b=new byte[8192];int n;long size=0;
                    while((n=in.read(b))!=-1){size+=n;total+=n;
                        if(size>35_000_000L||total>1_000_000_000L)throw new java.io.IOException("Архив слишком большой");
                        os.write(b,0,n);}
                }
            }
        }
    }
    private static void extractCbr(File archive,File dir) throws Exception {
        com.github.junrar.Archive rar=new com.github.junrar.Archive(archive);
        try{
            java.util.List<com.github.junrar.rarfile.FileHeader> headers=rar.getFileHeaders();
            ArrayList<com.github.junrar.rarfile.FileHeader> imageHeaders=new ArrayList<>();
            for(com.github.junrar.rarfile.FileHeader h:headers){
                String name=h.getFileName(); if(name==null) continue; String base=new File(name.replace('\\','/')).getName();
                if(!h.isDirectory()&&isImageName(base)) imageHeaders.add(h);
            }
            imageHeaders.sort((a,b)->natural(new File(a.getFileName().replace('\\','/')).getName(),new File(b.getFileName().replace('\\','/')).getName()));
            if(imageHeaders.size()>1500)throw new java.io.IOException("Слишком много страниц");
            int index=1;long total=0;
            for(com.github.junrar.rarfile.FileHeader h:imageHeaders){
                String base=new File(h.getFileName().replace('\\','/')).getName();
                String ext=base.lastIndexOf('.')>=0?base.substring(base.lastIndexOf('.')).toLowerCase(Locale.ROOT):".img";
                File out=new File(dir,String.format(Locale.US,"%04d_%s%s",index++,slug(stripExt(base)),ext));
                try(OutputStream os=new FileOutputStream(out)){
                    rar.extractFile(h,new java.io.FilterOutputStream(os){long written=0;
                        @Override public void write(int b)throws java.io.IOException{if(++written>35_000_000L)throw new java.io.IOException("Страница слишком большая");super.write(b);}
                        @Override public void write(byte[] b,int off,int len)throws java.io.IOException{written+=len;if(written>35_000_000L)throw new java.io.IOException("Страница слишком большая");out.write(b,off,len);}
                    });
                }            
            }
        } finally { try{rar.close();}catch(Exception ignored){} }
    }
    private static void delete(File file){ if(file==null||!file.exists()) return; if(file.isDirectory()){File[] kids=file.listFiles(); if(kids!=null) for(File kid:kids) delete(kid);} file.delete(); }
    static String stripExt(String name){int i=name.lastIndexOf('.'); return i>0?name.substring(0,i):name;}
    static String slug(String in){String s=in==null?"page":in.replaceAll("[^\\p{L}\\p{Nd}_-]+","_");s=s.replaceAll("_+","_");if(s.startsWith("_"))s=s.substring(1);if(s.endsWith("_"))s=s.substring(0,s.length()-1);return s.isEmpty()?"page":s;}
    static int natural(String a,String b){if(a==null)a="";if(b==null)b="";Matcher x=Pattern.compile("\\d+|\\D+").matcher(a.toLowerCase(Locale.ROOT)),y=Pattern.compile("\\d+|\\D+").matcher(b.toLowerCase(Locale.ROOT));
        while(x.find()&&y.find()){String aa=x.group(),bb=y.group();int diff;
            if(Character.isDigit(aa.charAt(0))&&Character.isDigit(bb.charAt(0))){try{diff=Long.compare(Long.parseLong(aa),Long.parseLong(bb));}catch(Exception e){diff=aa.compareTo(bb);}}
            else diff=aa.compareTo(bb); if(diff!=0) return diff;}
        return a.compareTo(b);
    }
}
