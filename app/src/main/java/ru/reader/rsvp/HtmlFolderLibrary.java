package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/** Import an Android SAF HTML chapter folder as one monitored, offline HTML book.
 * Original chapters remain untouched; generated book.html is rebuilt transactionally.
 */
final class HtmlFolderLibrary {
    private static final int MAX_CHAPTERS=2500,MAX_DEPTH=8;
    private static final long MAX_CHAPTER_BYTES=12L*1024*1024;
    private static final long MAX_TOTAL_BYTES=100L*1024*1024;
    private static final Object IO_LOCK=new Object();
    private HtmlFolderLibrary(){}
    static final class Result {
        final String uri,title;final int chapterCount;final boolean updated;
        Result(String uri,String title,int count,boolean changed){this.uri=uri;this.title=title;chapterCount=count;updated=changed;}
    }
    private static final class Part {
        final String path;final DocumentFile document;
        Part(String p,DocumentFile d){path=p;document=d;}
    }
    private static final class Scan {
        final ArrayList<Part> html=new ArrayList<>();
        final HashMap<String,DocumentFile> files=new HashMap<>();
        long total=0;
    }
    private static File root(Context c){
        File ext=c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File base=ext==null?c.getFilesDir():ext;
        File folder=new File(base,"Books/ImportedHTML");if(!folder.isDirectory())folder.mkdirs();return folder;
    }
    private static String digest(byte[] value){
        try{MessageDigest md=MessageDigest.getInstance("SHA-256");md.update(value);StringBuilder out=new StringBuilder();
            for(byte b:md.digest())out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static String digest(String text){return digest(text.getBytes(StandardCharsets.UTF_8));}
    private static File folder(Context c,Uri tree){return new File(root(c),digest(tree.toString()).substring(0,32));}
    private static File manifest(File folder){return new File(folder,"manifest.json");}
    private static String readFile(File file)throws IOException{
        try(InputStream stream=new FileInputStream(file)){return new String(readBounded(stream,MAX_TOTAL_BYTES),StandardCharsets.UTF_8);}
    }
    private static byte[] readBounded(InputStream in,long limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;long bytes=0;
        while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>limit)throw new IOException("Файл превышает допустимый размер");out.write(buffer,0,n);}
        return out.toByteArray();
    }
    private static byte[] readDocument(Context c,DocumentFile file,long max)throws IOException{
        try(InputStream in=c.getContentResolver().openInputStream(file.getUri())){
            if(in==null)throw new IOException("Не удалось открыть файл: "+file.getName());
            return readBounded(in,max);
        }
    }
    private static byte[] fileDigest(Context c,Uri uri)throws IOException{
        try{
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=c.getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException("Внешний HTML недоступен");
                byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)sha.update(bytes,0,n);
            }
            return sha.digest();
        }catch(IOException ex){throw ex;}
        catch(Exception ex){throw new IOException("Не удалось проверить HTML",ex);}
    }
    private static byte[] fileDigest(File file)throws IOException{
        try{
            MessageDigest sha=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(file)){
                byte[] bytes=new byte[32768];int n;while((n=in.read(bytes))!=-1)sha.update(bytes,0,n);
            }
            return sha.digest();
        }catch(IOException ex){throw ex;}
        catch(Exception ex){throw new IOException("Не удалось проверить файл",ex);}
    }
    private static void transfer(InputStream in,java.io.OutputStream out)throws IOException{
        byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
        out.flush();
    }
    private static DocumentFile transferredFolder(Context c,JSONObject previous)throws IOException{
        String grant=previous.optString("externalTree","");
        String name=previous.optString("externalDirectory","");
        if(grant.isEmpty()||name.isEmpty())
            throw new IOException("Нет доступа к папке перенесённой HTML-книги");
        DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(grant));
        DocumentFile destination=root==null?null:root.findFile(name);
        if(destination==null||!destination.isDirectory())
            throw new IOException("Папка перенесённой книги не найдена");
        return destination;
    }
    private static void publishAssets(Context c,JSONObject previous,File local)throws IOException{
        File images=new File(local,"assets");File[] all=images.listFiles();
        if(all==null||all.length==0)return;
        try{
            DocumentFile output=SafeDownloadTransfer.directory(transferredFolder(c,previous),"assets");
            SafeDownloadTransfer.Result tally=new SafeDownloadTransfer.Result();
            for(File image:all)if(image.isFile()&&!image.getName().endsWith(".part"))
                SafeDownloadTransfer.copyFile(c,image,output,image.getName(),tally);
        }catch(Exception error){throw new IOException("Не удалось перенести связанные изображения HTML-книги",error);}
    }
    /** SAF providers do not guarantee atomic replace. Keep a verified local rollback
     * and restore it if any byte of an in-place write fails verification. */
    private static void publishExternal(Context c,String external,File generated,File local)throws IOException{
        Uri document=Uri.parse(external);
        File rollback=new File(local,"external-book.rollback.html");
        try(InputStream from=c.getContentResolver().openInputStream(document);
            FileOutputStream backup=new FileOutputStream(rollback)){
            if(from==null)throw new IOException("Перенесённая HTML-книга недоступна");
            transfer(from,backup);backup.getFD().sync();
        }
        try{
            try(InputStream from=new FileInputStream(generated);
                java.io.OutputStream out=c.getContentResolver().openOutputStream(document,"wt")){
                if(out==null)throw new IOException("Нет разрешения записать HTML во внешнюю папку");
                transfer(from,out);
            }
            if(!MessageDigest.isEqual(fileDigest(generated),fileDigest(c,document)))
                throw new IOException("Содержимое внешней HTML-книги не совпало с исходным");
            // Keep one rollback on disk until the following successful update.
        }catch(Exception error){
            try{
                try(InputStream from=new FileInputStream(rollback);
                    java.io.OutputStream out=c.getContentResolver().openOutputStream(document,"wt")){
                    if(out==null)throw new IOException("Нет доступа к файлу для восстановления");
                    transfer(from,out);
                }
                if(!MessageDigest.isEqual(fileDigest(rollback),fileDigest(c,document)))
                    throw new IOException("Восстановленный HTML не прошёл проверку");
            }catch(Exception recovery){
                throw new IOException("Ошибка записи внешней книги, восстановление не завершилось. "
                    +"Резервная копия: "+rollback.getAbsolutePath(),recovery);
            }
            throw error instanceof IOException?(IOException)error:new IOException(error);
        }
    }
    private static void atomic(File target,byte[] contents,boolean backup)throws IOException{
        File dir=target.getParentFile();if(dir==null||(!dir.isDirectory()&&!dir.mkdirs()))throw new IOException("Нет доступа к папке");
        File stage=new File(dir,target.getName()+".part"),old=new File(dir,target.getName()+".bak");
        try(FileOutputStream out=new FileOutputStream(stage)){out.write(contents);out.getFD().sync();}
        if(target.exists()){
            if(old.exists()&&!old.delete())throw new IOException("Резервная копия заблокирована");
            if(!target.renameTo(old)){stage.delete();throw new IOException("Не удалось сохранить старую версию");}
        }
        if(!stage.renameTo(target)){
            if(old.exists())old.renameTo(target);
            stage.delete();throw new IOException("Не удалось обновить книгу");
        }
        if(!backup&&old.exists())old.delete();
    }
    private static void scan(DocumentFile current,String relative,Scan result,int depth)throws IOException{
        if(depth>MAX_DEPTH)return;
        DocumentFile[] files=current.listFiles();
        for(DocumentFile file:files){
            String name=file.getName();if(name==null||name.startsWith("."))continue;
            String path=relative.isEmpty()?name:relative+"/"+name;
            if(file.isDirectory())scan(file,path,result,depth+1);
            else if(file.isFile()){
                result.files.put(path.toLowerCase(Locale.ROOT),file);
                String lower=name.toLowerCase(Locale.ROOT);
                if(lower.endsWith(".html")||lower.endsWith(".htm")){
                    if(result.html.size()>=MAX_CHAPTERS)throw new IOException("Слишком много HTML-глав (более "+MAX_CHAPTERS+")");
                    long size=file.length();if(size>MAX_CHAPTER_BYTES)throw new IOException("Слишком большой HTML: "+path);
                    result.total+=Math.max(0,size);
                    if(result.total>MAX_TOTAL_BYTES)throw new IOException("Общий размер глав слишком велик");
                    result.html.add(new Part(path,file));
                }
            }
        }
    }
    private static Scan scanTree(DocumentFile root)throws IOException{
        if(root==null||!root.isDirectory())throw new IOException("Выбрана не папка");
        Scan result=new Scan();scan(root,"",result,0);
        // Generated index and book.html must not be treated as additional chapters.
        boolean hasChapters=false;
        for(Part part:result.html){String name=part.path.toLowerCase(Locale.ROOT);
            if(!name.endsWith("/book.html")&&!name.equals("book.html")
                &&!name.endsWith("/index.html")&&!name.equals("index.html")){hasChapters=true;break;}
        }
        if(hasChapters)result.html.removeIf(part->{String n=part.path.toLowerCase(Locale.ROOT);
            return n.equals("book.html")||n.endsWith("/book.html")||n.equals("index.html")||n.endsWith("/index.html");});
        result.html.sort((a,b)->HtmlChapterOrder.compare(a.path,b.path));
        if(result.html.isEmpty())throw new IOException("В папке нет HTML-файлов");
        return result;
    }
    private static String sanitizeName(String text){String value=text==null?"":text.replaceAll("\\s+"," ").trim();return value.length()>180?value.substring(0,180):value;}
    private static String titleFrom(Document doc,String filename){
        Element heading=doc.selectFirst(".node-doc h1,.text-content h1,article h1,article h2,h1,h2");
        String text=heading!=null?sanitizeName(heading.text()):"";
        if(text.isEmpty())text=sanitizeName(doc.title());
        if(text.isEmpty()||text.equalsIgnoreCase("index"))text=filename.replaceFirst("(?i)\\.html?$","");
        return text;
    }
    private static String normalizedPath(String container,String relative){
        if(relative==null||relative.isEmpty()||relative.startsWith("/")||relative.contains(":")||relative.startsWith("//"))return "";
        String base="";int at=container.lastIndexOf('/');if(at>=0)base=container.substring(0,at+1);
        ArrayList<String> stack=new ArrayList<>();
        for(String p:(base+relative).replace('\\','/').split("/")){
            if(p.equals(".")||p.isEmpty())continue;
            if(p.equals("..")){if(stack.isEmpty())return "";stack.remove(stack.size()-1);}
            else stack.add(p);
        }
        StringBuilder joined=new StringBuilder();for(String item:stack){if(joined.length()>0)joined.append("/");joined.append(item);}return joined.toString().toLowerCase(Locale.ROOT);
    }
    private static void copyIllustrations(Context c,Scan scan,Part part,Document doc,File output)throws IOException{
        int images=0;
        for(Element image:doc.select("img[src]")){
            if(images++>=45){image.remove();continue;}
            String raw=image.attr("src").split("[?#]",2)[0];
            String resolved=normalizedPath(part.path,raw);
            DocumentFile found=scan.files.get(resolved);
            if(found==null||!found.isFile()||found.length()>15L*1024*1024)continue;
            String ext=resolved.toLowerCase(Locale.ROOT).replaceAll("^.*\\.","");
            if(!ext.matches("png|jpg|jpeg|webp|gif"))continue;
            try{
                byte[] data=readDocument(c,found,15L*1024*1024);
                // Content-addressed assets keep a previous HTML backup fully usable.
                String name=digest(data).substring(0,24)+"."+ext;
                File local=new File(output,"assets/"+name);
                if(!local.isFile()||local.length()!=data.length)atomic(local,data,false);
                image.attr("src","assets/"+name);
            }catch(Exception ex){android.util.Log.w("HtmlFolderLibrary","Illustration "+resolved,ex);}
        }
    }
    private static DocumentFile findCover(Scan scan){
        String[] names={"cover.jpg","cover.jpeg","cover.png","cover.webp","folder.jpg","poster.jpg"};
        for(String n:names){DocumentFile file=scan.files.get(n);if(file!=null)return file;}
        for(String n:names)for(String file:scan.files.keySet())if(file.endsWith("/"+n))return scan.files.get(file);
        return null;
    }
    private static String bookHtml(Context c,Scan scan,File target,String title,JSONArray recorded)throws IOException{
        StringBuilder html=new StringBuilder("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>");
        html.append(RanobeLibStorage.escape(title)).append("</title><style>body{max-width:880px;margin:24px auto;padding:20px;font:1.1em/1.75 Georgia,serif}img{max-width:100%;height:auto}section.ranobe-chapter{margin:30px 0}h2{page-break-before:always}@media(prefers-color-scheme:dark){body{background:#222;color:#eee}}</style></head><body>");
        html.append("<header><h1>").append(RanobeLibStorage.escape(title)).append("</h1></header>");
        int n=0;long parsedBytes=0;
        Safelist safe=Safelist.relaxed().addTags("h1","h2","h3","h4","h5","section","article","figure","figcaption","details","summary")
            .addAttributes("img","src","alt").preserveRelativeLinks(true);
        for(Part part:scan.html){
            byte[] bytes=readDocument(c,part.document,MAX_CHAPTER_BYTES);
            parsedBytes+=bytes.length;
            if(parsedBytes>MAX_TOTAL_BYTES)throw new IOException("Суммарный размер HTML-глав слишком велик");
            String page=BookLoader.decode(bytes);Document document=Jsoup.parse(page);
            // A folder containing only an already assembled book.html must keep its TOC.
            if(scan.html.size()==1&&part.path.equalsIgnoreCase("book.html")
                    &&!document.select("section.ranobe-chapter").isEmpty()){
                // Preserve an existing structural TOC, but never copy scripts or event handlers.
                Safelist combined=Safelist.relaxed().addTags("section","article","h1","h2","h3","h4","figure","figcaption")
                    .addAttributes("section","class","data-volume","data-number","data-branch","data-chapter-key")
                    .addAttributes("img","src","alt").preserveRelativeLinks(true);
                Document cleanBook=Jsoup.parseBodyFragment(Jsoup.clean(document.body().html(),"",combined));
                copyIllustrations(c,scan,part,cleanBook,target);
                String safeBody=cleanBook.body().html();
                JSONObject existing=new JSONObject();try{existing.put("path",part.path);existing.put("sha256",digest(bytes));}catch(Exception ignored){}
                recorded.put(existing);
                String documentTitle=document.title().isEmpty()?title:document.title();
                return "<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><title>"
                    +RanobeLibStorage.escape(documentTitle)+"</title></head><body>"+safeBody+"</body></html>";
            }
            Element block=document.selectFirst("div.node-doc.text-content,.node-doc.text-content,.chapter-content,.chapter__content,.reading-content,article,main,.text-content,body");
            if(block==null||block.text().trim().isEmpty())throw new IOException("В главе отсутствует текст: "+part.path);
            Document clean=Jsoup.parseBodyFragment(Jsoup.clean(block.html(),"",safe));
            clean.select("script,style,nav,footer,iframe,form,button").remove();
            copyIllustrations(c,scan,part,clean,target);
            String content=clean.body().html();
            if(Jsoup.parseBodyFragment(content).text().trim().length()<3)throw new IOException("В главе нет читаемого текста: "+part.path);
            String heading=titleFrom(document,part.path.substring(part.path.lastIndexOf('/')+1));
            html.append("<section class=\"ranobe-chapter\" data-chapter-key=\"")
                .append(RanobeLibStorage.escape(part.path)).append("\"><h2>")
                .append(RanobeLibStorage.escape(heading)).append("</h2>").append(content).append("</section>\n");
            JSONObject row=new JSONObject();try{row.put("path",part.path);row.put("sha256",digest(bytes));row.put("title",heading);}catch(Exception ignored){}
            recorded.put(row);n++;
        }
        html.append("</body></html>");
        return html.toString();
    }
    private static JSONObject oldManifest(File local){
        File index=manifest(local);
        if(!index.isFile()&&new File(local,"manifest.json.bak").isFile())
            new File(local,"manifest.json.bak").renameTo(index);
        try{return new JSONObject(readFile(index));}catch(Exception ex){return new JSONObject();}
    }
    private static void recoverBook(File folder){
        File file=new File(folder,"book.html"),backup=new File(folder,"book.html.bak");
        if(!file.isFile()&&backup.isFile())backup.renameTo(file);
    }
    private static String signature(Context c,Scan scan)throws IOException{
        MessageDigest md;
        try{md=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IOException(e);}
        ArrayList<String> sources=new ArrayList<>();
        for(Part part:scan.html)sources.add(part.path);
        // Covers and inline images may change without any HTML filename/size change.
        for(String path:scan.files.keySet())if(path.matches("(?i).+\\.(?:png|jpg|jpeg|webp|gif)"))sources.add(path);
        sources.sort(HtmlChapterOrder::compare);
        for(String name:sources){
            DocumentFile document=scan.files.get(name.toLowerCase(Locale.ROOT));
            if(document==null)throw new IOException("Исчез файл: "+name);
            long bound=name.toLowerCase(Locale.ROOT).endsWith(".html")||name.toLowerCase(Locale.ROOT).endsWith(".htm")
                ?MAX_CHAPTER_BYTES:15L*1024*1024;
            md.update(name.getBytes(StandardCharsets.UTF_8));md.update((byte)0);
            byte[] buf=new byte[16384];long nbytes=0;
            try(InputStream in=c.getContentResolver().openInputStream(document.getUri())){
                if(in==null)throw new IOException("Файл недоступен: "+name);
                int n;while((n=in.read(buf))!=-1){nbytes+=n;
                    if(nbytes>bound)throw new IOException("Файл слишком велик: "+name);
                    md.update(buf,0,n);
                }
            }
        }
        return digest(md.digest());
    }
    static Result importFolder(Context c,Uri tree)throws IOException{
        synchronized(IO_LOCK){return sync(c,tree,true);}
    }
    private static Result sync(Context ctx,Uri tree,boolean initial)throws IOException{
        DocumentFile source=DocumentFile.fromTreeUri(ctx,tree);
        Scan scan=scanTree(source);
        File local=folder(ctx,tree),book=new File(local,"book.html");
        recoverBook(local);
        String title=sanitizeName(source.getName());if(title.isEmpty())title="Книга из HTML";
        JSONObject previous=oldManifest(local);
        String current=signature(ctx,scan);
        String externalUri=previous.optString("externalUri","");
        boolean external=!externalUri.isEmpty();
        if(current.equals(previous.optString("fingerprint"))&&(book.isFile()||external))
            return new Result(external?externalUri:Uri.fromFile(book).toString(),
                previous.optString("title",title),scan.html.size(),false);
        String readingUri=external?externalUri:Uri.fromFile(book).toString();
        HtmlReadingAnchor.Snapshot anchor=(book.isFile()||external)?HtmlReadingAnchor.capture(ctx,readingUri):null;
        JSONArray chapters=new JSONArray();
        String assembled=bookHtml(ctx,scan,local,title,chapters);
        if(BookLoader.ranobeHtml(assembled,title).text.trim().isEmpty())throw new IOException("Пустая книга после сборки");
        // A transaction: write the new file only after ALL chapters have been parsed.
        atomic(book,assembled.getBytes(StandardCharsets.UTF_8),true);
        if(external){
            // Publish all newly referenced assets *before* the new book.html.
            publishAssets(ctx,previous,local);
            // If SAF writing fails, the preceding version is restored from rollback.
            publishExternal(ctx,externalUri,book,local);
        }
        try{
            JSONObject manifest=new JSONObject();manifest.put("sourceTree",tree.toString());
            if(external){
                manifest.put("externalUri",externalUri);
                manifest.put("externalTree",previous.optString("externalTree",""));
                manifest.put("externalDirectory",previous.optString("externalDirectory",""));
            }
            manifest.put("title",title);manifest.put("fingerprint",current);
            manifest.put("chapters",chapters);manifest.put("updatedAt",System.currentTimeMillis());
            atomic(manifest(local),manifest.toString(2).getBytes(StandardCharsets.UTF_8),true);
        }catch(Exception error){
            File backup=new File(local,"book.html.bak");
            if(backup.isFile()){book.delete();backup.renameTo(book);}
            throw new IOException("Не удалось сохранить индекс глав",error);
        }
        DocumentFile cover=findCover(scan);
        if(cover!=null){
            try{byte[] data=readDocument(ctx,cover,15L*1024*1024);atomic(new File(local,"cover.img"),data,false);}
            catch(Exception error){android.util.Log.w("HtmlFolderLibrary","Cover import",error);}
        }
        String uri=external?externalUri:Uri.fromFile(book).toString();
        MainActivity.invalidateBookCover(uri);
        if(anchor!=null)HtmlReadingAnchor.restore(ctx,uri,anchor,assembled);
        else{ReaderBookCache.invalidate(ctx,Uri.parse(uri));UnifiedBookSession.invalidate(uri);}
        // After migration keep the verified external copy; local temporary HTML is removable.
        if(external)book.delete();
        return new Result(uri,title,scan.html.size(),true);
    }
    static boolean manages(Context c,String bookUri){return localFolder(c,bookUri)!=null;}
    static void rememberTransfer(Context ctx,String oldUri,String newUri,Uri destination,String childName)throws IOException{
        File dir=localFolder(ctx,oldUri);if(dir==null)return;
        JSONObject index=oldManifest(dir);
        try{index.put("externalUri",newUri);
            index.put("externalTree",destination.toString());
            index.put("externalDirectory",childName);
            atomic(manifest(dir),index.toString(2).getBytes(StandardCharsets.UTF_8),true);
        }catch(Exception error){throw new IOException("Не удалось сохранить привязку перенесённой HTML-книги",error);}
        if(!ctx.getSharedPreferences("html_folder_relocated_v159",0).edit()
                .putString(newUri,dir.getName()).commit())
            throw new IOException("Не удалось сохранить новый адрес HTML-книги");
    }
    private static File localFolder(Context ctx,String uri){
        try{
            Uri parsed=Uri.parse(uri);
            if("content".equalsIgnoreCase(parsed.getScheme())){
                String key=ctx.getSharedPreferences("html_folder_relocated_v159",0).getString(uri,"");
                if(!key.matches("[0-9a-f]{32}"))return null;
                File dir=new File(root(ctx),key).getCanonicalFile();
                return manifest(dir).isFile()?dir:null;
            }
            if(!"file".equalsIgnoreCase(parsed.getScheme()))return null;
            File book=new File(parsed.getPath()).getCanonicalFile();
            File parent=book.getParentFile(),home=root(ctx).getCanonicalFile();
            if(!book.getName().equals("book.html")||parent==null||!parent.getParentFile().equals(home))return null;
            if(!manifest(parent).isFile())return null;
            return parent;
        }catch(Exception e){return null;}
    }
    static boolean syncIfChanged(Context c,String bookUri)throws IOException{
        return refresh(c,bookUri,false);
    }
    static boolean checkChanges(Context c,String bookUri)throws IOException{
        return refresh(c,bookUri,true);
    }
    private static boolean refresh(Context c,String bookUri,boolean strict)throws IOException{
        File dir=localFolder(c,bookUri);if(dir==null)return false;
        JSONObject index=oldManifest(dir);
        String origin=index.optString("sourceTree","");
        if(origin.isEmpty()){
            if(strict)throw new IOException("Нет ссылки на исходную папку HTML");
            return false;
        }
        try{
            synchronized(IO_LOCK){return sync(c,Uri.parse(origin),false).updated;}
        }catch(Exception error){
            // The generated book remains readable offline if the source SD card is absent.
            if(strict||!new File(dir,"book.html").isFile())
                throw error instanceof IOException?(IOException)error:new IOException(error);
            android.util.Log.w("HtmlFolderLibrary","Offline book retained; source unavailable",error);
            return false;
        }
    }
    static byte[] coverBytes(Context ctx,String bookUri){
        File folder=localFolder(ctx,bookUri);if(folder==null)return null;
        File image=new File(folder,"cover.img");
        if(!image.isFile()||image.length()<=0||image.length()>15L*1024*1024)return null;
        try(InputStream stream=new FileInputStream(image)){return readBounded(stream,15L*1024*1024);}
        catch(Exception ignored){return null;}
    }
}
