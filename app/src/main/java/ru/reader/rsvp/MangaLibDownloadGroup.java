package ru.reader.rsvp;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Presentation-only rollup: one MangaLib series in Downloads, chapter jobs remain separate.
 * Pure Java to keep counting and mixed-state precedence independently testable. */
final class MangaLibDownloadGroup {
    private final Set<String> volumes=new HashSet<>();
    int chapters,finished,pagesDone,pagesKnown,unknownChapters;
    long downloadedBytes;
    boolean running,queued,paused,failed,cancelled;

    void add(String volume,int done,int total,long bytes,String status){
        volumes.add(volume==null?"":volume);
        chapters++;
        pagesDone+=Math.max(0,done);
        if(total>0)pagesKnown+=total;else unknownChapters++;
        downloadedBytes+=Math.max(0,bytes);
        if("complete".equals(status))finished++;
        else if("running".equals(status))running=true;
        else if("queued".equals(status))queued=true;
        else if("paused".equals(status))paused=true;
        else if("failed".equals(status)||"blocked".equals(status))failed=true;
        else if("cancelled".equals(status))cancelled=true;
    }
    boolean complete(){return chapters>0&&chapters==finished;}
    boolean active(){return running||queued;}
    String status(){
        if(complete())return "CBZ готовы";
        if(running)return failed?"Скачивается · есть ошибки":"Скачивается";
        if(queued)return failed?"В очереди · есть ошибки":"В очереди";
        if(failed)return "Ошибка загрузки";
        if(paused)return "Приостановлено";
        if(cancelled)return "Отменено";
        if(finished>0)return "Частично скачано";
        return "Ожидает";
    }
    String description(){
        String pages=unknownChapters>0?
            (pagesDone>0?"Страниц скачано: "+pagesDone:"Страницы: подготовка"):
            ("Страниц: "+pagesDone+"/"+pagesKnown);
        return "Томов: "+volumes.size()+" · Глав: "+finished+"/"+chapters+
            " · "+pages+" · "+String.format(Locale.ROOT,"%.1f МБ",downloadedBytes/1048576.0);
    }
}
