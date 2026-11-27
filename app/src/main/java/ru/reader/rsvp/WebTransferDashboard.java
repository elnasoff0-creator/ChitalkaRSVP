package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Read-only projection of registered local library items. No raw file paths are exposed. */
final class WebTransferDashboard {
    private static final int COVER_LIMIT=16*1024*1024, BOOK_LIMIT=24*1024*1024;
    private WebTransferDashboard(){}
    private static final class Item {
        String kind,id,title,detail,cover="",format="",audioFile="";int progress;boolean started;
        ComicLibrary.Comic comic;
        Item(String kind,String id,String title,String detail,int progress,boolean started){
            this.kind=kind;this.id=id;this.title=title;this.detail=detail;this.progress=progress<0?-1:Math.min(100,progress);this.started=started||progress>0;
        }
        String key() throws Exception {byte[] hash=MessageDigest.getInstance("SHA-256").digest((kind+"\n"+id).getBytes(StandardCharsets.UTF_8));return android.util.Base64.encodeToString(hash,android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);}
        JSONObject json() throws Exception {return new JSONObject().put("key",key()).put("kind",kind).put("title",title).put("detail",detail).put("progress",progress).put("started",started);}
    }
    private static ArrayList<Item> items(Context c) throws Exception {
        SharedPreferences p=c.getSharedPreferences("reader",0);ArrayList<Item> out=new ArrayList<>();
        Set<String> opened=ids(ChteicoStore.prefs(c),"opened_books");
        JSONArray a=new JSONArray(p.getString("library","[]"));
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;String id=o.optString("uri","");if(id.isEmpty())continue;
            String format=o.optString("format","");Item item=new Item("book",id,BookTitleStore.title(c,BookTitleStore.TEXT,id,o.optString("title","Книга")),format,o.optInt("progress",0),opened.contains(id)||o.optInt("index",0)>0||o.optInt("scroll",0)>0);item.format=format;out.add(item);
        }
        for(AudioLibrary.Book b:AudioLibrary.load(c)){
            if(b.id.isEmpty())continue;int count=Math.max(1,b.uris.size());int segment=Math.max(0,Math.min(count-1,p.getInt("audio_segment:"+b.id,0)));
            int progress=p.getInt("audio_progress:"+b.id,-1);if(progress<0)progress=segment*100/count;
            Item item=new Item("audio",b.id,b.name,b.uris.size()+" файлов",progress,p.contains("audio_pos:"+b.id)||p.contains("audio_segment:"+b.id));
            if(!b.uris.isEmpty())item.audioFile=b.uris.get(0);
            item.cover=p.getString("audio_cover:"+b.id,"");if(item.cover.isEmpty())item.cover=b.coverUri;if(item.cover.isEmpty())item.cover=AudiobookCoverStore.localUri(c,b.id);out.add(item);
        }
        for(ComicLibrary.Comic b:ComicLibrary.list(c)){
            if(b.uri.isEmpty())continue;Item item=new Item("comic",b.uri,b.title,b.chapterTotal>0?b.compactProgress():"",b.progressPercent(),b.chapter>0||b.page>0||b.scroll>0);item.comic=b;out.add(item);
        }
        return out;
    }
    private static Set<String> ids(SharedPreferences p,String key){Set<String> result=new HashSet<>();try{JSONArray a=new JSONArray(p.getString(key,"[]"));for(int i=0;i<a.length();i++)result.add(a.optString(i));}catch(Exception ignored){}return result;}
    static byte[] snapshot(Context c) throws Exception {
        SharedPreferences p=ChteicoStore.prefs(c);JSONArray library=new JSONArray(),history=new JSONArray(),awards=new JSONArray(),days=new JSONArray();
        int books=0,audio=0,comics=0;
        for(Item item:items(c)){library.put(item.json());if(item.kind.equals("book"))books++;else if(item.kind.equals("audio"))audio++;else comics++;}
        for(WebTransferDownloadStore.Entry e:WebTransferDownloadStore.all(c))history.put(new JSONObject().put("name",e.name).put("state",e.state).put("received",e.received).put("total",e.total).put("fileCount",e.fileCount).put("updated",e.updated));
        int finished=p.getInt("finished_books_count",0),listened=p.getInt("finished_audio_count",0),streak=p.getInt("best_streak",0);
        if(finished>0)awards.put("Первая книга");if(listened>0)awards.put("Первая аудиокнига");if(streak>=7)awards.put("Серия 7 дней");if(finished>=10)awards.put("10 прочитанных книг");if(listened>=10)awards.put("10 аудиокниг");if(streak>=30)awards.put("30 дней подряд");
        long since=p.getLong("daily_stats_since",0);SimpleDateFormat key=new SimpleDateFormat("yyyyMMdd",Locale.ROOT),label=new SimpleDateFormat("dd.MM",Locale.ROOT);
        Calendar day=Calendar.getInstance();day.add(Calendar.DAY_OF_YEAR,-6);String first=since>0?key.format(new Date(since)):"99999999";
        for(int i=0;i<7;i++,day.add(Calendar.DAY_OF_YEAR,1)){String date=key.format(day.getTime());boolean known=date.compareTo(first)>=0;days.put(new JSONObject().put("label",label.format(day.getTime())).put("known",known).put("read",p.getLong("daily_read_"+date,0)).put("audio",p.getLong("daily_audio_"+date,0)));}
        JSONObject stats=new JSONObject().put("books",books).put("audio",audio).put("comics",comics).put("finishedBooks",finished).put("finishedAudio",listened).put("comicPages",ChteicoStore.comicPages(c)).put("readingSeconds",p.getLong("seconds_classic",0)+p.getLong("seconds_speed",0)).put("audioSeconds",p.getLong("seconds_audio",0)).put("words",p.getLong("words_advanced",0)).put("sessions",p.getInt("sessions",0)).put("streak",p.getInt("streak",0)).put("bestStreak",streak).put("dailySince",since);
        return new JSONObject().put("library",library).put("stats",stats).put("history",history).put("awards",awards).put("days",days).toString().getBytes(StandardCharsets.UTF_8);
    }
    // Never resolve an arbitrary client-supplied filename/URI. Keys must match a current library record.
    static synchronized byte[] cover(Context c,String key) throws Exception {
        if(key==null||!key.matches("[A-Za-z0-9_-]{43}"))return null;
        for(Item item:items(c))if(key.equals(item.key())){
            Bitmap bitmap=null;
            if(item.kind.equals("audio")){bitmap=decode(readUri(c,item.cover,COVER_LIMIT));if(bitmap==null)bitmap=decode(embedded(c,item.audioFile));}
            else if(item.kind.equals("comic"))bitmap=ComicCovers.cover(c,item.comic);
            else {
                byte[] bytes=HtmlFolderLibrary.coverBytes(c,item.id);
                if(bytes==null)bytes=RanobesStorage.localCover(c,item.id);
                if(bytes==null)bytes=readFile(CoverLoader.target(c,item.id),COVER_LIMIT);
                // No network cover repair here. Only bounded local EPUB/FB2 extraction.
                if(bytes==null){byte[] book=readUri(c,item.id,BOOK_LIMIT);if(book!=null){
                    if(book.length>3&&book[0]=='P'&&book[1]=='K'){if(safeZip(book))bytes=CoverLoader.epub(book);}
                    else if(item.format.toLowerCase(Locale.ROOT).contains("fb2")||item.id.toLowerCase(Locale.ROOT).endsWith(".fb2"))bytes=CoverLoader.fb2(book);
                }}
                bitmap=decode(bytes);
            }
            if(bitmap==null)return null;
            int w=bitmap.getWidth(),h=bitmap.getHeight();float scale=Math.min(1f,Math.min(240f/w,320f/h));
            Bitmap thumb=scale<1?Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true):bitmap;
            ByteArrayOutputStream data=new ByteArrayOutputStream();thumb.compress(Bitmap.CompressFormat.JPEG,80,data);return data.toByteArray();
        }return null;
    }
    private static byte[] embedded(Context c,String raw){if(raw==null||raw.isEmpty())return null;Uri uri=Uri.parse(raw);if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme()))return null;
        android.media.MediaMetadataRetriever mm=new android.media.MediaMetadataRetriever();try{mm.setDataSource(c,uri);byte[] bytes=mm.getEmbeddedPicture();return bytes!=null&&bytes.length<=COVER_LIMIT?bytes:null;}catch(Exception ignored){return null;}finally{try{mm.release();}catch(Exception ignored){}}}
    private static byte[] readUri(Context c,String raw,int limit){if(raw==null||raw.isEmpty())return null;try{Uri uri=Uri.parse(raw);String scheme=uri.getScheme();if(!"content".equals(scheme)&&!"file".equals(scheme))return null;try(InputStream in=c.getContentResolver().openInputStream(uri)){return bounded(in,limit);}}catch(Exception e){return null;}}
    private static byte[] readFile(File file,int limit){if(!file.isFile()||file.length()>limit)return null;try(InputStream in=new FileInputStream(file)){return bounded(in,limit);}catch(Exception e){return null;}}
    private static byte[] bounded(InputStream in,int limit)throws IOException{if(in==null)return null;ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){if(out.size()+n>limit)return null;out.write(buf,0,n);}return out.toByteArray();}
    private static boolean safeZip(byte[] bytes){try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes))){long total=0;int entries=0,n;byte[] b=new byte[8192];while(zip.getNextEntry()!=null){if(++entries>4096)return false;while((n=zip.read(b))!=-1){total+=n;if(total>32L*1024*1024)return false;}}return true;}catch(Exception e){return false;}}
    private static Bitmap decode(byte[] bytes){if(bytes==null||bytes.length>COVER_LIMIT)return null;BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);if(opt.outWidth<=0||opt.outHeight<=0)return null;int sample=1;while(opt.outWidth/sample>480||opt.outHeight/sample>640)sample*=2;opt.inSampleSize=sample;opt.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(bytes,0,bytes.length,opt);}
}
