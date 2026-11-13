package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import android.os.Environment;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Durable per-title HTML storage, append-safe chapter cache, library registration. */
final class RanobeLibStorage {
    static final class Job {
        String slug="",title="",author="",volume="",number="",chapter="",status="queued",error="",file="";
        long branch=0,bytes=0;
        String id(){return slug+"|"+volume+"|"+number+"|"+branch;}
        RanobeLibApi.Chapter apiChapter(){return new RanobeLibApi.Chapter(volume,number,chapter,branch);}
        JSONObject json(){JSONObject j=new JSONObject();try{j.put("slug",slug);j.put("title",title);j.put("author",author);j.put("volume",volume);j.put("number",number);j.put("chapter",chapter);j.put("status",status);j.put("error",error);j.put("file",file);j.put("branch",branch);j.put("bytes",bytes);}catch(Exception ignored){}return j;}
        static Job fromJson(JSONObject o){Job j=new Job();j.slug=o.optString("slug");j.title=o.optString("title");j.author=o.optString("author");j.volume=o.optString("volume");j.number=o.optString("number");j.chapter=o.optString("chapter");j.status=o.optString("status","queued");j.error=o.optString("error");j.file=o.optString("file");j.branch=o.optLong("branch");j.bytes=o.optLong("bytes");return j;}
        static Job create(RanobeLibApi.Title title,RanobeLibApi.Chapter chapter){Job job=new Job();job.slug=title.slug;job.title=title.title;job.author=title.author;job.volume=chapter.volume;job.number=chapter.number;job.chapter=chapter.name;job.branch=chapter.branch;return job;}
    }
    private RanobeLibStorage(){}
    static File root(Context c){File base=c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(base==null)base=c.getFilesDir();File dir=new File(base,"Books/RanobeLIB");if(!dir.isDirectory())dir.mkdirs();return dir;}
    static File folder(Context c,String slug){File dir=new File(root(c),RanobeLibApi.validSlug(slug));if(!dir.isDirectory())dir.mkdirs();return dir;}
    static File book(Context c,String slug){return new File(folder(c,slug),"book.html");}
    static File cover(Context c,String slug){return new File(folder(c,slug),"cover.jpg");}
    static String bookUri(Context c,String slug){
        DocumentFile external=externalFile(c,slug,"book.html");
        return external!=null&&external.isFile()?external.getUri().toString():Uri.fromFile(book(c,slug)).toString();
    }
    static String externalDirUri(Context c,String slug){return c.getSharedPreferences("ranobelib_external_v158",0).getString(slug,"");}
    static DocumentFile externalDir(Context c,String slug){
        android.content.SharedPreferences p=c.getSharedPreferences("ranobelib_external_v158",0);
        String parent=p.getString(slug+":root","");
        String child=p.getString(slug+":name","");
        if(!parent.isEmpty()&&!child.isEmpty())try{
            DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(parent));
            DocumentFile result=root==null?null:root.findFile(child);
            return result!=null&&result.isDirectory()?result:null;
        }catch(Exception ignored){}
        String old=externalDirUri(c,slug);
        return old.isEmpty()?null:SafTreeDirectory.resolve(c,Uri.parse(old));
    }
    static void setExternalDir(Context c,String slug,String grantUri,String childName,String childUri)throws IOException {
        if(!c.getSharedPreferences("ranobelib_external_v158",0).edit()
            .putString(slug,childUri).putString(slug+":root",grantUri)
            .putString(slug+":name",childName).commit())
            throw new IOException("Не удалось сохранить новую папку RanobeLIB");
    }
    private static DocumentFile externalFile(Context c,String slug,String relative){
        DocumentFile current=externalDir(c,slug);if(current==null)return null;
        for(String part:relative.split("/")){
            if(part.isEmpty()||part.equals(".")||part.equals(".."))return null;
            current=current.findFile(part);if(current==null)return null;
        }
        return current;
    }
    static boolean stored(Context c,String location){
        if(location==null||location.isEmpty())return false;
        if(location.startsWith("content://"))try{
            DocumentFile doc=DocumentFile.fromSingleUri(c,Uri.parse(location));
            return doc!=null&&doc.isFile()&&doc.length()>0;
        }catch(Exception ex){return false;}
        return new File(location).isFile();
    }
    static String readStored(Context c,String location)throws IOException{
        if(!location.startsWith("content://"))return readText(new File(location));
        try(java.io.InputStream in=c.getContentResolver().openInputStream(Uri.parse(location));
            ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new IOException("HTML недоступен: "+location);
            byte[] buffer=new byte[16384];int n;long size=0;
            while((n=in.read(buffer))!=-1){size+=n;if(size>40_000_000L)throw new IOException("HTML слишком велик");out.write(buffer,0,n);}
            return out.toString("UTF-8");
        }
    }
    private static DocumentFile publish(Context c,String slug,File source,String relative)throws Exception{
        DocumentFile dir=externalDir(c,slug);
        if(dir==null)throw new IOException("Папка ранобэ недоступна");
        String[] parts=relative.split("/");
        for(int i=0;i<parts.length-1;i++)dir=SafeDownloadTransfer.directory(dir,parts[i]);
        DocumentFile existing=dir.findFile(parts[parts.length-1]);
        if(existing==null)return SafeDownloadTransfer.copyFile(c,source,dir,parts[parts.length-1],new SafeDownloadTransfer.Result());
        return SafeDownloadTransfer.replaceFile(c,source,dir,parts[parts.length-1]);
    }

    static String safe(String v){String result=v.replaceAll("[^A-Za-z0-9._-]","_");return result.isEmpty()?"0":result.length()>90?result.substring(0,90):result;}
    static File chapterFile(Context c,Job j){File volume=new File(folder(c,j.slug),"volumes/v"+safe(j.volume));if(!volume.isDirectory())volume.mkdirs();return new File(volume,"chapter_"+safe(j.number)+"_b"+j.branch+".html");}
    private static File jobsFile(Context c){return new File(c.getFilesDir(),"ranobelib_jobs_v1.json");}
    private static String readText(File f) throws IOException {if(!f.isFile()||f.length()>40_000_000L)throw new IOException("Файл отсутствует или слишком велик");try(FileInputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    private static void writeAtomic(File file,String data) throws IOException {
        File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Нет доступа к папке");
        File part=new File(file.getAbsolutePath()+".part");try(FileOutputStream out=new FileOutputStream(part)){out.write(data.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        File backup=new File(file.getAbsolutePath()+".bak");if(backup.exists())backup.delete();
        if(file.exists()&&!file.renameTo(backup)){part.delete();throw new IOException("Не удалось обновить " +file.getName());}
        if(!part.renameTo(file)){if(backup.exists())backup.renameTo(file);throw new IOException("Не удалось сохранить "+file.getName());}
        // Keep one verified rollback file for HTML book updates.
        if(backup.exists()&&!"book.html".equals(file.getName()))backup.delete();
    }
    private static synchronized ArrayList<Job> readJobs(Context c){ArrayList<Job> out=new ArrayList<>();File source=jobsFile(c);if(!source.isFile())source=new File(source.getAbsolutePath()+".bak");try{JSONArray a=new JSONArray(readText(source));for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null)out.add(Job.fromJson(o));}}catch(Exception ignored){}return out;}
    static synchronized ArrayList<Job> jobs(Context c){return readJobs(c);}
    static synchronized Job find(Context c,String id){for(Job j:readJobs(c))if(j.id().equals(id))return j;return null;}
    static synchronized ArrayList<Job> series(Context c,String slug){ArrayList<Job> result=new ArrayList<>();for(Job j:readJobs(c))if(slug.equals(j.slug))result.add(j);result.sort(RanobeLibStorage::compare);return result;}
    static int compare(Job a,Job b){int v=Double.compare(numeric(a.volume),numeric(b.volume));if(v!=0)return v;v=Double.compare(numeric(a.number),numeric(b.number));return v!=0?v:Long.compare(a.branch,b.branch);}
    private static double numeric(String s){try{return Double.parseDouble(s.replace(',','.'));}catch(Exception ignored){return 0;}}
    static synchronized void put(Context c,Job job){ArrayList<Job> all=readJobs(c);boolean replaced=false;for(int i=0;i<all.size();i++)if(all.get(i).id().equals(job.id())){all.set(i,job);replaced=true;break;}if(!replaced)all.add(job);saveJobs(c,all);}
    static synchronized void putAll(Context c,List<Job> jobs){ArrayList<Job> all=readJobs(c);java.util.HashMap<String,Integer> positions=new java.util.HashMap<>();for(int i=0;i<all.size();i++)positions.put(all.get(i).id(),i);
        for(Job job:jobs){Integer pos=positions.get(job.id());if(pos==null){positions.put(job.id(),all.size());all.add(job);}else all.set(pos,job);}saveJobs(c,all);}
    private static void saveJobs(Context c,List<Job> jobs){JSONArray a=new JSONArray();for(Job j:jobs)a.put(j.json());try{writeAtomic(jobsFile(c),a.toString());}catch(IOException ex){android.util.Log.e("RanobeLibStorage","Jobs not saved",ex);throw new IllegalStateException("Не удалось сохранить очередь RanobeLIB",ex);}}
    static void rememberTitle(Context c,RanobeLibApi.Title title){try{
        File local=new File(folder(c,title.slug),"book.json");
        writeAtomic(local,title.json().toString(2));
        if(externalDir(c,title.slug)!=null){publish(c,title.slug,local,"book.json");local.delete();}
    }catch(Exception error){android.util.Log.w("RanobeLibStorage","Metadata",error);}}
    static RanobeLibApi.Title savedTitle(Context c,String slug){
        try{
            File local=new File(folder(c,slug),"book.json");
            if(local.isFile())return RanobeLibApi.Title.fromJson(new JSONObject(readText(local)));
            DocumentFile stored=externalFile(c,slug,"book.json");
            if(stored!=null)return RanobeLibApi.Title.fromJson(new JSONObject(readStored(c,stored.getUri().toString())));
        }catch(Exception ignored){}
        return new RanobeLibApi.Title(slug,slug);
    }
    static boolean hasCover(Context c,String slug){
        File f=cover(c,slug);
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        if(f.isFile()&&f.length()>=100&&f.length()<=6_000_000){
            BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
            if(bounds.outWidth>0&&bounds.outHeight>0)return true;
        }
        DocumentFile remote=externalFile(c,slug,"cover.jpg");
        if(remote==null||!remote.isFile()||remote.length()<100||remote.length()>6_000_000)return false;
        try(java.io.InputStream in=c.getContentResolver().openInputStream(remote.getUri())){
            BitmapFactory.decodeStream(in,null,bounds);
            return bounds.outWidth>0&&bounds.outHeight>0;
        }catch(Exception e){return false;}
    }
    /** The retry stamp avoids hundreds of requests during a whole-book download. */
    static boolean ensureCover(Context c,RanobeLibApi.Title t,RanobeLibApi api){return ensureCover(c,t,api,false);}
    static synchronized boolean ensureCover(Context c,RanobeLibApi.Title t,RanobeLibApi api,boolean forced){
        if(t==null||t.slug==null)return false;
        try{RanobeLibApi.validSlug(t.slug);}catch(Exception ignored){return false;}
        if(hasCover(c,t.slug))return true;
        android.content.SharedPreferences prefs=c.getSharedPreferences("ranobelib_cover_attempts_v157",0);
        long now=System.currentTimeMillis();String key="cover_attempt:"+t.slug;
        if(!forced&&now-prefs.getLong(key,0)<15*60*1000L)return false;
        prefs.edit().putLong(key,now).apply();
        java.util.LinkedHashSet<String> candidates=new java.util.LinkedHashSet<>();
        candidates.addAll(RanobeLibCoverUrls.candidateUrls(t.cover));
        // A prior release might have stored an obsolete cover URL. Query fresh metadata.
        if(candidates.isEmpty()||forced){
            try{
                RanobeLibApi.Title remote=api.title(t.slug);
                candidates.addAll(RanobeLibCoverUrls.candidateUrls(remote.cover));
                if(t.title.isEmpty()||t.title.equals(t.slug))t.title=remote.title;
                if(t.author.isEmpty())t.author=remote.author;
                if(t.description.isEmpty())t.description=remote.description;
            }catch(Exception error){android.util.Log.d("RanobeLibStorage","Cover metadata unavailable: "+error.getMessage());}
        }
        // The current website renders the actual image as img.cover__img on the title page.
        // Try the page whenever the metadata URL fails, without relying on a hard-coded book.
        boolean inspectedPage=false,refreshedMetadata=candidates.isEmpty()||forced;
        java.util.HashSet<String> attempted=new java.util.HashSet<>();
        while(true){
            for(String url:new java.util.ArrayList<>(candidates)){
                if(!attempted.add(url))continue;
                try{
                    byte[] image=api.coverBytes(url);
                    BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;
                    BitmapFactory.decodeByteArray(image,0,image.length,info);
                    if(info.outWidth<=0||info.outHeight<=0)throw new IOException("Обложка не декодируется");
                    BitmapFactory.Options scale=new BitmapFactory.Options();int step=1;
                    while(info.outWidth/step>900||info.outHeight/step>1400)step*=2;
                    scale.inSampleSize=step;
                    android.graphics.Bitmap bitmap=BitmapFactory.decodeByteArray(image,0,image.length,scale);
                    if(bitmap==null)throw new IOException("Не удалось прочитать обложку");
                    File target=cover(c,t.slug),tmp=new File(target.getAbsolutePath()+".part");
                    try(FileOutputStream out=new FileOutputStream(tmp)){
                        if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,out))
                            throw new IOException("Ошибка кодирования обложки");
                        out.getFD().sync();
                    }finally{bitmap.recycle();}
                    if(target.exists()&&!target.delete())throw new IOException("Нельзя заменить повреждённую обложку");
                    if(!tmp.renameTo(target))throw new IOException("Не удалось сохранить cover.jpg");
                    if(externalDir(c,t.slug)!=null){
                        publish(c,t.slug,target,"cover.jpg");target.delete();
                    }
                    t.cover=RanobeLibApi.coverUrl(url);
                    rememberTitle(c,t);
                    prefs.edit().remove(key).apply();
                    // Covers saved after the original HTML book must also appear inside it.
                    if(book(c,t.slug).isFile())try{rebuildBook(c,t.slug);}catch(IOException ex){
                        android.util.Log.w("RanobeLibStorage","Cover saved; HTML refresh deferred",ex);
                    }
                    return true;
                }catch(Exception error){android.util.Log.w("RanobeLibStorage","Cover URL failed: "+url,error);}
            }
            if(!refreshedMetadata){
                refreshedMetadata=true;
                try{
                    RanobeLibApi.Title remote=api.title(t.slug);
                    candidates.addAll(RanobeLibCoverUrls.candidateUrls(remote.cover));
                }catch(Exception error){android.util.Log.w("RanobeLibStorage","Cover metadata refresh",error);}
                continue;
            }
            if(inspectedPage)break;
            inspectedPage=true;
            try{
                String found=api.coverFromPage(t.slug);
                candidates.addAll(RanobeLibCoverUrls.candidateUrls(found));
            }catch(Exception error){android.util.Log.w("RanobeLibStorage","Cover HTML fallback unavailable",error);}
        }
        return false;
    }
    /** On-demand repair for previously downloaded HTML books; called on a background thread. */
    static String slugForBookUri(Context c,String uri){
        try{
            Uri parsed=Uri.parse(uri);
            if("file".equalsIgnoreCase(parsed.getScheme())){
                File source=new File(parsed.getPath()).getCanonicalFile(),base=root(c).getCanonicalFile();
                if("book.html".equals(source.getName())&&source.getPath().startsWith(base.getPath()+File.separator))
                    return RanobeLibApi.validSlug(source.getParentFile().getName());
            }
            java.util.Map<String,?> saved=c.getSharedPreferences("ranobelib_external_v158",0).getAll();
            for(String slug:saved.keySet()){
                if(slug.endsWith(":root")||slug.endsWith(":name"))continue;
                if(uri.equals(bookUri(c,slug)))return slug;
            }
        }catch(Exception ignored){}
        return "";
    }
    static boolean repairMissingCover(Context c,String uri){
        try{
            String slug=slugForBookUri(c,uri);if(slug.isEmpty())return false;
            if(hasCover(c,slug))return true;
            return ensureCover(c,savedTitle(c,slug),new RanobeLibApi());
        }catch(Exception error){android.util.Log.w("RanobeLibStorage","Cover repair",error);return false;}
    }
    static byte[] localCover(Context c,String uri){
        try{
            String slug=slugForBookUri(c,uri);if(slug.isEmpty()||!hasCover(c,slug))return null;
            DocumentFile remote=externalFile(c,slug,"cover.jpg");
            if(remote!=null&&remote.isFile()){
                try(java.io.InputStream stream=c.getContentResolver().openInputStream(remote.getUri())){
                    return stream==null?null:BookLoader.readAll(stream);
                }
            }
            try(FileInputStream stream=new FileInputStream(cover(c,slug))){return BookLoader.readAll(stream);}
        }catch(Exception ignored){return null;}
    }
    /** Make chapter illustrations offline where the publisher exposes safe public URLs. */
    static String localizeImages(Context c,Job job,String html,RanobeLibApi api){
        Document doc=Jsoup.parseBodyFragment(html);int index=0;
        for(Element img:doc.select("img[src]")){
            String src=img.attr("src");if(++index>15){img.remove();continue;}
            // The HTML sanitiser already restricts scripts; only publisher CDN images can be saved.
            if(src.startsWith("/"))src="https://ranobelib.me"+src;
            if(!src.startsWith("https://")){img.remove();continue;}
            String valid=RanobeLibApi.coverUrl(src);if(valid.isEmpty()){img.remove();continue;}
            try{byte[] bytes=api.coverBytes(valid);BitmapFactory.Options info=new BitmapFactory.Options();info.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,info);
                if(info.outWidth<=0||info.outHeight<=0)throw new IOException("Некорректное изображение");
                String kind=bytes.length>3&&(bytes[0]&255)==0xff?"jpg":bytes.length>8&&bytes[0]==(byte)0x89?"png":bytes.length>12&&bytes[0]=='R'?"webp":"img";
                String code=hash(valid).substring(0,20);String name="illustration_"+code+"."+kind;
                File folder=new File(folder(c,job.slug),"images");if(!folder.exists())folder.mkdirs();File output=new File(folder,name);
                if(!output.isFile()){File part=new File(folder,name+".part");try(FileOutputStream out=new FileOutputStream(part)){out.write(bytes);}if(!part.renameTo(output))part.delete();}
                img.attr("src","images/"+name);
            }catch(Exception error){img.remove();}
        }
        return doc.body().html();
    }
    private static String hash(String s){try{MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] sum=md.digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte value:sum)b.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return b.toString();}catch(Exception ignored){return Integer.toHexString(s.hashCode());}}
    static void saveChapter(Context ctx,Job job,String html) throws IOException {
        String title=escape(job.apiChapter().fullTitle());
        // Base is relative to volumes/vX/chapter_Y.html (the book root is two levels up).
        String document="<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><base href=\"../../\"><title>"+title+"</title></head><body><h1>"+title+"</h1><article>"+html+"</article></body></html>";
        File file=chapterFile(ctx,job);writeAtomic(file,document);job.file=file.getAbsolutePath();job.bytes=file.length();
    }
    static String escape(String v){return v==null?"":v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    /** Deterministic book assembly; title/cover paths persist as chapters are appended. */
    static synchronized void rebuildBook(Context c,String slug) throws IOException {
        ArrayList<Job> complete=new ArrayList<>();for(Job j:series(c,slug)){
            if("complete".equals(j.status)&&stored(c,j.file))complete.add(j);
        }
        if(complete.isEmpty())return;
        RanobeLibApi.Title title=savedTitle(c,slug);StringBuilder html=new StringBuilder("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>");
        html.append(escape(title.title)).append("</title><meta name=\"author\" content=\"").append(escape(title.author)).append("\"><style>body{max-width:880px;margin:24px auto;padding:22px;font:1.12em/1.75 Georgia,serif}section.ranobe-chapter{margin-top:38px}h2{margin-top:2em}img{max-width:100%;height:auto}a{overflow-wrap:anywhere}@media(prefers-color-scheme:dark){body{background:#202020;color:#e5e5e5}}</style></head><body>");
        html.append("<header class=\"ranobe-book-header\"><h1>").append(escape(title.title)).append("</h1>");
        if(!title.author.isEmpty())html.append("<p>").append(escape(title.author)).append("</p>");
        if(hasCover(c,slug))html.append("<img src=\"cover.jpg\" alt=\"Обложка\" style=\"max-width:230px\">");
        html.append("</header>");
        for(Job j:complete){
            String document=readStored(c,j.file);Document parsed=Jsoup.parse(document);
            // User replacements may be standalone translated HTML pages rather
            // than the <article> wrapper generated by RanobeLibDownloadWorker.
            Element article=parsed.selectFirst("div.node-doc.text-content,.node-doc.text-content,"
                +".chapter-content,.chapter__content,.reading-content,article,.text-content,main,body");
            if(article==null||article.text().trim().length()<12)
                throw new IOException("HTML-глава повреждена или не содержит текста: "+j.file);
            org.jsoup.safety.Safelist safe=org.jsoup.safety.Safelist.relaxed()
                .addTags("h1","h2","h3","h4","figure","figcaption","blockquote","details","summary")
                .addAttributes("img","src","alt").preserveRelativeLinks(true);
            String chapterBody=Jsoup.clean(article.html(),"",safe);
            if(Jsoup.parseBodyFragment(chapterBody).text().trim().length()<12)
                throw new IOException("В HTML-главе не удалось извлечь основной текст: "+j.file);
            String name=j.apiChapter().fullTitle();
            html.append("<section class=\"ranobe-chapter\" data-volume=\"").append(escape(j.volume)).append("\" data-number=\"").append(escape(j.number)).append("\" data-branch=\"").append(j.branch).append("\"><h2>").append(escape(name)).append("</h2>");
            html.append(chapterBody).append("</section>\n");
        }
        html.append("</body></html>");
        File local=book(c,slug);writeAtomic(local,html.toString());
        if(externalDir(c,slug)!=null){
            try{
                // Publish new chapter HTML and illustrations before replacing book.html.
                for(Job j:complete)if(!j.file.startsWith("content://")&&new File(j.file).isFile()){
                    File src=new File(j.file);
                    DocumentFile remote=publish(c,slug,src,"volumes/v"+safe(j.volume)+"/"+src.getName());
                    j.file=remote.getUri().toString();put(c,j);src.delete();
                }
                File images=new File(folder(c,slug),"images");File[] pictures=images.listFiles();
                if(pictures!=null)for(File image:pictures)if(image.isFile()&&!image.getName().endsWith(".part")){
                    publish(c,slug,image,"images/"+image.getName());image.delete();
                }
                DocumentFile cover=externalFile(c,slug,"cover.jpg");
                File localCover=cover(c,slug);
                if(localCover.isFile()&&cover==null){publish(c,slug,localCover,"cover.jpg");localCover.delete();}
                publish(c,slug,local,"book.html");local.delete();
            }catch(Exception error){throw new IOException("Не удалось обновить перенесённую HTML-книгу",error);}
        }
        registerBook(c,title);
    }
    static synchronized void registerBook(Context c,RanobeLibApi.Title title){
        String uri=bookUri(c,title.slug);
        if(!stored(c,uri.startsWith("file:")?new File(Uri.parse(uri).getPath()).getAbsolutePath():uri))return;
        android.content.SharedPreferences prefs=c.getSharedPreferences("reader",0);
        try{JSONArray saved=new JSONArray(prefs.getString("library","[]"));JSONArray updated=new JSONArray();boolean has=false;
            for(int i=0;i<saved.length();i++){JSONObject old=saved.optJSONObject(i);if(old==null)continue;
                if(uri.equals(old.optString("uri"))){has=true;old.put("title",BookTitleStore.title(c,BookTitleStore.TEXT,uri,title.title));if(!title.author.isEmpty())old.put("author",title.author);old.put("format","HTML");if(old.optString("excerpt").isEmpty())old.put("excerpt",title.description);}
                updated.put(old);
            }
            if(!has){MainActivity.BookMeta meta=new MainActivity.BookMeta();meta.uri=uri;meta.title=BookTitleStore.title(c,BookTitleStore.TEXT,uri,title.title);meta.author=title.author;meta.excerpt=title.description;meta.format="HTML";updated.put(meta.toJson());}
            prefs.edit().putString("library",updated.toString()).commit();
        }catch(Exception error){android.util.Log.e("RanobeLibStorage","Library registration",error);}
    }
    /** Detect manually replaced chapter HTML, even if filenames and sizes are identical.
     * Rebuilds book.html, keeps .bak and remaps the current reading position.
     */
    static boolean syncManualEdits(Context c,String bookUri)throws IOException{
        String slug=slugForBookUri(c,bookUri);if(slug.isEmpty())return false;
        ArrayList<Job> entries=series(c,slug);
        java.security.MessageDigest md;
        try{md=java.security.MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IOException(e);}
        int count=0;
        for(Job job:entries){
            if(!"complete".equals(job.status)||!stored(c,job.file))continue;
            md.update(job.id().getBytes(StandardCharsets.UTF_8));md.update((byte)0);
            byte[] buffer=new byte[16384];long size=0;
            try(java.io.InputStream input=job.file.startsWith("content://")
                    ?c.getContentResolver().openInputStream(Uri.parse(job.file))
                    :new FileInputStream(new File(job.file))){
                if(input==null)throw new IOException("Глава недоступна: "+job.file);
                int n;while((n=input.read(buffer))!=-1){size+=n;
                    if(size>40_000_000L)throw new IOException("HTML-глава слишком велика");
                    md.update(buffer,0,n);
                }
            }
            count++;
        }
        if(count==0)return false;
        String signature=hash(java.util.Arrays.toString(md.digest()));
        android.content.SharedPreferences settings=c.getSharedPreferences("ranobelib_html_sync_v159",0);
        String storedSignature=settings.getString(slug,"");
        if(storedSignature.equals(signature))return false;
        HtmlReadingAnchor.Snapshot old=HtmlReadingAnchor.capture(c,bookUri);
        if(externalDir(c,slug)!=null){
            // The SAF publisher rotates its own temporary files; keep a recoverable
            // copy of the previous assembled text when user-supplied chapters change.
            File backup=new File(folder(c,slug),"book.html.bak");
            File part=new File(backup.getAbsolutePath()+".part");
            try(java.io.InputStream input=c.getContentResolver().openInputStream(Uri.parse(bookUri));
                java.io.FileOutputStream out=new java.io.FileOutputStream(part)){
                if(input==null)throw new IOException("Не удалось создать резервную копию книги");
                byte[] bytes=new byte[32768];int n;long size=0;
                while((n=input.read(bytes))!=-1){size+=n;
                    if(size>150_000_000L)throw new IOException("HTML-книга слишком велика для копирования");
                    out.write(bytes,0,n);
                }
                out.getFD().sync();
            }
            if(backup.exists()&&!backup.delete())throw new IOException("Резервная копия защищена от удаления");
            if(!part.renameTo(backup))throw new IOException("Не удалось сохранить резервную HTML-копию");
        }
        synchronized(RanobeLibStorage.class){
            // Do not overwrite the existing assembled book when its chapters disappeared.
            rebuildBook(c,slug);
        }
        String now=bookUri(c,slug);
        try{
            String html=readStored(c,now.startsWith("file:")
                ?new File(Uri.parse(now).getPath()).getAbsolutePath():now);
            HtmlReadingAnchor.restore(c,now,old,html);
        }catch(Exception error){throw new IOException("Книга пересобрана, но проверка положения не удалась",error);}
        if(!settings.edit().putString(slug,signature).commit())throw new IOException("Не удалось сохранить контрольную сумму глав");
        return true;
    }
    static synchronized void deleteSeries(Context c,String slug){
        ArrayList<Job> remaining=new ArrayList<>();for(Job j:jobs(c))if(!slug.equals(j.slug))remaining.add(j);saveJobs(c,remaining);
        String uri=bookUri(c,slug);try{org.json.JSONArray source=new org.json.JSONArray(c.getSharedPreferences("reader",0).getString("library","[]"));org.json.JSONArray next=new org.json.JSONArray();for(int i=0;i<source.length();i++){JSONObject o=source.optJSONObject(i);if(o!=null&&!uri.equals(o.optString("uri")))next.put(o);}c.getSharedPreferences("reader",0).edit().putString("library",next.toString()).commit();}catch(Exception ignored){}
        DocumentFile remote=externalDir(c,slug);
        if(remote!=null)remote.delete();
        c.getSharedPreferences("ranobelib_external_v158",0).edit().remove(slug).remove(slug+":root").remove(slug+":name").commit();
        deleteRecursive(folder(c,slug));
    }
    private static void deleteRecursive(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteRecursive(child);}f.delete();}
}
