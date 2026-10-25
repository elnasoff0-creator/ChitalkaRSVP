package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Immutable navigable structural chapter index. Character offsets always refer to
 * the exact normalized BookLoader.Book.text, not to UI chunks or TTS fragments. */
public final class BookChapterIndex {
    public static final class Chapter {
        public final String title;
        public final int startChar,endChar,level;
        public Chapter(String title,int start,int end,int level){
            this.title=title;startChar=start;endChar=end;this.level=level;
        }
    }
    private final ArrayList<Chapter> items;
    private BookChapterIndex(ArrayList<Chapter> chapters){items=chapters;}
    public static BookChapterIndex empty(){return new BookChapterIndex(new ArrayList<>());}
    /** Chapter starts must be in the SAME text as the TTS word index. */
    public static BookChapterIndex build(List<Chapter> proposed,int textLength){
        ArrayList<Chapter> candidates=new ArrayList<>();
        if(proposed!=null)for(Chapter c:proposed){
            if(c==null||c.title==null||c.title.trim().isEmpty()||c.startChar<0||c.startChar>=textLength)continue;
            candidates.add(new Chapter(c.title.trim(),c.startChar,0,Math.max(0,c.level)));
        }
        // Stable order retains source TOC precedence for duplicate destinations.
        Collections.sort(candidates,Comparator.comparingInt(c->c.startChar));
        ArrayList<Chapter> sorted=new ArrayList<>();
        for(Chapter c:candidates){
            if(!sorted.isEmpty()&&sorted.get(sorted.size()-1).startChar==c.startChar)continue;
            sorted.add(c);
        }
        ArrayList<Chapter> ranged=new ArrayList<>();
        for(int i=0;i<sorted.size();i++){
            Chapter c=sorted.get(i);
            int end=i+1<sorted.size()?sorted.get(i+1).startChar:textLength;
            ranged.add(new Chapter(c.title,c.startChar,end,c.level));
        }
        return new BookChapterIndex(ranged);
    }
    public int size(){return items.size();}
    public Chapter get(int i){return items.get(i);}
    public Chapter atChar(int pos){
        Chapter result=null;
        for(Chapter c:items){if(c.startChar<=pos)result=c;else break;}
        return result;
    }
}
