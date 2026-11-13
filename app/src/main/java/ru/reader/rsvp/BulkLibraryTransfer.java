package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/** Explicit user action: migrate all app-owned, inactive downloads and imported files. */
final class BulkLibraryTransfer {
    private BulkLibraryTransfer(){}
    static final class Report {
        int books,files,removed,skipped;
        final ArrayList<String> failures=new ArrayList<>();
        String message(){
            String info="Книг/загрузок перенесено: "+books+", файлов: "+files+
                ", старых файлов удалено: "+removed+", пропущено: "+skipped;
            if(!failures.isEmpty()){
                info+="\nНе перенесены (первые "+Math.min(8,failures.size())+"):";
                for(int i=0;i<Math.min(8,failures.size());i++)info+="\n• "+failures.get(i);
            }
            return info;
        }
    }
    static Report move(Context ctx,Uri destination){
        Report report=new Report();
        HashSet<String> downloaded=new HashSet<>();
        for(DownloadsRepository.Item item:DownloadsRepository.load(ctx,null)){
            if(item.previewOnly||item.running||DownloadsRepository.EDGE.equals(item.type)){
                report.skipped++;continue;
            }
            if(DownloadsRepository.RANOBE.equals(item.type)&&RanobeLibStorage.externalDir(ctx,item.source)!=null){report.skipped++;continue;}
            if(DownloadsRepository.MANGA.equals(item.type)&&!MangaLibStorage.externalDir(ctx,item.source).isEmpty()){
                report.skipped++;continue;
            }
            if(DownloadsRepository.LINK.equals(item.type)){
                // Direct-link files are written to an explicit user-selected folder already.
                report.skipped++;continue;
            }
            if(DownloadsRepository.AUDIO.equals(item.type))downloaded.add("audio:"+item.source);
            if(DownloadsRepository.RANOBE.equals(item.type))downloaded.add("ranobe:"+item.source);
            if(DownloadsRepository.MANGA.equals(item.type))downloaded.add("manga:"+item.source);
            try{
                SafeDownloadTransfer.Result result=SafeDownloadTransfer.copy(ctx,item,destination);
                report.books++;report.files+=result.copied+result.existing;report.removed+=result.deleted;
            }catch(Exception error){report.skipped++;report.failures.add(item.title+": "+error.getMessage());}
        }
        // Library records can exist without a download job (e.g. imported text files).
        try{
            JSONArray array=new JSONArray(ctx.getSharedPreferences("reader",0).getString("library","[]"));
            for(int i=0;i<array.length();i++){
                JSONObject row=array.optJSONObject(i);if(row==null)continue;
                String uri=row.optString("uri"),title=row.optString("title","Книга");
                if(!uri.startsWith("file:")){report.skipped++;continue;}
                File source=new File(Uri.parse(uri).getPath());
                if(!source.isFile()||!SafeDownloadTransfer.appOwned(ctx,source)){
                    report.skipped++;continue;
                }
                String slug=RanobeLibStorage.slugForBookUri(ctx,uri);
                if(!slug.isEmpty()&&downloaded.contains("ranobe:"+slug)){report.skipped++;continue;}
                try{
                    LibraryMoveManager.Report out=LibraryMoveManager.text(ctx,uri,title,destination);
                    report.books++;report.files+=out.copied+out.existing;report.removed+=out.deleted;
                }catch(Exception error){report.skipped++;report.failures.add(title+": "+error.getMessage());}
            }
        }catch(Exception error){report.failures.add("Список текстовых книг: "+error.getMessage());}
        for(AudioLibrary.Book book:AudioLibrary.load(ctx)){
            if(book.id.startsWith("url:")&&downloaded.contains("audio:"+book.id.substring(4)))continue;
            boolean appOwned=false;
            for(String track:book.uris){
                Uri uri=Uri.parse(track);
                if("file".equalsIgnoreCase(uri.getScheme())&&SafeDownloadTransfer.appOwned(ctx,new File(uri.getPath()))){appOwned=true;break;}
            }
            if(!appOwned)continue;
            try{
                LibraryMoveManager.Report out=LibraryMoveManager.audio(ctx,book,destination);
                report.books++;report.files+=out.copied+out.existing;report.removed+=out.deleted;
            }catch(Exception error){report.skipped++;report.failures.add(book.name+": "+error.getMessage());}
        }
        // Third-party SAF comic trees are already in user-controlled storage and
        // are intentionally never moved/deleted by an unattended bulk action.
        return report;
    }
}
