package ru.reader.rsvp;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Reanchor a changed HTML book to the same passage or chapter, never to an arbitrary word. */
final class HtmlReadingAnchor {
    private HtmlReadingAnchor() {}
    static final class Snapshot {
        int chapterOrdinal=-1, relativeWord=0, oldChapterWords=0, oldPosition=0;
        String heading="";
        String[] phrase=new String[0];
        int phrasePivot=0;
        ArrayList<BookmarkAnchor> bookmarks=new ArrayList<>();
    }
    private static final class BookmarkAnchor {
        final ReaderBookmarks.Item item;
        final Snapshot anchor;
        BookmarkAnchor(ReaderBookmarks.Item i,Snapshot s){item=i;anchor=s;}
    }
    private static String canonical(String word){
        return word==null?"":word.toLowerCase(Locale.ROOT)
            .replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$","");
    }
    private static int firstWordAt(ReadingWordMap map,int charOffset){
        int lo=0,hi=map.size();
        while(lo<hi){int middle=(lo+hi)>>>1;if(map.starts[middle]<charOffset)lo=middle+1;else hi=middle;}
        return lo;
    }
    private static Snapshot atPosition(BookLoader.Book book,ReadingWordMap map,int original){
        Snapshot result=new Snapshot();
        int position=map.clamp(original);result.oldPosition=position;
        int currentChar=map.starts[position];
        BookChapterIndex index=book.chapters;
        if(index!=null)for(int i=0;i<index.size();i++){
            BookChapterIndex.Chapter ch=index.get(i);
            if(currentChar>=ch.startChar&&currentChar<ch.endChar){
                result.chapterOrdinal=i;result.heading=ch.title;
                int begin=firstWordAt(map,ch.startChar),end=firstWordAt(map,ch.endChar);
                result.relativeWord=Math.max(0,position-begin);
                result.oldChapterWords=Math.max(1,end-begin);
                break;
            }
        }
        int begin=Math.max(0,position-3),end=Math.min(map.size(),position+7);
        result.phrase=new String[end-begin];result.phrasePivot=position-begin;
        for(int i=begin;i<end;i++)result.phrase[i-begin]=canonical(map.words[i]);
        return result;
    }
    static Snapshot capture(Context context,String bookUri){
        try{
            BookLoader.Book book=BookLoader.load(context,Uri.parse(bookUri));
            ReadingWordMap map=ReadingWordMap.parse(book.text);
            if(map.size()==0)return null;
            SharedPreferences prefs=context.getSharedPreferences("reader",0);
            int position=UnifiedReadingProgress.read(prefs,bookUri,0);
            Snapshot result=atPosition(book,map,position);
            for(ReaderBookmarks.Item bookmark:ReaderBookmarks.list(context,bookUri)){
                if(result.bookmarks.size()>=500)break;
                result.bookmarks.add(new BookmarkAnchor(bookmark,atPosition(book,map,bookmark.word)));
            }
            return result;
        }catch(Exception ex){android.util.Log.w("HtmlReadingAnchor","Anchor capture unavailable",ex);return null;}
    }
    private static int findPhrase(ReadingWordMap map,Snapshot old,int start,int end,int expected){
        int length=old.phrase.length;if(length<4||start<0||end-start<length)return -1;
        int best=-1,bestDistance=Integer.MAX_VALUE;
        for(int i=start;i+length<=end;i++){
            if(!old.phrase[0].equals(canonical(map.words[i])))continue;
            boolean matches=true;
            for(int j=1;j<length;j++)if(!old.phrase[j].equals(canonical(map.words[i+j]))){matches=false;break;}
            if(matches){int candidate=i+old.phrasePivot,distance=Math.abs(candidate-expected);
                if(distance<bestDistance){best=candidate;bestDistance=distance;}
            }
        }
        return best;
    }
    static int resolve(Snapshot old,BookLoader.Book updated,ReadingWordMap map){
        if(map.size()==0)return 0;
        int start=0,end=map.size();
        BookChapterIndex toc=updated.chapters;
        if(toc!=null&&toc.size()>0&&old.chapterOrdinal>=0){
            int idx=-1;
            for(int i=0;i<toc.size();i++)if(old.heading.equals(toc.get(i).title)){idx=i;break;}
            if(idx<0&&old.chapterOrdinal<toc.size())idx=old.chapterOrdinal;
            if(idx>=0){
                BookChapterIndex.Chapter chapter=toc.get(idx);
                start=firstWordAt(map,chapter.startChar);
                end=firstWordAt(map,chapter.endChar);
            }
        }
        if(end<=start){start=0;end=map.size();}
        int relative=Math.min(Math.max(0,old.relativeWord),Math.max(0,end-start-1));
        int expected=start+relative;
        // Search in the selected chapter, not in a different volume with a common sentence.
        int found=findPhrase(map,old,start,end,expected);
        if(found>=0)return map.clamp(found);
        if(old.oldChapterWords>0&&end-start>1){
            // Preserve the approximate *local* chapter position when the passage changed.
            double fraction=Math.min(1.0,old.relativeWord/(double)old.oldChapterWords);
            return map.clamp(start+(int)Math.round(fraction*(end-start-1)));
        }
        return map.clamp(old.oldPosition);
    }
    static boolean restore(Context ctx,String bookUri,Snapshot old,String updatedHtml){
        if(old==null)return false;
        try{
            BookLoader.Book book=BookLoader.ranobeHtml(updatedHtml,"book.html");
            ReadingWordMap words=ReadingWordMap.parse(book.text);
            if(words.size()==0)return false;
            int next=resolve(old,book,words);
            SharedPreferences p=ctx.getSharedPreferences("reader",0);
            SharedPreferences.Editor edit=p.edit().putInt("book_shared_word:"+bookUri,next)
                .putInt("position:"+bookUri,next).putInt("tts_word:"+bookUri,next)
                .putInt("book_words:"+bookUri,words.size()).remove("viewport:"+bookUri);
            if(bookUri.equals(p.getString("uri","")))edit.putInt("index",next);
            BookChapterIndex.Chapter at=book.chapters.atChar(words.starts[next]);
            if(at!=null)edit.putString("book_shared_chapter:"+bookUri,at.title);
            try{
                JSONArray list=new JSONArray(p.getString("library","[]"));
                for(int i=0;i<list.length();i++){
                    JSONObject item=list.optJSONObject(i);
                    if(item!=null&&bookUri.equals(item.optString("uri"))){
                        item.put("index",next);item.put("wordCount",words.size());
                        item.put("progress",(int)((next+1L)*100/words.size()));
                        if(at!=null)item.put("chapter",at.title);
                    }
                }
                edit.putString("library",list.toString());
            }catch(Exception ex){android.util.Log.w("HtmlReadingAnchor","Library progress",ex);}
            if(!old.bookmarks.isEmpty()){
                JSONArray remapped=new JSONArray();java.util.HashSet<Integer> used=new java.util.HashSet<>();
                for(BookmarkAnchor source:old.bookmarks){
                    int target=resolve(source.anchor,book,words);
                    if(!used.add(target))continue;
                    ReaderBookmarks.Item stored=new ReaderBookmarks.Item();
                    stored.word=target;stored.created=source.item.created;
                    BookChapterIndex.Chapter now=book.chapters.atChar(words.starts[target]);
                    stored.chapter=now==null?source.item.chapter:now.title;
                    stored.title=source.item.title;
                    if(stored.title==null||stored.title.isEmpty())stored.title=stored.chapter;
                    int ch=words.starts[target];
                    stored.excerpt=book.text.substring(Math.max(0,ch-42),
                        Math.min(book.text.length(),ch+120)).replaceAll("\\s+"," ").trim();
                    remapped.put(stored.json());
                }
                edit.putString("bookmarks_v2:"+bookUri,remapped.toString())
                    .remove("bookmark:"+bookUri);
            }
            if(!edit.commit())throw new IllegalStateException("Не удалось сохранить положение чтения");
            ReaderBookCache.invalidate(ctx,Uri.parse(bookUri));
            UnifiedBookSession.invalidate(bookUri);
            return true;
        }catch(Exception ex){
            ReaderBookCache.invalidate(ctx,Uri.parse(bookUri));
            UnifiedBookSession.invalidate(bookUri);
            android.util.Log.w("HtmlReadingAnchor","Position restore",ex);
            return false;
        }
    }
}
