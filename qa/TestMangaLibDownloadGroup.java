package ru.reader.rsvp;

/** JVM-only behaviour test for the aggregated MangaLib download card. */
public final class TestMangaLibDownloadGroup {
    private static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);}
    public static void main(String[] args){
        MangaLibDownloadGroup one=new MangaLibDownloadGroup();
        one.add("1",50,50,8_000_000,"complete");
        one.add("1",42,42,7_000_000,"complete");
        one.add("2",18,50,3_000_000,"running");
        check(one.chapters==3,"three chapter records in one manga");
        check(one.finished==2,"two complete chapters");
        check(one.pagesDone==110&&one.pagesKnown==142,"sum of page counters");
        check(one.downloadedBytes==18_000_000,"byte aggregation");
        check(!one.complete()&&one.active(),"active while another chapter downloads");
        check(one.description().contains("Томов: 2 · Глав: 2/3 · Страниц: 110/142"),"summary by volumes and chapters");
        MangaLibDownloadGroup partial=new MangaLibDownloadGroup();
        partial.add("1",50,50,500_000,"complete");
        partial.add("1",0,0,0,"queued");
        check(partial.unknownChapters==1,"unknown pages do not become zero pages");
        check(partial.description().contains("Страниц скачано: 50"),"unknown page count stays unknown");
        partial.add("1",0,0,0,"failed");
        check(partial.status().equals("В очереди · есть ошибки"),"queued mixed with failure reported");
        MangaLibDownloadGroup done=new MangaLibDownloadGroup();
        done.add("1",11,11,100_000,"complete");
        check(done.complete()&&done.status().equals("CBZ готовы"),"all finished");
        MangaLibDownloadGroup paused=new MangaLibDownloadGroup();
        paused.add("1",0,0,0,"paused");
        check(paused.status().equals("Приостановлено"),"paused state");
        MangaLibDownloadGroup empty=new MangaLibDownloadGroup();
        check(!empty.complete(),"empty list cannot be complete");
        System.out.println("PASS: manga series aggregation, mixed states, bytes, volumes, unknown pages");
    }
}
