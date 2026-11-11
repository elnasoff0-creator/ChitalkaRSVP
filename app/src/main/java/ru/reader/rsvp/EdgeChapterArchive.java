package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Completed chapter storage for Edge TTS.
 * SAF holds ONE frame-normalized MP3 and ONE WordBoundary JSON per chapter.
 * The JSON also contains a lossless fragment-byte index, so ReaderActivity's
 * existing per-clip player/highlighter can restore clips into a TEMPORARY cache
 * without recontacting Edge TTS or losing word offsets.
 *
 * Never delete a working fragment unless the published files are verified.
 */
final class EdgeChapterArchive {
    private static final String TAG="EdgeChapterArchive";
    private static final String INDEX_NAME="chapter.index.json";
    static final String AUTO_MERGE_PREF="edge_auto_merge_chapter_mp3_v151";
    static boolean autoMerge(Context c){return c.getSharedPreferences("reader",0).getBoolean(AUTO_MERGE_PREF,true);}
    private static ArrayList<SavedBook> bookCache;
    private static long bookCacheAt;
    private static final Map<String,ChapterSnapshot> chapterCache=new HashMap<>();
    private static final Map<String,Long> chapterCacheAt=new HashMap<>();
    static synchronized void invalidate(){bookCache=null;bookCacheAt=0;chapterCache.clear();chapterCacheAt.clear();}
    static final class ChapterSnapshot {
        final Map<Integer,Integer> states=new HashMap<>(); // 0=none,1=fragments,2=merged
        int state(int chapter){Integer v=states.get(chapter);return v==null?0:v;}
    }
    static synchronized ChapterSnapshot snapshot(Context c,String uri,String title){
        String key=(EdgeFolderPicker.selected(c)==null?"":EdgeFolderPicker.selected(c).toString())+":"+uri;
        long now=android.os.SystemClock.elapsedRealtime();Long at=chapterCacheAt.get(key);
        if(at!=null&&now-at<9000L&&chapterCache.containsKey(key))return chapterCache.get(key);
        ChapterSnapshot result=new ChapterSnapshot();
        try{
            DocumentFile dir=chapters(c,uri,title,false);
            if(dir!=null)for(DocumentFile chapter:dir.listFiles()){
                if(!chapter.isDirectory())continue;
                DocumentFile idx=chapter.findFile(INDEX_NAME);
                if(idx==null)continue;
                JSONObject row=new JSONObject(text(c,idx,16*1024*1024));
                if(!hash(uri).equals(row.optString("bookId")))continue;
                int number=row.optInt("chapter",0)-1;
                if(number<0)continue;
                boolean merged=chapter.findFile("Глава.mp3")!=null&&chapter.findFile("Глава.words.json")!=null
                    &&row.optString("audioSha256","").length()==64;
                boolean clips=chapter.findFile("fragments")!=null&&row.optJSONArray("segments")!=null;
                if(merged||clips)result.states.put(number,merged?2:1);
            }
        }catch(Exception error){Log.w(TAG,"SAF chapter snapshot error",error);}
        chapterCache.put(key,result);chapterCacheAt.put(key,now);return result;
    }
    private EdgeChapterArchive(){}

