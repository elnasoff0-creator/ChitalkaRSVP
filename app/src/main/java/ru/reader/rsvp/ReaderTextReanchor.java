package ru.reader.rsvp;

/** Maps legacy word offsets to the same textual neighborhood after navigation is removed. */
final class ReaderTextReanchor {
    private final ReadingWordMap before,after;
    private final BookChapterIndex oldChapters,newChapters;
    private final java.util.HashMap<Integer,Integer> resolved=new java.util.HashMap<>();
    ReaderTextReanchor(ReadingWordMap before,ReadingWordMap after){this(before,after,BookChapterIndex.empty(),BookChapterIndex.empty());}
    ReaderTextReanchor(ReadingWordMap before,ReadingWordMap after,BookChapterIndex oldChapters,BookChapterIndex newChapters){
        this.before=before;this.after=after;this.oldChapters=oldChapters;this.newChapters=newChapters;
    }
    private int estimate(int old){
        BookChapterIndex.Chapter prior=oldChapters.atChar(before.starts[old]);
        if(prior!=null){
            BookChapterIndex.Chapter match=null;int count=0;
            for(int i=0;i<newChapters.size();i++)if(prior.title.equals(newChapters.get(i).title)){match=newChapters.get(i);count++;}
            if(count==1)return after.clamp(after.atCharacter(match.startChar)+old-before.atCharacter(prior.startChar));
        }
        return after.clamp(old);
    }
    private boolean matches(int old,int candidate,int left,int right){
        if(candidate-left<0||candidate+right>=after.size())return false;
        for(int i=-left;i<=right;i++)if(!before.words[old+i].equals(after.words[candidate+i]))return false;
        return true;
    }
    private int locate(int old,int left,int right){
        int estimate=estimate(old);
        for(int distance=0;distance<after.size();distance++){
            int a=estimate-distance,b=estimate+distance;
            if(a>=left&&a<after.size()-right&&before.words[old].equals(after.words[a])&&matches(old,a,left,right))return a;
            if(distance!=0&&b>=left&&b<after.size()-right&&before.words[old].equals(after.words[b])&&matches(old,b,left,right))return b;
        }
        return -1;
    }
    synchronized int word(int old){
        if(old<0)return old;
        if(before.size()==0||after.size()==0)return after.clamp(old);
        old=before.clamp(old);
        Integer saved=resolved.get(old);if(saved!=null)return saved;
        int result=resolve(old);resolved.put(old,result);return result;
    }
    private int resolve(int old){
        for(int radius:new int[]{8,4,2}){
            int left=Math.min(old,radius),right=Math.min(before.size()-1-old,radius);
            if(left+right==0)break;
            int exact=locate(old,left,right);if(exact>=0)return exact;
            // Right/left-only contexts also handle a removed block next to the saved word.
            if(right>=2&&(exact=locate(old,0,right))>=0)return exact;
            if(left>=2&&(exact=locate(old,left,0))>=0)return exact;
        }
        // If the saved word was itself inside removed navigation, seek a surviving neighbor.
        for(int delta=1;delta<=16;delta++){
            int forward=old+delta,back=old-delta;
            if(forward+2<before.size()){int i=locate(forward,0,2);if(i>=0)return i;}
            if(back>=2){int i=locate(back,2,0);if(i>=0)return i;}
        }
        int exact=locate(old,0,0);return exact>=0?exact:estimate(old);
    }
    int startChar(int oldChar,int oldWord,int newWord){
        if(oldChar<0)return -1;
        int old=before.clamp(oldWord),next=after.clamp(newWord);
        int inside=Math.max(0,oldChar-before.starts[old]);
        return Math.min(after.ends[next],after.starts[next]+inside);
    }
    int endChar(int oldChar,int oldWord,int newWord){return startChar(oldChar,oldWord,newWord);}
}
