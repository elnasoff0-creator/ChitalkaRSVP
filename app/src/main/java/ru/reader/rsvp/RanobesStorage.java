package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;

/** Durable independent Ranobes chapter cache. One output directory per book/language. */
final class RanobesStorage {
    private static final String STORE="ranobes_jobs_v163",TITLES="ranobes_titles_v163";
    static final class Job {
        String bookId="",language="en",bookUrl="",bookTitle="",author="",cover="",chapterId="",chapterUrl="",title="",volume="1",status="queued",error="",file="";
        int index;long bytes;
        String sourceKey(){return bookId+":"+language;}
        String id(){return sourceKey()+"|"+chapterId;}
        JSONObject json(){JSONObject v=new JSONObject();try{v.put("bookId",bookId);v.put("language",language);v.put("bookUrl",bookUrl);v.put("bookTitle",bookTitle);v.put("author",author);v.put("cover",cover);v.put("chapterId",chapterId);v.put("chapterUrl",chapterUrl);v.put("title",title);v.put("volume",volume);v.put("status",status);v.put("error",error);v.put("file",file);v.put("index",index);v.put("bytes",bytes);}catch(Exception ignored){}return v;}
        static Job from(JSONObject v){Job j=new Job();j.bookId=v.optString("bookId");j.language=v.optString("language","en");j.bookUrl=v.optString("bookUrl");j.bookTitle=v.optString("bookTitle");j.author=v.optString("author");j.cover=v.optString("cover");j.chapterId=v.optString("chapterId");j.chapterUrl=v.optString("chapterUrl");j.title=v.optString("title");j.volume=v.optString("volume","1");j.status=v.optString("status","queued");j.error=v.optString("error");j.file=v.optString("file");j.index=v.optInt("index");j.bytes=v.optLong("bytes");return j;}
        static Job make(RanobesApi.Title b,RanobesApi.Chapter ch,String lang){Job j=new Job();j.bookId=b.id;j.bookUrl=b.url;j.bookTitle=b.title;j.author=b.author;j.cover=b.cover;j.language=lang;j.chapterId=ch.id;j.chapterUrl=ch.url;j.title=ch.title;j.volume=ch.volume;j.index=ch.index;return j;}
    }
    static boolean validKey(String source){return source!=null&&source.matches("[0-9]{3,12}:(en|ru)");}
    static ArrayList<Job> jobs(Context c){ArrayList<Job> out=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences("reader",0).getString(STORE,"[]"));for(int i=0;i<a.length();i++){JSONObject j=a.optJSONObject(i);if(j!=null){Job value=Job.from(j);if(validKey(value.sourceKey()))out.add(value);}}}catch(Exception ignored){}return out;}
    static synchronized void put(Context c,Job j){if(!validKey(j.sourceKey()))return;ArrayList<Job> jobs=jobs(c);boolean found=false;
        for(int i=0;i<jobs.size();i++)if(jobs.get(i).id().equals(j.id())){jobs.set(i,j);found=true;break;}
        if(!found)jobs.add(j);JSONArray a=new JSONArray();for(Job b:jobs)a.put(b.json());c.getSharedPreferences("reader",0).edit().putString(STORE,a.toString()).commit();}
    static synchronized void putAll(Context c,ArrayList<Job> incoming){if(incoming.isEmpty())return;ArrayList<Job> jobs=jobs(c);java.util.HashMap<String,Integer> index=new java.util.HashMap<>();for(int i=0;i<jobs.size();i++)index.put(jobs.get(i).id(),i);
        for(Job j:incoming){if(!validKey(j.sourceKey()))continue;Integer pos=index.get(j.id());if(pos==null){index.put(j.id(),jobs.size());jobs.add(j);}else jobs.set(pos,j);}
        JSONArray a=new JSONArray();for(Job j:jobs)a.put(j.json());c.getSharedPreferences("reader",0).edit().putString(STORE,a.toString()).commit();}
    static Job find(Context c,String id){for(Job j:jobs(c))if(j.id().equals(id))return j;return null;}
    static ArrayList<Job> jobs(Context c,String key){ArrayList<Job> result=new ArrayList<>();if(!validKey(key))return result;
        for(Job j:jobs(c))if(key.equals(j.sourceKey()))result.add(j);
        result.sort(Comparator.comparingInt(a->a.index));return result;}
    static String title(Context c,String key){return c.getSharedPreferences(TITLES,0).getString(key+":title","");}
    static void remember(Context c,RanobesApi.Title title){c.getSharedPreferences(TITLES,0).edit()
        .putString(title.id+":title",title.title).putString(title.id+":cover",title.cover)
        .putString(title.id+":author",title.author).putString(title.id+":description",title.description).apply();}
    static String coverSource(Context c,String id){return c.getSharedPreferences(TITLES,0).getString(id+":cover","");}
    static String author(Context c,String id){return c.getSharedPreferences(TITLES,0).getString(id+":author","");}
    static String description(Context c,String id){return c.getSharedPreferences(TITLES,0).getString(id+":description","");}
    static File root(Context c){File base=c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(base==null)base=c.getFilesDir();File dir=new File(base,"Books/Ranobes");if(!dir.isDirectory())dir.mkdirs();return dir;}
    static File folder(Context c,String source){if(!validKey(source))throw new IllegalArgumentException("Неизвестная книга Ranobes");
        File dir=new File(new File(root(c),source.split(":")[0]),source.split(":")[1]);if(!dir.isDirectory())dir.mkdirs();return dir;}
    static File book(Context c,String source){return new File(folder(c,source),"book.html");}
    static File cover(Context c,String source){return new File(folder(c,source),"cover.jpg");}
    static String keyForBookUri(Context c,String uri){
        if(uri==null||uri.isEmpty())return "";
        for(Job j:jobs(c))if(validKey(j.sourceKey())){
            String key=j.sourceKey();
            if(uri.equals(Uri.fromFile(book(c,key)).toString())||uri.equals(bookUri(c,key)))return key;
        }
        return "";
    }
    static byte[] localCover(Context c,String uri){
        String key=keyForBookUri(c,uri);if(key.isEmpty())return null;
        try{
            File image=cover(c,key);
            if(image.isFile()&&image.length()>100&&image.length()<8*1024*1024)return read(image,8*1024*1024);
            DocumentFile remote=externalBook(c,key,"cover.jpg");
            if(remote!=null&&remote.isFile()&&remote.length()>100&&remote.length()<8*1024*1024){
                try(java.io.InputStream in=c.getContentResolver().openInputStream(remote.getUri());java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
                    if(in==null)return null;
                    byte[] buffer=new byte[16384];int n;
                    while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>8*1024*1024)return null;}
                    return out.toByteArray();
                }
            }
        }catch(Exception ignored){}
        return null;
    }
    static File chapter(Context c,Job job){return new File(folder(c,job.sourceKey()),"chapter_"+job.chapterId+".html");}
    static String safeOutputDir(String key){return "Ranobes_"+key.replace(':','_');}
    private static DocumentFile externalBook(Context c,String key,String name){
        String uri=c.getSharedPreferences("reader",0).getString(DownloadFolderPicker.RANOBE,"");if(uri.isEmpty())return null;
        try{DocumentFile root=DocumentFile.fromTreeUri(c,Uri.parse(uri));if(root==null)return null;
            DocumentFile dir=root.findFile(safeOutputDir(key));return dir==null?null:dir.findFile(name);
        }catch(Exception ignored){return null;}
    }
    static boolean hasBook(Context c,String key){if(!validKey(key))return false;
        DocumentFile ext=externalBook(c,key,"book.html");return (ext!=null&&ext.isFile()&&ext.length()>0)||book(c,key).isFile();}
    static String bookUri(Context c,String key){DocumentFile ext=externalBook(c,key,"book.html");return ext!=null&&ext.isFile()?ext.getUri().toString():Uri.fromFile(book(c,key)).toString();}
    static String coverUri(Context c,String key){File file=cover(c,key);if(file.isFile())return Uri.fromFile(file).toString();
        DocumentFile ext=externalBook(c,key,"cover.jpg");if(ext!=null&&ext.isFile())return ext.getUri().toString();
        String id=validKey(key)?key.split(":")[0]:"";return coverSource(c,id);
    }
    static void atomic(File target,byte[] value)throws IOException {File parent=target.getParentFile();if(parent==null||!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Папка недоступна");
        File tmp=new File(target.getAbsolutePath()+".part");try(FileOutputStream out=new FileOutputStream(tmp)){out.write(value);out.getFD().sync();}
        if(target.exists()&&!target.delete())throw new IOException("Нельзя заменить "+target.getName());
        if(!tmp.renameTo(target))throw new IOException("Запись файла не завершена");}
    static void saveChapter(Context c,Job j,String markup)throws IOException {if(Jsoup.parseBodyFragment(markup).text().trim().length()<90)throw new IOException("Неполный текст главы");
        atomic(chapter(c,j),markup.getBytes(StandardCharsets.UTF_8));j.file=chapter(c,j).getAbsolutePath();j.bytes=chapter(c,j).length();put(c,j);}
    static void cover(Context c,RanobesApi.Title title,RanobesApi api,String lang){String key=title.id+":"+lang;File file=cover(c,key);
        if(file.isFile()&&file.length()>100||title.cover.isEmpty())return;
        try{byte[] data=api.imageBytes(title.cover);android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeByteArray(data,0,data.length);
            if(bitmap==null)throw new IOException("Неизвестный формат обложки");
            File output=cover(c,key);File tmp=new File(output.getAbsolutePath()+".part");
            try(FileOutputStream out=new FileOutputStream(tmp)){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,88,out))throw new IOException("Не удалось конвертировать обложку");out.getFD().sync();}finally{bitmap.recycle();}
            if(output.exists())output.delete();if(!tmp.renameTo(output))throw new IOException("Не удалось сохранить обложку");
        }catch(Exception e){android.util.Log.w("RanobesStorage","Cover unavailable",e);}
    }
    static void rebuild(Context c,String key)throws IOException {ArrayList<Job> available=new ArrayList<>();for(Job j:jobs(c,key))if("complete".equals(j.status)&&chapter(c,j).isFile())available.add(j);
        if(available.isEmpty())return;
        Job first=available.get(0);boolean translated="ru".equals(first.language);
        String display=first.bookTitle+(translated?" — русский машинный перевод":" — оригинал");
        StringBuilder out=new StringBuilder("<!DOCTYPE html><html lang=\"").append(translated?"ru":"en").append("\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>")
            .append(escape(display)).append("</title><meta name=\"author\" content=\"").append(escape(first.author)).append("\"><style>body{font:1.1em/1.75 Georgia,serif;max-width:840px;margin:22px auto;padding:22px}img{max-width:100%;height:auto}section{margin-top:36px}@media(prefers-color-scheme:dark){body{background:#202020;color:#eee}}</style></head><body>");
        out.append("<h1>").append(escape(display)).append("</h1>");if(!first.author.isEmpty())out.append("<p>").append(escape(first.author)).append("</p>");
        if(cover(c,key).isFile())out.append("<img alt=\"Обложка\" src=\"cover.jpg\">");
        String lastVolume="";
        for(Job j:available){if(!j.volume.equals(lastVolume)){lastVolume=j.volume;out.append("<h2>Том ").append(escape(lastVolume)).append("</h2>");}
            byte[] buffer=read(chapter(c,j),4_000_000L);String html=new String(buffer,StandardCharsets.UTF_8);
            if(Jsoup.parseBodyFragment(html).text().length()<90)throw new IOException("Глава повреждена: "+j.title);
            out.append("<section data-chapter=\"").append(j.chapterId).append("\"><h3>").append(escape(j.title)).append("</h3>").append(html).append("</section>\n");
        }
        out.append("</body></html>");
        File book=book(c,key);atomic(book,out.toString().getBytes(StandardCharsets.UTF_8));
        try{
            JSONObject meta=new JSONObject();meta.put("id",first.bookId);meta.put("language",first.language);
            meta.put("title",display);meta.put("author",first.author);meta.put("source",first.bookUrl);
            meta.put("cover",first.cover);meta.put("description",description(c,first.bookId));
            meta.put("downloadedChapters",available.size());
            atomic(new File(folder(c,key),"book.json"),meta.toString(2).getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){android.util.Log.w("RanobesStorage","Could not write metadata");}
        String name=safeOutputDir(key);
        // All Ranobes and RanobeLIB downloads use the same category-level SAF path.
        // The local completed book is authoritative. A missing / write-protected
        // SD card must not prevent adding the local HTML to the library.
        try{
            DownloadFolderPicker.export(c,DownloadFolderPicker.RANOBE,book,name);
            if(cover(c,key).isFile())DownloadFolderPicker.export(c,DownloadFolderPicker.RANOBE,cover(c,key),name);
            File meta=new File(folder(c,key),"book.json");
            if(meta.isFile())DownloadFolderPicker.export(c,DownloadFolderPicker.RANOBE,meta,name);
        }catch(IOException exportError){android.util.Log.w("RanobesStorage","SAF export postponed",exportError);}
        register(c,key,display,first.author);
        ReaderBookCache.invalidate(c,Uri.parse(bookUri(c,key)));
    }
    private static byte[] read(File file,long max)throws IOException {if(!file.isFile()||file.length()>max)throw new IOException("Файл недоступен: "+file.getName());
        try(FileInputStream in=new FileInputStream(file);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>max)throw new IOException("HTML слишком велик");}return out.toByteArray();}}
    private static String escape(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private static void register(Context c,String key,String title,String author){String uri=bookUri(c,key);
        try{android.content.SharedPreferences prefs=c.getSharedPreferences("reader",0);JSONArray old=new JSONArray(prefs.getString("library","[]"));JSONArray next=new JSONArray();boolean found=false;
            for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item==null)continue;
                if(uri.equals(item.optString("uri"))){found=true;item.put("title",BookTitleStore.title(c,BookTitleStore.TEXT,uri,title));item.put("format","HTML");if(!author.isEmpty())item.put("author",author);}
                next.put(item);}
            if(!found){MainActivity.BookMeta book=new MainActivity.BookMeta();book.uri=uri;book.title=BookTitleStore.title(c,BookTitleStore.TEXT,uri,title);book.author=author;book.format="HTML";book.excerpt=description(c,key.split(":")[0]);next.put(book.toJson());}
            prefs.edit().putString("library",next.toString()).commit();
        }catch(Exception error){android.util.Log.e("RanobesStorage","Library registration failed",error);}
    }
}
