package ru.reader.rsvp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded, in-process read model. Durable download stores stay authoritative. */
final class DownloadProgressTracker {
    static final class Seed {
        final String key; final int progress; final boolean committed;
        Seed(String key,int progress,boolean committed){this.key=key;this.progress=progress;this.committed=committed;}
    }
    static final class Summary {
        final String downloadId,activeChapter;
        final long revision, weightedDone, weightedTotal;
        final int chapters,finished,percent;
        final boolean hasPlan;
        Summary(String id,Book b){
            downloadId=id;revision=b.revision;weightedDone=b.done;weightedTotal=b.total;
            chapters=b.entries.size();finished=b.finished;activeChapter=b.active;
            hasPlan=b.plan;
            percent=b.plan && b.total>0 ? (int)Math.min(100,(b.done*100L)/b.total) : -1;
        }
    }
    private static final class Chapter {int permille; boolean committed; long sequence;}
    private static final class Book {
        final Map<String,Chapter> entries=new LinkedHashMap<>();
        boolean plan; long done,total,revision;int finished;String active="";
    }
    private static final LinkedHashMap<String,Book> books=new LinkedHashMap<>(32,0.75f,true);
    private static Book book(String id){
        Book value=books.get(id);
        if(value==null){value=new Book();books.put(id,value);}
        while(books.size()>48)books.remove(books.keySet().iterator().next());
        return value;
    }
    private DownloadProgressTracker(){}
    /** Structural change only (on opening or plan edits); never called on a byte callback. */
    static synchronized Summary plan(String id,List<Seed> rows){
        Book b=book(id);
        boolean same=b.plan&&b.entries.size()==rows.size();
        if(same)for(Seed row:rows)if(!b.entries.containsKey(row.key)){same=false;break;}
        if(same){
            // Durable evidence may have changed due to an explicit delete/reset.
            // Only revert a committed chapter when the authoritative row is empty.
            for(Seed row:rows){
                Chapter ch=b.entries.get(row.key);
                if(ch!=null&&row.committed&&!ch.committed){
                    b.done+=1000-ch.permille;ch.permille=1000;ch.committed=true;
                    b.finished++;b.revision++;
                }else if(ch!=null&&ch.committed&&!row.committed&&row.progress<=0){
                    ch.committed=false;ch.permille=0;ch.sequence=0;
                    b.done-=1000;b.finished=Math.max(0,b.finished-1);b.revision++;
                }
            }
        }
        if(!same){
            Map<String,Chapter> previous=new LinkedHashMap<>(b.entries);
            b.entries.clear();b.done=0;b.total=0;b.finished=0;
            for(Seed seed:rows){
                Chapter ch=previous.get(seed.key);
                if(ch==null){ch=new Chapter();ch.committed=seed.committed;
                    ch.permille=seed.committed?1000:clamp(seed.progress,0,99)*10;}
                else if(seed.committed){ch.committed=true;ch.permille=1000;}
                b.entries.put(seed.key,ch);b.total+=1000;b.done+=ch.permille;
                if(ch.committed)b.finished++;
            }
            b.plan=true;b.revision++;
        }
        return new Summary(id,b);
    }
    /** Constant-time update for an existing chapter, independent of chapter count. */
    static synchronized Summary update(String id,String key,int permille,boolean committed,long sequence){
        Book b=book(id);
        Chapter ch=b.entries.get(key);
        if(ch==null){
            // A worker may notify before the manager opens; keep just the delta.
            ch=new Chapter();b.entries.put(key,ch);
            if(b.plan)b.total+=1000;
        }
        if(sequence<=ch.sequence)return new Summary(id,b);
        if(ch.committed&&!committed)return new Summary(id,b); // durable completion wins over old network progress
        int next=committed?1000:clamp(permille,0,999);
        if(b.plan)b.done+=next-ch.permille;
        if(committed&&!ch.committed)b.finished++;
        ch.committed=committed;ch.permille=next;ch.sequence=sequence;
        b.active=key;b.revision++;
        return new Summary(id,b);
    }
    static synchronized Summary summary(String id){Book b=books.get(id);return b==null?null:new Summary(id,b);}
    static synchronized boolean known(String id){Book b=books.get(id);return b!=null&&b.plan;}
    static synchronized void forget(String id){books.remove(id);}
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
}
