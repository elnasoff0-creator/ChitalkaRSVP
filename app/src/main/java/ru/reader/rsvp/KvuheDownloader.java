package ru.reader.rsvp;
import android.content.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Range-aware, single-stream downloader. Does not access hidden or protected URLs. */
public final class KvuheDownloader {
    public interface Callback { boolean cancelled();void progress(long done,long total); }
    public static void download(Context context,BookImportManager.Job job,int index,Callback cb)throws Exception{
        String url=job.chapters.get(index).url;if(!KvuheParser.isSafeMedia(url)||!KvuheParser.isMp3(url))throw new IOException("Нет подтверждённой прямой MP3-ссылки для главы");
        File dest=BookImportManager.trackFile(context,job,index),dir=dest.getParentFile();if(!dir.exists()&&!dir.mkdirs())throw new IOException("Невозможно создать папку");File part=new File(dest.getAbsolutePath()+".part");
        if(dest.exists()&&dest.length()>1024 && isPlayable(dest)){cb.progress(dest.length(),dest.length());return;}
        int retries=0;while(retries<4){if(cb.cancelled())throw new InterruptedIOException("Загрузка остановлена");HttpURLConnection conn=null;try{
            long offset=part.exists()?part.length():0;conn=(HttpURLConnection)new URL(url).openConnection();conn.setConnectTimeout(12000);conn.setReadTimeout(18000);conn.setInstanceFollowRedirects(false);conn.setRequestProperty("User-Agent","ChitalkaRSVP/1.11");conn.setRequestProperty("Accept","audio/mpeg,application/octet-stream;q=0.5");if(offset>0)conn.setRequestProperty("Range","bytes="+offset+"-");int status=conn.getResponseCode();if(status==403||status==401)throw new IOException("Нет доступа к аудиофайлу (HTTP "+status+")");if(status==429)throw new IOException("Сервер ограничил количество запросов (429)");if(status!=200&&status!=206)throw new IOException("HTTP "+status);
            if(status==200&&offset>0){offset=0;try(FileOutputStream clear=new FileOutputStream(part,false)){} }
            if(status==206){String cr=conn.getHeaderField("Content-Range");if(cr==null||!cr.startsWith("bytes "+offset+"-"))throw new IOException("Некорректный Content-Range");}
            String contentType=conn.getContentType();if(contentType!=null&&(contentType.startsWith("text/")||contentType.contains("html")||contentType.contains("json")))throw new IOException("Сервер вернул страницу вместо MP3");
            long length=conn.getContentLengthLong();long total=length>=0?offset+length:-1;
            try(InputStream in=new BufferedInputStream(conn.getInputStream());FileOutputStream out=new FileOutputStream(part,offset>0)){byte[] buf=new byte[64*1024];int n;long done=offset;while((n=in.read(buf))!=-1){if(cb.cancelled())throw new InterruptedIOException("Приостановлено");out.write(buf,0,n);done+=n;cb.progress(done,total);}out.getFD().sync();}
            if(total>=0 && part.length()!=total)throw new IOException("Неожиданный размер файла");if(part.length()<1024||!isPlayable(part))throw new IOException("Загруженный файл не является корректным MP3");
            if(dest.exists()&&!dest.delete())throw new IOException("Не удалось заменить MP3");if(!part.renameTo(dest))throw new IOException("Не удалось завершить загрузку");return;
        }catch(InterruptedIOException e){throw e;}catch(IOException e){retries++;if(retries>=4||e.getMessage()!=null&&(e.getMessage().contains("Нет доступа")||e.getMessage().contains("вместо MP3")||e.getMessage().contains("429")))throw e;Thread.sleep(800L*retries*retries);}finally{if(conn!=null)conn.disconnect();}}
    }
    static boolean isPlayable(File file){android.media.MediaMetadataRetriever m=new android.media.MediaMetadataRetriever();try{m.setDataSource(file.getAbsolutePath());String duration=m.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);return duration!=null&&Long.parseLong(duration)>0;}catch(Exception e){return false;}finally{try{m.release();}catch(Exception ignored){}}}
}
