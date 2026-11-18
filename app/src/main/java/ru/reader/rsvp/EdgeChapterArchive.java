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
 * SAF holds independent MP3 audio fragments and WordBoundary timing files
 * per completed chapter. The chapter index is the commit point. Old merged
 * chapter archives remain readable; new merged MP3s are exported ONLY by
 * explicit user action into the AudioLibrary, without affecting the Edge source.
 *
 * Never delete a working fragment unless the published files are verified.
 */
final class EdgeChapterArchive {
    private static final String TAG="EdgeChapterArchive";
    private static final String INDEX_NAME="chapter.index.json";
    static final String AUTO_MERGE_PREF="edge_auto_merge_chapter_mp3_v151";
    static boolean autoMerge(Context c){return false;} // Legacy preference is deliberately ignored in v173.
    private static ArrayList<SavedBook> bookCache;
    private static long bookCacheAt;
    private static final Map<String,ChapterSnapshot> chapterCache=new HashMap<>();
    private static final Map<String,Long> chapterCacheAt=new HashMap<>();
    private static final class LazyChapter {
        final JSONObject index;final java.util.Map<String,JSONObject> rows=new HashMap<>();
        final java.util.Map<String,DocumentFile> files=new HashMap<>();
        LazyChapter(JSONObject data,DocumentFile clips){
            index=data;
            JSONArray segments=data.optJSONArray("segments");
            if(segments!=null)for(int i=0;i<segments.length();i++){
                JSONObject item=segments.optJSONObject(i);
                if(item!=null)rows.put(item.optString("key"),item);
            }
            if(clips!=null)for(DocumentFile file:clips.listFiles())
                if(file!=null&&file.getName()!=null)files.put(file.getName(),file);
        }
    }
    private static final java.util.HashSet<String> lazyMissingChapters=new java.util.HashSet<>();
    private static final java.util.LinkedHashMap<String,LazyChapter> lazyChapters=
        new java.util.LinkedHashMap<String,LazyChapter>(16,0.75f,true){
            @Override protected boolean removeEldestEntry(java.util.Map.Entry<String,LazyChapter> entry){
                return size()>12;
            }
        };
    private static final java.util.Map<String,DocumentFile> locatedBooks=new java.util.concurrent.ConcurrentHashMap<>();
    private static long cacheEpoch;
    private static final Object[] SNAPSHOT_LOCKS=new Object[32];
    static {for(int i=0;i<SNAPSHOT_LOCKS.length;i++)SNAPSHOT_LOCKS[i]=new Object();}
    static synchronized void invalidate(){cacheEpoch++;bookCache=null;bookCacheAt=0;chapterCache.clear();chapterCacheAt.clear();lazyChapters.clear();lazyMissingChapters.clear();locatedBooks.clear();}
    static final class ChapterSnapshot {
        final Map<Integer,Integer> states=new HashMap<>(); // 0=none,1=fragments,2=merged
        final Map<Integer,Integer> dictionaryRevisions=new HashMap<>();
        int state(int chapter){Integer v=states.get(chapter);return v==null?0:v;}
        int dictionaryRevision(int chapter){Integer v=dictionaryRevisions.get(chapter);return v==null?-1:v;}
    }
    /** SAF snapshot I/O must not monopolize the global archive monitor.
     * A striped book lock serializes concurrent readers of the same index while
     * the metadata cache itself has only short synchronized critical sections.
     */
    static ChapterSnapshot snapshot(Context c,String uri,String title){
        String key=(EdgeFolderPicker.selected(c)==null?"":EdgeFolderPicker.selected(c).toString())+":"+uri;
        final Object lock=SNAPSHOT_LOCKS[(key.hashCode()&0x7fffffff)%SNAPSHOT_LOCKS.length];
        synchronized(lock){
            long epoch;
            synchronized(EdgeChapterArchive.class){
                ChapterSnapshot cached=chapterCache.get(key);
                if(cached!=null)return cached;
                epoch=cacheEpoch;
            }
            ChapterSnapshot result=new ChapterSnapshot();
            try{
                DocumentFile dir=chapters(c,uri,title,false);
                if(dir!=null)for(DocumentFile chapter:dir.listFiles()){
                    if(!chapter.isDirectory())continue;
                    DocumentFile idx=chapter.findFile(INDEX_NAME);
                    if(idx==null)continue;
                    JSONObject row=new JSONObject(text(c,idx,16*1024*1024));
                    if(!hash(BookUriMigration.legacy(c,uri)).equals(row.optString("bookId")))continue;
                    int number=row.optInt("chapter",0)-1;
                    if(number<0)continue;
                    boolean merged=mergedPresent(c,chapter,row);
                    boolean clips=fragmentsPresent(chapter,row);
                    if(merged||clips){
                        result.states.put(number,merged?2:1);
                        result.dictionaryRevisions.put(number,row.optInt("dictionaryRevision",-1));
                    }
                }
            }catch(Exception error){Log.w(TAG,"SAF chapter snapshot error",error);}
            synchronized(EdgeChapterArchive.class){
                // A successful publish/rename can invalidate the cache while SAF is read.
                // Never publish a stale snapshot from an earlier index generation.
                if(epoch==cacheEpoch){chapterCache.put(key,result);
                    chapterCacheAt.put(key,android.os.SystemClock.elapsedRealtime());}
                return chapterCache.getOrDefault(key,result);
            }
        }
    }
    private EdgeChapterArchive(){}