    private static String safe(String source,int max){
        String s=source==null?"Книга":source.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");
        if(s.isEmpty())s="Книга";
        if(s.length()>max)s=s.substring(0,max);
        return s;
    }
    private static String hash(String s){
        try{
            byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder r=new StringBuilder();for(int i=0;i<12;i++)r.append(String.format(Locale.ROOT,"%02x",b[i]&255));
            return r.toString();
        }catch(Exception e){return Integer.toHexString(s.hashCode());}
    }
    private static String digest(InputStream source)throws Exception {
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[32768];int n;
        while((n=source.read(buffer))!=-1)md.update(buffer,0,n);
        StringBuilder result=new StringBuilder();
        for(byte b:md.digest())result.append(String.format(Locale.ROOT,"%02x",b&255));
        return result.toString();
    }
    private static String digest(File file)throws Exception {
        try(InputStream in=new FileInputStream(file)){return digest(in);}
    }
    private static DocumentFile root(Context c)throws Exception {
        if(!EdgeFolderPicker.available(c))throw new Exception("Папка Edge TTS не выбрана или недоступна");
        DocumentFile root=DocumentFile.fromTreeUri(c,EdgeFolderPicker.selected(c));
        if(root==null||!root.canWrite())throw new Exception("Нет разрешения записи в папку Edge TTS");
        return root;
    }
    private static String text(Context c,DocumentFile doc,int limit)throws Exception {
        if(doc==null||!doc.isFile()||doc.length()>limit)throw new Exception("Отсутствует индекс главы");
        ByteArrayOutputStream buf=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        try(InputStream in=c.getContentResolver().openInputStream(doc.getUri())){
            if(in==null)throw new Exception("Нет доступа к индексу");
            while((n=in.read(b))!=-1){if(buf.size()+n>limit)throw new Exception("Индекс превышает лимит");buf.write(b,0,n);}
        }
        return buf.toString("UTF-8");
    }
    private static void write(Context c,DocumentFile dir,String name,String mime,byte[] data)throws Exception {
        DocumentFile existing=dir.findFile(name);
        // Never partially overwrite a completed chapter. A staged file must be
        // fully flushed and renamed before the source clips become disposable.
        String stage=name+".pending";
        DocumentFile older=dir.findFile(stage);if(older!=null)older.delete();
        DocumentFile pending=dir.createFile(mime,stage);
        if(pending==null)throw new Exception("Нельзя создать "+name);
        try(OutputStream out=c.getContentResolver().openOutputStream(pending.getUri(),"wt")){
            if(out==null)throw new Exception("Нет потока записи");out.write(data);out.flush();
        }catch(Exception ex){pending.delete();throw ex;}
        if(pending.length()>0&&pending.length()!=data.length){pending.delete();throw new Exception("Размер файла не совпадает");}
        if(existing!=null&&!existing.delete()){pending.delete();throw new Exception("Нельзя заменить старый файл");}
        if(!pending.renameTo(name)){pending.delete();throw new Exception("Нельзя завершить запись "+name);}
    }
    /** Stream a long chapter to SAF without buffering its entire MP3 in RAM. */
    private static void write(Context c,DocumentFile dir,String name,String mime,File source)throws Exception {
        DocumentFile existing=dir.findFile(name);
        String stage=name+".pending";
        DocumentFile old=dir.findFile(stage);if(old!=null)old.delete();
        DocumentFile pending=dir.createFile(mime,stage);
        if(pending==null)throw new Exception("Не удалось создать аудиофайл");
        try(InputStream in=new FileInputStream(source);
            OutputStream out=c.getContentResolver().openOutputStream(pending.getUri(),"wt")){
            if(out==null)throw new Exception("Недоступен файл назначения");
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            out.flush();
        }catch(Exception e){pending.delete();throw e;}
        if(pending.length()>0&&pending.length()!=source.length()){
            pending.delete();throw new Exception("Не совпадает размер главы после копирования");
        }
        if(existing!=null&&!existing.delete()){
            pending.delete();throw new Exception("Нельзя заменить готовую главу");
        }
        if(!pending.renameTo(name)){
            pending.delete();throw new Exception("Нельзя завершить запись главы");
        }
    }
    private static DocumentFile book(Context c,String uri,String title,boolean create)throws Exception {
        DocumentFile r=root(c);
        String basename=safe(title,75),identity=hash(uri);
        // Reuse a book folder even if the user renamed the book in the library.
        for(DocumentFile candidate:r.listFiles()){
            if(!candidate.isDirectory())continue;
            DocumentFile marker=candidate.findFile(".edge-book.json");
            if(marker!=null)try{
                if(identity.equals(new JSONObject(text(c,marker,4096)).optString("id")))return candidate;
            }catch(Exception ignored){}
        }
        for(int n=1;n<=100;n++){
            String name=n==1?basename:basename+" ("+n+")";
            DocumentFile folder=r.findFile(name);
            if(folder==null){if(!create)continue;folder=r.createDirectory(name);
                if(folder==null)throw new Exception("Нельзя создать папку книги");}
            DocumentFile marker=folder.findFile(".edge-book.json");
            if(marker!=null){
                try{if(identity.equals(new JSONObject(text(c,marker,4096)).optString("id")))return folder;}
                catch(Exception ignored){} // A foreign folder: don't overwrite it.
                continue;
            }
            if(!create)continue;
            JSONObject info=new JSONObject();info.put("id",identity);info.put("title",title);info.put("uri",uri);
            write(c,folder,".edge-book.json","application/json",info.toString().getBytes(StandardCharsets.UTF_8));
            return folder;
        }
        throw new Exception("Не удалось найти папку книги");
    }
    private static DocumentFile chapters(Context c,String uri,String title,boolean create)throws Exception {
        DocumentFile folder=book(c,uri,title,create);if(folder==null)return null;
        DocumentFile result=folder.findFile("Главы");
        return result==null&&create?folder.createDirectory("Главы"):result;
    }
    private static int chapterFor(BookChapterIndex chapters,ArrayList<Integer> starts,int word){
        int charAt=word>=0&&word<starts.size()?starts.get(word):0;
        int id=0;
        if(chapters!=null)for(int i=0;i<chapters.size();i++){
            if(chapters.get(i).startChar<=charAt)id=i;else break;
        }
        return id;
    }
    /** MP3 audio frames; omit ID3 metadata so no extra headers break joined stream. */
    private static final class Frames {
        final byte[] frames;final long durationMs;
        Frames(byte[] bytes,long time){frames=bytes;durationMs=time;}
    }
    private static Frames audioFrames(File file)throws Exception {
        if(!file.isFile()||file.length()<1500||file.length()>24*1024*1024)
            throw new Exception("Неверный размер фрагмента "+file.getName());
        byte[] all=new byte[(int)file.length()];int read=0;
        try(InputStream in=new FileInputStream(file)){
            while(read<all.length){int n=in.read(all,read,all.length-read);if(n<0)break;read+=n;}
        }
        if(read!=all.length)throw new Exception("Повреждённый MP3");
        int start=0;
        if(read>=10&&all[0]=='I'&&all[1]=='D'&&all[2]=='3'){
            start=10+((all[6]&127)<<21)+((all[7]&127)<<14)+((all[8]&127)<<7)+(all[9]&127);
        }
        int end=read;
        if(end-start>128&&all[end-128]=='T'&&all[end-127]=='A'&&all[end-126]=='G')end-=128;
        ByteArrayOutputStream audio=new ByteArrayOutputStream();long micros=0;int p=start,errors=0;
        while(p+4<=end){
            int h=((all[p]&255)<<24)|((all[p+1]&255)<<16)|((all[p+2]&255)<<8)|(all[p+3]&255);
            int vers=(h>>>19)&3,layer=(h>>>17)&3,bit=(h>>>12)&15,sample=(h>>>10)&3;
            if((h&0xffe00000)!=0xffe00000||vers==1||layer!=1||bit==0||bit==15||sample==3){
                p++;errors++;if(errors>256)throw new Exception("Неверные MP3-кадры");continue;
            }
            int[] rates=vers==3?new int[]{0,32,40,48,56,64,80,96,112,128,160,192,224,256,320}:
                new int[]{0,8,16,24,32,40,48,56,64,80,96,112,128,144,160};
            int[] samples={44100,48000,32000};
            int hz=samples[sample];if(vers==2)hz/=2;else if(vers==0)hz/=4;
            int samplesInFrame=vers==3?1152:576;
            int bytes=((vers==3?144:72)*rates[bit]*1000)/hz+((h>>>9)&1);
            if(bytes<24||p+bytes>end)break;
            audio.write(all,p,bytes);p+=bytes;micros+=(long)samplesInFrame*1000000L/hz;
        }
        if(audio.size()<1000||micros<200000)throw new Exception("Невозможно собрать звуковые кадры MP3");
        return new Frames(audio.toByteArray(),(micros+500)/1000);
    }
    /** Called from background IO, for BOTH full-book and completed normal-reading chapters. */
    static synchronized int publish(Context ctx,String uri,String title,File localMp3,
        JSONArray queue,BookChapterIndex chapters,ArrayList<Integer> wordStarts){
        if(!EdgeFolderPicker.available(ctx)||queue==null||queue.length()==0)return 0;
        try{
            Map<Integer,ArrayList<JSONObject>> groups=new HashMap<>();
            for(int i=0;i<queue.length();i++){
                JSONObject row=queue.optJSONObject(i);if(row==null)continue;
                int chapter=chapterFor(chapters,wordStarts,row.optInt("start",0));
                ArrayList<JSONObject> list=groups.get(chapter);
                if(list==null){list=new ArrayList<>();groups.put(chapter,list);}list.add(row);
            }
            DocumentFile all=chapters(ctx,uri,title,true);
            if(all==null)throw new Exception("Нет папки глав");
            int published=0;
            for(Map.Entry<Integer,ArrayList<JSONObject>> group:groups.entrySet()){
                int chapter=group.getKey();ArrayList<JSONObject> parts=group.getValue();
                if(parts.isEmpty())continue;
                boolean complete=true;StringBuilder signature=new StringBuilder();
                for(JSONObject row:parts){String key=row.optString("key","");
                    File f=new File(localMp3,key+".mp3");
                    if(!key.matches("[a-f0-9]{64}")||!f.isFile()||f.length()<1500){complete=false;break;}
                    signature.append(key).append(':').append(f.length()).append(':').append(digest(f)).append(';');
                }
                if(!complete)continue;
                String titleChapter=chapters!=null&&chapter<chapters.size()?chapters.get(chapter).title:"Глава";
                String folderName=String.format(Locale.ROOT,"%03d - %s",chapter+1,safe(titleChapter,55));
                DocumentFile dir=all.findFile(folderName);
                if(dir==null)dir=all.createDirectory(folderName);
                if(dir==null)continue;
                String fingerprint=hash(signature.toString());
                DocumentFile existing=dir.findFile(INDEX_NAME);
                if(existing!=null){
                    try{JSONObject previous=new JSONObject(text(ctx,existing,16*1024*1024));
                        if(fingerprint.equals(previous.optString("fingerprint")) &&
                          dir.findFile("Глава.mp3")!=null&&dir.findFile("Глава.words.json")!=null){
                            published++;continue;
                        }
                    }catch(Exception ignored){}
                }
                File temporary=File.createTempFile("edge-chapter-",".mp3",ctx.getCacheDir());
                long combinedBytes=0L;
                JSONArray segments=new JSONArray(),words=new JSONArray();long timeMs=0;
                int chapterChar=chapters!=null&&chapter<chapters.size()?chapters.get(chapter).startChar:0;
                try(OutputStream combined=new FileOutputStream(temporary)){
                    for(JSONObject row:parts){
                        String key=row.getString("key"),clipText=row.optString("text","");
                        Frames fr=audioFrames(new File(localMp3,key+".mp3"));
                        JSONObject meta=new JSONObject();meta.put("key",key);meta.put("start",row.optInt("start"));
                        meta.put("end",row.optInt("end"));meta.put("text",clipText);
                        meta.put("offset",combinedBytes);meta.put("length",fr.frames.length);
                        meta.put("startMs",timeMs);meta.put("durationMs",fr.durationMs);
                        int clipChar=row.optInt("start",0)<wordStarts.size()?wordStarts.get(row.optInt("start",0)):chapterChar;
                        JSONArray originalWords=new JSONArray();
                        for(EdgeWordTimings.Word w:EdgeWordTimings.read(new File(localMp3,key+".mp3"),clipText)){
                            JSONArray original=new JSONArray();original.put(w.startMs);original.put(w.durationMs);
                            original.put(w.from);original.put(w.to);originalWords.put(original);
                            JSONArray normalized=new JSONArray();normalized.put(timeMs+w.startMs);normalized.put(w.durationMs);
                            normalized.put(clipChar-chapterChar+w.from);normalized.put(clipChar-chapterChar+w.to);
                            words.put(normalized);
                        }
                        meta.put("words",originalWords);segments.put(meta);
                        combined.write(fr.frames);combinedBytes+=fr.frames.length;timeMs+=fr.durationMs;
                    }
                    combined.flush();
                }catch(Exception e){temporary.delete();throw e;}
                JSONObject sidecar=new JSONObject();sidecar.put("version",2).put("durationMs",timeMs);
                sidecar.put("chapter",chapter+1);sidecar.put("words",words);
                JSONObject index=new JSONObject();index.put("version",1);index.put("bookId",hash(uri));
                index.put("chapter",chapter+1);index.put("fingerprint",fingerprint);
                index.put("length",combinedBytes);index.put("audioSha256",digest(temporary));
                index.put("segments",segments);
                // The completion marker (index) is written LAST. Failures leave working fragments intact.
                try{
                    write(ctx,dir,"Глава.mp3","audio/mpeg",temporary);
                    write(ctx,dir,"Глава.words.json","application/json",sidecar.toString().getBytes(StandardCharsets.UTF_8));
                    write(ctx,dir,INDEX_NAME,"application/json",index.toString().getBytes(StandardCharsets.UTF_8));
                    published++;
                }finally{temporary.delete();}
            }
            if(published>0)invalidate();
            return published;
        }catch(Exception ex){Log.w(TAG,"Could not publish chapter",ex);return 0;}
    }
    /** Rehydrate fragments from completed SAF chapters to a disposable internal cache. */
    static synchronized int restore(Context ctx,String uri,String title,File target){
        if(!EdgeFolderPicker.available(ctx))return 0;
        try{
            DocumentFile all=chapters(ctx,uri,title,false);
            if(all==null)return 0;
            if(!target.isDirectory()&&!target.mkdirs())return 0;
            int restored=0;
            for(DocumentFile chapter:all.listFiles()){
                if(!chapter.isDirectory())continue;
                DocumentFile idx=chapter.findFile(INDEX_NAME),mp3=chapter.findFile("Глава.mp3");
                if(idx==null)continue;
                JSONObject index=new JSONObject(text(ctx,idx,16*1024*1024));
                if(!hash(uri).equals(index.optString("bookId")))continue;
                if(index.optInt("version")==2&&"fragments".equals(index.optString("mode"))){
                    DocumentFile clips=chapter.findFile("fragments");
                    JSONArray rows=index.optJSONArray("segments");
                    if(clips==null||rows==null)continue;
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                        String key=row.optString("key","");
                        if(!key.matches("[a-f0-9]{64}"))continue;
                        String name=String.format(Locale.ROOT,"%04d",i+1);
                        DocumentFile src=clips.findFile(name+".mp3"),words=clips.findFile(name+".words.json");
                        if(src==null||words==null)continue;
                        File to=new File(target,key+".mp3");
                        if(to.length()!=src.length()||!row.optString("sha256").equals(digest(to))){
                            File temp=new File(target,key+".fragment.part");
                            try(InputStream in=ctx.getContentResolver().openInputStream(src.getUri());
                                OutputStream out=new FileOutputStream(temp)){
                                if(in==null)throw new Exception("Недоступен исходный фрагмент");
                                byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                            }
                            if(!row.optString("sha256").equals(digest(temp))){temp.delete();continue;}
                            if(to.exists())to.delete();if(!temp.renameTo(to))throw new Exception("Не удалось восстановить MP3");
                        }
                        File sidecar=new File(target,key+".mp3.words.json");
                        try(InputStream in=ctx.getContentResolver().openInputStream(words.getUri());
                            OutputStream out=new FileOutputStream(sidecar)){
                            if(in==null)throw new Exception("Нет WordBoundary");
                            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                        }
                        restored++;
                    }
                    continue;
                }
                if(index.optInt("version")!=1||mp3==null)continue;
                JSONArray parts=index.optJSONArray("segments");if(parts==null)continue;
                // A corrupted or truncated external MP3 must NEVER be trusted
                // as a source for offline playback or its WordBoundary mapping.
                String expected=index.optString("audioSha256","");
                if(expected.length()!=64)continue;
                try(InputStream verify=ctx.getContentResolver().openInputStream(mp3.getUri())){
                    if(verify==null||!expected.equals(digest(verify)))continue;
                }
                try(InputStream stream=ctx.getContentResolver().openInputStream(mp3.getUri())){
                    if(stream==null)continue;
                    long pos=0;
                    for(int i=0;i<parts.length();i++){
                        JSONObject row=parts.optJSONObject(i);if(row==null)break;
                        String key=row.optString("key","");int size=row.optInt("length",0);
                        long offset=row.optLong("offset",-1);
                        if(!key.matches("[0-9a-f]{64}")||size<1500||size>24*1024*1024||offset<pos)break;
                        byte[] buffer=new byte[16384];while(pos<offset){
                            int n=stream.read(buffer,0,(int)Math.min(buffer.length,offset-pos));
                            if(n<0)throw new Exception("Truncated MP3");pos+=n;
                        }
                        File file=new File(target,key+".mp3");boolean present=file.isFile()&&file.length()==size;
                        File temp=new File(target,key+".restore.part");
                        try(OutputStream out=present?new ByteArrayOutputStream():new FileOutputStream(temp)){
                            int remaining=size;
                            while(remaining>0){int n=stream.read(buffer,0,Math.min(buffer.length,remaining));
                                if(n<0)throw new Exception("Incomplete MP3");
                                if(!present)out.write(buffer,0,n);remaining-=n;pos+=n;
                            }
                        }
                        if(!present){if(file.exists())file.delete();if(!temp.renameTo(file))throw new Exception("MP3 restore failed");}
                        JSONArray boundary=row.optJSONArray("words");
                        if(boundary!=null&&boundary.length()>0){
                            ArrayList<EdgeWordTimings.Word> words=new ArrayList<>();
                            for(int k=0;k<boundary.length();k++){
                                JSONArray w=boundary.optJSONArray(k);if(w!=null&&w.length()==4)
                                    words.add(new EdgeWordTimings.Word(w.optLong(0),w.optLong(1),w.optInt(2),w.optInt(3)));
                            }
                            EdgeWordTimings.storeAligned(file,row.optString("text",""),words);
                        }
                        restored++;
                    }
                }
            }
            return restored;
        }catch(Exception ex){Log.w(TAG,"Could not restore SAF audio",ex);return 0;}
    }
    /** Publish all complete chapter fragments without auto-merging. SAF is authoritative. */
    static synchronized int publishFragments(Context ctx,String uri,String title,File localMp3,
        JSONArray queue,BookChapterIndex toc,ArrayList<Integer> wordStarts){
        if(!EdgeFolderPicker.available(ctx)||queue==null||queue.length()==0)return 0;
        try{
            Map<Integer,ArrayList<JSONObject>> groups=new HashMap<>();
            for(int i=0;i<queue.length();i++){
                JSONObject row=queue.optJSONObject(i);if(row==null)continue;
                int number=chapterFor(toc,wordStarts,row.optInt("start",0));
                ArrayList<JSONObject> list=groups.get(number);
                if(list==null){list=new ArrayList<>();groups.put(number,list);}list.add(row);
            }
            DocumentFile folder=chapters(ctx,uri,title,true);if(folder==null)return 0;
            int count=0;
            for(Map.Entry<Integer,ArrayList<JSONObject>> group:groups.entrySet()){
                int chapter=group.getKey();ArrayList<JSONObject> parts=group.getValue();
                if(parts.isEmpty())continue;
                boolean complete=true;StringBuilder sign=new StringBuilder();
                for(JSONObject row:parts){
                    File f=new File(localMp3,row.optString("key")+".mp3");
                    if(!f.isFile()||f.length()<1500){complete=false;break;}
                    sign.append(row.optString("key")).append(':').append(f.length()).append(':').append(digest(f)).append(';');
                }
                if(!complete)continue;
                String name=toc!=null&&chapter<toc.size()?toc.get(chapter).title:"Глава";
                String folderName=String.format(Locale.ROOT,"%03d - %s",chapter+1,safe(name,55));
                DocumentFile dir=folder.findFile(folderName);
                if(dir==null)dir=folder.createDirectory(folderName);
                if(dir==null)continue;
                String fingerprint=hash(sign.toString());
                DocumentFile prior=dir.findFile(INDEX_NAME);
                if(prior!=null)try{
                    JSONObject old=new JSONObject(text(ctx,prior,16*1024*1024));
                    if(fingerprint.equals(old.optString("fingerprint"))&&
                        "fragments".equals(old.optString("mode"))&&dir.findFile("fragments")!=null){count++;continue;}
                    if(fingerprint.equals(old.optString("fingerprint"))&&
                        dir.findFile("Глава.mp3")!=null){count++;continue;}
                }catch(Exception ignored){}
                DocumentFile clips=dir.findFile("fragments");
                if(clips==null)clips=dir.createDirectory("fragments");
                if(clips==null)throw new Exception("Нет папки фрагментов");
                JSONArray segments=new JSONArray();
                for(int i=0;i<parts.size();i++){
                    JSONObject row=parts.get(i);
                    String key=row.getString("key"),no=String.format(Locale.ROOT,"%04d",i+1);
                    File mp3=new File(localMp3,key+".mp3");
                    File words=new File(localMp3,key+".mp3.words.json");
                    if(!words.isFile())throw new Exception("Не найдена разметка WordBoundary");
                    write(ctx,clips,no+".mp3","audio/mpeg",mp3);
                    write(ctx,clips,no+".words.json","application/json",words);
                    JSONObject entry=new JSONObject(row.toString());
                    entry.put("sha256",digest(mp3));segments.put(entry);
                }
                JSONObject index=new JSONObject();index.put("version",2);
                index.put("mode","fragments");index.put("chapter",chapter+1);
                index.put("bookId",hash(uri));index.put("fingerprint",fingerprint);
                index.put("segments",segments);
                write(ctx,dir,INDEX_NAME,"application/json",index.toString().getBytes(StandardCharsets.UTF_8));
                count++;
            }
            if(count>0)invalidate();return count;
        }catch(Exception e){Log.w(TAG,"Failed to publish fragments",e);return 0;}
    }
    /** Read the durable manifest when the temporary full-book queue has been evicted. */
    static synchronized JSONArray queueFromSAF(Context c,String uri,String title,int number){
        JSONArray result=new JSONArray();
        try{
            DocumentFile folders=chapters(c,uri,title,false);
            if(folders==null)return result;
            for(DocumentFile dir:folders.listFiles()){
                if(!dir.isDirectory()||dir.getName()==null||!dir.getName().startsWith(
                    String.format(Locale.ROOT,"%03d - ",number+1)))continue;
                DocumentFile idx=dir.findFile(INDEX_NAME);if(idx==null)continue;
                JSONObject record=new JSONObject(text(c,idx,16*1024*1024));
                if(!hash(uri).equals(record.optString("bookId")))continue;
                JSONArray rows=record.optJSONArray("segments");
                if(rows!=null)for(int i=0;i<rows.length();i++){
                    JSONObject entry=rows.optJSONObject(i);if(entry!=null)result.put(entry);
                }
                break;
            }
        }catch(Exception e){Log.w(TAG,"Cannot load SAF chapter queue",e);}
        return result;
    }
    /** Manually merge an already persisted chapter: restore its pieces, merge and verify. */
    static synchronized boolean mergeChapter(Context ctx,String uri,String title,int chapter,File local,
        JSONArray queue,BookChapterIndex toc,ArrayList<Integer> starts){
        try{
            if(!local.isDirectory())local.mkdirs();
            restore(ctx,uri,title,local);
            // Convert the selected chapter ONLY; do not silently merge other chapters.
            JSONArray selected=new JSONArray();
            for(int i=0;i<queue.length();i++){
                JSONObject row=queue.optJSONObject(i);
                if(row!=null&&chapterFor(toc,starts,row.optInt("start",0))==chapter)selected.put(row);
            }
            if(selected.length()==0)return false;
            int merged=publish(ctx,uri,title,local,selected,toc,starts);
            boolean ok=snapshot(ctx,uri,title).state(chapter)==2;
            if(!ok||merged==0)return false;
            DocumentFile all=chapters(ctx,uri,title,false);
            if(all!=null)for(DocumentFile dir:all.listFiles()){
                if(dir.isDirectory()&&dir.getName()!=null&&dir.getName().startsWith(
                    String.format(Locale.ROOT,"%03d - ",chapter+1))){
                    DocumentFile stored=dir.findFile("Глава.mp3"),idx=dir.findFile(INDEX_NAME);
                    if(stored==null||idx==null)return false;
                    JSONObject index=new JSONObject(text(ctx,idx,16*1024*1024));
                    String expected=index.optString("audioSha256","");
                    if(expected.length()!=64)return false;
                    try(InputStream in=ctx.getContentResolver().openInputStream(stored.getUri())){
                        if(in==null||!expected.equals(digest(in)))return false;
                    }
                    DocumentFile fragments=dir.findFile("fragments");
                    if(fragments!=null)fragments.delete();
                }
            }
            invalidate();return true;
        }catch(Exception e){Log.w(TAG,"Manual merge failed",e);return false;}
    }
    /** Delete only the selected chapter and its SAF assets, never the rest of the book. */
    static synchronized boolean deleteChapter(Context ctx,String uri,String title,int chapter){
        try{
            DocumentFile all=chapters(ctx,uri,title,false);if(all==null)return true;
            for(DocumentFile dir:all.listFiles()){
                if(!dir.isDirectory()||dir.getName()==null||!dir.getName().startsWith(
                    String.format(Locale.ROOT,"%03d - ",chapter+1)))continue;
                DocumentFile idx=dir.findFile(INDEX_NAME);
                if(idx==null)continue;
                JSONObject data=new JSONObject(text(ctx,idx,16*1024*1024));
                if(!hash(uri).equals(data.optString("bookId")))return false;
                if(!dir.delete())return false;
            }
            invalidate();return true;
        }catch(Exception e){Log.w(TAG,"Delete chapter failed",e);return false;}
    }
    static final class SavedBook {
        final String uri,title;final int chapters;
        SavedBook(String uri,String title,int chapters){this.uri=uri;this.title=title;this.chapters=chapters;}
    }
    /** Find persisted SAF chapters even if Android has cleared our temporary cache. */
    static synchronized ArrayList<SavedBook> savedBooks(Context ctx){
        long now=android.os.SystemClock.elapsedRealtime();
        if(bookCache!=null&&now-bookCacheAt<15000L)return new ArrayList<>(bookCache);
        ArrayList<SavedBook> result=new ArrayList<>();
        if(!EdgeFolderPicker.available(ctx))return result;
        try{
            for(DocumentFile folder:root(ctx).listFiles()){
                if(!folder.isDirectory())continue;
                DocumentFile marker=folder.findFile(".edge-book.json");
                if(marker==null)continue;
                JSONObject book=new JSONObject(text(ctx,marker,4096));
                String uri=book.optString("uri",""),title=book.optString("title",folder.getName());
                if(uri.isEmpty()||!hash(uri).equals(book.optString("id")))continue;
                int count=0;DocumentFile chapters=folder.findFile("Главы");
                if(chapters!=null)for(DocumentFile chapter:chapters.listFiles())
                    if(chapter.isDirectory()&&chapter.findFile("chapter.index.json")!=null &&
                        (chapter.findFile("Глава.mp3")!=null||chapter.findFile("fragments")!=null))count++;
                if(count>0)result.add(new SavedBook(uri,title,count));
            }
        }catch(Exception ex){Log.w(TAG,"Cannot list chapter folders",ex);}
        bookCache=new ArrayList<>(result);bookCacheAt=now;
        return result;
    }
    /** Delete ONLY application-owned chapter assets after an explicit user request. */
    static boolean deleteSavedBook(Context ctx,String uri){
        if(!EdgeFolderPicker.available(ctx))return false;
        try{
            DocumentFile folder=book(ctx,uri,"",false);if(folder==null)return false;
            DocumentFile chapters=folder.findFile("Главы");if(chapters==null)return false;
            for(DocumentFile chapter:chapters.listFiles()){
                if(!chapter.isDirectory())continue;
                DocumentFile idx=chapter.findFile(INDEX_NAME);
                if(idx==null)continue;
                JSONObject index=new JSONObject(text(ctx,idx,16*1024*1024));
                if(!hash(uri).equals(index.optString("bookId")))continue;
                for(String filename:new String[]{"Глава.mp3","Глава.words.json",INDEX_NAME}){
                    DocumentFile file=chapter.findFile(filename);
                    if(file!=null&&!file.delete())throw new Exception("Невозможно удалить "+filename);
                }
                DocumentFile fragments=chapter.findFile("fragments");
                if(fragments!=null&&!fragments.delete())throw new Exception("Невозможно удалить фрагменты главы");
                if(chapter.listFiles().length==0)chapter.delete();
            }
            if(chapters.listFiles().length==0)chapters.delete();
            DocumentFile marker=folder.findFile(".edge-book.json");
            if(marker!=null&&folder.listFiles().length==1)marker.delete();
            if(folder.listFiles().length==0)folder.delete();
            invalidate();return true;
        }catch(Exception ex){Log.w(TAG,"Cannot delete saved chapter",ex);return false;}
    }

}
