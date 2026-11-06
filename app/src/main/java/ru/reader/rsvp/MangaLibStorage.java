package ru.reader.rsvp;

import android.content.Context;
import android.os.Environment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Persistent MangaLib CBZ collection and comic chapter index. */
final class MangaLibStorage {
    static final class Job {
        String slug="",series="",chapter="",volume="",number="",status="queued",error="",file="";
        long branch=0,bytes=0; int done=0,total=0;
        JSONObject json(){JSONObject o=new JSONObject();try{
            o.put("slug",slug);o.put("series",series);o.put("chapter",chapter);o.put("volume",volume);o.put("number",number);o.put("status",status);o.put("error",error);o.put("file",file);o.put("branch",branch);o.put("done",done);o.put("total",total);o.put("bytes",bytes);
        }catch(Exception ignored){}return o;}
        static Job read(JSONObject o){Job j=new Job();j.slug=o.optString("slug","");j.series=o.optString("series","");j.chapter=o.optString("chapter","");j.volume=o.optString("volume","");j.number=o.optString("number","");j.status=o.optString("status","queued");j.error=o.optString("error","");j.file=o.optString("file","");j.branch=o.optLong("branch",0);j.done=o.optInt("done",0);j.total=o.optInt("total",0);j.bytes=o.optLong("bytes",0);return j;}
        String id(){return slug+"|"+volume+"|"+number+"|"+branch;}
    }
    private static final String STORE="mangalib_jobs_v1";
    static synchronized ArrayList<Job> jobs(Context ctx){ArrayList<Job> out=new ArrayList<>();try{
        JSONArray a=new JSONArray(ctx.getSharedPreferences("reader",0).getString(STORE,"[]"));for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o!=null)out.add(Job.read(o));
        }
    }catch(Exception ignored){}return out;}
    static synchronized void put(Context ctx,Job job){ArrayList<Job> all=jobs(ctx);boolean found=false;
        for(int i=0;i<all.size();i++)if(all.get(i).id().equals(job.id())){all.set(i,job);found=true;break;}
        if(!found)all.add(job);JSONArray a=new JSONArray();for(Job j:all)a.put(j.json());
        ctx.getSharedPreferences("reader",0).edit().putString(STORE,a.toString()).commit();
    }
    static synchronized Job find(Context ctx,String id){for(Job j:jobs(ctx))if(j.id().equals(id))return j;return null;}
    static ArrayList<Job> seriesJobs(Context ctx,String slug){ArrayList<Job> out=new ArrayList<>();for(Job j:jobs(ctx))if(j.slug.equals(slug)&&"complete".equals(j.status)&&new File(j.file).isFile())out.add(j);
        out.sort((a,b)->{int d=Double.compare(num(a.volume),num(b.volume));return d!=0?d:Double.compare(num(a.number),num(b.number));});return out;
    }
    static String comicUri(String slug){return "mangalib:"+MangaLibApi.validSlug(slug);}
    static void registerComic(Context ctx,Job job){
        ComicLibrary.Comic comic=ComicLibrary.find(ctx,comicUri(job.slug));
        if(comic==null){comic=new ComicLibrary.Comic();comic.uri=comicUri(job.slug);comic.kind="mangalib";comic.title=job.series;comic.chapter=0;comic.mode=1;}
        ComicLibrary.save(ctx,comic);
    }
    static File root(Context c){File base=c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);if(base==null)base=c.getFilesDir();File f=new File(base,"Comics/MangaLib");if(!f.exists())f.mkdirs();return f;}
    static File seriesFolder(Context c,Job j){File d=new File(root(c),j.slug);if(!d.exists())d.mkdirs();return d;}
    static String safe(String s){String r=s.replaceAll("[^\\p{L}\\p{N}._ -]","_").trim();if(r.isEmpty())return "chapter";return r.length()>80?r.substring(0,80):r;}
    static File chapterFile(Context c,Job j){
        File vol=new File(seriesFolder(c,j),"Том "+safe(j.volume));if(!vol.exists())vol.mkdirs();
        return new File(vol,"Глава "+safe(j.number)+"_"+Long.toString(j.branch)+".cbz");
    }
    /** Verified per-page download cache, kept after Pause/Retry, deleted only on completion. */
    static File stageDir(Context c,Job j){
        File dir=new File(seriesFolder(c,j),".pages_"+safe(j.volume)+"_"+safe(j.number)+"_"+j.branch);
        if(!dir.exists())dir.mkdirs();return dir;
    }
    static void clearStage(File dir){
        if(dir==null||!dir.isDirectory())return;
        File[] files=dir.listFiles();if(files!=null)for(File file:files)if(file.isFile())file.delete();
        dir.delete();
    }
    static ArrayList<ComicLibrary.Chapter> comicChapters(Context c,String slug){ArrayList<ComicLibrary.Chapter> out=new ArrayList<>();
        for(Job j:seriesJobs(c,slug)){
            ComicLibrary.Chapter ch=new ComicLibrary.Chapter();ch.title=j.chapter;ch.type="mangalib_cbz";
            ch.imagePaths.add(j.file);out.add(ch);
        }
        return out;
    }
    /** Reading cache only; does not modify the durable CBZ archive. */
    static ArrayList<String> extractForReading(Context c,File cbz) throws IOException {
        ArrayList<String> pages=new ArrayList<>();if(!cbz.isFile()||cbz.length()>600_000_000L)return pages;
        String key=Long.toHexString(cbz.lastModified())+"_"+Long.toHexString(cbz.length())+"_"+Integer.toHexString(cbz.getAbsolutePath().hashCode());
        File cache=new File(c.getCacheDir(),"mangalib_pages/"+key);if(!cache.exists())cache.mkdirs();
        try(ZipFile zip=new ZipFile(cbz)){
            java.util.Enumeration<? extends ZipEntry> entries=zip.entries();int count=0;
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();String name=e.getName();
                if(e.isDirectory()||name.contains("/")||name.contains("\\")||!ComicLibrary.isImageName(name))continue;
                if(++count>500)throw new IOException("Превышено количество страниц");
                File out=new File(cache,name);
                if(!out.isFile()||out.length()!=e.getSize()){
                    if(e.getSize()>25_000_000L)throw new IOException("Слишком большая страница");
                    try(InputStream in=zip.getInputStream(e);OutputStream stream=new FileOutputStream(out)){
                        byte[] buffer=new byte[8192];int n;long size=0;
                        while((n=in.read(buffer))!=-1){size+=n;if(size>25_000_000L)throw new IOException("Слишком большая страница");stream.write(buffer,0,n);}
                    }
                }
                pages.add(out.getAbsolutePath());
            }
        }
        pages.sort((a,b)->ComicLibrary.natural(new File(a).getName(),new File(b).getName()));
        return pages;
    }
    private static double num(String s){try{return Double.parseDouble(s.replace(',','.'));}catch(Exception ex){return 0;}}
}