    /** Display names for newly prepared MP3 chapters; no generic "Глава.mp3". */
    static String chapterAudioName(int chapter,BookChapterIndex toc){
        String number=String.format(Locale.ROOT,"%03d",chapter+1);
        if(toc==null||toc.size()==0||chapter<0||chapter>=toc.size())return number+".mp3";
        String title=safe(toc.get(chapter).title,88);
        return number+" - "+title+".mp3";
    }
    private static boolean simpleFileName(String name,String suffix){
        return name!=null&&name.endsWith(suffix)&&!name.contains("/")&&name.indexOf('\\')<0
            &&!name.contains("..")&&name.length()<180;
    }
    private static String audioName(JSONObject index){
        String name=index.optString("audioFile","");
        // Existing published chapters are still readable after this naming change.
        return simpleFileName(name,".mp3")?name:"Глава.mp3";
    }
    private static String wordsName(JSONObject index){
        String name=index.optString("wordsFile","");
        return simpleFileName(name,".json")?name:"Глава.words.json";
    }
    /** A fragment chapter is complete only when every indexed MP3 and its word timings exist. */
    private static boolean fragmentsPresent(DocumentFile chapter,JSONObject index){
        if(!"fragments".equals(index.optString("mode")))return false;
        JSONArray parts=index.optJSONArray("segments");
        if(parts==null||parts.length()==0)return false;
        DocumentFile folder=chapter.findFile("fragments");
        if(folder==null||!folder.isDirectory())return false;
        java.util.HashSet<String> names=new java.util.HashSet<>();
        for(DocumentFile f:folder.listFiles())if(f!=null&&f.isFile()&&f.getName()!=null)names.add(f.getName());
        for(int i=0;i<parts.length();i++){
            String no=String.format(Locale.ROOT,"%04d",i+1);
            JSONObject entry=parts.optJSONObject(i);
            if(entry==null)return false;
            if(!names.contains(entry.optString("audioFile",no+".mp3"))||
                !names.contains(entry.optString("wordsFile",no+".words.json")))return false;
        }
        return true;
    }
    private static boolean mergedPresent(Context ctx,DocumentFile chapter,JSONObject index){
        return index.optString("audioSha256","").length()==64
            &&indexedFile(ctx,chapter,index,"audioUri","audioFile",audioName(index))!=null
            &&indexedFile(ctx,chapter,index,"wordsUri","wordsFile",wordsName(index))!=null;
    }
    /** Reuse the exact document returned by SAF, which may rename created files. */
    private static DocumentFile indexedFile(Context ctx,DocumentFile dir,JSONObject index,
            String uriKey,String nameKey,String fallback){
        String name=index.optString(nameKey,fallback);
        DocumentFile found=dir.findFile(name);
        if(found!=null)return found;
        String stored=index.optString(uriKey,"");
        if(stored.isEmpty())return null;
        try{
            Uri fileUri=Uri.parse(stored);
            if(!dir.getUri().getAuthority().equals(fileUri.getAuthority()))return null;
            DocumentFile indexed=DocumentFile.fromSingleUri(ctx,fileUri);
            return indexed!=null&&indexed.exists()?indexed:null;
        }catch(Exception ignored){return null;}
    }

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
    /** Book identity marker is small and has a stable filename. A new book has no
     * earlier data to replace; write directly instead of relying on SAF rename.
     */
    private static void writeBookMarker(Context ctx,DocumentFile dir,byte[] content)throws Exception {
        DocumentFile marker=dir.createFile("application/json",".edge-book.json");
        if(marker==null)throw new Exception("SAF: не удалось создать маркер книги");
        if(!".edge-book.json".equals(marker.getName())){
            marker.delete();
            throw new Exception("SAF изменил имя маркера книги: "+marker.getName());
        }
        try(OutputStream out=ctx.getContentResolver().openOutputStream(marker.getUri(),"w")){
            if(out==null)throw new Exception("SAF: нет потока записи маркера книги");
            out.write(content);out.flush();
        }catch(Exception error){try{marker.delete();}catch(Exception ignored){}throw error;}
    }
    /** New, uniquely named SAF document. Never rename a temporary MP3 or overwrite the
     * previous valid chapter. The returned URI is authoritative, not a guessed name.
     */
    private static DocumentFile writeNew(Context c,DocumentFile dir,String name,String mime,File source)
            throws Exception {
        DocumentFile output=dir.createFile(mime,uniqueName(name));
        if(output==null)throw new Exception("SAF: не удалось создать MP3 главы");
        try(InputStream input=new FileInputStream(source);
            OutputStream stream=c.getContentResolver().openOutputStream(output.getUri(),"w")){
            if(stream==null)throw new Exception("SAF: нет потока записи MP3");
            byte[] buffer=new byte[32768];int n;
            while((n=input.read(buffer))!=-1)stream.write(buffer,0,n);
            stream.flush();
        }catch(Exception error){try{output.delete();}catch(Exception ignored){}throw error;}
        return output;
    }
    private static DocumentFile writeNew(Context c,DocumentFile dir,String name,String mime,byte[] content)
            throws Exception {
        DocumentFile output=dir.createFile(mime,uniqueName(name));
        if(output==null)throw new Exception("SAF: не удалось создать "+name);
        try(OutputStream stream=c.getContentResolver().openOutputStream(output.getUri(),"w")){
            if(stream==null)throw new Exception("SAF: нет потока записи "+name);
            stream.write(content);stream.flush();
        }catch(Exception error){try{output.delete();}catch(Exception ignored){}throw error;}
        return output;
    }
    private static String uniqueName(String name){
        int dot=name.lastIndexOf('.');
        String token="-"+Long.toHexString(System.nanoTime());
        return dot>0?name.substring(0,dot)+token+name.substring(dot):name+token;
    }
    /** Commit point. Only a tiny JSON manifest is updated, after successful MP3 and
     * WordBoundary stream closure. On failure restore an earlier manifest if possible.
     */
    private static void writeManifest(Context ctx,DocumentFile dir,JSONObject index)throws Exception {
        DocumentFile manifest=dir.findFile(INDEX_NAME);
        boolean created=manifest==null;
        byte[] backup=null;
        if(manifest!=null)backup=text(ctx,manifest,16*1024*1024).getBytes(StandardCharsets.UTF_8);
        if(created)manifest=dir.createFile("application/json",INDEX_NAME);
        if(manifest==null)throw new Exception("SAF: не удалось создать индекс главы");
        if(!INDEX_NAME.equals(manifest.getName())){
            if(created)manifest.delete();
            throw new Exception("SAF изменил имя индекса главы: "+manifest.getName());
        }
        byte[] content=index.toString().getBytes(StandardCharsets.UTF_8);
        try(OutputStream out=ctx.getContentResolver().openOutputStream(manifest.getUri(),"wt")){
            if(out==null)throw new Exception("SAF: нет потока записи индекса");
            out.write(content);out.flush();
        }catch(Exception error){
            if(created){try{manifest.delete();}catch(Exception ignored){}}
            else if(backup!=null){
                try(OutputStream restore=ctx.getContentResolver().openOutputStream(manifest.getUri(),"wt")){
                    if(restore!=null){restore.write(backup);restore.flush();}
                }catch(Exception rollback){Log.e(TAG,"Manifest restore failed",rollback);}
            }
            throw error;
        }
    }
    private static void deleteAfterCommit(DocumentFile folder,JSONObject previous,
            DocumentFile audio,DocumentFile words){
        if(previous==null)return;
        for(String oldName:new String[]{audioName(previous),wordsName(previous)}){
            try{
                DocumentFile old=folder.findFile(oldName);
                if(old!=null&&!old.getUri().equals(audio.getUri())&&
                    !old.getUri().equals(words.getUri()))old.delete();
            }catch(Exception ignored){Log.w(TAG,"Old generation cleanup deferred");}
        }
    }
    private static DocumentFile book(Context c,String uri,String title,boolean create)throws Exception {
        DocumentFile r=root(c);
        String basename=safe(title,75),identity=hash(BookUriMigration.legacy(c,uri));
        String lookupKey=r.getUri().toString()+"|"+identity;
        DocumentFile located=locatedBooks.get(lookupKey);
        if(located!=null)return located;
        // Reuse a book folder even if the user renamed the book in the library.
        for(DocumentFile candidate:r.listFiles()){
            if(!candidate.isDirectory())continue;
            DocumentFile marker=candidate.findFile(".edge-book.json");
            if(marker!=null)try{
                if(identity.equals(new JSONObject(text(c,marker,4096)).optString("id"))){
                    locatedBooks.put(lookupKey,candidate);return candidate;
                }
            }catch(Exception ignored){}
        }
        for(int n=1;n<=100;n++){
            String name=n==1?basename:basename+" ("+n+")";
            DocumentFile folder=r.findFile(name);
            if(folder==null){if(!create)continue;folder=r.createDirectory(name);
                if(folder==null)throw new Exception("Нельзя создать папку книги");}
            DocumentFile marker=folder.findFile(".edge-book.json");
            if(marker!=null){
                try{if(identity.equals(new JSONObject(text(c,marker,4096)).optString("id"))){
                    locatedBooks.put(lookupKey,folder);return folder;
                }}
                catch(Exception ignored){} // A foreign folder: don't overwrite it.
                continue;
            }
            if(!create)continue;
            JSONObject info=new JSONObject();info.put("id",identity);info.put("title",title);info.put("uri",uri);
            writeBookMarker(c,folder,info.toString().getBytes(StandardCharsets.UTF_8));
            locatedBooks.put(lookupKey,folder);return folder;
        }
        throw new Exception("Не удалось найти папку книги");
    }
    private static DocumentFile chapters(Context c,String uri,String title,boolean create)throws Exception {
        DocumentFile folder=book(c,uri,title,create);if(folder==null)return null;
        DocumentFile result=folder.findFile("Главы");
        return result==null&&create?folder.createDirectory("Главы"):result;
    }
    static int chapterFor(BookChapterIndex chapters,ArrayList<Integer> starts,int word){
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
    private static volatile String lastPublishError="";
    private static volatile String currentPublishStage="";
    static String publicationError(){return lastPublishError;}
    private static void publishStage(String stage){currentPublishStage=stage;}
    private static void publishFailure(Exception cause){
        String detail=cause.getMessage();
        if(detail==null||detail.trim().isEmpty())detail=cause.getClass().getSimpleName();
        lastPublishError=currentPublishStage+": "+detail;
        ReaderDiagnosticLog.event("EDGE_EXPORT","failed stage="+currentPublishStage+" type="+
            cause.getClass().getSimpleName());
    }
    static synchronized int publish(Context ctx,String uri,String title,File localMp3,
        JSONArray queue,BookChapterIndex chapters,ArrayList<Integer> wordStarts){
        lastPublishError="";publishStage("Проверка папки и очереди");
        if(!EdgeFolderPicker.available(ctx)||queue==null||queue.length()==0){
            lastPublishError="Папка недоступна или очередь пуста";return 0;
        }
        try{
            Map<Integer,ArrayList<JSONObject>> groups=new HashMap<>();
            for(int i=0;i<queue.length();i++){
                JSONObject row=queue.optJSONObject(i);if(row==null)continue;
                int chapter=chapterFor(chapters,wordStarts,row.optInt("start",0));
                ArrayList<JSONObject> list=groups.get(chapter);
                if(list==null){list=new ArrayList<>();groups.put(chapter,list);}list.add(row);
            }
            publishStage("Открытие папки глав Android SAF");
            DocumentFile all=chapters(ctx,uri,title,true);
            if(all==null)throw new Exception("Нет папки глав");
            int published=0;
            for(Map.Entry<Integer,ArrayList<JSONObject>> group:groups.entrySet()){
                int chapter=group.getKey();ArrayList<JSONObject> parts=group.getValue();
                if(parts.isEmpty())continue;
                boolean complete=true;StringBuilder signature=new StringBuilder();
                long newestClipAt=0L;
                for(JSONObject row:parts){String key=row.optString("key","");
                    File f=new File(localMp3,key+".mp3");
                    if(!key.matches("[a-f0-9]{64}")||!f.isFile()||f.length()<1500){complete=false;break;}
                    signature.append(key).append(':').append(f.length()).append(';');
                    newestClipAt=Math.max(newestClipAt,f.lastModified());
                }
                if(!complete){lastPublishError="Глава "+(chapter+1)+": не все MP3 находятся в кэше";continue;}
                publishStage("Подготовка главы "+(chapter+1));
                String titleChapter=chapters!=null&&chapter<chapters.size()?chapters.get(chapter).title:"Глава";
                String folderName=String.format(Locale.ROOT,"%03d - %s",chapter+1,safe(titleChapter,55));
                DocumentFile dir=all.findFile(folderName);
                if(dir==null)dir=all.createDirectory(folderName);
                if(dir==null)continue;
                String fingerprint=hash(signature.toString());
                String audioFile=chapterAudioName(chapter,chapters);
                String wordsFile=audioFile.substring(0,audioFile.length()-4)+".words.json";
                DocumentFile existing=dir.findFile(INDEX_NAME);
                JSONObject previousIndex=null;
                if(existing!=null){
                    try{JSONObject previous=new JSONObject(text(ctx,existing,16*1024*1024));
                        previousIndex=previous;
                        if(fingerprint.equals(previous.optString("fingerprint")) &&
                          newestClipAt<=previous.optLong("publishedAt",Long.MAX_VALUE) &&
                          mergedPresent(ctx,dir,previous)){
                            published++;continue;
                        }
                    }catch(Exception ignored){}
                }
                File temporary=File.createTempFile("edge-chapter-",".mp3",ctx.getCacheDir());
                long combinedBytes=0L;
                JSONArray segments=new JSONArray(),words=new JSONArray();long timeMs=0;
                int chapterChar=chapters!=null&&chapter<chapters.size()?chapters.get(chapter).startChar:0;
                publishStage("Объединение MP3 главы "+(chapter+1));
                MessageDigest chapterDigest=MessageDigest.getInstance("SHA-256");
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
                        combined.write(fr.frames);chapterDigest.update(fr.frames);
                        combinedBytes+=fr.frames.length;timeMs+=fr.durationMs;
                    }
                    combined.flush();
                }catch(Exception e){temporary.delete();throw e;}
                JSONObject sidecar=new JSONObject();sidecar.put("version",2).put("durationMs",timeMs);
                sidecar.put("chapter",chapter+1);sidecar.put("words",words);
                JSONObject index=new JSONObject();index.put("version",1);index.put("bookId",hash(BookUriMigration.legacy(ctx,uri)));
                index.put("chapter",chapter+1);index.put("fingerprint",fingerprint);
                index.put("publishedAt",System.currentTimeMillis());
                index.put("length",combinedBytes);
                StringBuilder sha=new StringBuilder();
                for(byte digit:chapterDigest.digest())sha.append(String.format(Locale.ROOT,"%02x",digit&255));
                index.put("audioSha256",sha.toString());
                index.put("segments",segments);
                // One streaming write per asset; no .pending rename, SAF reread or
                // whole-folder scan. Keep previous indexed MP3 until commit succeeds.
                DocumentFile newAudio=null,newWords=null;
                try{
                    publishStage("Однократная запись MP3 главы "+(chapter+1));
                    newAudio=writeNew(ctx,dir,audioFile,"audio/mpeg",temporary);
                    index.put("audioFile",newAudio.getName());
                    index.put("audioUri",newAudio.getUri().toString());
                    publishStage("Запись WordBoundary главы "+(chapter+1));
                    newWords=writeNew(ctx,dir,wordsFile,"application/json",
                        sidecar.toString().getBytes(StandardCharsets.UTF_8));
                    index.put("wordsFile",newWords.getName());
                    index.put("wordsUri",newWords.getUri().toString());
                    publishStage("Фиксация индекса главы "+(chapter+1));
                    writeManifest(ctx,dir,index);
                    published++;
                    deleteAfterCommit(dir,previousIndex,newAudio,newWords);
                }catch(Exception error){
                    if(newAudio!=null)try{newAudio.delete();}catch(Exception ignored){}
                    if(newWords!=null)try{newWords.delete();}catch(Exception ignored){}
                    throw error;
                }finally{temporary.delete();}
            }
            if(published>0){invalidate();DownloadChangeBus.publish("edge:"+uri);}
            return published;
        }catch(Exception ex){publishFailure(ex);Log.w(TAG,"Could not publish chapter",ex);return 0;}
    }
    /** Publish just the requested chapter; a broken neighbouring chapter cannot
     * invalidate a successfully generated chapter. No SAF lookup is needed here.
     */
    private static JSONArray oneChapter(JSONArray queue,BookChapterIndex toc,
            ArrayList<Integer> wordStarts,int chapter){
        if(chapter<0)return queue;
        JSONArray selected=new JSONArray();
        if(queue!=null)for(int i=0;i<queue.length();i++){
            JSONObject row=queue.optJSONObject(i);
            if(row!=null&&chapterFor(toc,wordStarts,row.optInt("start",0))==chapter)
                selected.put(row);
        }
        return selected;
    }
    static synchronized int publishOne(Context ctx,String uri,String title,File localMp3,
            JSONArray queue,BookChapterIndex toc,ArrayList<Integer> wordStarts,int chapter){
        return publish(ctx,uri,title,localMp3,oneChapter(queue,toc,wordStarts,chapter),toc,wordStarts);
    }
    static synchronized int publishFragmentsOne(Context ctx,String uri,String title,File localMp3,
            JSONArray queue,BookChapterIndex toc,ArrayList<Integer> wordStarts,int chapter){
        return publishFragments(ctx,uri,title,localMp3,oneChapter(queue,toc,wordStarts,chapter),toc,wordStarts);
    }
    /** Rehydrate fragments from completed SAF chapters to a disposable internal cache. */
    static synchronized int restore(Context ctx,String uri,String title,File target){
        return restoreChapter(ctx,uri,title,target,-1);
    }
    /** Lazy restore: only this chapter when the current job lacks cached clips. */
    static synchronized int restoreChapter(Context ctx,String uri,String title,File target,
            int chapterNumber){
        if(!EdgeFolderPicker.available(ctx))return 0;
        try{
            DocumentFile all=chapters(ctx,uri,title,false);
            if(all==null)return 0;
            if(!target.isDirectory()&&!target.mkdirs())return 0;
            int restored=0;
            for(DocumentFile chapter:all.listFiles()){
                if(!chapter.isDirectory())continue;
                if(chapterNumber>=0 && (chapter.getName()==null ||
                    !chapter.getName().startsWith(String.format(Locale.ROOT,"%03d - ",chapterNumber+1))))
                    continue;
                DocumentFile idx=chapter.findFile(INDEX_NAME);
                if(idx==null)continue;
                JSONObject index=new JSONObject(text(ctx,idx,16*1024*1024));
                DocumentFile mp3=indexedFile(ctx,chapter,index,"audioUri","audioFile",audioName(index));
                if(!hash(BookUriMigration.legacy(ctx,uri)).equals(index.optString("bookId")))continue;
                if(index.optInt("version")==2&&"fragments".equals(index.optString("mode"))){
                    DocumentFile clips=chapter.findFile("fragments");
                    JSONArray rows=index.optJSONArray("segments");
                    if(clips==null||rows==null)continue;
                    for(int i=0;i<rows.length();i++){
                        JSONObject row=rows.optJSONObject(i);if(row==null)continue;
                        String key=row.optString("key","");
                        if(!key.matches("[a-f0-9]{64}"))continue;
                        String name=String.format(Locale.ROOT,"%04d",i+1);
                        DocumentFile src=clips.findFile(row.optString("audioFile",name+".mp3"));
                        DocumentFile words=clips.findFile(row.optString("wordsFile",name+".words.json"));
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
    /** Load one chapter's manifest and document map once for the nearby clip window.
     * Immutable indices are invalidated on publish/delete/folder change, not by timer.
     */
    private static LazyChapter lazyChapter(Context ctx,String uri,String title,int chapterNumber)
            throws Exception {
        Uri tree=EdgeFolderPicker.selected(ctx);
        String cacheKey=String.valueOf(tree)+"|"+uri+"|"+chapterNumber;
        LazyChapter cached=lazyChapters.get(cacheKey);
        if(cached!=null)return cached;
        // A fresh book has no SAF chapter at all. Never traverse its folder for
        // every single text fragment until a publish/invalidation event arrives.
        if(lazyMissingChapters.contains(cacheKey))return null;
        DocumentFile all=chapters(ctx,uri,title,false);
        if(all==null){lazyMissingChapters.add(cacheKey);return null;}
        String prefix=String.format(Locale.ROOT,"%03d - ",chapterNumber+1);
        for(DocumentFile chapter:all.listFiles()){
            if(chapter==null||!chapter.isDirectory()||chapter.getName()==null||
                !chapter.getName().startsWith(prefix))continue;
            DocumentFile manifest=chapter.findFile(INDEX_NAME);
            if(manifest==null){lazyMissingChapters.add(cacheKey);return null;}
            JSONObject index=new JSONObject(text(ctx,manifest,16*1024*1024));
            if(!hash(BookUriMigration.legacy(ctx,uri)).equals(index.optString("bookId"))){
                lazyMissingChapters.add(cacheKey);return null;
            }
            DocumentFile clips=index.optInt("version")==2?chapter.findFile("fragments"):null;
            LazyChapter result=new LazyChapter(index,clips);
            lazyChapters.put(cacheKey,result);
            return result;
        }
        lazyMissingChapters.add(cacheKey);
        return null;
    }
    /** v174: only a requested MP3+WordBoundary is restored. No full-book walk,
     * no repeated SHA-256 reads for a fragment already present in the local cache.
     * Called only on the chapter publisher executor, never on the main thread.
     */
    static synchronized boolean restoreClip(Context ctx,String uri,String title,File target,
            int chapterNumber,String key){
        if(key==null||!key.matches("[a-fA-F0-9]{64}"))return false;
        File audio=new File(target,key+".mp3");
        File words=new File(target,key+".mp3.words.json");
        if(audio.isFile()&&audio.length()>1500&&words.isFile()&&words.length()>0)return true;
        try{
            if(!target.isDirectory()&&!target.mkdirs())return false;
            LazyChapter chapter=lazyChapter(ctx,uri,title,chapterNumber);
            if(chapter==null)return false;
            JSONObject index=chapter.index;
            if(index.optInt("version")==1){
                // Old merged chapters remain readable via bounded chapter extraction.
                restoreChapter(ctx,uri,title,target,chapterNumber);
                return audio.isFile()&&audio.length()>1500;
            }
            if(index.optInt("version")!=2||!"fragments".equals(index.optString("mode")))return false;
            JSONObject row=chapter.rows.get(key);
            if(row==null)return false;
            DocumentFile source=chapter.files.get(row.optString("audioFile",""));
            DocumentFile timing=chapter.files.get(row.optString("wordsFile",""));
            if(source==null||timing==null)return false;
            if(!audio.isFile()||audio.length()!=source.length()){
                File part=new File(target,key+".lazy.part");
                try(InputStream in=ctx.getContentResolver().openInputStream(source.getUri());
                    OutputStream out=new FileOutputStream(part)){
                    if(in==null)throw new Exception("SAF MP3 недоступен");
                    byte[] buffer=new byte[32768];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                if(part.length()!=source.length()||
                    !row.optString("sha256").equals(digest(part))){part.delete();return false;}
                if(audio.exists()&&!audio.delete()){part.delete();return false;}
                if(!part.renameTo(audio))throw new Exception("Не удалось сохранить MP3 фрагмент");
            }
            if(!words.isFile()||words.length()==0){
                File temp=new File(target,key+".words.lazy.part");
                try(InputStream in=ctx.getContentResolver().openInputStream(timing.getUri());
                    OutputStream out=new FileOutputStream(temp)){
                    if(in==null)throw new Exception("SAF WordBoundary недоступен");
                    byte[] buffer=new byte[8192];int n;
                    while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                }
                if(temp.length()==0){temp.delete();return false;}
                if(words.exists()&&!words.delete()){temp.delete();return false;}
                if(!temp.renameTo(words))throw new Exception("Не удалось сохранить WordBoundary");
            }
            return audio.isFile()&&audio.length()>1500&&words.isFile();
        }catch(Exception problem){Log.w(TAG,"Lazy restore failed, chapter="+chapterNumber,problem);}
        return false;
    }
    /** Publish all complete chapter fragments without auto-merging. SAF is authoritative. */
    static synchronized int publishFragments(Context ctx,String uri,String title,File localMp3,
        JSONArray queue,BookChapterIndex toc,ArrayList<Integer> wordStarts){
        lastPublishError="";publishStage("Проверка фрагментов и папки");
        if(!EdgeFolderPicker.available(ctx)||queue==null||queue.length()==0){lastPublishError="Папка или очередь недоступна";return 0;}
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
                int missingAudio=0,missingTiming=0;String firstMissing="";
                for(JSONObject row:parts){
                    String clipKey=row.optString("key");
                    File f=new File(localMp3,clipKey+".mp3");
                    File timings=new File(localMp3,clipKey+".mp3.words.json");
                    if(!f.isFile()||f.length()<1500){
                        complete=false;missingAudio++;
                        if(firstMissing.isEmpty())firstMissing=clipKey.substring(0,Math.min(12,clipKey.length()));
                        continue;
                    }
                    if(!timings.isFile()||timings.length()==0){
                        complete=false;missingTiming++;
                        if(firstMissing.isEmpty())firstMissing=clipKey.substring(0,Math.min(12,clipKey.length()));
                        continue;
                    }
                    sign.append(clipKey).append(':').append(f.length()).append(':').append(f.lastModified()).append(';');
                }
                if(!complete){
                    lastPublishError="Глава "+(chapter+1)+": нет MP3="+missingAudio+
                        ", нет разметки="+missingTiming+", первый ключ="+firstMissing;
                    Log.w(TAG,"Публикация отложена: "+lastPublishError);
                    continue;
                }
                String name=toc!=null&&chapter<toc.size()?toc.get(chapter).title:"Глава";
                String folderName=String.format(Locale.ROOT,"%03d - %s",chapter+1,safe(name,55));
                DocumentFile dir=folder.findFile(folderName);
                if(dir==null)dir=folder.createDirectory(folderName);
                if(dir==null)continue;
                String fingerprint=hash(sign.toString());
                DocumentFile prior=dir.findFile(INDEX_NAME);
                JSONObject previousIndex=null;
                if(prior!=null)try{
                    JSONObject old=new JSONObject(text(ctx,prior,16*1024*1024));
                    previousIndex=old;
                    if(fingerprint.equals(old.optString("fingerprint"))&&
                        "fragments".equals(old.optString("mode"))&&fragmentsPresent(dir,old)){
                        if(old.optInt("dictionaryRevision",-1)!=EdgeDictionaryStore.get(ctx).revisionNumber()){
                            old.put("dictionaryRevision",EdgeDictionaryStore.get(ctx).revisionNumber());
                            writeManifest(ctx,dir,old);
                        }
                        count++;continue;
                    }
                    if(fingerprint.equals(old.optString("fingerprint"))&&
                        mergedPresent(ctx,dir,old)){count++;continue;}
                }catch(Exception ignored){}
                publishStage("Сохранение фрагментов главы "+(chapter+1));
                DocumentFile clips=dir.findFile("fragments");
                if(clips==null)clips=dir.createDirectory("fragments");
                if(clips==null)throw new Exception("Нет папки фрагментов");
                JSONArray segments=new JSONArray();
                ArrayList<DocumentFile> justCreated=new ArrayList<>();
                try{
                    for(int i=0;i<parts.size();i++){
                        JSONObject row=parts.get(i);
                        String key=row.getString("key"),no=String.format(Locale.ROOT,"%04d",i+1);
                        File mp3=new File(localMp3,key+".mp3");
                        File words=new File(localMp3,key+".mp3.words.json");
                        if(!words.isFile())throw new Exception("Не найдена разметка WordBoundary");
                        DocumentFile audioDoc=writeNew(ctx,clips,no+".mp3","audio/mpeg",mp3);
                        justCreated.add(audioDoc);
                        DocumentFile wordsDoc=writeNew(ctx,clips,no+".words.json","application/json",words);
                        justCreated.add(wordsDoc);
                        JSONObject entry=new JSONObject(row.toString());
                        entry.put("audioFile",audioDoc.getName());entry.put("wordsFile",wordsDoc.getName());
                        entry.put("sha256",digest(mp3));segments.put(entry);
                    }
                    JSONObject index=new JSONObject();index.put("version",2);
                    index.put("mode","fragments");index.put("chapter",chapter+1);
                    index.put("bookId",hash(BookUriMigration.legacy(ctx,uri)));index.put("fingerprint",fingerprint);
                    index.put("dictionaryRevision",EdgeDictionaryStore.get(ctx).revisionNumber());
                    index.put("segments",segments);
                    writeManifest(ctx,dir,index);
                    count++;
                    // Once the new manifest is committed, release the older generation.
                    if(previousIndex!=null&&"fragments".equals(previousIndex.optString("mode"))){
                        JSONArray older=previousIndex.optJSONArray("segments");
                        if(older!=null)for(int n=0;n<older.length();n++){
                            JSONObject part=older.optJSONObject(n);if(part==null)continue;
                            String prefix=String.format(Locale.ROOT,"%04d",n+1);
                            for(String staleName:new String[]{part.optString("audioFile",prefix+".mp3"),
                                    part.optString("wordsFile",prefix+".words.json")}){
                                try{DocumentFile stale=clips.findFile(staleName);if(stale!=null)stale.delete();}
                                catch(Exception ignored){Log.w(TAG,"Old fragment cleanup deferred");}
                            }
                        }
                    }
                }catch(Exception e){
                    for(DocumentFile file:justCreated)try{file.delete();}catch(Exception ignored){}
                    throw e;
                }
            }
            if(count>0){invalidate();DownloadChangeBus.publish("edge:"+uri);}return count;
        }catch(Exception e){publishFailure(e);Log.w(TAG,"Failed to publish fragments",e);return 0;}
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
                if(!hash(BookUriMigration.legacy(c,uri)).equals(record.optString("bookId")))continue;
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
            int merged=publishOne(ctx,uri,title,local,selected,toc,starts,chapter);
            if(merged==0)return false;
            // Successful manifest commit already confirms the merged chapter.
            // Do not reread its full MP3 over SAF just to finish a manual merge.
            DocumentFile all=chapters(ctx,uri,title,false);
            if(all!=null)for(DocumentFile dir:all.listFiles()){
                if(dir.isDirectory()&&dir.getName()!=null&&dir.getName().startsWith(
                    String.format(Locale.ROOT,"%03d - ",chapter+1))){
                    DocumentFile fragments=dir.findFile("fragments");
                    if(fragments!=null)fragments.delete();
                }
            }
            invalidate();DownloadChangeBus.publish("edge:"+uri);return true;
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
                if(!hash(BookUriMigration.legacy(ctx,uri)).equals(data.optString("bookId")))return false;
                if(!dir.delete())return false;
            }
            invalidate();DownloadChangeBus.publish("edge:"+uri);return true;
        }catch(Exception e){Log.w(TAG,"Delete chapter failed",e);return false;}
    }
    static final class SavedBook {
        final String uri,title;final int chapters;
        SavedBook(String uri,String title,int chapters){this.uri=uri;this.title=title;this.chapters=chapters;}
    }
    /** Find persisted SAF chapters even if Android has cleared our temporary cache. */
    static synchronized ArrayList<SavedBook> savedBooks(Context ctx){
        long now=android.os.SystemClock.elapsedRealtime();
        if(bookCache!=null)return new ArrayList<>(bookCache);
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
                    if(chapter.isDirectory()&&chapter.findFile(INDEX_NAME)!=null){
                        JSONObject index=new JSONObject(text(ctx,chapter.findFile(INDEX_NAME),16*1024*1024));
                        if(mergedPresent(ctx,chapter,index)||chapter.findFile("fragments")!=null)count++;
                    }
                if(count>0)result.add(new SavedBook(BookUriMigration.current(ctx,uri),title,count));
            }
        }catch(Exception ex){Log.w(TAG,"Cannot list chapter folders",ex);}
        bookCache=new ArrayList<>(result);bookCacheAt=now;
        return result;
    }
    /** Completed, index-committed chapters only. This is executed off the UI thread. */
    static int exportableCount(Context ctx,String uri,String title){
        return snapshot(ctx,uri,title).states.size();
    }
    private static final class ExportChapter {
        final int number;
        final ArrayList<DocumentFile> parts=new ArrayList<>();
        ExportChapter(int number){this.number=number;}
    }
    private static String exportName(int chapter,BookChapterIndex toc){
        String prefix=String.format(Locale.ROOT,"%03d",chapter+1);
        if(toc==null||chapter>=toc.size()||toc.size()==0){
            ArrayList<String> cached=null;
            return "Глава "+prefix+".mp3";
        }
        String title=toc.get(chapter).title;
        if(title==null||title.trim().isEmpty())return "Глава "+prefix+".mp3";
        return prefix+" - "+safe(title,110)+".mp3";
    }
    private static boolean sourceCover(byte[] bytes){
        return bytes!=null&&bytes.length>100&&bytes.length<5*1024*1024&&
            ((bytes[0]&255)==255&&(bytes[1]&255)==216 ||
             bytes[0]==(byte)137&&bytes[1]=='P'&&bytes[2]=='N'&&bytes[3]=='G' ||
             bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F'&&
                bytes[8]=='W'&&bytes[9]=='E'&&bytes[10]=='B'&&bytes[11]=='P');
    }
    private static String coverSuffix(byte[] bytes){
        return bytes[0]==(byte)137?"png":bytes[0]=='R'?"webp":"jpg";
    }
    private static byte[] localCover(Context ctx,String uri){
        try{
            byte[] raw=HtmlFolderLibrary.coverBytes(ctx,uri);if(sourceCover(raw))return raw;
        }catch(Exception ignored){}
        try{
            byte[] raw=RanobesStorage.localCover(ctx,uri);if(sourceCover(raw))return raw;
        }catch(Exception ignored){}
        try{
            byte[] raw=RanobeLibStorage.localCover(ctx,uri);if(sourceCover(raw))return raw;
        }catch(Exception ignored){}
        try{
            File cached=CoverLoader.target(ctx,uri);
            if(cached.isFile()&&cached.length()<5*1024*1024){
                try(InputStream in=new FileInputStream(cached)){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
                    while((n=in.read(buf))!=-1)bytes.write(buf,0,n);
                    byte[] raw=bytes.toByteArray();if(sourceCover(raw))return raw;
                }
            }
        }catch(Exception ignored){}
        // No network request or cover generation is permitted during export.
        return null;
    }
    private static DocumentFile commonCover(Context ctx,DocumentFile folder,String uri)throws Exception{
        DocumentFile previous=null;
        for(DocumentFile doc:folder.listFiles()){
            String name=doc.getName();
            if(name!=null&&name.matches("(?i)cover\\.(jpg|jpeg|png|webp)")){previous=doc;break;}
        }
        if(previous!=null)return previous;
        byte[] bytes=localCover(ctx,uri);
        if(bytes==null)return null; // Audio player offers manual cover selection later.
        String suffix=coverSuffix(bytes);
        DocumentFile cover=folder.createFile("image/"+("png".equals(suffix)?"png":
            "webp".equals(suffix)?"webp":"jpeg"),"cover."+suffix);
        if(cover==null)throw new Exception("Не удалось создать файл обложки");
        try(OutputStream out=ctx.getContentResolver().openOutputStream(cover.getUri(),"w")){
            if(out==null)throw new Exception("Нет потока записи обложки");
            out.write(bytes);out.flush();
        }catch(Exception failure){cover.delete();throw failure;}
        return cover;
    }
    /** Explicit transfer: a separate MP3 for EACH completed chapter; a single book cover.
     * Original Edge clips, WordBoundary and manifests are never modified.
     */
    static synchronized int exportAudiobook(Context ctx,String uri,String title,
            Uri destinationTree,BookChapterIndex toc)throws Exception {
        if(destinationTree==null)throw new Exception("Папка аудиотеки не выбрана");
        Uri sourceTree=EdgeFolderPicker.selected(ctx);
        if(sourceTree!=null&&sourceTree.equals(destinationTree))
            throw new Exception("Выберите другую папку аудиотеки, не папку Edge TTS");
        DocumentFile source=chapters(ctx,uri,title,false);
        if(source==null)throw new Exception("Не найдены сохранённые главы Edge TTS");
        java.util.TreeMap<Integer,ExportChapter> ready=new java.util.TreeMap<>();
        for(DocumentFile dir:source.listFiles()){
            if(dir==null||!dir.isDirectory())continue;
            DocumentFile manifest=dir.findFile(INDEX_NAME);if(manifest==null)continue;
            JSONObject index;
            try{index=new JSONObject(text(ctx,manifest,16*1024*1024));}
            catch(Exception damaged){
                Log.w(TAG,"Skipping unreadable chapter manifest during audio-library export",damaged);
                continue; // Another completed chapter must still be exportable.
            }
            if(!hash(BookUriMigration.legacy(ctx,uri)).equals(index.optString("bookId")))continue;
            int number=index.optInt("chapter",0)-1;
            if(number<0||(toc!=null&&toc.size()>0&&number>=toc.size())||ready.containsKey(number))continue;
            ExportChapter chapter=new ExportChapter(number);
            if("fragments".equals(index.optString("mode"))){
                if(!fragmentsPresent(dir,index))continue;
                DocumentFile clips=dir.findFile("fragments");JSONArray segments=index.optJSONArray("segments");
                if(clips==null||segments==null)continue;
                for(int i=0;i<segments.length();i++){
                    JSONObject row=segments.optJSONObject(i);
                    DocumentFile mp3=row==null?null:clips.findFile(row.optString("audioFile",""));
                    if(mp3==null||!mp3.isFile()){chapter.parts.clear();break;}
                    chapter.parts.add(mp3);
                }
            }else if(mergedPresent(ctx,dir,index)){
                DocumentFile mp3=indexedFile(ctx,dir,index,"audioUri","audioFile",audioName(index));
                if(mp3!=null)chapter.parts.add(mp3);
            }
            if(!chapter.parts.isEmpty())ready.put(number,chapter);
        }
        if(ready.isEmpty())throw new Exception("Нет полностью сохранённых глав для экспорта");
        DocumentFile root=DocumentFile.fromTreeUri(ctx,destinationTree);
        if(root==null||!root.canWrite())throw new Exception("Нет доступа к выбранной аудиотеке");
        String folderName=safe(title,75);
        DocumentFile folder=root.findFile(folderName);
        if(folder==null)folder=root.createDirectory(folderName);
        if(folder==null||!folder.isDirectory())throw new Exception("Не удалось создать папку книги");
        // One bounded directory listing for the explicit export; no background polling.
        java.util.HashMap<String,DocumentFile> existing=new java.util.HashMap<>();
        for(DocumentFile doc:folder.listFiles())if(doc.getName()!=null)existing.put(doc.getName(),doc);
        // The audio-library index is the commit record. An unindexed orphan left by a
        // process crash is NOT counted as a successful completed MP3 chapter.
        String docId;
        try{docId=android.provider.DocumentsContract.getDocumentId(folder.getUri());}
        catch(Exception e){docId=folder.getUri().toString();}
        AudioLibrary.Book book=new AudioLibrary.Book();
        book.id=destinationTree.toString()+"#"+docId;
        book.name=folder.getName();book.updated=System.currentTimeMillis();
        DocumentFile cover=commonCover(ctx,folder,uri);
        if(cover!=null)book.coverUri=cover.getUri().toString();
        java.util.TreeMap<String,DocumentFile> committed=new java.util.TreeMap<>(AudioLibrary::naturalCompare);
        for(AudioLibrary.Book previous:AudioLibrary.load(ctx))if(book.id.equals(previous.id)){
            for(String oldUri:previous.uris){
                for(Map.Entry<String,DocumentFile> pair:existing.entrySet()){
                    if(oldUri.equals(pair.getValue().getUri().toString())&&
                        pair.getKey().toLowerCase(Locale.ROOT).endsWith(".mp3")&&
                        !pair.getKey().matches(".* - [0-9]{4}\\.mp3")){
                        committed.put(pair.getKey(),pair.getValue());break;
                    }
                }
            }
            break;
        }
        for(ExportChapter entry:ready.values()){
            String name=exportName(entry.number,toc);
            DocumentFile already=existing.get(name);
            if(already==null)already=existing.get(name+".mp3"); // Some SAF providers append extension.
            if(already!=null){
                if(!committed.containsKey(already.getName())||already.length()<1000)
                    throw new Exception("В папке уже есть неподтверждённый MP3 главы "+(entry.number+1)+
                        ". Файл не перезаписан; выберите другую папку или удалите остаток сбоя");
                continue;
            }
            DocumentFile created=folder.createFile("audio/mpeg",name);
            if(created==null)throw new Exception("Глава "+(entry.number+1)+": не удалось создать MP3");
            try(OutputStream out=ctx.getContentResolver().openOutputStream(created.getUri(),"w")){
                if(out==null)throw new Exception("Глава "+(entry.number+1)+": SAF не открыл запись");
                String chapterTitle=toc!=null&&entry.number<toc.size()?toc.get(entry.number).title:
                    "Глава "+(entry.number+1);
                EdgeMp3ChapterExport.writeMetadata(out,chapterTitle,title,entry.number+1);
                EdgeMp3ChapterExport.Format format=new EdgeMp3ChapterExport.Format();
                for(DocumentFile part:entry.parts){
                    try(InputStream in=ctx.getContentResolver().openInputStream(part.getUri())){
                        if(in==null)throw new Exception("Глава "+(entry.number+1)+": фрагмент недоступен");
                        EdgeMp3ChapterExport.append(in,out,format);
                    }
                }
                if(format.frames==0||format.bytes<1000)throw new Exception("Глава "+(entry.number+1)+": нет MP3-кадров");
                out.flush();
            }catch(Exception failure){try{created.delete();}catch(Exception ignored){}
                throw new Exception("Ошибка экспорта главы "+(entry.number+1)+": "+failure.getMessage(),failure);}
            // Commit each successfully closed chapter immediately. A failure in a later
            // chapter cannot erase earlier exported tracks from the audio-library index.
            existing.put(created.getName(),created);
            committed.put(created.getName(),created);
            book.uris.clear();book.chapters.clear();
            for(Map.Entry<String,DocumentFile> track:committed.entrySet()){
                book.uris.add(track.getValue().getUri().toString());
                book.chapters.add(AudioLibrary.clean(track.getKey()));
            }
            AudioLibrary.merge(ctx,new java.util.ArrayList<>(java.util.Collections.singletonList(book)));
        }
        // Reuse prior tracks when nothing new was exported; preserve the book's
        // correct track order even after adding only chapters 1, 2 and 4.
        book.uris.clear();book.chapters.clear();
        for(Map.Entry<String,DocumentFile> track:committed.entrySet()){
            book.uris.add(track.getValue().getUri().toString());
            book.chapters.add(AudioLibrary.clean(track.getKey()));
        }
        if(!book.uris.isEmpty())
            AudioLibrary.merge(ctx,new java.util.ArrayList<>(java.util.Collections.singletonList(book)));
        if(cover!=null){String key="audio_cover:"+book.id;
            if(ctx.getSharedPreferences("reader",0).getString(key,"").isEmpty())
                ctx.getSharedPreferences("reader",0).edit().putString(key,book.coverUri).apply();}
        return book.uris.size();
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
                if(!hash(BookUriMigration.legacy(ctx,uri)).equals(index.optString("bookId")))continue;
                DocumentFile audio=indexedFile(ctx,chapter,index,"audioUri","audioFile",audioName(index));
                DocumentFile words=indexedFile(ctx,chapter,index,"wordsUri","wordsFile",wordsName(index));
                for(DocumentFile file:new DocumentFile[]{audio,words,idx}){
                    if(file!=null&&!file.delete())throw new Exception("Невозможно удалить "+file.getName());
                }
                DocumentFile fragments=chapter.findFile("fragments");
                if(fragments!=null&&!fragments.delete())throw new Exception("Невозможно удалить фрагменты главы");
                if(chapter.listFiles().length==0)chapter.delete();
            }
            if(chapters.listFiles().length==0)chapters.delete();
            DocumentFile marker=folder.findFile(".edge-book.json");
            if(marker!=null&&folder.listFiles().length==1)marker.delete();
            if(folder.listFiles().length==0)folder.delete();
            invalidate();DownloadChangeBus.publish("edge:"+uri);return true;
        }catch(Exception ex){Log.w(TAG,"Cannot delete saved chapter",ex);return false;}
    }

}
