package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
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
    static String bookUri(Context c,String slug){return Uri.fromFile(book(c,slug)).toString();}
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
        if(backup.exists())backup.delete();
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
    static void rememberTitle(Context c,RanobeLibApi.Title title){try{writeAtomic(new File(folder(c,title.slug),"book.json"),title.json().toString(2));}catch(IOException | org.json.JSONException error){android.util.Log.w("RanobeLibStorage","Metadata",error);}}
    static RanobeLibApi.Title savedTitle(Context c,String slug){try{return RanobeLibApi.Title.fromJson(new JSONObject(readText(new File(folder(c,slug),"book.json"))));}catch(Exception ignored){return new RanobeLibApi.Title(slug,slug);}}
    static boolean hasCover(Context c,String slug){
        File f=cover(c,slug);
        if(!f.isFile()||f.length()<100||f.length()>6_000_000)return false;
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        BitmapFactory.decodeFile(f.getAbsolutePath(),bounds);
        return bounds.outWidth>0&&bounds.outHeight>0;
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
    static boolean repairMissingCover(Context c,String uri){
        try{
            Uri target=Uri.parse(uri);
            if(!"file".equalsIgnoreCase(target.getScheme()))return false;
            File source=new File(target.getPath()).getCanonicalFile(),base=root(c).getCanonicalFile();
            if(!"book.html".equals(source.getName())||!source.getPath().startsWith(base.getPath()+File.separator))return false;
            String slug=RanobeLibApi.validSlug(source.getParentFile().getName());
            if(hasCover(c,slug))return true;
            return ensureCover(c,savedTitle(c,slug),new RanobeLibApi());
        }catch(Exception error){android.util.Log.w("RanobeLibStorage","Cover repair",error);return false;}
    }
    static byte[] localCover(Context c,String uri){
        try{Uri target=Uri.parse(uri);if(!"file".equalsIgnoreCase(target.getScheme()))return null;
            File source=new File(target.getPath()).getCanonicalFile(), base=root(c).getCanonicalFile();
            if(!source.getPath().startsWith(base.getPath()+File.separator)||!"book.html".equals(source.getName()))return null;
            File image=new File(source.getParentFile(),"cover.jpg");if(!hasCover(c,source.getParentFile().getName()))return null;
            try(FileInputStream stream=new FileInputStream(image)){return BookLoader.readAll(stream);}
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
            if("complete".equals(j.status)&&new File(j.file).isFile())complete.add(j);
        }
        if(complete.isEmpty())return;
        RanobeLibApi.Title title=savedTitle(c,slug);StringBuilder html=new StringBuilder("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>");
        html.append(escape(title.title)).append("</title><meta name=\"author\" content=\"").append(escape(title.author)).append("\"><style>body{max-width:880px;margin:24px auto;padding:22px;font:1.12em/1.75 Georgia,serif}section.ranobe-chapter{margin-top:38px}h2{margin-top:2em}img{max-width:100%;height:auto}a{overflow-wrap:anywhere}@media(prefers-color-scheme:dark){body{background:#202020;color:#e5e5e5}}</style></head><body>");
        html.append("<header class=\"ranobe-book-header\"><h1>").append(escape(title.title)).append("</h1>");
        if(!title.author.isEmpty())html.append("<p>").append(escape(title.author)).append("</p>");
        if(hasCover(c,slug))html.append("<img src=\"cover.jpg\" alt=\"Обложка\" style=\"max-width:230px\">");
        html.append("</header>");
        for(Job j:complete){
            String document=readText(new File(j.file));Document parsed=Jsoup.parse(document);
            Element article=parsed.selectFirst("article");if(article==null)continue;
            String name=j.apiChapter().fullTitle();
            html.append("<section class=\"ranobe-chapter\" data-volume=\"").append(escape(j.volume)).append("\" data-number=\"").append(escape(j.number)).append("\" data-branch=\"").append(j.branch).append("\"><h2>").append(escape(name)).append("</h2>");
            html.append(article.html()).append("</section>\n");
        }
        html.append("</body></html>");
        writeAtomic(book(c,slug),html.toString());registerBook(c,title);
    }
    static synchronized void registerBook(Context c,RanobeLibApi.Title title){
        File file=book(c,title.slug);if(!file.isFile())return;String uri=Uri.fromFile(file).toString();
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
    static synchronized void deleteSeries(Context c,String slug){
        ArrayList<Job> remaining=new ArrayList<>();for(Job j:jobs(c))if(!slug.equals(j.slug))remaining.add(j);saveJobs(c,remaining);
        String uri=bookUri(c,slug);try{org.json.JSONArray source=new org.json.JSONArray(c.getSharedPreferences("reader",0).getString("library","[]"));org.json.JSONArray next=new org.json.JSONArray();for(int i=0;i<source.length();i++){JSONObject o=source.optJSONObject(i);if(o!=null&&!uri.equals(o.optString("uri")))next.put(o);}c.getSharedPreferences("reader",0).edit().putString("library",next.toString()).commit();}catch(Exception ignored){}
        deleteRecursive(folder(c,slug));
    }
    private static void deleteRecursive(File f){if(f.isDirectory()){File[] children=f.listFiles();if(children!=null)for(File child:children)deleteRecursive(child);}f.delete();}
}
