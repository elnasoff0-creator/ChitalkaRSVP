package ru.reader.rsvp;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Embedded Edge chapter actions previously available only in a separate details Activity. */
final class EdgeChapterActions {
    private EdgeChapterActions(){}
    private static final class BookContext {
        BookChapterIndex toc;
        ArrayList<Integer> offsets;
    }
    private static BookContext index(Context ctx,String uri)throws Exception{
        BookLoader.Book book=BookLoader.load(ctx,Uri.parse(uri));
        BookContext data=new BookContext();data.toc=book.chapters;
        data.offsets=SentenceChunks.wordPositions(book.text==null?"":book.text);
        return data;
    }
    static String saveCached(Context ctx,String uri,String title,int chapter)throws Exception {
        if(chapter<0)throw new Exception("Выберите главу");
        if(!EdgeFolderPicker.available(ctx))throw new Exception("Сначала выберите папку Book");
        EdgeFullBookStore.Job job=EdgeFullBookStore.find(ctx,uri,title);
        if(job==null)throw new Exception("Очередь подготовки отсутствует");
        BookContext book=index(ctx,uri);
        if(chapter>=book.toc.size())throw new Exception("Глава отсутствует в оглавлении");
        int from=SentenceChunks.lowerBound(book.offsets,book.toc.get(chapter).startChar);
        int to=SentenceChunks.lowerBound(book.offsets,book.toc.get(chapter).endChar);
        JSONArray complete=EdgeFullBookStore.queue(job),chosen=new JSONArray();
        int missing=0;
        for(int i=0;i<complete.length();i++){
            JSONObject row=complete.optJSONObject(i);if(row==null)continue;
            if(row.optInt("end",0)<=from||row.optInt("start",0)>=to)continue;
            chosen.put(row);
            File mp3=new File(job.mp3Folder(),row.optString("key","")+".mp3");
            if(!EdgeFullBookStore.ready(mp3))missing++;
        }
        if(chosen.length()==0)throw new Exception("Нет сохранённой очереди фрагментов главы");
        if(missing>0)throw new Exception("Отсутствуют "+missing+" из "+chosen.length()+" MP3; готовые фрагменты сохранены");
        int result=EdgeChapterArchive.publishFragmentsOne(ctx,uri,title,job.mp3Folder(),
            chosen,book.toc,book.offsets,chapter);
        if(result<=0)
            throw new Exception("Публикация в SAF не завершена: "+EdgeChapterArchive.publicationError());
        EdgeFullBookStore.markPublished(job,chapter);
        return "Глава сохранена из готовых MP3";
    }
}
