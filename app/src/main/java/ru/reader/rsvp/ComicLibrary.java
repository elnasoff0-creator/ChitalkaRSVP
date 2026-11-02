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
        String uri="",title="",kind="auto";int chapter=0,mode=1,scroll=0;long opened=0;
        JSONObject json(){JSONObject o=new JSONObject();try{o.put("uri",uri).put("title",title).put("kind",kind).put("chapter",chapter).put("mode",mode).put("scroll",scroll).put("opened",opened);}catch(Exception ignored){}return o;}
        static Comic read(JSONObject o){Comic c=new Comic();c.uri=o.optString("uri","");c.title=o.optString("title","");c.kind=o.optString("kind","auto");c.chapter=o.optInt("chapter",0);c.mode=o.optInt("mode",1);c.scroll=o.optInt("scroll",0);c.opened=o.optLong("opened",0);return c;}
    }
    static final class Chapter {
        String title="";String type="html";DocumentFile htmlFile;ArrayList<String> imagePaths=new ArrayList<>();
    }

    static ArrayList<Comic> list(Context c){ArrayList<Comic> out=new ArrayList<>();try{JSONArray arr=new JSONArray(c.getSharedPreferences("reader",0).getString("comics_v1","[]"));for(int i=0;i<arr.length();i++){JSONObject o=arr.optJSONObject(i);if(o!=null)out.add(Comic.read(o));}}catch(Exception ignored){}return out;}
    static void save(Context c,Comic comic){ArrayList<Comic> all=list(c);for(int i=0;i<all.size();i++)if(all.get(i).uri.equals(comic.uri)){all.set(i,comic);write(c,all);return;}all.add(comic);write(c,all);}
    static void write(Context c,ArrayList<Comic> all){JSONArray a=new JSONArray();for(Comic item:all)a.put(item.json());c.getSharedPreferences("reader",0).edit().putString("comics_v1",a.toString()).apply();}
    static Comic find(Context c,String uri){for(Comic item:list(c))if(item.uri.equals(uri))return item;return null;}

    static DocumentFile tree(Context c,Comic item){try{return DocumentFile.fromTreeUri(c,Uri.parse(item.uri));}catch(Exception e){return null;}}
    static DocumentFile single(Context c,Comic item){try{return DocumentFile.fromSingleUri(c,Uri.parse(item.uri));}catch(Exception e){return null;}}

    static ArrayList<Chapter> chapters(Context c,Comic comic){
        ArrayList<Chapter> out=new ArrayList<>();
        if(isArchiveKind(comic.kind) || isArchiveUri(c, comic)) return archiveChapters(c,comic);
        DocumentFile folder=tree(c,comic);if(folder==null||!folder.exists())return out;

        ArrayList<DocumentFile> html=new ArrayList<>();
        ArrayList<DocumentFile> directImages=new ArrayList<>();
        ArrayList<DocumentFile> subFolders=new ArrayList<>();
        for(DocumentFile f:folder.listFiles()){
            String name=safeName(f);
            if(f.isFile()&&isHtmlName(name)) html.add(f);
            else if(f.isFile()&&isImageName(name)) directImages.add(f);
            else if(f.isDirectory()) subFolders.add(f);
        }
        html.sort((a,b)->natural(safeName(a),safeName(b)));
        directImages.sort((a,b)->natural(safeName(a),safeName(b)));
        subFolders.sort((a,b)->natural(safeName(a),safeName(b)));

        if(!html.isEmpty()){
            comic.kind="html";
            for(DocumentFile f:html){Chapter ch=new Chapter();ch.title=safeName(f);ch.type="html";ch.htmlFile=f;out.add(ch);}return out;
        }

        boolean hasImageSubfolders=false;
        for(DocumentFile dir:subFolders){ArrayList<String> pages=collectImagePaths(folder,dir);if(!pages.isEmpty()){hasImageSubfolders=true;Chapter ch=new Chapter();ch.title=safeName(dir);ch.type="images";ch.imagePaths.addAll(pages);out.add(ch);}}
        if(hasImageSubfolders){comic.kind="images";out.sort(Comparator.comparing(ch->ch.title, ComicLibrary::natural));return out;}

        if(!directImages.isEmpty()){
            comic.kind="images";Chapter ch=new Chapter();ch.title=safeName(folder);ch.type="images";
            for(DocumentFile f:directImages) ch.imagePaths.add(safeName(f));
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
                out.add(ch);
            }
        }catch(Exception ignored){}
        return out;
    }

    private static File ensureArchiveExtracted(Context c,Comic comic,DocumentFile source) throws Exception {
        File root=new File(c.getCacheDir(),"comic_archives"); if(!root.exists()) root.mkdirs();
        String key=Integer.toHexString((comic.uri+"|"+safeName(source)).hashCode());
        File dir=new File(root,key); File ready=new File(dir,".ready");
        if(ready.exists()) return dir;
        delete(dir); dir.mkdirs();
        File temp=new File(dir,"source.tmp");
        try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(comic.uri)); OutputStream out=new FileOutputStream(temp)){
            if(in==null) throw new Exception("Архив недоступен");
            byte[] buf=new byte[8192]; int n; while((n=in.read(buf))!=-1) out.write(buf,0,n);
        }
        String lower=safeName(source).toLowerCase(Locale.ROOT);
        if(lower.endsWith(".cbz")||lower.endsWith(".zip")) extractCbz(temp,dir); else extractCbr(temp,dir);
        ready.createNewFile();
        return dir;
    }
    private static void extractCbz(File archive,File dir) throws Exception {
        try(ZipInputStream zis=new ZipInputStream(new java.io.FileInputStream(archive))){
            ZipEntry e; int index=1;
            while((e=zis.getNextEntry())!=null){
                if(e.isDirectory()) continue; String name=e.getName();
                String base=new File(name).getName(); if(!isImageName(base)) continue;
                String ext=base.lastIndexOf('.')>=0?base.substring(base.lastIndexOf('.')).toLowerCase(Locale.ROOT):".img";
                File out=new File(dir,String.format(Locale.US,"%04d_%s%s",index++,slug(stripExt(base)),ext));
                try(OutputStream os=new FileOutputStream(out)){byte[] b=new byte[8192];int n;while((n=zis.read(b))!=-1)os.write(b,0,n);}            
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
            int index=1;
            for(com.github.junrar.rarfile.FileHeader h:imageHeaders){
                String base=new File(h.getFileName().replace('\\','/')).getName();
                String ext=base.lastIndexOf('.')>=0?base.substring(base.lastIndexOf('.')).toLowerCase(Locale.ROOT):".img";
                File out=new File(dir,String.format(Locale.US,"%04d_%s%s",index++,slug(stripExt(base)),ext));
                try(OutputStream os=new FileOutputStream(out)){rar.extractFile(h,os);}            
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
