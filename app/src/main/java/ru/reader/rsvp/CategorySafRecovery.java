package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/** Explicit SAF recovery after changing a category folder. Runs off the UI thread. */
final class CategorySafRecovery {
    static final class Report {
        int succeeded,missing,skipped;
        final java.util.ArrayList<String> errors=new java.util.ArrayList<>();
        String summary(){
            String s="Перепроверено и сохранено: "+succeeded+"; пропущено: "+skipped+
                "; недоступно: "+missing+".";
            if(!errors.isEmpty()){
                s+="\n\nОшибки ("+errors.size()+"):";
                for(int i=0;i<Math.min(6,errors.size());i++)s+="\n• "+errors.get(i);
            }
            s+="\n\nИсходные файлы не удалялись.";
            return s;
        }
        void error(String name,Exception ex){
            missing++;errors.add(name+": "+(ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage()));
        }
    }
    private CategorySafRecovery(){}
    static Report restore(Context context){
        Context ctx=context.getApplicationContext();Report report=new Report();
        if(DownloadFolderPicker.available(ctx,DownloadFolderPicker.AUDIO))audio(ctx,report);
        if(DownloadFolderPicker.available(ctx,DownloadFolderPicker.MANGA))manga(ctx,report);
        if(DownloadFolderPicker.available(ctx,DownloadFolderPicker.BOOK)){
            ranobes(ctx,report);ranobeLib(ctx,report);edge(ctx,report);
        }
        return report;
    }
    private static boolean exists(Context c,String uri){
        if(uri==null||uri.isEmpty())return false;
        try{
            if(uri.startsWith("content://")){
                DocumentFile doc=DocumentFile.fromSingleUri(c,Uri.parse(uri));
                return doc!=null&&doc.isFile()&&doc.length()>0;
            }
            if(uri.startsWith("file:"))return new File(Uri.parse(uri).getPath()).isFile();
            return new File(uri).isFile();
        }catch(Exception ex){return false;}
    }
    private static void audio(Context ctx,Report report){
        for(BookImportManager.Job job:BookImportManager.load(ctx)){
            boolean updated=false;
            String folder=BookImportManager.safe(job.title)+"__"+Integer.toHexString(job.source.hashCode());
            for(int i=0;i<job.chapters.size();i++){
                try{
                    File local=BookImportManager.trackFile(ctx,job,i);
                    String original=BookImportManager.relocated(job,i);
                    if(i>=job.states.size()||job.states.get(i)!=100){report.skipped++;continue;}
                    Uri remote;
                    if(local.isFile()&&local.length()>1024){
                        remote=DownloadFolderPicker.exportFile(ctx,DownloadFolderPicker.AUDIO,local,folder);
                    }else if(exists(ctx,original)&&original.startsWith("content://")){
                        DocumentFile root=DocumentFile.fromTreeUri(ctx,DownloadFolderPicker.selected(ctx,DownloadFolderPicker.AUDIO));
                        DocumentFile dir=SafeDownloadTransfer.directory(root,folder);
                        DocumentFile old=DocumentFile.fromSingleUri(ctx,Uri.parse(original));
                        DocumentFile copied=SafeDownloadTransfer.copyUri(ctx,original,dir,old.getName(),new SafeDownloadTransfer.Result());
                        remote=copied.getUri();
                    }else{report.skipped++;continue;}
                    while(job.relocatedUris.size()<=i)job.relocatedUris.add("");
                    job.relocatedUris.set(i,remote.toString());updated=true;report.succeeded++;
                }catch(Exception error){report.error("Аудиокнига «"+job.title+"», глава "+(i+1),error);}
            }
            if(updated){BookImportManager.put(ctx,job);BookImportManager.updateLibrary(ctx,job);}
            try{
                File cover=AudiobookCoverStore.coverFile(ctx,job);
                if(cover.isFile()&&cover.length()>100){
                    File staging=new File(ctx.getCacheDir(),"saf_audio_cover_restore_v165");
                    if(!staging.isDirectory()&&!staging.mkdirs())throw new IOException("Нет временной папки для обложки");
                    File image=new File(staging,"cover.jpg");
                    try(java.io.InputStream in=new java.io.FileInputStream(cover);
                        java.io.OutputStream out=new java.io.FileOutputStream(image)){
                        byte[] buffer=new byte[16384];int n;
                        while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    }
                    try{DownloadFolderPicker.exportFile(ctx,DownloadFolderPicker.AUDIO,image,folder);}
                    finally{image.delete();}
                }
            }catch(Exception error){report.error("Обложка аудиокниги «"+job.title+"»",error);}
        }
    }
    private static void manga(Context ctx,Report report){
        for(MangaLibStorage.Job job:MangaLibStorage.jobs(ctx)){
            if(!"complete".equals(job.status)&&!"failed".equals(job.status))continue;
            try{
                File original=MangaLibStorage.chapterFile(ctx,job);
                if(original.isFile()&&!"complete".equals(job.status)){
                    try(java.util.zip.ZipFile archive=new java.util.zip.ZipFile(original)){
                        if(archive.getEntry("ComicInfo.xml")==null||archive.size()<2)
                            throw new IOException("CBZ неполный; для восстановления требуется загрузчик");
                    }
                }
                String[] folders={MangaLibStorage.safe(job.series)+"__"+MangaLibStorage.safe(job.slug),
                    "Том "+MangaLibStorage.safe(job.volume)};
                Uri published;
                if(original.isFile()&&original.length()>2048){
                    published=DownloadFolderPicker.exportFile(ctx,DownloadFolderPicker.MANGA,original,folders);
                }else if(exists(ctx,job.file)&&job.file.startsWith("content://")){
                    DocumentFile dir=DocumentFile.fromTreeUri(ctx,DownloadFolderPicker.selected(ctx,DownloadFolderPicker.MANGA));
                    for(String name:folders)dir=SafeDownloadTransfer.directory(dir,name);
                    DocumentFile source=DocumentFile.fromSingleUri(ctx,Uri.parse(job.file));
                    published=SafeDownloadTransfer.copyUri(ctx,job.file,dir,source.getName(),new SafeDownloadTransfer.Result()).getUri();
                }else{report.skipped++;continue;}
                job.file=published.toString();job.status="complete";job.error="";
                MangaLibStorage.put(ctx,job);MangaLibStorage.registerComic(ctx,job);report.succeeded++;
                File cover=MangaDownloadCover.file(ctx,job.slug);
                if(cover.isFile()&&cover.length()>100){
                    File dir=new File(ctx.getCacheDir(),"saf_manga_cover_restore_v165");
                    if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось подготовить обложку");
                    File image=new File(dir,"cover.jpg");
                    try(java.io.InputStream in=new java.io.FileInputStream(cover);
                        java.io.OutputStream out=new java.io.FileOutputStream(image)){
                        byte[] bytes=new byte[16384];int n;
                        while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
                    }
                    try{DownloadFolderPicker.exportFile(ctx,DownloadFolderPicker.MANGA,image,folders[0]);}
                    finally{image.delete();}
                }
            }catch(Exception error){report.error("Манга «"+job.chapter+"»",error);}
        }
    }
    private static void ranobes(Context ctx,Report report){
        HashSet<String> keys=new HashSet<>();
        for(RanobesStorage.Job job:RanobesStorage.jobs(ctx))if("complete".equals(job.status))keys.add(job.sourceKey());
        for(String key:keys)try{
            RanobesStorage.rebuild(ctx,key);
            DocumentFile root=DocumentFile.fromTreeUri(ctx,DownloadFolderPicker.selected(ctx,DownloadFolderPicker.BOOK));
            DocumentFile dir=root==null?null:root.findFile(RanobesStorage.safeOutputDir(key));
            DocumentFile file=dir==null?null:dir.findFile("book.html");
            if(file==null||!file.isFile()||file.length()<100)
                throw new IOException("Файл не появился в выбранной папке Book");
            report.succeeded++;
        }catch(Exception error){report.error("Ranobes "+key,error);}
    }
    private static void ranobeLib(Context ctx,Report report){
        HashSet<String> slugs=new HashSet<>();
        for(RanobeLibStorage.Job job:RanobeLibStorage.readJobs(ctx))
            if("complete".equals(job.status))slugs.add(job.slug);
        for(String slug:slugs)try{
            if(RanobeLibStorage.relocateToSelectedBook(ctx,slug))report.succeeded++;
            else report.skipped++;
        }catch(Exception error){report.error("RanobeLIB "+slug,error);}
    }
    /** Preserve old finished Edge TTS chapters when user changes to the Book tree. */
    private static void edge(Context ctx,Report report){
        String old=ctx.getSharedPreferences("reader",0).getString("edge_audio_folder_uri_v150","");
        Uri current=DownloadFolderPicker.selected(ctx,DownloadFolderPicker.BOOK);
        if(old.isEmpty()||current==null||old.equals(current.toString()))return;
        try{
            DocumentFile previous=DocumentFile.fromTreeUri(ctx,Uri.parse(old));
            DocumentFile target=DocumentFile.fromTreeUri(ctx,current);
            if(previous==null||target==null||!previous.isDirectory()||!target.isDirectory())return;
            for(DocumentFile folder:previous.listFiles()){
                if(!folder.isDirectory()||folder.findFile(".edge-book.json")==null)continue;
                try{
                    String base=SafeDownloadTransfer.safe(folder.getName());
                    DocumentFile dest=target.findFile(base);
                    if(dest!=null&&dest.findFile(".edge-book.json")!=null){
                        // Do not overwrite an unrelated folder with the same displayed name.
                        String oldId=readSmall(ctx,folder.findFile(".edge-book.json"));
                        String newId=readSmall(ctx,dest.findFile(".edge-book.json"));
                        if(!oldId.equals(newId))base=base+" (старые главы)";
                    }
                    dest=SafeDownloadTransfer.directory(target,base);
                    SafeDownloadTransfer.copyDocumentTree(ctx,folder,dest,new SafeDownloadTransfer.Result(),0);
                    report.succeeded++;
                }catch(Exception error){report.error("Edge TTS "+folder.getName(),error);}
            }
            EdgeChapterArchive.invalidate();
        }catch(Exception error){report.error("Старые главы Edge TTS",error);}
    }
    private static String readSmall(Context c,DocumentFile file)throws IOException {
        if(file==null||!file.isFile()||file.length()>8192)return "";
        try(java.io.InputStream in=c.getContentResolver().openInputStream(file.getUri())){
            if(in==null)return "";
            byte[] b=new byte[8192];int n=in.read(b);
            return n<1?"":new String(b,0,n,java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
