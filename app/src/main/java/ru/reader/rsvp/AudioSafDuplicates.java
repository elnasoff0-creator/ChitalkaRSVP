package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;

/** Conservative, opt-in cleanup of already-published, byte-identical audiobook tracks. */
final class AudioSafDuplicates {
    private AudioSafDuplicates(){}
    static final class Report {
        int candidates,removed,skipped;
        long bytes,freed;
        final ArrayList<String> errors=new ArrayList<>();
        String summary(){return "Проверенных копий: "+candidates+"\nМожно освободить: "+
            String.format(java.util.Locale.ROOT,"%.1f МБ",bytes/1048576.0)+
            "\nУдалено: "+removed+"\nОсвобождено: "+
            String.format(java.util.Locale.ROOT,"%.1f МБ",freed/1048576.0)+
            (errors.isEmpty()?"":"\nОшибки: "+errors.size());}
    }
    private static byte[] hash(InputStream in)throws Exception{
        MessageDigest sha=MessageDigest.getInstance("SHA-256");
        try(InputStream source=in){byte[] buffer=new byte[65536];int n;
            while((n=source.read(buffer))!=-1)sha.update(buffer,0,n);
        }
        return sha.digest();
    }
    static boolean matches(Context ctx,File local,Uri saved){
        if(local==null||!local.isFile()||local.length()<=1024||saved==null)return false;
        try{
            DocumentFile remote=DocumentFile.fromSingleUri(ctx,saved);
            if(remote==null||!remote.isFile()||remote.length()!=local.length())return false;
            InputStream input=ctx.getContentResolver().openInputStream(saved);
            return input!=null&&Arrays.equals(hash(new FileInputStream(local)),hash(input));
        }catch(Exception failure){android.util.Log.w("PROChitalka-SAF","Verification failed",failure);return false;}
    }
    static boolean libraryReferences(Context ctx,String source,String uri){
        for(AudioLibrary.Book book:AudioLibrary.load(ctx))
            if(("url:"+source).equals(book.id)&&book.uris.contains(uri))return true;
        return false;
    }
    static Report inspect(Context ctx,boolean remove){
        Report result=new Report();
        // Never clean while an audio player might still hold a descriptor to an old path.
        if(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isAudioPlaying()){
            result.errors.add("Остановите воспроизведение перед очисткой");return result;
        }
        for(BookImportManager.Job job:BookImportManager.load(ctx)){
            for(int i=0;i<job.chapters.size();i++){
                File local=BookImportManager.trackFile(ctx,job,i);
                if(!local.isFile()||local.getName().endsWith(".part"))continue;
                String address=BookImportManager.relocated(job,i);
                if(!address.startsWith("content://")||!libraryReferences(ctx,job.source,address)){
                    result.skipped++;continue;
                }
                if(!matches(ctx,local,Uri.parse(address))){result.skipped++;continue;}
                result.candidates++;result.bytes+=local.length();
                if(remove){
                    if(AudioPlaybackService.instance!=null&&AudioPlaybackService.instance.isAudioPlaying()){
                        result.errors.add("Воспроизведение возобновилось");return result;
                    }
                    // Revalidate just before unlinking to avoid deleting the only copy.
                    if(!libraryReferences(ctx,job.source,address)||!matches(ctx,local,Uri.parse(address))){result.skipped++;continue;}
                    long size=local.length();
                    if(local.delete()){result.removed++;result.freed+=size;}
                    else result.errors.add("Не удалось удалить "+local.getName());
                }
            }
        }
        return result;
    }
}
