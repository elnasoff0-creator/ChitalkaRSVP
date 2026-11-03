package ru.reader.rsvp;

import java.util.List;

/** Exact whole-series progress. Unknown chapter sizes must never produce a guessed percentage. */
final class ComicProgressMath {
    private ComicProgressMath() {}

    static int percent(List<Integer> pageCounts, int chapter, int page, int chapterTotal) {
        if(pageCounts==null || chapterTotal<=0 || pageCounts.size()!=chapterTotal
                || chapter<0 || chapter>=chapterTotal)return -1;
        long total=0, preceding=0;
        for(int i=0;i<pageCounts.size();i++){
            Integer value=pageCounts.get(i);
            if(value==null || value<=0)return -1;
            total+=value;
            if(i<chapter)preceding+=value;
        }
        if(total<=0)return -1;
        int current=Math.max(1,Math.min(page+1,pageCounts.get(chapter)));
        return (int)Math.max(0,Math.min(100,Math.round((preceding+current)*100.0/total)));
    }
}
